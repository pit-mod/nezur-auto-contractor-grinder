package com.nezurstandalone.engine;

import java.util.Locale;
import java.util.regex.*;

/** Shared menu vocabulary used by dispatch, packet observation and post-purchase routing. */
public final class LoadoutMenus {
    private static final Pattern SLOT=Pattern.compile("^(Perk|Killstreak) Slot #([1-4])$",Pattern.CASE_INSENSITIVE);
    private LoadoutMenus(){}
    public static String kind(String title) {
        String name=LoadoutPlanner.clean(title).toLowerCase(Locale.ROOT).trim();
        if(name.contains("choose a killstreak"))return "chooseStreak";
        if(name.contains("choose a perk"))return "choosePerk";
        if(name.contains("permanent upgrades"))return "upgrades";
        if(name.contains("prestige & renown")||name.contains("resets & renown"))return "prestige";
        if(name.contains("are you sure")||name.equals("prestige?"))return "confirm";
        if(name.contains("killstreak"))return "killstreaks";
        return "unknown";
    }
    public static int slotNumber(LoadoutPlanner.Kind kind,String name) {
        Matcher m=SLOT.matcher(LoadoutPlanner.clean(name).trim());if(!m.matches())return -1;
        // Classic uses Perk Slot labels in its killstreak root as well.
        if(kind==LoadoutPlanner.Kind.PERK&&!m.group(1).equalsIgnoreCase("Perk"))return -1;
        int n=Integer.parseInt(m.group(2));return n<=(kind==LoadoutPlanner.Kind.PERK?4:2)?n:-1;
    }
    public static String[] afterSelection(LoadoutPlanner.Kind kind) {
        return new String[]{"confirm","upgrades","killstreaks",kind==LoadoutPlanner.Kind.PERK?"choosePerk":"chooseStreak"};
    }
    public static boolean responseEvidence(String kind,String desired,String name,java.util.List<String> lore) {
        if(kind.equals("confirm"))return LoadoutPlanner.clean(name).trim().equalsIgnoreCase("Confirm");
        if(kind.equals("choosePerk")||kind.equals("chooseStreak"))
            return LoadoutPlanner.normalize(LoadoutPlanner.clean(name).trim()).equalsIgnoreCase(LoadoutPlanner.normalize(desired));
        LoadoutPlanner.Kind type=kind.equals("upgrades")?LoadoutPlanner.Kind.PERK:
                kind.equals("killstreaks")?LoadoutPlanner.Kind.KILLSTREAK:null;
        return type!=null&&slotNumber(type,name)>0&&LoadoutPlanner.resolved(lore);
    }
    public static String[] afterConfirmation(LoadoutPlanner.Kind kind) {
        return new String[]{"upgrades","killstreaks",kind==LoadoutPlanner.Kind.PERK?"choosePerk":"chooseStreak"};
    }
}
