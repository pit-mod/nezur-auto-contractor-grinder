package com.nezurstandalone.engine;

import com.nezurstandalone.module.impl.player.*;

import com.nezurstandalone.combat.CombatAura;
import com.nezurstandalone.input.ClickSimulator;
import com.nezurstandalone.input.GuardedInput;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.module.impl.render.Focus;
import com.nezurstandalone.pathfinder.AutoWalker;
import com.nezurstandalone.pathfinder.PathfinderManager;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.utils.NotificationManager;
import com.nezurstandalone.utils.PitMapManager;
import com.nezurstandalone.utils.RotationManager;
import com.nezurstandalone.utils.RotationUtils;
import com.nezurstandalone.utils.Utils;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;
import com.nezurstandalone.utils.BlockScanner;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraft.client.renderer.GlStateManager;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.gui.GuiDraw;
import com.nezurstandalone.gui.GuiTheme;
import com.nezurstandalone.gui.GuiAnim;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous Pit grinder, built on Nezur's own systems.
 *
 * <p>All movement runs through {@link AutoWalker} / {@link PathfinderManager}; all rotation runs
 * through the shared {@link RotationManager} (the walker's steering aim, the combat aim, and the
 * NPC-interact aim are all its clients — this class never writes {@code rotationYaw} directly);
 * combat delegates to {@link KillAura}, using its live targeting, clicking and rotation settings.
 *
 * <p>On top of the core grind it routes the Pit's events — Spire (wait/rush/fight), Squads (rank
 * swap), Rage Pit / TDM (grind through, no swap/break) — tracks Night-Quest timing, and mutes the
 * game. Auto-Perk and Auto-Prestige navigate to <em>two blocks away</em> from the NPC and open it
 * with a crosshair-validated interact (the same safe click {@link
 * com.nezurstandalone.module.impl.player.ChestAura} uses); if the menu does not open — someone standing in
 * the way — they sidestep to another spot and try again until it does. Prestige is no longer
 * hardcoded: it finds the "PRESTIGE" nametag, aims at the villager under it, and clicks it.
 */
public class GrinderEngine extends Module implements DraggableHud {

    public enum State {
        IDLE, LEAVING_SPAWN, FIGHTING, RETURNING_TO_MID, DRAGON_EGG, LOBBY_SWAP, LIMBO, LOBBY_RECONNECT, JOINING_PIT, PICKING_UP_MYSTIC,
        SPIRE_WAITING_SPAWN, SPIRE_WALK_TO_MID, SPIRE_FIGHTING,
        BLOCKHEAD_PAINTING, BLOCKHEAD_POWERUP, BLOCKHEAD_FIGHTING, BLOCKHEAD_AVOIDING,
        ROBBERY_FIGHTING, ROBBERY_RETREATING, ROBBERY_BANKED,
        RAFFLE_TO_MID, RAFFLE_COLLECTING, RAFFLE_RETURNING, RAFFLE_DEPOSITING,
        PERK_NAVIGATING, PERK_INTERACTING, PERK_CLICKING_GUI,
        PRESTIGE_NAVIGATING, PRESTIGE_INTERACTING, PRESTIGE_CLICKING_GUI
    }

    private static GrinderEngine instance;
    private Object engineWorld, enginePlayer;
    private int correctionTick = -1;
    private String previousZone = "";

    private final Object interactionOwner = new Object();

    private boolean hasDragonEggPriority() {
        return dragonEggSupport.isEnabled() && currentEgg != null;
    }

    public static boolean usesPitClicker(CombatAura caller) {
        return false; // KillAura is the sole attack stream for grinder/contractor combat.
    }


