package com.nezurstandalone.module.impl.player;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.contract.ContractOffer;
import com.nezurstandalone.contract.ContractScoreboard;
import com.nezurstandalone.contract.ContractSelector;
import com.nezurstandalone.contract.StableOfferGate;
import com.nezurstandalone.gui.DebugHud;
import com.nezurstandalone.gui.GuiTheme;
import com.nezurstandalone.input.GuardedInput;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.pathfinder.PathfinderManager;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.PerkSetting;
import com.nezurstandalone.utils.RotationManager;
import com.nezurstandalone.utils.RotationUtils;
import com.nezurstandalone.utils.Utils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Scoreboard-driven Contractor controller. Existing AutoGrinder remains the combat owner. */
public final class AutoContractor extends Module implements com.nezurstandalone.module.DraggableHud {
    public enum State { GAPPLE_INSPECTING_OFFERS, GAPPLE_PREPARING, GAPPLE_ENTERING_MID,
        GAPPLE_NATURAL_DEATHS, GAPPLE_WAIT_APPLE, GAPPLE_MOVING_PAD, GAPPLE_EATING, GAPPLE_VERIFY_CONSUMPTION,
        GAPPLE_ACTIVATING_PAD, GAPPLE_WAIT_AIRBORNE, GAPPLE_WAIT_OOF_RESPAWN, GAPPLE_WAIT_NEXT_APPLE, GAPPLE_COOLDOWN, GAPPLE_WAIT_CYCLE_COOLDOWN, WAITING_FOR_MAJOR_EVENT, CHECKING_SCOREBOARD, FINDING_CONTRACTOR, MOVING_TO_CONTRACTOR,
        OPENING_CONTRACTOR, READING_MENU, WAITING_FOR_STABLE_OFFERS, VERIFYING_ACCEPTANCE,
        PREPARING_NO_PERK_OFFER, PREPARING_CONTRACT, FINDING_SHOP, MOVING_TO_SHOP, OPENING_SHOP, BUYING_SHOP,
        EXECUTING_CONTRACT, FALLBACK_REFRESH_MODE, CONTRACT_COMPLETE, RESTORING_PERKS,
        NO_VILE_OFFERS, LOBBY_SWITCHING, LIMBO, LOBBY_RECONNECT, JOINING_PIT, DISCONNECTED, RECOVERING }

    private final BooleanSetting goldenApples=new BooleanSetting("Golden Apples",true);
    private final NumberSetting gappleWaitDistance=new NumberSetting("Gapple Pad Wait Distance",8,7,12,1),
            gappleOofCooldown=new NumberSetting("Gapple Oof Cooldown ms",12000,12000,60000,0),
            gappleMaxDeaths=new NumberSetting("Gapple Natural Death Limit",8,4,20,0);
    private boolean gappleActive,gappleDeathSeen,gapplePendingOof,gapplePreAccept;
    private int gappleEatingSlot=-1;
    private Entity gappleFollow,gappleSkipped;
    private long gappleDamageAt,gappleSkippedUntil,gappleJumpAt;
    private int gappleLastHurt;
    private long gappleInventoryActionAt,gappleUseReadyAt;
    private com.nezurstandalone.module.impl.player.AutoHeal gappleHeal;private boolean gappleHealWasEnabled;
    private int gappleEatProgress;
    private State gappleDeathResume;
    private net.minecraft.client.gui.inventory.GuiInventory gappleInventoryScreen;
    private long gappleDeathAt,gappleUnreadableSince,gappleCooldownLogAt;
    private long gappleStepAt,gappleRespawnAt,gappleLaunchAt,gappleLastOof,gappleOofSentAt;
    private int gappleDeaths,gappleRetries,gappleEatCount,gappleLastProgress=-1;
    private net.minecraft.util.BlockPos gapplePad,gappleWait;
    private final BooleanSetting autoNext=new BooleanSetting("Auto Next Contract",true);
    private final BooleanSetting autoDailyQuests=new BooleanSetting("Auto Start Daily Quests",false);
    private final BooleanSetting autoWeeklyQuests=new BooleanSetting("Auto Start Weekly Quests",false);
    private final BooleanSetting debug=new BooleanSetting("Debug Log",true);
    private final NumberSetting retries=new NumberSetting("Max Retries",3,1,6,0);
    private final NumberSetting waitRadius=new NumberSetting("Contractor Wait Radius",4,2,8,1);
    private final NumberSetting pollMs=new NumberSetting("Scoreboard Poll (ms)",250,100,1000,0);
    private final NumberSetting menuDelay=new NumberSetting("Offer Stable (ms)",3000,3000,6000,0);
    private final NumberSetting clickDelay=new NumberSetting("GUI Click (ms)",180,100,350,0);
    private final BooleanSetting killPlayers=new BooleanSetting("Kill Players",true);
    private final BooleanSetting chainKills=new BooleanSetting("Chain Kills",true);
    private final BooleanSetting killStreak=new BooleanSetting("Kill Streak",false);
    private final BooleanSetting sneakKills=new BooleanSetting("Sneak Kills",true);
    private final BooleanSetting noArmorKills=new BooleanSetting("No Armor Kills",true);
    private final BooleanSetting noPerkKills=new BooleanSetting("No Perk Kills",true);
    private final BooleanSetting bountyKills=new BooleanSetting("Bounty Claims",true);
    private final BooleanSetting goldIngots=new BooleanSetting("Gold Ingots",true);
    private final BooleanSetting fistMid=new BooleanSetting("Fist Mid Kills (requires Kung Fu)",true);
    private final BooleanSetting diamondSword=new BooleanSetting("Diamond Sword Kills",true);
    // PerkSetting supplies the existing expandable list UI while persisting like a ModeSetting.
    private final PerkSetting equipmentMode=new PerkSetting("Equipment", "None", "None", "Iron Pack", "Diamond Chestplate + Boots", "Full Diamond");
    private final BooleanSetting showInfoHud=new BooleanSetting("Show Info HUD",true);
    private final BooleanSetting rebuyAfterDeath=new BooleanSetting("Rebuy After Death",true);
    private final BooleanSetting buyDiamondSword=new BooleanSetting("Buy Contract Diamond Sword",true);
    private final NumberSetting sneakRadius=new NumberSetting("Sneak Safety Radius",3.5,1,3.5,1);
    private final NumberSetting sneakDelay=new NumberSetting("Sneak Before Hit (ms)",100,0,500,0);
    private final NumberSetting goldScanRadius=new NumberSetting("Gold Scan Radius",com.nezurstandalone.contract.GoldPickupRecovery.DEFAULT_SCAN_RADIUS,8,com.nezurstandalone.contract.GoldPickupRecovery.MAX_SCAN_RADIUS,0);
    private final NumberSetting goldRescan=new NumberSetting("Gold Rescan (ms)",750,250,3000,0);
    private final NumberSetting minimumGold=new NumberSetting("Minimum Visible Gold",2,1,20,0);
    private final NumberSetting lowGoldTimeout=new NumberSetting("Low Gold Timeout (s)",45,10,180,0);
    private final BooleanSetting goldLobbySwap=new BooleanSetting("Low Gold Lobby Swap",false);
    private final BooleanSetting autoReconnect=new BooleanSetting("Auto Reconnect",true);
    private final BooleanSetting lobbySwap=new BooleanSetting("Lobby Swap",true);
    private final NumberSetting minMidPlayers=new NumberSetting("Min Mid Players",5,1,20,0);
    private final NumberSetting lobbySwapDelay=new NumberSetting("Swap Delay (s)",5,1,30,0);
    private final NumberSetting goldenApplePriority=new NumberSetting("Golden Apple Priority",1,1,20,0);
    private final NumberSetting killPriority=new NumberSetting("Kill Priority",1,1,20,0);
    private final NumberSetting fistPriority=new NumberSetting("Fist Priority",2,1,20,0);
    private final NumberSetting sneakPriority=new NumberSetting("Sneak Priority",3,1,20,0);
    private final NumberSetting noArmorPriority=new NumberSetting("No Armor Priority",4,1,20,0);
    private final NumberSetting noPerkPriority=new NumberSetting("No Perk Priority",9,1,20,0);
    private final NumberSetting diamondPriority=new NumberSetting("Diamond Sword Priority",5,1,20,0);
    private final NumberSetting chainPriority=new NumberSetting("Chain Priority",6,1,20,0);
    private final NumberSetting bountyPriority=new NumberSetting("Bounty Priority",7,1,20,0);
    private final NumberSetting goldPriority=new NumberSetting("Gold Priority",8,1,20,0);
    private final NumberSetting streakPriority=new NumberSetting("Streak Priority",9,1,20,0);

    private final Random random=new Random();
    private final StableOfferGate offerGate=new StableOfferGate();
    private final Object interactionOwner=new Object();
    private final String rotationOwner="contractor.npc";
    private final DebugHud infoHud=new DebugHud();
    private State state=State.CHECKING_SCOREBOARD;
    private long stateSince, nextAction, nextPoll, activeSince;
    private int attempts, missingSamples;
    private Object world;
    private Entity npc;
    private Entity lastInteractedNpc;
    private final java.util.Set<Integer> rejectedContractorNpcs=new java.util.HashSet<Integer>();
    private final java.util.Set<Integer> rejectedShopNpcs=new java.util.HashSet<Integer>();
    private ContainerChest observedContainer;
    private String observedMenu="", observedContents="";
    private boolean pendingClick;
    private boolean pendingQuestClick;
    private final java.util.Set<String> questsIssuedThisVisit=new java.util.HashSet<String>();
    private ContainerChest pendingContainer;
    private String pendingContents;
    private long pendingSince;
    private ContractOffer selected;
    private final java.util.Set<String> unknownDumpedMenus=new java.util.HashSet<String>();
    private boolean selectedFallback, grinderWasEnabled, grinderTouched;
    private ContractScoreboard.Snapshot scoreboard;
    private boolean acceptedByThisModule;
    private boolean contractStartedChat;
    private final com.nezurstandalone.contract.ContractTracker contractTracker=new com.nezurstandalone.contract.ContractTracker();
    private boolean ironPackPurchased;
    private long noOfferRetryAt;
    private long lastScoreboardDiagnostic;
    private String purchaseName;
    private String lastEquipmentMode="None";
    private boolean equipmentPurchasePending;
    private long equipmentRetryAfter;
    private int purchaseBefore;
    private long purchaseAt;
    private long lowGoldSince, lobbySwapSince;
    private int lobbySwapAttempts;
    private boolean lobbySawNonPit;
    private final com.nezurstandalone.contract.ContractConnectionFlow connection=new com.nezurstandalone.contract.ContractConnectionFlow();
    private String lastHypixelAddress;
    private Object disconnectedScreen;
    private int reconnectAttempts;
    private long thinLobbySince, nextMidScan;
    private int networkMidCount;

    public AutoContractor() {
        super("AutoContractor","Chooses Vile-paying Hypixel Pit contracts and tracks them from the scoreboard.",Category.AUTO);
        markDangerous();
        equipmentMode.cycleOnLeftClick = true;
        addSettings(autoNext,autoDailyQuests,autoWeeklyQuests,debug,showInfoHud,retries,waitRadius,pollMs,menuDelay,clickDelay,
                goldenApples,killPlayers,chainKills,killStreak,sneakKills,noArmorKills,noPerkKills,bountyKills,goldIngots,
                fistMid,diamondSword,equipmentMode,rebuyAfterDeath,buyDiamondSword,
                gappleWaitDistance,gappleOofCooldown,gappleMaxDeaths,sneakRadius,sneakDelay,goldScanRadius,goldRescan,minimumGold,lowGoldTimeout,goldLobbySwap,
                autoReconnect,lobbySwap,minMidPlayers,lobbySwapDelay,
                goldenApplePriority,killPriority,fistPriority,sneakPriority,noArmorPriority,noPerkPriority,diamondPriority,
                chainPriority,bountyPriority,goldPriority,streakPriority);
    }

    @SubscribeEvent public void renderInfoHud(RenderGameOverlayEvent.Post event) {
        if(event.type!=RenderGameOverlayEvent.ElementType.TEXT || !isToggled() || !showInfoHud.isEnabled()
                || mc.thePlayer==null || mc.theWorld==null || mc.currentScreen!=null)return;
        final int green=0xFF3FB950, amber=0xFFF0A63C, muted=0xFF83838F, text=0xFFECECF2;
        boolean running=state==State.EXECUTING_CONTRACT;
        boolean failure=state==State.RECOVERING;
        boolean waiting=state==State.WAITING_FOR_MAJOR_EVENT || state==State.FALLBACK_REFRESH_MODE || state==State.NO_VILE_OFFERS;
        int accent=failure?GuiTheme.DANGER:waiting?amber:running?green:GuiTheme.ACCENT;
        infoHud.begin("Auto Contractor",accent);
        infoHud.badge(failure?"RECOVERY":waiting?"WAITING":running?"ACTIVE":"SEARCHING",accent);
        infoHud.row("State",state.name().replace('_',' '),accent);
        if(state==State.WAITING_FOR_MAJOR_EVENT)infoHud.row("Event",waitingMajorEvent,amber);
        String title=Utils.getScoreboardTitle();
        infoHud.row("Server",scoreboard==null?"scanning":!scoreboard.validPit?"not Hypixel Pit":
                !scoreboard.readable?"unreadable":"Hypixel Pit",scoreboard!=null && scoreboard.validPit?green:amber);
        String objective=scoreboard!=null && scoreboard.active?scoreboard.objective:
                selected!=null?selected.task:"none selected";
        infoHud.row("Objective",objective,text);
        infoHud.row("Type",scoreboard!=null && scoreboard.active?scoreboard.type.name():
                selected!=null?selected.type.name():"none",muted);
        if(scoreboard!=null && scoreboard.active){
            if(scoreboard.target>0)infoHud.meter("Progress",scoreboard.current/(float)scoreboard.target,
                    scoreboard.current+" / "+scoreboard.target,green);
            else infoHud.row("Progress","awaiting server progress",muted);
            infoHud.row("Time left",scoreboard.remainingSeconds<0?"--:--":
                    DebugHud.mmss(scoreboard.remainingSeconds*1000L),
                    scoreboard.remainingSeconds>=0 && scoreboard.remainingSeconds<30?GuiTheme.DANGER:text);
        } else {
            infoHud.row("Progress","waiting for contract",muted);
            infoHud.row("Time left",state==State.NO_VILE_OFFERS?
                    DebugHud.mmss(Math.max(0,noOfferRetryAt-com.nezurstandalone.control.Clock.millis())):"--:--",muted);
        }
        if(scoreboard!=null && scoreboard.active)
            infoHud.row("Timer source",contractTracker.timerPaused()?"scoreboard (paused)":"scoreboard",
                    contractTracker.timerPaused()?amber:muted);
        infoHud.row("Vile reward",selected==null?"unknown":selected.vileAmount+" chunks",text);
        double gold=Utils.getGoldDouble();
        infoHud.row("Gold",!hasGoldBalanceLine() || Double.isNaN(gold)?"unknown":
                String.format(java.util.Locale.ROOT,"%.0fg",gold),text);
        infoHud.separator();
        infoHud.row("Equipment",equipmentMode.getMode(),text);
        String shopItem=neededShopItem();
        infoHud.row("Shop need",shopItem==null?"none":shopItem,shopItem==null?green:amber);
        infoHud.row("Purchase",purchaseName==null?"idle":"verifying "+purchaseName,purchaseName==null?muted:amber);
        AutoGrinder g=grinder();
        infoHud.row("Grinder",g==null?"unavailable":g.isToggled()?"running":"paused",
                g!=null && g.isToggled()?green:muted);
        infoHud.row("Perk lease",g==null?"unavailable":g.isRestoringTemporaryPerk()?"restoring":
                g.isTemporaryNoPerkReady()?"NO_PERK clear":g.isTemporaryKungFuReady()?"Kung Fu ready":
                g.hasTemporaryPerkLease()?"checking":"none",g!=null && g.hasTemporaryPerkLease()?amber:muted);
        infoHud.separator();
        infoHud.row("NPC",npc==null?"scanning":String.format(java.util.Locale.ROOT,"%.1f m",
                mc.thePlayer.getDistanceToEntity(npc)),npc==null?muted:green);
        infoHud.row("Menu",observedMenu.isEmpty()?"closed":observedMenu,text);
        infoHud.row("NPC rejected",String.valueOf(rejectedContractorNpcs.size()),
                rejectedContractorNpcs.isEmpty()?muted:amber);
        infoHud.row("Retries",attempts+" / "+(int)retries.value,attempts>0?amber:muted);
        infoHud.row("Next action",nextAction<=0?"now":nextAction==Long.MAX_VALUE?"stopped":
                Math.max(0,(nextAction-com.nezurstandalone.control.Clock.millis())/1000)+"s",muted);
        infoHud.render(getRenderX()+infoHud.getWidth(),getRenderY());
    }

