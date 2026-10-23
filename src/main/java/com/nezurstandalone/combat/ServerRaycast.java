package com.nezurstandalone.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Native-sized entity picking with block occlusion, using an explicit reported orientation. */
public final class ServerRaycast {
    private ServerRaycast() { }
    public static MovingObjectPosition trace(float yaw, float pitch, double reach) {
        return trace(yaw, pitch, reach, 3);
    }
    public static MovingObjectPosition traceBlock(float yaw, float pitch, double reach) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.playerController == null ? null : trace(yaw, pitch, reach, mc.playerController.getBlockReachDistance());
    }
    private static MovingObjectPosition trace(float yaw, float pitch, double reach, double limit) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        if (!Double.isFinite(reach) || !Double.isFinite(limit)) return null;
        reach = Math.max(0, Math.min(limit, reach));
        float a = -yaw * 0.017453292F - (float) Math.PI, b = -pitch * 0.017453292F;
        Vec3 look = new Vec3(MathHelper.sin(a) * -MathHelper.cos(b), MathHelper.sin(b), MathHelper.cos(a) * -MathHelper.cos(b));
        Vec3 eyes = mc.thePlayer.getPositionEyes(1), end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition block = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        double closest = block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? eyes.distanceTo(block.hitVec) : reach;
        Entity nearest = null;
        Vec3 point = null;
        AxisAlignedBB sweep = mc.thePlayer.getEntityBoundingBox().addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach).expand(1, 1, 1);
        for (Entity entity : mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, sweep)) {
            if (!entity.canBeCollidedWith() || entity.isDead || entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) continue;
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition hit = box.calculateIntercept(eyes, end);
            boolean inside = box.isVecInside(eyes);
            if (!inside && hit == null) continue;
            Vec3 hitPoint = inside ? eyes : hit.hitVec;
            double distance = eyes.distanceTo(hitPoint);
            if (distance < closest || nearest == null && distance <= closest && (block == null || block.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)) {
                nearest = entity; point = hitPoint; closest = distance;
            }
        }
        return nearest == null ? block : new MovingObjectPosition(nearest, point);
    }
}
