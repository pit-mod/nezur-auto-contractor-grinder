package com.nezurstandalone.contract;

/** Explicit in-place quest evidence for Classic menus that omit transaction acknowledgements. */
public final class ClassicContractMenus {
    private ClassicContractMenus(){}
    public static boolean questStarted(String lore){
        if(lore==null)return false;
        String clean=lore.replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(java.util.Locale.ROOT);
        return clean.contains("you've already started this quest!")||clean.contains("you have already started this quest!");
    }
}