    @Override public String getHudKey(){return "autocontractor";}
    @Override public boolean isHudVisible(){return isToggled() && showInfoHud.isEnabled();}
    @Override public double getDefaultHudX(){return Math.max(0,com.nezurstandalone.utils.ScreenScale.get().getScaledWidth()-174);}
    @Override public double getDefaultHudY(){return 6;}
    @Override public int getHudWidth(){return (int)Math.ceil(infoHud.getWidth());}
    @Override public int getHudHeight(){return Math.max(270,(int)Math.ceil(infoHud.getHeight()));}

    @Override protected void onEnable() {
        super.onEnable();
        AutoGrinder grinder=grinder();
        grinderWasEnabled=grinder!=null && grinder.isToggled();
        grinderTouched=false; world=mc.theWorld;selected=null;acceptedByThisModule=false;
        rejectedContractorNpcs.clear();rejectedShopNpcs.clear();lastInteractedNpc=null;
        infoHud.resetEntrance();
        lastEquipmentMode=equipmentMode.getMode();equipmentPurchasePending=false;equipmentRetryAfter=0;
        attempts=0;pendingClick=false;pendingQuestClick=false;questsIssuedThisVisit.clear();purchaseName=null;
        contractStartedChat=false;
        contractTracker.clear();inspectingActiveIdentity=false;noOfferRetryAt=0;ironPackPurchased=false;
        connection.reset();disconnectedScreen=null;reconnectAttempts=0;thinLobbySince=0;
        if(mc.getCurrentServerData()!=null && com.nezurstandalone.contract.ContractConnectionFlow.hypixelHost(mc.getCurrentServerData().serverIP))
            lastHypixelAddress=mc.getCurrentServerData().serverIP;
        unknownDumpedMenus.clear();
        move(State.CHECKING_SCOREBOARD);
    }
    @SubscribeEvent public void onContractChat(ClientChatReceivedEvent event) {
        if(!isToggled() || event.type==2)return;
        String message=com.nezurstandalone.utils.ChatEvents.plain(event).trim();
        if(gappleActive && message.startsWith("DEATH!"))noteGappleDeath(com.nezurstandalone.control.Clock.millis());
        if(message.equalsIgnoreCase("You already did all of the contracts you could do today!")) {
            stopForDailyLimit();return;
        }
        if(ContractOffer.completedMessage(message)) {
            cancelGapple();
            stopGrinder();closeOwnedMenu();cleanupNavigation();
            com.nezurstandalone.contract.ContractCombatPolicy.clear();
            restorePerks();
            log("Contract completed confirmed by server chat.");
            move(State.CONTRACT_COMPLETE);return;
        }
        if(state!=State.VERIFYING_ACCEPTANCE)return;
        if(message.equalsIgnoreCase("CONTRACT STARTED!")) {
            contractStartedChat=true;
            if(selected!=null) {
                scoreboard=new ContractScoreboard.Snapshot(true,true,true,selected.type,0,selected.required,-1,selected.task);
                contractTracker.confirm(scoreboard,com.nezurstandalone.control.Clock.millis());
                acceptedByThisModule=true;pendingClick=false;attempts=0;
                closeOwnedMenu();cleanupNavigation();
                move(!selectedFallback && isExecutable(selected.type)?State.PREPARING_CONTRACT:State.FALLBACK_REFRESH_MODE);
            }
        }
    }
    @Override protected void onDisable() {
        cleanup();
        super.onDisable();
    }

