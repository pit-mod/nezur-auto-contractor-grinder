package com.nezurstandalone.combat;

import com.nezurstandalone.control.ClientSession;
import com.nezurstandalone.control.Clock;
import com.nezurstandalone.control.MoveFix;
import com.nezurstandalone.control.ServerRotationState;
import com.nezurstandalone.control.SessionResets;
import com.nezurstandalone.input.GuardedInput;
import com.nezurstandalone.input.NativeActionGate;
import com.nezurstandalone.utils.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

import java.util.function.BooleanSupplier;

/** One leased server orientation; the visible camera and physical mouse remain independent. */
public final class ServerRotations {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final ServerRotationState STATE = new ServerRotationState();
    private static final RotationManager SOLVER = new RotationManager(new RotationManager.Output() {
        public float yaw() { return STATE.yaw(); }
        public float pitch() { return STATE.pitch(); }
        public void apply(float yaw, float pitch) { STATE.apply(yaw, pitch); }
    });
    private static final String SOLVER_OWNER = "server.combat";
    private static String owner, tickOwner, sentOwner;
    private static BooleanSupplier allowed = () -> false, claimKey = () -> false;
    private static long session = -1, expiresAt;
    private static boolean detached, returning, reported;
    private static boolean blockInteraction;
    public static String ownerName(){return owner==null?"none":owner;}
    /** Optional gesture within the current lease; the normal solver still owns all output. */
    public interface Offset {
        boolean active();
        boolean finished();
        float completionYaw();
        void sample(long now);
        float yaw();
        float pitch();
    }
    private static Object offsetSource;
    private static String offsetOwner;
    private static Offset offset;
    private static float aimYaw,aimPitch,gestureBaseYaw;
    private static Object realViewSource;
    private static String realViewOwner;
    private static float realViewYaw,realViewPitch;
    private static float movementCameraYaw;
    static {
        SessionResets.register(ServerRotations.class, ServerRotations::reset);
        NativeActionGate.attackKeyOwner = ServerRotations::claimsHeldAttack;
        NativeActionGate.cameraAttackAllowed = ServerRotations::cameraAttackAllowed;
        NativeActionGate.nativeAttackCadence = ServerRotations::attackActive;
        GuardedInput.cameraAttackAllowed = ServerRotations::cameraAttackAllowed;
        RotationManager.cameraAutomationAllowed = () -> !exclusiveRealView() && NativeActionGate.automationAllowed.getAsBoolean();
    }
    private ServerRotations() { }

    public static void request(String key, float yaw, float pitch, float speed,
                               BooleanSupplier eligibility, BooleanSupplier keyClaim) {
        request(key, yaw, pitch, speed, eligibility, keyClaim, false);
    }

    /** Block interactions share the solver/move fix without taking over the melee trigger. */
    public static void requestBlock(String key, float yaw, float pitch, float speed, BooleanSupplier eligibility) {
        request(key, yaw, pitch, speed, eligibility, () -> false, true);
    }

    private static void request(String key, float yaw, float pitch, float speed,
                                BooleanSupplier eligibility, BooleanSupplier keyClaim, boolean block) {
        if (!context() || !eligibility.getAsBoolean()) { release(key); return; }
        checkSession();
        if(exclusiveRealView() && !key.equals(realViewOwner))return;
        if(realViewSource!=null && !key.equals(realViewOwner))handoffRealView();
        if (offset != null && !offsetOwner.equals(key)) clearOffset();
        if (!detached) {
            STATE.seed(reported ? STATE.sentYaw() : MC.thePlayer.rotationYaw,
                    reported ? STATE.sentPitch() : MC.thePlayer.rotationPitch);
            SOLVER.clearAll();
        }
        owner = key; allowed = eligibility; claimKey = keyClaim;
        blockInteraction = block;
        detached = true; returning = false; expiresAt = Clock.millis() + 150;
        aimYaw=yaw;aimPitch=pitch;
        if(offset!=null)gestureBaseYaw+=ServerRotationState.wrap(yaw-gestureBaseYaw);
        SOLVER.setTargetRotation(SOLVER_OWNER, RotationManager.PRIORITY_COMBAT, yaw, pitch, speed, true);
    }

