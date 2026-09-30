package com.nezurstandalone.control;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
/** All temporary item operations lease the same slot/use resource. Main thread only. */
public final class InventoryOwner {
    private static final SlotLease SLOT = new SlotLease();
    private static Object heldUse;
    private static long useSession;
    public static boolean holdingUse(){return heldUse!=null&&useSession==ClientSession.current();}
    public static long generation(){return SLOT.generation();}
    private static int priority(Object owner){
        if(owner instanceof com.nezurstandalone.module.impl.player.AutoHeal)return 100;
        if(owner instanceof com.nezurstandalone.engine.GrinderEngine)return 20;
        return 40;
    }
    public static boolean available(Object owner) { Minecraft mc=Minecraft.getMinecraft(); refresh(); return mc.thePlayer!=null&&SLOT.available(owner,ClientSession.current(),mc.thePlayer.inventory.currentItem); }
    public static boolean acquire(Object owner) {
        Minecraft mc = Minecraft.getMinecraft();
        refresh();
        if(mc.thePlayer==null)return false;
        Object previous=SLOT.owner();
        boolean acquired=SLOT.acquire(owner,ClientSession.current(),mc.thePlayer.inventory.currentItem,priority(owner),true);
        if(acquired&&previous!=null&&previous!=owner){
            com.nezurstandalone.input.GuardedInput.cancel(previous);
            refresh(); // releases the preempted use key before the next owner may press it
        }
        return acquired;
    }
    public static boolean owns(Object owner) {
        Minecraft mc = Minecraft.getMinecraft(); refresh();
        return mc.thePlayer != null && SLOT.owns(owner, ClientSession.current(), mc.thePlayer.inventory.currentItem);
    }
    public static boolean select(Object owner, int slot) {
        if (slot < 0 || slot > 8 || !acquire(owner)) return false;
        SLOT.selected(slot); Minecraft.getMinecraft().thePlayer.inventory.currentItem = slot; return true;
    }
    public static void hold(Object owner, boolean down) {
        Minecraft mc = Minecraft.getMinecraft(); refresh();
        if (down) {
            if (!owns(owner)) return;
            heldUse = owner; useSession = ClientSession.current();
        } else if (heldUse != owner) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), down || com.nezurstandalone.input.NativeActionGate.physical(mc.gameSettings.keyBindUseItem.getKeyCode()));
        if (!down) heldUse = null;
    }
    public static void release(Object owner, boolean restore) {
        Minecraft mc = Minecraft.getMinecraft(); hold(owner, false);
        if (mc.thePlayer != null) mc.thePlayer.inventory.currentItem = SLOT.release(owner,
                ClientSession.current(), mc.thePlayer.inventory.currentItem, restore);
    }
    public static void refresh() {
        Minecraft mc = Minecraft.getMinecraft(); long session = ClientSession.current();
        if (heldUse != null && (useSession != session || mc.thePlayer == null || mc.thePlayer.isDead
                || !SLOT.owns(heldUse, session, mc.thePlayer.inventory.currentItem))) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), com.nezurstandalone.input.NativeActionGate.physical(mc.gameSettings.keyBindUseItem.getKeyCode())); heldUse = null;
        }
    }
}
