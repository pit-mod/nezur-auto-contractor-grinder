package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.utils.ConfigPresets;
import com.nezurstandalone.utils.NotificationManager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The config manager. One card in the same idiom as a category panel: header dot, a toolbar
 * for creating and importing presets, then a scrollable list whose rows reveal their actions
 * on hover. The top bar and footer come from {@link GuiChrome}, so this screen and the module
 * browser are the same GUI wearing different content.
 */
public class ConfigGUI extends GuiScreen {

    private static final int ROW_H = 22;
    private static final int TOOLBAR_H = 16;
    private static final int BODY_PAD = 3;
    private static final int CARD_MAX_W = 430;

    /** Row actions, drawn right-aligned in this order. */
    private static final String[] ACTIONS = {"Load", "Overwrite", "Rename", "Share", "Delete"};
    private static final int[] ACTION_TINTS = {
            GuiTheme.ACCENT,      // Load
            0xFFF0A63C,           // Overwrite — amber, it replaces what is there
            0xFF58C2E0,           // Rename
            0xFF3FB950,           // Share
            GuiTheme.DANGER       // Delete
    };

    private final GuiScreen parentScreen;
    private final GuiChrome chrome = new GuiChrome(GuiChrome.Tab.CONFIGS, "Search configs");

    private final PhysicsSpring openSpring = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring scrollSpring = PhysicsSpring.iOSFluid(0f);

    private final List<PresetRow> rows = new ArrayList<>();
    /** Rows surviving the search filter. Refilled by {@link #layout()}; never reallocated. */
    private final List<PresetRow> visible = new ArrayList<>();
    private int lastVisibleCount = -1;
    private float rowCascade = 0f;
    private float scrollbarFade = 0f;
    private float targetScroll = 0f;

    private final TextInputHelper createInput = new TextInputHelper();
    private boolean typingCreate = false;
    private float createHover, createFocus, saveHover, importHover;

    private String renamingPreset = null;
    private final TextInputHelper renameInput = new TextInputHelper();

    private String sharingPreset = null;
    private float shareAnim = 0f;
    private final boolean[] shareFlags = {true, true, true, true};
    private final float[] shareToggleAnim = {1f, 1f, 1f, 1f, 1f};
    private final float[] shareRowHover = new float[4];
    private float shareCancelHover, shareCopyHover;
    private static final String[] SHARE_LABELS = {"Modules", "Friends", "Truce", "HUD positions"};
    private static final int SHARE_W = 200;
    private static final int SHARE_ROW_H = 16;

    private static final String[] FOOTER_HINTS = {
            "click a config name to rename it",
            "load applies instantly · overwrite saves over it",
            "esc returns to modules"
    };

    // Geometry from the last layout pass; hit tests reuse it so they cannot drift from
    // what is drawn.
    private int cardX, cardY, cardW, cardH;
    private int shareX, shareY, shareH;
    private int toolbarY, inputX, inputW, saveX, saveW, importX, importW;
    private int bodyTop, bodyHeight, contentHeight;

    public ConfigGUI(GuiScreen parent) {
        this.parentScreen = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        super.initGui();
        GuiExitAnimator.cancel();
        chrome.resetIntro();
        if (chrome.consumeCrossNav()) {
            // Reached by a tab click: the outgoing screen was fully drawn, so come up already
            // solid and let the content intro carry the motion. No backdrop fade = no flash.
            openSpring.snapTo(1f);
            openSpring.setTarget(1f);
        } else {
            openSpring.snapTo(0f);
            openSpring.setTarget(1f);
            openSpring.setVelocity(6.5f);
        }
        chrome.resetClock();
        rowCascade = 0f;
        lastVisibleCount = -1;
        refreshPresets();
        layout();
    }

    private void refreshPresets() {
        rows.clear();
        for (String name : ConfigPresets.listPresets()) {
            rows.add(new PresetRow(name));
        }
        lastVisibleCount = -1;
    }

