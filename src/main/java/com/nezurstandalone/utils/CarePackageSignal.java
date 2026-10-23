package com.nezurstandalone.utils;

import java.util.*;
import java.util.regex.Pattern;

/** Server signals only: ordinary player chat, future-event timers and ordinary chests do not qualify. */
public final class CarePackageSignal {
    private static final Pattern ANNOUNCEMENT=Pattern.compile("^(?:minor event[!:]?[ ]*)?(?:\\[care package\\][ ]*|care package[!:][ ]*|(?:a )?care package (?:has (?:spawned|dropped|landed)|is (?:dropping|falling|landing)|spawned|dropped|landed|incoming)\\b).*");
    private static final Pattern FUTURE_OR_ENDED=Pattern.compile("\\b(?:next|will|starts|starting|looted|claimed|opened|ended|despawned|cancelled)\\b|\\bin \\d");
    private final LinkedHashSet<UUID> chickens=new LinkedHashSet<>();
    private boolean sidebar;
    private long lastAlert=Long.MIN_VALUE;
    private long lastScheduled=-1;
    public boolean observeScheduled(long timestamp,long wallTime,long monotonicTime,long cooldown) {
        if(timestamp<=0 || timestamp==lastScheduled || timestamp<=wallTime || timestamp-wallTime>5000)return false;
        lastScheduled=timestamp;
        return claim(monotonicTime,cooldown);
    }
    public static boolean announcement(String text) {
        String line=clean(text);
        return !FUTURE_OR_ENDED.matcher(line).find() && (ANNOUNCEMENT.matcher(line).matches()
                || line.matches("minor event[!:]? care package in [a-z].*"));
    }
    public static boolean sidebar(String text) {
        String line=clean(text);
        if(FUTURE_OR_ENDED.matcher(line).find())return false;
        return line.matches("(?:(?:minor )?event: ?)?care package(?:[ !]|$).*");
    }
    private static String clean(String text) {
        return text==null?"":text.replaceAll("\\u00a7[0-9a-fk-or]","").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
    }
    public boolean observeSidebar(boolean active,long now,long cooldown) {
        boolean fresh=active&&!sidebar;sidebar=active;
        return fresh&&claim(now,cooldown);
    }
    public boolean observeChicken(UUID id,long now,long cooldown) {
        if(!chickens.add(id))return false;
        while(chickens.size()>256)chickens.remove(chickens.iterator().next());
        return claim(now,cooldown);
    }
    public boolean claim(long now,long cooldown) {
        if(lastAlert!=Long.MIN_VALUE && now-lastAlert<cooldown)return false;
        lastAlert=now;return true;
    }
    public void reset(){chickens.clear();sidebar=false;lastAlert=Long.MIN_VALUE;lastScheduled=-1;}
}
