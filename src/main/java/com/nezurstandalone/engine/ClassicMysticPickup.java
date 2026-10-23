package com.nezurstandalone.engine;

import java.util.Locale;
import java.util.regex.Pattern;

/** Classic gives picked-up items server metadata that need not match the ground stack's NBT. */
public final class ClassicMysticPickup {
    private static final Pattern LABEL=Pattern.compile("\\b(?:fresh|mystic|tier)\\b");
    private ClassicMysticPickup(){}
    private static String clean(String text){return text==null?"":text.replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);}
    public static boolean named(String name){return LABEL.matcher(clean(name)).find();}
    public static boolean same(int item,int damage,String name,int otherItem,int otherDamage,String otherName){
        return item==otherItem&&damage==otherDamage&&named(name)&&clean(name).equals(clean(otherName));
    }
}
