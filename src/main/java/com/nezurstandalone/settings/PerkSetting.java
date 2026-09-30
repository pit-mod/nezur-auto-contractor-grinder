package com.nezurstandalone.settings;

import java.util.List;
import java.util.function.Supplier;

/**
 * A perk-box dropdown. It is a {@link ModeSetting} at heart - its {@code modes} hold every possible
 * option so the config saves/loads the choice by name exactly like any other mode - but it also
 * carries a live {@link #visibleOptions} supplier: the filtered subset the dropdown should actually
 * show (perks the player's level qualifies for, minus ones already chosen in another box). The
 * marker type also lets the ClickGUI render it as an expandable list instead of a cycle pill.
 */
public class PerkSetting extends ModeSetting {

    /** Live, filtered options to display; null falls back to the full {@code modes} list. */
    public Supplier<List<String>> visibleOptions;

    /** Cycle modes on left-click instead of opening the list; right-click still opens it. */
    public boolean cycleOnLeftClick;

    public PerkSetting(String name, String defaultMode, String... allModes) {
        super(name, defaultMode, allModes);
    }

    public List<String> shown() {
        return visibleOptions != null ? visibleOptions.get() : modes;
    }
}