    /** Attach to the already confirmed combat lease; never constructs or sends a packet. */
    public static boolean setRealMovement(Object source, Offset movement) {
        if (source == null || movement == null || !attackActive() || offset!=null) return false;
        offsetSource=source; offsetOwner=owner; offset=movement;
        gestureBaseYaw=STATE.yaw()+ServerRotationState.wrap(aimYaw-STATE.yaw());
        if(realViewSource!=source || !owner.equals(realViewOwner)){
            realViewYaw=STATE.sentYaw();realViewPitch=STATE.sentPitch();
        }
        realViewSource=source;realViewOwner=owner;
        return true;
    }
    public static boolean realMovementBusy(Object source){return source==offsetSource && offset!=null;}
    public static boolean realViewActive(Object source){return source==realViewSource && owner!=null && owner.equals(realViewOwner) && eligible();}
    private static boolean exclusiveRealView(){return realViewSource!=null && owner!=null && owner.equals(realViewOwner) && eligible();}
    /** Renderer records what is actually displayed; it never writes physical camera fields. */
    public static void captureRealView(Object source,float yaw,float pitch){
        if(realViewActive(source)&&Float.isFinite(yaw)&&Float.isFinite(pitch)){realViewYaw=yaw;realViewPitch=pitch;}
    }
    public static void endRealView(Object source){if(source==realViewSource)handoffRealView();}
    private static void handoffRealView(){
        if(realViewSource!=null && MC.theWorld!=null && MC.thePlayer!=null
                && session==ClientSession.current() && !MC.thePlayer.isDead && !MC.thePlayer.isRiding()){
            // Ownership ends at the last displayed heading, rather than the hidden pre-combo camera.
            MC.thePlayer.rotationYaw=MC.thePlayer.prevRotationYaw=realViewYaw;
            MC.thePlayer.rotationPitch=MC.thePlayer.prevRotationPitch=realViewPitch;
        }
        realViewSource=null;realViewOwner=null;
    }
    public static void clearRealMovement(Object source) { if (source == offsetSource) clearOffset(); }
    private static void clearOffset() { offsetSource=null; offsetOwner=null; offset=null; }

    public static void release(String key) {
        if (key == null || !key.equals(owner)) return;
        handoffRealView();
        clearOffset();
        owner = null; allowed = claimKey = () -> false; returning = detached;
    }

    /** Called after normal camera input, including frames without a physical mouse read. */
    public static void frame() {
        frame(Clock.SYSTEM.nanos());
    }
    private static void frame(long now) {
        checkSession();
        if (!context()) { reset(); return; }
        if (!detached) return;
        if (owner != null && !eligible()) release(owner);
        if(offset!=null){
            if(offsetOwner.equals(owner) && offset.active()){
                offset.sample(now);
                float yaw=gestureBaseYaw+(offset.finished()?offset.completionYaw():offset.yaw());
                float pitch=aimPitch+(offset.finished()?0:offset.pitch());
                if(Float.isFinite(yaw)&&Float.isFinite(pitch)){
                    SOLVER.followGesture(yaw,pitch,now);
                    float sensitivity=MC.gameSettings.mouseSensitivity*.6F+.2F;
                    float tolerance=Math.max(.3F,sensitivity*sensitivity*sensitivity*8*.15F+.0001F);
                    if(offset.finished() && Math.abs(yaw-STATE.yaw())<=tolerance
                            && Math.abs(Math.max(-89.9F,Math.min(89.9F,pitch))-STATE.pitch())<=tolerance)clearOffset();
                    return;
                }
            }
            clearOffset();
        }
        if (returning) {
            if (Math.abs(ServerRotationState.wrap(STATE.yaw() - MC.thePlayer.rotationYaw)) <= 0.2
                    && Math.abs(STATE.pitch() - MC.thePlayer.rotationPitch) <= 0.2) {
                detached = returning = false; STATE.reset(); SOLVER.clearAll(); return;
            }
            SOLVER.setTargetRotation(SOLVER_OWNER, RotationManager.PRIORITY_COMBAT,
                    MC.thePlayer.rotationYaw, MC.thePlayer.rotationPitch, 15, true);
        }
        SOLVER.afterMouseInput(0, 0);
    }

    /** Freeze one orientation before vanilla input, physics, sprint/jump and movement packets. */
    public static void beginTick() {
        checkSession();
        if (!context()) { reset(); return; }
        if (owner != null && !eligible()) release(owner);
        STATE.beginTick(detached);
        // Forward stays toward the tracked target while the visible real gesture spins.
        // The hidden physical camera must not become a second movement reference.
        movementCameraYaw=exclusiveRealView()?aimYaw:MC.thePlayer.rotationYaw;
        tickOwner = returning ? null : owner;
    }

