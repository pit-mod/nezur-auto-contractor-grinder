package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.*;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A single module row inside a category panel: name on the left, keybind hint and an
 * iOS-style toggle on the right. Clicking the name selects the module so its options open
 * in the palette; clicking the toggle flips it.
 */
public class ModuleButton {

    private static final int NAME_INSET = 9;
    private static final int TOGGLE_MARGIN = 8;
    private static final long TOGGLE_DEBOUNCE_MS = 120L;

    public final Module module;
    public int x, y, width, height;

    /** Built once and reused by the palette. */
    public final List<Component> components = new ArrayList<>();

    private final String animKey;
    private long lastToggleClickMs = 0L;

    private final PhysicsSpring toggleSpring = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring hoverSpring = PhysicsSpring.iOSFluid(0f);
    private float selectAnim;

    /** Click feedback: a brief lift of the row's own highlight, nothing drawn on top of it. */
    private float press;

    public ModuleButton(Module module, String frameKey, int x, int y, int width, int height) {
        this.module = module;
        this.animKey = frameKey + "/" + module.getName();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.toggleSpring.snapTo(module.isToggled() ? 1f : 0f);
        rebuildComponents();
    }

    public void rebuildComponents() {
        components.clear();
        for (Setting s : module.settings) {
            if ("X Pos".equals(s.name) || "Y Pos".equals(s.name)) {
                continue;
            }
            if (!s.isVisible()) {
                continue; // hidden settings (e.g. perk boxes while Auto Perk is off) draw no row
            }
            Component comp = null;
            if (s instanceof com.nezurstandalone.settings.PerkSetting) comp = new PerkDropdownComponent((com.nezurstandalone.settings.PerkSetting) s);
            else if (s instanceof BooleanSetting) comp = new BooleanComponent((BooleanSetting) s);
            else if (s instanceof RangeSetting) comp = new RangeSliderComponent((RangeSetting) s);
            else if (s instanceof NumberSetting) comp = new SliderComponent((NumberSetting) s);
            else if (s instanceof ModeSetting) comp = new ModeComponent((ModeSetting) s);
            else if (s instanceof ColorSetting) comp = new ColorComponent((ColorSetting) s);
            else if (s instanceof KeybindSetting) comp = new KeybindComponent((KeybindSetting) s);
            else if (s instanceof InputSetting) comp = new InputComponent((InputSetting) s);
            else if (s instanceof ButtonSetting) comp = new ButtonComponent((ButtonSetting) s);
            if (comp != null) {
                comp.setAnimPrefix(animKey + "/" + s.name);
                components.add(comp);
            }
        }
    }

