package com.nezurstandalone.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-only nick -> real name cache. Cleared when the game closes; never written to disk.
 */
public final class DenickManager {

    private DenickManager() {
    }

    private static final Map<String, String> denickedPlayers = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong GENERATION = new java.util.concurrent.atomic.AtomicLong();
    private static final Map<String, Integer> EVIDENCE = new ConcurrentHashMap<>();
    private static final Map<String, Long> MATCHED_AT = new ConcurrentHashMap<>();

    public static long generation() { return GENERATION.get(); }

    public static int evidenceCount(String nick) {
        return nick == null ? 0 : EVIDENCE.getOrDefault(nick.toLowerCase(Locale.ROOT), 0);
    }

    public static void putMatched(String nick, String realUsername, int itemCount) {
        if (!DenickEvidence.validName(nick) || !DenickEvidence.validName(realUsername)
                || nick.equalsIgnoreCase(realUsername) || itemCount < 1) return;
        put(nick, realUsername);
        EVIDENCE.put(nick.toLowerCase(Locale.ROOT), itemCount);
    }

    public static void put(String nick, String realUsername) {
        if (!DenickEvidence.validName(nick) || !DenickEvidence.validName(realUsername) || nick.equalsIgnoreCase(realUsername)) {
            return;
        }
        removeByNick(nick);
        denickedPlayers.put(nick, realUsername);
        MATCHED_AT.put(nick.toLowerCase(Locale.ROOT), System.currentTimeMillis());
    }

    public static String get(String nick) {
        if (nick == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : denickedPlayers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(nick)) {
                long age = System.currentTimeMillis() - MATCHED_AT.getOrDefault(nick.toLowerCase(Locale.ROOT), 0L);
                if (age < 0 || age >= 600000L) {
                    removeByNick(nick);
                    return null;
                }
                return entry.getValue();
            }
        }
        return null;
    }

    public static boolean contains(String nick) {
        return get(nick) != null;
    }

    public static void removeByNick(String nick) {
        if (nick == null) {
            return;
        }
        EVIDENCE.remove(nick.toLowerCase(Locale.ROOT));
        MATCHED_AT.remove(nick.toLowerCase(Locale.ROOT));
        denickedPlayers.entrySet().removeIf(entry -> entry.getKey().equalsIgnoreCase(nick));
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        EVIDENCE.clear();
        MATCHED_AT.clear();
        denickedPlayers.clear();
    }

    public static boolean isEmpty() {
        return denickedPlayers.isEmpty();
    }

    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(denickedPlayers));
    }
}