    private String waitingMajorEvent="";
    private long majorEventClearSince;
    private long sidebarSettleUntil;
    private boolean waitForMajorEvent(long now) {
        if(gappleActive){
            String event=ContractScoreboard.majorEvent(Utils.getScoreboardLinesFresh());
            if(!event.isEmpty()){cleanupNavigation();com.nezurstandalone.control.InventoryOwner.release(this,true);gappleStepAt=now;return true;}
            return false;
        }
        boolean acquiring=state==State.CHECKING_SCOREBOARD || state==State.FINDING_CONTRACTOR
                || state==State.MOVING_TO_CONTRACTOR || state==State.OPENING_CONTRACTOR
                || state==State.READING_MENU || state==State.WAITING_FOR_STABLE_OFFERS
                || state==State.NO_VILE_OFFERS || state==State.RECOVERING
                || state==State.WAITING_FOR_MAJOR_EVENT;
        if(state==State.RESTORING_PERKS)return false;
        boolean active=scoreboard!=null && scoreboard.active;
        if(!acquiring && !active)return false;
        List<String> lines=Utils.getScoreboardLinesFresh();
        ContractScoreboard.Snapshot eventBoard=ContractScoreboard.parse(Utils.getScoreboardTitle(),lines);
        boolean pit=eventBoard.validPit;
        if(pit && eventBoard.readable) {
            scoreboard=contractTracker.observe(eventBoard,now,ContractScoreboard.normalSidebar(lines));
            if(!eventBoard.active && scoreboard!=null && scoreboard.active && now-lastScoreboardDiagnostic>10000) {
                lastScoreboardDiagnostic=now;
                System.out.println("[AutoContractor] Sidebar mismatch; keeping confirmed contract. title="+Utils.getScoreboardTitle()+" raw="+lines);
            }
        }
        String event=pit?ContractScoreboard.majorEvent(lines):"";
        if(!event.isEmpty()) {
            if(state!=State.WAITING_FOR_MAJOR_EVENT)log("Waiting for major event to finish: "+event);
            waitingMajorEvent=event;majorEventClearSince=0;
            closeOwnedMenu();cleanupNavigation();stopGrinder();offerGate.reset();pendingClick=false;
            move(State.WAITING_FOR_MAJOR_EVENT);return true;
        }
        if(state!=State.WAITING_FOR_MAJOR_EVENT)return false;
        // A missing sidebar or a transfer is not proof that the event ended.
        if(!pit || !ContractScoreboard.normalSidebar(lines)){majorEventClearSince=0;return pit;}
        if(majorEventClearSince==0)majorEventClearSince=now;
        if(now-majorEventClearSince<3000)return true;
        waitingMajorEvent="";majorEventClearSince=0;attempts=0;nextPoll=0;
        log("Major event ended; resuming contract checks.");move(State.CHECKING_SCOREBOARD);
        return false;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START || !isToggled())return;
        com.nezurstandalone.control.MovementKeys.release("contractor-approach");
        com.nezurstandalone.control.MovementKeys.release("contractor-gapple");
        long now=com.nezurstandalone.control.Clock.millis();
        if(handleConnection(now))return;
        if(mc.theWorld!=null && mc.thePlayer!=null && waitForMajorEvent(now))return;
        if(mc.theWorld==null || mc.thePlayer==null) {
            boolean swapping=state==State.LOBBY_SWITCHING;
            if(swapping)lobbySawNonPit=true;
            closeOwnedMenu();cleanupNavigation();stopGrinder();com.nezurstandalone.contract.ContractCombatPolicy.clear();
            scoreboard=null;nextPoll=0;world=null;
            if(!swapping && !gappleActive)move(State.CHECKING_SCOREBOARD);
            return;
        }
        if(world!=mc.theWorld) {
            boolean swapping=state==State.LOBBY_SWITCHING;
            closeOwnedMenu();cleanupNavigation();stopGrinder();com.nezurstandalone.contract.ContractCombatPolicy.clear();
            if(grinder()!=null)grinder().invalidateTemporaryPerkVerification();
            sidebarSettleUntil=now+3000;
            world=mc.theWorld;scoreboard=null;nextPoll=0;offerGate.reset();observedContainer=null;
            rejectedContractorNpcs.clear();rejectedShopNpcs.clear();lastInteractedNpc=null;
            selected=null;pendingClick=false;purchaseName=null;attempts=0;
            if(!swapping && !gappleActive)move(State.CHECKING_SCOREBOARD);
        }
        boolean scoreboardPolled=now>=nextPoll;
        if(scoreboardPolled) {
            List<String> liveLines=Utils.getScoreboardLinesFresh();
            ContractScoreboard.Snapshot live=ContractScoreboard.parse(Utils.getScoreboardTitle(),liveLines);
            ContractScoreboard.Snapshot controllerLive=completedSidebarSuppressed
                    ?new ContractScoreboard.Snapshot(live.validPit,live.readable,false,ContractOffer.Type.UNKNOWN,0,0,-1,"") : live;
            scoreboard=contractTracker.observe(controllerLive,now,ContractScoreboard.normalSidebar(liveLines));
            com.nezurstandalone.contract.ContractScoreboardDump.capture(mc,liveLines,live,scoreboard,now);
            if(!live.active && scoreboard!=null && scoreboard.active && now-lastScoreboardDiagnostic>10000) {
                lastScoreboardDiagnostic=now;
                System.out.println("[AutoContractor] Using confirmed contract; raw sidebar="+liveLines);
            }
            nextPoll=now+(long)pollMs.value;
        }
        if(!equipmentMode.getMode().equals(lastEquipmentMode)){
            lastEquipmentMode=equipmentMode.getMode();equipmentRetryAfter=0;
            log("Equipment selected: "+lastEquipmentMode);
        }
        if(gappleActive){tickGapple(now,scoreboardPolled);return;}
        if(scoreboard==null || !scoreboard.validPit || !scoreboard.readable) {
            stopGrinder();cleanupNavigation();move(State.CHECKING_SCOREBOARD);return;
        }
        if(mc.thePlayer.isDead || mc.thePlayer.getHealth()<=0) {
            ironPackPurchased=false;
            cleanupNavigation();stopGrinder();com.nezurstandalone.contract.ContractCombatPolicy.clear();
            if(grinder()!=null)grinder().invalidateTemporaryPerkVerification();
            purchaseName=null;selected=null;move(State.CHECKING_SCOREBOARD);return;
        }
        if(!scoreboard.active && now<sidebarSettleUntil) {
            cleanupNavigation();move(State.CHECKING_SCOREBOARD);return;
        }
        if(stuckRespawnUntil>0){
            if(Utils.isInSpawn() || now>=stuckRespawnUntil){stuckRespawnUntil=0;move(State.PREPARING_CONTRACT);}
            else {stopGrinder();cleanupNavigation();return;}
        }
        if(recoverBlockedMovement(now))return;
        // Selecting equipment is an actionable request, not merely a display preference.
        // Buy it while idle before looking for another contract, without overriding NO_ARMOR.
        if(!scoreboard.active && !equipmentPurchasePending && now>=equipmentRetryAfter
                && !equipmentMode.getMode().equals("None") && needsShop()
                && (state==State.CHECKING_SCOREBOARD || state==State.FINDING_CONTRACTOR)){
            equipmentPurchasePending=true;closeOwnedMenu();cleanupNavigation();
            log("Buying selected equipment: "+lastEquipmentMode);
            move(State.FINDING_SHOP);
        }
        if(!scoreboard.active)inspectingActiveIdentity=false;
        if(contractTracker.needsIdentityInspection(scoreboard) && selected==null){
            if(!inspectingActiveIdentity){
                inspectingActiveIdentity=true;selectedFallback=false;attempts=0;
                closeOwnedMenu();cleanupNavigation();
                logImportant("Existing Kills objective is ambiguous; checking active task at Contractor before combat.");
                move(State.FINDING_CONTRACTOR);
            }
        }
        if(inspectingActiveIdentity){
            stopGrinder();com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.COLLECT_GOLD_INGOTS,0,3.5);
            if(state!=State.FINDING_CONTRACTOR && state!=State.MOVING_TO_CONTRACTOR
                    && state!=State.OPENING_CONTRACTOR && state!=State.READING_MENU && state!=State.RECOVERING)
                move(State.FINDING_CONTRACTOR);
        }
        if(scoreboard.active) {
            missingSamples=0;
            if(!inspectingActiveIdentity && (state==State.CHECKING_SCOREBOARD || state==State.FINDING_CONTRACTOR
                    || state==State.MOVING_TO_CONTRACTOR || state==State.OPENING_CONTRACTOR
                    || state==State.RECOVERING || state==State.READING_MENU
                    || state==State.NO_VILE_OFFERS || state==State.WAITING_FOR_STABLE_OFFERS
                    || state==State.WAITING_FOR_MAJOR_EVENT
                    || (!isExecutable(scoreboard.type) && (state==State.FINDING_SHOP
                    || state==State.MOVING_TO_SHOP || state==State.OPENING_SHOP || state==State.BUYING_SHOP)))) {
                // A contract already existed when enabled. Resume only objectives supported by a handler.
                if(scoreboard.type==ContractOffer.Type.GOLDEN_APPLES && goldenApples.enabled && grinder()!=null
                        && !grinder().hasTemporaryPerkLease()){
                    selectedFallback=false;beginGapple(now);return;
                }
                selectedFallback=selectedFallback || !isExecutable(scoreboard.type);
                purchaseName=null;equipmentPurchasePending=false;
                closeOwnedMenu();cleanupNavigation();stopGrinder();
                log("Resuming active "+scoreboard.type+" from scoreboard.");
                move(selectedFallback?State.FALLBACK_REFRESH_MODE:State.PREPARING_CONTRACT);
            } else if(state==State.VERIFYING_ACCEPTANCE) {
                if(selected==null || scoreboard.type==selected.type || scoreboard.type==ContractOffer.Type.UNKNOWN) {
                    acceptedByThisModule=true;activeSince=now;attempts=0;pendingClick=false;
                    closeOwnedMenu();
                    log("Acceptance confirmed by scoreboard: "+scoreboard.objective);
                    move(selectedFallback?State.FALLBACK_REFRESH_MODE:State.PREPARING_CONTRACT);
                }
            }
        } else if(state==State.EXECUTING_CONTRACT || state==State.FALLBACK_REFRESH_MODE
                || state==State.CONTRACT_COMPLETE || state==State.PREPARING_CONTRACT
                || (!equipmentPurchasePending && (state==State.FINDING_SHOP || state==State.MOVING_TO_SHOP
                || state==State.OPENING_SHOP || state==State.BUYING_SHOP))) {
            if(scoreboardPolled && ++missingSamples>=2) {
                log("Contract disappeared from scoreboard; requesting another.");
                restorePerks();
                stopGrinder();closeOwnedMenu();cleanupNavigation();com.nezurstandalone.contract.ContractCombatPolicy.clear();
                selected=null;selectedFallback=false;acceptedByThisModule=false;purchaseName=null;
                move(grinder()!=null && grinder().isRestoringTemporaryPerk()?State.RESTORING_PERKS:
                        (autoNext.enabled?State.FINDING_CONTRACTOR:State.CHECKING_SCOREBOARD));
                return;
            }
        }
        switch(state) {
            case GAPPLE_INSPECTING_OFFERS:
                AutoGrinder inspecting=grinder();
                if(inspecting==null){recover("Gapple perk inspection unavailable.");break;}
                if(inspecting.gappleInspectionReady()){
                    stopGrinder();cleanupNavigation();offerGate.reset();
                    if(inspecting.gappleEligible() && goldenAppleSlot()<0){
                        gapplePreAccept=true;beginGapple(now);
                        logImportant("Obtaining initial apple before accepting GOLDEN_APPLES.");
                    } else move(State.FINDING_CONTRACTOR);
                } else if(now-stateSince>45000){inspecting.releaseGappleInspection();stopGrinder();recover("Gapple perk inspection timed out.");}
                break;
            case CHECKING_SCOREBOARD:
                if(!scoreboard.active && grinder()!=null && grinder().hasTemporaryPerkLease()
                        && scoreboardPolled && ++missingSamples>=2){restorePerks();move(State.RESTORING_PERKS);break;}
                if(!scoreboard.active && autoNext.enabled
                        && (grinder()==null || !grinder().isRestoringTemporaryPerk()))move(State.FINDING_CONTRACTOR);
                break;
            case FINDING_CONTRACTOR:
            case MOVING_TO_CONTRACTOR:
                navigateToContractor(now);break;
            case OPENING_CONTRACTOR:
                openContractor(now);break;
            case READING_MENU:
            case WAITING_FOR_STABLE_OFFERS:
                handleMenu(now);break;
            case VERIFYING_ACCEPTANCE:
                if(now-stateSince>10000) {
                    if(contractStartedChat) {
                        acceptedByThisModule=true;
                        closeOwnedMenu();
                        log("Server confirmed contract in chat; scoreboard objective unreadable. Holding without repurchasing.");
                        move(State.FALLBACK_REFRESH_MODE);
                    } else recover("Neither chat nor scoreboard confirmed acceptance.");
                }
                break;
            case PREPARING_NO_PERK_OFFER:
                if(scoreboard.active){restorePerks();move(State.RESTORING_PERKS);break;}
                AutoGrinder noPerkGrinder=grinder();
                if(noPerkGrinder==null){move(State.NO_VILE_OFFERS);break;}
                if(!noPerkGrinder.hasTemporaryPerkLease())noPerkGrinder.requestTemporaryNoPerk();
                // No contract has been accepted yet: keep the normal grinder available to
                // gather the gold needed by the perk menu while the temporary lease is prepared.
                com.nezurstandalone.contract.ContractCombatPolicy.clear();
                startGrinder();
                if(noPerkGrinder.isTemporaryNoPerkReady()){
                    stopGrinder();offerGate.reset();move(State.FINDING_CONTRACTOR);
                }else if(now-stateSince>45000){restorePerks();move(State.RESTORING_PERKS);}
                break;
            case PREPARING_CONTRACT:
                if(scoreboard.type==ContractOffer.Type.GOLDEN_APPLES){
                    beginGapple(now);break;
                }
                if(scoreboard.target>0 && scoreboard.current>=scoreboard.target){move(State.CONTRACT_COMPLETE);break;}
                if(scoreboard.type==ContractOffer.Type.FIST_MID_KILLS) {
                    AutoGrinder g=grinder();
                    if(g==null || g.isTemporaryKungFuUnavailable()) {
                        log("FIST_MID_KILLS cannot start: no replaceable perk slot or perk unavailable.");
                        move(State.FALLBACK_REFRESH_MODE);break;
                    }
                    if(!g.isTemporaryKungFuReady()) {
                        if(!g.hasTemporaryPerkLease())g.requestTemporaryKungFu();
                        com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.COLLECT_GOLD_INGOTS,0,3.5);
                        startGrinder();
                        if(now-stateSince>45000){restorePerks();move(State.FALLBACK_REFRESH_MODE);}
                        break;
                    }
                }
                if(scoreboard.type==ContractOffer.Type.NO_PERK_KILLS) {
                    AutoGrinder g=grinder();
                    if(g==null){move(State.FALLBACK_REFRESH_MODE);break;}
                    if(!g.isTemporaryNoPerkReady()) {
                        if(!g.hasTemporaryPerkLease())g.requestTemporaryNoPerk();
                        com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.COLLECT_GOLD_INGOTS,0,3.5);
                        startGrinder();
                        if(now-stateSince>45000){restorePerks();move(State.FALLBACK_REFRESH_MODE);}
                        break;
                    }
                }
                if(isExecutable(scoreboard.type) && needsShop()) {move(State.FINDING_SHOP);break;}
                if(isExecutable(scoreboard.type) && prepareObjective(scoreboard.type)) {
                    com.nezurstandalone.contract.ContractCombatPolicy.install(scoreboard.type,(long)sneakDelay.value,sneakRadius.value);
                    if(scoreboard.type!=ContractOffer.Type.COLLECT_GOLD_INGOTS)startGrinder();
                    move(State.EXECUTING_CONTRACT);
                } else if(!isExecutable(scoreboard.type) || now-stateSince>8000) {
                    stopGrinder();log("Cannot safely prepare active objective "+scoreboard.type+"; waiting.");move(State.FALLBACK_REFRESH_MODE);
                }
                break;
            case EXECUTING_CONTRACT:
                if(scoreboard.target>0 && scoreboard.current>=scoreboard.target) {
                    if(scoreboard.type==ContractOffer.Type.NO_PERK_KILLS)logImportant("NO_PERK contract complete");
                    restorePerks();
                    stopGrinder();com.nezurstandalone.contract.ContractCombatPolicy.clear();
                    cleanupNavigation();log("Scoreboard objective complete; waiting for server removal.");move(State.CONTRACT_COMPLETE);
                }
                else if(scoreboard.type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW && !ensureDiamondSwordHotbar()){
                    stopGrinder();cleanupNavigation();move(State.PREPARING_CONTRACT);
                }
                else if(scoreboard.type==ContractOffer.Type.NO_PERK_KILLS
                        && (grinder()==null || !grinder().isTemporaryNoPerkReady())){
                    com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.COLLECT_GOLD_INGOTS,0,3.5);
                    move(State.PREPARING_CONTRACT);
                }
                else if(!isExecutable(scoreboard.type)) {restorePerks();stopGrinder();move(State.FALLBACK_REFRESH_MODE);}
                else if(scoreboard.type==ContractOffer.Type.COLLECT_GOLD_INGOTS)tickGold(now);
                else if(scoreboard.type==ContractOffer.Type.FIST_MID_KILLS && !ensureUnarmedHotbar()) {
                    // Fist attacks remain contract-gated until an empty hand can be selected.
                }
                else if(scoreboard.type==ContractOffer.Type.NO_ARMOR_KILLS && !ensureNoArmor())stopGrinder();
                else if(grinder()!=null && !grinder().isToggled()) startGrinder();
                break;
            case FINDING_SHOP:
            case MOVING_TO_SHOP: navigateToShop(now);break;
            case OPENING_SHOP: openShop(now);break;
            case BUYING_SHOP: buyShop(now);break;
            case CONTRACT_COMPLETE:
                stopGrinder();
                String completeZone=com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
                if(!"Spawn".equals(completeZone) && !"Overspawn".equals(completeZone) && now>=nextReturnToSpawn
                        && com.nezurstandalone.control.CommandCoordinator.send(this,"/oof"))nextReturnToSpawn=now+5000;
                if("Spawn".equals(completeZone) || "Overspawn".equals(completeZone)){
                    selected=null;selectedFallback=false;nextAction=0;
                    move(grinder()!=null && grinder().isRestoringTemporaryPerk()?State.RESTORING_PERKS:
                            (autoNext.enabled?State.FINDING_CONTRACTOR:State.CHECKING_SCOREBOARD));
                }
                break;
            case RESTORING_PERKS:
                if(grinder()==null || !grinder().isRestoringTemporaryPerk())
                    move(scoreboard.active?State.CONTRACT_COMPLETE:
                            (autoNext.enabled?State.FINDING_CONTRACTOR:State.CHECKING_SCOREBOARD));
                break;
            case FALLBACK_REFRESH_MODE:
                stopGrinder();
                purchaseName=null;equipmentPurchasePending=false;
                // A confirmed active contract must never drive navigation back to the Quest Master.
                if(scoreboard!=null && scoreboard.active) {
                    cleanupNavigation();
                    String zone=com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
                    if(!"Spawn".equals(zone) && !"Overspawn".equals(zone) && now>=nextReturnToSpawn
                            && com.nezurstandalone.control.CommandCoordinator.send(this,"/oof"))nextReturnToSpawn=now+5000;
                }
                else if(now-stateSince>3000)stayNearContractor(now);
                break;
            case NO_VILE_OFFERS:
                stopGrinder();
                if(now>=noOfferRetryAt) {offerGate.reset();move(State.FINDING_CONTRACTOR);}
                else cleanupNavigation();
                break;
            case RECOVERING:
                if(now>=nextAction)move(scoreboard.active?
                        (selectedFallback?State.FALLBACK_REFRESH_MODE:State.PREPARING_CONTRACT)
                        :State.FINDING_CONTRACTOR);
                break;
            case LOBBY_SWITCHING: break;
        }
    }

    private long nextReturnToSpawn;
    /** NPC travel and menus own interaction; unrelated chests must not steal their aim/clicks. */
    public boolean ownsNpcInteraction() {
        if(!isToggled())return false;
        switch(state) {
            case FINDING_CONTRACTOR: case MOVING_TO_CONTRACTOR: case OPENING_CONTRACTOR:
            case READING_MENU: case WAITING_FOR_STABLE_OFFERS: case VERIFYING_ACCEPTANCE:
            case FINDING_SHOP: case MOVING_TO_SHOP: case OPENING_SHOP: case BUYING_SHOP:
                return true;
            default: return false;
        }
    }

    private void navigateToContractor(long now) {
        if(scoreboard!=null && scoreboard.active && !inspectingActiveIdentity) {
            closeOwnedMenu();cleanupNavigation();
            move(isExecutable(scoreboard.type)?State.PREPARING_CONTRACT:State.FALLBACK_REFRESH_MODE);
            return;
        }
        stopGrinder();
        if(grinder()!=null && grinder().isRestoringTemporaryPerk())return;
        if(mc.currentScreen!=null) return;
        String zone=com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        if(!"Spawn".equals(zone) && !"Overspawn".equals(zone)) {
            cleanupNavigation();
            if(now>=nextReturnToSpawn && com.nezurstandalone.control.CommandCoordinator.send(this,"/oof"))
                nextReturnToSpawn=now+5000;
            stateSince=now;
            return;
        }
        npc=findNpc();
        if(npc==null) {if(now-stateSince>6000)recover("Contractor NPC not found.");return;}
        if(mc.thePlayer.getDistanceToEntity(npc)<=0.45) {
            PathfinderManager.clear(this,true);move(State.OPENING_CONTRACTOR);return;
        }
        if(mc.thePlayer.getDistanceToEntity(npc)<=2.0) {
            PathfinderManager.clear(this,true);
            float[] approach=RotationUtils.getRotations(npc,0,-0.35,0);
            RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,approach[0],approach[1],16,true);
            if(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(approach[0]-mc.thePlayer.rotationYaw))<20)
                com.nezurstandalone.control.MovementKeys.set("contractor-approach",mc.gameSettings.keyBindForward.getKeyCode(),true);
            if(now-stateSince>12000)recover("Contractor approach blocked.");
            return;
        }
        if(now>=nextAction) {
            PathfinderManager.walkTo(this,npc.posX,npc.posY,npc.posZ,true);
            nextAction=now+1200;
            move(State.MOVING_TO_CONTRACTOR);
        }
        if(now-stateSince>12000)recover("Contractor navigation timed out.");
    }

    private void openContractor(long now) {
        if(mc.currentScreen instanceof GuiChest) {questsIssuedThisVisit.clear();pendingQuestClick=false;move(State.READING_MENU);return;}
        if(mc.currentScreen!=null)return;
        npc=findNpc();
        if(npc==null || mc.thePlayer.getDistanceToEntity(npc)>0.8) {move(State.FINDING_CONTRACTOR);return;}
        PathfinderManager.stopIfAvailable(this);
        float[] rot=RotationUtils.getRotations(npc,0,-0.35,0);
        RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,rot[0],rot[1],16,true);
        if(now>=nextAction && !mc.playerController.getIsHittingBlock()) {
            final Entity target=npc;
            lastInteractedNpc=target;
            // Same native crosshair left-click emitter used by AutoGrinder. Do not
            // require entityHit==npc: overlapping entities can change the raycast.
            GuardedInput.attackCrosshair(interactionOwner,()->isToggled() && state==State.OPENING_CONTRACTOR
                    && mc.theWorld==world && mc.currentScreen==null && !target.isDead
                    && mc.thePlayer.getDistanceToEntity(target)<=0.8);
            nextAction=now+200;
        }
        if(now-stateSince>5000)recover("Contractor GUI did not open.");
    }

    private void handleMenu(long now) {
        if(!(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
            if(now-stateSince>2500)recover("Contractor menu closed or changed.");return;
        }
        ContainerChest container=(ContainerChest)mc.thePlayer.openContainer;
        if(((GuiChest)mc.currentScreen).inventorySlots!=container)return;
        IInventory inv=container.getLowerChestInventory();
        String title=StringUtils.stripControlCodes(inv.getDisplayName().getUnformattedText());
        String contents=signature(inv);
        if(pendingClick) {
            // Starting a quest is an in-place action: some server menus only send chat
            // confirmation, leaving the inventory unchanged. Do not close/reopen it.
            if(pendingQuestClick){
                if(now<nextAction)return;
                pendingQuestClick=false;
            }else if(container==pendingContainer && contents.equals(pendingContents)) {
                if(now-pendingSince>4000)recover("Menu click had no observed transition.");
                return;
            }
            pendingClick=false;
        }
        if(container!=observedContainer || !title.equals(observedMenu) || !contents.equals(observedContents)) {
            observedContainer=container;observedMenu=title;observedContents=contents;
            nextAction=now+100;
            if(!title.equalsIgnoreCase("Pick a contract!"))offerGate.reset();
            return;
        }
        if(now<nextAction || mc.thePlayer.inventory.getItemStack()!=null)return;
        if(title.equalsIgnoreCase("Quests & Contracts")) {
            for(int questSlot=0;!inspectingActiveIdentity && questSlot<inv.getSizeInventory();questSlot++){
                ItemStack quest=inv.getStackInSlot(questSlot);
                if(quest!=null && ContractOffer.shouldStartQuest(quest.getDisplayName(),lore(quest),
                        autoDailyQuests.enabled,autoWeeklyQuests.enabled)){
                    String questKey=StringUtils.stripControlCodes(quest.getDisplayName()).trim().toLowerCase(java.util.Locale.ROOT);
                    if(questsIssuedThisVisit.contains(questKey))continue;
                    if(click(container,questSlot,now)){questsIssuedThisVisit.add(questKey);pendingQuestClick=true;}
                    return;
                }
            }
            int noviceLimit=find(inv,"Novice Contract"), bigLimit=find(inv,"Big Time Contract");
            if(noviceLimit>=0 && bigLimit>=0
                    && ContractOffer.dailyLimit(lore(inv.getStackInSlot(noviceLimit)))
                    && ContractOffer.dailyLimit(lore(inv.getStackInSlot(bigLimit)))) {
                stopForDailyLimit();return;
            }
            int active=find(inv,"Active contract!");
            if(active>=0) {
                ContractScoreboard.Snapshot menu=ContractScoreboard.activeMenu(loreLines(inv.getStackInSlot(active)));
                if(menu.type==ContractOffer.Type.UNKNOWN){
                    if(!dumpUnknownBeforeAction(contents,now))return;
                    recover("Active task could not be identified; combat remains stopped.");return;
                }
                inspectingActiveIdentity=false;
                contractTracker.confirm(menu,now);
                scoreboard=contractTracker.observe(menu,now);
                selectedFallback=selectedFallback || !isExecutable(scoreboard.type);
                purchaseName=null;equipmentPurchasePending=false;attempts=0;
                closeOwnedMenu();cleanupNavigation();stopGrinder();
                log("Active contract verified in NPC menu: "+scoreboard.objective);
                move(selectedFallback?State.FALLBACK_REFRESH_MODE:State.PREPARING_CONTRACT);
                return;
            }
            if(inspectingActiveIdentity){recover("Active contract item missing; will retry verification without buying another.");return;}
            if(scoreboard.active) {closeOwnedMenu();return;}
            int pending=find(inv,"Pending choice");
            if(pending>=0) {click(container,pending,now);return;}
            int big=find(inv,"Big Time Contract");
            if(big>=0 && Utils.getGoldDouble()>=cost(inv.getStackInSlot(big))) {click(container,big,now);return;}
            recover("No affordable Big Time Contract or pending choice.");return;
        }
        if(inspectingActiveIdentity){
            if(now-stateSince>5000)recover("Expected active contract menu; refusing purchase or acceptance during inspection.");
            return;
        }
        if(title.equalsIgnoreCase("Are you sure?")) {
            int confirm=find(inv,"Confirm");
            if(confirm>=0 && lore(inv.getStackInSlot(confirm)).toLowerCase().contains("purchase: big time contract")) {click(container,confirm,now);return;}
            recover("Unknown Contractor purchase confirmation.");return;
        }
        if(title.equalsIgnoreCase("Pick a contract!")) {
            List<ContractOffer> offers=offers(inv);
            if(!offerGate.ready(offers,now,(long)menuDelay.value)) {move(State.WAITING_FOR_STABLE_OFFERS);return;}
            for(ContractOffer offer:offers)if(offer.type==ContractOffer.Type.UNKNOWN){
                if(!dumpUnknownBeforeAction(contents,now))return;
                break;
            }
            AutoGrinder perkGrinder=grinder();
            if(perkGrinder!=null && perkGrinder.isTemporaryNoPerkReady() && selected!=null
                    && selected.type==ContractOffer.Type.NO_PERK_KILLS){
                for(ContractOffer offer:offers)if(offer.type==ContractOffer.Type.NO_PERK_KILLS
                        && offer.task.equalsIgnoreCase(selected.task) && offer.vileAmount>0){
                    selected=offer;click(container,offer.slot,now);return;
                }
                restorePerks();closeOwnedMenu();move(State.RESTORING_PERKS);return;
            }
            if(goldenApples.enabled && grinder()!=null && !grinder().hasTemporaryPerkLease()){
                boolean inspect=false;
                for(ContractOffer offer:offers)if(offer.type==ContractOffer.Type.GOLDEN_APPLES && offer.vileAmount>0)inspect=true;
                List<ContractOffer.Type> potential=new ArrayList<ContractOffer.Type>(executable());
                potential.add(ContractOffer.Type.GOLDEN_APPLES);
                ContractSelector.Choice potentialChoice=ContractSelector.choose(offers,priority(),potential,false,random);
                inspect=inspect && potentialChoice.offer!=null && potentialChoice.offer.type==ContractOffer.Type.GOLDEN_APPLES;
                if(inspect){closeOwnedMenu();cleanupNavigation();grinder().requestGappleInspection();
                    com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.GOLDEN_APPLES,0,3.5);
                    move(State.GAPPLE_INSPECTING_OFFERS);startGrinder();return;}
            }
            ContractSelector.Choice choice=ContractSelector.choose(offers,priority(),executable(),true,random);
            for(ContractOffer offer:offers)log("Choice "+offer.name+" "+offer.type+" vile="+offer.vileAmount+
                    (offer.vileAmount==0?" rejected:no Vile":!isExecutable(offer.type)?" rejected:not executable":" playable"));
            if(choice.offer==null) {
                log("No executable Vile-paying offers; waiting five minutes at spawn.");
                noOfferRetryAt=now+300000L;
                closeOwnedMenu();move(State.NO_VILE_OFFERS);return;
            }
            selected=choice.offer;selectedFallback=choice.mode==ContractSelector.Mode.FALLBACK;
            if((selected.type!=ContractOffer.Type.GOLDEN_APPLES || selectedFallback) && grinder()!=null){
                grinder().releaseGappleInspection();stopGrinder();
            }
            log("Selected "+selected.type+" mode="+choice.mode);
            if(selected.type==ContractOffer.Type.NO_PERK_KILLS){
                closeOwnedMenu();move(State.PREPARING_NO_PERK_OFFER);return;
            }
            click(container,selected.slot,now);return;
        }
        if(title.equalsIgnoreCase("Start event?")) {
            int accept=find(inv,"Accept contract");
            if(selected!=null && (selectedFallback || selected.vileAmount>0) && accept>=0 && (selectedFallback || ContractOffer.vileReward(lore(inv.getStackInSlot(accept)))>0)
                    && lore(inv.getStackInSlot(accept)).toLowerCase().contains(selected.task.toLowerCase())) {
                completedSidebarSuppressed=false;contractTracker.clear();nextPoll=0;
                contractStartedChat=false;
                click(container,accept,now);move(State.VERIFYING_ACCEPTANCE);return;
            }
            recover("Contract confirmation does not match selected Vile offer.");return;
        }
        if(now-stateSince>5000){
            if(lastInteractedNpc!=null){
                rejectedContractorNpcs.add(lastInteractedNpc.getEntityId());
                log("Rejecting villager "+lastInteractedNpc.getEntityId()+" after it opened "+title+"; trying another NPC.");
                lastInteractedNpc=null;
            }
            recover("Wrong Contractor GUI: "+title);
        }
    }

    private boolean click(ContainerChest container,int slot,long now) {
        if(!com.nezurstandalone.control.GuiLease.acquire(this))return false;
        if(container!=mc.thePlayer.openContainer || slot<0 || slot>=container.getLowerChestInventory().getSizeInventory())return false;
        ItemStack stack=container.getLowerChestInventory().getStackInSlot(slot);
        if(stack==null)return false;
        mc.playerController.windowClick(container.windowId,slot,0,0,mc.thePlayer);
        pendingClick=true;pendingQuestClick=false;pendingContainer=container;pendingContents=signature(container.getLowerChestInventory());pendingSince=now;
        nextAction=now+500;
        observedContents="";
        log("Clicked "+StringUtils.stripControlCodes(stack.getDisplayName())+" in "+observedMenu);
        return true;
    }

    private List<ContractOffer> offers(IInventory inv) {
        List<ContractOffer> result=new ArrayList<ContractOffer>();
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack stack=inv.getStackInSlot(i);
            if(stack==null)continue;
            ContractOffer offer=ContractOffer.parse(i,net.minecraft.item.Item.getIdFromItem(stack.getItem()),
                    stack.getMetadata(),StringUtils.stripControlCodes(stack.getDisplayName()),loreLines(stack));
            if(offer!=null)result.add(offer);
        }
        return result;
    }
    private List<String> loreLines(ItemStack stack) {
        List<String> lines=new ArrayList<String>();
        if(stack==null || !stack.hasTagCompound())return lines;
        NBTTagCompound display=stack.getTagCompound().getCompoundTag("display");
        NBTTagList list=display.getTagList("Lore",8);
        for(int i=0;i<list.tagCount();i++)lines.add(StringUtils.stripControlCodes(list.getStringTagAt(i)));
        return lines;
    }
    private String lore(ItemStack stack) {return String.join(" ",loreLines(stack));}
    private int find(IInventory inv,String name) {
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack s=inv.getStackInSlot(i);
            if(s!=null && StringUtils.stripControlCodes(s.getDisplayName()).trim()
                    .replaceFirst("\\.+$", "").equalsIgnoreCase(name))return i;
        }
        return -1;
    }
    private int cost(ItemStack stack) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)cost:\\s*([\\d,]+)g").matcher(lore(stack));
        return m.find()?Integer.parseInt(m.group(1).replace(",","")):Integer.MAX_VALUE;
    }
    private String signature(IInventory inv) {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<inv.getSizeInventory();i++) {
            ItemStack s=inv.getStackInSlot(i);
            if(s!=null)b.append(i).append(':').append(s.writeToNBT(new NBTTagCompound())).append(';');
        }
        return b.toString();
    }
    private Entity findNpc() {return findNpc("CONTRACT");}
    private Entity findNpc(String kind) {
        if(mc.theWorld==null || mc.thePlayer==null)return null;
        Entity best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(Entity tag:mc.theWorld.loadedEntityList) {
            if(tag.isDead || tag instanceof net.minecraft.entity.player.EntityPlayer)continue;
            String label=tag.hasCustomName()?tag.getCustomNameTag():tag.getDisplayName().getUnformattedText();
            if(!com.nezurstandalone.contract.ContractNpcMatcher.matches(kind,label))continue;
            for(Entity candidate:mc.theWorld.loadedEntityList)if(candidate instanceof EntityVillager && !candidate.isDead) {
                if((kind.equals("CONTRACT")?rejectedContractorNpcs:rejectedShopNpcs).contains(candidate.getEntityId()))continue;
                double score=com.nezurstandalone.contract.ContractNpcMatcher.score(candidate.posX-tag.posX,
                        tag.posY-candidate.posY,candidate.posZ-tag.posZ,mc.thePlayer.getDistanceSqToEntity(candidate));
                if(score<bestScore){best=candidate;bestScore=score;}
            }
        }
        return best;
    }
    private void stayNearContractor(long now) {
        npc=findNpc();
        if(npc==null)return;
        if(mc.thePlayer.getDistanceToEntity(npc)>waitRadius.value && now>=nextAction) {
            PathfinderManager.walkTo(this,npc.posX,npc.posY,npc.posZ,true);nextAction=now+1200;
        } else if(mc.thePlayer.getDistanceToEntity(npc)<=waitRadius.value)PathfinderManager.clear(this,true);
    }
    private boolean hasGoldBalanceLine() {
        for(String line:Utils.getScoreboardLines())
            if(StringUtils.stripControlCodes(line).toLowerCase(java.util.Locale.ROOT).contains("gold:"))return true;
        return false;
    }
    private String neededShopItem() {
        if(scoreboard!=null && scoreboard.active && !isExecutable(scoreboard.type))return null;
        if(scoreboard!=null && scoreboard.active && scoreboard.type==ContractOffer.Type.NO_ARMOR_KILLS)return null;
        if(scoreboard!=null && scoreboard.active && scoreboard.type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW
                && !hasItemId(276))return "Diamond Sword";
        if(hasTwoEquippedArmorPieces())return null;
        String mode=equipmentMode.getMode();
        if(mode.equals("Iron Pack") && !ironPackPurchased && !hasIronPackCoverage())return "Iron Pack";
        if(mode.equals("Diamond Chestplate + Boots") || mode.equals("Full Diamond")) {
            if(!hasItemId(311))return "Diamond Chestplate";
            if(!hasItemId(313))return "Diamond Boots";
        }
        // In this Pit setup Full Diamond means the available diamond armor: chestplate + boots.
        return null;
    }
    private boolean needsShop() {return neededShopItem()!=null;}
    private boolean hasTwoEquippedArmorPieces() {
        if(mc.thePlayer==null)return false;
        int count=0;
        for(ItemStack stack:mc.thePlayer.inventory.armorInventory) {
            if(stack==null || !(stack.getItem() instanceof net.minecraft.item.ItemArmor))continue;
            net.minecraft.item.ItemArmor.ArmorMaterial material=((net.minecraft.item.ItemArmor)stack.getItem()).getArmorMaterial();
            if(material==net.minecraft.item.ItemArmor.ArmorMaterial.IRON
                    || material==net.minecraft.item.ItemArmor.ArmorMaterial.DIAMOND)count++;
        }
        return count>=2;
    }
    private boolean hasIronPackCoverage() {
        return hasArmorAtLeast(1,6) && hasArmorAtLeast(2,5) && hasArmorAtLeast(3,2);
    }
    private boolean hasArmorAtLeast(int type,int protection) {
        if(mc.thePlayer==null)return false;
        for(int i=0;i<mc.thePlayer.inventory.getSizeInventory();i++) {
            ItemStack stack=mc.thePlayer.inventory.getStackInSlot(i);
            if(stack==null || !(stack.getItem() instanceof net.minecraft.item.ItemArmor))continue;
            net.minecraft.item.ItemArmor armor=(net.minecraft.item.ItemArmor)stack.getItem();
            if(armor.armorType==type && (armor.damageReduceAmount>=protection || stack.isItemEnchanted()))return true;
        }
        return false;
    }
    private boolean hasItemId(int id) {return countItemId(id)>0;}
    private int countItemId(int id) {
        if(mc.thePlayer==null)return 0;
        int count=0;
        for(ItemStack item:mc.thePlayer.inventory.mainInventory)
            if(item!=null && net.minecraft.item.Item.getIdFromItem(item.getItem())==id)count+=item.stackSize;
        for(ItemStack item:mc.thePlayer.inventory.armorInventory)
            if(item!=null && net.minecraft.item.Item.getIdFromItem(item.getItem())==id)count+=item.stackSize;
        return count;
    }
    private int purchaseEvidence(String name) {
        if(name.equals("Diamond Sword"))return countItemId(276);
        if(name.equals("Diamond Chestplate"))return countItemId(311);
        if(name.equals("Diamond Boots"))return countItemId(313);
        if(name.equals("Iron Pack"))return countItemId(307)+countItemId(308)+countItemId(309);
        return 0;
    }
    private void navigateToShop(long now) {
        stopGrinder();
        if(mc.currentScreen!=null)return;
        npc=findNpc("ITEMS");
        if(npc==null){if(now-stateSince>6000)recover("Item shop NPC not found.");return;}
        if(mc.thePlayer.getDistanceToEntity(npc)<=0.45){PathfinderManager.clear(this,true);com.nezurstandalone.control.MovementKeys.release("contractor-approach");move(State.OPENING_SHOP);return;}
        if(mc.thePlayer.getDistanceToEntity(npc)<=2.0){
            PathfinderManager.clear(this,true);float[] rot=RotationUtils.getRotations(npc,0,-0.35,0);
            RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,rot[0],rot[1],16,true);
            com.nezurstandalone.control.MovementKeys.set("contractor-approach",mc.gameSettings.keyBindForward.getKeyCode(),Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(rot[0]-mc.thePlayer.rotationYaw))<20);
            if(now-stateSince>12000)recover("Item shop approach blocked.");return;
        }
        if(now>=nextAction){PathfinderManager.walkTo(this,npc.posX,npc.posY,npc.posZ,true);nextAction=now+1200;move(State.MOVING_TO_SHOP);}
        if(now-stateSince>12000)recover("Shop navigation timed out.");
    }
    private void openShop(long now) {
        if(mc.currentScreen instanceof GuiChest){move(State.BUYING_SHOP);return;}
        if(mc.currentScreen!=null)return;
        npc=findNpc("ITEMS");
        if(npc==null || mc.thePlayer.getDistanceToEntity(npc)>4.2){move(State.FINDING_SHOP);return;}
        PathfinderManager.stopIfAvailable(this);
        float[] rot=RotationUtils.getRotations(npc,0,-0.35,0);
        RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,rot[0],rot[1],16,true);
        if(now>=nextAction && !mc.playerController.getIsHittingBlock()) {
            final Entity target=npc;
            GuardedInput.attackCrosshair(interactionOwner,()->isToggled() && state==State.OPENING_SHOP
                    && mc.theWorld==world && mc.currentScreen==null && !target.isDead
                    && mc.thePlayer.getDistanceToEntity(target)<=3.0);
            nextAction=now+200;
        }
        if(now-stateSince>5000)recover("Shop GUI did not open.");
    }
    private void buyShop(long now) {
        if(!(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)){
            if(now-stateSince>3000)recover("Item shop GUI closed.");return;
        }
        ContainerChest container=(ContainerChest)mc.thePlayer.openContainer;
        if(((GuiChest)mc.currentScreen).inventorySlots!=container)return;
        IInventory inv=container.getLowerChestInventory();
        String title=StringUtils.stripControlCodes(inv.getDisplayName().getUnformattedText());
        if(!title.equalsIgnoreCase("Non-permanent items")){
            if(now-stateSince>5000){
                if(npc!=null)rejectedShopNpcs.add(npc.getEntityId());
                recover("Wrong shop GUI: "+title);
            }
            return;
        }
        observedContainer=container;
        if(purchaseName!=null){
            if(purchaseEvidence(purchaseName)>purchaseBefore
                    || (purchaseName.equals("Iron Pack") && (hasTwoEquippedArmorPieces() || hasIronPackCoverage()))){
                if(purchaseName.equals("Iron Pack"))ironPackPurchased=true;
                log("Verified purchase: "+purchaseName);purchaseName=null;nextAction=now+100;
            } else if(now-purchaseAt>3000){recover("Purchase did not enter inventory: "+purchaseName);return;}
            else return;
        }
        String needed=neededShopItem();
        if(needed==null){
            closeOwnedMenu();equipmentPurchasePending=false;
            move(scoreboard!=null && scoreboard.active?State.PREPARING_CONTRACT:
                    autoNext.enabled?State.FINDING_CONTRACTOR:State.CHECKING_SCOREBOARD);
            return;
        }
        if(now<nextAction)return;
        int slot=find(inv,needed);
        if(slot<0){recover("Shop item missing: "+needed);return;}
        ItemStack offer=inv.getStackInSlot(slot);
        double shopGold=Utils.getGoldDouble();
        if(!lore(offer).toLowerCase().contains("click to purchase") || Double.isNaN(shopGold)
                || shopGold<cost(offer)){
            closeOwnedMenu();log("Cannot afford or verify "+needed+"; pausing equipment purchase.");
            if(equipmentPurchasePending){equipmentPurchasePending=false;equipmentRetryAfter=now+30000;
                move(autoNext.enabled?State.FINDING_CONTRACTOR:State.CHECKING_SCOREBOARD);}
            else move(State.FALLBACK_REFRESH_MODE);
            return;
        }
        if(!com.nezurstandalone.control.GuiLease.acquire(this))return;
        purchaseBefore=purchaseEvidence(needed);purchaseName=needed;purchaseAt=now;
        mc.playerController.windowClick(container.windowId,slot,0,0,mc.thePlayer);
        log("Purchasing "+needed+"; awaiting inventory confirmation.");
    }
    private void recover(String reason) {
        log("Recovery: "+reason);
        purchaseName=null;
        if(equipmentPurchasePending){equipmentPurchasePending=false;equipmentRetryAfter=com.nezurstandalone.control.Clock.millis()+30000;}
        closeOwnedMenu();cleanupNavigation();offerGate.reset();observedContainer=null;
        attempts++;
        nextAction=attempts>(int)retries.value?Long.MAX_VALUE:com.nezurstandalone.control.Clock.millis()+3000;
        if(nextAction==Long.MAX_VALUE){
            log("Maximum retries reached; stopped until module is restarted.");
            restorePerks();
            if(grinder()!=null && grinder().isRestoringTemporaryPerk()){
                move(State.RESTORING_PERKS);return;
            }
        }
        move(State.RECOVERING);
    }
    private final java.util.Map<com.nezurstandalone.settings.BooleanSetting,Boolean> contractorProfile=new java.util.LinkedHashMap<>();
    private final java.util.Map<com.nezurstandalone.settings.NumberSetting,Double> contractorNumbers=new java.util.LinkedHashMap<>();
    private void applyContractorProfile(AutoGrinder g) {
        if(g==null)return;
        for(com.nezurstandalone.settings.Setting setting:g.getSettings()) {
            if(setting instanceof com.nezurstandalone.settings.NumberSetting && setting.name.equals("Combat Aim Speed")) {
                com.nezurstandalone.settings.NumberSetting number=(com.nezurstandalone.settings.NumberSetting)setting;
                if(!contractorNumbers.containsKey(number))contractorNumbers.put(number,number.value);
                number.value=8;
            }
            if(!(setting instanceof com.nezurstandalone.settings.BooleanSetting))continue;
            String name=setting.name;
            if(!java.util.Arrays.asList("Spire Mode","Squad Support","Night Quest Support",
                    "Dragon Egg Support","Mystic Pickup","Auto Prestige","Auto Perk",
                    "Target Fastest Kill","Auto Killstreak","Only Event Mode","Idle Retreat","Enable Break Time","Show HUD").contains(name))continue;
            com.nezurstandalone.settings.BooleanSetting flag=(com.nezurstandalone.settings.BooleanSetting)setting;
            if(!contractorProfile.containsKey(flag))contractorProfile.put(flag,flag.enabled);
            flag.enabled=name.equals("Target Fastest Kill");
        }
    }
    private void restoreContractorProfile() {
        for(java.util.Map.Entry<com.nezurstandalone.settings.BooleanSetting,Boolean> entry:contractorProfile.entrySet())
            entry.getKey().enabled=entry.getValue();
        contractorProfile.clear();
        for(java.util.Map.Entry<com.nezurstandalone.settings.NumberSetting,Double> entry:contractorNumbers.entrySet())entry.getKey().value=entry.getValue();
        contractorNumbers.clear();
    }
    private com.nezurstandalone.settings.NumberSetting leasedMidSetting;
    private double previousMidThreshold;
    private void restoreMidThreshold() {
        if(leasedMidSetting!=null){leasedMidSetting.value=previousMidThreshold;leasedMidSetting=null;}
    }
    private void startGrinder() {
        // Final execution gate: no caller may enable grinding for unsupported objectives.
        // Perk-only preparation must run before acceptance/eligibility is known.
        // The GOLDEN_APPLES policy and engine lease keep combat disabled.
        AutoGrinder preparationGrinder=grinder();
        boolean perkOnly=(state==State.GAPPLE_INSPECTING_OFFERS || state==State.GAPPLE_PREPARING)
                && goldenApples.enabled && preparationGrinder!=null && preparationGrinder.hasTemporaryPerkLease();
        if(inspectingActiveIdentity){stopGrinder();return;}
        if(!perkOnly && (scoreboard==null || !scoreboard.active || !isExecutable(scoreboard.type))) {
            stopGrinder();cleanupNavigation();return;
        }
        if(!perkOnly && scoreboard.type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW
                && !ensureDiamondSwordHotbar()){stopGrinder();return;}
        cleanupNavigation();
        AutoGrinder g=grinder();
        applyContractorProfile(g);
        if(g!=null && scoreboard!=null && scoreboard.active && isExecutable(scoreboard.type)
                && scoreboard.type!=ContractOffer.Type.COLLECT_GOLD_INGOTS && scoreboard.type!=ContractOffer.Type.GOLDEN_APPLES && leasedMidSetting==null) {
            for(com.nezurstandalone.settings.Setting setting:g.getSettings())
                if(setting instanceof com.nezurstandalone.settings.NumberSetting && setting.name.equals("Min Mid Players")) {
                    leasedMidSetting=(com.nezurstandalone.settings.NumberSetting)setting;
                    previousMidThreshold=leasedMidSetting.value;leasedMidSetting.value=3;break;
                }
        }
        if(g!=null && !g.isToggled()){g.setToggled(true);grinderTouched=true;}
    }
    private void gapplePhase(State next,long now,String message){
        cleanupNavigation();com.nezurstandalone.control.InventoryOwner.release(this,true);
        gappleStepAt=now;nextAction=0;gappleLaunchAt=0;move(next);logImportant("GOLDEN_APPLES "+message);
    }
    private void beginGapple(long now){
        AutoGrinder g=grinder();
        if(g==null || !goldenApples.enabled){move(State.FALLBACK_REFRESH_MODE);return;}
        gappleActive=true;gappleUnreadableSince=0;connection.reset();
        if(gappleHeal==null)gappleHeal=Nezur.moduleManager==null?null:Nezur.moduleManager.getModuleByClass(com.nezurstandalone.module.impl.player.AutoHeal.class);
        gappleHealWasEnabled=gappleHealWasEnabled || (gappleHeal!=null && gappleHeal.isToggled());
        if(gappleHealWasEnabled)gappleHeal.setToggled(false);gappleDeaths=0;gappleRetries=0;gappleDeathSeen=false;
        gapplePendingOof=false;gapplePad=null;gappleWait=null;gappleLastProgress=-1;
        com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.GOLDEN_APPLES,0,3.5);
        if(!g.hasTemporaryPerkLease())g.requestGappleInspection();
        gapplePhase(State.GAPPLE_PREPARING,now,"selected; inspecting full original loadout.");
        startGrinder();
    }
    private void cancelGapple(){
        gappleActive=false;gapplePendingOof=false;gapplePreAccept=false;
        if(gappleHeal!=null && gappleHealWasEnabled && !gappleHeal.isToggled())gappleHeal.setToggled(true);
        gappleHeal=null;gappleHealWasEnabled=false;
        com.nezurstandalone.control.InventoryOwner.release(this,true);
        gapplePad=null;gappleWait=null;
        if(gappleInventoryScreen!=null && mc.thePlayer!=null && mc.currentScreen==gappleInventoryScreen)mc.thePlayer.closeScreen();
        gappleInventoryScreen=null;com.nezurstandalone.control.GuiLease.release(this);
    }
    private void gappleFailed(String reason,long now){
        logImportant("GOLDEN_APPLES stopped: "+reason);
        cancelGapple();cleanupNavigation();restorePerks();stopGrinder();
        move(grinder()!=null && grinder().isRestoringTemporaryPerk()?State.RESTORING_PERKS:State.FALLBACK_REFRESH_MODE);
    }
    private void noteGappleDeath(long now){
        if(!gappleActive || gappleDeathSeen)return;
        gappleDeathSeen=true;gappleDeathAt=now;gappleRespawnAt=0;gappleDeathResume=state;
        cleanupNavigation();com.nezurstandalone.control.InventoryOwner.release(this,true);
        if(gapplePendingOof){
            // CommandCoordinator confirms dispatch only. A correlated death confirms this cycle.
            logImportant("GOLDEN_APPLES /oof death observed; cooldown remains anchored to dispatch.");
        } else if(state!=State.GAPPLE_PREPARING){gappleDeaths++;logImportant("GOLDEN_APPLES Natural death #"+gappleDeaths);}
    }
    private int goldenAppleSlot(){
        for(int i=0;i<36;i++){
            ItemStack item=mc.thePlayer.inventory.getStackInSlot(i);
            if(item!=null && item.getItem()==net.minecraft.init.Items.golden_apple
                    && !StringUtils.stripControlCodes(item.getDisplayName()).toLowerCase(java.util.Locale.ROOT).contains("head"))return i;
        }
        return -1;
    }
    private int goldenAppleCount(){
        int count=0;
        for(int i=0;i<36;i++){
            ItemStack item=mc.thePlayer.inventory.getStackInSlot(i);
            if(item!=null && item.getItem()==net.minecraft.init.Items.golden_apple
                    && !StringUtils.stripControlCodes(item.getDisplayName()).toLowerCase(java.util.Locale.ROOT).contains("head"))count+=item.stackSize;
        }
        return count;
    }
    private boolean gappleSelectApple(){
        int slot=goldenAppleSlot();if(slot<0)return false;
        long now=com.nezurstandalone.control.Clock.millis();
        if(com.nezurstandalone.input.NativeActionGate.manual() || mc.thePlayer.isDead
                || mc.thePlayer.inventory.getItemStack()!=null)return false;
        if(now<gappleInventoryActionAt)return false;
        if(slot>=9){
            int hot=-1;for(int i=0;i<9;i++)if(mc.thePlayer.inventory.getStackInSlot(i)==null){hot=i;break;}
            if(hot<0)hot=8; // swap, never discard the previous item
            if(!com.nezurstandalone.control.GuiLease.acquire(this))return false;
            if(mc.currentScreen==null){gappleInventoryScreen=new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer);mc.displayGuiScreen(gappleInventoryScreen);}
            // Never operate a user-owned inventory or a non-player server window.
            if(mc.currentScreen!=gappleInventoryScreen
                    || mc.thePlayer.openContainer!=mc.thePlayer.inventoryContainer)return false;
            mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,slot,hot,2,mc.thePlayer);
            gappleInventoryActionAt=now+500; // no repeated swap on each client tick
            return false;
        }
        if(gappleInventoryScreen!=null && mc.currentScreen==gappleInventoryScreen){mc.thePlayer.closeScreen();gappleInventoryScreen=null;com.nezurstandalone.control.GuiLease.release(this);}
        return mc.currentScreen==null && com.nezurstandalone.control.InventoryOwner.select(this,slot);
    }
    private boolean findGapplePad(){
        final net.minecraft.util.BlockPos[] chosen={null},wait={null};final double[] nearest={Double.MAX_VALUE};
        int y=(int)mc.thePlayer.posY;
        com.nezurstandalone.utils.BlockScanner.scan(60,y-5,y+5,
                block->block.getBlock()==net.minecraft.init.Blocks.slime_block,(pos,block)->{
                    net.minecraft.block.Block above=mc.theWorld.getBlockState(pos.up()).getBlock();
                    if(above!=net.minecraft.init.Blocks.air && above!=net.minecraft.init.Blocks.carpet
                            && above!=net.minecraft.init.Blocks.stone_pressure_plate && above!=net.minecraft.init.Blocks.wooden_pressure_plate
                            && above!=net.minecraft.init.Blocks.heavy_weighted_pressure_plate && above!=net.minecraft.init.Blocks.light_weighted_pressure_plate)return true;
                    if(!mc.theWorld.isAirBlock(pos.up(2)))return true;
                    double x=pos.getX()+.5,z=pos.getZ()+.5,length=Math.hypot(x,z);
                    if(length<1)return true;
                    double wx=x-x/length*Math.max(7.0,gappleWaitDistance.value),wz=z-z/length*Math.max(7.0,gappleWaitDistance.value);
                    net.minecraft.util.BlockPos floor=new net.minecraft.util.BlockPos(wx,pos.getY(),wz);
                    if(!mc.theWorld.getBlockState(floor).getBlock().getMaterial().isSolid()
                            || mc.theWorld.getBlockState(floor).getBlock()==net.minecraft.init.Blocks.slime_block
                            || !mc.theWorld.isAirBlock(floor.up()) || !mc.theWorld.isAirBlock(floor.up(2)))return true;
                    double distance=mc.thePlayer.getDistanceSq(wx,pos.getY()+1,wz);
                    if(distance<nearest[0]){nearest[0]=distance;chosen[0]=new net.minecraft.util.BlockPos(pos);wait[0]=floor.up();}
                    return true;
                });
        gapplePad=chosen[0];gappleWait=wait[0];return gapplePad!=null;
    }
    private void gappleDirect(double x,double z,boolean jump,long now){
        if(mc.currentScreen!=null || com.nezurstandalone.input.NativeActionGate.manual()){
            com.nezurstandalone.control.MovementKeys.release("contractor-gapple");return;
        }
        if(PathfinderManager.isPathing())PathfinderManager.clear(this,true);
        float yaw=(float)Math.toDegrees(Math.atan2(-(x-mc.thePlayer.posX),z-mc.thePlayer.posZ));
        RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,yaw,mc.thePlayer.rotationPitch,9,true);
        boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-mc.thePlayer.rotationYaw))<25;
        com.nezurstandalone.control.MovementKeys.set("contractor-gapple",mc.gameSettings.keyBindForward.getKeyCode(),aligned);
        com.nezurstandalone.control.MovementKeys.set("contractor-gapple",mc.gameSettings.keyBindSprint.getKeyCode(),aligned);
        boolean hop=jump && aligned && mc.thePlayer.onGround && now-gappleJumpAt>=1000;
        com.nezurstandalone.control.MovementKeys.set("contractor-gapple",mc.gameSettings.keyBindJump.getKeyCode(),hop);
        if(hop)gappleJumpAt=now;
    }
    private void followGappleDeathTarget(long now){
        if(mc.thePlayer.hurtTime>gappleLastHurt)gappleDamageAt=now;
        gappleLastHurt=mc.thePlayer.hurtTime;
        if(gappleFollow!=null && now-gappleDamageAt>=2000){gappleSkipped=gappleFollow;gappleSkippedUntil=now+2000;gappleFollow=null;}
        if(gappleFollow==null || gappleFollow.isDead || gappleFollow.getDistanceToEntity(mc.thePlayer)>20){
            gappleFollow=null;double distance=Double.MAX_VALUE;
            for(net.minecraft.entity.player.EntityPlayer player:mc.theWorld.playerEntities){
                if(player==mc.thePlayer || player.isDead || player.getHealth()<=0 || player.isInvisible()
                        || (player==gappleSkipped && now<gappleSkippedUntil)
                        || com.nezurstandalone.module.impl.render.Focus.isHidden(player) || mc.getNetHandler()==null
                        || mc.getNetHandler().getPlayerInfo(player.getUniqueID())==null
                        || !"Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(player.posX,player.posY,player.posZ)))continue;
                double d=mc.thePlayer.getDistanceSqToEntity(player);if(d<distance){distance=d;gappleFollow=player;}
            }
            gappleDamageAt=now;
        }
        if(gappleFollow!=null)gappleDirect(gappleFollow.posX,gappleFollow.posZ,false,now);
        else com.nezurstandalone.control.MovementKeys.release("contractor-gapple");
    }
    private void tickGapple(long now,boolean polled){
        com.nezurstandalone.contract.ContractCombatPolicy.install(ContractOffer.Type.GOLDEN_APPLES,0,3.5);
        if(scoreboard!=null && scoreboard.validPit && scoreboard.readable){
            gappleUnreadableSince=0;
            if(scoreboard.active && !gapplePreAccept){missingSamples=0;
                if(scoreboard.type!=ContractOffer.Type.GOLDEN_APPLES){gappleFailed("objective changed",now);return;}
                if(scoreboard.current!=gappleLastProgress){gappleLastProgress=scoreboard.current;logImportant("GOLDEN_APPLES progress: "+scoreboard.current+"/"+scoreboard.target);}
                if(com.nezurstandalone.contract.ContractPerkPlan.gappleComplete(scoreboard.current,scoreboard.target)){
                    logImportant("GOLDEN_APPLES contract complete.");cancelGapple();cleanupNavigation();restorePerks();stopGrinder();move(State.CONTRACT_COMPLETE);return;
                }
            } else if(!gapplePreAccept && polled && ++missingSamples>=2){
                cancelGapple();cleanupNavigation();restorePerks();stopGrinder();selected=null;selectedFallback=false;
                move(grinder()!=null && grinder().isRestoringTemporaryPerk()?State.RESTORING_PERKS:State.FINDING_CONTRACTOR);return;
            }
        } else {cleanupNavigation();com.nezurstandalone.control.InventoryOwner.release(this,true);gappleStepAt=now;
            if(gappleUnreadableSince==0)gappleUnreadableSince=now;
            if(now-gappleUnreadableSince>60000)gappleFailed("scoreboard unavailable for 60 seconds",now);
            return;}

        if(mc.thePlayer.isDead || mc.thePlayer.getHealth()<=0){noteGappleDeath(now);if(now-gappleDeathAt>30000)gappleFailed("respawn timeout",now);return;}
        if(gappleDeathSeen){
            if(gappleRespawnAt==0){gappleRespawnAt=now;return;}
            if(now-gappleRespawnAt<1000)return;
            gappleDeathSeen=false;boolean oof=gapplePendingOof;gapplePendingOof=false;
            if(gappleDeathResume==State.GAPPLE_PREPARING){
                gapplePhase(State.GAPPLE_PREPARING,now,"Respawn during preparation; preserving snapshot and verifying perks.");startGrinder();
            } else gapplePhase(oof?State.GAPPLE_WAIT_NEXT_APPLE:State.GAPPLE_WAIT_APPLE,now,"Respawn detected; waiting for consolation apple.");
        }
        if(gapplePreAccept && goldenAppleSlot()>=0
                && "Spawn".equals(com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ))){
            if(!gappleSelectApple())return;
            com.nezurstandalone.control.InventoryOwner.release(this,true);
            gapplePreAccept=false;gappleActive=false;stopGrinder();cleanupNavigation();offerGate.reset();
            logImportant("Initial Golden Apple ready in hotbar; returning to Contractor for acceptance.");
            move(State.FINDING_CONTRACTOR);return;
        }
        if(state!=State.GAPPLE_PREPARING)stopGrinder();
        AutoGrinder g=grinder();
        switch(state){
            case GAPPLE_PREPARING:
                if(g==null){gappleFailed("perk system unavailable",now);return;}
                if(g.gappleInspectionReady()){
                    if(!g.gappleEligible()){
                        if(!gapplePreAccept && scoreboard!=null && scoreboard.active
                                && scoreboard.type==ContractOffer.Type.GOLDEN_APPLES){
                            // Eligibility for a NEW offer must not reject an already accepted objective.
                            // Preserve the server snapshot; do not invent or equip either healing perk.
                            stopGrinder();gapplePhase(State.GAPPLE_WAIT_APPLE,now,
                                    "Active contract: server verified no blocking perks; continuing apple routine.");
                            break;
                        }
                        g.releaseGappleInspection();gappleFailed("neither Vampire nor Golden Heads originally equipped",now);return;
                    }
                    g.removeGappleBlockingPerks();
                    if(g.gapplePerksReady()){stopGrinder();gapplePhase(State.GAPPLE_WAIT_APPLE,now,"Blocking healing perks removed successfully.");}
                }
                if(now-gappleStepAt>60000)gappleFailed("perk preparation timeout",now);
                break;
            case GAPPLE_WAIT_APPLE:
            case GAPPLE_WAIT_NEXT_APPLE:
                if(goldenAppleSlot()>=0 && !gapplePreAccept){gapplePhase(State.GAPPLE_COOLDOWN,now,"Golden Apple detected.");break;}
                if(now-gappleStepAt<2000)break;
                if(state==State.GAPPLE_WAIT_NEXT_APPLE){if(now-gappleStepAt>15000)gappleFailed("next consolation apple did not arrive; not restarting natural deaths",now);break;}
                if(com.nezurstandalone.contract.ContractPerkPlan.gappleInitialAction(goldenAppleSlot()>=0,gappleDeaths,(int)gappleMaxDeaths.value)==2){gappleFailed("natural death limit reached without apple",now);break;}
                gapplePhase(State.GAPPLE_ENTERING_MID,now,"Entering mid for initial consolation apple (no attacks, no /oof).");break;
            case GAPPLE_ENTERING_MID:
            case GAPPLE_NATURAL_DEATHS:
                if(goldenAppleSlot()>=0 && !gapplePreAccept){gapplePhase(State.GAPPLE_COOLDOWN,now,"Golden Apple detected.");break;}
                if(now-gappleStepAt>90000){gappleFailed("no natural death/apple within 90 seconds",now);break;}
                if("Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ))){followGappleDeathTarget(now);move(State.GAPPLE_NATURAL_DEATHS);}
                else gappleDirect(0,0,true,now);
                break;
            case GAPPLE_COOLDOWN:
                if(!mc.thePlayer.onGround)break;
                if(!findGapplePad()){
                    if(now-gappleStepAt>15000)gappleFailed("no usable slimepad and safe wait position",now);break;
                }
                gapplePhase(State.GAPPLE_MOVING_PAD,now,"Moving to slimepad wait position "+gappleWaitDistance.value+" blocks behind pad.");break;
            case GAPPLE_MOVING_PAD:
                if(!mc.thePlayer.onGround && mc.thePlayer.motionY>.45){
                    cleanupNavigation();if(++gappleRetries>3){gappleFailed("pad triggered early repeatedly",now);break;}
                    gapplePhase(State.GAPPLE_COOLDOWN,now,"Pad triggered early; waiting to land before retry.");break;
                }
                if(goldenAppleSlot()<0){gapplePhase(State.GAPPLE_WAIT_NEXT_APPLE,now,"apple disappeared before eating");break;}
                double waitHorizontal=mc.thePlayer.getDistanceSq(gappleWait.getX()+.5,mc.thePlayer.posY,gappleWait.getZ()+.5);
                if(com.nezurstandalone.contract.ContractPerkPlan.gappleWaitReached(waitHorizontal,mc.thePlayer.posY-gappleWait.getY(),
                        mc.thePlayer.getDistanceSq(gapplePad.getX()+.5,mc.thePlayer.posY,gapplePad.getZ()+.5),mc.thePlayer.onGround)){
                    gappleEatingSlot=-1;gappleEatCount=goldenAppleCount();gappleEatProgress=scoreboard.current;gapplePhase(State.GAPPLE_EATING,now,"Eating Golden Apple.");break;
                }
                // Do not re-submit a completed route every 400ms. Finish the last short
                // approach directly if the walker's arrival radius is larger than ours.
                boolean sameWait=gappleWait.equals(PathfinderManager.getDestination());
                if(waitHorizontal<=9 && Math.abs(mc.thePlayer.posY-gappleWait.getY())<=1.25){
                    gappleDirect(gappleWait.getX()+.5,gappleWait.getZ()+.5,false,now);
                }else if(now>=nextAction && (!sameWait || PathfinderManager.getState()==PathfinderManager.State.FAILED
                        || PathfinderManager.getState()==PathfinderManager.State.CANCELLED || PathfinderManager.getState()==PathfinderManager.State.IDLE)){
                    PathfinderManager.walkTo(this,gappleWait.getX()+.5,gappleWait.getY(),gappleWait.getZ()+.5,true);nextAction=now+1000;
                }
                if(now-gappleStepAt>30000)gappleFailed("safe positioning timeout",now);
                break;
            case GAPPLE_EATING:
                if(now-gappleStepAt<250){PathfinderManager.clear(this,true);break;}
                if(!mc.thePlayer.onGround || mc.thePlayer.getDistanceSq(gapplePad.getX()+.5,mc.thePlayer.posY,gapplePad.getZ()+.5)<36){
                    if(++gappleRetries>3){gappleFailed("unable to maintain safe eating position",now);break;}
                    gapplePhase(State.GAPPLE_MOVING_PAD,now,"Eating position disturbed; returning to safe wait point.");nextAction=now+250;break;
                }
                PathfinderManager.clear(this,true);
                if(gappleEatingSlot>=0 && (mc.thePlayer.inventory.getStackInSlot(gappleEatingSlot)==null
                        || mc.thePlayer.inventory.getStackInSlot(gappleEatingSlot).getItem()!=net.minecraft.init.Items.golden_apple)){
                    com.nezurstandalone.control.InventoryOwner.release(this,true);
                    // Scoreboard remains authoritative; allow its normal streaming delay before another cycle.
                    if(scoreboard.current>=scoreboard.target && scoreboard.target>0)return;
                    gapplePhase(State.GAPPLE_VERIFY_CONSUMPTION,now,"Golden Apple consumed; awaiting scoreboard progress.");break;
                }
                if((mc.currentScreen!=null && mc.currentScreen!=gappleInventoryScreen) || com.nezurstandalone.input.NativeActionGate.manual()){
                    com.nezurstandalone.control.InventoryOwner.hold(this,false);break;
                }
                if(gappleEatingSlot<0 && gappleSelectApple()){
                    gappleEatingSlot=mc.thePlayer.inventory.currentItem;gappleUseReadyAt=now+150;
                }
                if(gappleEatingSlot>=0){
                    if(!com.nezurstandalone.control.InventoryOwner.owns(this)
                            || mc.thePlayer.inventory.currentItem!=gappleEatingSlot){
                        com.nezurstandalone.control.InventoryOwner.hold(this,false);
                        gappleEatingSlot=-1;gappleUseReadyAt=now+150;break;
                    }
                    // Let the vanilla controller synchronize the held slot before starting use.
                    if(now>=gappleUseReadyAt)com.nezurstandalone.control.InventoryOwner.hold(this,true);
                }
                if(now-gappleStepAt>30000){if(++gappleRetries>3)gappleFailed("apple use interrupted repeatedly",now);else gapplePhase(State.GAPPLE_COOLDOWN,now,"Retrying interrupted consumption.");}
                break;
            case GAPPLE_VERIFY_CONSUMPTION:
                if(scoreboard.current>gappleEatProgress || now-gappleStepAt>=300){
                    gapplePhase(State.GAPPLE_WAIT_CYCLE_COOLDOWN,now,"Apple consumed; waiting only for remaining /oof cooldown.");break;
                }
                if(now-gappleStepAt>10000)gappleFailed("consumption not reflected in scoreboard",now);
                break;
            case GAPPLE_WAIT_CYCLE_COOLDOWN:
                PathfinderManager.clear(this,true);
                if(!com.nezurstandalone.contract.ContractPerkPlan.gappleCooldownReady(now,gappleLastOof,(long)gappleOofCooldown.value)){
                    if(now-gappleCooldownLogAt>3000){gappleCooldownLogAt=now;logImportant("GOLDEN_APPLES cooldown remaining after eating: "+String.format(java.util.Locale.ROOT,"%.1fs",Math.max(0,gappleOofCooldown.value-(now-gappleLastOof))/1000));}
                    break;
                }
                if(mc.thePlayer.onGround)gapplePhase(State.GAPPLE_ACTIVATING_PAD,now,"Cooldown elapsed; activating slimepad now.");
                break;
            case GAPPLE_ACTIVATING_PAD:
                if(com.nezurstandalone.contract.ContractPerkPlan.gappleLaunchDetected(mc.thePlayer.onGround,mc.thePlayer.motionY,
                        mc.thePlayer.posY-(gapplePad.getY()+1),mc.thePlayer.getDistanceSq(gapplePad.getX()+.5,mc.thePlayer.posY,gapplePad.getZ()+.5))){
                    gapplePhase(State.GAPPLE_WAIT_AIRBORNE,now,"Airborne launch detected; delaying /oof.");gappleLaunchAt=now;break;
                }
                if(now>=nextAction){PathfinderManager.walkTo(this,gapplePad.getX()+.5,gapplePad.getY()+1,gapplePad.getZ()+.5,true);nextAction=now+400;}
                if(now-gappleStepAt>12000){if(++gappleRetries>3)gappleFailed("slimepad launch failed",now);else gapplePhase(State.GAPPLE_COOLDOWN,now,"Repositioning after failed launch.");}
                break;
            case GAPPLE_WAIT_AIRBORNE:
                if(mc.thePlayer.onGround){
                    if(++gappleRetries>3)gappleFailed("landed before delayed /oof",now);
                    else gapplePhase(State.GAPPLE_ACTIVATING_PAD,now,"Landed before /oof; retrying pad, not initial death farming.");
                    break;
                }
                if(!com.nezurstandalone.contract.ContractPerkPlan.gappleOofReady(now,gappleLaunchAt,gappleLastOof,1500L,(long)gappleOofCooldown.value,!mc.thePlayer.onGround))break;
                if(com.nezurstandalone.control.CommandCoordinator.send(this,"/oof")){
                    gapplePendingOof=true;gappleOofSentAt=now;gappleLastOof=now;gapplePhase(State.GAPPLE_WAIT_OOF_RESPAWN,now,"Executing /oof; awaiting death confirmation.");
                }
                if(now-gappleStepAt>15000)gappleFailed("/oof dispatch blocked",now);
                break;
            case GAPPLE_WAIT_OOF_RESPAWN:
                if(now-gappleOofSentAt>20000)gappleFailed("/oof death/respawn not confirmed",now);
                break;
            default:break;
        }
    }

    private boolean dumpUnknownBeforeAction(String contents,long now) {
        if(unknownDumpedMenus.contains(contents))return true;
        if(!(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer)
                || !com.nezurstandalone.utils.GuiDumper.dumpF6((net.minecraft.client.gui.inventory.GuiContainer)mc.currentScreen)){
            logImportant("Unknown contract dump failed; pausing menu actions and retrying in 5 seconds.");
            nextAction=now+5000;return false;
        }
        unknownDumpedMenus.add(contents);
        logImportant("Unknown contract saved to nezur_gui_dump_A.txt (F6 dump) before taking action.");
        return true;
    }
    private void stopForDailyLimit() {
        cancelGapple();
        logImportant("All daily contracts are used. AutoContractor and AutoGrinder disabled.");
        // Disable after contractor cleanup too: cleanup may restore the original grinder state.
        setToggled(false);
        AutoGrinder g=grinder();
        if(g!=null && g.isToggled())g.setToggled(false);
        com.nezurstandalone.contract.ContractCombatPolicy.clear();
    }
    private void stopGrinder() {
        restoreMidThreshold();
        AutoGrinder g=grinder();if(g!=null && g.isRestoringTemporaryPerk())return;
        if(g!=null && g.isToggled()){g.setToggled(false);grinderTouched=true;}
        restoreContractorProfile();
    }
    private void restorePerks() {
        AutoGrinder g=grinder();
        if(g==null || !g.hasTemporaryPerkLease())return;
        g.restoreTemporaryContractPerks(!grinderWasEnabled);
        if(g.isRestoringTemporaryPerk() && !g.isToggled()){g.setToggled(true);grinderTouched=true;}
    }
    private AutoGrinder grinder() {return Nezur.moduleManager==null?null:Nezur.moduleManager.getModuleByClass(AutoGrinder.class);}
    private boolean isExecutable(ContractOffer.Type type) {
        return (type==ContractOffer.Type.GOLDEN_APPLES && goldenApples.enabled && grinder()!=null && grinder().gappleEligible())
                || (type==ContractOffer.Type.KILL_PLAYERS && killPlayers.enabled)
                || (type==ContractOffer.Type.CHAIN_KILLS && chainKills.enabled)
                || (type==ContractOffer.Type.KILL_STREAK && killStreak.enabled)
                || (type==ContractOffer.Type.SNEAK_ATTACK_KILLS && sneakKills.enabled)
                || (type==ContractOffer.Type.NO_ARMOR_KILLS && noArmorKills.enabled && canStowArmor())
                || (type==ContractOffer.Type.NO_PERK_KILLS && noPerkKills.enabled && grinder()!=null)
                || (type==ContractOffer.Type.CLAIM_BOUNTIES && bountyKills.enabled)
                || (type==ContractOffer.Type.COLLECT_GOLD_INGOTS && goldIngots.enabled)
                || (type==ContractOffer.Type.FIST_MID_KILLS && fistMid.enabled && hasFreeHotbarSlot()
                    && (grinder()!=null && !grinder().isTemporaryKungFuUnavailable()))
                || (type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW && diamondSword.enabled
                    && (hasDiamondSword() || (hasFreeHotbarSlot() && (hasItemId(276) || buyDiamondSword.enabled))));
    }
    private List<ContractOffer.Type> executable() {
        List<ContractOffer.Type> list=new ArrayList<ContractOffer.Type>();
        for(ContractOffer.Type type:ContractSelector.DEFAULT_PRIORITY)if(isExecutable(type))list.add(type);
        if(killStreak.enabled)list.add(ContractOffer.Type.KILL_STREAK);
        return list;
    }
    private boolean prepareObjective(ContractOffer.Type type) {
        if(type==ContractOffer.Type.NO_ARMOR_KILLS)return ensureNoArmor();
        if(type==ContractOffer.Type.NO_PERK_KILLS)return grinder()!=null && grinder().isTemporaryNoPerkReady();
        if(type==ContractOffer.Type.FIST_MID_KILLS)return ensureUnarmedHotbar();
        if(type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW)return ensureDiamondSwordHotbar();
        return true;
    }
    private final com.nezurstandalone.contract.ContractStuckWatch stuckWatch=new com.nezurstandalone.contract.ContractStuckWatch();
    private long stuckRespawnUntil;
    private boolean recoverBlockedMovement(long now){
        boolean moving=mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
        boolean eligible=state==State.EXECUTING_CONTRACT && scoreboard!=null && scoreboard.active
                && !Utils.isInSpawn() && mc.currentScreen==null
                && !mc.thePlayer.isUsingItem() && !com.nezurstandalone.input.NativeActionGate.manual() && moving;
        if(stuckWatch.blocked(now,eligible,mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)
                && now>=nextReturnToSpawn && com.nezurstandalone.control.CommandCoordinator.send(this,"/oof")){
            stuckWatch.dispatched(now);nextReturnToSpawn=now+12000;stuckRespawnUntil=now+15000;
            stopGrinder();cleanupNavigation();
            logImportant("Movement blocked for 5 seconds; /oof sent, retaining active contract.");
            return true;
        }
        return false;
    }
    private boolean inspectingActiveIdentity;
    private boolean ensureDiamondSwordHotbar() {
        if(mc.thePlayer==null || mc.currentScreen!=null || mc.thePlayer.inventory.getItemStack()!=null)return false;
        for(int i=0;i<9;i++){
            ItemStack sword=mc.thePlayer.inventory.getStackInSlot(i);
            if(sword==null || net.minecraft.item.Item.getIdFromItem(sword.getItem())!=276)continue;
            if(mc.thePlayer.inventory.currentItem!=i){
                if(!com.nezurstandalone.control.InventoryOwner.select(this,i))return false;
                com.nezurstandalone.control.InventoryOwner.release(this,false);
            }
            return mc.thePlayer.inventory.currentItem==i;
        }
        int free=-1,source=-1;
        for(int i=0;i<9;i++)if(mc.thePlayer.inventory.getStackInSlot(i)==null){free=i;break;}
        for(int i=9;i<36;i++){
            ItemStack item=mc.thePlayer.inventory.getStackInSlot(i);
            if(item!=null && net.minecraft.item.Item.getIdFromItem(item.getItem())==276){source=i;break;}
        }
        if(free<0 || source<0 || !com.nezurstandalone.control.GuiLease.acquire(this))return false;
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,source,free,2,mc.thePlayer);
        return false;
    }
    private boolean hasFreeHotbarSlot() {
        if(mc.thePlayer==null)return false;
        for(int i=0;i<9;i++)if(mc.thePlayer.inventory.getStackInSlot(i)==null)return true;
        return false;
    }
    private boolean ensureUnarmedHotbar() {
        if(mc.thePlayer==null || mc.currentScreen!=null || mc.thePlayer.inventory.getItemStack()!=null)return false;
        for(int i=0;i<9;i++) {
            if(mc.thePlayer.inventory.getStackInSlot(i)!=null)continue;
            if(mc.thePlayer.inventory.currentItem==i)return true;
            if(!com.nezurstandalone.control.InventoryOwner.select(this,i))return false;
            com.nezurstandalone.control.InventoryOwner.release(this,false);
            return true;
        }
        return false;
    }
    private boolean hasDiamondSword() {
        if(mc.thePlayer==null)return false;
        for(int i=0;i<9;i++) {
            ItemStack item=mc.thePlayer.inventory.getStackInSlot(i);
            if(item!=null && net.minecraft.item.Item.getIdFromItem(item.getItem())==276)return true;
        }
        return false;
    }
    private boolean canStowArmor() {
        if(mc.thePlayer==null)return false;
        int armor=0,free=0;
        for(ItemStack item:mc.thePlayer.inventory.armorInventory)if(item!=null)armor++;
        for(int i=9;i<36;i++)if(mc.thePlayer.inventory.getStackInSlot(i)==null)free++;
        return free>=armor;
    }
    private net.minecraft.client.gui.inventory.GuiInventory armorScreen;
    private long nextArmorClick;
    private int pendingArmorSlot=-1;
    private int armorDestination=-1;
    private int firstEmptyArmorDestination() {
        for(int slot=9;slot<36;slot++)if(mc.thePlayer.inventory.getStackInSlot(slot)==null)return slot;
        return -1;
    }
    private boolean ensureNoArmor() {
        if(mc.thePlayer==null)return false;
        if(armorScreen!=null && mc.currentScreen!=armorScreen){
            armorScreen=null;pendingArmorSlot=-1;armorDestination=-1;
            com.nezurstandalone.control.GuiLease.release(this);return false;
        }
        if(mc.currentScreen!=null && mc.currentScreen!=armorScreen)return false;
        long now=com.nezurstandalone.control.Clock.millis();
        if(pendingArmorSlot>=0) {
            if(mc.currentScreen!=armorScreen || mc.thePlayer.openContainer!=mc.thePlayer.inventoryContainer
                    || !com.nezurstandalone.control.GuiLease.owns(this) || now<nextArmorClick)return false;
            if(mc.thePlayer.inventory.getItemStack()!=null) {
                if(mc.thePlayer.inventory.armorInventory[pendingArmorSlot]!=null)return false;
                armorDestination=firstEmptyArmorDestination();
                if(armorDestination<0)return false; // Hold safely; never drop or overwrite an item.
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,armorDestination,0,0,mc.thePlayer);
                nextArmorClick=now+50;return false;
            }
            if(armorDestination<0 || mc.thePlayer.inventory.getStackInSlot(armorDestination)==null
                    || mc.thePlayer.inventory.armorInventory[pendingArmorSlot]!=null)return false;
            pendingArmorSlot=-1;armorDestination=-1;
        }
        if(mc.thePlayer.inventory.getItemStack()!=null || !canStowArmor())return false;
        boolean equipped=false;
        for(ItemStack item:mc.thePlayer.inventory.armorInventory)equipped|=item!=null;
        if(!equipped) {
            if(armorScreen!=null && mc.currentScreen==armorScreen)mc.thePlayer.closeScreen();
            armorScreen=null;com.nezurstandalone.control.GuiLease.release(this);return true;
        }
        if(!com.nezurstandalone.control.GuiLease.acquire(this))return false;
        if(armorScreen==null) {
            stopGrinder();cleanupNavigation();
            armorScreen=new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer);
            mc.displayGuiScreen(armorScreen);nextArmorClick=now+50;return false;
        }
        if(mc.thePlayer.openContainer!=mc.thePlayer.inventoryContainer || now<nextArmorClick)return false;
        for(int armorIndex=3;armorIndex>=0;armorIndex--) {
            if(mc.thePlayer.inventory.armorInventory[armorIndex]==null)continue;
            armorDestination=firstEmptyArmorDestination();if(armorDestination<0)return false;
            mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,8-armorIndex,0,0,mc.thePlayer);
            pendingArmorSlot=armorIndex;nextArmorClick=now+50;return false;
        }
        return false;
    }
    private boolean completedSidebarSuppressed;
    private net.minecraft.entity.item.EntityItem goldTarget;
    private int lastGoldProgress=-1;
    private final com.nezurstandalone.contract.GoldPickupRecovery goldPickupRecovery=new com.nezurstandalone.contract.GoldPickupRecovery();
    private final java.util.Map<Integer,Long> blockedGoldTargets=new java.util.HashMap<Integer,Long>();

    private boolean liveGold(net.minecraft.entity.item.EntityItem item){
        return item!=null && !item.isDead && mc.theWorld.loadedEntityList.contains(item)
                && item.getEntityItem()!=null && item.getEntityItem().getItem()==net.minecraft.init.Items.gold_ingot;
    }
    private void tickGold(long now) {
        if(scoreboard!=null && scoreboard.active && scoreboard.type==ContractOffer.Type.COLLECT_GOLD_INGOTS
                && scoreboard.current!=lastGoldProgress){
            lastGoldProgress=scoreboard.current;log("Gold progress from scoreboard: "+scoreboard.current+"/"+scoreboard.target);
        }
        // Despawn/pickup is only navigation evidence, never contract progress.
        if(goldTarget!=null && !liveGold(goldTarget)){
            goldTarget=null;goldPickupRecovery.reset();PathfinderManager.clear(this,true);
            com.nezurstandalone.control.MovementKeys.release("contractor-gold");nextAction=0;
        }
        if(Utils.isInSpawn()) {lowGoldSince=0;startGrinder();return;} // Existing grinder handles the spawn exit.
        stopGrinder();
        blockedGoldTargets.entrySet().removeIf(entry -> now>=entry.getValue());
        net.minecraft.entity.item.EntityItem best=null;
        double bestScore=-Double.MAX_VALUE;
        int visible=0;
        for(Entity entity:mc.theWorld.loadedEntityList) {
            if(!(entity instanceof net.minecraft.entity.item.EntityItem))continue;
            net.minecraft.entity.item.EntityItem drop=(net.minecraft.entity.item.EntityItem)entity;
            ItemStack stack=drop.getEntityItem();
            if(!liveGold(drop))continue;
            Long retryAt=blockedGoldTargets.get(drop.getEntityId());
            if(retryAt!=null && now<retryAt)continue;
            double distance=mc.thePlayer.getDistanceToEntity(drop);
            if(distance>goldScanRadius.value)continue;
            visible++;
            int cluster=0,danger=0;
            for(Entity other:mc.theWorld.loadedEntityList) {
                if(other instanceof net.minecraft.entity.item.EntityItem && other.getDistanceToEntity(drop)<5) {
                    ItemStack nearby=((net.minecraft.entity.item.EntityItem)other).getEntityItem();
                    if(nearby!=null && nearby.getItem()==net.minecraft.init.Items.gold_ingot)cluster++;
                }
                if(other instanceof net.minecraft.entity.player.EntityPlayer && other!=mc.thePlayer
                        && other.getDistanceToEntity(drop)<4)danger++;
            }
            double score=cluster*3-distance-danger*2;
            if(score>bestScore){bestScore=score;best=drop;}
        }
        if(goldTarget==null){goldTarget=best;goldPickupRecovery.reset();nextAction=0;}
        if(goldTarget!=null){
            double distance=mc.thePlayer.getDistanceToEntity(goldTarget);
            if(distance<=2.0 || (PathfinderManager.getState()==PathfinderManager.State.COMPLETED && distance<=4.0)){
                if(PathfinderManager.isPathing())PathfinderManager.clear(this,true);
                if(mc.currentScreen==null && !mc.thePlayer.isUsingItem() && !com.nezurstandalone.input.NativeActionGate.manual()){
                    float[] rot=RotationUtils.getRotations(goldTarget,0,0,0);
                    RotationManager.getInstance().setTargetRotation(rotationOwner,RotationManager.PRIORITY_COMBAT,rot[0],rot[1],12,true);
                    boolean aligned=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(rot[0]-mc.thePlayer.rotationYaw))<25;
                    com.nezurstandalone.contract.GoldPickupRecovery.Action recovery=goldPickupRecovery.update(now,
                            goldTarget.getEntityId(),aligned,mc.thePlayer.onGround,
                            mc.thePlayer.posX,mc.thePlayer.posZ,distance);
                    if(recovery==com.nezurstandalone.contract.GoldPickupRecovery.Action.REPATH){
                        blockedGoldTargets.put(goldTarget.getEntityId(),now+8000);
                        goldTarget=null;PathfinderManager.clear(this,true);
                        com.nezurstandalone.control.MovementKeys.release("contractor-gold");nextAction=0;
                        log("Gold pickup remained blocked after two recovery attempts; rescanning another drop.");
                    } else {
                        boolean recovering=recovery!=com.nezurstandalone.contract.GoldPickupRecovery.Action.NONE;
                        if(recovering && now==goldPickupRecovery.recoveryStartedAt()){
                            stuckWatch.reset();log("Gold pickup stuck; brief sidestep/jump recovery.");
                        }
                        com.nezurstandalone.control.MovementKeys.set("contractor-gold",mc.gameSettings.keyBindForward.getKeyCode(),aligned && !recovering);
                        com.nezurstandalone.control.MovementKeys.set("contractor-gold",mc.gameSettings.keyBindLeft.getKeyCode(),recovery==com.nezurstandalone.contract.GoldPickupRecovery.Action.SIDESTEP_LEFT);
                        com.nezurstandalone.control.MovementKeys.set("contractor-gold",mc.gameSettings.keyBindRight.getKeyCode(),recovery==com.nezurstandalone.contract.GoldPickupRecovery.Action.SIDESTEP_RIGHT);
                        com.nezurstandalone.control.MovementKeys.set("contractor-gold",mc.gameSettings.keyBindJump.getKeyCode(),recovering
                                && mc.thePlayer.onGround && now-goldPickupRecovery.recoveryStartedAt()<100);
                    }
                } else {goldPickupRecovery.reset();com.nezurstandalone.control.MovementKeys.release("contractor-gold");}
            } else {
                goldPickupRecovery.reset();com.nezurstandalone.control.MovementKeys.release("contractor-gold");
                if(now>=nextAction){PathfinderManager.walkTo(this,goldTarget.posX,goldTarget.posY,goldTarget.posZ,true);nextAction=now+(long)goldRescan.value;}
            }
        } else {PathfinderManager.clear(this,true);com.nezurstandalone.control.MovementKeys.release("contractor-gold");}

        if(visible>=(int)minimumGold.value)lowGoldSince=0;
        else if(lowGoldSince==0)lowGoldSince=now;
        else if(goldLobbySwap.enabled && now-lowGoldSince>(long)(lowGoldTimeout.value*1000)) {
            cleanupNavigation();
            connection.requestSwap(now);lowGoldSince=0;
            log("Low gold density; requesting lobby swap.");move(State.LOBBY_SWITCHING);
        }
    }
    private boolean handleConnection(long now) {
        net.minecraft.client.multiplayer.ServerData server=mc.getCurrentServerData();
        if(server!=null)lastHypixelAddress=com.nezurstandalone.utils.PitLocationTracker.supportedHost(server.serverIP)?server.serverIP:null;
        if(mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected) {
            if(!autoReconnect.enabled || lastHypixelAddress==null)return false;
            if(disconnectedScreen!=mc.currentScreen) {
                disconnectedScreen=mc.currentScreen;
                connection.observe(com.nezurstandalone.contract.ContractConnectionFlow.Location.UNKNOWN,now,0);
                connection.observe(com.nezurstandalone.contract.ContractConnectionFlow.Location.DISCONNECTED,now,5000);
            }
            stopGrinder();cleanupNavigation();move(State.DISCONNECTED);
            nextAction=connection.nextAt();
            AutoReconnect other=Nezur.moduleManager.getModuleByClass(AutoReconnect.class);
            if(other!=null && other.isToggled())return true;
            if(reconnectAttempts>=3){nextAction=Long.MAX_VALUE;return true;}
            if(connection.action(now,false)==com.nezurstandalone.contract.ContractConnectionFlow.Action.RECONNECT
                    && com.nezurstandalone.control.CommandCoordinator.reconnect(this)) {
                reconnectAttempts++;connection.sent(now);
                mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(
                        new net.minecraft.client.gui.GuiMainMenu(),mc,
                        new net.minecraft.client.multiplayer.ServerData("Pit",lastHypixelAddress,false)));
            }
            return true;
        }
        if(mc.theWorld==null || mc.thePlayer==null) {
            connection.observe(com.nezurstandalone.contract.ContractConnectionFlow.Location.UNKNOWN,now,0);
            return false;
        }
        if(mc.isSingleplayer()||server==null||!com.nezurstandalone.utils.PitLocationTracker.supportedHost(server.serverIP)) {
            connection.observe(com.nezurstandalone.contract.ContractConnectionFlow.Location.UNKNOWN,now,0);
            return false;
        }
        String title=Utils.getScoreboardTitle().trim();
        boolean inPit=title.equalsIgnoreCase("THE HYPIXEL PIT");
        com.nezurstandalone.contract.ContractConnectionFlow.Location location=com.nezurstandalone.utils.PitSessionLocation.current();
        boolean inLimbo=location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LIMBO;
        boolean inLobby=location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LOBBY;
        connection.observe(location,now,(long)(lobbySwapDelay.value*1000));
        if(inPit) {
            reconnectAttempts=0;disconnectedScreen=null;
            if(!connection.swapping() && (state==State.LIMBO || state==State.LOBBY_RECONNECT
                    || state==State.JOINING_PIT || state==State.DISCONNECTED || state==State.LOBBY_SWITCHING)) {
                nextPoll=0;purchaseName=null;equipmentPurchasePending=false;attempts=0;
                cleanupNavigation();move(State.CHECKING_SCOREBOARD);
            }
            if(now>=nextMidScan) {
                nextMidScan=now+2000;networkMidCount=0;
                for(net.minecraft.entity.player.EntityPlayer player:mc.theWorld.playerEntities)
                    if(player!=mc.thePlayer && !com.nezurstandalone.module.impl.render.Focus.isHidden(player) && !player.isDead && player.getHealth()>0 && !player.isInvisible()
                            && mc.getNetHandler()!=null && mc.getNetHandler().getPlayerInfo(player.getUniqueID())!=null
                            && "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(player.posX,player.posY,player.posZ)))networkMidCount++;
            }
            boolean goldObjective=scoreboard!=null && scoreboard.active
                    && com.nezurstandalone.contract.GoldPickupRecovery.ignoresPopulation(scoreboard.type);
            if(!goldObjective && networkMidCount<((scoreboard!=null && scoreboard.active && isExecutable(scoreboard.type)
                    && scoreboard.type!=ContractOffer.Type.COLLECT_GOLD_INGOTS)?3:(int)minMidPlayers.value)){if(thinLobbySince==0)thinLobbySince=now;}
            else thinLobbySince=0;
            AutoGrinder g=grinder();
            boolean lease=g!=null && g.hasTemporaryPerkLease();
            boolean swapState=state==State.FINDING_CONTRACTOR || state==State.CHECKING_SCOREBOARD
                    || (state==State.EXECUTING_CONTRACT && (g==null || !g.isToggled()));
            if(!gappleActive && !goldObjective && lobbySwap.enabled && swapState && !lease && mc.currentScreen==null
                    && thinLobbySince>0 && now-thinLobbySince>=10000)connection.requestSwap(now);
            if(!connection.swapping())return false;
            move(State.LOBBY_SWITCHING);
        } else {
            thinLobbySince=0;
            if(!(inLobby || inLimbo) || (!autoReconnect.enabled && !connection.swapping()))return false;
            if(inLimbo)move(State.LIMBO);
            else if(state!=State.JOINING_PIT)move(State.LOBBY_RECONNECT);
        }
        closeOwnedMenu();stopGrinder();cleanupNavigation();
        com.nezurstandalone.contract.ContractCombatPolicy.clear();
        nextAction=connection.nextAt();
        if(mc.currentScreen!=null)return true;
        com.nezurstandalone.contract.ContractConnectionFlow.Action action=connection.action(now,inPit && Utils.isInSpawn());
        String command=action==com.nezurstandalone.contract.ContractConnectionFlow.Action.LEAVE?"/l":
                action==com.nezurstandalone.contract.ContractConnectionFlow.Action.JOIN?"/play pit":
                action==com.nezurstandalone.contract.ContractConnectionFlow.Action.RETURN_TO_SPAWN?"/oof":null;
        if(command!=null && com.nezurstandalone.control.CommandCoordinator.send(this,command)) {
            connection.sent(now);nextAction=connection.nextAt();
            if(action==com.nezurstandalone.contract.ContractConnectionFlow.Action.JOIN)move(State.JOINING_PIT);
            log("Session recovery: "+command);
        }
        return true;
    }
    private List<ContractOffer.Type> priority() {
        List<ContractOffer.Type> list=new ArrayList<ContractOffer.Type>(ContractSelector.DEFAULT_PRIORITY);
        if(killStreak.enabled)list.add(ContractOffer.Type.KILL_STREAK);
        Collections.sort(list,(a,b)->Double.compare(rank(a),rank(b)));
        return list;
    }
    private double rank(ContractOffer.Type type) {
        if(type==ContractOffer.Type.GOLDEN_APPLES)return goldenApplePriority.value;
        if(type==ContractOffer.Type.KILL_PLAYERS)return killPriority.value;
        if(type==ContractOffer.Type.FIST_MID_KILLS)return fistPriority.value;
        if(type==ContractOffer.Type.SNEAK_ATTACK_KILLS)return sneakPriority.value;
        if(type==ContractOffer.Type.NO_ARMOR_KILLS)return noArmorPriority.value;
        if(type==ContractOffer.Type.NO_PERK_KILLS)return noPerkPriority.value;
        if(type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW)return diamondPriority.value;
        if(type==ContractOffer.Type.CHAIN_KILLS)return chainPriority.value;
        if(type==ContractOffer.Type.CLAIM_BOUNTIES)return bountyPriority.value;
        if(type==ContractOffer.Type.COLLECT_GOLD_INGOTS)return goldPriority.value;
        if(type==ContractOffer.Type.KILL_STREAK)return streakPriority.value;
        return ContractSelector.DEFAULT_PRIORITY.indexOf(type)+1;
    }
    private void closeOwnedMenu() {
        if(armorScreen!=null) {
            if(mc.thePlayer!=null && mc.currentScreen==armorScreen)mc.thePlayer.closeScreen();
            armorScreen=null;pendingArmorSlot=-1;armorDestination=-1;com.nezurstandalone.control.GuiLease.release(this);
        }
        if(mc.thePlayer!=null && mc.currentScreen instanceof GuiChest && observedContainer==mc.thePlayer.openContainer)
            mc.thePlayer.closeScreen();
        com.nezurstandalone.control.GuiLease.release(this);
    }
    private void cleanupNavigation() {
        goldTarget=null;lastGoldProgress=-1;goldPickupRecovery.reset();blockedGoldTargets.clear();
        com.nezurstandalone.control.MovementKeys.release("contractor-gold");
        com.nezurstandalone.control.MovementKeys.release("contractor-approach");
        com.nezurstandalone.control.MovementKeys.release("contractor-gapple");
        GuardedInput.cancel(interactionOwner);
        RotationManager.getInstance().clearTarget(rotationOwner);
        PathfinderManager.clear(this,true);
        npc=null;observedContainer=null;observedMenu="";observedContents="";pendingClick=false;
    }
    private void cleanup() {
        cancelGapple();
        stuckWatch.reset();stuckRespawnUntil=0;
        restoreContractorProfile();
        restoreMidThreshold();
        connection.reset();com.nezurstandalone.control.CommandCoordinator.cancel(this);
        restorePerks();closeOwnedMenu();cleanupNavigation();
        com.nezurstandalone.contract.ContractCombatPolicy.clear();
        AutoGrinder g=grinder();
        if(grinderTouched && g!=null && !g.isRestoringTemporaryPerk()
                && g.isToggled()!=grinderWasEnabled)g.setToggled(grinderWasEnabled);
        grinderTouched=false;selected=null;offerGate.reset();scoreboard=null;
    }
    private void move(State next) {
        if(next==State.CONTRACT_COMPLETE){
            completedSidebarSuppressed=true;contractTracker.clear();
            scoreboard=new ContractScoreboard.Snapshot(true,true,false,ContractOffer.Type.UNKNOWN,0,0,-1,"");
            selected=null;selectedFallback=false;nextPoll=0;
        }
        if(state==next)return;
        state=next;stateSince=com.nezurstandalone.control.Clock.millis();
        if(next==State.NO_VILE_OFFERS)noOfferRetryAt=stateSince+300000L;
        if(next==State.FINDING_CONTRACTOR) nextAction=0;
        if(next==State.READING_MENU) {observedContainer=null;observedContents="";}
        if(debug.enabled)log("State: "+next);
    }
    private void log(String message) {
        if(!debug.enabled || mc.thePlayer==null)return;
        mc.thePlayer.addChatMessage(new ChatComponentText("§8[§dAutoContractor§8] §7"+message));
    }
    private void logImportant(String message) {
        if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(
                new ChatComponentText("§8[§dAutoContractor§8] §7"+message));
    }
}
