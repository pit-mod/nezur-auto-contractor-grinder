package com.nezurstandalone.control;
import java.util.regex.*;
/** Narrow supported grammars. A parsed system-channel message is NOT proof of provenance. */
public final class ServerMessages {
    private static final Pattern PRESTIGE=Pattern.compile("^PRESTIGE! ([A-Za-z0-9_]{1,16}) unlocked prestige ([IVXLCDM]+|[0-9]+)!?$");
    private static final Pattern QUEST=Pattern.compile("^NIGHT QUEST! Kill ([0-9]+) players[.!]?$");
    private static final Pattern DONE=Pattern.compile("^(?:NIGHT QUEST! )?DONE! \\+[0-9,]+ XP!(?: \\+[0-9]+ Chunks? of Vile)?$");
    private static final Pattern DROP=Pattern.compile("^(RARE DROP!|MYSTIC DROP!|FRESH DROP!) ([A-Za-z0-9 ()!+'-]{1,120})$",Pattern.CASE_INSENSITIVE);
    public static boolean throttle(String text){return "Woah there, slow down!".equals(text);}
    public static boolean prestige(String text,String player){Matcher m=PRESTIGE.matcher(text);return player!=null&&m.matches()&&m.group(1).equalsIgnoreCase(player);}
    public static boolean questStart(String text){return QUEST.matcher(text).matches();}
    public static boolean questDone(String text){return DONE.matcher(text).matches();}
    private static final Pattern CLASSIC_MYSTIC=Pattern.compile("^MYSTIC ITEM! dropped from killing \\[([0-9]{1,3})\\]\\s*([A-Za-z0-9_]{1,16})!?$",Pattern.CASE_INSENSITIVE);
    public static boolean mystic(String text){
        if(text==null)return false;
        if(CLASSIC_MYSTIC.matcher(text).matches())return true;
        Matcher m=DROP.matcher(text);return m.matches()&&Pattern.compile("\\b(MYSTIC|FRESH)\\b",Pattern.CASE_INSENSITIVE).matcher(m.group(2)).find();
    }
}
