package com.nezurstandalone.input;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Main-thread intents, consumed immediately after Minecraft refreshes its native raycast. */
public final class GuardedInput {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Map<Object, Intent> PENDING = new LinkedHashMap<>();
    private static long generation;
    public static java.util.function.BiConsumer<String,String> diagnostic=(event,details)->{};
    private static final Map<Object,String> labels=new java.util.WeakHashMap<>();
    public static void label(Object owner,String name){labels.put(owner,name);}

    public enum Status { QUEUED, DISPATCHED, NATIVE_INVOKED, DROPPED, CANCELLED }
    public static java.util.function.Predicate<Boolean> dispatchPermit = attack -> true;
    public static BooleanSupplier cameraAttackAllowed = () -> true;
    public static void watch(Object owner, java.util.function.Consumer<Status> observer) {
        Intent intent=PENDING.get(owner);
        if(intent==null){observer.accept(Status.DROPPED);return;}
        intent.observer=observer;observer.accept(Status.QUEUED);
    }
    private GuardedInput() { }

    /** Repeated ordinary left clicks, independent of a selected combat target. */
    public static void attackCrosshair(Object owner, BooleanSupplier allowed) {
        if (MC.thePlayer == null || MC.theWorld == null || MC.currentScreen != null) return;
        Intent intent = new Intent(owner, true, null, null, allowed);
        intent.crosshairAttack = true;
        cancel(owner); PENDING.put(owner, intent);
    }

    public static void attack(Object owner, Entity entity, BooleanSupplier allowed) {
        enqueue(owner, true, entity, null, allowed);
    }

    /** Validate a fresh server-facing ray, then scope it to one native click. */
    public static void attack(Object owner, Entity entity, BooleanSupplier allowed,
                              java.util.function.Supplier<MovingObjectPosition> raycast) {
        enqueue(owner, true, entity, null, allowed);
        Intent intent = PENDING.get(owner);
        if (intent != null) intent.raycast = raycast;
    }

    public static void useEntity(Object owner, Entity entity, BooleanSupplier allowed) {
        enqueue(owner, false, entity, null, allowed);
    }

    /** Exact NPC interaction after a fresh entity ray, without native item-use fallback. */
    public static void interactEntity(Object owner,Entity entity,BooleanSupplier allowed,Runnable interact) {
        enqueue(owner,false,entity,null,allowed);
        Intent intent=PENDING.get(owner);if(intent!=null)intent.directEntityInteract=interact;
    }

    public static void useEntity(Object owner, Entity entity, BooleanSupplier allowed, Runnable invoked) {
        enqueue(owner, false, entity, null, allowed);
        Intent intent=PENDING.get(owner); if(intent!=null) intent.executed=invoked;
    }
    public static void useBlock(Object owner, BlockPos block, BooleanSupplier allowed, Runnable invoked) {
        enqueue(owner, false, null, block, allowed);
        Intent intent=PENDING.get(owner); if(intent!=null) intent.executed=invoked;
    }
    public static void useBlock(Object owner, BlockPos block, BooleanSupplier allowed, Runnable invoked,
                                java.util.function.Supplier<MovingObjectPosition> raycast) {
        useBlock(owner, block, allowed, invoked);
        Intent intent=PENDING.get(owner); if(intent!=null) intent.raycast=raycast;
    }
    public static void useBlock(Object owner, BlockPos block, BooleanSupplier allowed) {
        enqueue(owner, false, null, block, allowed);
    }

    /** Uses the captured hotbar item through the native right-click path. */
    public static void useItem(Object owner, BooleanSupplier allowed, Runnable executed) {
        if (MC.thePlayer == null || MC.theWorld == null || MC.currentScreen != null) return;
        Intent intent = new Intent(owner, false, null, null, allowed);
        intent.itemUse = true;
        intent.executed = executed;
        cancel(owner); PENDING.put(owner, intent);
    }

    /** Use the held item itself even when the crosshair points at an entity or block. */
    public static void useSelectedItem(Object owner, BooleanSupplier allowed, Runnable executed) {
        if (MC.thePlayer == null || MC.theWorld == null || MC.currentScreen != null) return;
        Intent intent = new Intent(owner, false, null, null, allowed);
        intent.itemUse = true;
        intent.directItemUse = true;
        intent.executed = executed;
        cancel(owner); PENDING.put(owner, intent);
    }

    private static void enqueue(Object owner, boolean attack, Entity entity, BlockPos block,
                                BooleanSupplier allowed) {
        if (MC.thePlayer == null || MC.theWorld == null || MC.currentScreen != null) return;
        cancel(owner); PENDING.put(owner, new Intent(owner, attack, entity, block, allowed));
    }

    public static void cancel(Object owner) { Intent i=PENDING.remove(owner); if(i!=null)i.report(Status.CANCELLED); }
    public static void clear() { Intent[] all=PENDING.values().toArray(new Intent[0]); PENDING.clear(); for(Intent i:all)i.report(Status.CANCELLED); }

    /** No catch-up bursts: at most one valid synthetic action from this queue per client tick. */
    public static void dispatch(Runnable attack, Runnable use) {
        dispatch(attack, use, 0);
    }
    public static void dispatchCamera(Runnable attack, Runnable use) { dispatch(attack, use, 1); }
    public static void dispatchServer(Runnable attack, Runnable use) { dispatch(attack, use, 2); }

