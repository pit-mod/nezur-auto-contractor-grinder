package com.nezurstandalone.utils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Shared Mojang profile lookups (UUID + nick detection) with in-memory caching.
 */
public final class MojangCache {

    public enum NickStatus {
        UNKNOWN,
        REAL,
        NICK,
        ERROR
    }

    private static final String PROFILE_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Map<String, String> UUID_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<String, NickStatus> NICK_BY_NAME = new ConcurrentHashMap<>();

    private MojangCache() {
    }

    public static String getCachedUuid(String name) {
        if (name == null) {
            return null;
        }
        return UUID_BY_NAME.get(ProfileLookup.getCleanName(name));
    }

    public static NickStatus getCachedNickStatus(String name) {
        if (name == null) {
            return NickStatus.UNKNOWN;
        }
        return NICK_BY_NAME.getOrDefault(name, NickStatus.UNKNOWN);
    }

    public static String fetchUuid(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String clean = ProfileLookup.getCleanName(name);
        String cached = UUID_BY_NAME.get(clean);
        if (cached != null) {
            return cached;
        }
        String resolved = requestUuid(name);
        if (resolved != null) {
            UUID_BY_NAME.put(clean, resolved);
        }
        return resolved;
    }

    public static void fetchUuidAsync(String name, java.util.function.Consumer<String> callback) {
        if (name == null || name.isEmpty()) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }
        String clean = ProfileLookup.getCleanName(name);
        String cached = UUID_BY_NAME.get(clean);
        if (cached != null) {
            if (callback != null) {
                callback.accept(cached);
            }
            return;
        }
        CompletableFuture.runAsync(() -> {
            String resolved = requestUuid(name);
            if (resolved != null) {
                UUID_BY_NAME.put(clean, resolved);
            }
            if (callback != null) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                if (mc != null) {
                    mc.addScheduledTask(() -> callback.accept(resolved));
                } else {
                    callback.accept(resolved);
                }
            }
        });
    }

    public static boolean isNickedUuid(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return false;
        java.util.UUID uuid = player.getUniqueID();
        return uuid != null && uuid.version() == 1;
    }

    public static void checkNickStatusAsync(String name, Consumer<NickStatus> callback) {
        checkNickStatusAsync(name, null, callback);
    }

    public static void checkNickStatusAsync(String name, java.util.UUID tabUuid, Consumer<NickStatus> callback) {
        if (name == null || name.isEmpty()) {
            if (callback != null) {
                callback.accept(NickStatus.ERROR);
            }
            return;
        }

        NickStatus cached = NICK_BY_NAME.get(name);
        if (cached != null && cached != NickStatus.UNKNOWN) {
            if (callback != null) {
                callback.accept(cached);
            }
            return;
        }

        CompletableFuture.runAsync(() -> {
            NickStatus status = requestNickStatus(name, tabUuid);
            NICK_BY_NAME.put(name, status);
            if (callback != null) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> callback.accept(status));
            }
        });
    }

    private static NickStatus requestNickStatus(String name, java.util.UUID tabUuid) {
        try {
            if (tabUuid != null) {
                int ver = tabUuid.version();
                if (ver == 1 || ver == 3) {
                    return NickStatus.NICK;
                }
            }
            String mojangUuid = requestUuid(name);
            if (mojangUuid == null || mojangUuid.isEmpty()) {
                return NickStatus.NICK;
            }
            if (tabUuid != null) {
                String cleanTabUuid = tabUuid.toString().replace("-", "");
                if (!cleanTabUuid.equalsIgnoreCase(mojangUuid)) {
                    return NickStatus.NICK;
                }
            }
            return NickStatus.REAL;
        } catch (Exception e) {
            return NickStatus.ERROR;
        }
    }

    private static String requestUuid(String name) {
        try {
            HttpURLConnection conn = openGet(PROFILE_API + name);
            try {
                if (conn.getResponseCode() != 200) {
                    return null;
                }
                try (InputStream in = conn.getInputStream(); java.util.Scanner sc = new java.util.Scanner(in)) {
                    String response = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
                    int idIndex = response.indexOf("\"id\"");
                    if (idIndex == -1) {
                        return null;
                    }
                    int start = response.indexOf("\"", idIndex + 4) + 1;
                    int end = response.indexOf("\"", start);
                    if (start <= 0 || end <= start) {
                        return null;
                    }
                    String raw = response.substring(start, end);
                    if (raw.length() != 32) {
                        return ProfileLookup.normalizeUuid(raw);
                    }
                    return raw.substring(0, 8) + "-"
                            + raw.substring(8, 12) + "-"
                            + raw.substring(12, 16) + "-"
                            + raw.substring(16, 20) + "-"
                            + raw.substring(20);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static HttpURLConnection openGet(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }
}
