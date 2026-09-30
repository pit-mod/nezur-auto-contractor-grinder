package com.nezurstandalone.utils;

import java.util.*;

/** Correlates public item ownership; a match is evidence, not proof of identity. */
public final class DenickEvidence {
    public enum Status { MATCH, CONFLICT, NO_EVIDENCE, UNAVAILABLE }
    public static final class Match {
        public final String username;
        public final int itemCount;
        public final Status status;
        Match(String username, int itemCount, Status status) {
            this.username = username;
            this.itemCount = itemCount;
            this.status = status;
        }
    }
    private DenickEvidence() { }

    public static Match resolve(Collection<PitMartService.OwnerHistoryResult> histories) {
        Set<Integer> seen = new HashSet<>();
        Map<String, PitMartService.OwnerRecord> candidates = null;
        int count = 0;
        boolean unavailable = false;
        for (PitMartService.OwnerHistoryResult history : histories) {
            if (history == null || history.status == PitMartService.FetchStatus.ERROR
                    || history.status == PitMartService.FetchStatus.BLOCKED) {
                unavailable = true;
                continue;
            }
            if (!seen.add(history.nonce) || history.status != PitMartService.FetchStatus.OK || history.duped) continue;
            Map<String, PitMartService.OwnerRecord> holders = new HashMap<>();
            for (PitMartService.OwnerRecord owner : history.owners) {
                if (!owner.currentHolder) continue;
                String key = key(owner);
                if (key == null) continue;
                PitMartService.OwnerRecord old = holders.get(key);
                if (old == null || owner.seenAtMs > old.seenAtMs) holders.put(key, owner);
            }
            if (holders.isEmpty()) continue;
            count++;
            if (candidates == null) candidates = new HashMap<>(holders);
            else {
                candidates.keySet().retainAll(holders.keySet());
                for (String key : candidates.keySet()) {
                    PitMartService.OwnerRecord newest = holders.get(key);
                    if (newest.seenAtMs > candidates.get(key).seenAtMs) candidates.put(key, newest);
                }
            }
            if (candidates.isEmpty()) return new Match(null, count, Status.CONFLICT);
        }
        if (candidates != null && candidates.size() == 1) {
            String name = candidates.values().iterator().next().username;
            if (validName(name)) return new Match(name, count, Status.MATCH);
        }
        return new Match(null, count, unavailable ? Status.UNAVAILABLE : Status.NO_EVIDENCE);
    }

    private static String key(PitMartService.OwnerRecord owner) {
        if (owner.uuid != null) {
            String uuid = owner.uuid.replace("-", "").toLowerCase(Locale.ROOT);
            if (uuid.matches("[a-f0-9]{32}")) return "uuid:" + uuid;
        }
        return validName(owner.username) ? "name:" + owner.username.toLowerCase(Locale.ROOT) : null;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}")
                && !name.equalsIgnoreCase("unknown") && !name.equalsIgnoreCase("null");
    }
}
