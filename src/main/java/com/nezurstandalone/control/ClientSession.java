package com.nezurstandalone.control;
import net.minecraft.client.Minecraft;
/** Main-thread identity. Captured tokens are invalid after world/player replacement. */
public final class ClientSession {
    private static Object world, player;
    private static volatile long generation;
    /** Worker-safe read; only the main thread advances identity. */
    public static long snapshot() { return generation; }
    public static long current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || player != mc.thePlayer) {
            world = mc.theWorld; player = mc.thePlayer; generation++;
        }
        return generation;
    }
    public static void invalidate() { current(); generation++; }
}
