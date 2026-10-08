package com.nezurstandalone.engine;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The global event announcement is not evidence that this player entered the tower. */
public final class SpireEntryPolicy {
    // Hypixel spells floors out ("Floor: ONE"). Any populated floor field is entry
    // evidence; requiring a numeric/Roman value left the grinder trying to reach mid.
    private static final Pattern FLOOR=Pattern.compile("^(?:(?:YOUR|SPIRE)\\s+)?FLOOR\\s*[:#-]?\\s*[A-Z0-9]+\\b.*$");
    private static final Pattern START=Pattern.compile("SPIRE\\s*STARTING\\s+IN\\s*(\\d{1,2}):(\\d{2})\\b");
    private SpireEntryPolicy(){}
    public static boolean inside(String title,List<String> lines){
        String heading=clean(title);
        if(heading.equals("SPIRE")||heading.startsWith("SPIRE FLOOR"))return true;
        for(String line:lines)if(FLOOR.matcher(clean(line)).matches())return true;
        return false;
    }
    public static boolean active(List<String> lines) {
        for(String line:lines)if(clean(line).replaceAll("[^A-Z]", "").contains("EVENTSPIRE"))return true;
        return false;
    }
    public static int startSeconds(String boss,int visibleTicks) {
        if(visibleTicks<=0)return -1;
        java.util.regex.Matcher match=START.matcher(clean(boss));
        if(!match.find())return -1;
        int seconds=Integer.parseInt(match.group(2));
        return seconds<60?Integer.parseInt(match.group(1))*60+seconds:-1;
    }
    private static String clean(String value){return value==null?"":value.replaceAll("§.","")
            .replace('\u00a0',' ').toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9:# -]", "").trim();}
}
