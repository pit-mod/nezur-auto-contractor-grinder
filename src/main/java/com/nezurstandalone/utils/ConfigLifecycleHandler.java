package com.nezurstandalone.utils;

import com.nezurstandalone.Nezur;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Applies module on/off from config after the client is in a world, so startup hooks
 * (e.g. Discord IPC) do not fail before the game is ready.
 */
public class ConfigLifecycleHandler {

    private static boolean appliedLoadedStates;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (appliedLoadedStates || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (net.minecraft.client.Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        appliedLoadedStates = true;
        Nezur.moduleManager.applyLoadedModuleStates();
    }
}
