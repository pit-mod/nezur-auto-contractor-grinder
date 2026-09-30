package com.nezurstandalone.utils;

public final class ProfileLookup {

    private ProfileLookup() {
    }

    public static String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        return uuid.toLowerCase();
    }

    public static String fetchUuid(String name) {
        return MojangCache.fetchUuid(name);
    }

    public static String getCleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("(?i)§[0-9A-FK-OR]", "").replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
    }
}
