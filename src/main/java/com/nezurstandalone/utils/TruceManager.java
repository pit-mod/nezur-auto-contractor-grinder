package com.nezurstandalone.utils;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Map;

public class TruceManager {
    public static final String COLOR_CODE = "§9";
    public static final int COLOR_RGB = 0x5555FF;
    public static final int COLOR_ARGB = 0xFF5555FF;

    public static void add(String name, String uuid) {
        PlayerListStore.add(PlayerListStore.ListType.TRUCE, name, uuid);
    }

    public static void removeByName(String name) {
        PlayerListStore.removeByName(PlayerListStore.ListType.TRUCE, name);
    }

    public static Map<String, String> getTrucePlayers() {
        return PlayerListStore.get(PlayerListStore.ListType.TRUCE);
    }

    public static void clear() {
        PlayerListStore.clear(PlayerListStore.ListType.TRUCE);
    }

    public static void loadEntry(String uuidKey, String displayName) {
        PlayerListStore.loadEntry(PlayerListStore.ListType.TRUCE, uuidKey, displayName);
    }

    public static boolean isTruce(EntityPlayer player) {
        return PlayerListStore.containsPlayer(PlayerListStore.ListType.TRUCE, player);
    }

    public static boolean isTruceByName(String name) {
        return PlayerListStore.containsByName(PlayerListStore.ListType.TRUCE, name);
    }

    public static boolean isTruceByUuid(String uuid) {
        return PlayerListStore.containsByUuid(PlayerListStore.ListType.TRUCE, uuid);
    }
}
