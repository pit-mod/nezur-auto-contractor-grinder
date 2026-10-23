package com.nezurstandalone.module.impl.player;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.utils.Timer;

public class AutoReconnect extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (s)", 5, 1, 60, 0);
    private final com.nezurstandalone.settings.BooleanSetting lowestPlayerClassicPit = new com.nezurstandalone.settings.BooleanSetting("Lowest Player Classic Pit", true);
    private final com.nezurstandalone.control.LowestClassicPit lowestClassic=new com.nezurstandalone.control.LowestClassicPit();
    private final Timer reconnectTimer = new Timer();
    private boolean reconnecting = false;
    private Object disconnectScreen;
    private long disconnectSession;
    private GuiButton autoReconnectButton;
    private final com.nezurstandalone.contract.ContractConnectionFlow recovery=new com.nezurstandalone.contract.ContractConnectionFlow();
    private final com.nezurstandalone.utils.CarePackageTravelHold packageHold=new com.nezurstandalone.utils.CarePackageTravelHold();
    private String lastServer="mc.pitclassic.net";

    public AutoReconnect() {
        super("AutoReconnect", "Reconnects to the Pit server and rejoins Pit from lobby or Limbo.", Category.AUTO);
        addSettings(delay,lowestPlayerClassicPit);
    }
    /** Bots delegate recovery instead of racing this module with their own join commands. */
    public static boolean enableRecovery() {
        if(com.nezurstandalone.Nezur.moduleManager==null)return false;
        AutoReconnect module=com.nezurstandalone.Nezur.moduleManager.getModuleByClass(AutoReconnect.class);
        if(module==null)return false;
        if(!module.isToggled())module.setToggled(true);
        return module.isToggled();
    }
    public static boolean requestLobbySwap(Object requester,java.util.function.BooleanSupplier stillEnabled) {
        if(!stillEnabled.getAsBoolean()||holdLobbySwapForCarePackage())return false;
        if(!enableRecovery())return false;
        AutoReconnect module=com.nezurstandalone.Nezur.moduleManager.getModuleByClass(AutoReconnect.class);
        long now=com.nezurstandalone.control.Clock.millis();
        module.recovery.observe(com.nezurstandalone.utils.PitSessionLocation.current(),now,0);
        if(!module.recovery.swapping()){
            module.lowestClassic.reset();
            java.util.logging.Logger.getLogger(AutoReconnect.class.getName()).info(
                    "Lobby swap requested by "+requester.getClass().getSimpleName());
        }
        module.recovery.requestSwap(now,stillEnabled);
        return true;
    }

    /** Read live event evidence even when AutoGrinder has been disabled. */
    public static boolean holdLobbySwapForCarePackage(){
        if(com.nezurstandalone.Nezur.moduleManager==null)return false;
        AutoReconnect module=com.nezurstandalone.Nezur.moduleManager.getModuleByClass(AutoReconnect.class);
        if(module==null)return false;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        module.packageHold.context(mc.theWorld);
        boolean live=false;
        for(String line:com.nezurstandalone.utils.Utils.getScoreboardLines())
            if(com.nezurstandalone.utils.CarePackageSignal.sidebar(line)){live=true;break;}
        if(!live&&mc.theWorld!=null)for(net.minecraft.entity.Entity entity:mc.theWorld.loadedEntityList)
            if(entity instanceof net.minecraft.entity.passive.EntityChicken&&!entity.isDead
                    &&((net.minecraft.entity.passive.EntityChicken)entity).getLeashed()){live=true;break;}
        return module.packageHold.active(live,com.nezurstandalone.control.Clock.millis());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reconnecting = false;
        recovery.reset();lowestClassic.reset();rememberServer();
        if(mc.currentScreen instanceof GuiDisconnected) {
            reconnecting=true;disconnectScreen=mc.currentScreen;disconnectSession=com.nezurstandalone.control.ClientSession.current();
            reconnectTimer.reset();
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isToggled()) return;
        if (event.gui instanceof GuiDisconnected) {
            rememberServer();recovery.reset();lowestClassic.reset();
            reconnecting = true; disconnectScreen=event.gui; disconnectSession=com.nezurstandalone.control.ClientSession.current();
            reconnectTimer.reset();
            int yPos = event.gui.height / 2 + event.gui.height / 4 + 24;
            autoReconnectButton = new GuiButton(8844, event.gui.width / 2 - 100, yPos, 200, 20, "AutoReconnect");
            event.buttonList.add(autoReconnectButton);
            updateButtonText();
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (!isToggled()) return;
        if (event.gui instanceof GuiDisconnected && event.button.id == 8844) {
            reconnecting = !reconnecting;
            if (reconnecting) {
                reconnectTimer.reset();
            }
            updateButtonText();
        }
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isToggled()) return;
        if (event.gui instanceof GuiDisconnected) {
            updateButtonText();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!isToggled()) return;
        if (event.phase != TickEvent.Phase.END) return;
        rememberServer();
        net.minecraft.client.network.NetHandlerPlayClient connection=mc.getNetHandler();
        ServerData server=mc.getCurrentServerData();
        boolean classic=lowestPlayerClassicPit.isEnabled()&&server!=null
                &&com.nezurstandalone.control.LowestClassicPit.classicHost(server.serverIP)
                &&mc.thePlayer!=null&&mc.theWorld!=null&&connection!=null
                &&connection.getNetworkManager()!=null&&connection.getNetworkManager().isChannelOpen();
        lowestClassic.context(classic?connection:null,classic);
        if (mc.currentScreen instanceof GuiDisconnected) {
            if (reconnecting) {
                long delayMs = (long) (delay.value * 1000);
                if (mc.currentScreen != disconnectScreen || disconnectSession != com.nezurstandalone.control.ClientSession.current()) { reconnecting=false; return; }
                if (reconnectTimer.hasTimeElapsed(delayMs, false) && com.nezurstandalone.control.CommandCoordinator.reconnect(this)) {
                    reconnecting = false;
                    ServerData serverData = new ServerData("Pit", lastServer, false);
                    mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, serverData));
                }
            }
        } else {
            reconnecting = false;
            long now=com.nezurstandalone.control.Clock.millis();
            com.nezurstandalone.contract.ContractConnectionFlow.Location location=com.nezurstandalone.utils.PitSessionLocation.current();
            recovery.observe(location,now,classic?0:(long)(delay.value*1000));
            boolean packageActive=holdLobbySwapForCarePackage();
            if(packageActive&&location!=com.nezurstandalone.contract.ContractConnectionFlow.Location.LOBBY){
                recovery.cancelSwap();lowestClassic.reset();return;
            }
            if(mc.currentScreen!=null||mc.thePlayer==null||mc.theWorld==null)return;
            com.nezurstandalone.contract.ContractConnectionFlow.Action action=recovery.action(now,com.nezurstandalone.utils.Utils.isInSpawn());
            if(classic&&lowestClassic.tick(location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LOBBY
                            ||location==com.nezurstandalone.contract.ContractConnectionFlow.Location.PIT&&!recovery.swapping()&&com.nezurstandalone.utils.Utils.isInSpawn(),
                    com.nezurstandalone.control.GuiLease.available(this)&&!mc.thePlayer.isUsingItem(),
                    location==com.nezurstandalone.contract.ContractConnectionFlow.Location.PIT,
                    location==com.nezurstandalone.contract.ContractConnectionFlow.Location.LOBBY,
                    com.nezurstandalone.control.LowestClassicPit.currentServer(com.nezurstandalone.utils.Utils.getScoreboardLines()),now,
                    requested->{boolean sent=com.nezurstandalone.control.CommandCoordinator.send(this,requested);
                        if(sent&&!requested.equals("/play pit list"))recovery.sent(now);return sent;}))return;
            String command=action==com.nezurstandalone.contract.ContractConnectionFlow.Action.LEAVE?"/l":
                    action==com.nezurstandalone.contract.ContractConnectionFlow.Action.JOIN?"/play pit":
                    action==com.nezurstandalone.contract.ContractConnectionFlow.Action.RETURN_TO_SPAWN?"/oof":null;
            if(command!=null&&com.nezurstandalone.control.CommandCoordinator.send(this,command))recovery.sent(now);
        }
    }

    @SubscribeEvent public void onChat(ClientChatReceivedEvent event) {
        if(isToggled()&&event.type==0&&event.message!=null){
            packageHold.context(mc.theWorld);
            packageHold.message(event.message.getUnformattedText(),com.nezurstandalone.control.Clock.millis());
        }
        if(!isToggled()||!lowestPlayerClassicPit.isEnabled()||event.type!=0||event.message==null
                ||!lowestClassic.belongsTo(mc.getNetHandler()))return;
        lowestClassic.message(event.message.getUnformattedText(),com.nezurstandalone.control.Clock.millis());
    }

    private void rememberServer(){
        ServerData server=mc.getCurrentServerData();
        if(server!=null&&com.nezurstandalone.utils.PitLocationTracker.supportedHost(server.serverIP))lastServer=server.serverIP;
    }

    @Override public void onDisable(){
        reconnecting=false;recovery.reset();lowestClassic.reset();com.nezurstandalone.control.CommandCoordinator.cancel(this);super.onDisable();
    }

    private void updateButtonText() {
        if (autoReconnectButton != null) {
            if (reconnecting) {
                long delayMs = (long) (delay.value * 1000);
                long timeLeft = delayMs - reconnectTimer.getPassed();
                if (timeLeft < 0) timeLeft = 0;
                int secondsLeft = (int) Math.ceil(timeLeft / 1000.0);
                autoReconnectButton.displayString = "AutoReconnect (" + secondsLeft + ")";
            } else {
                autoReconnectButton.displayString = "AutoReconnect";
            }
        }
    }
}