    public void updatePosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        // Name only: matching descriptions too surfaces confusing hits (searching "esp"
        // would pull in unrelated modules whose blurb happens to mention it).
        String q = query.toLowerCase(Locale.ROOT).trim();
        return module.getName().toLowerCase(Locale.ROOT).contains(q);
    }

    public String getAnimKey() {
        return animKey;
    }

    public int getHeight() {
        return height;
    }

    // ---------------------------------------------------------------- rendering

    public void render(int mouseX, int mouseY, boolean selected, boolean hoverable, float delta, float alpha) {
        boolean enabled = module.isToggled();
        boolean hovered = hoverable && isRowHovered(mouseX, mouseY);

        toggleSpring.setTarget(enabled ? 1f : 0f);
        hoverSpring.setTarget(hovered ? 1f : 0f);
        toggleSpring.update(delta);
        hoverSpring.update(delta);
        selectAnim = GuiAnim.approach(selectAnim, selected ? 1f : 0f, 30f, delta);
        if (!selected && selectAnim < 0.025f) selectAnim = 0f;

        float toggleAnim = toggleSpring.getCurrentValue();
        float hoverAnim = GuiAnim.clamp01(hoverSpring.getCurrentValue());
        selectAnim = GuiAnim.clamp01(selectAnim);

        press = Math.max(0f, press - delta * 5.5f);

        float bgStrength = Math.max(Math.max(hoverAnim * 0.60f, selectAnim * 0.78f), press * 0.65f);
        if (bgStrength > 0.01f) {
            int bg = GuiDraw.lerpColor(GuiTheme.ROW_BG, GuiTheme.ROW_BG_SELECTED, bgStrength);
            GuiDraw.roundedRect(x + 3, y + 1, x + width - 3, y + height - 1, 3f,
                    GuiDraw.withAlpha(bg, alpha));
        }


        if (selectAnim > 0.01f) {
            int accent = GuiTheme.categoryColor(module.getCategory());
            float barH = (height - 6) * Math.min(1f, selectAnim);
            float barY = y + (height - barH) / 2f;
            // The marker is deliberately crisp. The old glow bled into adjacent rows and
            // made a right-click selection look as though the row had jumped.
            GuiDraw.roundedRect(x + 4f, barY, x + 5.5f, barY + barH, 0.75f,
                    GuiDraw.withAlpha(accent, alpha * 0.92f));
        }

        float toggleX = x + width - TOGGLE_MARGIN - IoSToggleRenderer.TRACK_W;
        float toggleY = y + (height - IoSToggleRenderer.TRACK_H) / 2f;

        String keyLabel = getKeybindLabel();
        float keyScale = 0.7f;
        float keyW = GuiDraw.textWidthScaled(keyLabel, keyScale);
        float keyX = toggleX - 6f - keyW;
        GuiDraw.textScaled(keyLabel, keyX, y + (height - 8f * keyScale) / 2f - 0.5f, keyScale,
                GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, alpha), false);

        int nameColor = enabled ? GuiTheme.TEXT : GuiTheme.TEXT_DIM;
        nameColor = GuiDraw.lerpColor(nameColor, GuiTheme.TEXT, hoverAnim * 0.6f);
        if (module.isDangerous() && enabled) {
            nameColor = GuiDraw.lerpColor(nameColor, GuiTheme.DANGER, 0.55f);
        }
        // Keep text fixed; sliding labels during selection made palette changes look unstable.
        float nameX = x + NAME_INSET;
        float nameMax = Math.max(10f, keyX - 5f - nameX);
        GuiDraw.textFitScaled(module.getName(), nameX, y + (height - 8) / 2f + 0.5f, 1f, nameMax,
                GuiDraw.withAlpha(nameColor, alpha), false);

        if (module.isSettingsOnly()) {
            // No switch: the shared chevron, whose openness rotates it from pointing right
            // (closed, 0) to pointing down (open, 1). Driven straight off the clamped select
            // spring - no extra easing on top, which is what made the earlier caret overshoot
            // past vertical and jitter. The spring's own settle carries the motion.
            float cx = toggleX + IoSToggleRenderer.TRACK_W / 2f;
            float cy = y + height / 2f;
            int caretColor = GuiDraw.withAlpha(GuiDraw.lerpColor(
                    GuiDraw.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT_DIM, hoverAnim),
                    GuiTheme.ACCENT, selectAnim), alpha);
            GuiDraw.chevron(cx, cy, 5.0f, selectAnim, caretColor);
        } else {
            IoSToggleRenderer.draw(toggleX, toggleY, IoSToggleRenderer.TRACK_W, IoSToggleRenderer.TRACK_H,
                    toggleAnim, GuiTheme.TOGGLE_ON, alpha);
        }
    }

    private String getKeybindLabel() {
        int code = module.keybind.code;
        if (code < 0) {
            return "MB" + (code + 100);
        }
        if (code == Keyboard.KEY_NONE) {
            return "-";
        }
        String name = Keyboard.getKeyName(code);
        return name == null || name.isEmpty() ? "-" : name;
    }

    // ------------------------------------------------------------------- input

    public boolean isRowHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + height;
    }

    /** Acknowledges a click on this row, whichever button it was. */
    public void press() {
        press = 1f;
    }


    public void toggleModule() {
        if (module.isSettingsOnly()) {
            return; // click is handled by the frame as an options-open, not a toggle.
        }
        long now = GuiAnim.millis();
        if (now - lastToggleClickMs < TOGGLE_DEBOUNCE_MS) {
            return;
        }
        lastToggleClickMs = now;
        module.toggle();
        // Fluid motion is interruptible but never overshoots the switch track.
        toggleSpring.setVelocity(0f);
    }

    public static String getDisplayLabel(Module module) {
        return module.getName();
    }

    // ------------------------------------------------------- settings drawer
    // The ClickGUI shows settings in the palette, but the HUD editor still wants a
    // self-contained drawer it can drop into a popup. These helpers drive that.

    private int settingsX, settingsY, settingsW;

    public int getSettingsHeight() {
        int total = 0;
        for (Component comp : components) {
            total += GuiTheme.ROW_H + comp.getExtraHeight();
        }
        return total;
    }

    /** Rebuilds the component list if the set of visible settings changed (live show/hide). */
    private String lastVisibleSig = null;
    public void refreshVisibility() {
        StringBuilder sb = new StringBuilder();
        for (Setting s : module.settings) {
            if (s.isVisible()) sb.append(s.name).append(';');
        }
        String sig = sb.toString();
        if (!sig.equals(lastVisibleSig)) {
            lastVisibleSig = sig;
            rebuildComponents();
        }
    }

    public void layoutSettings(int x, int y, int width) {
        refreshVisibility();
        this.settingsX = x;
        this.settingsY = y;
        this.settingsW = width;
        int offset = 0;
        for (Component comp : components) {
            comp.setRowAnim(1f, 0f);
            int h = GuiTheme.ROW_H + comp.getExtraHeight();
            comp.updatePosition(x, y + offset, width, h);
            offset += h;
        }
    }

    public void renderSettings(int mouseX, int mouseY) {
        for (Component comp : components) {
            comp.render(mouseX, mouseY);
        }
    }

    public boolean isSettingsHovered(int mouseX, int mouseY) {
        return mouseX >= settingsX && mouseX <= settingsX + settingsW
                && mouseY >= settingsY && mouseY < settingsY + getSettingsHeight();
    }

    public void settingsMouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (Component comp : components) {
            comp.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    public void settingsMouseReleased(int mouseX, int mouseY, int state) {
        for (Component comp : components) {
            comp.mouseReleased(mouseX, mouseY, state);
        }
    }

    public void settingsMouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        for (Component comp : components) {
            comp.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
        }
    }

    public void settingsKeyTyped(char typedChar, int keyCode) {
        for (Component comp : components) {
            comp.keyTyped(typedChar, keyCode);
        }
    }
}
