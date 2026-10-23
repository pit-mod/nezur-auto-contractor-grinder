package com.nezurstandalone.control;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Age each continuously present egg independently; ignored eggs stay ignored until removed. */
public final class StationaryEggs<K> {
    public static final long TIMEOUT_MILLIS = 25_000L;
    private final Map<K, Long> firstSeen = new HashMap<>();

    public boolean observe(K position, boolean present, long now) {
        if (!present) { firstSeen.remove(position); return false; }
        Long start = firstSeen.get(position);
        if (start == null) { start = now; firstSeen.put(position, start); }
        return now - start < TIMEOUT_MILLIS;
    }

    public void retain(Predicate<K> present) {
        firstSeen.keySet().removeIf(position -> !present.test(position));
    }

    public void clear() { firstSeen.clear(); }
}
