package com.nezurstandalone.utils;

import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

/**
 * One way to read the text out of a chat message.
 *
 * <p>The modules that listen to chat had settled on three different answers to the same
 * question: {@code StringUtils.stripControlCodes(...)},
 * {@code EnumChatFormatting.getTextWithoutFormattingCodes(...)}, and - in seven places - a bare
 * {@code getUnformattedText()} with no stripping at all.
 *
 * <p>The third is the one that matters. {@code getUnformattedText} resolves translation
 * components but leaves the section-sign colour codes in place, so any test against it silently
 * fails whenever the server colours the middle of a line. {@code "DEATH!"} matches, but
 * {@code "§cDEATH!"} does not, and Hypixel colours plenty of what it prints. Every caller wants
 * the stripped form; only some of them knew it.
 */
public final class ChatEvents {

    private ChatEvents() {
    }

    /** The message with formatting codes removed. Never null. */
    public static String plain(ClientChatReceivedEvent event) {
        if (event == null || event.message == null) return "";
        return plain(event.message);
    }

    /** The component with formatting codes removed. Never null. */
    public static String plain(IChatComponent component) {
        if (component == null) return "";
        String text = component.getUnformattedText();
        return text == null ? "" : StringUtils.stripControlCodes(text);
    }

    /** Stripped and lower-cased, for case-insensitive matching. */
    public static String plainLower(ClientChatReceivedEvent event) {
        return plain(event).toLowerCase();
    }

    /** True when the stripped message contains {@code needle}. */
    public static boolean contains(ClientChatReceivedEvent event, String needle) {
        return plain(event).contains(needle);
    }
}
