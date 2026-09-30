package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class RotationUtils {
    public static final Minecraft mc = Minecraft.getMinecraft();

    public static float[] fixedSensitivity(float sensitivity, float yaw, float pitch) {
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;

        float deltaYaw = yaw - mc.thePlayer.rotationYaw;
        deltaYaw -= deltaYaw % gcd;
        float fixedYaw = mc.thePlayer.rotationYaw + deltaYaw;

        float deltaPitch = pitch - mc.thePlayer.rotationPitch;
        deltaPitch -= deltaPitch % gcd;
        float fixedPitch = mc.thePlayer.rotationPitch + deltaPitch;

        return new float[]{fixedYaw, fixedPitch};
    }

    public static float[] getRotations(BlockPos blockPos) {
        double x = blockPos.getX() + 0.5 - mc.thePlayer.posX;
        double y = blockPos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
        double distance = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float)(Math.atan2(z, x) * (180 / Math.PI)) - 90.0F;
        float pitch = (float)(-(Math.atan2(y, distance) * (180 / Math.PI)));
        return new float[]{yaw, pitch};
    }

    public static float interpolateValue(float partialTicks, float prev, float current) {
        return prev + (current - prev) * partialTicks;
    }

    public static float[] getRotations(Entity entity) {
        return getRotations(entity, 0, 0, 0);
    }

    public static float[] getRotations(Entity entity, double xOff, double yOff, double zOff) {
        double x = entity.posX + xOff - mc.thePlayer.posX;
        double y = entity.posY + (entity.getEyeHeight() * 0.85) + yOff - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = entity.posZ + zOff - mc.thePlayer.posZ;
        double distance = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float)(Math.atan2(z, x) * (180 / Math.PI)) - 90.0F;
        float pitch = (float)(-(Math.atan2(y, distance) * (180 / Math.PI)));
        return new float[]{yaw, pitch};
    }

    public static float getAngleDifference(float a, float b) {
        return ((a - b) % 360f + 540f) % 360f - 180f;
    }

    public static void applyMovementCorrection(float originalYaw) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;

        float diff = mc.thePlayer.rotationYaw - originalYaw;
        double radians = Math.toRadians(diff);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        mc.thePlayer.movementInput.moveForward = forward * cos + strafe * sin;
        mc.thePlayer.movementInput.moveStrafe = strafe * cos - forward * sin;
    }

    public static MovingObjectPosition rayCast(double distance, float yaw, float pitch) {
        Vec3 start = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 direction = getDirectionVector(yaw, pitch);
        Vec3 end = start.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(start, end, false, false, false);
    }

    public static boolean isOverEntity(Entity target, double distance) {
        Vec3 start = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 direction = mc.thePlayer.getLook(1.0F);
        Vec3 end = start.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        net.minecraft.util.AxisAlignedBB bb = target.getEntityBoundingBox().expand(0.03, 0.03, 0.03);
        return bb.calculateIntercept(start, end) != null;
    }

    private static Vec3 getDirectionVector(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    /** Hard cap for combat aim assist — beyond ~5 blocks is not credible for Pit melee. */
    public static final double LEGIT_AIM_RANGE_CAP = 5.0;
    public static final double LEGIT_AIM2_MAX_CAP = 10.0;
    public static final double LEGIT_AIM2_MIN_CAP = 2.5;

    public static double clampLegitRange(double range) {
        return Math.min(range, LEGIT_AIM_RANGE_CAP);
    }

    public static double clampLegitAim2Max(double maxRange) {
        return Math.min(maxRange, LEGIT_AIM2_MAX_CAP);
    }

    public static double clampLegitAim2Min(double minRange) {
        return Math.max(LEGIT_AIM2_MIN_CAP, Math.min(minRange, LEGIT_AIM2_MAX_CAP - 0.5));
    }

    public static float consumeFrameDeltaSec(long[] lastNanosHolder) {
        long now = System.nanoTime();
        if (lastNanosHolder[0] == 0L) {
            lastNanosHolder[0] = now;
            return 1f / 60f;
        }
        float delta = (now - lastNanosHolder[0]) / 1_000_000_000f;
        lastNanosHolder[0] = now;
        if (delta > 0.05f) delta = 0.05f;
        if (delta < 0.001f) delta = 0.001f;
        return delta;
    }

    private static final float REFERENCE_TPS = 20f;

    /** Converts a per-tick (20 TPS) lerp factor into a frame-rate independent step. */
    public static float tickFactor(float perTickFactor, float deltaSec) {
        perTickFactor = MathHelper.clamp_float(perTickFactor, 0.001f, 1f);
        double ticks = deltaSec * REFERENCE_TPS;
        return (float) (1.0 - Math.pow(1.0 - perTickFactor, ticks));
    }

    // The direct-write aim helpers that used to live here (applyRotationGcd, applySmoothBotAim,
    // applyAngularAim, applySpeedLerpAim) are gone on purpose. They assigned rotationYaw /
    // rotationPitch directly - deltas no physical mouse could have generated - and the GCD snap
    // followed by a pitch clamp emitted off-lattice angles at the rails. Every caller now
    // publishes to RotationManager, whose emit() leaves through vanilla setAngles as whole mouse
    // pixels under one ballistic motion model. Do not reintroduce direct writes: a second
    // rotation pipeline makes the client's input fingerprint depend on which module is driving.

    public static boolean isRotationAligned(float targetYaw, float targetPitch, float thresholdDeg) {
        if (mc.thePlayer == null) {
            return false;
        }
        return Math.abs(getAngleDifference(targetYaw, mc.thePlayer.rotationYaw)) <= thresholdDeg
                && Math.abs(getAngleDifference(targetPitch, mc.thePlayer.rotationPitch)) <= thresholdDeg;
    }
}