    public static float yaw(Entity entity, float cameraYaw) {
        if(entity==MC.thePlayer&&com.nezurstandalone.control.InventoryRotationHold.owns(entity,MC.theWorld,ClientSession.current()))
            return com.nezurstandalone.control.InventoryRotationHold.yaw(entity,MC.theWorld,ClientSession.current(),cameraYaw);
        return entity == MC.thePlayer && context() && STATE.tickActive() && session == ClientSession.current()
                ? STATE.tickYaw() : cameraYaw;
    }
    public static float pitch(Entity entity, float cameraPitch) {
        if(entity==MC.thePlayer&&com.nezurstandalone.control.InventoryRotationHold.owns(entity,MC.theWorld,ClientSession.current()))
            return com.nezurstandalone.control.InventoryRotationHold.pitch(entity,MC.theWorld,ClientSession.current(),cameraPitch);
        return entity == MC.thePlayer && context() && STATE.tickActive() && session == ClientSession.current()
                ? STATE.tickPitch() : cameraPitch;
    }
    public static void correctMovement() {
        if (!context() || !STATE.tickActive() || session != ClientSession.current()) return;
        float[] input = MoveFix.correct(movementCameraYaw, STATE.tickYaw(),
                MC.thePlayer.movementInput.moveForward, MC.thePlayer.movementInput.moveStrafe);
        MC.thePlayer.movementInput.moveForward = input[0];
        MC.thePlayer.movementInput.moveStrafe = input[1];
        // Vanilla subsequently checks forward input for sprinting; incompatible strafing cannot retain sprint.
        if (input[0] < 0.8F) MC.thePlayer.setSprinting(false);
    }
    public static void report(float yaw, float pitch) {
        com.nezurstandalone.control.InventoryRotationHold.observe(MC.thePlayer,MC.theWorld,ClientSession.current(),yaw,pitch);
        if (!context() || session != ClientSession.current()) return;
        STATE.report(yaw, pitch); reported = true;
        sentOwner = STATE.confirmed() ? tickOwner : null;
    }
    public static MovingObjectPosition raycast(String key, double reach) {
        if (blockInteraction || key == null || !key.equals(owner) || !key.equals(sentOwner) || !eligible()
                || !STATE.confirmed() || session != ClientSession.current()) return null;
        return ServerRaycast.trace(STATE.sentYaw(), STATE.sentPitch(), reach);
    }

    /** Identity only, so an owner's eligibility callback can check competing leases without recursion. */
    public static boolean owns(String key) { return key != null && key.equals(owner); }

    public static MovingObjectPosition raycastBlock(String key, double reach) {
        if (!blockInteraction || !owns(key) || !eligible() || !key.equals(sentOwner) || !STATE.confirmed()) return null;
        return ServerRaycast.traceBlock(STATE.sentYaw(), STATE.sentPitch(), reach);
    }

    /** Mining handoff may emit ABORT_DESTROY_BLOCK, so keep it in the native input phase too. */
    public static void prepareNativeInput() {
        if (claimsHeldAttack() && MC.playerController != null && MC.playerController.getIsHittingBlock())
            MC.playerController.resetBlockRemoving();
    }

    public static boolean attackActive() { return !blockInteraction && owner != null && eligible() && STATE.confirmed(); }
    /** Tracking owns input before the first confirmed combat ray as well. */
    public static boolean trackingActive() { return owner != null && eligible(); }
    public static float viewedYaw(float partial){return STATE.previousSentYaw()+ServerRotationState.wrap(STATE.sentYaw()-STATE.previousSentYaw())*partial;}
    public static float viewedPitch(float partial){return STATE.previousSentPitch()+(STATE.sentPitch()-STATE.previousSentPitch())*partial;}

    /** Renderer-only reads: show the last two reported poses without changing camera/entity fields. */
    public static float renderYaw(Entity entity, float fallback, boolean previous) {
        return renderActive(entity) ? previous ? STATE.previousSentYaw() : STATE.sentYaw() : fallback;
    }
    public static float renderPitch(Entity entity, float fallback, boolean previous) {
        return renderActive(entity) ? previous ? STATE.previousSentPitch() : STATE.sentPitch() : fallback;
    }
    private static boolean renderActive(Entity entity) {
        return entity == MC.thePlayer && MC.gameSettings.thirdPersonView != 0 && detached && reported
                && context() && session == ClientSession.current();
    }

    private static boolean claimsHeldAttack() {
        return !blockInteraction && owner != null && eligible() && claimKey.getAsBoolean()
                && NativeActionGate.physical(MC.gameSettings.keyBindAttack.getKeyCode())
                && !NativeActionGate.physical(MC.gameSettings.keyBindUseItem.getKeyCode());
    }
    private static boolean cameraAttackAllowed() {
        return !detached || session != ClientSession.current() || reported && context()
                && Math.abs(ServerRotationState.wrap(STATE.sentYaw() - MC.thePlayer.rotationYaw)) <= 0.2
                && Math.abs(STATE.sentPitch() - MC.thePlayer.rotationPitch) <= 0.2;
    }
    private static boolean eligible() { return context() && session == ClientSession.current() && Clock.millis() < expiresAt && allowed.getAsBoolean(); }
    private static boolean context() {
        return MC.theWorld != null && MC.thePlayer != null && !MC.thePlayer.isDead && !MC.thePlayer.isRiding()
                && MC.currentScreen == null && NativeActionGate.inventoryTransactionAllowed.getAsBoolean()
                && NativeActionGate.automationAllowed.getAsBoolean()
                ;
    }
    private static void checkSession() {
        long current = ClientSession.current();
        if (session != current) { reset(); session = current; }
    }
    public static void reset() {
        handoffRealView();
        clearOffset();
        owner = tickOwner = sentOwner = null; allowed = claimKey = () -> false;
        blockInteraction = detached = returning = reported = false; STATE.reset(); SOLVER.clearAll();
    }
}
