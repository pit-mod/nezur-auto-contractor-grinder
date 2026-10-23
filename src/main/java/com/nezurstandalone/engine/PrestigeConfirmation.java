package com.nezurstandalone.engine;

import java.util.List;

/** Original Hypixel's server-populated Prestige? menu does not require a click ACK. */
public final class PrestigeConfirmation {
    private PrestigeConfirmation() { }

    public static boolean serverMenu(boolean classic,String sourceKind,String... expected) {
        return !classic && "prestige".equals(sourceKind) && expected.length==1 && "confirm".equals(expected[0]);
    }

    public static boolean hypixelButton(String title,String name,int item,int metadata,List<String> lore) {
        if(!"confirm".equals(LoadoutMenus.kind(title)) || item!=159 || metadata!=13
                || !"ARE YOU SURE?".equalsIgnoreCase(LoadoutPlanner.clean(name).trim()))return false;
        boolean nextPrestige=false,reset=false,click=false;
        for(String line:lore) {
            String clean=LoadoutPlanner.clean(line).trim();
            if(clean.startsWith("New prestige:") && clean.length()>"New prestige:".length())nextPrestige=true;
            if(clean.equalsIgnoreCase("RESETTING LEVEL!"))reset=true;
            if(clean.equalsIgnoreCase("Click to prestige!"))click=true;
        }
        return nextPrestige && reset && click;
    }
}
