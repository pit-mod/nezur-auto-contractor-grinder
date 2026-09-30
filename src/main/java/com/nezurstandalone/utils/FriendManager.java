package com.nezurstandalone.utils;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Map;

public class FriendManager {

    public static void add(String name, String uuid) {
        PlayerListStore.add(PlayerListStore.ListType.FRIEND, name, uuid);
    }

    public static void removeByName(String name) {
        PlayerListStore.removeByName(PlayerListStore.ListType.FRIEND, name);
    }

    public static Map<String, String> getFriends() {
        return PlayerListStore.get(PlayerListStore.ListType.FRIEND);
    }

    public static void clear() {
        PlayerListStore.clear(PlayerListStore.ListType.FRIEND);
    }

    public static void loadEntry(String uuidKey, String displayName) {
        PlayerListStore.loadEntry(PlayerListStore.ListType.FRIEND, uuidKey, displayName);
    }

    public static boolean isFriend(EntityPlayer player) {
        return PlayerListStore.containsPlayer(PlayerListStore.ListType.FRIEND, player);
    }

    public static boolean isFriendByName(String name) {
        return PlayerListStore.containsByName(PlayerListStore.ListType.FRIEND, name);
    }

    public static boolean isFriendByUuid(String uuid) {
        return PlayerListStore.containsByUuid(PlayerListStore.ListType.FRIEND, uuid);
    }
}
