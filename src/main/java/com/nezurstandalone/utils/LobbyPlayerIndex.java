package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a per-tick name -> entity map so list/ESP modules avoid O(n²) player scans.
 */
public final class LobbyPlayerIndex {

    public static final LobbyPlayerIndex INSTANCE = new LobbyPlayerIndex();

    private static Map<String, EntityPlayer> byCleanName = Collections.emptyMap();
    private static Map<String, EntityPlayer> byDisplayName = Collections.emptyMap();

    private LobbyPlayerIndex() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.theWorld.playerEntities == null) {
                byCleanName = Collections.emptyMap();
                byDisplayName = Collections.emptyMap();
                return;
            }

            Map<String, EntityPlayer> clean = new HashMap<>();
            Map<String, EntityPlayer> display = new HashMap<>();
            for (EntityPlayer player : new java.util.ArrayList<>(mc.theWorld.playerEntities)) {
                if (player == null || player.getName() == null) {
                    continue;
                }
                display.put(player.getName().toLowerCase(), player);
                clean.put(ProfileLookup.getCleanName(player.getName()).toLowerCase(), player);
            }
            byCleanName = clean;
            byDisplayName = display;
        } catch (Throwable ignored) {
            // Guard against AFK entity list mutation crashes
        }
    }

    public static EntityPlayer findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        EntityPlayer p = byDisplayName.get(name.toLowerCase());
        if (p != null) {
            return p;
        }
        return byCleanName.get(ProfileLookup.getCleanName(name).toLowerCase());
    }

    public static Map<String, EntityPlayer> snapshotByDisplayName() {
        return byDisplayName;
    }
}
