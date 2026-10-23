package com.nezurstandalone.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

/** Aim at a visible reachable body point, with a tick/movement/block revision cache. */
public final class CombatAim {
    private Entity target;
    private Object world;
    private Object player;
    private double cachedReach;
    private int tick;
    private long revision;
    private double eyeX,eyeY,eyeZ,minX,minY,minZ,maxX,maxY,maxZ;
    private Vec3 point;
    public void reset(){target=null;world=null;point=null;}
    public float[] rotations(Entity entity,double reach,double offX,double offY,double offZ) {
        Minecraft mc=Minecraft.getMinecraft();Vec3 eyes=mc.thePlayer.getPositionEyes(1);
        AxisAlignedBB box=entity.getEntityBoundingBox();
        long currentRevision=com.nezurstandalone.pathfinder.WorldCapture.visibilityRevision();
        if(entity!=target || world!=mc.theWorld || player!=mc.thePlayer || cachedReach!=reach || tick!=mc.thePlayer.ticksExisted || revision!=currentRevision
                || eyeX!=eyes.xCoord || eyeY!=eyes.yCoord || eyeZ!=eyes.zCoord
                || minX!=box.minX || minY!=box.minY || minZ!=box.minZ
                || maxX!=box.maxX || maxY!=box.maxY || maxZ!=box.maxZ) {
            target=entity;world=mc.theWorld;player=mc.thePlayer;cachedReach=reach;tick=mc.thePlayer.ticksExisted;revision=currentRevision;
            eyeX=eyes.xCoord;eyeY=eyes.yCoord;eyeZ=eyes.zCoord;
            minX=box.minX;minY=box.minY;minZ=box.minZ;maxX=box.maxX;maxY=box.maxY;maxZ=box.maxZ;
            Vec3 preferred=clamp(box,new Vec3(entity.posX+offX,entity.posY+entity.getEyeHeight()*.85+offY,entity.posZ+offZ));
            point=choose(mc,eyes,box,preferred,reach);
        }
        double x=point.xCoord-eyes.xCoord,y=point.yCoord-eyes.yCoord,z=point.zCoord-eyes.zCoord;
        if(x*x+y*y+z*z<1e-12)return new float[]{mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch};
        return new float[]{(float)(Math.toDegrees(Math.atan2(z,x))-90),
                (float)-Math.toDegrees(Math.atan2(y,Math.sqrt(x*x+z*z)))};
    }
    private static Vec3 choose(Minecraft mc,Vec3 eyes,AxisAlignedBB box,Vec3 preferred,double reach) {
        if(box.isVecInside(eyes))return eyes;
        if(eyes.distanceTo(preferred)<=reach && visible(mc,eyes,preferred))return preferred;
        Vec3 closest=clamp(box,eyes);
        if(eyes.distanceTo(closest)<=reach+.001 && visible(mc,eyes,closest))return closest;
        double x=(box.minX+box.maxX)*.5,z=(box.minZ+box.maxZ)*.5;
        Vec3 visible=null;
        for(double height:new double[]{.5,.15,.85}) {
            Vec3 candidate=new Vec3(x,box.minY+(box.maxY-box.minY)*height,z);
            if(!visible(mc,eyes,candidate))continue;
            if(eyes.distanceTo(candidate)<=reach)return candidate;
            if(visible==null)visible=candidate;
        }
        return visible==null?preferred:visible;
    }
    private static boolean visible(Minecraft mc,Vec3 eyes,Vec3 point) {
        return mc.theWorld.rayTraceBlocks(eyes,point,false,true,false)==null;
    }
    private static Vec3 clamp(AxisAlignedBB box,Vec3 p) {
        double x=Math.min(.0001,(box.maxX-box.minX)*.25),y=Math.min(.0001,(box.maxY-box.minY)*.25),z=Math.min(.0001,(box.maxZ-box.minZ)*.25);
        return new Vec3(Math.max(box.minX+x,Math.min(box.maxX-x,p.xCoord)),
                Math.max(box.minY+y,Math.min(box.maxY-y,p.yCoord)),Math.max(box.minZ+z,Math.min(box.maxZ-z,p.zCoord)));
    }
}
