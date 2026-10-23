package com.nezurstandalone.utils;

/** A team can use a symbol-only score entry as an invisible key between prefix and suffix. */
public final class SidebarText {
    private SidebarText() {}
    public static String entry(String name,boolean teamed) {
        if(name==null)return "";
        if(!teamed)return name;
        String plain=name.replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
        String text=plain.replaceAll("[\\p{So}\\p{Sk}\\p{Cf}\\p{M}]", "").trim();
        // Never remove real entry text/digits or alter either server-provided team fragment.
        return text.isEmpty()?name.replaceAll("[\\p{So}\\p{Sk}\\p{Cf}\\p{M}]", ""):name;
    }
}
