package com.nezurstandalone.module.impl.render;

import com.nezurstandalone.gui.GuiTheme;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.ColorSetting;
import com.nezurstandalone.settings.KeybindSetting;
import com.nezurstandalone.settings.ModeSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.Setting;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/**
 * The ClickGUI's own settings panel.
 *
 * <p>Not a feature to switch on - like the pathfinder tuning module it is
 * {@linkplain #markSettingsOnly() settings-only}, so its row opens options on either click and can
 * never be toggled. It owns the whole look and feel of the menu: the key that opens it, the accent
 * every control is tinted from, how transparent the surfaces are, the text and border treatment,
 * the panel metrics and the animation feel.
 *
 * <p>{@link GuiTheme} keeps a {@code _DEFAULT} for every themeable value and a mutable live copy;
 * {@link #apply()} recomputes the live copies from these settings, so a colour or an opacity is
 * reflected on the very next frame with no restart. Everything persists through the normal config.
 */
public class ClickGuiSettings extends Module {

    private static ClickGuiSettings INSTANCE;

    // ---- opening ------------------------------------------------------------
    private final KeybindSetting openKey = new KeybindSetting("Open Key", Keyboard.KEY_RSHIFT);

    // ---- colour -------------------------------------------------------------
    private final ColorSetting accent = new ColorSetting("Accent Color", new Color(0x2F81F7));
    private final BooleanSetting rainbow = new BooleanSetting("Rainbow Accent", false);
    private final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed", 1.0, 0.1, 5.0, 1);
    private final ColorSetting textColor = new ColorSetting("Text Color", new Color(0xECECF2));
    private final ColorSetting panelColor = new ColorSetting("Panel Color", new Color(0x0E0E12));
    private final ColorSetting borderColor = new ColorSetting("Border Color", new Color(0x272730));

    // ---- transparency -------------------------------------------------------
    private final NumberSetting panelOpacity = new NumberSetting("Panel Opacity", 91, 0, 100, 0);
    private final NumberSetting borderOpacity = new NumberSetting("Border Opacity", 100, 0, 100, 0);
    private final NumberSetting bgDim = new NumberSetting("Background Dim", 78, 0, 100, 0);
    private final NumberSetting hoverStrength = new NumberSetting("Hover Strength", 100, 0, 300, 0);

    // ---- metrics ------------------------------------------------------------
    private final NumberSetting panelWidth = new NumberSetting("Panel Width", 130, 90, 220, 0);
    private final NumberSetting paletteWidth = new NumberSetting("Settings Width", 136, 100, 260, 0);
    private final NumberSetting panelGap = new NumberSetting("Panel Gap", 7, 0, 30, 0);
    private final NumberSetting cornerRadius = new NumberSetting("Corner Radius", 4, 0, 12, 0);
    private final NumberSetting rowHeight = new NumberSetting("Row Height", 14, 10, 26, 0);
    private final NumberSetting headerHeight = new NumberSetting("Header Height", 17, 12, 30, 0);
    private final NumberSetting panelTop = new NumberSetting("Panel Top", 34, 0, 200, 0);
    private final NumberSetting panelLeft = new NumberSetting("Panel Left", 12, 0, 300, 0);

    // ---- behaviour / feel ---------------------------------------------------
    private final BooleanSetting animations = new BooleanSetting("Animations", true);
    private final NumberSetting animSpeed = new NumberSetting("Animation Speed", 1.0, 0.3, 3.0, 2);
    private final BooleanSetting showDescriptions = new BooleanSetting("Show Descriptions", true);
    private final BooleanSetting textShadow = new BooleanSetting("Text Shadow", false);
    private final BooleanSetting showShadows = new BooleanSetting("Panel Shadows", true);
    private final BooleanSetting categoryDots = new BooleanSetting("Category Colors", true);
    private final ModeSetting blurMode = new ModeSetting("Backdrop", "Dim", "Dim", "Dark", "None");

    public ClickGuiSettings() {
        super("ClickGUI", "Customise the menu: key, colours, transparency, sizing and animation.",
                Category.RENDER);
        INSTANCE = this;
        markSettingsOnly();
        addSettings(openKey,
                accent, rainbow, rainbowSpeed, textColor, panelColor, borderColor,
                panelOpacity, borderOpacity, bgDim, hoverStrength,
                panelWidth, paletteWidth, panelGap, cornerRadius, rowHeight, headerHeight,
                panelTop, panelLeft,
                animations, animSpeed, showDescriptions, textShadow, showShadows, categoryDots,
                blurMode);

        // Any themeable setting re-applies immediately, including when the config is loaded from
        // disk (the loaders go through the setters, which notify).
        for (Setting s : new Setting[]{accent, textColor, panelColor, borderColor,
                panelOpacity, borderOpacity, hoverStrength,
                panelWidth, paletteWidth, panelGap, cornerRadius, rowHeight, headerHeight,
                panelTop, panelLeft}) {
            s.addChangeListener(x -> apply());
        }
        rainbow.addChangeListener(x -> apply());
        apply();
    }

    public static ClickGuiSettings getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ theming