    private void layout() {
        visible.clear();
        for (int i = 0; i < rows.size(); i++) {
            PresetRow row = rows.get(i);
            if (chrome.matches(row.name)) {
                visible.add(row);
            }
        }

        cardW = Math.min(CARD_MAX_W, Math.max(200, width - GuiTheme.PANEL_LEFT * 2));
        cardX = (width - cardW) / 2;
        cardY = GuiTheme.PANEL_TOP;

        toolbarY = cardY + GuiTheme.HEADER_H + 6;
        saveW = (int) GuiChrome.buttonWidth("+ Save", TOOLBAR_H);
        importW = (int) GuiChrome.buttonWidth("Import from clipboard", TOOLBAR_H);
        inputX = cardX + 8;
        inputW = Math.max(60, cardW - 16 - saveW - importW - 12);
        saveX = inputX + inputW + 6;
        importX = saveX + saveW + 6;

        bodyTop = toolbarY + TOOLBAR_H + 6;

        contentHeight = visible.isEmpty() ? 34 : visible.size() * ROW_H + BODY_PAD * 2;
        int available = Math.max(0, (height - GuiTheme.FOOTER_H - 4) - bodyTop - 5);
        bodyHeight = Math.min(contentHeight, available);
        cardH = bodyTop - cardY + bodyHeight + 5;
    }

    // --------------------------------------------------------------- rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float delta = chrome.delta();
        openSpring.update(delta);
        float appear = GuiAnim.clamp01(openSpring.getCurrentValue());