    private String diagnosticState = "";
    private long diagnosticCorrections, diagnosticWindowStart;
    private int diagnosticWindowCount;
    private double diagnosticX, diagnosticY, diagnosticZ;
    private long diagnosticPacketStart;
    private void diagnosticTick() {
        try {
            com.nezurstandalone.control.GrinderDiagnostics.setActive(isToggled() && debugLogging.isEnabled(), new java.io.File(mc.mcDataDir, "logs/nezur-acg-diagnostics"));
            if (!isToggled()) return;
            String state = String.valueOf(currentState);
            if (!state.equals(diagnosticState)) {
                com.nezurstandalone.control.GrinderDiagnostics.record("STATE", "from="+diagnosticState+" to="+state);
                diagnosticState=state;
            }
            if (com.nezurstandalone.control.GrinderDiagnostics.sampleDue()) diagnosticSnapshot("SAMPLE");
        } catch (RuntimeException ignored) { /* Diagnostics must not control gameplay. */ }
        diagnosticGameDump();
    }
    private void diagnosticSnapshot(String event) {
        if(!com.nezurstandalone.control.GrinderDiagnostics.isActive())return;
        try {
            if (mc.thePlayer==null || mc.theWorld==null) {
                com.nezurstandalone.control.GrinderDiagnostics.record(event,"world_or_player_missing=true"); return;
            }
            net.minecraft.client.entity.EntityPlayerSP p=mc.thePlayer;
            Entity target=combat.getTarget();
            String hit=mc.objectMouseOver==null?"null":String.valueOf(mc.objectMouseOver.typeOfHit);
            com.nezurstandalone.control.GrinderDiagnostics.record(event,
                "state="+currentState+" tick="+p.ticksExisted+" dimension="+p.dimension
                +" x="+p.posX+" y="+p.posY+" z="+p.posZ+" mx="+p.motionX+" my="+p.motionY+" mz="+p.motionZ
                +" yaw="+p.rotationYaw+" pitch="+p.rotationPitch+" ground="+p.onGround
                +" collidedH="+p.isCollidedHorizontally+" collidedV="+p.isCollidedVertically
                +" sprint="+p.isSprinting()+" water="+p.isInWater()+" lava="+p.isInLava()
                +" health="+p.getHealth()+" hurtTicks="+p.hurtTime+" fallDistance="+p.fallDistance
                +" inputForward="+p.movementInput.moveForward+" inputStrafe="+p.movementInput.moveStrafe
                +" forwardKey="+mc.gameSettings.keyBindForward.isKeyDown()+" jumpKey="+mc.gameSettings.keyBindJump.isKeyDown()
                +" sprintKey="+mc.gameSettings.keyBindSprint.isKeyDown()+" usingItem="+p.isUsingItem()+" slot="+p.inventory.currentItem
                +" gui="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName())
                +" zone="+PitMapManager.getZone(p.posX,p.posY,p.posZ)+" targetId="+(target==null?-1:target.getEntityId())
                +" targetDistance="+(target==null?-1:p.getDistanceToEntity(target))+" raycast="+hit
                +" combatStage="+combat.getStage()+" combatPermitted="+combatPermitted+" clickEligible="+mayFight()
                +" cpsMin="+activeCombatOptions().minCps()+" cpsMax="+activeCombatOptions().maxCps()+" healing="+isAutoHealBusy()
                +" eggPriority="+hasDragonEggPriority()+" pathing="+PathfinderManager.isPathing()
                +" correctionTick="+correctionTick+" session="+com.nezurstandalone.control.ClientSession.current());
        } catch (RuntimeException failure) {
            com.nezurstandalone.control.GrinderDiagnostics.record("SNAPSHOT_ERROR",failure.getClass().getSimpleName());
        }
    }
    public static void recordCorrection(net.minecraft.network.play.server.S08PacketPlayerPosLook packet, boolean before) {
        if (!isRunning()) return;
        GrinderEngine g=instance;
        try {
            g.diagnosticTick();
            if (before) {
                long now=System.nanoTime();
                if(now-g.diagnosticWindowStart>20000000000L){g.diagnosticWindowStart=now;g.diagnosticWindowCount=0;}
                g.diagnosticWindowCount++;g.diagnosticCorrections++;g.diagnosticPacketStart=now;
                if(g.mc.thePlayer!=null){g.diagnosticX=g.mc.thePlayer.posX;g.diagnosticY=g.mc.thePlayer.posY;g.diagnosticZ=g.mc.thePlayer.posZ;}
                com.nezurstandalone.control.GrinderDiagnostics.record("CORRECTION_PACKET",
                    "id="+g.diagnosticCorrections+" countIn20sWindow="+g.diagnosticWindowCount
                    +" classification=server_position_correction_NOT_confirmed_anticheat"
                    +" rawX="+packet.getX()+" rawY="+packet.getY()+" rawZ="+packet.getZ()
                    +" rawYaw="+packet.getYaw()+" rawPitch="+packet.getPitch()+" relativeFlags="+packet.func_179834_f());
                g.diagnosticSnapshot("BEFORE_CORRECTION");
            } else {
                if(g.mc.thePlayer!=null)com.nezurstandalone.control.GrinderDiagnostics.record("CORRECTION_APPLIED",
                    "id="+g.diagnosticCorrections+" dx="+(g.mc.thePlayer.posX-g.diagnosticX)
                    +" dy="+(g.mc.thePlayer.posY-g.diagnosticY)+" dz="+(g.mc.thePlayer.posZ-g.diagnosticZ)
                    +" handlerMicros="+((System.nanoTime()-g.diagnosticPacketStart)/1000));
                g.diagnosticSnapshot("AFTER_CORRECTION");
            }
        } catch (RuntimeException failure) { com.nezurstandalone.control.GrinderDiagnostics.record("CORRECTION_LOG_ERROR",failure.getClass().getSimpleName()); }
    }

    private static final String MOVEMENT_ROTATION_OWNER = "grinder.movement";
    private static final String INTERACTION_ROTATION_OWNER = "grinder.interaction";
    private boolean combatPermitted;
    private ContainerChest observedMenu;
    private String observedContents = "";
    private final com.nezurstandalone.utils.MenuTransition menuTransition = new com.nezurstandalone.utils.MenuTransition();
    private long nextGuiClickNanos;
    private long npcRetryAfterNanos;
    private long npcInteractionUntilNanos;

    public static void stopForEngineSwitch() {
        if (isRunning()) instance.setToggled(false);
    }

    public static boolean isRunning() { return instance != null && instance.isToggled(); }
    public static boolean hasUnconfirmedOperation(){return isRunning() && (instance.menuTransition.isPending()
            || com.nezurstandalone.input.WindowActions.pending(instance));}
    public static String diagnosticState(){return instance==null?"none":instance.currentState+" combat="+instance.combatPermitted;}

    public static boolean isAutoBlockheadActive() {
        return isRunning() && instance.autoBlockhead.isEnabled() && BlockheadController.live();
    }

    public static boolean isAutoRobberyActive() {
        return isRunning() && instance.autoRobbery.isEnabled() && RobberyController.live();
    }

    public static boolean isAutoRaffleActive() {
        return isRunning() && instance.autoRaffle.isEnabled() && RaffleController.live();
    }

    /** Exempts only this grinder's healing from the navigation item-use pause. */
    public static boolean allowsHealingMovement() {
        return isRunning() && instance.correctionTick < 0 && !instance.isOnBreak
                && instance.isAutoHealBusy() && PathfinderManager.available(instance)
                && (instance.currentState == State.FIGHTING || instance.currentState == State.SPIRE_FIGHTING
                    || instance.isBlockheadState()
                    || instance.currentState==State.ROBBERY_FIGHTING || instance.currentState==State.ROBBERY_RETREATING
                    || instance.currentState == State.DRAGON_EGG || instance.currentState == State.PICKING_UP_MYSTIC);
    }

    /** Mid-only pressure; ordinary navigation and other combat consumers keep their policy. */
    public static boolean isMidPressureActive() {
        if (!isRunning() || instance.hasDragonEggPriority() || instance.correctionTick >= 0 || !instance.combatPermitted || instance.currentState != State.FIGHTING) return false;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
        return client.thePlayer != null && client.theWorld != null && client.currentScreen == null
                && !client.thePlayer.isDead && client.thePlayer.getHealth() > 0
                && (!client.thePlayer.isUsingItem() || allowsHealingMovement())
                && client.thePlayer.openContainer == client.thePlayer.inventoryContainer
                && PitMapManager.getZone(client.thePlayer.posX, client.thePlayer.posY, client.thePlayer.posZ).equals("Pit");
    }

    public static boolean isMidPressureActive(CombatAura caller) {
        return false; // Legacy CombatAura consumers do not own this macro's combat.
    }

    public static boolean ownsCombatChase(EntityPlayer target) {
        return target!=null && isRunning() && instance.mayFight() && instance.combat.getTarget()==target;
    }


    public static void onMenuContents(int window,Object container){if(instance!=null)instance.menuTransition.contents(window,container);}
    /** Only server packet stacks populate loadout evidence, including menus opened by the user. */
    public static void onLoadoutContents(int window, Object container, ItemStack[] stacks) {
        if(instance!=null)instance.completeMysticPickupOnInventory();
        if(instance==null || !(container instanceof ContainerChest))return;
        instance.observeLoadoutPacket(window,(ContainerChest)container,stacks,-1);
    }
    public static void onLoadoutSlot(int window,int index,ItemStack stack) {
        if(instance!=null)instance.completeMysticPickupOnInventory();
        if(instance==null || instance.mc.thePlayer==null || !(instance.mc.thePlayer.openContainer instanceof ContainerChest))return;
        instance.observeLoadoutPacket(window,(ContainerChest)instance.mc.thePlayer.openContainer,new ItemStack[]{stack},index);
    }
    public static void onLoadoutScoreboard() {
        if(instance==null || instance.mc.thePlayer==null || instance.mc.theWorld==null)return;
        instance.refreshPerkAccount();
        Utils.getScoreboardLinesFresh();
        instance.refreshLoadoutRequirements();
        instance.refreshLoadoutGold();
    }
    public static void onMenuConfirmation(int window, short action, boolean accepted) {
        if (instance != null) instance.menuTransition.confirm(window, action, accepted);
    }
    public static void onServerCorrection() {
        if (!isRunning()) return;
        instance.pauseGameplay();
        instance.diagnosticSnapshot("CORRECTION_PAUSE");
        instance.combat.resetTargeting();
        instance.resetMenuTracking();
        instance.currentState = State.IDLE;
        instance.lastMidScan=0; instance.currentEgg=null; instance.previousZone="";
        instance.correctionTick = instance.mc.thePlayer == null ? -1 : instance.mc.thePlayer.ticksExisted;
        com.nezurstandalone.module.impl.player.AutoHeal.interrupt();
        PathfinderManager.clear(instance, true);
        RotationManager.getInstance().clearAll();
        GuardedInput.clear();
    }

    // ---- settings -----------------------------------------------------------
    private final NumberSetting minMidPlayers = new NumberSetting("Min Mid Players", 5, 1, 20, 0);
    private final BooleanSetting lobbySwap = new BooleanSetting("Lobby Swap", true);
    private final NumberSetting lobbySwapDelay = new NumberSetting("Swap Delay (s)", 5, 1, 30, 0);
    private final BooleanSetting autoWeapon = new BooleanSetting("Auto Weapon", true);
    private final BooleanSetting mysticPickup = new BooleanSetting("Mystic Pickup", true);
    private final BooleanSetting idleRetreat = new BooleanSetting("Idle Retreat", false);
    private final BooleanSetting spireMode = new BooleanSetting("Spire Mode", true);
    private final BooleanSetting autoBlockhead = new BooleanSetting("Auto Blockhead", false);
    private final BooleanSetting autoRobbery = new BooleanSetting("Auto Robbery", false);
    private final BooleanSetting autoRaffle = new BooleanSetting("Auto Raffle", false);
    private final RaffleController raffle = new RaffleController();
    private final Object raffleInputOwner = new Object();
    private static final String RAFFLE_MOVEMENT_OWNER="grinder.raffle", RAFFLE_ROTATION_OWNER="grinder.raffle.aim";
    private final ClickSimulator.Rate raffleClickRate=new ClickSimulator.Rate(() -> 6.0);
    private boolean raffleOwned, raffleEnteringMid, raffleSlotOwned, raffleInventoryPending;
    private long raffleRepathAt;
    private int rafflePathTicket=Integer.MIN_VALUE;
    private final RobberyController robbery = new RobberyController();
    private boolean robberyOwned;
    private long robberyRetreatAt;
    private final BlockheadController blockhead = new BlockheadController();
    private long blockheadRepathAt;
    private boolean blockheadOwned;
    private boolean eventApproaching;
    private boolean blockheadEnteringMid;
    private final BooleanSetting squadSupport = new BooleanSetting("Squad Support", true);
    private final BooleanSetting nightQuestSupport = new BooleanSetting("Night Quest Support", true);
    private final BooleanSetting autoPerk = new BooleanSetting("Auto Perk", true);
    private final BooleanSetting autoPrestige = new BooleanSetting("Auto Prestige", false);
    private final BooleanSetting oofOnOverdrive = new BooleanSetting("Oof on Overdrive", false);
    private final com.nezurstandalone.control.OverdriveOof overdriveOof = new com.nezurstandalone.control.OverdriveOof();
    private final BooleanSetting dragonEggSupport = new BooleanSetting("Dragon Egg Support", false);
    private final BooleanSetting muteGame = new BooleanSetting("Mute Game", false);
    private final BooleanSetting breakTimeEnable = new BooleanSetting("Enable Break Time", false);
    private final NumberSetting workDurationLimit = new NumberSetting("Work Time (h)", 2, 0.1, 24, 1);
    private final NumberSetting breakDurationLimit = new NumberSetting("Break Time (m)", 20, 1, 120, 0);
    private final NumberSetting interactAimSpeed = new NumberSetting("Interact Aim Speed", 16, 5, 30, 1);
    private final NumberSetting aimSpeed = new NumberSetting("Aim Speed", 4, 1, 50, 1);
    private final BooleanSetting onlyEventMode = new BooleanSetting("Only Event Mode", false);
    private final BooleanSetting showHud = new BooleanSetting("Show HUD", true);
    private final NumberSetting hudScale = new NumberSetting("HUD Scale", 0.9, 0.6, 1.4, 2);
    private final BooleanSetting hudAnimations = new BooleanSetting("HUD Animations", true);
    private final BooleanSetting debugLogging = new BooleanSetting("Debug Logging", false);

    // ---- combat -------------------------------------------------------------
    private final com.nezurstandalone.combat.CombatOptions combatOptions=new com.nezurstandalone.combat.CombatOptions();
    private final com.nezurstandalone.settings.BooleanSetting advancedSettings=new com.nezurstandalone.settings.BooleanSetting("Show Advanced Settings",false);
    private final com.nezurstandalone.settings.ModeSetting quickSetup=new com.nezurstandalone.settings.ModeSetting("Quick Setup","Custom","Custom","Conservative","Balanced","Aggressive");
    private Module combatOptionsOwner;
    private com.nezurstandalone.combat.CombatOptions borrowedCombatOptions;
    private long combatOptionsSession;
    public final void claimCombatOptions(Module owner,com.nezurstandalone.combat.CombatOptions options){
        combatOptionsOwner=owner;borrowedCombatOptions=options;combatOptionsSession=com.nezurstandalone.control.ClientSession.current();
    }
    public final void releaseCombatOptions(Module owner){if(combatOptionsOwner==owner){combatOptionsOwner=null;borrowedCombatOptions=null;combat.resetTargeting();}}
    private com.nezurstandalone.combat.CombatOptions activeCombatOptions(){
        return combatOptionsOwner!=null&&combatOptionsOwner.isToggled()&&combatOptionsSession==com.nezurstandalone.control.ClientSession.current()&&borrowedCombatOptions!=null?borrowedCombatOptions:combatOptions;
    }
    @Override public boolean isSettingVisible(com.nezurstandalone.settings.Setting setting){
        if(!setting.isVisible())return false;
        if(advancedSettings.enabled)return true;
        String name=setting.name;
        return setting==keybind||setting==advancedSettings||setting==quickSetup||name.startsWith("KillAura ")||name.equals("Combat Preset")||name.equals("Use KillAura")
                ||name.equals("CPS Min")||name.equals("CPS Max")||name.equals("Attack Reach")||name.equals("Combat Aim Speed")||name.equals("Team Check (TDM)")
                ||setting==autoWeapon||setting==idleRetreat||setting==onlyEventMode||setting==aimSpeed||setting==oofOnOverdrive
                ||setting==autoPerk||setting==perk1||setting==perk2||setting==perk3||setting==perk4||setting==minMidPlayers||setting==lobbySwap
                ||setting==showHud||setting==hudScale||name.equals("Hide in HUD");
    }
    private final SpireTargeting<net.minecraft.entity.EntityLivingBase> spireTargeting=new SpireTargeting<>();
    private java.util.function.Function<java.util.List<com.nezurstandalone.combat.AuraTargeting.Candidate<net.minecraft.entity.EntityLivingBase>>,net.minecraft.entity.EntityLivingBase> grinderTargetSelector;
    private final com.nezurstandalone.combat.GrinderCombat combat = new com.nezurstandalone.combat.GrinderCombat(this,this::mayFight,this::allowsCombatTarget,this::combatAimSpeed,this::activeCombatOptions)
            .targetSelector(this::selectGrinderTarget).targetSelectorWhen(this::customGrinderSelection);
    private Module aimSpeedOwner;
    private java.util.function.DoubleSupplier aimSpeedOverride;
    private long aimSpeedSession;

    /** Contractor supplies its live camera preference without overwriting Grinder's saved value. */
    public final void claimCombatAimSpeed(Module owner,java.util.function.DoubleSupplier speed) {
        aimSpeedOwner=owner;aimSpeedOverride=speed;aimSpeedSession=com.nezurstandalone.control.ClientSession.current();
    }

    public final void releaseCombatAimSpeed(Module owner) {
        if(aimSpeedOwner==owner){aimSpeedOwner=null;aimSpeedOverride=null;}
    }

    private double combatAimSpeed() {
        if(aimSpeedOwner!=null && aimSpeedOwner.isToggled() && aimSpeedOverride!=null
                && aimSpeedSession==com.nezurstandalone.control.ClientSession.current())return aimSpeedOverride.getAsDouble();
        return aimSpeed.value;
    }

    private boolean mayFight() {
        return combatPermitted && isToggled() && correctionTick < 0 && !isOnBreak && !hasDragonEggPriority() && !isAutoBlockheadActive()
                && (!hasTemporaryPerkLease() || !contractKungFuRestoring && contractKungFuReady)
                && PathfinderManager.available(this) && isCombatState(currentState);
    }

    private boolean allowsCombatTarget(EntityPlayer player) {
        boolean region=currentState==State.SPIRE_FIGHTING
                ? PitCombatArea.sameSpireFloor(mc.thePlayer.posY,player.posY)
                : isRagePitEventActive() ? !Utils.isInSpawn() && !safeCombatZone(player)
                : PitMapManager.isInCombatArea(player.posX,player.posY,player.posZ);
        return region && (currentState != State.ROBBERY_FIGHTING || robbery.canTarget(player)) && tdmAllows(player);
    }

    private boolean safeCombatZone(EntityPlayer player) {
        String zone=PitMapManager.getZone(player.posX,player.posY,player.posZ);
        return zone.equals("Spawn") || zone.equals("Overspawn") || zone.equals("Sewer");
    }

    public static boolean allowsCombatJump() {
        return isRunning() && instance.combatPermitted && instance.mc.thePlayer!=null
                && (instance.currentState==State.SPIRE_FIGHTING
                    || PitMapManager.isInCombatArea(instance.mc.thePlayer.posX,instance.mc.thePlayer.posY,instance.mc.thePlayer.posZ));
    }

    private boolean mayReturnToMid() {
        return PitCombatArea.mayReturn(isRagePitEventActive(),
                "Pit".equals(PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)),
                PitMapManager.isInCombatArea(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ));
    }

    private boolean customGrinderSelection() {
        return currentState==State.SPIRE_FIGHTING || refreshTdmContext() || currentState==State.FIGHTING && grinderTargetSelector!=null;
    }
    private net.minecraft.entity.EntityLivingBase selectGrinderTarget(java.util.List<com.nezurstandalone.combat.AuraTargeting.Candidate<net.minecraft.entity.EntityLivingBase>> candidates) {
        if(currentState==State.SPIRE_FIGHTING)return spireTargeting.select(candidates,activeCombatOptions().range());
        if(refreshTdmContext())return tdmAssists.select(candidates,activeCombatOptions().range());
        spireTargeting.reset();return grinderTargetSelector==null?null:grinderTargetSelector.apply(candidates);
    }

    private static boolean isCombatState(State state) {
        return state == State.FIGHTING || state == State.RETURNING_TO_MID || state == State.SPIRE_FIGHTING
                || state == State.ROBBERY_FIGHTING;
    }

    // ---- core state ---------------------------------------------------------
    public State currentState = State.IDLE;
    private long oofCooldown = 0L;
    private long lobbyStamp = 0L;
    private long reconnectStamp = 0L;
    private boolean lobbyFirstCommand = false;
    private boolean limboSentOnce = false;
    private long throttleCooldownEnd = 0L;
    private long screenOpenStamp = 0L;
    private long lastMidScan = 0L;
    private int cachedMidCount = 0;
    private long combatRepathAt = 0L;
    private long midRepathAt = 0L;
    private double combatMoveLastX = 0, combatMoveLastZ = 0;
    private long combatMoveProgressAt = 0L;
    private long combatMoveStuckSince = 0L;
    // When the stuck-handoff path comes back empty (A* can't route through a crowded mid),
    // fall back to the direct chase instead of standing still re-requesting the same failing
    // path every 600 ms - that was the "aims and clicks but never moves" state.
    private boolean combatPathFailed = false;
    private boolean wasDeadLastTick = false;
    private long lastAttackTime = 0L;
    private long otherZoneEntryTime = 0L;
    private long spawnWalkStart = 0L;
    // Spawn-walk bhop rhythm state (see handleSpawn).
    private boolean wasOnGroundLastTick = false;
    private int bhopDelayTicks = 0;
    private boolean bhopSkipBeat = false;
    private final java.util.Random spawnBhopRandom = new java.util.Random();
    private boolean prevWalkerSprint = false;
    private long lastBreakEndTime = 0L;
    private boolean isOnBreak = false;

    // mystic
    private long lastMysticChatTime = 0L;
    private long lastMysticCountMs = 0L;
    private long lastMysticTraceTime = 0L;
    private int mysticTargetId = -1;
    private GuiInventory mysticStowScreen;
    private int mysticStowSlot = -1;
    private long mysticStowStarted;
    private boolean mysticStowAll;
    private long mysticStowRetryAt;
    private double mysticDropX, mysticDropY, mysticDropZ;
    public int sessionMystics = 0;
    public int sessionXP = 0;
    public double sessionGold = 0;
    public double sessionRenown = 0;
    private static final Pattern XP_PATTERN = Pattern.compile("\\+([0-9,]+)\\s*XP", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOLD_PATTERN = Pattern.compile("\\+([0-9.,]+)\\s*(g|gold)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RENOWN_PATTERN = Pattern.compile("\\+([0-9.,]+)\\s*Renown", Pattern.CASE_INSENSITIVE);

    // mute
    private float originalVolume = -1f;
    private boolean isMuted = false;

    // spire
    private boolean spireActive = false;
    private long spireActiveStartTime = 0L;
    private boolean spireFocusPaused = false;
    private long spireEndStamp = 0L;
    private long lastCenterUpdate = 0L;
    private double centerX = 0, centerZ = 0;

    // level tracking for perk updates
    private int lastSeenLevel = -1;

    // squads
    private boolean lowMidTracking = false;
    private long lowMidStamp = 0L;
    private boolean squadSwapForced = false;

    // night quest
    public boolean nightQuestActive = false;
    private long nightQuestStartTime = 0L;
    private long nextNightQuestTime = 0L;
    public int sessionNightQuests = 0;

    // ---- HUD ---------------------------------------------------------------
    private long sessionStartTime = 0L;
    private final GrinderHudStats hudStats=new GrinderHudStats();
    private com.nezurstandalone.gui.GrinderDashboard.Snapshot hudSnapshot;
    private long hudSnapshotAt;
    private Object hudSnapshotWorld;
    private State hudSnapshotState;
    private long hudFadeStart;

    // perk / prestige NPC interaction
    private int npcPosIndex = 0;

    // ---- Perk selection -----------------------------------------------------
    private static final class Perk {
        final String name; final int gold; final int renown; final int minLevel;
        Perk(String n, int g, int r, int lv) { name = n; gold = g; renown = r; minLevel = lv; }
    }
    private static final Perk[] PERKS = {
        new Perk("Golden Heads", 500, 0, 10), new Perk("Fishing Rod", 1000, 0, 10),
        new Perk("Lava Bucket", 1000, 0, 10), new Perk("Strength-Chaining", 2000, 0, 20),
        new Perk("Safety First", 3000, 0, 20), new Perk("Mineman", 3000, 0, 30),
        new Perk("Bonk!", 2000, 0, 35), new Perk("Trickle-down", 1000, 0, 40),
        new Perk("Lucky Diamond", 4000, 0, 40), new Perk("Spammer", 4000, 0, 40),
        new Perk("Bounty Hunter", 2000, 0, 50), new Perk("Streaker", 8000, 0, 50),
        new Perk("Gladiator", 4000, 0, 60), new Perk("Vampire", 4000, 0, 60),
        new Perk("Barbarian", 3000, 10, 30), new Perk("Co-op Cat", 6000, 10, 50),
        new Perk("Overheal", 6000, 10, 70), new Perk("Assistant (to the) Streaker", 8000, 15, 50),
        new Perk("Rambo", 6000, 15, 70), new Perk("Dirty", 8000, 15, 80),
        new Perk("Marathon", 8000, 20, 90), new Perk("Olympus", 6000, 20, 70),
        new Perk("Recon", 6000, 20, 60), new Perk("First Strike", 8000, 25, 80),
        new Perk("Soup", 8000, 30, 90), new Perk("Conglomerate", 20000, 40, 50),
        new Perk("Kung Fu Knowledge", 10000, 40, 100), new Perk("Thick", 10000, 45, 90)
    };
    // Killstreaks share the perk menus' whole vocabulary, so they reuse the Perk record. Levels
    // are the ones the player supplied; entries only seen in a menu dump get level 0 so they are
    // always listed and the lore gate decides. "The Way" does NOT waive killstreak level limits.
    private static final Perk[] KILLSTREAKS = {
        new Perk("Second Gapple", 1500, 0, 10), new Perk("Explicious", 3000, 0, 20),
        new Perk("Tough Skin", 3000, 0, 30), new Perk("Feast", 4000, 0, 30),
        new Perk("R&R", 4000, 0, 40), new Perk("Counter-Strike", 5000, 0, 40),
        new Perk("Monster", 10000, 0, 40), new Perk("Arquebusier", 5000, 0, 50),
        new Perk("Fight or Flight", 5000, 0, 50), new Perk("Tactical Retreat", 5000, 0, 50),
        new Perk("Pungent", 5000, 0, 50), new Perk("Aura of Protection", 8000, 0, 50),
        new Perk("Gold Nano-factory", 6000, 0, 50), new Perk("Glass Pickaxe", 6000, 0, 60),
        new Perk("Ice Cube", 9000, 0, 60), new Perk("Khanate", 6000, 0, 60),
        new Perk("Spongesteve", 12000, 0, 70), new Perk("Hero's Haste", 15000, 0, 100),
        new Perk("Rush", 25000, 0, 110), new Perk("Beastmode", 10000, 0, 30),
        new Perk("Hermit", 20000, 0, 50), new Perk("Highlander", 30000, 0, 60),
        new Perk("Magnum Opus", 40000, 0, 70), new Perk("To The Moon", 50000, 0, 80),
        new Perk("Uberstreak", 50000, 0, 90), new Perk("Super Streaker", 20000, 0, 80),
        new Perk("Gold Stack", 25000, 0, 90), new Perk("XP Stack", 25000, 0, 90),
        new Perk("Leech", 6000, 0, 70), new Perk("Assured Strike", 10000, 0, 80),
        new Perk("Apostle to RNGesus", 50000, 0, 100)
    };
    private static final String[] ALL_KS_OPTIONS;
    private static final String[] ALL_OPTIONS;
    static {
        ALL_OPTIONS = new String[PERKS.length + 1];
        ALL_OPTIONS[0] = "No Perk";
        for (int i = 0; i < PERKS.length; i++) ALL_OPTIONS[i + 1] = PERKS[i].name;
        ALL_KS_OPTIONS = new String[KILLSTREAKS.length + 1];
        ALL_KS_OPTIONS[0] = "No Perk";
        for (int i = 0; i < KILLSTREAKS.length; i++) ALL_KS_OPTIONS[i + 1] = KILLSTREAKS[i].name;
    }
    private final com.nezurstandalone.settings.PerkSetting perk1 = new com.nezurstandalone.settings.PerkSetting("Perk 1 (Lv 10)", "No Perk", ALL_OPTIONS);
    private final com.nezurstandalone.settings.PerkSetting perk2 = new com.nezurstandalone.settings.PerkSetting("Perk 2 (Lv 35)", "No Perk", ALL_OPTIONS);
    private final com.nezurstandalone.settings.PerkSetting perk3 = new com.nezurstandalone.settings.PerkSetting("Perk 3 (Lv 70)", "No Perk", ALL_OPTIONS);
    private final com.nezurstandalone.settings.PerkSetting perk4 = new com.nezurstandalone.settings.PerkSetting("Perk 4 (Lv 100)", "No Perk", ALL_OPTIONS);
    private final BooleanSetting theWay = new BooleanSetting("The Way (no level req)", false);
    private final BooleanSetting autoKillstreak = new BooleanSetting("Auto Killstreak", false);
    private final com.nezurstandalone.settings.PerkSetting ks1 = new com.nezurstandalone.settings.PerkSetting("Killstreak 1 (Lv 10)", "No Perk", ALL_KS_OPTIONS);
    private final com.nezurstandalone.settings.PerkSetting ks2 = new com.nezurstandalone.settings.PerkSetting("Killstreak 2 (Lv 75)", "No Perk", ALL_KS_OPTIONS);
    private boolean perksNeedSync = false;
    // Contract-owned temporary perk lease. NO_PERK clears all slots; Kung Fu replaces one safe slot.
    private boolean contractGappleRequested, contractGappleRemove;
    private boolean contractKungFuRequested, contractNoPerkRequested, contractKungFuRestoring, contractKungFuReady;
    private boolean contractKungFuUnavailable, contractKungFuStopAfterRestore;
    private int contractKungFuSlot = -1;
    private String[] contractKungFuBefore;
    private long contractPerkTravelAt, contractNoPerkVerifyAt;

    // Perk sync runs once per ACCOUNT, not once per enable: re-toggling the module mid-session
    // must not walk back to the NPC when everything is already equipped. Keyed on the session
    // username, so switching accounts re-checks.
    private String lastPerkUser = null;
    private final boolean[] perkAttempted = new boolean[4];
    private String targetPerkName = "No Perk";
    private long interactStamp = 0L;
    private long navRepathAt = 0L;
    private long npcSpotStartedAt;

    private final LoadoutPlanner loadout = new LoadoutPlanner(this::log);
    private final LoadoutGoldObserver loadoutGold = new LoadoutGoldObserver();
    private final LoadoutConfirmation loadoutConfirmation = new LoadoutConfirmation();
    private final LoadoutContext loadoutContext=new LoadoutContext();
    private Object loadoutWorld;
    private boolean managedVisit;
    private boolean loadoutSettingsDirty=true;
    private java.util.List<String> lastLoadoutLines=java.util.Collections.emptyList();
    private LoadoutPlanner.Slot chooserSlot;

    private void refreshLoadoutGold() {
        net.minecraft.client.network.NetHandlerPlayClient connection=mc.getNetHandler();
        net.minecraft.scoreboard.ScoreObjective objective=mc.theWorld==null?null:Utils.displayedSidebar(mc.theWorld.getScoreboard());
        boolean connected=connection!=null && connection.getNetworkManager()!=null
                && connection.getNetworkManager().isChannelOpen() && mc.thePlayer!=null;
        loadoutGold.observe(loadout,mc.theWorld,connection,objective,connected,
                isPitScoreboardTitle(Utils.getScoreboardTitle()),Utils.getScoreboardLines(),com.nezurstandalone.control.Clock.millis());
    }

    private void refreshLoadoutRequirements() {
        if(mc.thePlayer==null || mc.theWorld==null)return;
        String title=Utils.getScoreboardTitle();
        if(!isPitScoreboardTitle(title))return;
        if(loadoutWorld!=mc.theWorld){
            loadout.invalidateGold();loadoutWorld=mc.theWorld;
            lastLoadoutLines=java.util.Collections.emptyList();loadoutSettingsDirty=true;
        }
        loadout.suspend(hasTemporaryPerkLease());
        java.util.List<String> lines=Utils.getScoreboardLines();
        if(!loadoutSettingsDirty && lines.equals(lastLoadoutLines))return;
        loadoutSettingsDirty=false;lastLoadoutLines=lines;
        loadoutContext.observe(loadout,mc.theWorld,lastPerkUser,title,lines);
        loadout.requirements(Utils.getLevel(),theWay.isEnabled());
        loadout.suspend(hasTemporaryPerkLease());
        com.nezurstandalone.settings.PerkSetting[] perks=perkBoxes(),streaks=ksBoxes();
        for(int i=0;i<perks.length;i++)loadout.configure(LoadoutPlanner.Kind.PERK,i+1,perks[i].getMode(),autoPerk.isEnabled());
        for(int i=0;i<streaks.length;i++)loadout.configure(LoadoutPlanner.Kind.KILLSTREAK,i+1,streaks[i].getMode(),autoKillstreak.isEnabled());
    }

    private void observeLoadoutPacket(int window,ContainerChest container,ItemStack[] stacks,int index) {
        if(mc.thePlayer==null || mc.theWorld==null || !mc.isCallingFromMinecraftThread()
                || mc.thePlayer.openContainer!=container || container.windowId!=window
                || !isPitScoreboardTitle(Utils.getScoreboardTitle()))return;
        int size=container.getLowerChestInventory().getSizeInventory();
        if(index>=size||index< -1)return; // Player inventory updates are not menu evidence.
        refreshPerkAccount();refreshLoadoutRequirements();
        String kind=menuKind(container.getLowerChestInventory().getDisplayName().getUnformattedText());
        if(isToggled() && currentState==State.PRESTIGE_CLICKING_GUI
                && !isClassicPitTitle(Utils.getScoreboardTitle()) && kind.equals("confirm")) {
            String title=container.getLowerChestInventory().getDisplayName().getUnformattedText();
            for(int i=0;i<(index<0?Math.min(stacks.length,size):stacks.length);i++) {
                ItemStack stack=stacks[i];
                if(stack!=null && PrestigeConfirmation.hypixelButton(title,stack.getDisplayName(),
                        net.minecraft.item.Item.getIdFromItem(stack.getItem()),stack.getMetadata(),loreOf(stack))) {
                    menuTransition.serverContents(container);
                    break;
                }
            }
        }
        if(managedVisit) {
            for(int i=0;i<(index<0?Math.min(stacks.length,size):stacks.length);i++) {
                if(stacks[i]!=null&&LoadoutMenus.responseEvidence(kind,targetPerkName,stacks[i].getDisplayName(),loreOf(stacks[i]))){
                    menuTransition.serverContents(container);
                    if(kind.equals("confirm"))loadoutConfirmation.contents(container);
                    break;
                }
            }
        }
        LoadoutPlanner.Kind type;
        boolean root=kind.equals("upgrades")||kind.equals("killstreaks");
        if(kind.equals("upgrades")||kind.equals("choosePerk"))type=LoadoutPlanner.Kind.PERK;
        else if(kind.equals("killstreaks")||kind.equals("chooseStreak"))type=LoadoutPlanner.Kind.KILLSTREAK;
        else return;
        boolean[] seen=new boolean[type==LoadoutPlanner.Kind.PERK?4:2];
        boolean anyResolved=false;
        int count=index<0?Math.min(stacks.length,container.getLowerChestInventory().getSizeInventory()):1;
        for(int i=0;i<count;i++) {
            ItemStack stack=stacks[i];if(stack==null)continue;
            String name=StringUtils.stripControlCodes(stack.getDisplayName()).trim();
            java.util.List<String> lore=loreOf(stack);
            if(root) {
                int n=LoadoutMenus.slotNumber(type,name);
                if(n<1||n>seen.length)continue;
                seen[n-1]=true;
                if(LoadoutPlanner.resolved(lore)){
                    anyResolved=true;loadout.observeSlot(type,n,lore,com.nezurstandalone.control.Clock.millis());
                    if(!managedVisit && !hasTemporaryPerkLease())loadout.retryVerifiedObservation(type,n);
                }
            } else loadout.observeChoice(type,name,lore);
        }
        // Empty/streaming packets are not proof that a profile lacks slots.
        if(root && index<0 && anyResolved)for(int i=0;i<seen.length;i++)if(!seen[i])loadout.missingSlot(type,i+1);
    }

    private boolean managedActionReady() {
        refreshLoadoutRequirements();
        refreshLoadoutGold();
        return loadout.next(null,com.nezurstandalone.control.Clock.millis())!=null;
    }

    private void handleManagedRoot(ContainerChest container,IInventory inv,LoadoutPlanner.Kind kind) {
        long now=com.nezurstandalone.control.Clock.millis();
        if(loadout.pending()!=null) {
            // Menu return/ACK alone is insufficient. Only packet Selected lore completes the action.
            if(loadout.pending().state==LoadoutPlanner.State.VERIFYING) {
                if(System.nanoTime()-guiOpenStamp<1_500_000_000L)return;
                failMenu("Requested loadout selection was not confirmed by server slot lore.");return;
            }
            loadout.blocked(now,"chooser returned without a verified action");
        }
        chooserSlot=null;
        loadoutConfirmation.clear();
        LoadoutPlanner.Slot next=loadout.next(null,now);
        if(next!=null && next.kind==kind) {
            int slot=findManagedSlotIndex(inv,kind,next.number);
            if(slot<0){loadout.missingSlot(kind,next.number);return;}
            java.util.List<String> lore=loreOf(inv.getStackInSlot(slot));
            if(!LoadoutPlanner.resolved(lore)){failMenu("Server slot lore is unresolved.");return;}
            // Cached equipment must agree with the live slot before a chooser is opened.
            if(!LoadoutPlanner.selected(lore).equalsIgnoreCase(next.equipped)){
                failMenu("Live equipment differs from authoritative cached slot evidence.");return;
            }
            if(!loadout.begin(next,now))return;
            chooserSlot=next;targetPerkName=next.desired;
            clickMenu(container,slot,kind==LoadoutPlanner.Kind.PERK?"choosePerk":"chooseStreak");return;
        }
        LoadoutPlanner.Kind other=kind==LoadoutPlanner.Kind.PERK?LoadoutPlanner.Kind.KILLSTREAK:LoadoutPlanner.Kind.PERK;
        if(loadout.next(other,now)!=null) {
            int route=findSlotByName(inv,kind==LoadoutPlanner.Kind.PERK?"Killstreaks":"Go Back");
            if(route<0){failMenu("Cannot identify loadout menu route.");return;}
            clickMenu(container,route,kind==LoadoutPlanner.Kind.PERK?"killstreaks":"upgrades");return;
        }
        finishNpcFlow();
    }

    private void handleManagedChooser(ContainerChest container,IInventory inv,String noneName) {
        refreshLoadoutGold();
        long now=com.nezurstandalone.control.Clock.millis();
        LoadoutPlanner.Slot operation=loadout.pending();
        if(operation==null || operation!=chooserSlot || !operation.desired.equalsIgnoreCase(targetPerkName)){
            finishNpcFlow();return;
        }
        if(operation.state==LoadoutPlanner.State.VERIFYING) {
            if(loadout.choiceSelectedAfterDispatch()) {
                int back=findSlotByName(inv,"Go Back");
                if(back<0){failMenu("Selection acknowledged; cannot return to verify the configured slot.");return;}
                clickMenu(container,back,"upgrades","killstreaks");return;
            }
            if(!loadout.prepareOwnedSelection(now)) {
                failMenu("Chooser returned without a verified free selection or equipped result.");return;
            }
            log("Purchase ownership verified; equipping "+targetPerkName+" for 0g.");
        }
        int found=-1;
        String wanted=targetPerkName.equalsIgnoreCase("No Perk")?noneName:targetPerkName;
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack item=inv.getStackInSlot(i);if(item==null)continue;
            if(StringUtils.stripControlCodes(item.getDisplayName()).trim().equalsIgnoreCase(wanted)){found=i;break;}
        }
        if(found<0){loadout.choiceMissing(operation.kind,targetPerkName);loadout.blocked(now,"exact configured choice absent");}
        else {
            java.util.List<String> lore=loreOf(inv.getStackInSlot(found));
            // Re-read the exact live entry even if earlier packet evidence differed.
            loadout.inspectChoice(operation.kind,wanted,lore);
            if(loadout.canApply(now)) {
                // Reserve only after the menu/cursor/lease guards allow native dispatch.
                clickMenu(container,found,()->{
                    loadout.applied(com.nezurstandalone.control.Clock.millis());
                    loadoutConfirmation.begin(operation,container);
                },LoadoutMenus.afterSelection(operation.kind));
                return;
            }
            loadout.blocked(now,"live price, gold or availability prevents this action");
        }
        int back=findSlotByName(inv,"Go Back");
        if(back>=0)clickMenu(container,back,"upgrades","killstreaks");else finishNpcFlow();
    }

    private void handleManagedConfirm(ContainerChest container,IInventory inv) {
        refreshLoadoutGold();
        LoadoutPlanner.Slot operation=loadout.pending();
        if(operation==null || operation!=chooserSlot){finishNpcFlow();return;}
        if(!loadoutConfirmation.matches(operation,container)) {
            failMenu("Confirmation is not the server response to the configured chooser selection.");return;
        }
        loadout.confirmationOpened();
        if(!loadout.canConfirm(com.nezurstandalone.control.Clock.millis())){
            failMenu("Gold or eligibility changed before loadout confirmation.");return;
        }
        int confirm=findSlotByName(inv,"Confirm");
        if(confirm<0){failMenu("Loadout confirmation is not identifiable.");return;}
        // A generic Confirm need not repeat the item name: the chooser/server transition binds it.
        java.util.List<String> lore=loreOf(inv.getStackInSlot(confirm));
        if(!loadout.confirmationPriceMatches(lore)){
            failMenu("Confirmation price differs from the validated selection.");return;
        }
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack st=inv.getStackInSlot(i);
            if(st==null)continue;
            String name=LoadoutPlanner.normalize(StringUtils.stripControlCodes(st.getDisplayName()).trim());
            if(!LoadoutConfirmation.labelsMatch(targetPerkName,loreOf(st))) {
                failMenu("Confirmation identifies another loadout selection.");return;
            }
            if(loadout.entry(operation.kind,name)!=null&&!name.equalsIgnoreCase(targetPerkName)) {
                failMenu("Confirmation preview identifies another loadout selection.");return;
            }
            if(name.equalsIgnoreCase(targetPerkName)) {
                if(!loadout.confirmationPriceMatches(loreOf(st))){failMenu("Confirmed item's price differs from the validated choice.");return;}
            }
        }
        // Commit before native dispatch; a subsequent lost response remains an ambiguous purchase.
        if(clickMenu(container,confirm,()->loadout.confirmed(com.nezurstandalone.control.Clock.millis()),LoadoutMenus.afterConfirmation(operation.kind)))loadoutConfirmation.clear();
    }

    private void refreshPerkAccount() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        String host = mc.getCurrentServerData() == null ? "local" : mc.getCurrentServerData().serverIP;
        String identity = mc.thePlayer.getUniqueID().toString() + "@" + host;
        if (identity.equals(lastPerkUser)) return;
        lastPerkUser = identity;
        loadoutSettingsDirty=true;
        loadout.context(identity);
        chooserSlot=null;
        prestigeGold.clear();
        contractKungFuUnavailable = false;
        lastSeenLevel = -1;
        perksNeedSync = true;
        npcRetryAfterNanos = 0L;
        java.util.Arrays.fill(perkAttempted, false);
    }

    public static boolean isNpcNavigationActive() {
        return isRunning() && (instance.currentState == State.PERK_NAVIGATING
                || instance.currentState == State.PRESTIGE_NAVIGATING);
    }

    private void advanceNpcSpot(boolean prestige) {
        GuardedInput.cancel(interactionOwner);
        RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
        PathfinderManager.clear(this, true);
        npcPosIndex++;
        navRepathAt = 0L;
        npcSpotStartedAt = 0L;
        if (npcPosIndex >= NPC_OFFSETS.length) {
            failMenu("NPC approach exhausted; retry deferred.");
            return;
        }
        currentState = prestige ? State.PRESTIGE_NAVIGATING : State.PERK_NAVIGATING;
    }

    private boolean prestigeConfirmStarted = false;
    private long prestigeConfirmStamp = 0L;
    private final com.nezurstandalone.control.MessageOperation prestigeMessage=new com.nezurstandalone.control.MessageOperation(),questMessage=new com.nezurstandalone.control.MessageOperation();
    private long prestigeMessageId,questMessageId;
    // After a prestige the XP/level does not refresh until a server swap, so we /l then /play pit
    // when we see our own prestige chat line. Pending blocks a re-prestige on the stale 120 level.
    private boolean prestigeSwapPending = false;
    private boolean prestigeLeftPit = false;
    private long prestigeSwapStart = 0L;
    // Classic stays on one Pit server; a confirmed scoreboard level reset, not a lobby swap,
    // completes its prestige. Keep a pending barrier so a stale level 120 cannot retry.
    private boolean classicPrestigePending = false;
    private final PrestigeGoldProgress prestigeGold = new PrestigeGoldProgress();
    private long prestigeGoldLogAt;
    private long classicPrestigeStart = 0L;
    // Rate-limited, humanised right-clicks for NPC interacts and the dragon egg, plus a spacing
    // stamp so GUI slot clicks are never machine-gunned at the server.
    private final ClickSimulator.Rate npcClickRate = new ClickSimulator.Rate(() -> 3.0);
    private final ClickSimulator.Rate eggClickRate = new ClickSimulator.Rate(() -> 4.0);

    /**
     * Spacing between container clicks in the perk/prestige menus, sampled per click.
     *
     * <p>The old fixed 500 ms gap put a single spike at exactly 500 ms in the inter-click
     * histogram - the metronome signature nothing with a hand behind it produces. This samples a
     * log-normal about a 500 ms median (sigma 0.30) clamped to 280-1100 ms: right-skewed like real
     * motor intervals, with an occasional long look-at-the-menu pause and no hard periodicity.
     */
    private long nextGuiClickDelay() {
        double v = 500.0 * Math.exp(java.util.concurrent.ThreadLocalRandom.current().nextGaussian() * 0.30);
        return (long) Math.max(280.0, Math.min(1100.0, v));
    }

    // dragon egg
    private BlockPos currentEgg = null;
    private BlockPos lastEggPos = null;
    private long eggScanStamp = 0L;
    private long eggStationaryStart = 0L;
    private long eggNavRepathAt = 0L;
    private BlockPos eggBlacklistPos = null;
    private long eggBlacklistUntil = 0L;
    private long eggUnstuckUntil = 0L;
    private int eggUnstuckAttempts = 0;
    private static final String EGG_MOVEMENT_OWNER = "grinder-egg";
    private double eggApproachBestDistance = Double.MAX_VALUE;
    private long mysticRepathAt = 0L;
    private boolean sawMysticItem = false;
    private ItemStack mysticPickupStack;
    private int mysticInventoryBefore;
    private long mysticMissingSince;
    private long guiOpenStamp = 0L; // monotonic timestamp of the last observed menu change
    private String lastMenuTitle = ""; // last menu title seen, to give each sub-menu its own settle

    private static final Pattern SPIRE_TIME = Pattern.compile("STARTING IN (\\d+):(\\d+)");
    private static final Pattern SQUAD_RANK = Pattern.compile("#(\\d+)");
    // Ring of standing spots, ~2 blocks out, cycled through when a menu fails to open.
    private static final double[][] NPC_OFFSETS = {
            {0, 2}, {2, 0}, {0, -2}, {-2, 0}, {1.6, 1.6}, {-1.6, 1.6}, {1.6, -1.6}, {-1.6, -1.6}
    };

    protected GrinderEngine() {
        super("AutoGrinder", "Autonomously grinds the Pit: fights mid, swaps thin lobbies, routes events.",
                Category.AUTO);
        instance = this;
        GuardedInput.label(interactionOwner,"AutoGrinder.Interaction");
        GuardedInput.label(raffleInputOwner,"AutoGrinder.Raffle");
        for(Perk p:PERKS)loadout.define(LoadoutPlanner.Kind.PERK,p.name,p.gold,p.minLevel,p.renown);
        for(Perk p:KILLSTREAKS)loadout.define(LoadoutPlanner.Kind.KILLSTREAK,p.name,p.gold,p.minLevel,p.renown);
        com.nezurstandalone.control.SessionResets.register(this,this::resetSessionActivity);
        addSettings(quickSetup,advancedSettings);
        addSettings(combatOptions.settings());
        quickSetup.addChangeListener(changed->{
            if(com.nezurstandalone.settings.Setting.isRestoring())return;
            String profile=quickSetup.getMode();if("Custom".equals(profile))return;
            combatOptions.useKillAura.setEnabled(true);combatOptions.preset.setMode(profile);
            autoWeapon.setEnabled(true);idleRetreat.setEnabled(true);onlyEventMode.setEnabled(false);
            AutoHeal healing=com.nezurstandalone.Nezur.moduleManager==null?null:com.nezurstandalone.Nezur.moduleManager.getModuleByClass(AutoHeal.class);
            if(healing!=null&&!healing.isToggled()){
                if(mc.thePlayer==null||mc.theWorld==null)healing.setToggledFromConfig(true);
                else healing.setToggled(true);
            }
        });
        addSettings(minMidPlayers, lobbySwap, lobbySwapDelay, autoWeapon, mysticPickup, idleRetreat,
                spireMode, autoBlockhead, autoRobbery, autoRaffle, squadSupport, nightQuestSupport, autoPerk, perk1, perk2, perk3, perk4, theWay,
                autoKillstreak, ks1, ks2,
                autoPrestige, oofOnOverdrive, dragonEggSupport, muteGame,
                breakTimeEnable, workDurationLimit, breakDurationLimit,
                aimSpeed, interactAimSpeed, onlyEventMode, showHud, hudScale, hudAnimations, debugLogging);
        for (final com.nezurstandalone.settings.PerkSetting b : perkBoxes()) {
            b.visibleOptions = () -> visiblePerksFor(b);
            b.addChangeListener(s -> loadoutSettingsDirty=true);
        }
        for (final com.nezurstandalone.settings.PerkSetting b : ksBoxes()) {
            b.visibleOptions = () -> visibleStreaksFor(b);
            b.addChangeListener(s -> loadoutSettingsDirty=true);
        }
        autoPerk.addChangeListener(s -> {updatePerkVisibility();loadoutSettingsDirty=true;});
        autoKillstreak.addChangeListener(s -> {updatePerkVisibility();loadoutSettingsDirty=true;});
        theWay.addChangeListener(s -> loadoutSettingsDirty=true);
        updatePerkVisibility();
    }

    private void resetSessionActivity(){
        mysticPickupPending=false;mysticPendingWorld=null;mysticPickupStarted=0;
        if(tdmWorld!=mc.theWorld||!CombatAura.isTdmEventActive()){
            tdmAssists.reset();tdmWasActive=false;tdmWorld=tdmPlayer=null;tdmSession=-1;
        }else tdmAssists.invalidatePending();

        hudSnapshot=null;hudSnapshotAt=0;hudFadeStart=0;
        loadoutGold.clear();
        overdriveOof.clear();
        loadout.interrupt(com.nezurstandalone.control.Clock.millis(),"automation session reset");
        loadout.invalidateGold();managedVisit=false;chooserSlot=null;
        prestigeGold.suspend();
        blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
        pauseGameplay();resetMenuTracking();combat.resetTargeting();PathfinderManager.clear(this,true);
        currentState=State.IDLE;combatPermitted=false;
        spireActive=false;spireActiveStartTime=0;spireEndStamp=0;lastCenterUpdate=0;centerX=centerZ=0;
        updateSpireFocusPause(false);lowMidTracking=false;lowMidStamp=0;squadSwapForced=false;
        nightQuestActive=false;nightQuestStartTime=nextNightQuestTime=0;questMessage.cancel();
        currentEgg=lastEggPos=eggBlacklistPos=null;eggScanStamp=eggStationaryStart=eggNavRepathAt=eggBlacklistUntil=eggUnstuckUntil=0;eggUnstuckAttempts=0;
        lastMysticChatTime=lastMysticCountMs=mysticRepathAt=0;sawMysticItem=false;
        mysticPickupStack=null;mysticMissingSince=0L;mysticInventoryBefore=0;mysticTargetId=-1;
        mysticStowAll=false;mysticStowRetryAt=0L;
        cancelMysticStow();
        prestigeSwapPending=prestigeLeftPit=prestigeConfirmStarted=classicPrestigePending=false;prestigeSwapStart=prestigeConfirmStamp=classicPrestigeStart=0;
        refreshPerkAccount();java.util.Arrays.fill(perkAttempted,false);
        npcClickRate.reset();eggClickRate.reset();
        com.nezurstandalone.control.CommandCoordinator.cancel(this);
    }
    private com.nezurstandalone.settings.PerkSetting[] perkBoxes() {
        return new com.nezurstandalone.settings.PerkSetting[]{perk1, perk2, perk3, perk4};
    }

    public void requestGappleInspection() {
        if(hasTemporaryPerkLease())return;
        contractGappleRequested=true;contractGappleRemove=false;contractKungFuReady=false;
        contractKungFuUnavailable=false;contractKungFuBefore=null;perksNeedSync=true;
        java.util.Arrays.fill(perkAttempted,false);npcRetryAfterNanos=0;
        currentState=State.IDLE;pauseGameplay();
    }
    public void releaseGappleInspection(){
        if(!contractGappleRequested || contractGappleRemove)return;
        contractGappleRequested=false;contractKungFuReady=false;contractKungFuBefore=null;perksNeedSync=false;
    }
    public boolean gappleInspectionReady(){return contractGappleRequested && contractKungFuBefore!=null;}
    public boolean gappleEligible(){return contractGappleRequested && com.nezurstandalone.contract.ContractPerkPlan.gappleEligible(contractKungFuBefore);}
    public void removeGappleBlockingPerks(){
        if(!gappleEligible() || contractGappleRemove)return;
        contractGappleRemove=true;contractKungFuReady=false;perksNeedSync=true;
        java.util.Arrays.fill(perkAttempted,false);npcRetryAfterNanos=0;currentState=State.IDLE;pauseGameplay();
    }
    public boolean gapplePerksReady(){return contractGappleRequested && contractGappleRemove && contractKungFuReady && !contractKungFuRestoring;}
    public void requestTemporaryKungFu() {
        if (hasTemporaryPerkLease()) return;
        contractKungFuRequested = true; contractKungFuReady = false;
        contractKungFuUnavailable = false; contractKungFuSlot = -1; contractNoPerkVerifyAt = 0L;
        contractKungFuBefore = null; perksNeedSync = true;
        java.util.Arrays.fill(perkAttempted, false);
        npcRetryAfterNanos = 0L;
        currentState = State.IDLE; pauseGameplay();
    }

    public void requestTemporaryNoPerk() {
        if (hasTemporaryPerkLease()) return;
        contractNoPerkRequested = true; contractKungFuReady = false; contractKungFuUnavailable = false;
        contractKungFuSlot = -1; contractNoPerkVerifyAt = 0L; contractKungFuBefore = null; perksNeedSync = true;
        java.util.Arrays.fill(perkAttempted, false); npcRetryAfterNanos = 0L;
        currentState = State.IDLE; pauseGameplay();
    }

    public boolean isTemporaryKungFuReady() { return contractKungFuRequested && contractKungFuReady && !contractKungFuRestoring; }
    public boolean isTemporaryNoPerkReady() { return contractNoPerkRequested && contractKungFuReady && !contractKungFuRestoring; }
    public boolean isTemporaryKungFuUnavailable() { return contractKungFuUnavailable; }
    public boolean isRestoringTemporaryPerk() { return contractKungFuRestoring; }
    public boolean hasTemporaryPerkLease() { return contractGappleRequested || contractKungFuRequested || contractNoPerkRequested || contractKungFuRestoring; }
    public boolean hasTemporaryPerkSnapshot() { return contractKungFuBefore != null; }

    private void contractLog(String message) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                "§8[§dAutoContractor§8] §7" + message));
    }

    public void invalidateTemporaryPerkVerification() {
        if (!hasTemporaryPerkLease() || contractKungFuRestoring) return;
        if ((contractNoPerkRequested || contractGappleRequested) && contractKungFuReady) return;
        contractKungFuReady = false; contractNoPerkVerifyAt = 0L; perksNeedSync = true;
        java.util.Arrays.fill(perkAttempted, false);
        currentState = State.IDLE; pauseGameplay();
    }

    public void restoreTemporaryContractPerks(boolean stopGrinderAfter) {
        if (!hasTemporaryPerkLease()) return;
        contractKungFuStopAfterRestore |= stopGrinderAfter;
        if (contractKungFuBefore == null || (contractKungFuRequested && contractKungFuSlot >= 0
                && "Kung Fu Knowledge".equalsIgnoreCase(contractKungFuBefore[contractKungFuSlot]))) {
            contractGappleRequested=false;contractGappleRemove=false;
            contractKungFuRequested = false; contractNoPerkRequested = false; contractKungFuRestoring = false;
            contractKungFuReady = false; contractKungFuBefore = null; contractNoPerkVerifyAt = 0L;
            return;
        }
        contractKungFuRestoring = true; contractKungFuReady = false;
        perksNeedSync = true; npcRetryAfterNanos = 0L;
        java.util.Arrays.fill(perkAttempted, false);
        currentState = State.IDLE; pauseGameplay();
        contractLog("Restoring previous perk loadout.");
    }

    private String contractDesiredPerk(int slot) {
        if (contractKungFuBefore == null) return perkBoxes()[slot].getMode();
        if(!contractKungFuRestoring && contractGappleRequested && contractGappleRemove
                && com.nezurstandalone.contract.ContractPerkPlan.protectedForKungFu(contractKungFuBefore[slot]))return "No Perk";
        if (!contractKungFuRestoring && contractNoPerkRequested) return "No Perk";
        if (!contractKungFuRestoring && contractKungFuRequested && slot == contractKungFuSlot) return "Kung Fu Knowledge";
        String previous = contractKungFuBefore[slot];
        return previous == null ? "No Perk" : previous;
    }

    /** Snapshot server-observed perks only after all unlocked slots have resolved lore. */
    private boolean inspectContractPerks(IInventory inv) {
        if (!hasTemporaryPerkLease()) return true;
        if (contractKungFuBefore != null) return true;
        int level = Utils.getLevel();
        // The fourth perk slot is account-dependent, not guaranteed by level 100.
        int expected = level >= 70 ? 3 : level >= 35 ? 2 : level >= 10 ? 1 : 0;
        for (int i = 0; i < expected; i++) {
            int slot = findPerkSlotIndex(inv, i + 1);
            if (slot < 0 || !hasResolvedPerkLore(loreOf(inv.getStackInSlot(slot)))) return false;
        }
        for (int i = expected; i < 4; i++) {
            int slot = findPerkSlotIndex(inv, i + 1);
            if (slot >= 0 && !hasResolvedPerkLore(loreOf(inv.getStackInSlot(slot)))) return false;
        }
        if (System.nanoTime() - guiOpenStamp < 1_500_000_000L) return false;

        String[] equipped = new String[4];
        int[] indices = new int[4];
        for (int i = 0; i < 4; i++) {
            indices[i] = findPerkSlotIndex(inv, i + 1);
            if (indices[i] >= 0) equipped[i] = selectedPerkFrom(loreOf(inv.getStackInSlot(indices[i])));
        }
        if(contractGappleRequested){
            contractKungFuBefore=com.nezurstandalone.contract.ContractPerkPlan.snapshot(equipped);
            contractKungFuReady=true;perksNeedSync=false;
            contractLog("GOLDEN_APPLES saved complete server perk loadout; blockers="+gappleEligible());
            for(int i=0;i<equipped.length;i++)if(com.nezurstandalone.contract.ContractPerkPlan.protectedForKungFu(equipped[i]))
                contractLog(equipped[i]+" detected in slot "+(i+1)+"; removal only after GOLDEN_APPLES acceptance.");
            finishNpcFlow();return false;
        }
        if (contractNoPerkRequested) {
            contractKungFuBefore = com.nezurstandalone.contract.ContractPerkPlan.snapshot(equipped);
            contractLog("Saved previous perk loadout.");
            boolean empty = true;
            for (String perk : equipped) if (perk != null) empty = false;
            if (empty) {
                contractKungFuReady = true; perksNeedSync = false;
                contractLog("All perks disabled for NO_PERK contract.");
            }
            return true;
        }
        for (int i = 0; i < 4; i++) if ("Kung Fu Knowledge".equalsIgnoreCase(equipped[i])) {
            contractKungFuBefore = com.nezurstandalone.contract.ContractPerkPlan.snapshot(equipped);
            contractKungFuSlot = i; contractKungFuReady = true; perksNeedSync = false;
            contractLog("Kung Fu Knowledge already equipped in perk slot " + (i + 1) + ".");
            return true;
        }
        com.nezurstandalone.settings.PerkSetting[] configured = perkBoxes();
        String[] wanted = new String[4]; boolean[] usable = new boolean[4];
        for (int i = 0; i < 4; i++) {
            wanted[i] = configured[i].getMode();
            usable[i] = indices[i] >= 0 && !(equipped[i] == null
                    && loreHas(loreOf(inv.getStackInSlot(indices[i])), "Required level"));
        }
        int candidate = com.nezurstandalone.contract.ContractPerkPlan.kungFuSlot(equipped, wanted, usable);
        if (candidate >= 0) {
            contractKungFuBefore = com.nezurstandalone.contract.ContractPerkPlan.snapshot(equipped);
            contractKungFuSlot = candidate;
            contractLog("Temporarily replacing perk slot " + (candidate + 1) + " ("
                    + (equipped[candidate] == null ? "empty" : equipped[candidate]) + ") with Kung Fu Knowledge.");
            return true;
        }
        contractKungFuUnavailable = true; contractKungFuRequested = false; perksNeedSync = false;
        contractLog("FIST_MID_KILLS cannot start: Kung Fu Knowledge not equipped and no replaceable perk slot. Vampire and Golden Heads protected.");
        finishNpcFlow();
        return false;
    }

    private com.nezurstandalone.settings.PerkSetting[] ksBoxes() {
        return new com.nezurstandalone.settings.PerkSetting[]{ks1, ks2};
    }

    /**
     * Options for a killstreak box: the level gate always applies here - unlike perks, "The Way"
     * does not waive it - and a streak picked in the other slot is hidden so the two cannot
     * collide. Megastreaks are a separate system and are deliberately never touched.
     */
    private java.util.List<String> visibleStreaksFor(com.nezurstandalone.settings.PerkSetting box) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add("No Perk");
        int level = Utils.getLevel();
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (com.nezurstandalone.settings.PerkSetting b : ksBoxes()) {
            if (b != box && !"No Perk".equals(b.getMode())) used.add(b.getMode());
        }
        for (Perk k : KILLSTREAKS) {
            if (used.contains(k.name)) continue;
            if (level < k.minLevel) continue;
            out.add(k.name);
        }
        if (!"No Perk".equals(box.getMode()) && !out.contains(box.getMode())) out.add(box.getMode());
        return out;
    }

    /** Perk boxes + The Way only exist on the panel while Auto Perk is on. */
    private void updatePerkVisibility() {
        boolean show = autoPerk.isEnabled();
        perk1.setVisible(show); perk2.setVisible(show); perk3.setVisible(show); perk4.setVisible(show);
        theWay.setVisible(show);
        boolean showKs = autoKillstreak.isEnabled();
        ks1.setVisible(showKs); ks2.setVisible(showKs);
    }

    private Perk perkByName(String name) {
        for (Perk p : PERKS) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    private Perk killstreakByName(String name) {
        for (Perk p : KILLSTREAKS) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    /** The options a given box should show: No Perk, plus perks the level allows (or all, with The
     *  Way), minus any already picked in another box. The current pick is always kept present. */
    private java.util.List<String> visiblePerksFor(com.nezurstandalone.settings.PerkSetting box) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add("No Perk");
        int level = Utils.getLevel();
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (com.nezurstandalone.settings.PerkSetting b : perkBoxes()) {
            if (b != box && !"No Perk".equals(b.getMode())) used.add(b.getMode());
        }
        for (Perk p : PERKS) {
            if (used.contains(p.name)) continue;
            if (!theWay.isEnabled() && level < p.minLevel) continue;
            out.add(p.name);
        }
        if (!"No Perk".equals(box.getMode()) && !out.contains(box.getMode())) out.add(box.getMode());
        return out;
    }

    private void log(String msg) {
        if (debugLogging.isEnabled() && mc.thePlayer != null) {
            NotificationManager.showInChat("§8[§dGrinder§8] §7" + msg);
        }
    }

    // =========================================================================
    @Override
    protected void onEnable() {
        super.onEnable();
        overdriveOof.reset();
        blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
        currentState = State.IDLE;
        combatPermitted = false;
        resetMenuTracking();
        npcRetryAfterNanos = 0L;
        npcInteractionUntilNanos = 0L;
        oofCooldown = 0L;
        lobbyFirstCommand = false;
        limboSentOnce = false;
        combat.stop();
        combat.resetTargeting();
        // Full walker wipe, not just stop(): internal latches (suicideMode above all) survive
        // a plain stop(), and a re-enable after one tripped inherited a walker that released
        // keys every tick - aiming and clicking but never moving.
        com.nezurstandalone.pathfinder.PathfinderManager.resetIfAvailable(this);
        lastAttackTime = com.nezurstandalone.control.Clock.millis();
        lastBreakEndTime = com.nezurstandalone.control.Clock.millis();
        isOnBreak = false;
        spireActive = false;
        spireFocusPaused = false;
        if (sessionStartTime == 0L) sessionStartTime = com.nezurstandalone.control.Clock.millis();
        hudFadeStart=0;hudSnapshot=null;hudSnapshotAt=0;
        PathfinderManager.combatSettings(false,true,activeCombatOptions().range()); // forward pressure only, no strafe
           // approach hops + occasional crit jumps
        prevWalkerSprint = com.nezurstandalone.pathfinder.PathfinderConfig.sprint.enabled;
        com.nezurstandalone.pathfinder.PathfinderConfig.sprint.enabled = true; // sprint the approach too
        spawnWalkStart = 0L;
        combatMoveProgressAt = com.nezurstandalone.control.Clock.millis();
        combatMoveStuckSince = 0L;
        combatPathFailed = false;
        // Fresh reconcile only when the account changed since the last sync. Same user + same
        // boxes = already equipped, skip the NPC trip entirely.
        refreshPerkAccount();
    }

    @Override
    protected void onDisable() {
        overdriveOof.clear();
        loadout.interrupt(com.nezurstandalone.control.Clock.millis(),"AutoGrinder disabled during menu operation");
        managedVisit=false;chooserSlot=null;
        prestigeGold.clear();
        blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
        cancelMysticStow();
        com.nezurstandalone.control.GrinderDiagnostics.setActive(false, new java.io.File(mc.mcDataDir, "logs/nezur-acg-diagnostics"));
        pauseGameplay();
        resetMenuTracking();
        combat.stop();
        combat.resetTargeting();
        // Full wipe on disable too: guarantees the next enable starts from a clean walker
        // regardless of what state the previous session left behind.
        com.nezurstandalone.pathfinder.PathfinderManager.resetIfAvailable(this);
        PathfinderManager.clear(this, true);
        PathfinderManager.combatSettings(true,false,activeCombatOptions().range());
        PathfinderManager.clear(this,true);
        
        com.nezurstandalone.pathfinder.PathfinderConfig.sprint.enabled = prevWalkerSprint;
        releaseSpawnWalk(true);
        restoreVolume();
        updateSpireFocusPause(false);
        super.onDisable();
    }

    private void releaseCombat() {
        combatPermitted = false;
        com.nezurstandalone.contract.ContractCombatPolicy.noTarget();
        combat.stop();
        com.nezurstandalone.pathfinder.PathfinderManager.clearCombatTarget();
        // setCombatTarget(null) only drops the chase - if the walker was left active with no
        // route (combat cleared the path, then the target died) it would sit in resetKeys()
        // every tick: alive, aiming, never moving.
        if (!PathfinderManager.isPathing()) {
            com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
        }
    }

    private void pauseGameplay() {
        releaseRaffleInteraction();
        releaseEventApproach();
        com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
        com.nezurstandalone.control.MovementKeys.release("grinder-npc");
        com.nezurstandalone.control.MovementKeys.release("grinder");
        if(mysticStowScreen==null || mc.currentScreen!=mysticStowScreen)
            com.nezurstandalone.control.InventoryOwner.release(this, false);
        combatPermitted = false;
        com.nezurstandalone.contract.ContractCombatPolicy.noTarget();
        combat.stop();
        GuardedInput.cancel(interactionOwner);
        com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
        releaseSpawnWalk(false);
        RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
        RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);
    }

    private void closeCurrentScreen() {
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
            mc.thePlayer.closeScreen();
        } else if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
    }

    // =========================================================================
    @SubscribeEvent
    public void onCombatFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled() || !combatPermitted) com.nezurstandalone.contract.ContractCombatPolicy.noTarget();
        else com.nezurstandalone.contract.ContractCombatPolicy.updateSneak();
        if (hasTemporaryPerkLease() && (contractKungFuRestoring || !contractKungFuReady)) {
            combat.stop();
            return;
        }
        // Render frames must obey the same correction barrier as client ticks and queued dispatch.
        if(cancelledMysticStowScreen())return;
        if (correctionTick >= 0) {
            pauseGameplay();
            return;
        }
        // Egg interaction owns aim and clicks; cancel queued attacks before either click driver runs.
        if (hasDragonEggPriority()) {
            releaseCombat();
            combat.resetTargeting();
            return;
        }
        if (!isToggled() || !combatPermitted || isOnBreak || isAutoBlockheadActive() || mc.thePlayer == null
                || mc.theWorld == null || mc.currentScreen != null || mc.thePlayer.isDead
                || mc.thePlayer.getHealth() <= 0 || !PathfinderManager.available(this)
                || (mc.thePlayer.isUsingItem() && !isAutoHealBusy())
                || !isCombatState(currentState)) {
            combat.stop();
            return;
        }
        combat.onRenderFrame();
    }

    @SubscribeEvent
    public void onCombatAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) { combat.attacked(event); }

    private boolean closeUnaffordablePerkMenu() {
        if (managedVisit || mc.thePlayer == null || !(mc.currentScreen instanceof GuiChest)
                || currentState != State.PERK_CLICKING_GUI) return false;
        GuiChest gui = (GuiChest) mc.currentScreen;
        if (!ownsMenu(gui)) return false;
        IInventory inv = ((ContainerChest) gui.inventorySlots).getLowerChestInventory();
        String kind = menuKind(inv.getDisplayName().getUnformattedText());
        if (!kind.equals("choosePerk") && !kind.equals("chooseStreak")) return false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack != null && StringUtils.stripControlCodes(stack.getDisplayName()).trim().equalsIgnoreCase(targetPerkName)
                    && loreHas(loreOf(stack), "Not enough gold")) {
                // Negative evidence does not require a purchase/menu acknowledgement.
                // Abort before navigation ownership or a pending transition can stall us.
                failMenu("Not enough gold for " + targetPerkName + ".");
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isToggled()) return;
        if(mc.thePlayer!=null)hudStats.life(mc.thePlayer.isDead||mc.thePlayer.getHealth()<=0,com.nezurstandalone.control.Clock.millis());
        if(refreshTdmContext())for(Entity entity:mc.theWorld.loadedEntityList)if(entity instanceof EntityPlayer)observeTdmLife((EntityPlayer)entity);
        if (!isToggled() || !combatPermitted) com.nezurstandalone.contract.ContractCombatPolicy.noTarget();
        else com.nezurstandalone.contract.ContractCombatPolicy.updateSneak();
        diagnosticTick();

        if (isToggled() && closeUnaffordablePerkMenu()) return;
        if (!PathfinderManager.available(this)) {
            releaseRaffleInteraction();releaseEventApproach();
            combat.stop(); combatPermitted=false;
            com.nezurstandalone.control.MovementKeys.release("grinder");
            com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
            GuardedInput.cancel(interactionOwner);
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            return;
        }
        try {
            runGrinderTick(event);
        } catch (RuntimeException failure) {
            System.err.println("[Nezur] Grinder fault state=" + currentState + " world=" + engineWorld);
            failure.printStackTrace();
            pauseGameplay();
            PathfinderManager.clear(this, true);
            resetMenuTracking();
            combat.resetTargeting();
            currentState = State.IDLE;
        } finally {
            if (!combatPermitted) {
                combat.stop();
                com.nezurstandalone.pathfinder.PathfinderManager.clearCombatTarget();
            }
        }
        if (contractKungFuStopAfterRestore && !hasTemporaryPerkLease() && mc.currentScreen == null) {
            contractKungFuStopAfterRestore = false;
            setToggled(false);
        }
    }

    private static boolean isClassicPitTitle(String title) {
        return title != null && "THE PIT CLASSIC".equalsIgnoreCase(title.trim());
    }

    private static boolean isPitScoreboardTitle(String title) {
        return title != null && ("THE HYPIXEL PIT".equalsIgnoreCase(title.trim()) || isClassicPitTitle(title));
    }

    private void runGrinderTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if(isToggled()&&mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected)AutoReconnect.enableRecovery();
        combatPermitted = false;
        if (engineWorld != mc.theWorld || enginePlayer != mc.thePlayer) {
            if(wasDeadLastTick)overdriveOof.lifeEnded();else overdriveOof.clear();
            loadout.interrupt(com.nezurstandalone.control.Clock.millis(),"world/player changed");
            managedVisit=false;chooserSlot=null;
            if(mc.theWorld==null){loadoutContext.disconnected(loadout);loadoutWorld=null;}
            prestigeGold.suspend();
            blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
            pauseGameplay(); PathfinderManager.clear(this, true); resetMenuTracking(); combat.resetTargeting();
            engineWorld = mc.theWorld; enginePlayer = mc.thePlayer;
            currentState = State.IDLE; previousZone = ""; lastMidScan = 0; cachedMidCount = 0;
            currentEgg = null; nightQuestActive = false; nextNightQuestTime = 0;
            spireActive=false; spireEndStamp=0; updateSpireFocusPause(false);
            lastEggPos=null;eggBlacklistPos=null;eggBlacklistUntil=0;eggStationaryStart=0;
            combatMoveStuckSince=0;combatPathFailed=false;otherZoneEntryTime=0;
            prestigeSwapPending=false;prestigeLeftPit=false;lastSeenLevel=-1;
            correctionTick = mc.thePlayer == null ? -1 : mc.thePlayer.ticksExisted;
        }
        if(mc.thePlayer!=null && mc.theWorld!=null){refreshPerkAccount();refreshLoadoutRequirements();refreshLoadoutGold();}

        if (mc.thePlayer != null && (mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0)) {
            wasDeadLastTick = true;overdriveOof.lifeEnded();
        }
        if (!isToggled() || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0) {
            blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
            pauseGameplay();
            resetMenuTracking();
            return;
        }

        if(cancelledMysticStowScreen())return;

        if (correctionTick >= 0) {
            pauseGameplay();
            if (mc.thePlayer.ticksExisted - correctionTick < 3 || mc.currentScreen != null
                    || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                    || Utils.getScoreboardTitle().isEmpty()) return;
            correctionTick = -1;
            diagnosticSnapshot("CORRECTION_RESUME");
        }
        PathfinderManager.combatSettings(false,true,activeCombatOptions().range());
        if (menuTransition.isPending() && menuTransition.failed(System.nanoTime())) { failMenu("Menu transition timed out."); return; }
        handleMuteGameTick();

        refreshPerkAccount();
        int currentLevel = Utils.getLevel();
        double previousPrestigeGold=prestigeGold.earned();
        if(currentLevel>0 && currentLevel<120)prestigeGold.clear();
        if(isPitScoreboardTitle(Utils.getScoreboardTitle()) && currentLevel>=120 && !mc.thePlayer.isDead)
            prestigeGold.observe(scoreboardGold());
        else prestigeGold.suspend();
        if(autoPrestige.isEnabled() && prestigeGold.known() && prestigeGold.earned()>previousPrestigeGold
                && com.nezurstandalone.control.Clock.millis()-prestigeGoldLogAt>=30000) {
            prestigeGoldLogAt=com.nezurstandalone.control.Clock.millis();
            log("Prestige gold: "+formatGold(prestigeGold.earned())+"/"+formatGold(prestigeGold.required())
                    +"g tracked; "+formatGold(prestigeGold.remaining())+"g left."
                    +(prestigeGold.ready()?" Will verify at the NPC when back in spawn.":""));
        }
        if(currentLevel>0 && currentLevel!=lastSeenLevel) {
            contractKungFuUnavailable=false;lastSeenLevel=currentLevel;
        }

        refreshLoadoutRequirements();
        loadout.evaluate(com.nezurstandalone.control.Clock.millis());

        boolean overdriveInPit=isPitScoreboardTitle(Utils.getScoreboardTitle());
        boolean overdriveInSpawn=Utils.isInSpawn();
        if(overdriveOof.sidebar(Utils.getScoreboardLines(),oofOnOverdrive.isEnabled(),overdriveInPit,
                overdriveInSpawn,false,com.nezurstandalone.control.Clock.millis()))log("Your sidebar shows Overdrive - /oof queued.");
        boolean overdriveSafe=mc.currentScreen==null && com.nezurstandalone.control.GuiLease.available(this)
                && !menuTransition.isPending() && !hasTemporaryPerkLease()
                && !isAutoHealBusy() && !mc.thePlayer.isUsingItem()
                && com.nezurstandalone.control.Clock.millis()>=throttleCooldownEnd;
        if(overdriveOof.tick(oofOnOverdrive.isEnabled(),overdriveInPit,
                overdriveInSpawn,false,overdriveSafe,com.nezurstandalone.control.Clock.millis(),
                () -> {boolean sent=com.nezurstandalone.control.CommandCoordinator.send(this,"/oof");
                    if(sent)log("Overdrive /oof sent.");return sent;})) {
            pauseGameplay();PathfinderManager.clear(this,true);currentState=State.IDLE;
            return;
        }

        // Night-quest expiry / cancellation.
        if (nightQuestActive) {
            if (com.nezurstandalone.control.Clock.millis() - nightQuestStartTime > 740000L
                    || isSpireActive() || isSquadsEventActive()) {
                nightQuestActive = false;
            }
        }

        if (mc.currentScreen != null) {
            pauseGameplay();
            if (mc.currentScreen == mysticStowScreen) { handleMysticStow(); return; }
            if (mc.currentScreen instanceof GuiChest && ownsMenu((GuiChest) mc.currentScreen)) {
                boolean prestige = currentState == State.PRESTIGE_INTERACTING
                        || currentState == State.PRESTIGE_CLICKING_GUI;
                currentState = prestige ? State.PRESTIGE_CLICKING_GUI : State.PERK_CLICKING_GUI;
                if (prestige) handlePrestigeGui((GuiChest) mc.currentScreen);
                else handlePerkGui((GuiChest) mc.currentScreen);
            }
            // Other modules and the user own all unexpected screens; never dismiss them here.
            return;
        }
        if (menuTransition.isPending()) {
            pauseGameplay();
            if (menuTransition.failed(System.nanoTime())) failMenu("Menu transition timed out.");
            return;
        }
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            pauseGameplay();
            mc.thePlayer.closeScreen();
            return;
        }

        handleAutoWeapon();
        updateSpireStatus();

        String title = Utils.getScoreboardTitle();
        com.nezurstandalone.contract.ContractConnectionFlow.Location location=com.nezurstandalone.utils.PitSessionLocation.current();
        boolean inPit = location==com.nezurstandalone.contract.ContractConnectionFlow.Location.PIT;
        boolean inLobby = location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LOBBY;
        boolean inLimbo = location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LIMBO;

        // Prestige swap bookkeeping: once we have left the pit (into lobby/limbo) the swap is under
        // way; when we are back in the pit the level has refreshed, so clear the pending block. A
        // 30s failsafe covers a swap that never completes.
        if (prestigeSwapPending) {
            if (!inPit) prestigeLeftPit = true;
            if ((inPit && prestigeLeftPit) || com.nezurstandalone.control.Clock.millis() - prestigeSwapStart > 30000L) {
                prestigeSwapPending = false;
                prestigeLeftPit = false;
            }
        }

        if (inLimbo) { handleLimbo(); return; }
        if (inLobby) { handleLobbyReconnect(); return; }

        if (classicPrestigePending) {
            if (isClassicPitTitle(title) && Utils.getLevel() > 0 && Utils.getLevel() < 120) {
                classicPrestigePending = false;
                perksNeedSync = true;
                java.util.Arrays.fill(perkAttempted, false);
                loadout.resetEvidence();
                log("Classic prestige level reset confirmed.");
                finishNpcFlow();
                return;
            }
            if (com.nezurstandalone.control.Clock.millis() - classicPrestigeStart > 15000L) {
                classicPrestigePending = false;
                failMenu("Classic prestige level reset was not confirmed.");
                return;
            }
        }

        // Perk/prestige GUI clicking runs even before the in-pit gate (the menu is a screen).
        if (currentState == State.PERK_CLICKING_GUI) {
            if (mc.currentScreen instanceof GuiChest) { handlePerkGui((GuiChest) mc.currentScreen); return; }
            currentState = State.IDLE;
        }
        if (currentState == State.PRESTIGE_CLICKING_GUI) {
            if (mc.currentScreen instanceof GuiChest) { handlePrestigeGui((GuiChest) mc.currentScreen); return; }
            currentState = State.IDLE;
        }

        if (!inPit) { pauseGameplay(); return; }

        long contractNow = com.nezurstandalone.control.Clock.millis();
        if (contractNoPerkRequested && contractKungFuReady && !contractKungFuRestoring
                && contractNoPerkVerifyAt > 0 && contractNow >= contractNoPerkVerifyAt) {
            invalidateTemporaryPerkVerification();
        }
        if (hasTemporaryPerkLease() && (contractKungFuRestoring || !contractKungFuReady)
                && !Utils.isInSpawn()) {
            pauseGameplay();
            if (contractNow - contractPerkTravelAt > 3000L
                    && sendRecoveryOof()) contractPerkTravelAt = contractNow;
            return;
        }

        // Keep mystic gear out of the hotbar as soon as it fills, except while a contract lease
        // is being prepared or restored and must retain its path to the upgrades NPC.
        if(classicRuntime() && currentState==State.PICKING_UP_MYSTIC && mysticPickupStack!=null
                && countMatchingMystics(mysticPickupStack)>mysticInventoryBefore){finishMysticPickup();return;}
        if (!(hasTemporaryPerkLease() && (contractKungFuRestoring || !contractKungFuReady))
                && startMysticStow()) return;

        if (currentState == State.LIMBO || currentState == State.LOBBY_RECONNECT || currentState == State.JOINING_PIT) {
            currentState = State.IDLE;
            limboSentOnce = false;
        }

        if(mysticPickupPending && (mysticPendingWorld!=mc.theWorld || com.nezurstandalone.control.Clock.millis()-lastMysticChatTime>30000))mysticPickupPending=false;
        if(classicRuntime() && mysticPickupPending && mysticPickup.isEnabled() && !isBusyFlow()) {
            mysticPickupPending=false;mysticPickupStarted=com.nezurstandalone.control.Clock.millis();
            mysticRepathAt=lastMysticTraceTime=0L;sawMysticItem=false;mysticTargetId=-1;
            mysticPickupStack=null;mysticMissingSince=0L;currentState=State.PICKING_UP_MYSTIC;
            pauseGameplay();log("Mystic dropped - collecting it.");
        }
        // A detected egg preempts combat and NPC navigation, except while a temporary perk lease
        // is being prepared or restored and must retain its path to the upgrades NPC.
        if (!isAutoBlockheadActive() && !isAutoRobberyActive() && !isAutoRaffleActive() && !isSpireActive() && dragonEggSupport.isEnabled() && !(hasTemporaryPerkLease()
                && (contractKungFuRestoring || !contractKungFuReady))) scanDragonEgg();
        else currentEgg = null;
        if (hasDragonEggPriority()) { handleDragonEgg(); return; }
        if (currentState == State.DRAGON_EGG) {
            com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
            GuardedInput.cancel(interactionOwner);
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            PathfinderManager.clear(this,true); currentState=State.IDLE; lastEggPos=null;
        }

        // Healing may keep combat navigation and tracking; attacks and weapon swaps remain gated.
        // Other item use and non-combat menu/navigation flows retain their existing pause.
        if ((isAutoHealBusy() || mc.thePlayer.isUsingItem()) && !allowsHealingMovement()) {
            pauseGameplay(); return;
        }

        if (currentState == State.PICKING_UP_MYSTIC) { handleMysticPickup(); return; }

        // --- Perk / Prestige NPC flows -------------------------------------
        if (currentState == State.PERK_NAVIGATING)      { handleNpcNavigation(false); return; }
        if (currentState == State.PERK_INTERACTING)     { handleNpcInteract(false); return; }
        if (currentState == State.PRESTIGE_NAVIGATING)  { handleNpcNavigation(true); return; }
        if (currentState == State.PRESTIGE_INTERACTING) { handleNpcInteract(true); return; }

        // Kick off perk/prestige while idle in spawn.
        String zone = PitMapManager.getZone(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        boolean inSpawn = zone.equals("Spawn") || zone.equals("Overspawn");
        if (!zone.equals(previousZone)) { previousZone = zone; otherZoneEntryTime = com.nezurstandalone.control.Clock.millis(); }
        if (!isAutoBlockheadActive() && !isAutoRobberyActive() && !isAutoRaffleActive() && (currentState == State.IDLE || currentState == State.LEAVING_SPAWN)) {
            if (!hasTemporaryPerkLease() && !prestigeSwapPending && !classicPrestigePending
                    && autoPrestige.isEnabled() && Utils.getLevel() >= 120 && startPrestigeProcess()) return;
            if (checkPerkTriggers()) return;
        }
        if (hasTemporaryPerkLease() && (contractKungFuRestoring || !contractKungFuReady)) {
            pauseGameplay();
            return;
        }

        if (isClassicPitTitle(title) && currentState == State.LOBBY_SWAP) {
            currentState = State.IDLE; lobbyFirstCommand = false; squadSwapForced = false;
        }
        if (currentState == State.LOBBY_SWAP) { handleLobbySwapInPit(); return; }

        if(contractGappleRequested){pauseGameplay();return;}

        if (isAutoRaffleActive()) {handleRaffle(inSpawn);return;}
        if (raffleOwned) {
            resetRaffle();releaseEventApproach();releaseCombat();combat.resetTargeting();
            PathfinderManager.clear(this,true);currentState=State.IDLE;
        }

        // Blockhead owns scoring movement for the whole active event, ahead of lobby population
        // swaps, break shifts and the ordinary mid grinder; menus and reconnects still own above.
        if (isAutoBlockheadActive()) { handleBlockhead(inSpawn); return; }
        if (isBlockheadState() || blockheadOwned) {
            releaseEventApproach();
            blockheadOwned=false;
            blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle(); releaseCombat(); combat.resetTargeting();
            PathfinderManager.clear(this,true); currentState=State.IDLE;
        }
        if (isAutoRobberyActive()) {handleRobbery(inSpawn);return;}
        if (robberyOwned) {
            releaseEventApproach();
            robbery.reset();robberyOwned=false;robberyRetreatAt=0;
            releaseCombat();combat.resetTargeting();PathfinderManager.clear(this,true);currentState=State.IDLE;
        }

        // --- Spire priority: overrides lobby-swap / break / idle ----------
        if (isSpireActive()) { handleSpire(); return; }

        // --- Squads ranking -----------------------------------------------
        if (!isClassicPitTitle(title) && squadSupport.isEnabled() && isSquadsEventActive()) { handleSquads(inSpawn); return; }

        // Only Event Mode: sit idle in spawn until a supported event is live, then grind it. Spire,
        // Squads and the dragon egg are already handled above (they return); this gates the plain
        // no-event mid grind.
        if (onlyEventMode.isEnabled()) {
            isOnBreak = false;
            boolean bypass = nightQuestSupport.isEnabled() && nightQuestActive;
            if (!isAnyEventActive() && !bypass) {
                pauseGameplay();
                long t = com.nezurstandalone.control.Clock.millis();
                if (!inSpawn) {
                    if (t - oofCooldown > 3000L && t >= throttleCooldownEnd) {
                        if (!sendRecoveryOof()) return; oofCooldown = t;
                    }
                } else {
                    releaseCombat(); releaseSpawnWalk(true); PathfinderManager.clear(this, true); currentState = State.IDLE;
                }
                return;
            }
        }

        // Break shifts (skipped during priority events).
        boolean priorityEvent = isRagePitEventActive() || CombatAura.isTdmEventActive() || isSquadsEventActive();
        long now = com.nezurstandalone.control.Clock.millis();
        if (breakTimeEnable.isEnabled() && !priorityEvent) {
            if (isOnBreak) {
                if (now - lastBreakEndTime > (long) (breakDurationLimit.value * 60000L)) {
                    isOnBreak = false; lastBreakEndTime = now; log("Break over, resuming.");
                }
            } else if (now - lastBreakEndTime > (long) (workDurationLimit.value * 3600000L)) {
                isOnBreak = true; lastBreakEndTime = now; log("Shift done, break.");
            }
        } else {
            isOnBreak = false;
        }
        if (isOnBreak) {
            pauseGameplay();
            if (!inSpawn) {
                if (now - oofCooldown > 3000L && now >= throttleCooldownEnd) { if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/spawn")) return; oofCooldown = now; }
            } else { releaseCombat(); PathfinderManager.clear(this, true); currentState = State.IDLE; }
            return;
        }

        // Thin-lobby swap (respecting night-quest hold + priority events).
        if (lobbySwap.isEnabled() && !priorityEvent && shouldLobbySwap()) {
            pauseGameplay();
            if (inSpawn) { enterLobbySwap(); return; }
            if (now - oofCooldown > 3000L && now >= throttleCooldownEnd) { if (!sendRecoveryOof()) return; oofCooldown = now; }
            return;
        }

        if (!inSpawn && (PitMapManager.isInCombatArea(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)
                || isRagePitEventActive() && !zone.equals("Sewer"))) {
            handlePit();
        } else if (inSpawn || currentState == State.LEAVING_SPAWN) {
            handleSpawn(); // hold W toward mid until we are actually in the pit
        } else {
            handleOtherZone();
        }
    }

    // ---- mute ---------------------------------------------------------------
    private void handleMuteGameTick() {
        if (muteGame.isEnabled() && !isMuted) muteVolume();
        else if (!muteGame.isEnabled() && isMuted) restoreVolume();
    }

    private void muteVolume() {
        try {
            originalVolume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            mc.gameSettings.setSoundLevel(SoundCategory.MASTER, 0.0f);
            isMuted = true;
        } catch (Exception ignored) { }
    }

    private void restoreVolume() {
        if (isMuted && originalVolume >= 0f) {
            try { mc.gameSettings.setSoundLevel(SoundCategory.MASTER, originalVolume); } catch (Exception ignored) { }
            isMuted = false;
            originalVolume = -1f;
        }
    }

    // ---- zone handlers ------------------------------------------------------
    private void handleSpawn() {
        releaseCombat();
        combat.resetTargeting();
        // Stop the walker only when entering this state or if it somehow became active - 
        // calling stop() every tick reset the forward key this method presses below and 
        // cleared its own rotation request, so the bot stood still.
        if ((currentState != State.LEAVING_SPAWN && currentState != State.SPIRE_WALK_TO_MID) || com.nezurstandalone.pathfinder.AutoWalker.INSTANCE.isActive() || com.nezurstandalone.pathfinder.PathfinderManager.hasDestination()) {
            com.nezurstandalone.pathfinder.PathfinderManager.clearCombatTarget();
            com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
            com.nezurstandalone.pathfinder.PathfinderManager.clear(this, true);
        }
        if(currentState!=State.SPIRE_WALK_TO_MID)currentState = State.LEAVING_SPAWN;
        oofCooldown = 0L;
        long now = com.nezurstandalone.control.Clock.millis();
        if (spawnWalkStart == 0L) spawnWalkStart = now;

        // No pathfinding out of spawn - it is unreliable across the drop-in. Aim straight at pit
        // centre (0,0) through the rotation manager and just hold forward+sprint until PitMapManager
        // reports we are in the Pit zone. Only the forward/sprint keys are pressed here.
        double dx = 0.0 - mc.thePlayer.posX;
        double dz = 0.0 - mc.thePlayer.posZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        RotationManager.getInstance().setTargetRotation(MOVEMENT_ROTATION_OWNER,
                RotationManager.PRIORITY_LEGACY, yaw, mc.thePlayer.rotationPitch, 18.0f);
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindForward.getKeyCode(), true);
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindSprint.getKeyCode(), true);
        // Sprint-jumping out of spawn carries speed, but re-jumping on the exact landing tick is a
        // frame-perfect hop chain - a periodic jump train no hand produces. Same bookkeeping the
        // AutoWalker uses: a sampled 0-2 tick ground delay plus an occasional dropped beat, so the
        // rhythm has variance and sometimes just... doesn't jump.
        if (mc.thePlayer.onGround && !wasOnGroundLastTick) {
            bhopDelayTicks = spawnBhopRandom.nextInt(3);
            bhopSkipBeat = spawnBhopRandom.nextDouble() < 0.20;
        }
        wasOnGroundLastTick = mc.thePlayer.onGround;
        boolean wantSpawnJump = false;
        if (mc.thePlayer.onGround) {
            if (bhopDelayTicks > 0) {
                bhopDelayTicks--;
            } else if (!bhopSkipBeat) {
                wantSpawnJump = true;
            } else {
                // The skip is consumed here: skipping means never leaving the ground, so there is
                // no touchdown to re-roll it on.
                bhopSkipBeat = false;
            }
        }
        wasOnGroundLastTick = mc.thePlayer.onGround;
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindJump.getKeyCode(), wantSpawnJump);
        // Let vanilla process jump input exactly once in the living update.

        // Failsafe: if we still are not in the pit after ~10s (walled in, wrong way), /oof.
        if (now - spawnWalkStart > 10000L) {
            releaseSpawnWalk(true);
            com.nezurstandalone.pathfinder.PathfinderManager.resetIfAvailable(this);
            if (now - oofCooldown > 3000L && now >= throttleCooldownEnd) {
                if (!sendRecoveryOof()) return; oofCooldown = now;
            }
        }
    }

    /** Drops the forward/sprint keys held while walking out of spawn. */
    private void releaseSpawnWalk(boolean resetTimer) {
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindForward.getKeyCode(), false);
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindSprint.getKeyCode(), false);
        com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindJump.getKeyCode(), false);
        if (resetTimer) spawnWalkStart = 0L;
        wasOnGroundLastTick = false;
        bhopDelayTicks = 0;
        bhopSkipBeat = false;
    }

    private boolean isAutoHealBusy() {
        return AutoHeal.isHealing();
    }

    private void handlePit() {
        // Release the spawn-walk keys ONCE on arrival - calling it every tick pins forward to
        // false and cancels the walker's combat forward-press, so the bot would just stand and
        // stare. spawnWalkStart is zeroed by releaseSpawnWalk, so this runs a single time.
        if (spawnWalkStart != 0L) releaseSpawnWalk(true);
        oofCooldown = 0L;
        long now = com.nezurstandalone.control.Clock.millis();
        equipBestWeapon(); // Re-arm before KillAura applies its Weapons Only filter.
        if(currentState==State.RETURNING_TO_MID && handleIdleReturn(now))return;
        currentState = State.FIGHTING;
        combatPermitted=true;
        EntityPlayer target = combat.pickTarget();
        if (target != null) {
            combatPermitted = true;
            driveCombatMovement(target);
            lastAttackTime = now;
        } else {
            releaseCombat();
            combat.resetTargeting();
            combatMoveStuckSince = 0L;
            if (idleRetreat.isEnabled() && mayReturnToMid() && now - lastAttackTime > 3000L
                    && Math.hypot(mc.thePlayer.posX,mc.thePlayer.posZ)>3.0) navigateToMid();
            else PathfinderManager.clear(this, true);
        }
    }

    /** Keep the mid route until arrival or a real nearby combat target; never chase a far fallback. */
    private boolean handleIdleReturn(long now) {
        if(!idleRetreat.isEnabled() || !mayReturnToMid() || Math.hypot(mc.thePlayer.posX,mc.thePlayer.posZ)<=3.0) {
            releaseCombat();PathfinderManager.clear(this,true);currentState=State.FIGHTING;
            lastAttackTime=now;
            return false;
        }
        combatPermitted=true;
        EntityPlayer nearby=combat.pickCombatTarget();
        if(nearby!=null) {
            // Cancel route ownership BEFORE arming direct chase. clear() stops the walker.
            PathfinderManager.clear(this,true);
            combatMoveStuckSince=0L;combatPathFailed=false;combatRepathAt=0L;
            combatMoveProgressAt=now;combatMoveLastX=mc.thePlayer.posX;combatMoveLastZ=mc.thePlayer.posZ;
            currentState=State.FIGHTING;
            driveCombatMovement(nearby);lastAttackTime=now;
            return true;
        }
        releaseCombat(); // Clears combat/camera demand while retaining an existing navigation route.
        navigateToMid();
        return true;
    }

    /**
     * Runs the bot straight at the target (the walker's combat block faces and sprints at it) with
     * no A* path - pathing to a moving player recomputed every tick, which drew that curving route
     * line and constantly re-found. Only when the direct chase is genuinely stuck - collided and
     * not making ground for ~0.6s - does it hand off to the pathfinder to route around whatever is
     * in the way, then snaps back to the straight chase the moment it is moving again.
     */
    private void driveCombatMovement(EntityPlayer target) {
        long now = com.nezurstandalone.control.Clock.millis();
        if(isRagePitEventActive() && !"Pit".equals(PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ))) {
            if(PathfinderManager.hasDestination())PathfinderManager.clear(this,true);
            combatMoveStuckSince=0;combatPathFailed=false;
            PathfinderManager.combatTarget(PathfinderManager.claim(this),target);return;
        }

        // Someone is inside attack reach: there is nothing to path to - just fight them. Drop any
        // route immediately so none is computed or rendered while they are in range. The dragon
        // egg / perk / mystic flows run before combat and never reach here, so they keep their paths.
        if (combatMoveStuckSince==0L && !mc.thePlayer.isCollidedHorizontally && mc.thePlayer.getDistanceToEntity(target) <= activeCombatOptions().range() + 0.5) {
            combatMoveStuckSince = 0L;
            combatMoveProgressAt = now;
            // Clear BEFORE setting the combat target: clear() calls AutoWalker.stop() (active=false)
            // and the walker's tick bails on !active, so setting the target first would be undone
            // and the bot would just stand still.
            if (PathfinderManager.hasDestination()) {
                PathfinderManager.clear(this, true);
            }
            PathfinderManager.combatTarget(PathfinderManager.claim(this), target);
            return;
        }

        double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        if (Math.hypot(px - combatMoveLastX, pz - combatMoveLastZ) > 0.14) {
            combatMoveLastX = px; combatMoveLastZ = pz; combatMoveProgressAt = now;
        }
        boolean stuck = mc.thePlayer.isCollidedHorizontally && now - combatMoveProgressAt > 600L;

        if (stuck || combatMoveStuckSince != 0L) {
            if (combatMoveStuckSince != 0L && (PathfinderManager.getState() == PathfinderManager.State.FAILED
                    || PathfinderManager.getState() == PathfinderManager.State.COMPLETED)) {
                combatPathFailed = PathfinderManager.getState() == PathfinderManager.State.FAILED;
                combatMoveStuckSince = 0L; combatMoveProgressAt = now;
                PathfinderManager.combatTarget(PathfinderManager.claim(this), target); return;
            }
            if (combatMoveStuckSince == 0L) combatMoveStuckSince = now;
            // Hand movement to A* to route around the obstacle. If the search comes back
            // empty (no route through the crowd), combatPathFailed flips and the next ticks
            // fall back to the direct chase rather than freezing in place.
            com.nezurstandalone.pathfinder.PathfinderManager.clearCombatTarget();
            if (combatMoveStuckSince == now) com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
            if (now >= combatRepathAt && !combatPathFailed && PathfinderManager.getState() != PathfinderManager.State.SEARCHING
                    && PathfinderManager.getState() != PathfinderManager.State.ACTIVE) {
                PathfinderManager.walkTo(this, target.posX, target.posY, target.posZ, true);
                combatRepathAt = now + 300L;
                // A path that was requested but never published (empty result leaves the
                // walker inactive) means routing failed - give up on A* for this chase.
                if (PathfinderManager.getState() == PathfinderManager.State.FAILED) {
                    combatPathFailed = true;
                    combatMoveProgressAt = now; // fresh stuck window for the direct chase
                }
            }
        } else {
            combatMoveStuckSince = 0L;
            combatPathFailed = false;
            // Direct straight-line chase: clear any path FIRST (clear() stops the walker), then set
            // the combat target so the walker stays active and its combat block drives movement.
            if (PathfinderManager.hasDestination()) {
                PathfinderManager.clear(this, true);
            }
            PathfinderManager.combatTarget(PathfinderManager.claim(this), target);
        }
    }

    private void handleOtherZone() {
        if (spawnWalkStart != 0L) releaseSpawnWalk(true);

        releaseCombat();
        combat.resetTargeting();
        if (com.nezurstandalone.contract.ContractCombatPolicy.type() != null) {
            navigateToMid();
            return;
        }
        if (idleRetreat.isEnabled()) navigateToMid(); else PathfinderManager.clear(this, true);
        long now = com.nezurstandalone.control.Clock.millis();
        boolean hitRecently = now - lastAttackTime < 10000L;
        boolean freshInZone = now - otherZoneEntryTime < 10000L;
        if (!hitRecently && !freshInZone && now - oofCooldown > 3000L && now >= throttleCooldownEnd
                && !(squadSupport.isEnabled() && isSquadsEventActive())) {
            log("Stuck outside the pit, /oof.");
            if (!sendRecoveryOof()) return;
            oofCooldown = now;
        }
    }

    private void navigateToMid() {
        if(!mayReturnToMid()){PathfinderManager.clear(this,true);currentState=State.FIGHTING;return;}
        currentState=State.RETURNING_TO_MID;
        navigateContractMid(this);
    }
    public void navigateContractMid(Object owner) {
        if(!mayReturnToMid()){PathfinderManager.clear(owner,true);return;}
        long now = com.nezurstandalone.control.Clock.millis();
        if (now < midRepathAt && PathfinderManager.isPathing()) return;
        midRepathAt = now + 1500L;
        double[] center = centerSpot();
        double targetY = 55.0; // Fallback
        for (int y = 75; y > 30; y--) {
            net.minecraft.util.BlockPos p = new net.minecraft.util.BlockPos(center[0], y, center[1]);
            if (mc.theWorld.getBlockState(p).getBlock().getMaterial().isSolid()) {
                targetY = y + 1.0;
                break;
            }
        }
        PathfinderManager.walkTo(owner, center[0], targetY, center[1], true);
    }

    /** A gently-wandering point near pit centre so the bot does not park on one exact block. */
    private double[] centerSpot() {
        if (com.nezurstandalone.control.Clock.millis() - lastCenterUpdate > 5000L) {
            centerX = clampGauss();
            centerZ = clampGauss();
            lastCenterUpdate = com.nezurstandalone.control.Clock.millis();
        }
        return new double[]{centerX, centerZ};
    }

    private double clampGauss() {
        double v;
        do { v = java.util.concurrent.ThreadLocalRandom.current().nextGaussian() * 0.5; }
        while (v < -1.5 || v > 1.5);
        return v;
    }

    private boolean isBlockheadState() {
        return currentState==State.BLOCKHEAD_PAINTING || currentState==State.BLOCKHEAD_POWERUP
                || currentState==State.BLOCKHEAD_FIGHTING || currentState==State.BLOCKHEAD_AVOIDING;
    }

    private void handleBlockhead(boolean inSpawn) {
        if(!blockheadOwned) {
            blockheadEnteringMid=inSpawn;blockheadRepathAt=0;
            releaseEventApproach();PathfinderManager.clear(this,true);
        }
        blockheadOwned=true;
        long now=com.nezurstandalone.control.Clock.millis();
        isOnBreak=false;
        releaseCombat();combat.resetTargeting();
        blockhead.begin(now);
        if(inSpawn) {blockheadEnteringMid=true;releaseEventApproach();blockhead.interrupted();handleSpawn();return;}
        if(spawnWalkStart!=0L)releaseSpawnWalk(true);
        if(blockheadEnteringMid) {
            currentState=State.BLOCKHEAD_PAINTING;
            if(approachEventMid())return;
            blockheadEnteringMid=false;
        }
        BlockheadController.Goal goal=blockhead.plan(now);
        if(goal==null) {currentState=State.BLOCKHEAD_PAINTING;approachEventMid();return;}
        boolean pickup=goal.kind==BlockheadController.GoalKind.POWERUP;
        currentState=pickup?State.BLOCKHEAD_POWERUP:State.BLOCKHEAD_PAINTING;
        double pickupDx=goal.pickupPosition.xCoord-mc.thePlayer.posX;
        double pickupDz=goal.pickupPosition.zCoord-mc.thePlayer.posZ;
        double distance=Math.hypot(pickupDx,pickupDz);
        if(!pickup || (distance<=2 && Math.abs(goal.pickupPosition.yCoord-mc.thePlayer.posY)<1.6)) {
            if(PathfinderManager.hasDestination() || AutoWalker.INSTANCE.isActive())PathfinderManager.clear(this,true);
            eventApproaching=true;
            float yaw=distance<.1?mc.thePlayer.rotationYaw:(float)Math.toDegrees(Math.atan2(-pickupDx,pickupDz));
            double titleHeight=goal.titlePosition.yCoord-(mc.thePlayer.posY+mc.thePlayer.getEyeHeight());
            float pitch=pickup?(float)-Math.toDegrees(Math.atan2(titleHeight,Math.max(.1,distance))):mc.thePlayer.rotationPitch;
            RotationManager.getInstance().setTargetRotation(MOVEMENT_ROTATION_OWNER,RotationManager.PRIORITY_MACRO,
                    yaw,pitch,(float)interactAimSpeed.value,true);
            boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-mc.thePlayer.rotationYaw))<30;
            boolean moving=aligned && distance>(pickup?(goal.cooldown>0?1.4:.2):1.0);
            com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindForward.getKeyCode(),moving);
            com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindSprint.getKeyCode(),moving && !pickup);
            com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindJump.getKeyCode(),
                    moving && mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally);
            return;
        }
        releaseEventApproach();
        if(PathfinderManager.isNavigatingTo(goal.position) && PathfinderManager.getState()==PathfinderManager.State.FAILED) {
            blockhead.failed(now);PathfinderManager.clear(this,true);blockheadRepathAt=now+500;return;
        }
        if(now>=blockheadRepathAt && (!PathfinderManager.isNavigatingTo(goal.position)
                || PathfinderManager.getState()==PathfinderManager.State.CANCELLED
                || PathfinderManager.getState()==PathfinderManager.State.COMPLETED)) {
            PathfinderManager.walkTo(this,goal.position.getX()+.5,goal.position.getY(),goal.position.getZ()+.5,
                    true,blockhead.routeBounds());
            blockheadRepathAt=now+1500;
        }
    }

    /** Event entry/idle movement goes straight to mid, just like the spawn drop-in. */
    private boolean approachEventMid() {
        return approachEventMid(6.0);
    }

    private boolean approachEventMid(double arrivalDistance) {
        if(PathfinderManager.hasDestination() || AutoWalker.INSTANCE.isActive())PathfinderManager.clear(this,true);
        double[] center=arrivalDistance<=1?new double[]{0,0}:centerSpot();
        double dx=center[0]-mc.thePlayer.posX, dz=center[1]-mc.thePlayer.posZ;
        if(Math.hypot(dx,dz)<=arrivalDistance) {releaseEventApproach();return false;}
        eventApproaching=true;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        RotationManager.getInstance().setTargetRotation(MOVEMENT_ROTATION_OWNER,RotationManager.PRIORITY_LEGACY,
                yaw,mc.thePlayer.rotationPitch,18.0f);
        boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-mc.thePlayer.rotationYaw))<60;
        com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindForward.getKeyCode(),aligned);
        com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindSprint.getKeyCode(),aligned);
        com.nezurstandalone.control.MovementKeys.set("grinder",mc.gameSettings.keyBindJump.getKeyCode(),
                aligned && mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally);
        return true;
    }

    private void releaseEventApproach() {
        if(!eventApproaching)return;
        releaseSpawnWalk(true);RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);eventApproaching=false;
    }

    private void handleRobbery(boolean inSpawn) {
        robberyOwned=true;isOnBreak=false;
        long now=com.nezurstandalone.control.Clock.millis();
        if(robbery.updateHold()) {
            releaseEventApproach();
            releaseCombat();combat.resetTargeting();releaseSpawnWalk(true);
            if(inSpawn) {PathfinderManager.clear(this,true);currentState=State.ROBBERY_BANKED;return;}
            if(currentState!=State.ROBBERY_RETREATING){PathfinderManager.clear(this,true);robberyRetreatAt=0;}
            currentState=State.ROBBERY_RETREATING;
            // /oof would risk throwing away the event gold. Wait out combat and use /spawn.
            if(now-oofCooldown>3000 && now>=throttleCooldownEnd
                    && com.nezurstandalone.control.CommandCoordinator.send(this,"/spawn"))oofCooldown=now;
            if(now>=robberyRetreatAt) {
                BlockPos retreat=robbery.retreat();
                if(retreat!=null)PathfinderManager.walkTo(this,retreat.getX()+.5,retreat.getY(),retreat.getZ()+.5,true);
                robberyRetreatAt=now+2500;
            }
            return;
        }
        if(inSpawn) {releaseEventApproach();handleSpawn();return;}
        if(spawnWalkStart!=0L)releaseSpawnWalk(true);
        robbery.scan(now);
        currentState=State.ROBBERY_FIGHTING;
        equipBestWeapon();
        combatPermitted=true;
        EntityPlayer target=combat.pickTarget();
        if(target!=null) {
            releaseEventApproach();
            driveCombatMovement(target);lastAttackTime=now;
        } else {releaseCombat();combat.resetTargeting();approachEventMid();}
    }

    // ---- Raffle -------------------------------------------------------------
    private void resetRaffle() {
        releaseRaffleInteraction();raffle.reset();raffleOwned=false;raffleEnteringMid=true;
        raffleRepathAt=0;rafflePathTicket=Integer.MIN_VALUE;raffleClickRate.reset();
    }

    private void releaseRaffleInteraction() {
        GuardedInput.cancel(raffleInputOwner);
        com.nezurstandalone.input.WindowActions.cancel(raffleInputOwner);
        com.nezurstandalone.control.MovementKeys.release(RAFFLE_MOVEMENT_OWNER);
        RotationManager.getInstance().clearTarget(RAFFLE_ROTATION_OWNER);
        if(raffleSlotOwned)com.nezurstandalone.control.InventoryOwner.release(this,true);
        raffleSlotOwned=false;raffleInventoryPending=false;
    }

    /** Keep tickets concealed on every travelling/collection tick, even with Auto Weapon off. */
    private void putAwayRaffleTickets() {
        putAwayRaffleTickets(true);
    }

    private void putAwayRaffleTickets(boolean releaseMovement) {
        if(releaseMovement)releaseRaffleInteraction();
        else {
            GuardedInput.cancel(raffleInputOwner);com.nezurstandalone.input.WindowActions.cancel(raffleInputOwner);
            if(raffleSlotOwned)com.nezurstandalone.control.InventoryOwner.release(this,true);
            raffleSlotOwned=false;raffleInventoryPending=false;
        }
        if(!RaffleController.isTicket(mc.thePlayer.getHeldItem()) || isAutoHealBusy())return;
        equipBestWeapon();
        if(!RaffleController.isTicket(mc.thePlayer.getHeldItem()))return;
        for(int slot=0;slot<9;slot++)if(!RaffleController.isTicket(mc.thePlayer.inventory.getStackInSlot(slot))) {
            if(com.nezurstandalone.control.InventoryOwner.select(this,slot))com.nezurstandalone.control.InventoryOwner.release(this,false);
            return;
        }
    }

    private void skipRaffleTicket(long now) {
        raffle.failedTicket(now);putAwayRaffleTickets();PathfinderManager.clear(this,true);
        rafflePathTicket=Integer.MIN_VALUE;raffleRepathAt=0;
    }

    private boolean sendRecoveryOof() {
        return !isAutoRaffleActive() && com.nezurstandalone.control.CommandCoordinator.send(this,"/oof");
    }

    private void handleRaffle(boolean inSpawn) {
        long now=com.nezurstandalone.control.Clock.millis();
        if(!raffleOwned) {
            raffleEnteringMid=true;raffleRepathAt=0;releaseEventApproach();
            PathfinderManager.clear(this,true);
        }
        raffleOwned=true;isOnBreak=false;
        releaseCombat();combat.resetTargeting();
        if(inSpawn) {raffleEnteringMid=true;putAwayRaffleTickets();handleSpawn();return;}
        if(spawnWalkStart!=0L)releaseSpawnWalk(true);
        if(raffleEnteringMid) {
            putAwayRaffleTickets();currentState=State.RAFFLE_TO_MID;
            if(approachEventMid())return;
            raffleEnteringMid=false;
        }
        if(raffle.updateDeposit()) {rafflePathTicket=Integer.MIN_VALUE;handleRaffleDeposit(now);return;}
        currentState=State.RAFFLE_COLLECTING;
        EntityItem ticket=raffle.findTicket(now);
        if(ticket==null || ticket.getEntityId()!=rafflePathTicket) {
            if(rafflePathTicket!=Integer.MIN_VALUE)PathfinderManager.clear(this,true);
            rafflePathTicket=Integer.MIN_VALUE;raffleRepathAt=0;
        }
        if(ticket==null) {putAwayRaffleTickets();approachEventMid();return;}
        releaseEventApproach();
        BlockPos stand=raffle.ticketStand(ticket);
        if(rafflePathTicket==ticket.getEntityId() && PathfinderManager.available(this)) {
            com.nezurstandalone.pathfinder.PathSnapshot route=PathfinderManager.getSnapshot();
            boolean failed=PathfinderManager.getState()==PathfinderManager.State.FAILED;
            boolean stoppedShort=PathfinderManager.getState()==PathfinderManager.State.COMPLETED
                    && mc.thePlayer.getDistanceToEntity(ticket)>1.0;
            boolean endsShort=false;
            if(!route.isEmpty() && stand!=null && stand.equals(route.getDestination())) {
                net.minecraft.util.Vec3 end=route.get(route.size()-1);
                endsShort=!RaffleTicketProgress.withinPickup(end.xCoord,end.yCoord,end.zCoord,ticket.posX,ticket.posY,ticket.posZ);
            }
            if(failed || stoppedShort || endsShort) {skipRaffleTicket(now);return;}
        }
        double horizontal=Math.hypot(ticket.posX-mc.thePlayer.posX,ticket.posZ-mc.thePlayer.posZ);
        if(horizontal<2.8 && Math.abs(ticket.posY-mc.thePlayer.posY)<1.5
                && (horizontal<.4 || eggForwardClear(ticket.posX,ticket.posZ))) {
            putAwayRaffleTickets(false);
            if(PathfinderManager.hasDestination() || AutoWalker.INSTANCE.isActive())PathfinderManager.clear(this,true);
            float[] aim=RotationUtils.getRotations(ticket);
            RotationManager.getInstance().setTargetRotation(RAFFLE_ROTATION_OWNER,RotationManager.PRIORITY_MACRO,
                    aim[0],aim[1],(float)interactAimSpeed.value,true);
            boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(aim[0]-mc.thePlayer.rotationYaw))<30;
            com.nezurstandalone.control.MovementKeys.set(RAFFLE_MOVEMENT_OWNER,mc.gameSettings.keyBindForward.getKeyCode(),aligned && horizontal>.25);
            return;
        }
        putAwayRaffleTickets();
        if(stand==null) {skipRaffleTicket(now);return;}
        if(now>=raffleRepathAt && (!PathfinderManager.isNavigatingTo(stand)
                || PathfinderManager.getState()==PathfinderManager.State.COMPLETED
                || PathfinderManager.getState()==PathfinderManager.State.CANCELLED)) {
            if(PathfinderManager.available(this)) {
                PathfinderManager.walkTo(this,stand.getX()+.5,stand.getY(),stand.getZ()+.5,true);
                rafflePathTicket=ticket.getEntityId();
            }
            raffleRepathAt=now+750;
        }
    }

    private void handleRaffleDeposit(long now) {
        releaseEventApproach();currentState=State.RAFFLE_RETURNING;
        BlockPos box=null;Vec3 visible=null;
        for(BlockPos candidate:raffle.boxes(now)) {
            Vec3 point=eggVisiblePoint(candidate);
            if(point!=null) {box=candidate;visible=point;break;}
        }
        if(box==null && !raffle.boxes(now).isEmpty())box=raffle.boxes(now).get(0);
        if(box==null) {putAwayRaffleTickets();approachEventMid();return;}
        double distance=mc.thePlayer.getDistance(box.getX()+.5,box.getY()+.5,box.getZ()+.5);
        if(distance<=5 && visible!=null && (mc.thePlayer.getPositionEyes(1.0F).distanceTo(visible)<=mc.playerController.getBlockReachDistance()
                || eggForwardClear(visible.xCoord,visible.zCoord))) {
            if(PathfinderManager.hasDestination() || AutoWalker.INSTANCE.isActive())PathfinderManager.clear(this,true);
            double dx=visible.xCoord-mc.thePlayer.posX, dz=visible.zCoord-mc.thePlayer.posZ;
            double dy=visible.yCoord-(mc.thePlayer.posY+mc.thePlayer.getEyeHeight());
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
            float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
            RotationManager.getInstance().setTargetRotation(RAFFLE_ROTATION_OWNER,RotationManager.PRIORITY_MACRO,
                    yaw,pitch,(float)interactAimSpeed.value,true);
            boolean inReach=mc.thePlayer.getPositionEyes(1.0F).distanceTo(visible)<=mc.playerController.getBlockReachDistance();
            boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-mc.thePlayer.rotationYaw))<30;
            com.nezurstandalone.control.MovementKeys.set(RAFFLE_MOVEMENT_OWNER,mc.gameSettings.keyBindForward.getKeyCode(),
                    !inReach && aligned && eggForwardClear(visible.xCoord,visible.zCoord));
            currentState=State.RAFFLE_DEPOSITING;
            if(!selectRaffleTickets())return;
            final BlockPos selectedBox=box;
            if(inReach && raffleClickRate.ready())GuardedInput.useBlock(raffleInputOwner,selectedBox,
                    () -> isToggled() && isAutoRaffleActive() && currentState==State.RAFFLE_DEPOSITING
                        && correctionTick<0 && !isAutoHealBusy() && PathfinderManager.available(this)
                        && com.nezurstandalone.control.InventoryOwner.owns(this) && RaffleController.isTicket(mc.thePlayer.getHeldItem())
                        && raffle.isBox(selectedBox) && mc.objectMouseOver!=null && mc.objectMouseOver.hitVec!=null
                        && mc.thePlayer.getPositionEyes(1.0F).distanceTo(mc.objectMouseOver.hitVec)<=mc.playerController.getBlockReachDistance(),
                    raffleClickRate::consume);
            return;
        }
        putAwayRaffleTickets();
        BlockPos stand=raffle.boxStand(box);
        if(stand!=null && now>=raffleRepathAt && (!PathfinderManager.isNavigatingTo(stand)
                || PathfinderManager.getState()==PathfinderManager.State.FAILED
                || PathfinderManager.getState()==PathfinderManager.State.COMPLETED
                || PathfinderManager.getState()==PathfinderManager.State.CANCELLED)) {
            PathfinderManager.walkTo(this,stand.getX()+.5,stand.getY(),stand.getZ()+.5,true);raffleRepathAt=now+1000;
        }
    }

    private boolean selectRaffleTickets() {
        if(raffleInventoryPending) {
            if(!com.nezurstandalone.input.WindowActions.ready(raffleInputOwner))return false;
            com.nezurstandalone.control.GuiLease.release(raffleInputOwner);raffleInventoryPending=false;
        }
        int slot=raffle.ticketSlot(true);
        if(slot>=0) {
            if(!com.nezurstandalone.control.InventoryOwner.select(this,slot))return false;
            raffleSlotOwned=true;return true;
        }
        int source=raffle.ticketSlot(false);
        if(source<0 || mc.thePlayer.inventory.getItemStack()!=null)return false;
        int destination=-1;float lowest=Float.MAX_VALUE;
        for(int i=8;i>=0;i--)if(i!=mc.thePlayer.inventory.currentItem) {
            ItemStack stack=mc.thePlayer.inventory.getStackInSlot(i);
            if(stack==null) {destination=i;break;}
            float score=getWeaponScore(stack);
            if(score<lowest) {lowest=score;destination=i;}
        }
        if(destination<0 || !com.nezurstandalone.control.GuiLease.acquire(raffleInputOwner))return false;
        if(!com.nezurstandalone.control.InventoryOwner.acquire(this)) {com.nezurstandalone.control.GuiLease.release(raffleInputOwner);return false;}
        raffleSlotOwned=true;
        com.nezurstandalone.input.WindowActions.click(raffleInputOwner,mc.thePlayer.inventoryContainer.windowId,source,destination,2);
        raffleInventoryPending=true;return false;
    }

    // ---- Spire --------------------------------------------------------------
    /** States that own the tick and must not be overwritten by Spire detection mid-flow. */
    private boolean isBusyFlow() {
        switch (currentState) {
            case PERK_NAVIGATING: case PERK_INTERACTING: case PERK_CLICKING_GUI:
            case PRESTIGE_NAVIGATING: case PRESTIGE_INTERACTING: case PRESTIGE_CLICKING_GUI:
            case PICKING_UP_MYSTIC:
            case LOBBY_SWAP: case LIMBO: case LOBBY_RECONNECT: case JOINING_PIT:
                return true;
            default:
                return false;
        }
    }

    private void updateSpireStatus() {
        if (!spireMode.isEnabled()) { spireActive = false; updateSpireFocusPause(false); return; }
        // Track the event, but never seize currentState away from an active perk/prestige/mystic
        // or lobby flow - doing so used to abort a perk purchase the instant Spire started.
        boolean canSetState = !isBusyFlow();
        boolean onBoard = SpireEntryPolicy.active(Utils.getScoreboardLines());
        boolean inside=SpireEntryPolicy.inside(Utils.getScoreboardTitle(),Utils.getScoreboardLines());
        int secs=SpireEntryPolicy.startSeconds(BossStatus.bossName,BossStatus.statusBarTime);

        // Expired boss text must not override a live event sidebar or trap us in pre-start wait.
        if (!inside && !onBoard && secs>=0) {
            spireActive = true;
            spireEndStamp = com.nezurstandalone.control.Clock.millis();
            if (canSetState) currentState = secs > 50 ? State.SPIRE_WAITING_SPAWN
                    : (secs >= 0 ? State.SPIRE_WALK_TO_MID : currentState);
        } else if (onBoard || inside) {
            spireActive = true;
            spireEndStamp = com.nezurstandalone.control.Clock.millis();
            if (spireActiveStartTime == 0) spireActiveStartTime = com.nezurstandalone.control.Clock.millis();
            if (canSetState) {
                if(inside && currentState!=State.SPIRE_FIGHTING){releaseEventApproach();releaseSpawnWalk(true);PathfinderManager.clear(this,true);}
                currentState = inside?State.SPIRE_FIGHTING:State.SPIRE_WALK_TO_MID;
            }
        } else if (spireActive) {
            long dur = spireActiveStartTime != 0 ? com.nezurstandalone.control.Clock.millis() - spireActiveStartTime : 0;
            if (dur > 310000L || (spireActiveStartTime == 0 && com.nezurstandalone.control.Clock.millis() - spireEndStamp > 10000L)
                    || com.nezurstandalone.control.Clock.millis() - spireEndStamp > 7000L) {
                spireActive = false; spireActiveStartTime = 0; combat.resetTargeting(); combat.stop();
                if (currentState == State.SPIRE_WAITING_SPAWN || currentState == State.SPIRE_WALK_TO_MID || currentState == State.SPIRE_FIGHTING) {
                    currentState = State.IDLE;
                }
            }
        }
        updateSpireFocusPause(isSpireActive());
    }

    private void updateSpireFocusPause(boolean running) {
        Focus focus = com.nezurstandalone.Nezur.moduleManager != null
                ? com.nezurstandalone.Nezur.moduleManager.getModuleByClass(Focus.class) : null;
        if (focus == null) { spireFocusPaused = false; return; }
        if (running && !spireFocusPaused) { focus.pauseForSpire(); spireFocusPaused = true; }
        else if (!running && spireFocusPaused) { focus.resumeAfterSpire(); spireFocusPaused = false; }
    }

    private boolean isSpireActive() { return spireMode.isEnabled() && spireActive; }

    private void handleSpire() {
        // Event combat must not inherit a paused work/break shift from ordinary grinding.
        isOnBreak=false;
        if (currentState == State.SPIRE_WAITING_SPAWN) {
            pauseGameplay(); PathfinderManager.clear(this, true); // CLEAR on entry and while waiting.
            String zone = PitMapManager.getZone(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            if (!Utils.isInSpawn() && !zone.equals("Spawn")) {
                long t = com.nezurstandalone.control.Clock.millis();
                if (t - oofCooldown > 3000L && t >= throttleCooldownEnd) {
                    if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/spawn")) return; oofCooldown = t;
                }
            } else { releaseCombat(); PathfinderManager.clear(this, true); }
        } else if (currentState == State.SPIRE_WALK_TO_MID) {
            releaseCombat();
            // A late arrival must still leave spawn and reach the entry at mid.
            if(Utils.isInSpawn())handleSpawn();
            else {if(spawnWalkStart!=0L)releaseSpawnWalk(true);approachEventMid(1.0);}
        } else if (currentState == State.SPIRE_FIGHTING) {
            long now = com.nezurstandalone.control.Clock.millis();
            // Spire: fight everyone in reach regardless of zone.
            equipBestWeapon();
            combatPermitted=true;
            EntityPlayer target = combat.pickTarget();
            if (target != null) {
                combatPermitted = true;
                driveCombatMovement(target);
                lastAttackTime = now;
            } else { 
                releaseCombat(); 
                if (com.nezurstandalone.pathfinder.AutoWalker.INSTANCE.isActive() || com.nezurstandalone.pathfinder.PathfinderManager.hasDestination()) {
                    com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
                    com.nezurstandalone.pathfinder.PathfinderManager.clear(this, true);
                }
                combatMoveStuckSince = 0L; 
            }
        }
    }

    // ---- Squads -------------------------------------------------------------
    private void handleSquads(boolean inSpawn) {
        int rank = squadRank();
        long now = com.nezurstandalone.control.Clock.millis();
        if (rank == -1 || rank > 7) {
            if (!lowMidTracking) { lowMidTracking = true; lowMidStamp = now; }
            if (now - lowMidStamp > 8000L) {
                lowMidTracking = false; squadSwapForced = true;
                if (!inSpawn && now - oofCooldown > 3000L && now >= throttleCooldownEnd) {
                    if (!sendRecoveryOof()) return; oofCooldown = now;
                }
                enterLobbySwap();
                return;
            }
        } else {
            lowMidTracking = false;
        }
        // Ranked well enough - just sit idle.
        releaseCombat();
        PathfinderManager.clear(this, true);
        com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
        currentState = State.IDLE;
    }

    private int squadRank() {
        for (String line : Utils.getScoreboardLines()) {
            Matcher m = SQUAD_RANK.matcher(StringUtils.stripControlCodes(line));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private boolean isSquadsEventActive() {
        if (!squadSupport.isEnabled()) return false;
        for (String line : Utils.getScoreboardLines()) {
            String u = StringUtils.stripControlCodes(line).toUpperCase();
            if (u.contains("EVENT:") && u.contains("SQUAD")) return true;
        }
        return false;
    }

    private boolean isAnyEventActive() {
        return isAutoRaffleActive() || isAutoRobberyActive() || isAutoBlockheadActive() || isSpireActive() || isSquadsEventActive() || isRagePitEventActive()
                || CombatAura.isTdmEventActive()
                || (dragonEggSupport.isEnabled() && currentEgg != null)
                || nightQuestActive;
    }

    private boolean isRagePitEventActive() {
        for (String line : Utils.getScoreboardLines()) {
            String u = StringUtils.stripControlCodes(line).toUpperCase();
            if (u.contains("EVENT:") && u.contains("RAGE PIT")) return true;
        }
        return false;
    }

    // ---- lobby swap / reconnect --------------------------------------------
    private boolean shouldLobbySwap() {
        if(AutoReconnect.holdLobbySwapForCarePackage())return false;

        if(com.nezurstandalone.contract.GoldPickupRecovery.ignoresPopulation(com.nezurstandalone.contract.ContractCombatPolicy.type()))return false;
        // Classic has no verified Hypixel lobby/rejoin command contract. Stay and grind.
        if (isClassicPitTitle(Utils.getScoreboardTitle())) return false;
        // Night-quest hold: never swap while a quest runs; near quest time, only stay in a full mid.
        if (nightQuestSupport.isEnabled()) {
            if (nightQuestActive) return false;
            if (nextNightQuestTime > 0) {
                long dt = nextNightQuestTime - com.nezurstandalone.control.Clock.millis();
                if (dt > 0 && dt <= 120000L) return getMidPlayerCount() < (int) minMidPlayers.value;
            }
        }
        return getMidPlayerCount() < (int) minMidPlayers.value;
    }

    private int getMidPlayerCount() {
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - lastMidScan < 2000L) return cachedMidCount;
        lastMidScan = now;
        int count = 0;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || Focus.isHidden(p)) continue;
            if (!isClassicPitTitle(Utils.getScoreboardTitle()) && mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(p.getUniqueID()) == null) continue;
            if (p.isDead || p.getHealth() <= 0 || p.isInvisible()) continue;

            if (PitMapManager.getZone(p.posX, p.posY, p.posZ).equals("Pit")) count++;
        }
        cachedMidCount = count;
        return count;
    }

    private void enterLobbySwap() {
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        currentState = State.LOBBY_SWAP;
        lobbyFirstCommand = false;
        lobbyStamp = com.nezurstandalone.control.Clock.millis();
        releaseCombat();
        PathfinderManager.clear(this, true);
    }

    private void handleLobbySwapInPit() {
        if(AutoReconnect.holdLobbySwapForCarePackage()){currentState=State.IDLE;squadSwapForced=false;return;}

        if(com.nezurstandalone.contract.GoldPickupRecovery.ignoresPopulation(com.nezurstandalone.contract.ContractCombatPolicy.type())) {
            currentState=State.IDLE;lobbyFirstCommand=false;squadSwapForced=false;return;
        }
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        if (!squadSwapForced && !shouldLobbySwap()) { currentState = State.IDLE; lobbyFirstCommand = false; return; }
        long delay = lobbyFirstCommand ? 5000L : 1000L;
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - lobbyStamp < delay || now < throttleCooldownEnd) return;
        if (Utils.isInSpawn()) {
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!AutoReconnect.requestLobbySwap(this,this::isToggled)) return;
            lobbyFirstCommand = true;
            squadSwapForced = false;
            lobbyStamp = now;
        }
    }

    private void handleLobbyReconnect() {
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        releaseCombat();PathfinderManager.clear(this,true);
        AutoReconnect.enableRecovery();currentState=State.LOBBY_RECONNECT;
    }

    private void handleLimbo() {
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        releaseCombat();PathfinderManager.clear(this,true);
        AutoReconnect.enableRecovery();currentState=State.LIMBO;
    }

    // ---- dragon egg ---------------------------------------------------------
    // Farmed with the exact ChestAura recipe: approach with the walker, aim at a raytrace-verified
    // visible point through the rotation manager, and right-click only when the crosshair is truly
    // on the egg block, we are not mid-dig, and the humanised rate allows it. No blind interacts.
    private final com.nezurstandalone.utils.NearestEggScan eggScan = new com.nezurstandalone.utils.NearestEggScan();
    private void scanDragonEgg() {
        long now = com.nezurstandalone.control.Clock.millis();
        if (currentEgg != null && !com.nezurstandalone.utils.DragonEggTracker.usable(currentEgg)) currentEgg=null;
        // The shared section index can take seconds to reach a nearby egg. A small loaded-only
        // local pass gives prompt mid detection without loading chunks or scanning the whole world.
        if (currentEgg == null && now >= eggScanStamp) {
            eggScanStamp=now+500L;
            BlockPos origin=new BlockPos(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
            double best=Double.MAX_VALUE;
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-4;y<=4;y++) {
                BlockPos at=origin.add(x,y,z);
                if(mc.theWorld.isBlockLoaded(at) && mc.theWorld.getBlockState(at).getBlock()==Blocks.dragon_egg
                        && com.nezurstandalone.utils.DragonEggTracker.usable(at)) {
                    double d=mc.thePlayer.getDistanceSq(at.getX()+0.5,at.getY()+0.5,at.getZ()+0.5);
                    if(d<best){best=d;currentEgg=at;}
                }
            }
        }
        BlockPos indexed=eggScan.step(16384);
        // A partially scanned index is not evidence that our validated egg disappeared.
        if(currentEgg==null && indexed!=null)currentEgg=indexed;
    }

    private void handleDragonEgg() {
        BlockPos egg = currentEgg;
        if (egg == null) return;
        String eggZone=PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        boolean inEggSpawn=Utils.isInSpawn() || "Spawn".equals(eggZone) || "Overspawn".equals(eggZone);
        boolean inEggMid="Pit".equals(eggZone);
        if(inEggSpawn) {
            // The normal grinder already handles the spawn drop without building a route.
            // Preserve its forward/sprint/jump cadence until we have actually left spawn.
            com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
            GuardedInput.cancel(interactionOwner);
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            handleSpawn();
            return;
        }
        boolean entering = currentState != State.DRAGON_EGG || !egg.equals(lastEggPos);
        currentState = State.DRAGON_EGG;
        releaseCombat(); combat.resetTargeting();
        long now = com.nezurstandalone.control.Clock.millis();
        if (entering) {
            // Discard the old chase/destination exactly once; isPathing alone includes combat.
            PathfinderManager.clear(this,true); GuardedInput.cancel(interactionOwner);
            releaseSpawnWalk(true);
            com.nezurstandalone.control.MovementKeys.release("grinder-npc");
            com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
            RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);
            lastEggPos=egg; eggNavRepathAt=0; eggUnstuckAttempts=0;
            eggUnstuckUntil=0; eggStationaryStart=now; eggApproachBestDistance=Double.MAX_VALUE;
        }
        double cx=egg.getX()+0.5,cy=egg.getY()+0.5,cz=egg.getZ()+0.5;
        double dist=mc.thePlayer.getDistance(cx,cy,cz);
        Vec3 vp=eggVisiblePoint(egg);
        // Take over the final approach before the old three-block stopping point. The walker
        // must release its steering/keys first so aiming at the egg also drives us toward it.
        // Mid is an open direct-walk area: never create an egg route there. Outside mid,
        // retain pathfinding for the long approach and take over directly within reach.
        boolean near = inEggMid || (dist<=4.0 && vp!=null);
        boolean stopped = dist<=2.0;
        boolean direct = near && (stopped || ((inEggMid || now>=eggUnstuckUntil) && eggForwardClear(cx,cz)));
        if (direct && !stopped && !inEggMid) {
            if (dist<eggApproachBestDistance-0.05) {
                eggApproachBestDistance=dist; eggStationaryStart=now;
            } else if (now-eggStationaryStart>=2000L) {
                // A visible egg can still be separated by a ledge or obstruction. Let the
                // walker choose another neighbour instead of pressing into it indefinitely.
                direct=false; eggUnstuckUntil=now+1500L;
                eggApproachBestDistance=Double.MAX_VALUE; eggStationaryStart=now;
            }
        }
        if(direct) {
            if(PathfinderManager.isPathing())PathfinderManager.clear(this,true);
            Vec3 aim=vp!=null?vp:new Vec3(cx,cy,cz);
            double dx=aim.xCoord-mc.thePlayer.posX;
            double dy=aim.yCoord-(mc.thePlayer.posY+mc.thePlayer.getEyeHeight());
            double dz=aim.zCoord-mc.thePlayer.posZ;
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
            float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
            RotationManager.getInstance().setTargetRotation(INTERACTION_ROTATION_OWNER,
                    RotationManager.PRIORITY_MACRO,yaw,pitch,(float)interactAimSpeed.value,true);
            float yawError=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-mc.thePlayer.rotationYaw));
            com.nezurstandalone.control.MovementKeys.set(EGG_MOVEMENT_OWNER,
                    mc.gameSettings.keyBindForward.getKeyCode(),!stopped && yawError<=30.0F);
            eggApproachSpeed(!stopped && yawError<=30.0F,dist);
            if(vp!=null)legitClickBlock(egg);
            else GuardedInput.cancel(interactionOwner);
            return;
        }
        com.nezurstandalone.control.MovementKeys.release(EGG_MOVEMENT_OWNER);
        GuardedInput.cancel(interactionOwner);
        RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
        if(inEggMid) {
            PathfinderManager.clear(this,true);
            return;
        }
        // Preserve an active approach (including a no-line-of-sight sidestep) instead of stopping
        // it each tick. Failed/completed routes choose another walkable neighbour, never the egg.
        if(now>=eggNavRepathAt && (!PathfinderManager.isPathing()
                || PathfinderManager.getState()==PathfinderManager.State.FAILED
                || PathfinderManager.getState()==PathfinderManager.State.COMPLETED)) {
            BlockPos stand=eggApproachSpot(egg);
            eggNavRepathAt=now+1000L;
            if(stand!=null)PathfinderManager.walkTo(this,stand.getX()+0.5,stand.getY(),stand.getZ()+0.5,true);
        }
        eggApproachSpeed(AutoWalker.INSTANCE.isActive(),dist);
    }

    private void eggApproachSpeed(boolean moving,double distance) {
        boolean fast=moving && distance>3.0;
        com.nezurstandalone.control.MovementKeys.set(EGG_MOVEMENT_OWNER,mc.gameSettings.keyBindSprint.getKeyCode(),fast);
        com.nezurstandalone.control.MovementKeys.set(EGG_MOVEMENT_OWNER,mc.gameSettings.keyBindJump.getKeyCode(),fast);
    }

    /** Probe the next walking step, including floor support; visibility alone is not a route. */
    private boolean eggForwardClear(double x,double z) {
        double dx=x-mc.thePlayer.posX,dz=z-mc.thePlayer.posZ;
        double length=Math.sqrt(dx*dx+dz*dz);
        if(length<0.001)return false;
        net.minecraft.util.AxisAlignedBB step=mc.thePlayer.getEntityBoundingBox()
                .offset(dx/length*0.45,0,dz/length*0.45);
        if(!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,step).isEmpty())return false;
        net.minecraft.util.AxisAlignedBB floor=new net.minecraft.util.AxisAlignedBB(
                step.minX,step.minY-0.6,step.minZ,step.maxX,step.minY,step.maxZ);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,floor).isEmpty();
    }

    private BlockPos eggApproachSpot(BlockPos egg) {
        java.util.List<BlockPos> spots=new java.util.ArrayList<>();
        for(int r=1;r<=2;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
            for(int y=-1;y<=1;y++) {
                BlockPos at=egg.add(x,y,z);
                if(mc.theWorld.isBlockLoaded(at) && mc.theWorld.isBlockLoaded(at.up())
                        && mc.theWorld.isBlockLoaded(at.down())
                        && !mc.theWorld.getBlockState(at).getBlock().getMaterial().blocksMovement()
                        && !mc.theWorld.getBlockState(at.up()).getBlock().getMaterial().blocksMovement()
                        && mc.theWorld.getBlockState(at.down()).getBlock().getMaterial().isSolid())spots.add(at);
            }
        }
        spots.sort(java.util.Comparator.comparingDouble(at -> mc.thePlayer.getDistanceSq(at.getX()+0.5,at.getY(),at.getZ()+0.5)));
        return spots.isEmpty()?null:spots.get((eggUnstuckAttempts++ & Integer.MAX_VALUE)%spots.size());
    }

    /** Crosshair-validated, not-mid-dig, rate-limited right-click on a block. ChestAura's gate. */
    private void legitClickBlock(BlockPos pos) {
        if (!eggClickRate.ready()) return;
        if (mc.playerController.getIsHittingBlock()) return;
        // GuardedInput checks the fresh native raycast after the camera update. Checking the
        // previous tick's objectMouseOver here lost clicks while turning during the approach.
        GuardedInput.useBlock(interactionOwner, pos, () -> isToggled() && !isAutoHealBusy()
                && dragonEggSupport.isEnabled() && currentState==State.DRAGON_EGG
                && PathfinderManager.available(this) && pos.equals(currentEgg)
                && mc.theWorld.isBlockLoaded(pos)
                && com.nezurstandalone.utils.DragonEggTracker.usable(pos), eggClickRate::consume);
    }

    /** First raytraced point on the block visible from the eyes, or null. */
    private Vec3 eggVisiblePoint(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3[] points = new Vec3[] {
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.2, pos.getY() + 0.5, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.8, pos.getY() + 0.5, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.2),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.8)
        };
        for (Vec3 point : points) {
            MovingObjectPosition ray = mc.theWorld.rayTraceBlocks(eyes, point, false, false, false);
            if (ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && ray.getBlockPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }

    // ---- mystic pickup ------------------------------------------------------
    private void handleMysticPickup() {
        if (!mysticPickup.isEnabled() || classicRuntime() && com.nezurstandalone.control.Clock.millis()-mysticPickupStarted>30000) { finishMysticPickup(); return; }
        releaseCombat();
        if (classicRuntime() && mysticPickupStack != null && countMatchingMystics(mysticPickupStack)>mysticInventoryBefore) {
            finishMysticPickup();return;
        }
        if (startMysticStow()) return;
        EntityItem target = findNearestMystic();
        long now = com.nezurstandalone.control.Clock.millis();

        if (mysticPickupStack != null && countMatchingMystics(mysticPickupStack) > mysticInventoryBefore) {
            finishMysticPickup();
            return;
        }

        if (target != null) {
            sawMysticItem = true;
            mysticMissingSince = 0L;
            if (mysticTargetId != target.getEntityId()) {
                mysticTargetId = target.getEntityId();
                mysticRepathAt = 0L;
                mysticPickupStack = ItemStack.copyItemStack(target.getEntityItem());
                mysticInventoryBefore = countMatchingMystics(mysticPickupStack);
                com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_TARGET",
                        "id="+mysticTargetId+" item="+target.getEntityItem().getDisplayName()
                        +" distance="+mc.thePlayer.getDistanceToEntity(target));
            }
        } else {
            // Entity disappearance is not proof that our inventory received it. Allow the
            // server's inventory update to arrive, then leave only if the drop is truly gone.
            if (mysticMissingSince == 0L) mysticMissingSince = now;
            if(classicRuntime()) {
                PathfinderManager.clear(this,true);
                com.nezurstandalone.control.MovementKeys.release("grinder");
                RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);
                if(now-mysticMissingSince>=500L){finishMysticPickup();return;}
            }else if (now - mysticMissingSince > 5000L && now - lastMysticChatTime > 8000L)
                finishMysticPickup();
            if (now - lastMysticTraceTime > 1000L) {
                lastMysticTraceTime = now;
                com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_SCAN",
                        "found=false ageMs="+(now-lastMysticChatTime)+" entities="+mc.theWorld.loadedEntityList.size());
            }
            return;
        }

        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double horizontalSq = dx * dx + dz * dz;
        double dy = target.posY - mc.thePlayer.posY;
        // Inside 1.5 blocks the item itself, not a path node, owns the final approach.
        if (horizontalSq + dy * dy <= 2.25) {
            if (PathfinderManager.isPathing()) PathfinderManager.clear(this, true);
            float yaw = horizontalSq < 0.01 ? mc.thePlayer.rotationYaw
                    : (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(Math.atan2(
                    mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - target.posY - 0.15,
                    Math.sqrt(horizontalSq)));
            RotationManager.getInstance().setTargetRotation(MOVEMENT_ROTATION_OWNER,
                    RotationManager.PRIORITY_LEGACY, yaw, pitch, 9.0f);
            float error = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindForward.getKeyCode(),
                    horizontalSq > 0.16 && Math.abs(error) < 35.0f);
            com.nezurstandalone.control.MovementKeys.set("grinder", mc.gameSettings.keyBindSprint.getKeyCode(), false);
            if (now - lastMysticTraceTime > 1000L) {
                lastMysticTraceTime = now;
                com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_APPROACH",
                        "distance="+Math.sqrt(horizontalSq)+" yawError="+error);
            }
            return;
        }

        com.nezurstandalone.control.MovementKeys.release("grinder");
        RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);
        BlockPos destination = new BlockPos(target.posX, target.posY, target.posZ);
        if (now >= mysticRepathAt && (!PathfinderManager.isNavigatingTo(destination)
                || PathfinderManager.getState() == PathfinderManager.State.FAILED
                || PathfinderManager.getState() == PathfinderManager.State.CANCELLED
                || !PathfinderManager.isPathing())) {
            PathfinderManager.State route = PathfinderManager.walkTo(this, target.posX, target.posY, target.posZ, true);
            mysticRepathAt = now + 750L;
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_ROUTE",
                    "id="+mysticTargetId+" state="+route+" distance="+mc.thePlayer.getDistanceToEntity(target));
        }
    }

    private int countMatchingMystics(ItemStack target) {
        if (target == null || mc.thePlayer == null) return 0;
        int count = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null && stack.getItem() == target.getItem()
                    && stack.getItemDamage() == target.getItemDamage()
                    && (classicRuntime()?ClassicMysticPickup.same(net.minecraft.item.Item.getIdFromItem(stack.getItem()),stack.getItemDamage(),stack.getDisplayName(),
                        net.minecraft.item.Item.getIdFromItem(target.getItem()),target.getItemDamage(),target.getDisplayName())
                        :ItemStack.areItemStackTagsEqual(stack, target)&&stack.getDisplayName().equals(target.getDisplayName()))) count += stack.stackSize;
        }
        return count;
    }

    /** Move clearly labelled fresh drops out of a full hotbar using a normal inventory shift-click. */
    /** Open the inventory once when the hotbar fills and stow recognized mystic gear. */
    private boolean startMysticStow() {
        if (mc.thePlayer == null || mc.currentScreen != null || mc.thePlayer.isUsingItem() || isAutoHealBusy()
                || currentState == State.PERK_NAVIGATING || currentState == State.PERK_INTERACTING
                || currentState == State.PRESTIGE_NAVIGATING || currentState == State.PRESTIGE_INTERACTING
                || !com.nezurstandalone.control.GuiLease.available(this)
                || !com.nezurstandalone.control.InventoryOwner.available(this)) return false;
        if (com.nezurstandalone.control.Clock.millis() < mysticStowRetryAt) return false;
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        boolean full = true;
        for (int i = 0; i < 9; i++) if (inv[i] == null) { full = false; break; }
        if (full) mysticStowAll = true;
        if (!mysticStowAll || !hasMysticStowStorage()) return false;
        int slot = findMysticStowSlot();
        if (slot < 0) { mysticStowAll = false; return false; }
        if (!com.nezurstandalone.control.GuiLease.acquire(this)) return false;
        if (!com.nezurstandalone.control.InventoryOwner.acquire(this)) {
            com.nezurstandalone.control.GuiLease.release(this); return false;
        }
        mysticStowSlot = slot;
        mysticStowStarted = com.nezurstandalone.control.Clock.millis();
        mysticStowScreen = new GuiInventory(mc.thePlayer);
        mc.displayGuiScreen(mysticStowScreen);
        com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", "open hotbar="+slot);
        return true;
    }

    private int findMysticStowSlot() {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        // Slot 0 is the user's primary combat sword: never quick-move or rearrange it.
        for (int i = 1; i < 9; i++) {
            if (isMysticHotbarItem(inv[i])) return i;
        }
        return -1;
    }

    private boolean hasMysticStowStorage() {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 9; i < 36; i++) if (inv[i] == null) return true;
        return false;
    }
    private boolean isMysticHotbarItem(ItemStack stack) {
        if (stack == null) return false;
        String name = stack.hasDisplayName()
                ? StringUtils.stripControlCodes(stack.getDisplayName()).toLowerCase(java.util.Locale.ROOT) : "";
        if (name.contains("fresh") || name.contains("mystic") || name.contains("tier")) return true;
        // Pit fresh gear can have a custom display label unrelated to its item type.
        // Treat supported gear as stowable only during the mystic-pickup flow, and slot 0
        // is excluded by the caller so the equipped primary weapon stays untouched.
        net.minecraft.item.Item item = stack.getItem();
        return item == Items.golden_sword || item == Items.bow || item == Items.leather_leggings
                || item == Items.golden_helmet || item == Items.diamond_sword || item == Items.diamond_leggings;
    }
    private void handleMysticStow() {
        if (mc.thePlayer == null || mc.currentScreen != mysticStowScreen
                || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                || com.nezurstandalone.control.Clock.millis() - mysticStowStarted > 6000L) {
            cancelMysticStow(); return;
        }
        if(!com.nezurstandalone.control.GuiLease.owns(this) || !com.nezurstandalone.control.InventoryOwner.owns(this)){
            cancelMysticStow();return;
        }
        try{if(!com.nezurstandalone.input.WindowActions.ready(this))return;}
        catch(RuntimeException failure){
            com.nezurstandalone.control.GrinderDiagnostics.capture("MYSTIC_STOW_UNKNOWN",failure.getMessage());
            NotificationManager.show("[AutoGrinder] Inventory move unconfirmed. Inspect inventory before restarting.",5000);
            setToggled(false);return;
        }
        // Vanilla prediction is confirmed by its exact S32; time alone cannot advance the batch.
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - mysticStowStarted < 50L) return;
        int slot = findMysticStowSlot();
        if (slot < 0 || !hasMysticStowStorage()) {
            mysticStowAll = false;
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", slot < 0 ? "complete" : "storage_full");
            cancelMysticStow();
            return;
        }
        try {
            com.nezurstandalone.input.WindowActions.click(this,mc.thePlayer.inventoryContainer.windowId,
                    slot + 36,0,1);
            mysticStowStarted = now;
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", "vanilla_quick_move hotbar="+slot);
        } catch (RuntimeException ex) {
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", "failed="+ex.getMessage());
            mysticStowRetryAt = now + 3000L;
            cancelMysticStow();
        }
    }
    private boolean cancelledMysticStowScreen(){
        if(mysticStowScreen==null || mc.currentScreen==mysticStowScreen)return false;
        boolean pending=com.nezurstandalone.input.WindowActions.pending(this);cancelMysticStow();
        if(!pending)return false;
        NotificationManager.show("[AutoGrinder] Inventory closed before its result. Inspect inventory before restarting.",5000);
        setToggled(false);return true;
    }
    private void cancelMysticStow() {
        com.nezurstandalone.input.WindowActions.cancel(this);
        if (mc.thePlayer != null && mysticStowScreen != null
                && mc.currentScreen == mysticStowScreen) mc.thePlayer.closeScreen();
        mysticStowScreen = null;
        mysticStowSlot = -1;
        com.nezurstandalone.control.GuiLease.release(this);
        com.nezurstandalone.control.InventoryOwner.release(this, false);
    }

    private void finishMysticPickup() {
        com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_FINISH",
                "seen="+sawMysticItem+" matched="+(mysticPickupStack != null
                        && countMatchingMystics(mysticPickupStack) > mysticInventoryBefore));
        sawMysticItem = false;
        mysticPickupStack = null;
        mysticMissingSince = 0L;
        mysticTargetId = -1;
        currentState = classicRuntime()&&!Utils.isInSpawn()?State.FIGHTING:State.IDLE;
        if(classicRuntime()){
            mysticRepathAt=0L;mysticPickupStarted=0L;
        }
        PathfinderManager.clear(this, true);
        com.nezurstandalone.control.MovementKeys.release("grinder");
        RotationManager.getInstance().clearTarget(MOVEMENT_ROTATION_OWNER);
    }

    private EntityItem findNearestMystic() {
        EntityItem best = null; double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityItem)) continue;
            EntityItem ei = (EntityItem) e;
            if (ei.isDead || ei.getEntityItem() == null || ei.getEntityItem().stackSize <= 0) continue;
            if(classicRuntime()&&!ClassicMysticPickup.named(ei.getEntityItem().getDisplayName()))continue;
            double d = mc.thePlayer.getDistanceToEntity(ei);
            if (ei.getEntityId() == mysticTargetId && d < 48.0) return ei;
            if (d > 35.0) continue;
            boolean named = isMysticItem(ei);
            String dropName=StringUtils.stripControlCodes(ei.getEntityItem().getDisplayName()).toLowerCase(java.util.Locale.ROOT);
            boolean identified=dropName.contains("mystic")||dropName.contains("fresh")||dropName.contains("tier");
            if(ei.ticksExisted>(classicRuntime()&&identified?1200:120))continue;
            net.minecraft.item.Item item = ei.getEntityItem().getItem();
            boolean gear = item instanceof ItemSword || item instanceof net.minecraft.item.ItemBow
                    || item instanceof net.minecraft.item.ItemArmor;
            double fromDrop = Math.sqrt(Math.pow(ei.posX-mysticDropX, 2)
                    + Math.pow(ei.posZ-mysticDropZ, 2));
            if (fromDrop > 16.0) continue;
            if (!named && !(gear && ei.ticksExisted < 60 && fromDrop < 12.0)) continue;
            double score = d + fromDrop * 0.5 + (named ? 0.0 : 8.0) + ei.ticksExisted * 0.03;
            if (score < bestScore) { bestScore = score; best = ei; }
        }
        return best;
    }

    private boolean isMysticItem(EntityItem entity) {
        if (entity == null || entity.getEntityItem() == null) return false;
        ItemStack stack = entity.getEntityItem();
        net.minecraft.item.Item item = stack.getItem();
        if (item == Items.golden_sword || item == Items.bow || item == Items.leather_leggings
                || item == Items.golden_helmet || item == Items.diamond_sword || item == Items.diamond_leggings) {
            return true;
        }
        if (stack.hasDisplayName()) {
            String name = StringUtils.stripControlCodes(stack.getDisplayName()).toLowerCase();
            if (name.contains("mystic") || name.contains("fresh") || name.contains("tier")
                    || name.contains("pant") || name.contains("sword") || name.contains("bow") || name.contains("helm")) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Perk / Prestige NPC interaction: pathfind to two blocks away, open with a crosshair-
    // validated interact, and sidestep to another spot if the menu never opens.
    // =========================================================================
    private boolean checkPerkTriggers() {
        if (System.nanoTime() < npcRetryAfterNanos || !Utils.isInSpawn()
                || !com.nezurstandalone.control.GuiLease.available(this) || !PathfinderManager.available(this)) return false;
        boolean contract=hasTemporaryPerkLease();
        if(contract) {
            if(!perksNeedSync || contractKungFuReady && !contractKungFuRestoring)return false;
        } else if(!managedActionReady())return false;
        if(!com.nezurstandalone.control.GuiLease.acquire(this))return false;
        log(contract?"Preparing contract-owned perk operation.":"Affordable/free loadout action queued - heading to UPGRADES NPC.");
        pauseGameplay();resetMenuTracking();
        if(!com.nezurstandalone.control.GuiLease.acquire(this))return false;
        managedVisit=!contract;chooserSlot=null;
        npcPosIndex=0;npcSpotStartedAt=interactStamp=navRepathAt=0L;
        currentState=State.PERK_NAVIGATING;
        return true;
    }

    private boolean startPrestigeProcess() {
        if (!Utils.isInSpawn() || prestigeGold.known() && !prestigeGold.ready()) return false;
        if (System.nanoTime() < npcRetryAfterNanos) return false;
        if (findNpc(true) == null) return false;
        // A blocked prestige request retains priority over upgrades and ordinary grinding.
        if(!reserveNpcAccess())return true;
        pauseGameplay();
        resetMenuTracking();
        if(!reserveNpcAccess())return true;
        log("Prestige available — heading to PRESTIGE NPC.");
        npcPosIndex = 0;
        npcSpotStartedAt = 0L;
        interactStamp = 0L;
        navRepathAt = 0L;
        prestigeConfirmStarted = false;
        currentState = State.PRESTIGE_NAVIGATING;
        return true;
    }

    /** Walk to a standing spot ~2 blocks from the NPC, then hand off to interaction. */
    private void handleNpcNavigation(boolean prestige) {
        if(!prestige && managedVisit && !managedActionReady()) {
            finishNpcFlow();return;
        }
        if(!reserveNpcAccess())return;
        Entity npc = findNpc(prestige);
        if (npc == null) { abortNpc("NPC vanished."); return; }
        if (!Utils.isInSpawn()) { abortNpc("Fell out of spawn."); return; }

        double[] off = NPC_OFFSETS[npcPosIndex % NPC_OFFSETS.length];
        double sx = npc.posX+off[0];
        double sz = npc.posZ+off[1];
        double distToNpc = mc.thePlayer.getDistanceToEntity(npc);
        double spotDistance=Math.hypot(mc.thePlayer.posX-sx,mc.thePlayer.posZ-sz);

        // Close enough to interact from: stop the walker completely and switch to aiming. This
        // fires on distance-to-NPC alone (any spot within reach), so a path that stops a little
        // short still hands off - the old 1.5-3.0 window could be missed and it never aimed.
        if (distToNpc <= 3.0 && (npcPosIndex==0 && distToNpc<=2.5 || spotDistance<=1.5)) {
            com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
            PathfinderManager.clear(this, true);
            com.nezurstandalone.control.MovementKeys.release("grinder-npc");
            interactStamp = com.nezurstandalone.control.Clock.millis();
            currentState = prestige ? State.PRESTIGE_INTERACTING : State.PERK_INTERACTING;
            return;
        }
        long now = com.nezurstandalone.control.Clock.millis();
        if (npcSpotStartedAt == 0L) npcSpotStartedAt = now;
        if (now - npcSpotStartedAt >= 8000L || (navRepathAt != 0L
                && PathfinderManager.getState() == PathfinderManager.State.FAILED)) {
            advanceNpcSpot(prestige);
            return;
        }
        if (now >= navRepathAt && !PathfinderManager.isPathing()) {
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            PathfinderManager.walkTo(this, sx, npc.posY, sz, true);
            navRepathAt = now + 1200L;
        }
    }

    /** Aim at the villager and open it with a crosshair-validated interact; retry from a new spot. */
    private void handleNpcInteract(boolean prestige) {
        if(!prestige && managedVisit && !managedActionReady()) {
            finishNpcFlow();return;
        }
        if(!reserveNpcAccess())return;
        Entity npc = findNpc(prestige);
        if (npc == null) { abortNpc("NPC vanished."); return; }

        // GUI opened — move on to clicking it.
        if (mc.currentScreen instanceof GuiChest && ownsMenu((GuiChest) mc.currentScreen)) {
            guiOpenStamp = System.nanoTime();
            currentState = prestige ? State.PRESTIGE_CLICKING_GUI : State.PERK_CLICKING_GUI;
            return;
        }
        // Any other screen open: do nothing (no rotation, no click) until it clears.
        if (mc.currentScreen != null) return;

        // The walker must not steer while we line up the click, or the camera fights the aim and
        // the crosshair never settles on the villager. Keep it stopped the whole interact.
        com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
        if (PathfinderManager.isPathing()) PathfinderManager.clear(this, true);

        // Aim at the villager's body through the rotation manager (never a direct write). Aiming a
        // touch below centre keeps the crosshair on the villager hitbox rather than drifting up
        // onto the floating nametag above it.
        float[] rot = RotationUtils.getRotations(npc, 0.0, -0.35, 0.0);
        RotationManager.getInstance().setTargetRotation(INTERACTION_ROTATION_OWNER,
                RotationManager.PRIORITY_COMBAT, rot[0], rot[1], (float) interactAimSpeed.value, true);

        // Fire the interact only when the crosshair is genuinely on THIS villager, we are not
        // mid-dig, and the humanised rate allows it — the same safe gate ChestAura uses.
        if (mc.thePlayer.getDistanceToEntity(npc)<=3.0 && !mc.playerController.getIsHittingBlock()) {
            if (npcClickRate.ready()) {
                com.nezurstandalone.input.NpcInteractions.request(interactionOwner,npc, () -> isToggled() && !isAutoHealBusy()
                        && currentState == (prestige ? State.PRESTIGE_INTERACTING : State.PERK_INTERACTING)
                        && npc == findNpc(prestige));
                GuardedInput.watch(interactionOwner,status->{
                    if(status==GuardedInput.Status.NATIVE_INVOKED){
                        npcClickRate.consume();npcInteractionUntilNanos=System.nanoTime()+10_000_000_000L;
                    }
                    if(status==GuardedInput.Status.NATIVE_INVOKED || status==GuardedInput.Status.DROPPED)
                        com.nezurstandalone.control.GrinderDiagnostics.record("NPC_INPUT","status="+status+" npc="+npc.getEntityId()
                                +" state="+currentState+" ray="+(mc.objectMouseOver==null?"none":mc.objectMouseOver.typeOfHit));
                });
            }
        }

        // No menu after ~2.5s — someone is standing in front of us. Step to the next spot and
        // retry, but cap the ring: a full lap with no menu means the villager is wrong or boxed
        // in, and cycling forever starves the actual grind.
        if (com.nezurstandalone.control.Clock.millis() - interactStamp > 2500L) {
            advanceNpcSpot(prestige);
        }
    }

    private void abortNpc(String why) {
        if(managedVisit)loadout.deferVisit(com.nezurstandalone.control.Clock.millis(),why);
        managedVisit=false;chooserSlot=null;
        npcRetryAfterNanos = Math.max(npcRetryAfterNanos, System.nanoTime() + 60_000_000_000L);
        pauseGameplay();
        // Close only our active menu, BEFORE resetting state/ownership tracking.
        if (mc.thePlayer != null && mc.currentScreen instanceof GuiChest
                && ownsMenu((GuiChest) mc.currentScreen)) {
            mc.thePlayer.closeScreen();
        }
        resetMenuTracking();
        log(why + " Resuming grind.");
        PathfinderManager.clear(this, true); targetPerkName = "No Perk";
        prestigeConfirmStarted = false;
        lastMenuTitle = "";
        currentState = State.IDLE;
    }

    private void finishNpcFlow() {
        loadout.interrupt(com.nezurstandalone.control.Clock.millis(),"menu closed before verified result");
        managedVisit=false;chooserSlot=null;
        boolean closeOwned=mc.thePlayer!=null && mc.currentScreen instanceof GuiChest
                && ownsMenu((GuiChest)mc.currentScreen);
        pauseGameplay();
        if(closeOwned)mc.thePlayer.closeScreen();
        resetMenuTracking();
        PathfinderManager.clear(this, true);
        targetPerkName = "No Perk";
        prestigeConfirmStarted = false;
        lastMenuTitle = "";
        currentState = State.LEAVING_SPAWN;
    }

    /** Resolve the exact NPC label and the living villager directly beneath it. */
    private Entity findNpc(boolean prestige) {
        if(mc.theWorld==null || mc.thePlayer==null)return null;
        Entity best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(Entity label:mc.theWorld.loadedEntityList) {
            if(label.isDead || !(label instanceof EntityArmorStand || label instanceof EntityVillager)
                    || !label.hasCustomName() || !UpgradeNpcMatcher.matches(prestige,label.getCustomNameTag()))continue;
            for(Entity candidate:mc.theWorld.loadedEntityList) {
                if(!(candidate instanceof EntityVillager) || candidate.isDead)continue;
                double score=UpgradeNpcMatcher.score(candidate.posX-label.posX,label.posY-candidate.posY,
                        candidate.posZ-label.posZ,mc.thePlayer.getDistanceSqToEntity(candidate));
                if(score<bestScore){best=candidate;bestScore=score;}
            }
        }
        return best;
    }

    // ---- perk GUI -----------------------------------------------------------
    /** True once the server has actually populated the open menu (any non-empty slot). */
    private void resetMenuTracking() {
        prestigeMessage.cancel();
        com.nezurstandalone.control.GuiLease.release(this);
        menuTransition.clear();
        loadoutConfirmation.clear();
        observedMenu = null;
        observedContents = "";
        lastMenuTitle = "";
        nextGuiClickNanos = 0L;
        prestigeConfirmStarted = false;
    }

    private void failMenu(String reason) {
        if(managedVisit&&mc.thePlayer!=null&&mc.thePlayer.openContainer instanceof ContainerChest) {
            String title=((ContainerChest)mc.thePlayer.openContainer).getLowerChestInventory().getDisplayName().getUnformattedText();
            log("Loadout stopped in '"+title+"' while handling "+targetPerkName+".");
        }
        npcRetryAfterNanos = System.nanoTime() + 60_000_000_000L;
        NotificationManager.show("§e[AutoGrinder] " + reason + " Leaving the failed menu and resuming.", 5000);
        // Keep sync requested. An attempted click is not evidence of a successful purchase.
        java.util.Arrays.fill(perkAttempted, false);
        abortNpc(reason);
    }

    private String menuKind(String title) {
        return LoadoutMenus.kind(title);
    }

    private boolean ownsMenu(GuiChest gui) {
        if (gui.inventorySlots != mc.thePlayer.openContainer
                || !(gui.inventorySlots instanceof ContainerChest)) return false;
        String kind = menuKind(((ContainerChest) gui.inventorySlots).getLowerChestInventory()
                .getDisplayName().getUnformattedText());
        if (currentState == State.PERK_INTERACTING || currentState == State.PRESTIGE_INTERACTING) {
            if (System.nanoTime() > npcInteractionUntilNanos) return false;
            return currentState == State.PERK_INTERACTING ? kind.equals("upgrades") : kind.equals("prestige");
        }
        if (currentState == State.PERK_CLICKING_GUI) {
            return kind.equals("upgrades") || kind.equals("killstreaks") || kind.equals("choosePerk")
                    || kind.equals("chooseStreak") || ((menuTransition.isPending()
                        || observedMenu == gui.inventorySlots) && kind.equals("confirm"));
        }
        return currentState == State.PRESTIGE_CLICKING_GUI
                && (kind.equals("prestige") || ((menuTransition.isPending()
                        || observedMenu == gui.inventorySlots) && kind.equals("confirm"))
                    || (prestigeConfirmStarted && kind.equals("confirm")));
    }

    private String menuContents(IInventory inv) {
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            contents.append(i).append(':');
            if (stack != null) contents.append(stack.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            contents.append(';');
        }
        return contents.toString();
    }

    private boolean menuReady(GuiChest gui) {
        if (!ownsMenu(gui)) return false;
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inv = container.getLowerChestInventory();
        String title = inv.getDisplayName().getUnformattedText();
        String contents = menuContents(inv);
        long now = System.nanoTime();
        if(managedVisit&&loadoutConfirmation.matches(loadout.pending(),container))loadout.confirmationOpened();
        com.nezurstandalone.utils.MenuTransition.Result transition = menuTransition.observe(container,
                menuKind(title), contents, now);
        if(managedVisit&&transition==com.nezurstandalone.utils.MenuTransition.Result.CHANGED&&menuKind(title).equals("confirm")) {
            loadoutConfirmation.transitioned(container);
            if(loadoutConfirmation.matches(loadout.pending(),container))loadout.confirmationOpened();
        }
        if ((transition == com.nezurstandalone.utils.MenuTransition.Result.TIMED_OUT || transition == com.nezurstandalone.utils.MenuTransition.Result.UNKNOWN)) {
            failMenu("Server did not confirm the menu action.");
            return false;
        }
        if (container != observedMenu || !title.equals(lastMenuTitle) || !contents.equals(observedContents)) {
            if (container != observedMenu || !title.equals(lastMenuTitle)) prestigeConfirmStarted = false;
            observedMenu = container;
            lastMenuTitle = title;
            observedContents = contents;
            guiOpenStamp = now;
            return false;
        }
        return !menuTransition.isPending() && menuPopulated(inv)
                && now - guiOpenStamp >= 600_000_000L && now >= nextGuiClickNanos;
    }

    private boolean clickMenu(ContainerChest container, int slot, String... expected) {
        return clickMenu(container,slot,null,expected);
    }

    private boolean clickMenu(ContainerChest container, int slot, Runnable beforeDispatch, String... expected) {
        IInventory inv = container.getLowerChestInventory();
        if (menuTransition.isPending() || container != mc.thePlayer.openContainer
                || !(mc.currentScreen instanceof GuiChest)
                || ((GuiChest) mc.currentScreen).inventorySlots != container
                || slot < 0 || slot >= inv.getSizeInventory() || inv.getStackInSlot(slot) == null
                || mc.thePlayer.inventory.getItemStack() != null) {
            failMenu("Menu or cursor state changed; action canceled.");
            return false;
        }
        if (!com.nezurstandalone.control.GuiLease.acquire(this)) return false;
        if(beforeDispatch!=null)beforeDispatch.run();
        mc.playerController.windowClick(container.windowId, slot, 0, 0, mc.thePlayer);
        long now = System.nanoTime();
        if(currentState==State.PRESTIGE_CLICKING_GUI && PrestigeConfirmation.serverMenu(
                isClassicPitTitle(Utils.getScoreboardTitle()),menuKind(inv.getDisplayName().getUnformattedText()),expected)) {
            // Hypixel may open Prestige? without S32 confirmation. Require the exact
            // prestige button from guarded server inventory packets instead of a timer/ACK.
            menuTransition.beginServerContents(container,"prestige",menuContents(inv),now,expected);
            nextGuiClickNanos=now+nextGuiClickDelay()*1_000_000L;return true;
        }
        // Classic opens a new server menu without a matching transaction acknowledgement.
        // Its first prestige step is accepted only when the expected confirmation menu appears;
        // Hypixel retains the separate authoritative transaction handling below.
        if(expected.length>0&&managedVisit&&currentState==State.PERK_CLICKING_GUI) {
            menuTransition.beginServerContents(container,menuKind(inv.getDisplayName().getUnformattedText()),
                    menuContents(inv),now,expected);
            nextGuiClickNanos=now+nextGuiClickDelay()*1_000_000L;return true;
        }
        if (expected.length > 0 && (currentState == State.PERK_CLICKING_GUI
                || (currentState == State.PRESTIGE_CLICKING_GUI
                    && isClassicPitTitle(Utils.getScoreboardTitle())))) {
            menuTransition.begin(container, menuKind(inv.getDisplayName().getUnformattedText()),
                    menuContents(inv), now, expected);
            nextGuiClickNanos = now + nextGuiClickDelay() * 1_000_000L;
            return true;
        }
        // Snapshot after vanilla local prediction so that prediction cannot acknowledge itself.
        final short actionNumber;
        try {
            java.lang.reflect.Field counter = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.inventory.Container.class, "transactionID", "field_75150_e");
            actionNumber = counter.getShort(container);
        } catch (ReflectiveOperationException | RuntimeException failure) { failMenu("Cannot correlate native menu transaction."); return false; }
        if (expected.length == 0) {menuTransition.beginResponse(container, now, container.windowId, actionNumber);
            prestigeMessageId=prestigeMessage.begin(com.nezurstandalone.control.ClientSession.current(),currentState.name(),now,15_000_000_000L);
        }
        else menuTransition.beginAuthoritative(container, menuKind(inv.getDisplayName().getUnformattedText()),
                menuContents(inv), now, container.windowId, actionNumber, slot, expected);
        nextGuiClickNanos = now + nextGuiClickDelay() * 1_000_000L;
        return true;
    }

    private int findAction(IInventory inv, String... names) {
        for (String name : names) {
            int slot = findSlotByName(inv, name);
            if (slot >= 0) return slot;
        }
        return -1;
    }

    private boolean menuPopulated(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i) != null) return true;
        return false;
    }

    private void handlePerkGui(GuiChest gui) {
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inv = container.getLowerChestInventory();
        long now = com.nezurstandalone.control.Clock.millis();
        String menu = inv.getDisplayName().getUnformattedText();

        if (!menuReady(gui)) return;
        String kind = menuKind(menu);
        if(managedVisit && !hasTemporaryPerkLease()) {
            if(kind.equals("upgrades"))handleManagedRoot(container,inv,LoadoutPlanner.Kind.PERK);
            else if(kind.equals("killstreaks"))handleManagedRoot(container,inv,LoadoutPlanner.Kind.KILLSTREAK);
            else if(kind.equals("choosePerk"))handleManagedChooser(container,inv,"No perk");
            else if(kind.equals("chooseStreak"))handleManagedChooser(container,inv,"No killstreak");
            else if(kind.equals("confirm"))handleManagedConfirm(container,inv);
            else failMenu("Unexpected loadout menu.");
            return;
        }

        if (kind.equals("upgrades")) {
            handleUpgradesMenu(container, inv, now);
        } else if (kind.equals("killstreaks")) {
            finishNpcFlow();
        } else if (kind.equals("choosePerk")) {
            handleChooseMenu(container, inv, "No perk");
        } else if (kind.equals("chooseStreak")) {
            handleChooseMenu(container, inv, "No killstreak");
        } else if (kind.equals("confirm")) {
            // Confirm is found by name rather than a fixed slot, so a layout change cannot strand
            // a half-finished purchase.
            int confirm = findSlotByName(inv, "Confirm");
            if (confirm >= 0) {
                clickMenu(container, confirm, "upgrades", "killstreaks");
            } else {
                finishNpcFlow();
            }
            lastMenuTitle = "";
        } else {
            if (System.nanoTime() - guiOpenStamp < 2_000_000_000L) return;
            finishNpcFlow();
        }
    }

    /**
     * Reconciles the four perk slots with the four boxes.
     *
     * <p>Slots are located by their item name ("Perk Slot #1".."#4") rather than by a fixed index,
     * because the menu is not laid out identically on every account - a profile with only three
     * unlocked slots puts Killstreaks where the fourth slot sits on a fully unlocked one. What is
     * equipped is read from the slot's lore ("Selected: Bonk!"); the slot's own display name is
     * always just "Perk Slot #N", which is why comparing against it matched nothing and made the
     * bot re-open the same slot forever.
     */
    private void handleUpgradesMenu(ContainerChest container, IInventory inv, long now) {
        if (!perksNeedSync || (!autoPerk.isEnabled() && !hasTemporaryPerkLease())) {
            openKillstreaksOrFinish(container, inv);
            return;
        }
        if (!inspectContractPerks(inv)) return;
        com.nezurstandalone.settings.PerkSetting[] boxes = perkBoxes();
        for (int i = 0; i < boxes.length; i++) {
            int slotIdx = findPerkSlotIndex(inv, i + 1);
            if (slotIdx < 0) {
                continue; // this account does not have that perk slot at all
            }
            ItemStack st = inv.getStackInSlot(slotIdx);
            java.util.List<String> lore = loreOf(st);

            // During a temporary lease, unresolved/streaming lore cannot be treated as an empty slot.
            if (hasTemporaryPerkLease() && !hasResolvedPerkLore(lore)) return;

            // A slot still locked behind a level shows only "Required level: [70]" - nothing to do.
            if (selectedPerkFrom(lore) == null && loreHas(lore, "Required level")) {
                perkAttempted[i] = false;
                continue;
            }

            String equipped = selectedPerkFrom(lore); // null => empty slot
            String want = contractDesiredPerk(i);
            boolean wantNone = "No Perk".equalsIgnoreCase(want);
            boolean matches = wantNone ? equipped == null
                    : (equipped != null && equipped.equalsIgnoreCase(want));

            if (matches) {
                perkAttempted[i] = false; // already correct - leave it alone
                continue;
            }
            if (perkAttempted[i]) {
                failMenu("Perk selection was not confirmed in server lore.");
                return;
            }
            perkAttempted[i] = true;
            targetPerkName = want;
            log("Perk slot " + (i + 1) + ": " + (equipped == null ? "empty" : equipped) + " -> " + want);
            clickMenu(container, slotIdx, "choosePerk");
            return;
        }
        if (System.nanoTime() - guiOpenStamp < 1_500_000_000L) return;
        log("Perks verified.");
        perksNeedSync = false;
        if (contractKungFuRestoring) {
            contractKungFuRestoring = false; contractKungFuRequested = false;
            contractGappleRequested=false;contractGappleRemove=false;
            contractNoPerkRequested = false; contractKungFuReady = false;
            contractKungFuBefore = null; contractKungFuSlot = -1; contractNoPerkVerifyAt = 0L;
            contractLog("Previous perk loadout restored successfully.");
        } else if (contractGappleRequested || contractKungFuRequested || contractNoPerkRequested) {
            contractKungFuReady = true;
            if (contractNoPerkRequested) contractNoPerkVerifyAt = 0L; // Cached until explicit restoration.
            contractLog(contractGappleRequested ? "GOLDEN_APPLES blocking perks removed and unrelated slots verified." : contractNoPerkRequested ? "All perks disabled for NO_PERK contract."
                    : "Kung Fu Knowledge verified equipped from server perk lore.");
        }
        openKillstreaksOrFinish(container, inv);
    }

    /** Contractor changes end their own lease flow; normal scheduling resumes independently. */
    private void openKillstreaksOrFinish(ContainerChest container,IInventory inv) {
        finishNpcFlow();
    }

    /**
     * Picks the wanted perk out of the chooser. Availability is read from the entry's own lore, so
     * no cost or level table has to be kept in sync with the server: an entry that says it needs
     * renown, more gold or a higher level is simply skipped, and the flow moves on to the next
     * perk instead of firing a click that would do nothing.
     */
    private void handleChooseMenu(ContainerChest container, IInventory inv, String noneName) {
        boolean wantNone = "No Perk".equalsIgnoreCase(targetPerkName);
        int found = -1;
        String blockedBy = null;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st == null) continue;
            String nm = StringUtils.stripControlCodes(st.getDisplayName()).trim();

            if (wantNone) {
                if (nm.equalsIgnoreCase(noneName) || nm.toLowerCase().startsWith("no ")) { found = i; break; }
                continue;
            }
            if (!nm.equalsIgnoreCase(targetPerkName)) continue;

            java.util.List<String> lore = loreOf(st);
            if (loreHas(lore, "Already selected")) {
                blockedBy = "already selected in another slot";
            } else if (loreHas(lore, "Unlocked in Renown shop")) {
                blockedBy = "not unlocked (renown shop)";
            } else if (loreHas(lore, "Not enough gold")) {
                blockedBy = "not enough gold";
            } else if (loreHas(lore, "Too low level")) {
                blockedBy = "level too low";
            } else {
                boolean purchase = loreHas(lore, "Click to purchase");
                boolean select = loreHas(lore, "Click to select");
                if (purchase) {
                    double cost = menuCost(lore, targetPerkName);
                    double gold = scoreboardGold();
                    if (cost < 0.0) blockedBy = "purchase cost missing; refusing to click";
                    else if (Double.isNaN(gold)) blockedBy = "current gold unavailable; refusing to purchase";
                    else if (gold + 0.000001 < cost) blockedBy = "not enough gold (need "
                            + formatGold(cost) + ", have " + formatGold(gold) + ")";
                    else found = i;
                } else if (select) {
                    found = i; // Equipping an already purchased item does not spend gold.
                } else {
                    blockedBy = "purchase/select availability not confirmed";
                }
            }
            break;
        }

        if (found >= 0) {
            clickMenu(container, found, "confirm", "upgrades", "killstreaks");
        } else {
            log("\"" + targetPerkName + "\" unavailable"
                    + (blockedBy == null ? " (not in menu)" : " (" + blockedBy + ")") + " - skipping.");
            // Back out to the previous menu rather than closing everything, so the remaining
            // slots still get their turn on this same visit.
            int back = findSlotByName(inv, "Go Back");
            if (back >= 0) {
                clickMenu(container, back, "upgrades", "killstreaks");
            } else {
                finishNpcFlow();
            }
        }
        lastMenuTitle = "";
    }

    // ---- menu reading helpers ----------------------------------------------
    private static final Pattern PERK_SLOT_NAME = Pattern.compile("Perk Slot #(\\d+)");

    private int findManagedSlotIndex(IInventory inv,LoadoutPlanner.Kind kind,int number) {
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack item=inv.getStackInSlot(i);
            if(item!=null&&LoadoutMenus.slotNumber(kind,item.getDisplayName())==number)return i;
        }
        return -1;
    }

    /** Menu index of the "Perk Slot #n" item, or -1 when this account has no such slot. */
    private int findPerkSlotIndex(IInventory inv, int slotNumber) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st == null) continue;
            Matcher m = PERK_SLOT_NAME.matcher(StringUtils.stripControlCodes(st.getDisplayName()));
            if (m.find()) {
                try {
                    if (Integer.parseInt(m.group(1)) == slotNumber) return i;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /** Menu index of the first item with this exact display name, or -1. */
    private int findSlotByName(IInventory inv, String name) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st == null) continue;
            if (StringUtils.stripControlCodes(st.getDisplayName()).trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** The perk named by a "Selected: X" lore line, or null when the slot is empty. */
    private static String selectedPerkFrom(java.util.List<String> lore) {
        for (String line : lore) {
            String t = line.trim();
            if (t.startsWith("Selected:")) {
                String selected = t.substring("Selected:".length()).trim();
                return selected.isEmpty() || selected.equalsIgnoreCase("No Perk")
                        || selected.equalsIgnoreCase("None") || selected.equalsIgnoreCase("Empty")
                        ? null : selected;
            }
        }
        return null;
    }

    private static boolean hasResolvedPerkLore(java.util.List<String> lore) {
        return loreHas(lore, "Selected:") || loreHas(lore, "Click to choose perk")
                || loreHas(lore, "Select a perk to fill this slot")
                || loreHas(lore, "Required level");
    }

    private static boolean loreHas(java.util.List<String> lore, String needle) {
        for (String line : lore) {
            if (line.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    /** Unknown cost or balance means no purchase click is sent. */
    private double menuCost(java.util.List<String> lore, String name) {
        for(String line:lore) {
            String text=line.trim();
            if(text.toLowerCase(java.util.Locale.ROOT).matches("(?:gold\\s+)?cost\\s*:.*")) {
                double price=LoadoutPlanner.money(text.substring(text.indexOf(':')+1));
                return Double.isFinite(price)?price:-1.0;
            }
        }
        Perk p = perkByName(name);
        if (p == null) p = killstreakByName(name);
        return p == null ? -1.0 : p.gold;
    }

    private double scoreboardGold() {
        return LoadoutPlanner.scoreboardGold(Utils.getScoreboardLines());
    }

    private static String formatGold(double gold) {
        return String.format(java.util.Locale.ROOT, "%.2f", gold);
    }

    /** Lore lines of a stack with colour codes stripped; empty when it has none. */
    private static java.util.List<String> loreOf(ItemStack st) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (st == null || !st.hasTagCompound()) return out;
        net.minecraft.nbt.NBTTagCompound tag = st.getTagCompound();
        if (!tag.hasKey("display", 10)) return out;
        net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9)) return out;
        net.minecraft.nbt.NBTTagList lore = display.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++) {
            out.add(StringUtils.stripControlCodes(lore.getStringTagAt(i)));
        }
        return out;
    }

    // ---- prestige GUI -------------------------------------------------------
    private int findClassicPrestigeAction(IInventory inv, boolean confirm) {
        String name = confirm ? "ARE YOU SURE?" : "Prestige";
        int slot = findSlotByName(inv, name);
        if (slot < 0) return -1;
        ItemStack item = inv.getStackInSlot(slot);
        if (item == null || net.minecraft.item.Item.getIdFromItem(item.getItem()) != (confirm ? 159 : 264)
                || (confirm && item.getMetadata() != 13)) return -1;
        java.util.List<String> lore = loreOf(item);
        return (loreHas(lore,"Click to prestige!") || !confirm && loreHas(lore,"Click to purchase!")) ? slot : -1;
    }

    private void handlePrestigeGui(GuiChest gui) {
        if (classicPrestigePending) return;
        if (!menuReady(gui)) return;
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inv = container.getLowerChestInventory();
        String kind = menuKind(inv.getDisplayName().getUnformattedText());
        boolean classic = isClassicPitTitle(Utils.getScoreboardTitle());
        if (kind.equals("prestige")) {
            int prestigeSlot=findAction(inv,"Prestige","Prestige!","Prestige Now","Prestige Now!");
            if(prestigeSlot>=0 && prestigeGold.read(loreOf(inv.getStackInSlot(prestigeSlot)),scoreboardGold())
                    && !prestigeGold.ready()) {
                log("Prestige needs "+formatGold(prestigeGold.remaining())+"g more grinded ("
                        +formatGold(prestigeGold.earned())+"/"+formatGold(prestigeGold.required())+"g). Resuming grind.");
                prestigeGoldLogAt=com.nezurstandalone.control.Clock.millis();
                finishNpcFlow();return;
            }
            int action = classic ? findClassicPrestigeAction(inv, false)
                    : findAction(inv, "Prestige", "Prestige!", "Prestige Now", "Prestige Now!");
            if (action < 0) { failMenu("Prestige action is not identifiable; no slot was clicked."); return; }
            clickMenu(container, action, "confirm");
        } else if (kind.equals("confirm")) {
            if (!prestigeConfirmStarted) {
                prestigeConfirmStarted = true;
                prestigeConfirmStamp = System.nanoTime();
            }
            if (System.nanoTime() - prestigeConfirmStamp < 7_000_000_000L) return;
            int action = classic ? findClassicPrestigeAction(inv, true)
                    : findHypixelPrestigeConfirmation(inv);
            if (action < 0) { failMenu("Prestige confirmation is not identifiable."); return; }
            // Hypixel waits for its chat acknowledgment; Classic waits for a scoreboard level reset.
            if (clickMenu(container, action) && classic) {
                classicPrestigePending = true;
                classicPrestigeStart = com.nezurstandalone.control.Clock.millis();
            }
        }
    }

    // ---- weapon -------------------------------------------------------------
    private void handleAutoWeapon() {
        boolean isDead = mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0;
        if (wasDeadLastTick && !isDead) equipBestWeapon();
        wasDeadLastTick = isDead;
    }

    private void equipBestWeapon() {
        if (!autoWeapon.isEnabled()
                || com.nezurstandalone.contract.ContractCombatPolicy.type() == com.nezurstandalone.contract.ContractOffer.Type.FIST_MID_KILLS) return;
        if (isAutoHealBusy() || mc.currentScreen != null || mc.thePlayer.isUsingItem()) return;
        int bestSlot = -1; float bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            float score = getWeaponScore(stack);
            if (score > bestScore) { bestScore = score; bestSlot = i; }
        }
        if (bestSlot != -1 && mc.thePlayer.inventory.currentItem != bestSlot) {
            if (com.nezurstandalone.control.InventoryOwner.select(this, bestSlot)) com.nezurstandalone.control.InventoryOwner.release(this, false);
        }
    }

    private float getWeaponScore(ItemStack stack) {
        if (stack == null) return 0f;
        net.minecraft.item.Item item = stack.getItem();
        float baseScore = 0f;
        if (item == Items.diamond_sword) {
            baseScore = 1000f; // 1: Diamond Sword
        } else if (item == Items.golden_sword) {
            baseScore = 500f;  // 2: Gold Sword
        } else if (item == Items.iron_sword) {
            baseScore = 100f;  // 3: Iron Sword
        } else if (item instanceof ItemSword) {
            baseScore = 10f;
        } else if (item instanceof ItemAxe) {
            baseScore = 5f;
        } else {
            return 0f;
        }
        float enchantBonus = net.minecraft.enchantment.EnchantmentHelper.getModifierForCreature(stack, net.minecraft.entity.EnumCreatureAttribute.UNDEFINED);
        return baseScore + enchantBonus;
    }

    // ---- chat ---------------------------------------------------------------
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!isToggled() || mc.thePlayer == null) return;
        boolean classic=classicRuntime();
        String msg = com.nezurstandalone.utils.ChatEvents.plain(event);
        if(classic)msg=msg.trim();
        boolean serverText=com.nezurstandalone.control.ServerMessages.chatChannel(event.type,classic);
        if(classic && serverText && GrinderHudStats.rewardMessage(msg)) {
            Matcher xpM=XP_PATTERN.matcher(msg);while(xpM.find())try{sessionXP+=Integer.parseInt(xpM.group(1).replace(",",""));}catch(NumberFormatException ignored){}
            Matcher goldM=GOLD_PATTERN.matcher(msg);while(goldM.find())try{sessionGold+=Double.parseDouble(goldM.group(1).replace(",",""));}catch(NumberFormatException ignored){}
            Matcher renM=RENOWN_PATTERN.matcher(msg);while(renM.find())try{sessionRenown+=Double.parseDouble(renM.group(1).replace(",",""));}catch(NumberFormatException ignored){}
        }
        if(classic&&serverText)sessionGold+=GrinderHudStats.pickupGold(msg);
        if(classic && serverText && (isMysticDropMessage(msg)||msg.startsWith("NIGHT QUEST!")||msg.startsWith("DONE!")))
            com.nezurstandalone.control.GrinderDiagnostics.record("SERVER_SIGNAL","channel="+event.type+" state="+currentState+" contextReady="+(engineWorld==mc.theWorld&&enginePlayer==mc.thePlayer)+" text="+msg);
        if(serverText)hudStats.chat(msg,nightQuestActive,com.nezurstandalone.control.Clock.millis(),classic);
        if (!classic && (engineWorld!=mc.theWorld||enginePlayer!=mc.thePlayer))return;
        if(serverText && isMysticDropMessage(msg))recordMysticDrop(msg,classic,event.type);

        if(engineWorld!=mc.theWorld||enginePlayer!=mc.thePlayer)return;
        if(!classic && event.type==0){
            Matcher xpM=XP_PATTERN.matcher(msg);while(xpM.find())try{sessionXP+=Integer.parseInt(xpM.group(1).replace(",",""));}catch(Exception ignored){}
            Matcher goldM=GOLD_PATTERN.matcher(msg);while(goldM.find())try{sessionGold+=Double.parseDouble(goldM.group(1).replace(",",""));}catch(Exception ignored){}
            Matcher renM=RENOWN_PATTERN.matcher(msg);while(renM.find())try{sessionRenown+=Double.parseDouble(renM.group(1).replace(",",""));}catch(Exception ignored){}
        }
        if(overdriveOof.message(msg,mc.thePlayer.getName(),oofOnOverdrive.isEnabled(),serverText,
                com.nezurstandalone.control.Clock.millis()))log("Your OVERDRIVE activated - /oof queued.");
        if(serverText && (currentState==State.PRESTIGE_NAVIGATING || currentState==State.PRESTIGE_INTERACTING || currentState==State.PRESTIGE_CLICKING_GUI)
                && System.nanoTime()<npcInteractionUntilNanos && PrestigeGoldProgress.denied(msg)) {
            prestigeGold.rejected(scoreboardGold());
            abortNpc("Server rejected Prestige: more earned gold is required.");
            return;
        }
        if(serverText)prestigeGold.reward(msg);
        if (serverText && autoBlockhead.isEnabled()) blockhead.chat(msg,com.nezurstandalone.control.Clock.millis());

        if (serverText && com.nezurstandalone.control.ServerMessages.throttle(msg)) {
            overdriveOof.rejected(com.nezurstandalone.control.Clock.millis());
            throttleCooldownEnd = com.nezurstandalone.control.Clock.millis() + 15000L;
            log("Command throttle hit — backing off 15s.");
        }

        if (currentState == State.PERK_CLICKING_GUI && msg.toLowerCase().contains("not enough gold")) {
            finishNpcFlow();
        }

        // Our own prestige unlocked: the level bar stays stale until a server swap, so /l then let
        // the reconnect flow /play pit us back with a refreshed level. Matches "PRESTIGE! <us>
        // unlocked prestige ...". Guard on it being us, and on not already swapping.
        String self = mc.thePlayer.getName();
        if (!isClassicPitTitle(Utils.getScoreboardTitle()) && serverText && currentState == State.PRESTIGE_CLICKING_GUI && prestigeConfirmStarted
                && menuTransition.awaitingResponse(System.nanoTime()) && !prestigeSwapPending && self != null
                && prestigeMessage.accept(prestigeMessageId,com.nezurstandalone.control.ClientSession.current(),currentState.name(),System.nanoTime(),
                    com.nezurstandalone.control.ServerMessages.prestige(msg,self))) {
            if (!menuTransition.completeResponse(System.nanoTime())) return;
            loadout.resetEvidence();
            log("Prestige unlocked - lobby-swapping to refresh the level.");
            perksNeedSync = true; java.util.Arrays.fill(perkAttempted, false);
            prestigeSwapPending = true;
            prestigeLeftPit = false;
            prestigeSwapStart = com.nezurstandalone.control.Clock.millis();
            finishNpcFlow();
            releaseCombat();
            com.nezurstandalone.pathfinder.PathfinderManager.clearCombatTarget();
            PathfinderManager.clear(this, true);
            if (com.nezurstandalone.control.Clock.millis() >= throttleCooldownEnd) {
                if (!AutoReconnect.requestLobbySwap(this,this::isToggled)) return;
            }
        }

        // Night quest lifecycle.
        if (nightQuestSupport.isEnabled()) {
            if (serverText && com.nezurstandalone.control.ServerMessages.questStart(msg,classic)) {
                hudStats.questStart(msg,classic);
                nightQuestActive = true;
                questMessageId=questMessage.begin(com.nezurstandalone.control.ClientSession.current(),"ACTIVE_QUEST",System.nanoTime(),36L*60*1_000_000_000L);
                nightQuestStartTime = com.nezurstandalone.control.Clock.millis();
                nextNightQuestTime = nightQuestStartTime + ((classic?20L:36L) * 60L * 1000L);
                log("Night Quest started.");
            }
            if (serverText && nightQuestActive && questMessage.accept(questMessageId,com.nezurstandalone.control.ClientSession.current(),"ACTIVE_QUEST",System.nanoTime(),com.nezurstandalone.control.ServerMessages.questDone(msg,classic))) {
                nightQuestActive = false;
                sessionNightQuests++;
                hudStats.questDone(msg,com.nezurstandalone.control.Clock.millis()-nightQuestStartTime);
                log("Night Quest completed.");
            }
        }

        if (event.type == 2 && msg.contains("❤")) {
            lastAttackTime = com.nezurstandalone.control.Clock.millis();
        }
    }

    private boolean isMysticDropMessage(String msg) {
        return com.nezurstandalone.control.ServerMessages.mystic(msg);
    }

    // =========================================================================
    // Compact, measured dashboard. Snapshot building runs at most four times/second.
    // =========================================================================
    private static final int GREEN = 0xFF69D7AD, AMBER = 0xFFF3C76B, PURPLE = 0xFFBAA3FF,
            PINK = 0xFFEC88BC, CYAN = 0xFF77CFE2;

    @Override public String getHudKey() { return "autogrinder"; }
    @Override public boolean isHudVisible() { return isToggled() && showHud.isEnabled(); }
    @Override public double getDefaultHudX() { return 6; }
    @Override public double getDefaultHudY() { return 6; }
    @Override public int getHudWidth() { return (int)Math.ceil(com.nezurstandalone.gui.GrinderDashboard.WIDTH*dashboardScale()); }
    @Override public int getHudHeight() { return (int)Math.ceil(dashboard().height()*dashboardScale()); }

    private float dashboardScale() {
        float scale=(float)hudScale.value;
        net.minecraft.client.gui.ScaledResolution screen=com.nezurstandalone.utils.ScreenScale.get();
        return Math.max(.1f,Math.min(scale,Math.min((screen.getScaledWidth()-12f)/com.nezurstandalone.gui.GrinderDashboard.WIDTH,
                (screen.getScaledHeight()-12f)/dashboard().height())));
    }

    private com.nezurstandalone.gui.GrinderDashboard.Snapshot dashboard() {
        long now=com.nezurstandalone.control.Clock.millis();
        if(hudSnapshot!=null && now-hudSnapshotAt<250 && hudSnapshotWorld==mc.theWorld && hudSnapshotState==currentState)return hudSnapshot;
        hudSnapshotAt=now;hudSnapshotWorld=mc.theWorld;hudSnapshotState=currentState;
        com.nezurstandalone.gui.GrinderDashboard.Snapshot d=new com.nezurstandalone.gui.GrinderDashboard.Snapshot();
        d.title=getName();d.state=prettyState(currentState);d.accent=stateColor(currentState);
        long elapsed=sessionStartTime==0?0:Math.max(0,now-sessionStartTime);
        double hours=Math.max(1.0/3600,elapsed/3600000.0);
        d.uptime=GrinderHudStats.time(elapsed);
        d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("XP EARNED",GrinderHudStats.number(sessionXP),hudRate(sessionXP,hours,elapsed),CYAN));
        d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("GOLD EARNED",GrinderHudStats.number(sessionGold),hudRate(sessionGold,hours,elapsed),AMBER));
        d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("FRESH DROPS",String.valueOf(sessionMystics),hudRate(sessionMystics,hours,elapsed),PURPLE));
        if(CombatAura.isTdmEventActive())d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("ASSISTS",String.valueOf(hudStats.assists),hudRate(hudStats.assists,hours,elapsed),GREEN));
        else if(nightQuestSupport.isEnabled())d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("QUESTS",String.valueOf(sessionNightQuests),"+"+hudStats.questVile+" Vile",GREEN));
        else d.metrics.add(new com.nezurstandalone.gui.GrinderDashboard.Metric("KILLS",String.valueOf(hudStats.kills),hudRate(hudStats.kills,hours,elapsed),GREEN));
        d.footer=hudStats.kills+" kills / "+hudStats.assists+" assists / "+hudStats.deaths+" deaths"
                +(sessionRenown>0?" / +"+GrinderHudStats.number(sessionRenown)+" renown":"");
        if(mc.theWorld==null||mc.thePlayer==null){d.activity="Waiting for a connected world";hudSnapshot=d;return d;}
        java.util.List<String> lines=Utils.getScoreboardLines();
        String zone=PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        String map=PitMapManager.getCurrentMap().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        d.location=map+" / "+zone+" / Lv "+Utils.getLevel();
        Entity entity=combat.getTarget();EntityPlayer target=entity instanceof EntityPlayer&&!entity.isDead?(EntityPlayer)entity:null;
        if(target!=null&&target.getHealth()>0){
            d.target=target.getName();
            float hp=target.getHealth()+target.getAbsorptionAmount();
            d.targetDetail=GrinderHudStats.number(hp)+" HP / "+String.format(java.util.Locale.ROOT,"%.1fm",mc.thePlayer.getDistanceToEntity(target));
            d.health=hp/Math.max(1,target.getMaxHealth()+target.getAbsorptionAmount());
        }else if(hasDragonEggPriority()){
            d.target="Dragon Egg";d.targetDetail="interacting";d.health=1;
        }
        d.activity=hudActivity(now);
        double wallet=LoadoutPlanner.scoreboardGold(lines);
        d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Health",GrinderHudStats.number(mc.thePlayer.getHealth()+mc.thePlayer.getAbsorptionAmount())+" HP",GuiTheme.TEXT));
        d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Gold",(Double.isFinite(wallet)?String.format(java.util.Locale.ROOT,"%,.2fg",wallet):"Unknown"),AMBER));
        d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Streak",String.valueOf(Utils.getStreak()),CYAN));
        d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Mid players",String.valueOf(getMidPlayerCount()),GuiTheme.TEXT));
        if(nightQuestSupport.isEnabled()&&hudStats.questXP>0&&!nightQuestActive)d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Quest rewards","+"+GrinderHudStats.number(hudStats.questXP)+" XP / "+hudStats.questVile+" Vile",PURPLE));
        if(sessionRenown>0)d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Renown","+"+GrinderHudStats.number(sessionRenown)+" / "+hudRate(sessionRenown,hours,elapsed),AMBER));
        if(target!=null&&(isAutoHealBusy()||correctionTick>=0||isOnBreak))
            d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Paused",d.activity,AMBER));
        else if(target!=null)d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Movement",hudMovement(),GuiTheme.TEXT_MUTED));
        else if(!d.activity.equals(hudMovement()) && PathfinderManager.hasDestination())
            d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Movement",hudMovement(),GuiTheme.TEXT_MUTED));

        String event=activeEventName();
        if(event.equals("—")){
            String live=hudField(lines,"Event");
            if(!live.isEmpty())event=live;
        }
        if(!event.equals("—")&&!event.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]","").contains("nightquest")){
            int color=activeEventColor();if(color==GuiTheme.TEXT_MUTED)color=CYAN;
            com.nezurstandalone.gui.GrinderDashboard.Section panel=new com.nezurstandalone.gui.GrinderDashboard.Section(event,"LIVE",color);
            if(event.toLowerCase(java.util.Locale.ROOT).contains("spire")){
                String floor=hudField(lines,"Floor","Your Floor","Spire Floor");
                if(!floor.isEmpty())panel.row("Floor",floor);
                panel.row("Plan",currentState==State.SPIRE_FIGHTING?"Fight on current floor":prettyState(currentState));
                int start=SpireEntryPolicy.startSeconds(BossStatus.bossName,BossStatus.statusBarTime);
                if(start>=0)panel.row("Starts in",GrinderHudStats.time(start*1000L));
                if(spireActiveStartTime>0)panel.row("Elapsed",GrinderHudStats.time(now-spireActiveStartTime));
            }else if(event.toLowerCase(java.util.Locale.ROOT).contains("squad")){
                int rank=squadRank();
                if(rank>0)panel.progress("Your rank","#"+rank+" / stay top 7",Math.max(0,(8f-rank)/7f));
                panel.row("Plan",squadSwapForced?"Lobby swap requested":rank>0&&rank<=7?"Hold position":lowMidTracking?"Checking rank":"Waiting for rank");
                if(lowMidTracking)panel.row("Swap check",GrinderHudStats.time(Math.max(0,8000-(now-lowMidStamp))));
            }else if(event.toLowerCase(java.util.Locale.ROOT).contains("raffle")){
                panel.progress("Held tickets",raffle.tickets()+" / 9 to deposit",raffle.tickets()/9f);
                panel.row("Plan",prettyState(currentState));
                if(rafflePathTicket!=Integer.MIN_VALUE)panel.row("Ticket target","Following a dropped ticket");
            }else if(event.toLowerCase(java.util.Locale.ROOT).contains("robbery")){
                int rank=-1;for(String line:lines){int value=RobberyController.rank(line);if(value>0){rank=value;break;}}
                if(rank>0)panel.row("Your rank","#"+rank);
                panel.row("Plan",robbery.holding()?"Protect winnings / hold top 20":"Hunt toward top 10");
                panel.row("Movement",prettyState(currentState));
            }else if(event.toLowerCase(java.util.Locale.ROOT).contains("blockhead")){
                panel.row("Plan",prettyState(currentState));
                BlockheadController.Goal goal=blockhead.observedGoal();
                if(goal!=null){
                    panel.row("Target",goal.kind==BlockheadController.GoalKind.PAINT?"Paint fresh ground":goal.pickup.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '));
                    if(goal.cooldown>0)panel.row("Respawn",String.format(java.util.Locale.ROOT,"%.1fs",goal.cooldown));
                }
                panel.row("Powerups seen",String.valueOf(blockhead.observedPowerupCount()));
                if(blockhead.quicktrailRemaining(now)>0)panel.progress("Quicktrail",GrinderHudStats.time(blockhead.quicktrailRemaining(now)),blockhead.quicktrailRemaining(now)/20000f);
                if(BlockheadController.diamond(mc.thePlayer))panel.row("Armor","Diamond equipped",GREEN);
            }else if(event.equalsIgnoreCase("TDM")||event.toLowerCase(java.util.Locale.ROOT).contains("team deathmatch")){
                panel.row("Strategy","One hit per opponent life");
                panel.row("Tagged players",String.valueOf(tdmAssists.aliveTagged()));
                panel.row("Total tags",String.valueOf(tdmAssists.totalTags()));
                panel.row("Assists",String.valueOf(Math.max(0,hudStats.assists-tdmAssistBaseline)));
                panel.row("Team check","Automatic");
            }else if(event.equals("Dragon Egg")){
                panel.row("Plan","Aim + interact; ignore if still 25s");
                if(currentEgg!=null)panel.row("Distance",String.format(java.util.Locale.ROOT,"%.1fm",mc.thePlayer.getDistance(currentEgg.getX()+.5,currentEgg.getY()+.5,currentEgg.getZ()+.5)));
            }else panel.row("Plan",event.toLowerCase(java.util.Locale.ROOT).contains("rage")?"Free combat / no forced mid return":"Fight event targets");
            addHudEventFields(panel,lines);
            d.sections.add(panel);
        }
        if(nightQuestSupport.isEnabled()&&nightQuestActive){
            com.nezurstandalone.gui.GrinderDashboard.Section q=new com.nezurstandalone.gui.GrinderDashboard.Section("Night Quest","ACTIVE",PURPLE);
            String live=hudField(lines,"Quest kills","Night Quest","Quest");
            if(!live.isEmpty())q.row("Server progress",live);
            if(hudStats.questGoal>0)q.progress("Kills observed",Math.min(hudStats.questKills,hudStats.questGoal)+" / "+hudStats.questGoal,hudStats.questKills/(float)hudStats.questGoal);
            q.row("Elapsed",GrinderHudStats.time(now-nightQuestStartTime));
            q.row("Session rewards",sessionNightQuests+" done / "+hudStats.questVile+" Vile");
            if(hudStats.questXP>0)q.row("Quest XP","+"+GrinderHudStats.number(hudStats.questXP));
            d.sections.add(q);
        }else if(nextNightQuestTime>now&&nightQuestSupport.isEnabled()){
            d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Night Quest",GrinderHudStats.time(nextNightQuestTime-now)+" estimated",PURPLE));
        }
        if(isHudShopState()){
            com.nezurstandalone.gui.GrinderDashboard.Section shop=new com.nezurstandalone.gui.GrinderDashboard.Section("Loadout",prettyState(currentState),PURPLE);
            LoadoutPlanner.Slot pending=loadout.pending();
            shop.row("Action",pending==null?"Verify server equipment":pending.desired);
            if(pending!=null)shop.row("Stage",pending.state.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '));
            if(npcRetryAfterNanos>System.nanoTime())shop.row("Retry",GrinderHudStats.time((npcRetryAfterNanos-System.nanoTime())/1000000));
            d.sections.add(shop);
        }else if(autoPerk.isEnabled()||autoKillstreak.isEnabled()){
            for(LoadoutPlanner.Slot slot:loadout.slots())if(slot.enabled&&slot.state!=LoadoutPlanner.State.SATISFIED&&slot.state!=LoadoutPlanner.State.DISABLED
                    &&!(slot.state==LoadoutPlanner.State.LEVEL_LOCKED&&LoadoutPlanner.NONE.equals(slot.desired))){
                String action=slot.desired+" / "+slot.reason;
                if(slot.state==LoadoutPlanner.State.LEVEL_LOCKED)action=slot.desired+" / "+slot.reason.replace("requires slot level ","Lv ").replace("requires level ","Lv ");
                else if(slot.state==LoadoutPlanner.State.WAITING_FOR_GOLD){
                    LoadoutPlanner.Entry entry=loadout.entry(slot.kind,slot.desired);
                    double missing=entry==null?Double.NaN:entry.price()-wallet;
                    action=slot.desired+" / "+(Double.isFinite(missing)&&missing>0?"+"+GrinderHudStats.number(missing)+"g needed":"waiting for gold");
                }
                else if(slot.state==LoadoutPlanner.State.NEEDS_PURCHASE||slot.state==LoadoutPlanner.State.NEEDS_REVALIDATION||slot.state==LoadoutPlanner.State.NEEDS_EQUIP)action=slot.desired+" / next spawn";
                d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Upgrade",action,slot.state==LoadoutPlanner.State.LEVEL_LOCKED?GuiTheme.TEXT_MUTED:AMBER));break;
            }
        }
        if(autoPrestige.isEnabled()&&prestigeGold.known()){
            com.nezurstandalone.gui.GrinderDashboard.Section p=new com.nezurstandalone.gui.GrinderDashboard.Section("Prestige",prestigeGold.ready()?"READY":"EARNING GOLD",AMBER);
            p.progress("Earned gold",GrinderHudStats.number(prestigeGold.earned())+" / "+GrinderHudStats.number(prestigeGold.required()),(float)(prestigeGold.earned()/Math.max(1,prestigeGold.required())));
            p.row("Remaining",GrinderHudStats.number(prestigeGold.remaining())+"g");d.sections.add(p);
        }
        if(isOnBreak||breakTimeEnable.isEnabled()){
            long total=isOnBreak?(long)(breakDurationLimit.value*60000):(long)(workDurationLimit.value*3600000);
            long remaining=Math.max(0,total-(now-lastBreakEndTime));
            d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row(isOnBreak?"Break":"Next break",GrinderHudStats.time(remaining),isOnBreak?AMBER:GREEN));
        }
        if(throttleCooldownEnd>now)d.status.add(new com.nezurstandalone.gui.GrinderDashboard.Row("Command delay",GrinderHudStats.time(throttleCooldownEnd-now),AMBER));
        hudSnapshot=d;return d;
    }

    private boolean isHudShopState(){
        return currentState==State.PERK_NAVIGATING||currentState==State.PERK_INTERACTING||currentState==State.PERK_CLICKING_GUI
            ||currentState==State.PRESTIGE_NAVIGATING||currentState==State.PRESTIGE_INTERACTING||currentState==State.PRESTIGE_CLICKING_GUI;
    }
    private String hudRate(double amount,double hours,long elapsed){return elapsed<30000?"warming up":GrinderHudStats.number(amount/hours)+" /h";}
    private String hudMovement(){
        if(!PathfinderManager.available(this))return "Another module owns movement";
        if(PathfinderManager.hasDestination())return PathfinderManager.getState().name().toLowerCase(java.util.Locale.ROOT);
        return currentState==State.FIGHTING||currentState==State.SPIRE_FIGHTING?"Direct combat":"No route needed";
    }
    private String hudActivity(long now){
        if(isAutoHealBusy())return "Healing / combat paused";
        if(correctionTick>=0)return "Waiting for position to settle";
        if(isOnBreak)return "On break / waiting in spawn";
        if(currentState==State.IDLE)return "Waiting for the next action";
        if(currentState==State.FIGHTING)return "Scanning for an eligible opponent";
        if(currentState==State.RETURNING_TO_MID)return "Returning to mid / nearby combat interrupts";
        return prettyState(currentState);
    }
    private static String hudField(java.util.List<String> lines,String... names){
        for(String line:lines){String text=LoadoutPlanner.clean(line).trim();int colon=text.indexOf(':');
            if(colon<0)continue;String key=text.substring(0,colon).trim();
            for(String name:names)if(key.equalsIgnoreCase(name))return text.substring(colon+1).trim();
        }return "";
    }
    private void addHudEventFields(com.nezurstandalone.gui.GrinderDashboard.Section panel,java.util.List<String> lines){
        for(String line:lines){
            if(panel.rows.size()>=7)break;
            String text=LoadoutPlanner.clean(line).trim();int colon=text.indexOf(':');if(colon<0)continue;
            String key=text.substring(0,colon).trim(),value=text.substring(colon+1).trim();
            if(value.isEmpty()||!key.toLowerCase(java.util.Locale.ROOT).matches("(?:time(?: left)?|remaining|ends? in|score|points|rank|position|kills|team|team score|tickets|deposited|pool|winnings|carried gold|event gold|souls|lives|powerup|power-up)"))continue;
            boolean duplicate=false;for(com.nezurstandalone.gui.GrinderDashboard.Row row:panel.rows)if(row.label.equalsIgnoreCase(key)||row.value.equalsIgnoreCase(value))duplicate=true;
            if(!duplicate)panel.row(key,value,GuiTheme.TEXT);
        }
    }

    @SubscribeEvent public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if(event.type!=RenderGameOverlayEvent.ElementType.ALL||!isHudVisible()||mc.thePlayer==null||mc.theWorld==null||mc.currentScreen!=null)return;
        com.nezurstandalone.gui.GrinderDashboard.Snapshot snapshot=dashboard();
        if(hudFadeStart==0)hudFadeStart=com.nezurstandalone.control.Clock.millis();
        float alpha=hudAnimations.isEnabled()?Math.min(1,(com.nezurstandalone.control.Clock.millis()-hudFadeStart)/200f):1;
        drawDashboard(snapshot,getRenderX(),getRenderY(),alpha);
    }

    /** The editor uses the same snapshot and bounds as the live HUD. */
    public void editorPreview(int x,int y,float alpha){drawDashboard(dashboard(),x,y,alpha);}

    private void drawDashboard(com.nezurstandalone.gui.GrinderDashboard.Snapshot snapshot,int x,int y,float alpha){
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x,y,0);float scale=dashboardScale();GlStateManager.scale(scale,scale,1);
            GuiDraw.resetState();com.nezurstandalone.gui.GrinderDashboard.render(snapshot,alpha);
        }finally{GuiDraw.resetState();GlStateManager.popMatrix();}
    }

    private int stateColor(State s) {
        switch (s) {
            case DRAGON_EGG: return PURPLE;
            case FIGHTING: case SPIRE_FIGHTING: return GREEN;
            case RETURNING_TO_MID: return CYAN;
            case BLOCKHEAD_FIGHTING: return GREEN;
            case BLOCKHEAD_AVOIDING: return AMBER;
            case ROBBERY_FIGHTING: return GREEN;
            case ROBBERY_RETREATING: case ROBBERY_BANKED: return AMBER;
            case BLOCKHEAD_PAINTING: case BLOCKHEAD_POWERUP: return CYAN;
            case RAFFLE_TO_MID: case RAFFLE_COLLECTING: case RAFFLE_RETURNING: case RAFFLE_DEPOSITING: return CYAN;
            case LOBBY_SWAP: case LIMBO: case LOBBY_RECONNECT: case JOINING_PIT: return AMBER;
            case PERK_NAVIGATING: case PERK_INTERACTING: case PERK_CLICKING_GUI:
            case PRESTIGE_NAVIGATING: case PRESTIGE_INTERACTING: case PRESTIGE_CLICKING_GUI: return PURPLE;
            case PICKING_UP_MYSTIC: return PINK;
            case SPIRE_WAITING_SPAWN: case SPIRE_WALK_TO_MID: return CYAN;
            default: return GuiTheme.ACCENT;
        }
    }

    private String prettyState(State s) {
        switch (s) {
            case IDLE: return "Idle";
            case LEAVING_SPAWN: return "Leaving Spawn";
            case DRAGON_EGG: return "Dragon Egg";
            case FIGHTING: return "Fighting";
            case RETURNING_TO_MID: return "Returning to Mid";
            case LOBBY_SWAP: return "Swapping";
            case LIMBO: return "Limbo";
            case LOBBY_RECONNECT: return "Reconnecting";
            case JOINING_PIT: return "Joining Pit";
            case PICKING_UP_MYSTIC: return "Mystic";
            case SPIRE_WAITING_SPAWN: return "Spire Wait";
            case SPIRE_WALK_TO_MID: return "Spire Rush";
            case SPIRE_FIGHTING: return "Spire Fight";
            case BLOCKHEAD_PAINTING: return "Blockhead Paint";
            case BLOCKHEAD_POWERUP: return "Blockhead Powerup";
            case BLOCKHEAD_FIGHTING: return "Blockhead Fight";
            case BLOCKHEAD_AVOIDING: return "Blockhead Avoid";
            case ROBBERY_FIGHTING: return "Robbery Hunt";
            case ROBBERY_RETREATING: return "Robbery Return";
            case ROBBERY_BANKED: return "Robbery Top 20 Hold";
            case RAFFLE_TO_MID: return "Raffle Entry";
            case RAFFLE_COLLECTING: return "Raffle Tickets "+(mc.thePlayer==null?0:raffle.tickets())+"/9";
            case RAFFLE_RETURNING: return "Raffle Return";
            case RAFFLE_DEPOSITING: return "Raffle Deposit";
            case PERK_NAVIGATING: case PERK_INTERACTING: case PERK_CLICKING_GUI: return "Perk Shop";
            case PRESTIGE_NAVIGATING: case PRESTIGE_INTERACTING: case PRESTIGE_CLICKING_GUI: return "Prestige";
            default: return s.name();
        }
    }

    private String activeEventName() {
        if (isAutoRaffleActive()) return "Raffle";
        if (isAutoBlockheadActive()) return "Blockhead";
        if (isAutoRobberyActive()) return "Robbery";
        if (hasDragonEggPriority()) return "Dragon Egg";
        if (isSpireActive()) return "Spire";
        if (isSquadsEventActive()) return "Squads";
        if (isRagePitEventActive()) return "Rage Pit";
        if (CombatAura.isTdmEventActive()) return "TDM";
        if (nightQuestActive) return "Night Quest";
        return "—";
    }

    private int activeEventColor() {
        if (isAutoRaffleActive()) return CYAN;
        if (isAutoBlockheadActive()) return CYAN;
        if (isAutoRobberyActive()) return AMBER;
        if (isSpireActive()) return PURPLE;
        if (isSquadsEventActive()) return AMBER;
        if (isRagePitEventActive()) return GuiTheme.DANGER;
        if (CombatAura.isTdmEventActive()) return GuiTheme.ACCENT;
        if (nightQuestActive) return CYAN;
        return GuiTheme.TEXT_MUTED;
    }

    /** Compact number for the per-hour readouts: 1.2k, 3.4M. */
    private String fmtRate(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000.0);
        return String.valueOf((long) v);
    }

    private String uptime() {
        long ms = sessionStartTime == 0 ? 0 : com.nezurstandalone.control.Clock.millis() - sessionStartTime;
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%02d:%02d", m, s % 60);
    }

    public static void recordInputPhase(String phase) {
        if (!isRunning()) return;
        instance.diagnosticSnapshot(phase);
    }
    public static void recordOutbound(net.minecraft.network.Packet packet) {
        if (!isRunning() || !com.nezurstandalone.control.GrinderDiagnostics.isActive()) return;
        try {
            net.minecraft.client.entity.EntityPlayerSP p=instance.mc.thePlayer;
            if(p==null)return;
            String detail;
            if(packet instanceof net.minecraft.network.play.client.C03PacketPlayer){
                net.minecraft.network.play.client.C03PacketPlayer m=(net.minecraft.network.play.client.C03PacketPlayer)packet;
                detail="type=movement positionPresent="+m.isMoving()+" rotationPresent="+m.getRotating()
                    +" rawX="+m.getPositionX()+" rawY="+m.getPositionY()+" rawZ="+m.getPositionZ()
                    +" rawYaw="+m.getYaw()+" rawPitch="+m.getPitch()+" ground="+m.isOnGround();
            }else if(packet instanceof net.minecraft.network.play.client.C0BPacketEntityAction){
                detail="type=entity_action action="+((net.minecraft.network.play.client.C0BPacketEntityAction)packet).getAction();
            }else if(packet instanceof net.minecraft.network.play.client.C02PacketUseEntity){
                detail="type=entity_interaction action="+((net.minecraft.network.play.client.C02PacketUseEntity)packet).getAction();
            }else if(packet instanceof net.minecraft.network.play.client.C0APacketAnimation){detail="type=swing";
            }else if(packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement){detail="type=use_item_or_block";
            }else return;
            com.nezurstandalone.control.GrinderDiagnostics.record("OUTBOUND_ENQUEUE",detail+" tick="+p.ticksExisted
                +" clientX="+p.posX+" clientY="+p.posY+" clientZ="+p.posZ
                +" clientYaw="+p.rotationYaw+" clientPitch="+p.rotationPitch
                +" motionX="+p.motionX+" motionY="+p.motionY+" motionZ="+p.motionZ+" sprint="+p.isSprinting());
        }catch(RuntimeException failure){com.nezurstandalone.control.GrinderDiagnostics.record("PACKET_LOG_ERROR",failure.getClass().getSimpleName());}
    }
    public static void recordVelocity(net.minecraft.network.play.server.S12PacketEntityVelocity packet,boolean before){
        if(!isRunning() || instance.mc.thePlayer==null || packet.getEntityID()!=instance.mc.thePlayer.getEntityId())return;
        try{
            com.nezurstandalone.control.GrinderDiagnostics.record(before?"VELOCITY_RECEIVED":"VELOCITY_APPLIED",
                "tick="+instance.mc.thePlayer.ticksExisted+" expectedX="+(packet.getMotionX()/8000.0)
                +" expectedY="+(packet.getMotionY()/8000.0)+" expectedZ="+(packet.getMotionZ()/8000.0)
                +" actualX="+instance.mc.thePlayer.motionX+" actualY="+instance.mc.thePlayer.motionY+" actualZ="+instance.mc.thePlayer.motionZ);
        }catch(RuntimeException failure){com.nezurstandalone.control.GrinderDiagnostics.record("VELOCITY_LOG_ERROR",failure.getClass().getSimpleName());}
    }

    private void diagnosticGameDump() {
        if(!classicRuntime()||!com.nezurstandalone.control.GrinderDiagnostics.isActive()||mc.thePlayer==null||mc.theWorld==null)return;
        long now=com.nezurstandalone.control.Clock.millis();
        if(gameDumpWorld==mc.theWorld&&gameDumpScreen==mc.currentScreen&&now-gameDumpAt<30000)return;
        if(gameDumpWorld==mc.theWorld&&now-gameDumpAt<1000)return;
        gameDumpAt=now;gameDumpWorld=mc.theWorld;gameDumpScreen=mc.currentScreen;
        java.util.Map<String,Object> data=new java.util.LinkedHashMap<>();
        data.put("capturedAt",now);data.put("state",String.valueOf(currentState));
        data.put("sidebarTitle",Utils.getScoreboardTitle());data.put("sidebarLines",Utils.getScoreboardLines());
        data.put("level",Utils.getLevel());double dumpGold=scoreboardGold();data.put("gold",Double.isFinite(dumpGold)?dumpGold:null);
        data.put("map",String.valueOf(PitMapManager.getCurrentMap()));
        data.put("zone",PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ));
        data.put("position",java.util.Arrays.asList(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ));
        data.put("guiOwner",com.nezurstandalone.control.GuiLease.ownerName());data.put("inventoryOwner",com.nezurstandalone.control.InventoryOwner.ownerName());
        data.put("movementOwner",PathfinderManager.ownerName());
        data.put("xp",sessionXP);data.put("goldEarned",sessionGold);data.put("freshDrops",sessionMystics);
        data.put("kills",hudStats.kills);data.put("assists",hudStats.assists);data.put("deaths",hudStats.deaths);
        data.put("quests",sessionNightQuests);data.put("questActive",nightQuestActive);data.put("questKills",hudStats.questKills);
        data.put("mysticPending",mysticPickupPending);data.put("mysticTarget",mysticTargetId);
        java.util.List<Object> inventory=new java.util.ArrayList<>(),drops=new java.util.ArrayList<>(),menu=new java.util.ArrayList<>();
        for(int i=0;i<mc.thePlayer.inventory.mainInventory.length;i++) {
            ItemStack stack=mc.thePlayer.inventory.mainInventory[i];if(stack!=null)inventory.add(dumpStack(i,stack));
        }
        for(Entity entity:mc.theWorld.loadedEntityList)if(entity instanceof EntityItem&&mc.thePlayer.getDistanceSqToEntity(entity)<=4096&&drops.size()<64) {
            EntityItem item=(EntityItem)entity;java.util.Map<String,Object> row=dumpStack(item.getEntityId(),item.getEntityItem());
            row.put("ageTicks",item.ticksExisted);row.put("position",java.util.Arrays.asList(item.posX,item.posY,item.posZ));
            row.put("distance",mc.thePlayer.getDistanceToEntity(item));row.put("recognizedMystic",isMysticItem(item));drops.add(row);
        }
        if(mc.currentScreen instanceof GuiChest){IInventory inv=((ContainerChest)((GuiChest)mc.currentScreen).inventorySlots).getLowerChestInventory();
            data.put("menuTitle",inv.getDisplayName().getUnformattedText());
            for(int i=0;i<inv.getSizeInventory();i++)if(inv.getStackInSlot(i)!=null)menu.add(dumpStack(i,inv.getStackInSlot(i)));
        }
        data.put("inventory",inventory);data.put("nearbyDrops",drops);data.put("menu",menu);
        com.nezurstandalone.control.GrinderDiagnostics.gameDump(new com.google.gson.Gson().toJson(data));
    }

    private boolean classicRuntime(){
        net.minecraft.client.multiplayer.ServerData server=mc.getCurrentServerData();
        return server!=null&&com.nezurstandalone.control.LowestClassicPit.classicHost(server.serverIP);
    }

    private boolean completeMysticPickupOnInventory(){
        if(!classicRuntime()||!isToggled()||mc.thePlayer==null||mc.theWorld==null
                ||engineWorld!=mc.theWorld||enginePlayer!=mc.thePlayer
                ||currentState!=State.PICKING_UP_MYSTIC||mysticPickupStack==null)return false;
        if(countMatchingMystics(mysticPickupStack)<=mysticInventoryBefore)return false;
        com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_COLLECTED","source=server_inventory cancelling_pickup_path=true");
        finishMysticPickup();return true;
    }

    private boolean reserveNpcAccess() {
        if(PathfinderManager.available(this) && com.nezurstandalone.control.GuiLease.acquire(this)) {
            npcAccessMessageAt=0L;return true;
        }
        pauseGameplay();
        PathfinderManager.clear(this,true);
        long now=com.nezurstandalone.control.Clock.millis();
        npcSpotStartedAt=navRepathAt=0L;interactStamp=now;
        if(npcAccessMessageAt==0L || now-npcAccessMessageAt>=10000L) {
            npcAccessMessageAt=now;
            log("Waiting for NPC access (GUI: "+com.nezurstandalone.control.GuiLease.ownerName()
                    +", inventory: "+com.nezurstandalone.control.InventoryOwner.ownerName()
                    +", movement: "+PathfinderManager.ownerName()+").");
        }
        return false;
    }

    private int findHypixelPrestigeConfirmation(IInventory inv) {
        String title=inv.getDisplayName().getUnformattedText();
        for(int slot=0;slot<inv.getSizeInventory();slot++) {
            ItemStack stack=inv.getStackInSlot(slot);
            if(stack!=null && PrestigeConfirmation.hypixelButton(title,stack.getDisplayName(),
                    net.minecraft.item.Item.getIdFromItem(stack.getItem()),stack.getMetadata(),loreOf(stack)))return slot;
        }
        return findAction(inv,"Confirm","Confirm!","Prestige","Prestige!");
    }

    private void recordMysticDrop(String msg,boolean classic,int channel){
            long now=com.nezurstandalone.control.Clock.millis();
            if(now-lastMysticCountMs>400L){
                lastMysticCountMs=now;sessionMystics++;
                if(classic){
                    lastMysticChatTime=now;mysticPickupPending=true;mysticPendingWorld=mc.theWorld;
                    mysticDropX=mc.thePlayer.posX;mysticDropY=mc.thePlayer.posY;mysticDropZ=mc.thePlayer.posZ;
                }else if(mysticPickup.isEnabled() && !isBusyFlow()){
                    lastMysticChatTime=now;
                    mysticDropX=mc.thePlayer.posX;mysticDropY=mc.thePlayer.posY;mysticDropZ=mc.thePlayer.posZ;
                    mysticRepathAt=lastMysticTraceTime=0L;sawMysticItem=false;mysticTargetId=-1;
                    mysticPickupStack=null;mysticMissingSince=0L;currentState=State.PICKING_UP_MYSTIC;
                    log("Mystic dropped — picking it up at all costs!");
                }
                com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_CHAT","channel="+channel+" state="+currentState+" busy="+isBusyFlow()+" enabled="+mysticPickup.isEnabled()+" message="+msg);
            }
    }

    private boolean refreshTdmContext(){
        long token=com.nezurstandalone.control.ClientSession.current();
        boolean active=isToggled()&&mc.theWorld!=null&&mc.thePlayer!=null&&CombatAura.isTdmEventActive();
        if(tdmWorld!=mc.theWorld||active!=tdmWasActive){
            tdmAssists.reset();tdmAssistBaseline=hudStats.assists;
        }else if(tdmPlayer!=mc.thePlayer||tdmSession!=token)tdmAssists.invalidatePending();
        tdmWorld=mc.theWorld;tdmPlayer=mc.thePlayer;tdmSession=token;tdmWasActive=active;
        return active;
    }

    private boolean tdmAllows(EntityPlayer player){
        if(!refreshTdmContext())return true;
        observeTdmLife(player);
        if(!tdmAssists.allows(player.getUniqueID()))return false;
        com.nezurstandalone.combat.TargetEvaluation estimate=new com.nezurstandalone.combat.TargetEvaluation(mc.thePlayer,player,activeCombatOptions().range(),5.6,.5,6);
        return TdmAssistTracker.survivesTag(player.getHealth()+player.getAbsorptionAmount(),estimate.damage,estimate.multiplier);
    }

    private void observeTdmLife(EntityPlayer player){
        if(player==null||player==mc.thePlayer)return;
        // Removal from view is not death. A real death or a return to spawn ends that life.
        String zone=PitMapManager.getZone(player.posX,player.posY,player.posZ);
        if(player.getHealth()<=0||player.deathTime>0)tdmAssists.death(player.getUniqueID(),player);
        else if("Spawn".equals(zone)||"Overspawn".equals(zone))tdmAssists.death(player.getUniqueID());
        else tdmAssists.seen(player.getUniqueID(),player);
    }

    private void tdmAttackSent(EntityPlayer player){
        if(refreshTdmContext()&&player.worldObj==mc.theWorld)tdmAssists.sent(player.getUniqueID(),player,com.nezurstandalone.control.Clock.SYSTEM.nanos());
    }

    private void tdmHurtObserved(EntityPlayer player){
        if(refreshTdmContext()&&player.worldObj==mc.theWorld&&tdmAssists.hurt(player.getUniqueID(),player,com.nezurstandalone.control.Clock.SYSTEM.nanos()))
            com.nezurstandalone.control.GrinderDiagnostics.record("TDM_TAG","player="+player.getName()+" switching to an untagged opponent");
    }

    public static void serverHurtObserved(Entity entity){
        GrinderEngine g=instance;
        if(g!=null&&g.isToggled()&&entity instanceof EntityPlayer&&entity!=g.mc.thePlayer&&entity.worldObj==g.mc.theWorld)
            g.tdmHurtObserved((EntityPlayer)entity);
    }

    public static void serverDeathObserved(Entity entity){
        GrinderEngine g=instance;if(g==null||!g.isToggled()||entity==null||entity.worldObj!=g.mc.theWorld)return;
        if(entity==g.mc.thePlayer)g.hudStats.serverDeath(com.nezurstandalone.control.Clock.millis());
        else if(entity instanceof EntityPlayer&&g.refreshTdmContext())g.tdmAssists.death(((EntityPlayer)entity).getUniqueID(),entity);
    }

    public static void serverAttackSent(Entity entity){
        GrinderEngine g=instance;
        if(g!=null&&g.isToggled()&&entity instanceof EntityPlayer&&entity.worldObj==g.mc.theWorld)
            g.tdmAttackSent((EntityPlayer)entity);
    }
    private long npcAccessMessageAt,gameDumpAt,mysticPickupStarted;
    private boolean mysticPickupPending;
    private Object mysticPendingWorld,gameDumpWorld,gameDumpScreen;
    private final TdmAssistTracker tdmAssists=new TdmAssistTracker();
    private Object tdmWorld,tdmPlayer;
    private long tdmSession=-1;
    private boolean tdmWasActive;
    private int tdmAssistBaseline;


    private java.util.Map<String,Object> dumpStack(int slot,ItemStack stack){
        java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();row.put("slotOrEntityId",slot);
        if(stack!=null){row.put("name",stack.getDisplayName());row.put("itemId",net.minecraft.item.Item.getIdFromItem(stack.getItem()));
            row.put("damage",stack.getItemDamage());row.put("count",stack.stackSize);row.put("lore",loreOf(stack));}
        return row;
    }
}