    private static void dispatch(Runnable attack, Runnable use, int phase) {

        java.util.List<Intent> intents = new java.util.ArrayList<>();
        java.util.Iterator<Map.Entry<Object, Intent>> pending = PENDING.entrySet().iterator();
        while (pending.hasNext()) {
            Intent intent = pending.next().getValue();
            if (phase == 0 || phase == 1 && intent.raycast == null || phase == 2 && intent.raycast != null) {
                intents.add(intent); pending.remove();
            }
        }
        if (MC.thePlayer == null || MC.theWorld == null || MC.playerController == null
                || MC.currentScreen != null || MC.thePlayer.isDead
                || MC.thePlayer.getHealth() <= 0 || MC.thePlayer.isUsingItem()
                || MC.thePlayer.openContainer != MC.thePlayer.inventoryContainer) { for(Intent i:intents)i.report(Status.DROPPED); return; }
        boolean dispatched=false;
        for (Intent intent : intents) {
            MovingObjectPosition ray = intent.raycast == null ? MC.objectMouseOver : intent.raycast.get();
            if (!dispatched && dispatchPermit.test(intent.attack)
                    && (!intent.attack || intent.raycast != null || cameraAttackAllowed.getAsBoolean()) && intent.valid(ray)) {
                if ((intent.directItemUse || intent.directEntityInteract!=null) && !com.nezurstandalone.input.NativeActionGate.allow(false)) {
                    intent.report(Status.DROPPED);
                    continue;
                }
                intent.reason="validated";intent.report(Status.DISPATCHED);
                if(intent.directEntityInteract!=null)intent.directEntityInteract.run();
                else if (!intent.directItemUse) {
                    MovingObjectPosition cameraRay = MC.objectMouseOver;
                    try { MC.objectMouseOver = ray; if (intent.attack) attack.run(); else use.run(); }
                    finally { MC.objectMouseOver = cameraRay; }
                }
                else MC.playerController.sendUseItem(MC.thePlayer, MC.theWorld, MC.thePlayer.getHeldItem());
                dispatched=true;
                intent.report(Status.NATIVE_INVOKED);
                if (intent.executed != null) intent.executed.run();
            } else intent.report(Status.DROPPED);
        }
    }

    private static final class Intent {
        java.util.function.Consumer<Status> observer;
        void report(Status status){
            try{diagnostic.accept("INPUT_"+status,"request="+requestGeneration+" owner="+source+" attack="+attack+" slot="+slot+" session="+session+" reason="+reason);}
            catch(RuntimeException ignored){/* Diagnostics cannot affect dispatch. */}
            if(observer!=null)observer.accept(status);
        }
        boolean itemUse, directItemUse, crosshairAttack;
        Runnable executed;
        Runnable directEntityInteract;
        final long requestGeneration=++generation;
        final boolean attack;
        final Entity entity;
        final BlockPos block;
        final BooleanSupplier allowed;
        final String source;
        String reason="native_gate_or_client_context";
        java.util.function.Supplier<MovingObjectPosition> raycast;
        final long session = com.nezurstandalone.control.ClientSession.current();
        final Object world = MC.theWorld;
        final Entity player = MC.thePlayer;
        final int slot = MC.thePlayer.inventory.currentItem;
        final ItemStack held = ItemStack.copyItemStack(MC.thePlayer.getHeldItem());
        final long created = System.nanoTime();

        Intent(Object owner, boolean attack, Entity entity, BlockPos block, BooleanSupplier allowed) {
            source=labels.containsKey(owner)?labels.get(owner):owner.getClass().getSimpleName()+"@"+Integer.toHexString(System.identityHashCode(owner));
            this.attack = attack;
            this.entity = entity;
            this.block = block;
            this.allowed = allowed;
            report(Status.QUEUED);
        }

        boolean valid(MovingObjectPosition hit) {
            if(session!=com.nezurstandalone.control.ClientSession.current() || world!=MC.theWorld || player!=MC.thePlayer){reason="session_replaced";return false;}
            if(System.nanoTime()-created>250_000_000L){reason="request_expired";return false;}
            if(slot!=MC.thePlayer.inventory.currentItem){reason="slot_changed";return false;}
            if(!ItemStack.areItemStacksEqual(held,MC.thePlayer.getHeldItem())){reason="item_changed";return false;}
            if(!allowed.getAsBoolean()){reason="owner_or_target_ineligible";return false;}
            reason="ray_target_or_reach";
            if (itemUse) return held != null && !MC.playerController.getIsHittingBlock();
            if (crosshairAttack) {
                if (hit == null) return false;
                if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) return true;
                if (hit.hitVec == null) return false;
                double distance = MC.thePlayer.getPositionEyes(1.0F).distanceTo(hit.hitVec);
                if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
                    return hit.entityHit != null && !hit.entityHit.isDead
                            && hit.entityHit.worldObj == MC.theWorld && distance <= 3.0;
                return hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && distance <= 4.5;
            }
            if (hit == null || hit.hitVec == null) return false;
            if (!attack && MC.playerController.getIsHittingBlock()) return false;
            if (entity != null) {
                return !entity.isDead && entity.worldObj == MC.theWorld
                        && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                        && hit.entityHit == entity
                        && (!(attack || directEntityInteract!=null) || MC.thePlayer.getPositionEyes(1.0F).distanceTo(hit.hitVec) <= 3.0);
            }
            return !MC.playerController.getIsHittingBlock()
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && block != null && block.equals(hit.getBlockPos());
        }
    }
}
