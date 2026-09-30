package com.nezurstandalone.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.util.Random;
import java.util.function.DoubleSupplier;

/**
 * The one way this mod presses the mouse buttons.
 *
 * <p>Six modules hand-rolled this, and they had drifted into three subtly different forms:
 * a press/tick/release tap, a bare {@code KeyBinding.onTick} that leaves the hold state alone,
 * and a press-plus-tick with no release for items that charge up. All three are legitimate and
 * all three are needed - what was not needed was each module rediscovering which one it wanted.
 *
 * <p>Everything here goes through {@link KeyBinding}, which is the same path a physical mouse
 * takes. Nothing in this class builds a packet.
 */
public final class ClickSimulator {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private ClickSimulator() {
    }

    private static int keyOf(boolean leftClick) {
        return leftClick
                ? mc.gameSettings.keyBindAttack.getKeyCode()
                : mc.gameSettings.keyBindUseItem.getKeyCode();
    }

    /** Queue an operation-specific intent, never an indistinguishable synthetic key event. */
    public static void tap(Object owner,boolean leftClick){pulse(owner,leftClick);}
    public static void pulse(Object owner,boolean leftClick){
        if(!com.nezurstandalone.control.InventoryOwner.available(owner)||mc.thePlayer==null)return;
        net.minecraft.util.MovingObjectPosition hit=mc.objectMouseOver;
        java.util.function.BooleanSupplier allowed=()->com.nezurstandalone.control.InventoryOwner.available(owner);
        if(leftClick){if(hit!=null&&hit.entityHit!=null)GuardedInput.attack(owner,hit.entityHit,allowed);}
        else if(hit!=null&&hit.entityHit!=null)GuardedInput.useEntity(owner,hit.entityHit,allowed);
        else if(hit!=null&&hit.getBlockPos()!=null)GuardedInput.useBlock(owner,hit.getBlockPos(),allowed);
        else GuardedInput.useItem(owner,allowed,null);
    }
    public static void beginHold(Object owner,boolean leftClick){
        if(leftClick){pulse(owner,true);return;}
        if(com.nezurstandalone.control.InventoryOwner.acquire(owner))GuardedInput.useItem(owner,()->com.nezurstandalone.control.InventoryOwner.owns(owner),()->com.nezurstandalone.control.InventoryOwner.hold(owner,true));
    }
    public static void endHold(Object owner,boolean leftClick){GuardedInput.cancel(owner);if(!leftClick)com.nezurstandalone.control.InventoryOwner.release(owner,false);}
    public static void invalidate(){GuardedInput.clear();}

    /**
     * The interval model that decides *when* a repeated click is allowed.
     *
     * <p>ChestAura had the good version of this and the others had cruder copies, so the good
     * one is what survived: a Gaussian around the target interval, plus an occasional long
     * pause and an occasional fast pair, clamped to a physically plausible band. A fixed
     * interval produces a spike in the interval histogram that nothing with a hand behind it
     * ever produces.
     */
    public static final class Rate {

        /**
         * Ceiling applied to any requested rate.
         *
         * <p>GiantCakeHelper asked for 35 clicks per second by default and allowed 50. Sustained
         * human clicking does not reach half of that, and unlike inventory work this is ordinary
         * item use, which is the kind of thing a server can reasonably meter.
         */
        public static final double MAX_CPS = 20.0;

        private static final Random RNG = new Random();

        private final DoubleSupplier targetCps;
        private long lastClickAt = 0;
        private long nextDelay = 0;

        public Rate(DoubleSupplier targetCps) {
            this.targetCps = targetCps;
        }

        /** True when enough time has passed for another click. */
        public boolean ready() {
            return System.currentTimeMillis() - lastClickAt >= nextDelay;
        }

        /** Records a click and draws the interval before the next one is allowed. */
        public void consume() {
            lastClickAt = System.currentTimeMillis();

            double cps = Math.min(MAX_CPS, Math.max(0.5, targetCps.getAsDouble()));
            double mean = 1000.0 / cps;
            double delay = mean + RNG.nextGaussian() * (mean * 0.25);

            if (RNG.nextDouble() < 0.05) delay *= 1.5;   // a pause, the way attention lapses
            if (RNG.nextDouble() < 0.02) delay *= 0.5;   // an occasional fast pair

            nextDelay = (long) Math.max(Math.ceil(1000.0 / MAX_CPS), delay);
        }

        /** Convenience: click now if the interval allows it. */
        public boolean tryTap(Object owner,boolean leftClick) {
            if (!ready()) return false;
            tap(owner,leftClick);
            consume();
            return true;
        }

        /** Convenience: pulse now if the interval allows it. */
        public boolean tryPulse(Object owner,boolean leftClick) {
            if (!ready()) return false;
            pulse(owner,leftClick);
            consume();
            return true;
        }

        public void reset() {
            lastClickAt = 0;
            nextDelay = 0;
        }
    }
}
