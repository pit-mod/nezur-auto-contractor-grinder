package com.nezurstandalone.utils;

import com.nezurstandalone.control.Clock;
import com.nezurstandalone.control.StationaryEggs;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

/** Shared by ESP, the section index and grinder, including the final queued interaction gate. */
public final class DragonEggTracker {
    private static final StationaryEggs<BlockPos> eggs = new StationaryEggs<>();
    private static Object world;
    private static long lastRefresh = Long.MIN_VALUE;

    private DragonEggTracker() {}

    private static boolean present(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.theWorld.isBlockLoaded(pos)
                && mc.theWorld.getBlockState(pos).getBlock() == Blocks.dragon_egg;
    }

    private static void refresh() {
        Object current = Minecraft.getMinecraft().theWorld;
        if (current != world) { world = current; eggs.clear(); lastRefresh = Long.MIN_VALUE; }
        long now = Clock.millis();
        if (now != lastRefresh) {
            lastRefresh = now;
            // Actual removal/unloading resets that position, never a module toggle or correction.
            eggs.retain(DragonEggTracker::present);
        }
    }

    public static boolean usable(BlockPos pos) {
        refresh();
        return eggs.observe(pos, present(pos), Clock.millis());
    }

    public static void blockChanged(Object changedWorld, BlockPos pos) {
        refresh();
        if (world == changedWorld) eggs.observe(pos, present(pos), Clock.millis());
    }
}