    /** Scales an ARGB colour's alpha by {@code mult} (0..n), clamped to a byte. */
    private static int scaleAlpha(int argb, double mult) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = (int) Math.round(a * mult);
        if (scaled < 0) scaled = 0;
        if (scaled > 255) scaled = 255;
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /** Replaces an ARGB colour's RGB while keeping its alpha. */
    private static int reTint(int argb, int rgb) {
        return (argb & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    /** Blends toward black by {@code t} (0 = unchanged, 1 = black), keeping alpha. */
    private static int dim(int argb, float t) {
        int a = argb & 0xFF000000;
        int r = (int) (((argb >> 16) & 0xFF) * (1f - t));
        int g = (int) (((argb >> 8) & 0xFF) * (1f - t));
        int b = (int) ((argb & 0xFF) * (1f - t));
        return a | (r << 16) | (g << 8) | b;
    }

    /** Recomputes every live {@link GuiTheme} value from the current settings. */
    public void apply() {
        GuiTheme.setAccent(accent.getColor().getRGB());

        double panelA = panelOpacity.value / 100.0;
        double borderA = borderOpacity.value / 100.0;
        double hoverA = hoverStrength.value / 100.0;

        int panelRgb = panelColor.getColor().getRGB();
        int borderRgb = borderColor.getColor().getRGB();
        int textRgb = textColor.getColor().getRGB();

        // Surfaces: user tint, then user transparency.
        GuiTheme.PANEL_BG = scaleAlpha(reTint(GuiTheme.PANEL_BG_DEFAULT, panelRgb), panelA);
        GuiTheme.GLASS_BODY = scaleAlpha(reTint(GuiTheme.GLASS_BODY_DEFAULT, panelRgb), panelA);
        GuiTheme.GLASS_CHIP = scaleAlpha(reTint(GuiTheme.GLASS_CHIP_DEFAULT, panelRgb), panelA);
        GuiTheme.CHIP_BG = scaleAlpha(reTint(GuiTheme.CHIP_BG_DEFAULT, panelRgb), panelA);
        GuiTheme.CHIP_BG_HOVER = scaleAlpha(reTint(GuiTheme.CHIP_BG_HOVER_DEFAULT, panelRgb), panelA);

        // Borders and separators.
        GuiTheme.PANEL_BORDER = scaleAlpha(reTint(GuiTheme.PANEL_BORDER_DEFAULT, borderRgb), borderA);
        GuiTheme.GLASS_BORDER = scaleAlpha(GuiTheme.GLASS_BORDER_DEFAULT, borderA);
        GuiTheme.SEPARATOR = scaleAlpha(GuiTheme.SEPARATOR_DEFAULT, borderA);
        GuiTheme.BORDER = GuiTheme.PANEL_BORDER;

        // Hover / selection washes.
        GuiTheme.ROW_BG_HOVER = scaleAlpha(GuiTheme.ROW_BG_HOVER_DEFAULT, hoverA);
        GuiTheme.ROW_BG_SELECTED = scaleAlpha(GuiTheme.ROW_BG_SELECTED_DEFAULT, hoverA);
        GuiTheme.HEADER_BG = scaleAlpha(GuiTheme.HEADER_BG_DEFAULT, hoverA);
        GuiTheme.MODULE_BG = GuiTheme.ROW_BG_HOVER;

        // Text: one colour, with the dimmer tiers derived so the hierarchy survives.
        GuiTheme.TEXT = reTint(GuiTheme.TEXT_DEFAULT, textRgb);
        GuiTheme.TEXT_DIM = dim(GuiTheme.TEXT, 0.22f);
        GuiTheme.TEXT_MUTED = dim(GuiTheme.TEXT, 0.44f);
        GuiTheme.TEXT_FAINT = dim(GuiTheme.TEXT, 0.62f);
        GuiTheme.SETTINGS_TEXT = GuiTheme.TEXT_DIM;

        // Metrics.
        GuiTheme.PANEL_W = (int) panelWidth.value;
        GuiTheme.PALETTE_W = (int) paletteWidth.value;
        GuiTheme.PANEL_GAP = (int) panelGap.value;
        GuiTheme.PANEL_RADIUS = (int) cornerRadius.value;
        GuiTheme.ROW_H = (int) rowHeight.value;
        GuiTheme.HEADER_H = (int) headerHeight.value;
        GuiTheme.PANEL_TOP = (int) panelTop.value;
        GuiTheme.PANEL_LEFT = (int) panelLeft.value;
    }

    /** Advances the rainbow accent. Called once a frame while the menu is open. */
    public static void tickTheme() {
        if (INSTANCE == null || !INSTANCE.rainbow.isEnabled()) {
            return;
        }
        float period = (float) Math.max(0.1, INSTANCE.rainbowSpeed.value);
        float hue = (System.currentTimeMillis() % (long) (6000 / period)) / (6000f / period);
        GuiTheme.setAccent(Color.HSBtoRGB(hue, 0.72f, 1f) | 0xFF000000);
    }

    // ---- read by the GUI ----------------------------------------------------

    public static int openKeyCode() {
        return INSTANCE == null ? Keyboard.KEY_RSHIFT : INSTANCE.openKey.code;
    }

    /** Backdrop opacity, 0..255, honouring the backdrop mode. */
    public static int backgroundAlpha() {
        if (INSTANCE == null) return 200;
        String mode = INSTANCE.blurMode.getMode();
        if ("None".equals(mode)) return 0;
        double pct = INSTANCE.bgDim.value / 100.0;
        if ("Dark".equals(mode)) pct = Math.min(1.0, pct * 1.45);
        return (int) (pct * 255.0);
    }

    public static boolean animationsOn() {
        return INSTANCE == null || INSTANCE.animations.isEnabled();
    }

    public static float animationSpeed() {
        return INSTANCE == null ? 1f : (float) INSTANCE.animSpeed.value;
    }

    public static boolean descriptionsOn() {
        return INSTANCE == null || INSTANCE.showDescriptions.isEnabled();
    }

    public static boolean textShadowOn() {
        return INSTANCE != null && INSTANCE.textShadow.isEnabled();
    }

    public static boolean shadowsOn() {
        return INSTANCE == null || INSTANCE.showShadows.isEnabled();
    }

    public static boolean categoryColorsOn() {
        return INSTANCE == null || INSTANCE.categoryDots.isEnabled();
    }
}
