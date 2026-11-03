package com.nezurstandalone.contract;
import java.util.List;
import java.util.Locale;
/** Fresh mystic pants only; color is deliberately irrelevant. */
public final class ContractFreshPants {
    private ContractFreshPants(){}
    public static boolean matches(boolean leggings,int count,boolean enchanted,String name,List<String> lore){
        if(!leggings||count<=0||enchanted)return false;
        String text=((name==null?"":name)+" "+String.join(" ",lore)).replaceAll("(?i)\u00a7.","").toLowerCase(Locale.ROOT);
        if(text.contains("tier")||text.contains("lives")||text.contains("dark pants")||text.contains("sewer pants"))return false;
        return text.contains("fresh")||text.contains("used in the mystic well");
    }
}
