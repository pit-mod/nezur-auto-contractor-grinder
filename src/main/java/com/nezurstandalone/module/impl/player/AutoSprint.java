package com.nezurstandalone.module.impl.player;

import com.nezurstandalone.control.MovementKeys;
import com.nezurstandalone.control.SessionResets;
import com.nezurstandalone.input.NativeActionGate;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AutoSprint extends Module {
    private static final String OWNER = "auto-sprint";

    public AutoSprint() {
        super("AutoSprint", "Sprints automatically while you hold forward.", Category.PLAYER);
        SessionResets.register(this, this::releaseSprint);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (!isToggled() || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead
                || !mc.inGameHasFocus || mc.currentScreen != null || mc.thePlayer.isUsingItem()
                || mc.gameSettings.keyBindSneak.isKeyDown()
                || !NativeActionGate.physical(mc.gameSettings.keyBindForward.getKeyCode())) {
            releaseSprint();
            return;
        }
        MovementKeys.holdManualSprint(OWNER);
    }

    @Override
    protected void onDisable() {
        releaseSprint();
    }

    private void releaseSprint() {
        MovementKeys.release(OWNER);
    }
}
