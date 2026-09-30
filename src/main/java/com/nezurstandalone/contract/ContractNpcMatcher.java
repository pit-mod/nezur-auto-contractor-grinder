package com.nezurstandalone.contract;

import java.util.Locale;

/** Match NPC labels, not instructional holograms that merely mention items. */
public final class ContractNpcMatcher {
    public static boolean matches(String kind,String label) {
        if(label==null)return false;
        String s=label.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("\\p{Cf}", "")
                .replace('\u00a0',' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if("ITEMS".equals(kind))return s.equals("ITEMS") || s.equals("NON-PERMANENT")
                || s.equals("NON-PERMANENT ITEMS");
        return s.equals("QUEST MASTER") || s.equals("QUESTS & CONTRACTS") || s.equals("QUESTS AND CONTRACTS");
    }
    public static double score(double dx,double dy,double dz,double playerDistanceSq) {
        double horizontal=dx*dx+dz*dz;
        if(horizontal>6.25 || dy<-.5 || dy>6 || playerDistanceSq>4096)return Double.POSITIVE_INFINITY;
        return horizontal*16+playerDistanceSq*.01;
    }
}
