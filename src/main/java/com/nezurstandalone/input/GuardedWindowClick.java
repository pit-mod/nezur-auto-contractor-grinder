package com.nezurstandalone.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;

/** Serializes chest transfers against native server transaction acknowledgments. */
public final class GuardedWindowClick {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static Container container;
    private static Object world;
    private static boolean contentsReceived;
    private static boolean waiting;
    private static boolean rejected;
    private static short action;
    private GuardedWindowClick() { }

    private static void refresh() {
        Container current = MC.thePlayer == null ? null : MC.thePlayer.openContainer;
        if (current != container || world != MC.theWorld) {
            container = current;
            world = MC.theWorld;
            waiting = rejected = false;
            contentsReceived = false;
        }
    }

    public static boolean isBlocked() {
        refresh();
        return !contentsReceived || waiting || rejected;
    }

    public static boolean click(ContainerChest target, int slot) {
        return click(target, slot, 1);
    }

    /** Pit Classic transfers on an ordinary left click rather than a shift click. */
    public static boolean clickNormally(ContainerChest target, int slot) {
        return click(target, slot, 0);
    }

    private static boolean click(ContainerChest target, int slot, int mode) {
        refresh();
        if (!com.nezurstandalone.control.GuiLease.available(GuardedWindowClick.class)) return false;
        if (!contentsReceived || waiting || rejected || MC.thePlayer == null || MC.playerController == null
                || MC.thePlayer.isDead || MC.thePlayer.isUsingItem()
                || !(MC.currentScreen instanceof GuiChest)
                || ((GuiChest) MC.currentScreen).inventorySlots != target || container != target
                || MC.thePlayer.inventory.getItemStack() != null
                || slot < 0 || slot >= target.getLowerChestInventory().getSizeInventory()
                || slot >= target.inventorySlots.size() || !target.getSlot(slot).getHasStack()) return false;
        // Read only: vanilla alone increments the counter and constructs the packet.
        final Field counter;
        try {
            counter = ReflectionHelper.findField(Container.class, "transactionID", "field_75150_e");
            counter.getShort(target);
        } catch (RuntimeException | IllegalAccessException e) {
            rejected = true;
            return false;
        }
        waiting = true;
        MC.playerController.windowClick(target.windowId, slot, 0, mode, MC.thePlayer);
        try {
            action = counter.getShort(target);
        } catch (IllegalAccessException e) {
            rejected = true;
        }
        return true;
    }

    public static void contents(int windowId) {
        refresh();
        if (container != null && container.windowId == windowId) contentsReceived = true;
    }

    public static void confirm(int windowId, short actionNumber, boolean accepted) {
        refresh();
        if (waiting && container != null && container.windowId == windowId && action == actionNumber) {
            waiting = false;
            rejected = !accepted; // Reopen after a rejected transfer; never ghost-click retries.
        }
    }
}
