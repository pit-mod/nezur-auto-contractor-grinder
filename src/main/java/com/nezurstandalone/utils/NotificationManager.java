package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * On-screen toast notifications (bottom-right, PitX-style). Not chat spam.
 */
public final class NotificationManager {

    private static final int MAX_TOASTS = 7;
    private static final List<Toast> ACTIVE = new ArrayList<>();
    private static boolean suppressModuleToggle;

    private NotificationManager() {
    }

    public static void show(String message, int durationMs) {
        if (message == null || message.isEmpty()) {
            return;
        }
        int duration = Math.max(1200, durationMs);
        ACTIVE.add(0, new Toast(message, duration, accentFromMessage(message)));
        trim();
    }

    public static void showModuleToggle(String moduleName, boolean enabled) {
        if (suppressModuleToggle || moduleName == null) {
            return;
        }
        // Toggling from the GUI already shows the result on the switch itself — a toast
        // there just flashes over the menu. Keybind toggles in-game still notify.
        if (Minecraft.getMinecraft().currentScreen instanceof com.nezurstandalone.gui.ClickGUI) {
            return;
        }
        int accent = enabled ? 0xFF55CC55 : 0xFFFF5555;
        String state = enabled ? "Enabled" : "Disabled";
        String color = enabled ? "\u00a7a" : "\u00a7c";
        show(color + "\u00a7l" + moduleName + "\u00a7r\u00a77 " + state, 2200, accent);
    }

    public static void show(String message, int durationMs, int accentColor) {
        if (message == null || message.isEmpty()) {
            return;
        }
        ACTIVE.add(0, new Toast(message, Math.max(1200, durationMs), accentColor));
        trim();
    }

    /** Multi-line / list output that does not fit toasts — still uses chat. */
    public static void showInChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && message != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }

    public static void setSuppressModuleToggle(boolean suppress) {
        suppressModuleToggle = suppress;
    }

    static List<Toast> getActiveToasts() {
        return Collections.unmodifiableList(ACTIVE);
    }

    static void pruneExpired(long now) {
        Iterator<Toast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(now)) {
                it.remove();
            }
        }
    }

    private static void trim() {
        while (ACTIVE.size() > MAX_TOASTS) {
            ACTIVE.remove(ACTIVE.size() - 1);
        }
    }

    private static int accentFromMessage(String message) {
        if (message.contains("\u00a7a")) {
            return 0xFF55CC55;
        }
        if (message.contains("\u00a7c")) {
            return 0xFFFF5555;
        }
        if (message.contains("\u00a7e")) {
            return 0xFFFFCC55;
        }
        if (message.contains("\u00a79")) {
            return 0xFF5599FF;
        }
        if (message.contains("\u00a7b")) {
            return 0xFF55CCFF;
        }
        return 0xFF2F89FF;
    }

    public static final class Toast {
        public final String message;
        public final long createdAt;
        public final int durationMs;
        public final int accentColor;

        public boolean exiting = false;
        public final com.nezurstandalone.gui.physics.PhysicsSpring spring = com.nezurstandalone.gui.physics.PhysicsSpring.iOSBouncy(0f);
        private boolean initiated = false;

        Toast(String message, int durationMs, int accentColor) {
            this.message = message;
            this.durationMs = durationMs;
            this.accentColor = accentColor;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long now) {
            return exiting && spring.isSettled() && spring.getCurrentValue() <= 0.01f;
        }

        public void updateState(long now, float dt) {
            if (!initiated) {
                spring.setTarget(1f);
                initiated = true;
            }
            if (!exiting && now - createdAt > durationMs) {
                exiting = true;
                spring.setTarget(0f);
                spring.applyPreset(com.nezurstandalone.gui.physics.PhysicsSpring.Preset.IOS_SNAPPY);
            }
            spring.update(dt);
        }
    }
}
