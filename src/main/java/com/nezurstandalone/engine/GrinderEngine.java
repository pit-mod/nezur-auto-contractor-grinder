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
 * combat is the {@link CombatAura} self-defence driver carrying the grinder's smart target
 * selection (team filter, low-HP priority, Focus/Friend/line-of-sight).
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
        IDLE, LEAVING_SPAWN, FIGHTING, DRAGON_EGG, LOBBY_SWAP, LIMBO, LOBBY_RECONNECT, JOINING_PIT, PICKING_UP_MYSTIC,
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
    private final Object pitClickOwner = new Object();
    private final com.nezurstandalone.combat.AutoClicker pitClicker = new com.nezurstandalone.combat.AutoClicker()
            .pauseChance(0.0).emitter(() -> GuardedInput.attackCrosshair(pitClickOwner, this::canPitClick));

    private boolean canPitClick() {
        return isToggled() && correctionTick < 0 && !isOnBreak && !hasDragonEggPriority() && !isAutoRaffleActive() && !isAutoBlockheadActive() && mc.thePlayer != null && mc.theWorld != null
                && (!isAutoRobberyActive() || (combatPermitted && combat.getTarget() instanceof EntityPlayer
                    && robbery.canTarget((EntityPlayer)combat.getTarget()) && mc.objectMouseOver!=null
                    && mc.objectMouseOver.entityHit==combat.getTarget()))
                && mc.currentScreen == null && !mc.thePlayer.isDead && mc.thePlayer.getHealth() > 0
                && !mc.thePlayer.isUsingItem() && !isAutoHealBusy()
                && mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer
                && com.nezurstandalone.control.InventoryOwner.available(this)
                && PitMapManager.getZone(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ).equals("Pit")
                && com.nezurstandalone.contract.ContractCombatPolicy.allowsCrosshairAttack();
    }

    private boolean hasDragonEggPriority() {
        return dragonEggSupport.isEnabled() && currentEgg != null;
    }

    public static boolean usesPitClicker(CombatAura caller) {
        return instance != null && instance.combat == caller && instance.canPitClick();
    }


    private String diagnosticState = "";
    private long diagnosticCorrections, diagnosticWindowStart;
    private int diagnosticWindowCount;
    private double diagnosticX, diagnosticY, diagnosticZ;
    private long diagnosticPacketStart;
    private void diagnosticTick() {
        try {
            com.nezurstandalone.control.GrinderDiagnostics.setActive(isToggled(), new java.io.File(mc.mcDataDir, "logs/nezur-diagnostics"));
            if (!isToggled()) return;
            String state = String.valueOf(currentState);
            if (!state.equals(diagnosticState)) {
                com.nezurstandalone.control.GrinderDiagnostics.record("STATE", "from="+diagnosticState+" to="+state);
                diagnosticState=state;
            }
            if (com.nezurstandalone.control.GrinderDiagnostics.sampleDue()) diagnosticSnapshot("SAMPLE");
        } catch (RuntimeException ignored) { /* Diagnostics must not control gameplay. */ }
    }
    private void diagnosticSnapshot(String event) {
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
                +" combatStage="+combat.getStage()+" combatPermitted="+combatPermitted+" clickEligible="+canPitClick()
                +" cpsMin="+botCpsMin.value+" cpsMax="+botCpsMax.value+" healing="+isAutoHealBusy()
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
        return isMidPressureActive() && instance.combat == caller;
    }


    public static void onMenuContents(int window,Object container){if(instance!=null)instance.menuTransition.contents(window,container);}
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
    private final BooleanSetting dragonEggSupport = new BooleanSetting("Dragon Egg Support", true);
    private final BooleanSetting muteGame = new BooleanSetting("Mute Game", false);
    private final BooleanSetting breakTimeEnable = new BooleanSetting("Enable Break Time", false);
    private final NumberSetting workDurationLimit = new NumberSetting("Work Time (h)", 2, 0.1, 24, 1);
    private final NumberSetting breakDurationLimit = new NumberSetting("Break Time (m)", 20, 1, 120, 0);
    private final NumberSetting ttkSpeed=new NumberSetting("TTK Movement Speed",5.6,1,8,1),
            ttkInterval=new NumberSetting("TTK Hit Interval",0.5,0.5,1.5,2),
            ttkDamage=new NumberSetting("TTK Fallback Damage",6,1,20,1),
            ttkSwitch=new NumberSetting("TTK Switch Improvement %",20,5,50,0);
    private final BooleanSetting ttkDebug=new BooleanSetting("TTK Debug",false);
    private final BooleanSetting prioritizeHealth = new BooleanSetting("Target Fastest Kill", true);
    private final BooleanSetting teamCheckTdm = new BooleanSetting("Team Check (TDM)", true);
    // Aim reach is no longer a slider - it is just the fallback search range used when nobody is
    // within attack reach. Attack reach is what decides the target.
    private static final double AIM_FALLBACK_REACH = 30.0;
    private final NumberSetting botAttackReach = new NumberSetting("Attack Reach", 3.0, 1.0, 3.0, 1);
    private final NumberSetting botCpsMin = new NumberSetting("CPS Min", 8, 3, 16, 0);
    private final NumberSetting botCpsMax = new NumberSetting("CPS Max", 12, 3, 20, 0);
    private final NumberSetting combatAimSpeed = new NumberSetting("Combat Aim Speed", 15, 5, 30, 1);
    private final NumberSetting interactAimSpeed = new NumberSetting("Interact Aim Speed", 16, 5, 30, 1);
    private final BooleanSetting onlyEventMode = new BooleanSetting("Only Event Mode", false);
    private final BooleanSetting showHud = new BooleanSetting("Show HUD", true);
    private final BooleanSetting hudAnimations = new BooleanSetting("HUD Animations", true);
    private final BooleanSetting debugLogging = new BooleanSetting("Debug Logging", false);

    // ---- combat -------------------------------------------------------------
    private final CombatAura combat = new CombatAura().cps(8, 12).reach(3.0).fovToClick(60.0).clickRange(3.0).nearbyFirst(5.0)
            .allowedWhen(this::mayFight)
            .allowUnlistedPitNpcsWhen(() -> isClassicPitTitle(Utils.getScoreboardTitle()));

    private boolean mayFight() {
        return combatPermitted && isToggled() && !isOnBreak && !isAutoHealBusy() && !hasDragonEggPriority() && !isAutoBlockheadActive()
                && PathfinderManager.available(this) && isCombatState(currentState);
    }

    private static boolean isCombatState(State state) {
        return state == State.FIGHTING || state == State.SPIRE_FIGHTING
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
    private final Spring hudAppear = new Spring(0f);
    private final Spring midSpring = new Spring(0f);
    private final Spring mysticSpring = new Spring(0f);
    private final Spring hpSpring = new Spring(0f);
    private final Spring hpLagSpring = new Spring(0f);
    private final Spring activitySpring = new Spring(0f);

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
    private boolean ksNeedSync = false;
    private final boolean[] ksAttempted = new boolean[2];
    private String lastKsSig = "";
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
    private String lastPerkSig = "";
    private long interactStamp = 0L;
    private long navRepathAt = 0L;
    private long npcSpotStartedAt;
    private long lastPerkGoldGateLogAt;
    private double deferredSyncGoldRequirement = Double.NaN;
    private String deferredSyncGoldSignature = "";
    private long deferredSyncGoldUntil;
    private boolean perkGoldPreflightDone;
    private double preflightPerkGoldCost;

    private void refreshPerkAccount() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        String host = mc.getCurrentServerData() == null ? "local" : mc.getCurrentServerData().serverIP;
        String identity = mc.thePlayer.getUniqueID().toString() + "@" + host;
        if (identity.equals(lastPerkUser)) return;
        lastPerkUser = identity;
        contractKungFuUnavailable = false;
        deferredSyncGoldRequirement = Double.NaN;
        deferredSyncGoldSignature = "";
        deferredSyncGoldUntil = 0L;
        lastSeenLevel = -1;
        perksNeedSync = ksNeedSync = true;
        npcRetryAfterNanos = 0L;
        java.util.Arrays.fill(perkAttempted, false);
        java.util.Arrays.fill(ksAttempted, false);
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
        com.nezurstandalone.control.SessionResets.register(this,this::resetSessionActivity);
        addSettings(minMidPlayers, lobbySwap, lobbySwapDelay, autoWeapon, mysticPickup, idleRetreat,
                spireMode, autoBlockhead, autoRobbery, autoRaffle, squadSupport, nightQuestSupport, autoPerk, perk1, perk2, perk3, perk4, theWay,
                autoKillstreak, ks1, ks2,
                autoPrestige, dragonEggSupport, muteGame,
                breakTimeEnable, workDurationLimit, breakDurationLimit,
                prioritizeHealth,ttkSpeed,ttkInterval,ttkDamage,ttkSwitch,ttkDebug, teamCheckTdm, botAttackReach, botCpsMin, botCpsMax,
                combatAimSpeed, interactAimSpeed, onlyEventMode, showHud, hudAnimations, debugLogging);
        for (final com.nezurstandalone.settings.PerkSetting b : perkBoxes()) {
            b.visibleOptions = () -> visiblePerksFor(b);
        }
        for (final com.nezurstandalone.settings.PerkSetting b : ksBoxes()) {
            b.visibleOptions = () -> visibleStreaksFor(b);
        }
        autoPerk.addChangeListener(s -> updatePerkVisibility());
        autoKillstreak.addChangeListener(s -> updatePerkVisibility());
        updatePerkVisibility();
    }

    private void resetSessionActivity(){
        blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
        deferredSyncGoldRequirement=Double.NaN;deferredSyncGoldSignature="";deferredSyncGoldUntil=0L;
        perkGoldPreflightDone=false;preflightPerkGoldCost=0.0;
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
        refreshPerkAccount();java.util.Arrays.fill(perkAttempted,false);java.util.Arrays.fill(ksAttempted,false);
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
        combat.ttk(ttkSpeed.value,ttkInterval.value,ttkDamage.value,ttkSwitch.value,ttkDebug.enabled);
        combat.cps(botCpsMin.value, botCpsMax.value).reach(botAttackReach.value);
        if (sessionStartTime == 0L) sessionStartTime = com.nezurstandalone.control.Clock.millis();
        hudAppear.value = 0f; hudAppear.velocity = 0f;
        PathfinderManager.combatSettings(false,true,botAttackReach.value); // stacked mid: forward pressure only, no strafe
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
        lastKsSig = ks1.getMode() + "|" + ks2.getMode();
        lastPerkSig = perk1.getMode() + "|" + perk2.getMode() + "|" + perk3.getMode() + "|" + perk4.getMode();
    }

    @Override
    protected void onDisable() {
        blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
        cancelMysticStow();
        com.nezurstandalone.control.GrinderDiagnostics.setActive(false, new java.io.File(mc.mcDataDir, "logs/nezur-diagnostics"));
        pauseGameplay();
        resetMenuTracking();
        combat.stop();
        combat.resetTargeting();
        // Full wipe on disable too: guarantees the next enable starts from a clean walker
        // regardless of what state the previous session left behind.
        com.nezurstandalone.pathfinder.PathfinderManager.resetIfAvailable(this);
        PathfinderManager.clear(this, true);
        PathfinderManager.combatSettings(true,false,botAttackReach.value);
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
        pitClicker.reset();
        GuardedInput.cancel(pitClickOwner);
        com.nezurstandalone.control.MovementKeys.release("grinder");
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
            pitClicker.reset();
            GuardedInput.cancel(pitClickOwner);
            combat.stop();
            return;
        }
        // Render frames must obey the same correction barrier as client ticks and queued dispatch.
        if (correctionTick >= 0) {
            pauseGameplay();
            return;
        }
        // Egg interaction owns aim and clicks; cancel queued attacks before either click driver runs.
        if (hasDragonEggPriority()) {
            pitClicker.reset();
            GuardedInput.cancel(pitClickOwner);
            releaseCombat();
            combat.resetTargeting();
            return;
        }
        // Region-scoped click pump runs even when target selection is empty or combat aim is paused.
        if (canPitClick()) {
            pitClicker.cps(botCpsMin.value, botCpsMax.value).onRenderFrame(true);
        } else {
            pitClicker.reset();
            GuardedInput.cancel(pitClickOwner);
        }

        if (!isToggled() || !combatPermitted || isOnBreak || isAutoBlockheadActive() || mc.thePlayer == null
                || mc.theWorld == null || mc.currentScreen != null || mc.thePlayer.isDead
                || mc.thePlayer.getHealth() <= 0 || !PathfinderManager.available(this)
                || (mc.thePlayer.isUsingItem() && !isAutoHealBusy())
                || !isCombatState(currentState)) {
            combat.stop();
            return;
        }
        combat.rotationSpeed((float) combatAimSpeed.value);
        if (isAutoHealBusy()) { combat.onAimOnlyFrame(); return; }
        combat.onRenderFrame();
    }

    private boolean shouldSyncOnLevelUp(int level) {
        if (level == 10 || level == 35 || level == 70 || level == 75) return true;
        if (level == 100 && !"No Perk".equals(perk4.getMode())) return true;
        for (com.nezurstandalone.settings.PerkSetting box : perkBoxes()) {
            Perk p = perkByName(box.getMode());
            if (p != null && p.minLevel == level) return true;
        }
        for (com.nezurstandalone.settings.PerkSetting box : ksBoxes()) {
            Perk p = perkByName(box.getMode());
            if (p != null && p.minLevel == level) return true;
        }
        return false;
    }

    private boolean closeUnaffordablePerkMenu() {
        if (mc.thePlayer == null || !(mc.currentScreen instanceof GuiChest)
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
        if (event.phase != TickEvent.Phase.START) return;
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
        combatPermitted = false;
        if (engineWorld != mc.theWorld || enginePlayer != mc.thePlayer) {
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
        if (mc.thePlayer != null && (mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0)) wasDeadLastTick = true;
        if (!isToggled() || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0) {
            blockhead.reset(); blockheadRepathAt=0; blockheadOwned=false; robbery.reset();robberyOwned=false;robberyRetreatAt=0; resetRaffle();
            pauseGameplay();
            resetMenuTracking();
            return;
        }

        if (correctionTick >= 0) {
            pauseGameplay();
            if (mc.thePlayer.ticksExisted - correctionTick < 3 || mc.currentScreen != null
                    || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                    || Utils.getScoreboardTitle().isEmpty()) return;
            correctionTick = -1;
            diagnosticSnapshot("CORRECTION_RESUME");
        }
        combat.ttk(ttkSpeed.value,ttkInterval.value,ttkDamage.value,ttkSwitch.value,ttkDebug.enabled);
        combat.cps(botCpsMin.value, botCpsMax.value).reach(botAttackReach.value);
        PathfinderManager.combatSettings(false,true,botAttackReach.value);
        if (menuTransition.isPending() && menuTransition.failed(System.nanoTime())) { failMenu("Menu transition timed out."); return; }
        handleMuteGameTick();

        refreshPerkAccount();
        int currentLevel = Utils.getLevel();
        if (currentLevel > 0 && currentLevel != lastSeenLevel) {
            contractKungFuUnavailable = false;
            if (lastSeenLevel != -1 && shouldSyncOnLevelUp(currentLevel)) {
                if (autoPerk.isEnabled()) { perksNeedSync = true; java.util.Arrays.fill(perkAttempted, false); }
                if (autoKillstreak.isEnabled()) { ksNeedSync = true; java.util.Arrays.fill(ksAttempted, false); }
            }
            lastSeenLevel = currentLevel;
        }

        // A perk box changed → sync the shop next chance (only matters while Auto Perk is on).
        String psig = perk1.getMode() + "|" + perk2.getMode() + "|" + perk3.getMode() + "|" + perk4.getMode();
        if (!psig.equals(lastPerkSig)) { lastPerkSig = psig; if (autoPerk.isEnabled()) { perksNeedSync = true; java.util.Arrays.fill(perkAttempted, false); } }
        String ksig = ks1.getMode() + "|" + ks2.getMode();
        if (!ksig.equals(lastKsSig)) { lastKsSig = ksig; if (autoKillstreak.isEnabled()) { ksNeedSync = true; java.util.Arrays.fill(ksAttempted, false); } }

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
        if (!(hasTemporaryPerkLease() && (contractKungFuRestoring || !contractKungFuReady))
                && startMysticStow()) return;

        if (currentState == State.LIMBO || currentState == State.LOBBY_RECONNECT || currentState == State.JOINING_PIT) {
            currentState = State.IDLE;
            limboSentOnce = false;
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

        if (zone.equals("Pit")) {
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
        currentState = State.FIGHTING;
        oofCooldown = 0L;
        long now = com.nezurstandalone.control.Clock.millis();
        EntityPlayer target = combat.pickTarget(AIM_FALLBACK_REACH, botAttackReach.value,
                true, prioritizeHealth.isEnabled(), teamCheckTdm.isEnabled());
        if (target != null) {
            equipBestWeapon(); // idempotent + heal-gated: re-arms after a heal, never spams
            combatPermitted = true;
            combat.setTarget(target);
            driveCombatMovement(target);
            lastAttackTime = now;
        } else {
            releaseCombat();
            combat.resetTargeting();
            combatMoveStuckSince = 0L;
            if (idleRetreat.isEnabled() && now - lastAttackTime > 3000L) navigateToMid();
            else PathfinderManager.clear(this, true);
        }
    }

    /**
     * Runs the bot straight at the target (the walker's combat block faces and sprints at it) with
     * no A* path - pathing to a moving player recomputed every tick, which drew that curving route
     * line and constantly re-found. Only when the direct chase is genuinely stuck - collided and
     * not making ground for ~1.5s - does it hand off to the pathfinder to route around whatever is
     * in the way, then snaps back to the straight chase the moment it is moving again.
     */
    private void driveCombatMovement(EntityPlayer target) {
        long now = com.nezurstandalone.control.Clock.millis();

        // Someone is inside attack reach: there is nothing to path to - just fight them. Drop any
        // route immediately so none is computed or rendered while they are in range. The dragon
        // egg / perk / mystic flows run before combat and never reach here, so they keep their paths.
        if (mc.thePlayer.getDistanceToEntity(target) <= botAttackReach.value + 0.5) {
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
        boolean stuck = mc.thePlayer.isCollidedHorizontally && now - combatMoveProgressAt > 1500L;

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
            if (now >= combatRepathAt && !combatPathFailed && PathfinderManager.getState() != PathfinderManager.State.SEARCHING) {
                PathfinderManager.walkTo(this, target.posX, target.posY, target.posZ, true);
                combatRepathAt = now + 600L;
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

    private void navigateToMid() {navigateContractMid(this);}
    public void navigateContractMid(Object owner) {
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
        releaseCombat();combat.resetTargeting();pitClicker.reset();GuardedInput.cancel(pitClickOwner);
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
        EntityPlayer target=combat.pickTarget(5,botAttackReach.value,true,true,teamCheckTdm.isEnabled(),robbery::canTarget,robbery::targetScore);
        currentState=State.ROBBERY_FIGHTING;
        if(target!=null) {
            releaseEventApproach();
            equipBestWeapon();combatPermitted=true;combat.setTarget(target);driveCombatMovement(target);lastAttackTime=now;
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
        releaseCombat();combat.resetTargeting();pitClicker.reset();GuardedInput.cancel(pitClickOwner);
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
            EntityPlayer target = combat.pickTarget(AIM_FALLBACK_REACH, botAttackReach.value,
                    false, prioritizeHealth.isEnabled(), teamCheckTdm.isEnabled());
            if (target != null) {
                equipBestWeapon(); // idempotent + heal-gated
                combatPermitted = true;
                combat.setTarget(target);
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
        if (com.nezurstandalone.contract.GoldPickupRecovery.ignoresPopulation(com.nezurstandalone.contract.ContractCombatPolicy.type())) {
            currentState = State.IDLE; lobbyFirstCommand = false; squadSwapForced = false; return;
        }
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        if (!squadSwapForced && !shouldLobbySwap()) { currentState = State.IDLE; lobbyFirstCommand = false; return; }
        long delay = lobbyFirstCommand ? 5000L : 1000L;
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - lobbyStamp < delay || now < throttleCooldownEnd) return;
        if (Utils.isInSpawn()) {
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/l")) return;
            lobbyFirstCommand = true;
            squadSwapForced = false;
            lobbyStamp = now;
        }
    }

    private void handleLobbyReconnect() {
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        releaseCombat();
        PathfinderManager.clear(this, true);
        long now = com.nezurstandalone.control.Clock.millis();
        if (currentState == State.LOBBY_SWAP) {
            long delay = lobbyFirstCommand ? (long) (lobbySwapDelay.value * 1000L) : 1000L;
            if (now - lobbyStamp < delay || now < throttleCooldownEnd) return;
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/play pit")) return;
            lobbyFirstCommand = true;
            lobbyStamp = now;
            return;
        }
        if (currentState != State.LOBBY_RECONNECT && currentState != State.JOINING_PIT) {
            currentState = State.LOBBY_RECONNECT;
            reconnectStamp = now;
        }
        if (currentState == State.LOBBY_RECONNECT && now - reconnectStamp > 5000L) {
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/play pit")) return;
            currentState = State.JOINING_PIT;
            reconnectStamp = now;
        } else if (currentState == State.JOINING_PIT && now - reconnectStamp > 10000L) {
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/play pit")) return;
            reconnectStamp = now;
        }
    }

    private void handleLimbo() {
        if (isClassicPitTitle(Utils.getScoreboardTitle())) { currentState = State.IDLE; return; }
        releaseCombat();
        PathfinderManager.clear(this, true);
        long now = com.nezurstandalone.control.Clock.millis();
        if (currentState != State.LIMBO) { currentState = State.LIMBO; limboSentOnce = false; reconnectStamp = now; }
        long wait = limboSentOnce ? 10000L : 5000L;
        if (now - reconnectStamp > wait) {
            if (mc.currentScreen != null) closeCurrentScreen();
            if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/l")) return;
            limboSentOnce = true;
            reconnectStamp = now;
        }
    }

    // ---- dragon egg ---------------------------------------------------------
    // Farmed with the exact ChestAura recipe: approach with the walker, aim at a raytrace-verified
    // visible point through the rotation manager, and right-click only when the crosshair is truly
    // on the egg block, we are not mid-dig, and the humanised rate allows it. No blind interacts.
    private final com.nezurstandalone.utils.NearestEggScan eggScan = new com.nezurstandalone.utils.NearestEggScan();
    private void scanDragonEgg() {
        long now = com.nezurstandalone.control.Clock.millis();
        if (currentEgg != null && (!mc.theWorld.isBlockLoaded(currentEgg)
                || mc.theWorld.getBlockState(currentEgg).getBlock() != Blocks.dragon_egg)) currentEgg=null;
        // The shared section index can take seconds to reach a nearby egg. A small loaded-only
        // local pass gives prompt mid detection without loading chunks or scanning the whole world.
        if (currentEgg == null && now >= eggScanStamp) {
            eggScanStamp=now+500L;
            BlockPos origin=new BlockPos(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
            double best=Double.MAX_VALUE;
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-4;y<=4;y++) {
                BlockPos at=origin.add(x,y,z);
                if(mc.theWorld.isBlockLoaded(at) && mc.theWorld.getBlockState(at).getBlock()==Blocks.dragon_egg) {
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
            pitClicker.reset(); GuardedInput.cancel(pitClickOwner);
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            handleSpawn();
            return;
        }
        boolean entering = currentState != State.DRAGON_EGG || !egg.equals(lastEggPos);
        currentState = State.DRAGON_EGG;
        pitClicker.reset(); GuardedInput.cancel(pitClickOwner);
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
                && mc.theWorld.getBlockState(pos).getBlock() == Blocks.dragon_egg, eggClickRate::consume);
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
        if (!mysticPickup.isEnabled()) { finishMysticPickup(); return; }
        releaseCombat();
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
            if (now - mysticMissingSince > 5000L && now - lastMysticChatTime > 8000L)
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
                    && ItemStack.areItemStackTagsEqual(stack, target)
                    && stack.getDisplayName().equals(target.getDisplayName())) count += stack.stackSize;
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
        // Send one ordinary vanilla quick-move per client tick. WindowActions' multi-signal
        // ACK wait was leaving this screen stuck after its first move on some Pit servers.
        // The local container updates immediately, so the next tick picks the next mystic.
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
            mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,
                    slot + 36, 0, 1, mc.thePlayer);
            mysticStowStarted = now;
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", "vanilla_quick_move hotbar="+slot);
        } catch (RuntimeException ex) {
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_STOW", "failed="+ex.getMessage());
            mysticStowRetryAt = now + 3000L;
            cancelMysticStow();
        }
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
        currentState = State.IDLE;
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
            double d = mc.thePlayer.getDistanceToEntity(ei);
            if (ei.getEntityId() == mysticTargetId && d < 48.0) return ei;
            if (d > 35.0 || ei.ticksExisted > 120) continue;
            boolean named = isMysticItem(ei);
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
        if (System.nanoTime() < npcRetryAfterNanos) return false;
        if (!hasTemporaryPerkLease() && (!autoPerk.isEnabled() || !anySelected(perkBoxes()))) perksNeedSync = false;
        if (!autoKillstreak.isEnabled() || !anySelected(ksBoxes())) ksNeedSync = false;
        if (!perksNeedSync && (!ksNeedSync || hasTemporaryPerkLease())) return false;
        if (!Utils.isInSpawn()) return false;
        String goldSignature = syncGoldSignature();
        long nowMs = com.nezurstandalone.control.Clock.millis();
        if (nowMs < deferredSyncGoldUntil && goldSignature.equals(deferredSyncGoldSignature)) {
            double balance = scoreboardGold();
            if (Double.isNaN(balance) || balance + 0.000001 < deferredSyncGoldRequirement) {
                logInsufficientSyncGold(deferredSyncGoldRequirement, balance);
                return false;
            }
            deferredSyncGoldRequirement = Double.NaN;
            deferredSyncGoldUntil = 0L;
        }
        log("Syncing " + (perksNeedSync ? "perks" : "killstreaks") + " - heading to UPGRADES NPC.");
        pauseGameplay();
        resetMenuTracking();
        npcPosIndex = 0;
        npcSpotStartedAt = 0L;
        interactStamp = 0L;
        navRepathAt = 0L;
        currentState = State.PERK_NAVIGATING;
        return true;
    }

    private String syncGoldSignature() {
        return perk1.getMode()+"|"+perk2.getMode()+"|"+perk3.getMode()+"|"+perk4.getMode()+"|"
                +ks1.getMode()+"|"+ks2.getMode()+"|p="+perksNeedSync+"|k="+ksNeedSync;
    }

    private static boolean anySelected(com.nezurstandalone.settings.PerkSetting[] boxes) {
        for (com.nezurstandalone.settings.PerkSetting b : boxes) if (!"No Perk".equals(b.getMode())) return true;
        return false;
    }

    private boolean startPrestigeProcess() {
        if (System.nanoTime() < npcRetryAfterNanos) return false;
        if (findNpc(true) == null) return false;
        log("Prestige available — heading to PRESTIGE NPC.");
        pauseGameplay();
        resetMenuTracking();
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
        Entity npc = findNpc(prestige);
        if (npc == null) { abortNpc("NPC vanished."); return; }
        if (!Utils.isInSpawn()) { abortNpc("Fell out of spawn."); return; }

        double[] off = NPC_OFFSETS[npcPosIndex % NPC_OFFSETS.length];
        double sx = npc.posX;
        double sz = npc.posZ;
        double distToNpc = mc.thePlayer.getDistanceToEntity(npc);

        // Close enough to interact from: stop the walker completely and switch to aiming. This
        // fires on distance-to-NPC alone (any spot within reach), so a path that stops a little
        // short still hands off - the old 1.5-3.0 window could be missed and it never aimed.
        if (distToNpc <= 0.45) {
            com.nezurstandalone.pathfinder.PathfinderManager.stopIfAvailable(this);
            PathfinderManager.clear(this, true);
            com.nezurstandalone.control.MovementKeys.release("grinder-npc");
            interactStamp = com.nezurstandalone.control.Clock.millis();
            currentState = prestige ? State.PRESTIGE_INTERACTING : State.PERK_INTERACTING;
            return;
        }
        if(distToNpc<=2.0){
            PathfinderManager.clear(this,true);float[] rot=RotationUtils.getRotations(npc,0,-0.35,0);
            RotationManager.getInstance().setTargetRotation(INTERACTION_ROTATION_OWNER,RotationManager.PRIORITY_COMBAT,rot[0],rot[1],16,true);
            com.nezurstandalone.control.MovementKeys.set("grinder-npc",mc.gameSettings.keyBindForward.getKeyCode(),Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(rot[0]-mc.thePlayer.rotationYaw))<20);
        }
        long now = com.nezurstandalone.control.Clock.millis();
        if (npcSpotStartedAt == 0L) npcSpotStartedAt = now;
        if (now - npcSpotStartedAt >= 8000L || (navRepathAt != 0L
                && PathfinderManager.getState() == PathfinderManager.State.FAILED)) {
            advanceNpcSpot(prestige);
            return;
        }
        if (distToNpc>2.0 && now >= navRepathAt && !PathfinderManager.isPathing()) {
            RotationManager.getInstance().clearTarget(INTERACTION_ROTATION_OWNER);
            PathfinderManager.walkTo(this, sx, npc.posY, sz, true);
            navRepathAt = now + 1200L;
        }
    }

    /** Aim at the villager and open it with a crosshair-validated interact; retry from a new spot. */
    private void handleNpcInteract(boolean prestige) {
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
        MovingObjectPosition look = mc.objectMouseOver;
        if (mc.thePlayer.getDistanceToEntity(npc)<=3.0 && !mc.playerController.getIsHittingBlock()) {
            if (npcClickRate.ready()) {
                GuardedInput.attackCrosshair(interactionOwner, () -> isToggled() && !isAutoHealBusy()
                        && (currentState == State.PERK_INTERACTING || currentState == State.PRESTIGE_INTERACTING));
                {
                    npcClickRate.consume(); npcInteractionUntilNanos = System.nanoTime() + 10_000_000_000L;
                }
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
        npcRetryAfterNanos = Math.max(npcRetryAfterNanos, System.nanoTime() + 60_000_000_000L);
        perkGoldPreflightDone=false;preflightPerkGoldCost=0.0;
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
        perkGoldPreflightDone=false;preflightPerkGoldCost=0.0;
        pauseGameplay();
        resetMenuTracking();
        if (mc.currentScreen != null) mc.thePlayer.closeScreen();
        PathfinderManager.clear(this, true);
        // forever. It is reset only when a fresh sync is requested (enable / prestige / box change). targetPerkName = "No Perk";
        prestigeConfirmStarted = false;
        lastMenuTitle = "";
        currentState = State.LEAVING_SPAWN;
    }

    /**
     * The clickable villager under the "UPGRADES" / "PRESTIGE" nametag. Always a villager — the
     * floating armor-stand nametag itself is never returned, because aiming at it would put the
     * crosshair above the NPC and the interact would never validate. Returns null if no villager
     * sits under the tag, so the flow aborts cleanly rather than clicking a nametag.
     */
    private Entity findNpc(boolean prestige) {
        String tag = prestige ? "PRESTIGE" : "UPGRADES";
        Entity nameTag = null;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityArmorStand) || !e.hasCustomName()) continue;
            if (StringUtils.stripControlCodes(e.getCustomNameTag()).toUpperCase().contains(tag)) {
                nameTag = e;
                break;
            }
        }
        if (nameTag == null) return null;
        Entity best = null;
        double bestDist = 10.0; // the villager sits a few blocks under the floating tag
        for (Entity v : mc.theWorld.loadedEntityList) {
            if (!(v instanceof EntityVillager)) continue;
            double d = nameTag.getDistanceToEntity(v);
            if (d < bestDist) { bestDist = d; best = v; }
        }
        return best; // villager or null, never the nametag
    }

    // ---- perk GUI -----------------------------------------------------------
    /** True once the server has actually populated the open menu (any non-empty slot). */
    private void resetMenuTracking() {
        prestigeMessage.cancel();
        com.nezurstandalone.control.GuiLease.release(this);
        menuTransition.clear();
        observedMenu = null;
        observedContents = "";
        lastMenuTitle = "";
        nextGuiClickNanos = 0L;
        prestigeConfirmStarted = false;
    }

    private void failMenu(String reason) {
        npcRetryAfterNanos = System.nanoTime() + 60_000_000_000L;
        NotificationManager.show("§e[AutoGrinder] " + reason + " Leaving the failed menu and resuming.", 5000);
        // Keep sync requested. An attempted click is not evidence of a successful purchase.
        java.util.Arrays.fill(perkAttempted, false);
        java.util.Arrays.fill(ksAttempted, false);
        abortNpc(reason);
    }

    private String menuKind(String title) {
        String name = StringUtils.stripControlCodes(title).toLowerCase(java.util.Locale.ROOT).trim();
        if (name.contains("choose a killstreak")) return "chooseStreak";
        if (name.contains("choose a perk")) return "choosePerk";
        if (name.contains("permanent upgrades")) return "upgrades";
        if (name.contains("prestige & renown") || name.contains("resets & renown")) return "prestige";
        if (name.contains("are you sure") || name.equals("prestige?")) return "confirm";
        if (name.contains("killstreak")) return "killstreaks";
        return "unknown";
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
        com.nezurstandalone.utils.MenuTransition.Result transition = menuTransition.observe(container,
                menuKind(title), contents, now);
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
        mc.playerController.windowClick(container.windowId, slot, 0, 0, mc.thePlayer);
        long now = System.nanoTime();
        // Classic opens a new server menu without a matching transaction acknowledgement.
        // Its first prestige step is accepted only when the expected confirmation menu appears;
        // Hypixel retains the separate authoritative transaction handling below.
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

        if (kind.equals("upgrades")) {
            handleUpgradesMenu(container, inv, now);
        } else if (kind.equals("killstreaks")) {
            handleKillstreakMenu(container, inv, now);
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
        double missingCost = contractKungFuRestoring ? 0.0 : missingPerkGoldCost(inv);
        double balance = scoreboardGold();
        if (Double.isNaN(missingCost)) {
            logInsufficientSyncGold(missingCost, balance);
            deferSyncForGold(missingCost);
            npcRetryAfterNanos = System.nanoTime() + 15_000_000_000L;
            finishNpcFlow();
            return;
        }
        if (ksNeedSync && autoKillstreak.isEnabled() && !perkGoldPreflightDone
                && !hasTemporaryPerkLease()) {
            int ksIdx = findSlotByName(inv, "Killstreaks");
            if (ksIdx >= 0) {
                perkGoldPreflightDone = true;
                preflightPerkGoldCost = missingCost;
                log("Checking equipped killstreaks before calculating total purchase cost.");
                clickMenu(container, ksIdx, "killstreaks");
                return;
            }
            ksNeedSync = false;
        }
        if (missingCost > 0.0 && (Double.isNaN(balance)
                || balance + 0.000001 < missingCost)) {
            logInsufficientSyncGold(missingCost, balance);
            deferSyncForGold(missingCost);
            npcRetryAfterNanos = System.nanoTime() + 15_000_000_000L;
            finishNpcFlow();
            return;
        }
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

    /** Perks are done: step into the Killstreaks menu if streaks still need syncing, else leave. */
    private void openKillstreaksOrFinish(ContainerChest container, IInventory inv) {
        if (ksNeedSync && autoKillstreak.isEnabled() && !hasTemporaryPerkLease()) {
            int ksIdx = findSlotByName(inv, "Killstreaks");
            if (ksIdx >= 0) {
                log("Opening killstreaks...");
                clickMenu(container, ksIdx, "killstreaks");
                lastMenuTitle = "";
                return;
            }
            ksNeedSync = false; // no killstreak entry in this menu
        }
        finishNpcFlow();
    }

    /**
     * Reconciles the two killstreak boxes. The slots are named "Perk Slot #1"/"#2" here exactly as
     * in the perk menu, and the rest of the vocabulary - "Selected: X", the level lock, the
     * availability wording - is identical, so the same readers are reused. The Megastreak entry in
     * this menu is a different system and is never touched.
     */
    private void handleKillstreakMenu(ContainerChest container, IInventory inv, long now) {
        if (!ksNeedSync || !autoKillstreak.isEnabled() || hasTemporaryPerkLease()) {
            finishNpcFlow(); return;
        }
        com.nezurstandalone.settings.PerkSetting[] boxes = ksBoxes();
        double missingCost = missingKillstreakGoldCost(inv);
        double balance = scoreboardGold();
        if (Double.isNaN(missingCost)) {
            logInsufficientSyncGold(missingCost, balance);
            deferSyncForGold(missingCost);
            npcRetryAfterNanos = System.nanoTime() + 15_000_000_000L;
            finishNpcFlow();
            return;
        }
        if (perkGoldPreflightDone && perksNeedSync && autoPerk.isEnabled()) {
            double totalMissing = preflightPerkGoldCost + missingCost;
            if (Double.isNaN(balance) || balance + 0.000001 < totalMissing) {
                logInsufficientSyncGold(totalMissing, balance);
                deferSyncForGold(totalMissing);
                npcRetryAfterNanos = System.nanoTime() + 15_000_000_000L;
                finishNpcFlow();
                return;
            }
            int back = findSlotByName(inv, "Go Back");
            if (back < 0) { failMenu("Cannot return from gold precheck to apply selected perks."); return; }
            clickMenu(container, back, "upgrades");
            return;
        }
        if (missingCost > 0.0 && (Double.isNaN(balance)
                || balance + 0.000001 < missingCost)) {
            logInsufficientSyncGold(missingCost, balance);
            deferSyncForGold(missingCost);
            npcRetryAfterNanos = System.nanoTime() + 15_000_000_000L;
            finishNpcFlow();
            return;
        }
        for (int i = 0; i < boxes.length; i++) {
            int slotIdx = findPerkSlotIndex(inv, i + 1);
            if (slotIdx < 0) continue; // slot not present on this account
            ItemStack st = inv.getStackInSlot(slotIdx);
            java.util.List<String> lore = loreOf(st);
            String equipped = selectedPerkFrom(lore);
            if (equipped == null && loreHas(lore, "Required level")) {
                ksAttempted[i] = false;
                continue; // still level-locked
            }
            String want = boxes[i].getMode();
            boolean wantNone = "No Perk".equalsIgnoreCase(want);
            boolean matches = wantNone ? equipped == null
                    : (equipped != null && equipped.equalsIgnoreCase(want));
            if (matches) { ksAttempted[i] = false; continue; }
            if (ksAttempted[i]) {
                failMenu("Killstreak selection was not confirmed in server lore.");
                return;
            }
            ksAttempted[i] = true;
            targetPerkName = want;
            log("Killstreak " + (i + 1) + ": " + (equipped == null ? "empty" : equipped) + " -> " + want);
            clickMenu(container, slotIdx, "chooseStreak");
            return;
        }
        if (System.nanoTime() - guiOpenStamp < 1_500_000_000L) return;
        log("Killstreaks verified.");
        ksNeedSync = false;
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
    private static final Pattern MENU_GOLD_COST = Pattern.compile("(?:gold\\s+)?cost\\s*:?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*g\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCOREBOARD_GOLD = Pattern.compile("Gold:\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

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
        for (String line : lore) {
            Matcher m = MENU_GOLD_COST.matcher(line);
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) { return -1.0; }
            }
        }
        Perk p = perkByName(name);
        if (p == null) p = killstreakByName(name);
        return p == null ? -1.0 : p.gold;
    }

    private double scoreboardGold() {
        for (String line : Utils.getScoreboardLines()) {
            Matcher m = SCOREBOARD_GOLD.matcher(StringUtils.stripControlCodes(line));
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) { return Double.NaN; }
            }
        }
        return Double.NaN;
    }

    private static String formatGold(double gold) {
        return String.format(java.util.Locale.ROOT, "%.2f", gold);
    }

    /** Add prices only for configured slots that are not already selected in the live menu. */
    private double missingPerkGoldCost(IInventory inv) {
        return missingSelectedGoldCost(inv, perkBoxes(), false);
    }

    private double missingKillstreakGoldCost(IInventory inv) {
        return missingSelectedGoldCost(inv, ksBoxes(), true);
    }

    private double missingSelectedGoldCost(IInventory inv,
            com.nezurstandalone.settings.PerkSetting[] boxes, boolean killstreak) {
        double total = 0.0;
        for (int i = 0; i < boxes.length; i++) {
            int slot = findPerkSlotIndex(inv, i + 1);
            if (slot < 0) continue;
            java.util.List<String> lore = loreOf(inv.getStackInSlot(slot));
            String equipped = selectedPerkFrom(lore);
            if (equipped == null && loreHas(lore, "Required level")) continue;
            String wanted = killstreak ? boxes[i].getMode() : contractDesiredPerk(i);
            if ("No Perk".equalsIgnoreCase(wanted)
                    || (equipped != null && equipped.equalsIgnoreCase(wanted))) continue;
            Perk price = killstreak ? killstreakByName(wanted) : perkByName(wanted);
            if (price == null) return Double.NaN;
            total += price.gold;
        }
        return total;
    }

    private void logInsufficientSyncGold(double required, double balance) {
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - lastPerkGoldGateLogAt < 15000L) return;
        lastPerkGoldGateLogAt = now;
        log("Skipping purchases: missing selections need "
                + (Double.isNaN(required) ? "unknown gold" : formatGold(required) + "g")
                + "; scoreboard shows "
                + (Double.isNaN(balance) ? "no gold balance" : formatGold(balance) + "g") + ".");
    }

    private void deferSyncForGold(double required) {
        if (Double.isNaN(required)) return;
        deferredSyncGoldRequirement = required;
        deferredSyncGoldSignature = syncGoldSignature();
        deferredSyncGoldUntil = com.nezurstandalone.control.Clock.millis() + 90000L;
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
        return loreHas(lore, confirm ? "Click to prestige!" : "Click to purchase!") ? slot : -1;
    }

    private void handlePrestigeGui(GuiChest gui) {
        if (classicPrestigePending) return;
        if (!menuReady(gui)) return;
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inv = container.getLowerChestInventory();
        String kind = menuKind(inv.getDisplayName().getUnformattedText());
        boolean classic = isClassicPitTitle(Utils.getScoreboardTitle());
        if (kind.equals("prestige")) {
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
                    : findAction(inv, "Confirm", "Confirm!", "Prestige", "Prestige!");
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
        if (!isToggled() || mc.thePlayer == null || engineWorld != mc.theWorld || enginePlayer != mc.thePlayer) return;
        String msg = StringUtils.stripControlCodes(event.message.getUnformattedText());
        if (event.type == 0 && autoBlockhead.isEnabled()) blockhead.chat(msg,com.nezurstandalone.control.Clock.millis());

        if (event.type == 0 && isMysticDropMessage(msg)) {
            com.nezurstandalone.control.GrinderDiagnostics.record("MYSTIC_CHAT",
                    "state="+currentState+" busy="+isBusyFlow()+" enabled="+mysticPickup.isEnabled()
                    +" message="+msg);
            long now = com.nezurstandalone.control.Clock.millis();
            if (now - lastMysticCountMs > 400L) {
                lastMysticCountMs = now;
                sessionMystics++;
                if (mysticPickup.isEnabled() && !isBusyFlow()) {
                    lastMysticChatTime = now;
                    mysticRepathAt = 0L;
                    lastMysticTraceTime = 0L;
                    sawMysticItem = false;
                    mysticTargetId = -1;
                    mysticDropX = mc.thePlayer.posX;
                    mysticDropY = mc.thePlayer.posY;
                    mysticDropZ = mc.thePlayer.posZ;
                    mysticPickupStack = null;
                    mysticMissingSince = 0L;
                    currentState = State.PICKING_UP_MYSTIC;
                    log("Mystic dropped — picking it up at all costs!");
                }
            }
        }

        Matcher xpM = XP_PATTERN.matcher(msg);
        while (xpM.find()) { try { sessionXP += Integer.parseInt(xpM.group(1).replace(",", "")); } catch (Exception ignored) {} }
        Matcher goldM = GOLD_PATTERN.matcher(msg);
        while (goldM.find()) { try { sessionGold += Double.parseDouble(goldM.group(1).replace(",", "")); } catch (Exception ignored) {} }
        Matcher renM = RENOWN_PATTERN.matcher(msg);
        while (renM.find()) { try { sessionRenown += Double.parseDouble(renM.group(1).replace(",", "")); } catch (Exception ignored) {} }

        if (event.type==0 && com.nezurstandalone.control.ServerMessages.throttle(msg)) {
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
        if (!isClassicPitTitle(Utils.getScoreboardTitle()) && event.type == 0 && currentState == State.PRESTIGE_CLICKING_GUI && prestigeConfirmStarted
                && menuTransition.awaitingResponse(System.nanoTime()) && !prestigeSwapPending && self != null
                && prestigeMessage.accept(prestigeMessageId,com.nezurstandalone.control.ClientSession.current(),currentState.name(),System.nanoTime(),
                    com.nezurstandalone.control.ServerMessages.prestige(msg,self))) {
            if (!menuTransition.completeResponse(System.nanoTime())) return;
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
                if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/l")) return;
            }
        }

        // Night quest lifecycle.
        if (nightQuestSupport.isEnabled()) {
            if (event.type==0 && com.nezurstandalone.control.ServerMessages.questStart(msg)) {
                nightQuestActive = true;
                questMessageId=questMessage.begin(com.nezurstandalone.control.ClientSession.current(),"ACTIVE_QUEST",System.nanoTime(),36L*60*1_000_000_000L);
                nightQuestStartTime = com.nezurstandalone.control.Clock.millis();
                nextNightQuestTime = nightQuestStartTime + (36L * 60L * 1000L);
                log("Night Quest started.");
            }
            if (event.type==0 && nightQuestActive && questMessage.accept(questMessageId,com.nezurstandalone.control.ClientSession.current(),"ACTIVE_QUEST",System.nanoTime(),com.nezurstandalone.control.ServerMessages.questDone(msg))) {
                nightQuestActive = false;
                sessionNightQuests++;
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
    // HUD — glass panel in the ClickGUI language: a scale/fade intro, a travelling glass sweep,
    // a state-tinted accent spine, a status dot that grows a live ring while fighting, per-row
    // cascade, spring-smoothed counters, and a lag-damped target health bar.
    // =========================================================================
    private static final float HUD_W = 188f;
    private static final int GREEN = 0xFF3FB950, AMBER = 0xFFF0A63C, PURPLE = 0xFFA855F7,
            PINK = 0xFFEC4899, CYAN = 0xFF56D4E0;

    private static final class Spring {
        float value, velocity;
        Spring(float v) { this.value = v; }
        void update(float target, float stiffness, float damping) {
            float force = stiffness * (target - value) - damping * velocity;
            velocity += force * 0.0166f;
            value += velocity * 0.0166f;
        }
        void snap(float v) { value = v; velocity = 0f; }
    }

    @Override public String getHudKey() { return "autogrinder"; }
    @Override public boolean isHudVisible() { return isToggled() && showHud.isEnabled(); }
    @Override public double getDefaultHudX() { return 6; }
    @Override public double getDefaultHudY() { return 6; }
    @Override public int getHudWidth() { return (int) HUD_W; }
    @Override public int getHudHeight() { return 168; }

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!isToggled() || !showHud.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return; // let the ClickGUI / menus own the screen

        boolean anim = hudAnimations.isEnabled();
        if (anim) hudAppear.update(1f, 150f, 16f); else hudAppear.snap(1f);
        float appear = GuiAnim.clamp01(hudAppear.value);
        float ease = GuiAnim.outCubic(appear);
        if (ease <= 0.01f) return;

        // Live values → springs.
        int mid = getMidPlayerCount();
        Entity te = combat.getTarget();
        EntityPlayer tp = (te instanceof EntityPlayer && !te.isDead && ((EntityPlayer) te).getHealth() > 0)
                ? (EntityPlayer) te : null;
        boolean fighting = tp != null
                && PathfinderManager.available(this) && (currentState == State.FIGHTING || currentState == State.SPIRE_FIGHTING);
        float hp = tp != null ? tp.getHealth() + tp.getAbsorptionAmount() : 0f;
        float maxHp = tp != null ? Math.max(1f, tp.getMaxHealth()) : 20f;
        if (anim) {
            midSpring.update(mid, 170f, 22f);
            mysticSpring.update(sessionMystics, 170f, 22f);
            activitySpring.update(fighting ? 1f : 0f, 150f, 18f);
            if (tp != null) { hpSpring.update(hp, 200f, 16f); hpLagSpring.update(hp, 55f, 12f); }
        } else {
            midSpring.snap(mid); mysticSpring.snap(sessionMystics);
            activitySpring.snap(fighting ? 1f : 0f); hpSpring.snap(hp); hpLagSpring.snap(hp);
        }

        int accent = stateColor(currentState);
        String eventName = activeEventName();
        int eventColor = activeEventColor();

        // Row set (label, value, colour, kind). kind 0 = text, 1 = mid bar, 2 = hp bar.
        java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
        rows.add(new String[]{"STATE", prettyState(currentState)});
        rows.add(new String[]{"MID", String.valueOf(Math.round(midSpring.value))});
        rows.add(new String[]{"TARGET", hasDragonEggPriority() ? "Dragon Egg" : tp != null ? tp.getName() : "scanning"});
        double hrs = Math.max(5.0 / 3600.0, (com.nezurstandalone.control.Clock.millis() - sessionStartTime) / 3600000.0);
        rows.add(new String[]{"XP/HR", fmtRate(sessionXP / hrs)});
        rows.add(new String[]{"GOLD/HR", fmtRate(sessionGold / hrs)});
        rows.add(new String[]{"RENOWN/HR", fmtRate(sessionRenown / hrs)});
        rows.add(new String[]{"MYST/HR", fmtRate(sessionMystics / hrs)});
        rows.add(new String[]{"UPTIME", uptime()});
        rows.add(new String[]{"EVENT", eventName});

        float pad = 8f, headerH = 22f, rowH = 13f;
        float bodyH = rows.size() * rowH;
        boolean breakBar = breakTimeEnable.isEnabled();
        float h = headerH + pad + bodyH + (breakBar ? 12f : 0f) + pad - 2f;

        int x = getRenderX();
        int y = getRenderY();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        float sc = 0.95f + 0.05f * ease;                 // gentle scale-in
        GlStateManager.translate(0f, (1f - ease) * -6f, 0f); // slide down as it settles
        GlStateManager.scale(sc, sc, 1f);
        GuiDraw.resetState();

        // Panel: shadow, glass, travelling sweep.
        GuiDraw.shadow(0, 0, HUD_W, h, GuiTheme.PANEL_RADIUS, 5, (int) (120 * ease));
        GuiDraw.glass(0, 0, HUD_W, h, GuiTheme.PANEL_RADIUS, GuiTheme.GLASS_BODY, GuiTheme.GLASS_BORDER, 1f, ease);
        GuiDraw.glassSweep(0, 0, HUD_W, h, GuiTheme.PANEL_RADIUS, GuiAnim.wrap(7f) / 7f,
                GuiTheme.GLASS_SWEEP, ease * 0.9f);

        // Accent spine, breathing with the state colour.
        float spinePulse = 0.75f + 0.25f * GuiAnim.pulse(1.6f);
        GuiDraw.roundedRect(1.5f, 4f, 3f, h - 4f, 0.75f, GuiDraw.withAlpha(accent, ease * spinePulse));

        // Header: glowing status dot with a combat ring, title, state pill.
        float dotX = 12f, dotY = headerH / 2f;
        float act = GuiAnim.clamp01(activitySpring.value);
        float dotPulse = 0.7f + 0.3f * GuiAnim.pulse(1.2f);
        GuiDraw.glow(dotX, dotY, 6.5f * dotPulse, GuiDraw.withAlpha(accent, 0.42f * ease), 4);
        if (act > 0.02f) {
            float ringR = 4.5f + 3.5f * GuiAnim.pulse(0.9f);
            GuiDraw.ring(dotX, dotY, ringR, 1.1f, GuiDraw.withAlpha(accent, 0.5f * act * ease));
        }
        GuiDraw.circle(dotX, dotY, 2.6f, GuiDraw.withAlpha(accent, ease));

        GuiDraw.textScaled("AutoGrinder", 18f, (headerH - 8f) / 2f + 0.5f, 0.95f,
                GuiDraw.withAlpha(GuiTheme.TEXT, ease), false);

        String badge = prettyState(currentState).toUpperCase();
        float bw = GuiDraw.textWidthScaled(badge, 0.62f) + 9f;
        float bx = HUD_W - bw - 7f, by = (headerH - 10f) / 2f;
        GuiDraw.roundedRect(bx, by, bx + bw, by + 10f, 3f,
                GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.CHIP_BG, accent, 0.22f), ease),
                GuiDraw.withAlpha(GuiDraw.alpha(accent, 0xB0), ease));
        GuiDraw.textScaled(badge, bx + 4.5f, by + 2f, 0.62f, GuiDraw.withAlpha(accent, ease), false);

        GuiDraw.rect(5f, headerH, HUD_W - 5f, headerH + 0.75f, GuiDraw.withAlpha(GuiTheme.SEPARATOR, ease));

        // Body rows, each cascading in.
        float ry = headerH + pad - 2f;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            String[] row = rows.get(i);
            float rowT = GuiAnim.outCubic(GuiAnim.stagger(ease, i, n, 0.55f));
            float rowA = ease * rowT;
            float slide = (1f - rowT) * 8f;

            GuiDraw.textScaled(row[0], pad + slide, ry, 0.72f, GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, rowA), false);

            boolean isTargetRow = row[0].equals("TARGET") && tp != null;
            boolean isMidRow = row[0].equals("MID");
            int valColor = row[0].equals("STATE") ? accent
                    : row[0].equals("EVENT") ? eventColor
                    : GuiTheme.TEXT;

            if (isTargetRow) {
                // Name + a lag-damped HP bar under the value.
                float vw = GuiDraw.textWidthScaled(row[1], 0.8f);
                float vx = HUD_W - pad - vw;
                GuiDraw.textFitScaled(row[1], vx, ry, 0.8f, vw, GuiDraw.withAlpha(GuiTheme.TEXT, rowA), false);
                float barY = ry + 8.5f, bx1 = pad + slide, bx2 = HUD_W - pad;
                GuiDraw.roundedRect(bx1, barY, bx2, barY + 2.4f, 1.2f, GuiDraw.withAlpha(0x33FFFFFF, rowA));
                float lagPct = GuiAnim.clamp01(hpLagSpring.value / maxHp);
                float hpPct = GuiAnim.clamp01(hpSpring.value / maxHp);
                int hpCol = GuiDraw.lerpColor(GuiTheme.DANGER, GREEN, hpPct);
                if (lagPct > hpPct) {
                    GuiDraw.roundedRect(bx1, barY, bx1 + (bx2 - bx1) * lagPct, barY + 2.4f, 1.2f,
                            GuiDraw.withAlpha(0x66FFFFFF, rowA));
                }
                if (hpPct > 0f) {
                    GuiDraw.roundedRect(bx1, barY, bx1 + (bx2 - bx1) * hpPct, barY + 2.4f, 1.2f,
                            GuiDraw.withAlpha(hpCol, rowA));
                }
            } else {
                float vw = GuiDraw.textWidthScaled(row[1], 0.8f);
                float vx = HUD_W - pad - vw;
                if (isMidRow) {
                    // Small fill bar showing mid population against the swap threshold.
                    float frac = GuiAnim.clamp01(midSpring.value / (float) Math.max(1, minMidPlayers.value * 2));
                    float mbW = 26f, mbX = vx - mbW - 5f, mbY = ry + 2.5f;
                    GuiDraw.roundedRect(mbX, mbY, mbX + mbW, mbY + 2.4f, 1.2f, GuiDraw.withAlpha(0x33FFFFFF, rowA));
                    int mc2 = mid < minMidPlayers.value ? AMBER : GREEN;
                    if (frac > 0f) GuiDraw.roundedRect(mbX, mbY, mbX + mbW * frac, mbY + 2.4f, 1.2f, GuiDraw.withAlpha(mc2, rowA));
                    valColor = mc2;
                }
                GuiDraw.textFitScaled(row[1], vx, ry, 0.8f, HUD_W - pad - vx, GuiDraw.withAlpha(valColor, rowA), false);
            }
            ry += rowH;
        }

        // Break shift meter.
        if (breakBar) {
            long now = com.nezurstandalone.control.Clock.millis();
            long total = isOnBreak ? (long) (breakDurationLimit.value * 60000L)
                    : (long) (workDurationLimit.value * 3600000L);
            float frac = total <= 0 ? 0f : GuiAnim.clamp01(1f - (now - lastBreakEndTime) / (float) total);
            int col = isOnBreak ? AMBER : GREEN;
            GuiDraw.textScaled(isOnBreak ? "BREAK" : "SHIFT", pad, ry, 0.72f, GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, ease), false);
            float bX1 = pad, bX2 = HUD_W - pad, bY = ry + 8.5f;
            GuiDraw.roundedRect(bX1, bY, bX2, bY + 2.4f, 1.2f, GuiDraw.withAlpha(0x33FFFFFF, ease));
            if (frac > 0f) GuiDraw.roundedRect(bX1, bY, bX1 + (bX2 - bX1) * frac, bY + 2.4f, 1.2f, GuiDraw.withAlpha(col, ease));
        }

        GuiDraw.resetState();
        GlStateManager.popMatrix();
    }

    private int stateColor(State s) {
        switch (s) {
            case DRAGON_EGG: return PURPLE;
            case FIGHTING: case SPIRE_FIGHTING: return GREEN;
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
}
