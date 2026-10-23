package com.nezurstandalone.engine;

import java.util.Locale;

/** Match real shop labels, not nearby instructions that merely mention prestige/upgrades. */
public final class UpgradeNpcMatcher {
    private UpgradeNpcMatcher() {}

    public static boolean matches(boolean prestige, String label) {
        String text=LoadoutPlanner.clean(label).replaceAll("\\p{Cf}", "").trim()
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (!prestige) return text.equals("UPGRADES") || text.equals("PERMANENT UPGRADES");
        return text.equals("PRESTIGE") || text.equals("PRESTIGE & RENOWN")
                || text.equals("PRESTIGE AND RENOWN") || text.equals("RESETS & RENOWN")
                || text.equals("RESETS AND RENOWN");
    }

    public static double score(double dx, double labelHeight, double dz, double playerDistanceSq) {
        double horizontal=dx*dx+dz*dz;
        // An overhead hologram belongs to the NPC immediately below it, not any villager
        // within the old ten-block sphere. Directly named villagers have height zero.
        if(horizontal>2.25 || labelHeight<0 || labelHeight>4.5 || playerDistanceSq>4096)
            return Double.POSITIVE_INFINITY;
        return horizontal*100 + labelHeight*.01 + playerDistanceSq*.001;
    }
}