        GuiDraw.resetState();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x000000, (int) (200 * appear)));

        layout();

        if (visible.size() != lastVisibleCount) {
            lastVisibleCount = visible.size();
            rowCascade = 0f;
        }
        rowCascade = Math.min(1f, rowCascade + delta * 2.2f);

        float maxScroll = Math.max(0f, contentHeight - bodyHeight);
        targetScroll = Math.max(0f, Math.min(maxScroll, targetScroll));
        scrollSpring.setTarget(targetScroll);
        scrollSpring.update(delta);
        float scroll = scrollSpring.getCurrentValue();

        boolean scrollable = contentHeight > bodyHeight;
        boolean scrollActive = scrollable && (Math.abs(scroll - targetScroll) > 0.2f || isBodyHovered(mouseX, mouseY));
        scrollbarFade = GuiDraw.approach(scrollbarFade, scrollActive ? 1f : 0.25f, 6f, delta);

        // The card rises into place, exactly like a category panel does.
        // The card arrives on this screen's own entrance instead of the rise every screen used
        // to share, so the destination is recognisable before its heading has been read.
        GuiIntro.Style intro = chrome.introStyle();
        float content = chrome.contentIntro();
        float cardAppear = appear * GuiIntro.ease(intro, content);
        GuiIntro.begin(intro, content, cardX + cardW / 2f, cardY + cardH / 2f);

        GuiChrome.card(cardX, cardY, cardW, cardH, cardAppear, 0f);
        String counter = rows.size() == 1 ? "1 config" : rows.size() + " configs";
        GuiChrome.cardHeader(cardX, cardY, cardW, GuiTheme.ACCENT, "Configs", counter, cardAppear, 1f);

        drawToolbar(mouseX, mouseY, delta, cardAppear);
        drawList(mouseX, mouseY, delta, scroll, cardAppear);
        GuiChrome.scrollbar(cardX + cardW, bodyTop, bodyHeight, contentHeight, scroll, scrollbarFade, cardAppear);

        GuiIntro.end();

        chrome.drawTopBar(mouseX, mouseY, delta, appear);
        chrome.drawFooter(width, height, FOOTER_HINTS, null,
                rows.size() + (rows.size() == 1 ? " saved config" : " saved configs"),
                GuiTheme.ACCENT, mouseX, mouseY, delta, appear);

        drawSharePopup(mouseX, mouseY, delta, appear);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private void drawToolbar(int mouseX, int mouseY, float delta, float appear) {
        boolean inputHovered = GuiChrome.inside(mouseX, mouseY, inputX, toolbarY, inputW, TOOLBAR_H);
        createHover = GuiDraw.approach(createHover, inputHovered ? 1f : 0f, 16f, delta);
        createFocus = GuiDraw.approach(createFocus, typingCreate ? 1f : 0f, 16f, delta);
        GuiChrome.inputField(inputX, toolbarY, inputW, TOOLBAR_H, createHover, createFocus, appear);

        int textX = inputX + 6;
        int textY = toolbarY + (TOOLBAR_H - 8) / 2;
        if (typingCreate) {
            createInput.drawWithin(fontRendererObj, textX, textY, GuiDraw.withAlpha(GuiTheme.TEXT, appear), true,inputW-12);
        } else if (!createInput.getText().isEmpty()) {
            GuiDraw.textFitScaled(createInput.getText(), textX, textY, 1f, inputW - 12,
                    GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);
        } else {
            GuiDraw.textFitScaled("Name a new config...", textX, textY, 1f, inputW - 12,
                    GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, appear), false);
        }

        saveHover = GuiDraw.approach(saveHover,
                GuiChrome.inside(mouseX, mouseY, saveX, toolbarY, saveW, TOOLBAR_H) ? 1f : 0f, 16f, delta);
        importHover = GuiDraw.approach(importHover,
                GuiChrome.inside(mouseX, mouseY, importX, toolbarY, importW, TOOLBAR_H) ? 1f : 0f, 16f, delta);

        GuiChrome.button("+ Save", saveX, toolbarY, saveW, TOOLBAR_H, saveHover, GuiTheme.ACCENT, appear);
        GuiChrome.button("Import from clipboard", importX, toolbarY, importW, TOOLBAR_H, importHover, 0xFF3FB950, appear);
    }

    private void drawList(int mouseX, int mouseY, float delta, float scroll, float appear) {
        GuiDraw.rect(cardX + 1, bodyTop, cardX + cardW - 1, bodyTop + 1,
                GuiDraw.withAlpha(GuiTheme.SEPARATOR, appear));
        if (bodyHeight <= 1) {
            return;
        }

        GuiDraw.beginClip(cardX + 1, bodyTop + 1, cardW - 2, bodyHeight - 1);

        if (visible.isEmpty()) {
            String line = rows.isEmpty()
                    ? "No configs yet — name one above and hit Save"
                    : "No config matches that search";
            GuiChrome.emptyState(cardX, bodyTop + bodyHeight / 2f - 4f, cardW, line, appear);
            GuiDraw.endClip();
            return;
        }

        float rowY = bodyTop + BODY_PAD - scroll;
        boolean bodyHovered = isBodyHovered(mouseX, mouseY);
        for (int i = 0; i < visible.size(); i++) {
            PresetRow row = visible.get(i);
            row.y = Math.round(rowY);
            rowY += ROW_H;
            if (row.y + ROW_H < bodyTop || row.y > bodyTop + bodyHeight) {
                continue;
            }
            float t = GuiAnim.outCubic(GuiAnim.stagger(rowCascade, i, visible.size(), 0.45f));
            if (t <= 0.001f) {
                continue;
            }
            GuiDraw.pushTranslate(-(1f - t) * 12f, 0f);
            row.render(mouseX, mouseY, delta, bodyHovered, appear * t);
            GuiDraw.popMatrix();
        }
        GuiDraw.endClip();
    }

    /** One preset: name (or an inline rename field) on the left, action pills on the right. */
    private class PresetRow {
        final String name;
        int y;
        private float hover;
        private final float[] btnHover = new float[ACTIONS.length];
        private final int[] btnX = new int[ACTIONS.length];
        private final int[] btnW = new int[ACTIONS.length];
        private float renameHover;
        private int nameW;

        PresetRow(String name) {
            this.name = name;
        }

        void layoutButtons() {
            int bx = cardX + cardW - 8;
            for (int i = ACTIONS.length - 1; i >= 0; i--) {
                btnW[i] = (int) GuiChrome.buttonWidth(ACTIONS[i], 13f);
                bx -= btnW[i];
                btnX[i] = bx;
                bx -= 4;
            }
        }

        void render(int mouseX, int mouseY, float delta, boolean hoverable, float alpha) {
            layoutButtons();
            boolean rowHovered = hoverable && isRowHovered(mouseX, mouseY);
            hover = GuiDraw.approach(hover, rowHovered ? 1f : 0f, 16f, delta);

            GuiChrome.rowHighlight(cardX + 4, y + 1, cardW - 8, ROW_H - 2, hover * 0.85f, alpha);
            GuiChrome.rowMarker(cardX + 4.5f, y + ROW_H / 2f, ROW_H - 8f, GuiTheme.ACCENT, hover, alpha);

            float nameX = cardX + 12 + hover * 2.2f;
            float nameMax = Math.max(20f, btnX[0] - 8 - nameX);

            if (name.equals(renamingPreset)) {
                float fieldW = Math.max(60f, Math.min(180f, nameMax));
                GuiChrome.inputField(cardX + 10, y + 3, fieldW, ROW_H - 6, 0f, 1f, alpha);
                renameInput.drawWithin(fontRendererObj, cardX + 15, y + (ROW_H - 8) / 2,
                        GuiDraw.withAlpha(GuiTheme.TEXT, alpha), true,(int)fieldW-10);
            } else {
                nameW = GuiDraw.textWidth(name);
                int nameColor = GuiDraw.lerpColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, hover);
                GuiDraw.textFitScaled(name, nameX, y + (ROW_H - 8) / 2f + 0.5f, 1f, nameMax,
                        GuiDraw.withAlpha(nameColor, alpha), false);
                renameHover = GuiDraw.approach(renameHover,
                        rowHovered && GuiChrome.inside(mouseX, mouseY, nameX, y, Math.min(nameW, nameMax), ROW_H) ? 1f : 0f,
                        16f, delta);
                if (renameHover > 0.01f) {
                    // Underline hints that the name itself is the rename affordance.
                    float underlineW = Math.min(nameW, nameMax) * renameHover;
                    GuiDraw.rect(nameX, y + ROW_H / 2f + 5f, nameX + underlineW, y + ROW_H / 2f + 5.7f,
                            GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, alpha * renameHover));
                }
            }

            for (int i = 0; i < ACTIONS.length; i++) {
                boolean hovered = hoverable && GuiChrome.inside(mouseX, mouseY, btnX[i], y + 4, btnW[i], 13);
                btnHover[i] = GuiDraw.approach(btnHover[i], hovered ? 1f : 0f, 16f, delta);
                // Actions rest dim and light up with the row, so a long list stays calm.
                float visibility = 0.55f + 0.45f * hover;
                GuiChrome.button(ACTIONS[i], btnX[i], y + 4, btnW[i], 13, btnHover[i],
                        ACTION_TINTS[i], alpha * visibility);
            }
        }

        boolean isRowHovered(int mouseX, int mouseY) {
            return mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= Math.max(y, bodyTop) && mouseY < Math.min(y + ROW_H, bodyTop + bodyHeight);
        }

        /** @return index of the clicked action, or -1 */
        int actionAt(int mouseX, int mouseY) {
            // Laid out here too: a click can land before this row has ever been drawn, and
            // stale (or zeroed) button bounds would silently miss every action.
            layoutButtons();
            for (int i = 0; i < ACTIONS.length; i++) {
                if (GuiChrome.inside(mouseX, mouseY, btnX[i], y + 4, btnW[i], 13)) {
                    return i;
                }
            }
            return -1;
        }

        boolean isNameHovered(int mouseX, int mouseY) {
            layoutButtons();
            float nameX = cardX + 12;
            float nameMax = Math.max(20f, btnX[0] - 8 - nameX);
            return GuiChrome.inside(mouseX, mouseY, nameX, y, Math.min(Math.max(nameW, 20), nameMax), ROW_H);
        }
    }

    // ----------------------------------------------------------- share popup

    private void drawSharePopup(int mouseX, int mouseY, float delta, float appear) {
        shareAnim = GuiDraw.approach(shareAnim, sharingPreset != null ? 1f : 0f, 15f, delta);
        if (shareAnim < 0.01f || sharingPreset == null) {
            return;
        }
        float t = GuiDraw.easeOutCubic(shareAnim) * appear;

        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x000000, (int) (130 * t)));

        layoutSharePopup();
        int pw = SHARE_W;
        int ph = shareH;
        int px = shareX;
        int py = shareY;

        float scale = 0.92f + 0.08f * t;
        GuiDraw.pushScale(px + pw / 2f, py + ph / 2f, scale, scale);

        GuiChrome.card(px, py, pw, ph, t, 0.6f);
        GuiChrome.cardHeader(px, py, pw, 0xFF3FB950, "Share config", null, t, 1f);
        GuiDraw.textFitScaled(sharingPreset, px + 16 + GuiDraw.textWidth("Share config") + 6,
                py + (GuiTheme.HEADER_H - 8f * 0.75f) / 2f, 0.75f, pw - 110,
                GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, t), false);

        int cy = shareRowY(0);
        for (int i = 0; i < SHARE_LABELS.length; i++) {
            boolean hovered = GuiChrome.inside(mouseX, mouseY, px + 6, cy, pw - 12, SHARE_ROW_H);
            shareRowHover[i] = GuiDraw.approach(shareRowHover[i], hovered ? 1f : 0f, 16f, delta);
            shareToggleAnim[i] = GuiDraw.approach(shareToggleAnim[i], shareFlags[i] ? 1f : 0f, 14f, delta);

            GuiChrome.rowHighlight(px + 5, cy + 1, pw - 10, SHARE_ROW_H - 2, shareRowHover[i] * 0.8f, t);
            GuiDraw.text(SHARE_LABELS[i], px + 12, cy + 4f,
                    GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, shareRowHover[i]), t));
            IoSToggleRenderer.draw(px + pw - 12 - IoSToggleRenderer.TRACK_W,
                    cy + (SHARE_ROW_H - IoSToggleRenderer.TRACK_H) / 2f,
                    IoSToggleRenderer.TRACK_W, IoSToggleRenderer.TRACK_H,
                    shareToggleAnim[i], GuiTheme.TOGGLE_ON, t);
            cy += SHARE_ROW_H;
        }

        int btnY = shareButtonY();
        int cancelW = shareCancelW();
        int copyW = shareCopyW();
        shareCancelHover = GuiDraw.approach(shareCancelHover,
                GuiChrome.inside(mouseX, mouseY, px + 8, btnY, cancelW, 16) ? 1f : 0f, 16f, delta);
        shareCopyHover = GuiDraw.approach(shareCopyHover,
                GuiChrome.inside(mouseX, mouseY, px + pw - 8 - copyW, btnY, copyW, 16) ? 1f : 0f, 16f, delta);
        GuiChrome.button("Cancel", px + 8, btnY, cancelW, 16, shareCancelHover, GuiTheme.TEXT_DIM, t);
        GuiChrome.button("Copy code", px + pw - 8 - copyW, btnY, copyW, 16, shareCopyHover, 0xFF3FB950, t);

        GuiDraw.popMatrix();
    }

    /**
     * Popup geometry lives in one place. Drawing and hit testing both read it, so a tweak to
     * the layout can never leave the click targets behind where the buttons used to be.
     */
    private void layoutSharePopup() {
        shareH = GuiTheme.HEADER_H + 6 + SHARE_LABELS.length * SHARE_ROW_H + 8 + 16 + 8;
        shareX = (width - SHARE_W) / 2;
        shareY = (height - shareH) / 2;
    }

    private int shareRowY(int index) {
        return shareY + GuiTheme.HEADER_H + 6 + index * SHARE_ROW_H;
    }

    private int shareButtonY() {
        return shareY + shareH - 8 - 16;
    }

    private static int shareCancelW() {
        return (int) GuiChrome.buttonWidth("Cancel", 16f);
    }

    private static int shareCopyW() {
        return (int) GuiChrome.buttonWidth("Copy code", 16f);
    }

    private boolean sharePopupClick(int mouseX, int mouseY) {
        layoutSharePopup();
        int pw = SHARE_W;
        int ph = shareH;
        int px = shareX;
        int py = shareY;

        for (int i = 0; i < SHARE_LABELS.length; i++) {
            if (GuiChrome.inside(mouseX, mouseY, px + 6, shareRowY(i), pw - 12, SHARE_ROW_H)) {
                shareFlags[i] = !shareFlags[i];
                return true;
            }
        }

        int btnY = shareButtonY();
        int cancelW = shareCancelW();
        int copyW = shareCopyW();
        if (GuiChrome.inside(mouseX, mouseY, px + 8, btnY, cancelW, 16)) {
            sharingPreset = null;
            return true;
        }
        if (GuiChrome.inside(mouseX, mouseY, px + pw - 8 - copyW, btnY, copyW, 16)) {
            String code = ConfigPresets.exportToCode(sharingPreset, shareFlags[0], shareFlags[1],
                    shareFlags[2], shareFlags[3]);
            if (code != null) {
                GuiScreen.setClipboardString(code);
                NotificationManager.show("§aCopied code to clipboard!", 3000);
            } else {
                NotificationManager.show("§cFailed to generate code.", 3000);
            }
            sharingPreset = null;
            return true;
        }
        if (!GuiChrome.inside(mouseX, mouseY, px, py, pw, ph)) {
            sharingPreset = null;
        }
        return true;
    }

    // ------------------------------------------------------------------- input

    private boolean isBodyHovered(int mouseX, int mouseY) {
        return mouseX >= cardX && mouseX <= cardX + cardW
                && mouseY >= bodyTop && mouseY < bodyTop + bodyHeight;
    }

    /**
     * Steps straight back to the screen that opened this one. There is no fade here: the
     * player is still inside a GUI, and the screen being returned to plays its own entrance.
     */
    private void requestClose() {
        mc.displayGuiScreen(parentScreen != null ? parentScreen : new ClickGUI());
    }

    private void commitRename() {
        if (renamingPreset == null) {
            return;
        }
        String newName = renameInput.getText().trim();
        if (!newName.isEmpty() && !newName.equals(renamingPreset)) {
            if (ConfigPresets.renamePreset(renamingPreset, newName)) {
                refreshPresets();
            } else {
                // Most often the sanitised name collides with an existing preset. Saying so
                // beats the edit silently reverting.
                NotificationManager.show("§cCould not rename to §f" + newName, 3000);
            }
        }
        renamingPreset = null;
    }

    private void saveNewPreset() {
        String name = createInput.getText().trim();
        if (name.isEmpty()) {
            NotificationManager.show("§cName the config first!", 2500);
            return;
        }
        if (!ConfigPresets.exportPreset(name)) {
            // exportPreset already explains the failure; do not claim success on top of it.
            return;
        }
        refreshPresets();
        createInput.setText("");
    }

    private void importFromClipboard() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null || clip.isEmpty()) {
            NotificationManager.show("§cClipboard is empty!", 3000);
            return;
        }
        String newName = createInput.getText().trim();
        if (newName.isEmpty()) {
            newName = "Imported_" + (System.currentTimeMillis() % 10000);
        }
        if (ConfigPresets.importFromCode(clip, newName)) {
            NotificationManager.show("§aImported config: §f" + newName, 3000);
            refreshPresets();
            createInput.setText("");
        } else {
            NotificationManager.show("§cInvalid or corrupted config code!", 3000);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (sharingPreset != null) {
            sharePopupClick(mouseX, mouseY);
            return;
        }
        if (chrome.mouseClicked(mouseX, mouseY, mouseButton, mc, fontRendererObj)) {
            // Focus moved to the search field: let go of any inline edit first.
            typingCreate = false;
            commitRename();
            return;
        }

        layout();

        if (GuiChrome.inside(mouseX, mouseY, inputX, toolbarY, inputW, TOOLBAR_H)) {
            typingCreate = true;
            chrome.stopTyping();
            createInput.beginMouseSelection(mouseX, inputX + 6, fontRendererObj);
            return;
        }
        typingCreate = false;

        if (GuiChrome.inside(mouseX, mouseY, saveX, toolbarY, saveW, TOOLBAR_H)) {
            saveNewPreset();
            return;
        }
        if (GuiChrome.inside(mouseX, mouseY, importX, toolbarY, importW, TOOLBAR_H)) {
            importFromClipboard();
            return;
        }

        if (isBodyHovered(mouseX, mouseY)) {
            for (int i = 0; i < visible.size(); i++) {
                PresetRow row = visible.get(i);
                if (!row.isRowHovered(mouseX, mouseY)) {
                    continue;
                }
                int action = row.actionAt(mouseX, mouseY);
                if (action >= 0) {
                    handleAction(action, row.name);
                    return;
                }
                if (row.isNameHovered(mouseX, mouseY) && !row.name.equals(renamingPreset)) {
                    commitRename();
                    beginRename(row.name, mouseX);
                }
                return;
            }
            commitRename();
            return;
        }

        commitRename();
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void beginRename(String name, int mouseX) {
        renamingPreset = name;
        renameInput.setText(name);
        renameInput.beginMouseSelection(mouseX, cardX + 15, fontRendererObj);
    }

    private void handleAction(int action, String name) {
        switch (action) {
            case 0:
                if (ConfigPresets.importPreset(name)) {
                    NotificationManager.show("§aLoaded config §f" + name, 2500);
                }
                break;
            case 1:
                if (ConfigPresets.exportPreset(name)) {
                    NotificationManager.show("§eOverwrote config §f" + name, 2500);
                }
                break;
            case 2:
                if (!name.equals(renamingPreset)) {
                    commitRename();
                    beginRename(name, cardX + 15);
                }
                break;
            case 3:
                sharingPreset = name;
                for (int i = 0; i < shareFlags.length; i++) {
                    shareFlags[i] = true;
                    shareToggleAnim[i] = 1f;
                }
                break;
            default:
                if (ConfigPresets.deletePreset(name)) {
                    NotificationManager.show("§cDeleted config §f" + name, 2500);
                } else {
                    NotificationManager.show("§cCould not delete §f" + name, 3000);
                }
                refreshPresets();
                break;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        chrome.mouseClickMove(mouseX, clickedMouseButton, fontRendererObj);
        if (typingCreate && clickedMouseButton == 0) {
            createInput.updateMouseSelection(mouseX, inputX + 6, fontRendererObj);
        } else if (renamingPreset != null && clickedMouseButton == 0) {
            renameInput.updateMouseSelection(mouseX, cardX + 15, fontRendererObj);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        chrome.mouseReleased();
        if (typingCreate) {
            createInput.endMouseSelection();
        } else if (renamingPreset != null) {
            renameInput.endMouseSelection();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (sharingPreset != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                sharingPreset = null;
            }
            return;
        }
        if (chrome.keyTyped(typedChar, keyCode)) {
            return;
        }

        if (renamingPreset != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                renamingPreset = null;
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                commitRename();
                return;
            }
            renameInput.handleKeyTyped(typedChar, keyCode);
            return;
        }

        if (typingCreate) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                typingCreate = false;
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                saveNewPreset();
                typingCreate = false;
                return;
            }
            createInput.handleKeyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            requestClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        chrome.updateScreen();
        if (typingCreate) {
            createInput.tickRepeatKeys();
        } else if (renamingPreset != null) {
            renameInput.tickRepeatKeys();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (sharingPreset != null || !isBodyHovered(mouseX, mouseY) || contentHeight <= bodyHeight) {
            return;
        }
        float amount = wheel > 0 ? -ROW_H * 2f : ROW_H * 2f;
        targetScroll = Math.max(0f, Math.min(contentHeight - bodyHeight, targetScroll + amount));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
