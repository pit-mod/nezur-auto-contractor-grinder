package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Debug helper: dumps the container menu that is currently open to a text file.
 *
 * <p>Menus like the Pit's perk shop can only be inspected while they are on screen, and opening
 * chat to type a command closes them - so this hangs off a key press that is read while the GUI
 * itself has keyboard focus. Every slot is written out with its index, item id, metadata, display
 * name and full lore, which is exactly what is needed to drive a menu by name instead of by
 * hardcoded slot numbers.
 *
 * <p>Two keys write to two separate files so different accounts can be captured without mixing:
 * {@code F6} appends to {@code nezur_gui_dump_A.txt} and {@code F7} to {@code nezur_gui_dump_B.txt}.
 * Each key appends, so all of an account's menus land in one file.
 */
public class GuiDumper {

    private static final int DUMP_KEY_A = Keyboard.KEY_F6;
    private static final int DUMP_KEY_B = Keyboard.KEY_F7;

    @SubscribeEvent
    public void onGuiKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) {
            return; // key release
        }
        int key = Keyboard.getEventKey();
        if (key != DUMP_KEY_A && key != DUMP_KEY_B) {
            return;
        }
        if (!(event.gui instanceof GuiContainer)) {
            return;
        }
        dump((GuiContainer) event.gui, key == DUMP_KEY_A ? "A" : "B");
    }

    /** Same capture as F6; synchronous so callers can wait for the file to be flushed. */
    public static boolean dumpF6(GuiContainer gui) { return dump(gui,"A"); }

    private static boolean dump(GuiContainer gui, String slotLabel) {
        Minecraft mc = Minecraft.getMinecraft();
        Writer w = null;
        try {
            File out = new File(mc.mcDataDir, "nezur_gui_dump_" + slotLabel + ".txt");
            w = new OutputStreamWriter(new FileOutputStream(out, true), "UTF-8");

            String title = "(unknown)";
            IInventory lower = null;
            if (gui instanceof GuiChest && gui.inventorySlots instanceof ContainerChest) {
                lower = ((ContainerChest) gui.inventorySlots).getLowerChestInventory();
                title = lower.getDisplayName().getUnformattedText();
            }

            w.write("=========================================================\n");
            w.write("ACCOUNT    : " + (mc.thePlayer != null ? mc.thePlayer.getName() : "?")
                    + "   (level line: " + Utils.getLevel() + ", gold: " + Utils.getGoldDouble() + ")\n");
            w.write("MENU TITLE : \"" + title + "\"\n");
            w.write("GUI CLASS  : " + gui.getClass().getName() + "\n");
            if (lower != null) {
                w.write("SIZE       : " + lower.getSizeInventory() + " slots\n");
            }
            w.write("---------------------------------------------------------\n");

            if (lower != null) {
                for (int i = 0; i < lower.getSizeInventory(); i++) {
                    ItemStack st = lower.getStackInSlot(i);
                    if (st == null) {
                        continue; // empty slots are noise
                    }
                    int id = net.minecraft.item.Item.getIdFromItem(st.getItem());
                    w.write("SLOT " + i + "\n");
                    w.write("   id=" + id + "  meta=" + st.getMetadata() + "  count=" + st.stackSize + "\n");
                    w.write("   name=\"" + StringUtils.stripControlCodes(st.getDisplayName()) + "\"\n");
                    w.write("   raw =\"" + st.getDisplayName().replace("§", "&") + "\"\n");
                    for (String line : loreOf(st)) {
                        w.write("   lore: " + line + "\n");
                    }
                    w.write("\n");
                }
            }
            w.write("=========================================================\n\n");
            w.flush();

            NotificationManager.show("§aDumped \"" + title + "\" to dump_" + slotLabel, 3000);
            return true;
        } catch (Exception e) {
            NotificationManager.show("§cGUI dump failed: " + e.getMessage(), 4000);
            return false;
        } finally {
            if (w != null) {
                try { w.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** Lore lines of a stack, colour codes stripped; empty when it has none. */
    private static java.util.List<String> loreOf(ItemStack st) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (!st.hasTagCompound()) {
            return out;
        }
        NBTTagCompound tag = st.getTagCompound();
        if (!tag.hasKey("display", 10)) {
            return out;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9)) {
            return out;
        }
        NBTTagList lore = display.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++) {
            out.add(StringUtils.stripControlCodes(lore.getStringTagAt(i)));
        }
        return out;
    }
}
