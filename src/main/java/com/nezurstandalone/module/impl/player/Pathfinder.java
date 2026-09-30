package com.nezurstandalone.module.impl.player;

import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.pathfinder.PathfinderConfig;

/**
 * The tuning panel for the shared pathfinding engine.
 *
 * <p>Not a feature you switch on — it is the settings surface for the A* search and the walker
 * that every navigating module (Auto Care Package, the sewer camper, future combat routing)
 * runs through. Marked {@linkplain #markSettingsOnly() settings-only}, so the category row opens
 * its options on either click and can never be toggled; the switch would mean nothing.
 *
 * <p>It owns no logic. Its whole body is {@link PathfinderConfig}'s settings, which the search
 * and walker read live, so a slider dragged here retunes the very next route with no rebuild and
 * no restart. Persistence, the ClickGUI palette and the search all read the one set of objects.
 */
public class Pathfinder extends Module {

    public Pathfinder() {
        super("Pathfinder", "Tune the shared A* search & walker. Opens settings; nothing to toggle.",
                Category.PLAYER);
        markSettingsOnly();
        addSettings(PathfinderConfig.all());
    }
}
