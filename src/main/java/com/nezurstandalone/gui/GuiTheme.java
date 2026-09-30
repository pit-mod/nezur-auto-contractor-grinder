package com.nezurstandalone.gui;

import com.nezurstandalone.module.Category;

/**
 * Central palette and metrics for the ClickGUI. Dark neutral surfaces, one blue accent,
 * and a per-category hue used only for the small header dot.
 */
public final class GuiTheme {
    private GuiTheme() {}

    /** Retints the accent and everything derived from it. */
    public static void setAccent(int argb) {
        ACCENT = argb;
        ACCENT_MUTED = (argb & 0x00FFFFFF) | 0x99000000;
        TOGGLE_ON = argb;
    }

    // --- Accent ---------------------------------------------------------------
    // Not final: the ClickGUI module retints these live from a colour picker. Everything that
    // reads GuiTheme.ACCENT therefore follows the user's choice with no restart.
    public static final int ACCENT_DEFAULT = 0xFF2F81F7;
    public static int ACCENT = ACCENT_DEFAULT;
    public static int ACCENT_MUTED = 0x992F81F7;
    public static final int DANGER = 0xFFF0544C;

    // --- Surfaces -------------------------------------------------------------
    public static final int PANEL_BG_DEFAULT = 0xE80E0E12;
    public static int PANEL_BG = PANEL_BG_DEFAULT;
    public static final int PANEL_BORDER_DEFAULT = 0xFF272730;
    public static int PANEL_BORDER = PANEL_BORDER_DEFAULT;
    public static final int HEADER_BG_DEFAULT = 0x0FFFFFFF;
    public static int HEADER_BG = HEADER_BG_DEFAULT;
    public static int BORDER = PANEL_BORDER;
    public static final int SEPARATOR_DEFAULT = 0x14FFFFFF;
    public static int SEPARATOR = SEPARATOR_DEFAULT;

    public static final int ROW_BG = 0x00000000;
    public static final int ROW_BG_HOVER_DEFAULT = 0x14FFFFFF;
    public static int ROW_BG_HOVER = ROW_BG_HOVER_DEFAULT;
    public static final int ROW_BG_SELECTED_DEFAULT = 0x24FFFFFF;
    public static int ROW_BG_SELECTED = ROW_BG_SELECTED_DEFAULT;
    public static int MODULE_BG = ROW_BG_HOVER;

    public static final int CHIP_BG_DEFAULT = 0xE616161C;
    public static int CHIP_BG = CHIP_BG_DEFAULT;
    public static final int CHIP_BG_HOVER_DEFAULT = 0xF0232330;
    public static int CHIP_BG_HOVER = CHIP_BG_HOVER_DEFAULT;

    // --- Liquid glass ---------------------------------------------------------
    // A pane is four things stacked: a translucent body, light falling through it, a bright
    // catch along the top edge and a shadow grounding the bottom. Leave any one of them out
    // and the surface stops reading as glass - a flat translucent fill in particular reads as
    // a hole punched in the screen rather than as something lying on top of it.

    /** Body of a panel-sized pane. Deliberately not opaque; the sheen supplies the volume. */
    public static final int GLASS_BODY_DEFAULT = 0xB2141419;
    public static int GLASS_BODY = GLASS_BODY_DEFAULT;
    /** Body of a chip-sized pane, lighter so small controls keep their edges legible. */
    public static final int GLASS_CHIP_DEFAULT = 0x9C1B1B22;
    public static int GLASS_CHIP = GLASS_CHIP_DEFAULT;
    /** Light through the pane, strongest along the top and gone by the middle. */
    public static final int GLASS_SHEEN = 0x24FFFFFF;
    /** The bright edge catch that gives the pane a measurable thickness. */
    public static final int GLASS_SPECULAR = 0x42FFFFFF;
    /** Shade along the bottom edge, so the pane sits on the background instead of floating. */
    public static final int GLASS_BASE = 0x4D000000;
    public static final int GLASS_BORDER_DEFAULT = 0x45555560;
    public static int GLASS_BORDER = GLASS_BORDER_DEFAULT;
    /** The slow travelling highlight. Very low alpha - it should be felt, not watched. */
    public static final int GLASS_SWEEP = 0x14FFFFFF;

    // Settings drawer surfaces are flat now — the palette itself provides the frame.
    public static final int SETTINGS_CONTAINER_BG = 0x00000000;
    public static final int SETTINGS_CONTAINER_BORDER = 0x00000000;

    // --- Toggle ---------------------------------------------------------------
    public static final int TOGGLE_OFF_DEFAULT = 0xFF3A3A44;
    public static int TOGGLE_OFF = TOGGLE_OFF_DEFAULT;
    public static int TOGGLE_ON = ACCENT;
    public static final int TOGGLE_KNOB = 0xFFFFFFFF;

    // --- Text -----------------------------------------------------------------
    public static final int TEXT_DEFAULT = 0xFFECECF2;
    public static int TEXT = TEXT_DEFAULT;
    public static final int TEXT_DIM_DEFAULT = 0xFFB4B4C0;
    public static int TEXT_DIM = TEXT_DIM_DEFAULT;
    public static final int TEXT_MUTED_DEFAULT = 0xFF83838F;
    public static int TEXT_MUTED = TEXT_MUTED_DEFAULT;
    public static final int TEXT_FAINT_DEFAULT = 0xFF5C5C68;
    public static int TEXT_FAINT = TEXT_FAINT_DEFAULT;
    public static int SETTINGS_TEXT = TEXT_DIM;

    // --- Metrics --------------------------------------------------------------
    public static final int PADDING_X = 6;
    public static final int PADDING_Y = 4;
    public static final int ROW_H_DEFAULT = 14;
    public static int ROW_H = ROW_H_DEFAULT;
    public static final int HEADER_H_DEFAULT = 17;
    public static int HEADER_H = HEADER_H_DEFAULT;

    public static int PANEL_W = 130;
    public static int PANEL_GAP = 7;
    public static int PANEL_RADIUS = 4;
    public static final int PANEL_TOP_DEFAULT = 34;
    public static int PANEL_TOP = PANEL_TOP_DEFAULT;
    public static final int PANEL_LEFT_DEFAULT = 12;
    public static int PANEL_LEFT = PANEL_LEFT_DEFAULT;
    public static final int FOOTER_H = 20;

    public static final int PALETTE_W_DEFAULT = 136;
    public static int PALETTE_W = PALETTE_W_DEFAULT;

    /** Hue for a category's header dot. Everything else stays neutral. */
    public static int categoryColor(Category category) {
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.categoryColorsOn()) {
            return ACCENT; // one accent everywhere instead of per-category hues
        }
        if (category == null) {
            return TEXT_MUTED;
        }
        switch (category) {
            case AUTO:   return 0xFFF0544C; // red
            case PLAYER:   return 0xFFF0A63C; // amber
            case RENDER:   return 0xFFA855F7; // violet
            case SWAPPING: return 0xFF3FB950; // green
            case MISC:     return 0xFF58C2E0; // cyan
            default:       return 0xFFC4C4D0;
        }
    }
}
