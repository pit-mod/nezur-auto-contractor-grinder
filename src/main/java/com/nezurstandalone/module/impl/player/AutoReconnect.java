package com.nezurstandalone.module.impl.player;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.utils.Timer;

public class AutoReconnect extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (s)", 5, 1, 60, 0);
    private final Timer reconnectTimer = new Timer();
    private boolean reconnecting = false;
    private Object disconnectScreen;
    private long disconnectSession;
    private GuiButton autoReconnectButton;

    public AutoReconnect() {
        super("AutoReconnect", "Automatically reconnects to Pit Classic when disconnected.", Category.AUTO);
        addSettings(delay);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reconnecting = false;
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isToggled()) return;
        if (event.gui instanceof GuiDisconnected) {
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
        if (mc.currentScreen instanceof GuiDisconnected) {
            if (reconnecting) {
                long delayMs = (long) (delay.value * 1000);
                if (mc.currentScreen != disconnectScreen || disconnectSession != com.nezurstandalone.control.ClientSession.current()) { reconnecting=false; return; }
                if (reconnectTimer.hasTimeElapsed(delayMs, false) && com.nezurstandalone.control.CommandCoordinator.reconnect(this)) {
                    reconnecting = false;
                    ServerData serverData = new ServerData("Pit Classic", "mc.pitclassic.net", false);
                    mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, serverData));
                }
            }
        } else {
            reconnecting = false;
        }
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





