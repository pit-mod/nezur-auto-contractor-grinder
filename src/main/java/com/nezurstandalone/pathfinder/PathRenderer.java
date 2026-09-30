package com.nezurstandalone.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Passive route visualization. Never issues movement or navigation requests. */
public class PathRenderer {
    private static final double HEIGHT = 1.05, END_HEIGHT = 0.30, STEP = 0.20, VISIBLE_LENGTH = 96;
    private static final int HEAD_STEPS = 18, CAPACITY = 520;
    private static Trail current, outgoing;
    private static long changedAt, frameAt;
    private static double animationTime, outgoingOpacity;
    private static final double[] sample = new double[3], ahead = new double[3], end = new double[3];
    private static final double[] x = new double[CAPACITY], y = new double[CAPACITY], z = new double[CAPACITY];
    private static final double[] sx = new double[CAPACITY], sy = new double[CAPACITY], sz = new double[CAPACITY];
    private static final double[] width = new double[CAPACITY], remaining = new double[CAPACITY];
    private static final float[] red = new float[CAPACITY], green = new float[CAPACITY], blue = new float[CAPACITY], fade = new float[CAPACITY];
    private static int count;

    private static final class Trail {
        final RouteCurve curve;
        final Object world;
        final boolean partial;
        double progress = Double.NaN, lastX, lastY, lastZ;
        Trail(PathSnapshot snapshot) {
            List<Vec3> points = snapshot.getSmoothed();
            double[] px = new double[points.size()], py = new double[px.length], pz = new double[px.length];
            for (int i=0;i<px.length;i++) {
                Vec3 p=points.get(i); px[i]=p.xCoord; py[i]=p.yCoord; pz[i]=p.zCoord;
            }
            curve=new RouteCurve(px,py,pz);
            world=Minecraft.getMinecraft().theWorld;
            partial=snapshot.isPartial();
        }
    }

    static void setPath(PathSnapshot snapshot) {
        Trail next=snapshot==null || snapshot.getSmoothed().size()<2 ? null : new Trail(snapshot);
        if (next==null && current==null) return;
        long now=System.nanoTime();
        double previousBlend=RouteCurve.ease((now-changedAt)/260_000_000.0);
        if (current!=null) { outgoing=current; outgoingOpacity=previousBlend; }
        else outgoingOpacity*=1-previousBlend;
        current=next;
        changedAt=now;
        // Preserve the animation clock across repaths and clear/publish pairs.
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.theWorld==null || mc.thePlayer==null) { current=outgoing=null; frameAt=0; return; }
        if (current!=null && current.world!=mc.theWorld) current=null;
        if (outgoing!=null && outgoing.world!=mc.theWorld) outgoing=null;
        if (current==null && outgoing==null) { frameAt=0; return; }
        long now=System.nanoTime();
        double dt=frameAt==0 ? 0 : Math.min(0.1, Math.max(0,(now-frameAt)*1e-9));
        frameAt=now;
        if (!mc.isGamePaused()) animationTime+=dt;
        else dt=0;
        double blend=RouteCurve.ease((now-changedAt)/260_000_000.0);
        if (blend>=1) outgoing=null;

        double partial=event.partialTicks;
        double px=mc.thePlayer.lastTickPosX+(mc.thePlayer.posX-mc.thePlayer.lastTickPosX)*partial;
        double py=mc.thePlayer.lastTickPosY+(mc.thePlayer.posY-mc.thePlayer.lastTickPosY)*partial;
        double pz=mc.thePlayer.lastTickPosZ+(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)*partial;
        Entity camera=mc.getRenderViewEntity();
        Vec3 eye=(camera==null ? mc.thePlayer : camera).getPositionEyes(event.partialTicks);

        // Restore the incoming state, including alpha testing, blend factors and depth writes.
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D), lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST), blendEnabled=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean fog=GL11.glIsEnabled(GL11.GL_FOG), depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int shade=GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int srcRgb=GL11.glGetInteger(0x80C9), dstRgb=GL11.glGetInteger(0x80C8);
        int srcAlpha=GL11.glGetInteger(0x80CB), dstAlpha=GL11.glGetInteger(0x80CA);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.disableAlpha();
            GlStateManager.disableFog(); GlStateManager.disableCull(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.depthMask(false); GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.translate(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);
            if (outgoing!=null) draw(outgoing,px,py,pz,eye,dt,(float)(outgoingOpacity*(1-blend)),mc);
            if (current!=null) draw(current,px,py,pz,eye,dt,(float)blend,mc);
        } finally {
            GlStateManager.shadeModel(shade); GlStateManager.depthMask(depthWrite);
            GlStateManager.tryBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (blendEnabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            GlStateManager.color(1,1,1,1); GlStateManager.resetColor(); GlStateManager.popMatrix();
        }
    }

    private static void draw(Trail trail,double px,double py,double pz,Vec3 eye,double dt,float opacity,Minecraft mc) {
        if (opacity<0.002f) return;
        RouteCurve curve=trail.curve;
        boolean initial=Double.isNaN(trail.progress);
        boolean teleported=!initial && distance(px-trail.lastX,py-trail.lastY,pz-trail.lastZ)>8;
        double target=curve.project(px,py,pz,initial||teleported ? Double.NaN : trail.progress,6);
        trail.progress=initial||teleported ? target : RouteCurve.damp(trail.progress,target,18,dt);
        trail.lastX=px; trail.lastY=py; trail.lastZ=pz;
        curve.sample(curve.length(),end);
        double arrival=RouteCurve.ease((distance(px-end[0],py-end[1],pz-end[2])-0.35)/1.5);
        double headAt=Math.min(curve.length(),trail.progress+1.8);
        curve.sample(headAt,sample); curve.sample(Math.min(curve.length(),headAt+0.25),ahead);
        double hx=sample[0], hy=sample[1]+trailHeight(curve.length()-headAt), hz=sample[2];
        double dx=hx-px, dy=hy-(py+HEIGHT), dz=hz-pz;
        double headDistance=distance(dx,dy,dz), push=Math.min(0.45,headDistance*0.25);
        double ax=px+(headDistance>1e-6?dx/headDistance*push:0);
        double ay=py+HEIGHT+(headDistance>1e-6?dy/headDistance*push:0);
        double az=pz+(headDistance>1e-6?dz/headDistance*push:0);
        double tx=ahead[0]-hx, ty=ahead[1]+trailHeight(curve.length()-Math.min(curve.length(),headAt+0.25))-hy, tz=ahead[2]-hz;
        double tangent=distance(tx,ty,tz), handle=Math.min(0.8,headDistance*0.35);
        if (tangent<1e-6) { tx=dx;ty=dy;tz=dz;tangent=Math.max(1e-6,headDistance); }
        double bx=hx-tx/tangent*handle, by=hy-ty/tangent*handle, bz=hz-tz/tangent*handle;
        count=0;
        for (int i=0;i<=HEAD_STEPS;i++) {
            double t=i/(double)HEAD_STEPS,u=1-t;
            append(u*u*u*ax+3*u*u*t*(ax+(hx-ax)*0.32)+3*u*t*t*bx+t*t*t*hx,
                    u*u*u*ay+3*u*u*t*(ay+(hy-ay)*0.32)+3*u*t*t*by+t*t*t*hy,
                    u*u*u*az+3*u*u*t*(az+(hz-az)*0.32)+3*u*t*t*bz+t*t*t*hz,
                    curve.length()-(trail.progress+(headAt-trail.progress)*t));
        }
        double limit=Math.min(curve.length(),headAt+VISIBLE_LENGTH);
        for (double at=headAt+STEP;at<limit;at+=STEP) {
            curve.sample(at,sample); append(sample[0],sample[1]+trailHeight(curve.length()-at),sample[2],curve.length()-at);
        }
        curve.sample(limit,sample); append(sample[0],sample[1]+trailHeight(curve.length()-limit),sample[2],curve.length()-limit);
        double fov=mc.gameSettings.fovSetting;
        if (!Double.isFinite(fov)||fov<1||fov>179) fov=70;
        double focal=Math.max(1,mc.displayHeight/(2*Math.tan(Math.toRadians(fov)*0.5)));
        for (int i=0;i<count;i++) {
            int previous=Math.max(0,i-1), next=Math.min(count-1,i+1);
            double vx=x[next]-x[previous],vy=y[next]-y[previous],vz=z[next]-z[previous];
            double ex=eye.xCoord-x[i],ey=eye.yCoord-y[i],ez=eye.zCoord-z[i];
            double sideX=vy*ez-vz*ey,sideY=vz*ex-vx*ez,sideZ=vx*ey-vy*ex;
            double norm=distance(sideX,sideY,sideZ);
            if (norm<1e-7) { sideX=-vz;sideY=0;sideZ=vx;norm=distance(sideX,sideY,sideZ); }
            if (norm<1e-7) { sideX=1;sideY=0;sideZ=0;norm=1; }
            if (i>0 && sideX*sx[i-1]+sideY*sy[i-1]+sideZ*sz[i-1]<0) norm=-norm;
            sx[i]=sideX/norm;sy[i]=sideY/norm;sz[i]=sideZ/norm;
            width[i]=Math.min(0.032,distance(ex,ey,ez)/focal);
            double taper=RouteCurve.ease(i/(double)HEAD_STEPS)*RouteCurve.ease((count-1-i)*STEP/1.2);
            fade[i]=(float)(opacity*arrival*taper);
            double ramp=Math.min(1,remaining[i]/40), wave=0.83+0.17*Math.sin((remaining[i]+animationTime*4)*Math.PI/3);
            int a=ramp<0.5?0x64F7FF:0xFF69DF,b=ramp<0.5?0xFF69DF:0x9257FF;
            double t=ramp<0.5?ramp*2:(ramp-0.5)*2;
            red[i]=channel(a>>16,b>>16,t,wave);green[i]=channel(a>>8,b>>8,t,wave);blue[i]=channel(a,b,t,wave);
        }
        GlStateManager.disableDepth(); ribbon(7,0.075f,0);
        GlStateManager.enableDepth(); ribbon(12,0.28f,0);ribbon(3,0.95f,0);ribbon(0.8,0.90f,0.78f);
        if (curve.length()-headAt<VISIBLE_LENGTH) marker(end,opacity,trail.partial);
    }

    private static void append(double px,double py,double pz,double left) {
        if (count>=CAPACITY) return;
        if (count>0 && distance(px-x[count-1],py-y[count-1],pz-z[count-1])<1e-6) return;
        x[count]=px;y[count]=py;z[count]=pz;remaining[count]=left;count++;
    }

    private static void ribbon(double pixels,float alpha,float white) {
        if (count<2) return;
        Tessellator tess=Tessellator.getInstance();WorldRenderer wr=tess.getWorldRenderer();
        // All edge strips share one draw call, reducing the cost of layered glow.
        wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for (int i=0;i<count-1;i++) for (int side=-1;side<=1;side+=2) {
            vertex(wr,i,0,alpha,white);vertex(wr,i,side*pixels,0,white);
            vertex(wr,i+1,side*pixels,0,white);vertex(wr,i+1,0,alpha,white);
        }
        tess.draw();
    }
    private static void vertex(WorldRenderer wr,int i,double offset,float alpha,float white) {
        double w=width[i]*offset;
        wr.pos(x[i]+sx[i]*w,y[i]+sy[i]*w,z[i]+sz[i]*w)
                .color(red[i]+(1-red[i])*white,green[i]+(1-green[i])*white,blue[i]+(1-blue[i])*white,alpha*fade[i]).endVertex();
    }

    private static void marker(double[] end,float opacity,boolean partial) {
        Tessellator tess=Tessellator.getInstance();WorldRenderer wr=tess.getWorldRenderer();
        double radius=0.46+0.035*Math.sin(animationTime*3),py=end[1]+END_HEIGHT;
        wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for (int layer=0;layer<3;layer++) for (int i=0;i<64;i++) {
            if (layer==2 && i%32>=20) continue;
            double inner=radius+(layer==0?-0.12:layer==1?-0.024:0.11);
            double outer=radius+(layer==0?0.14:layer==1?0.024:0.15);
            double a=i*Math.PI/32+(layer==2?animationTime:0),b=(i+1)*Math.PI/32+(layer==2?animationTime:0);
            float r=partial?1f:layer==2?0.95f:0.39f,g=partial?0.65f:layer==2?0.42f:0.97f,bl=partial?0.25f:1f;
            float alpha=opacity*(layer==0?0.22f:0.9f),edge=layer==0?0:alpha;
            wr.pos(end[0]+Math.cos(a)*inner,py,end[2]+Math.sin(a)*inner).color(r,g,bl,alpha).endVertex();
            wr.pos(end[0]+Math.cos(a)*outer,py,end[2]+Math.sin(a)*outer).color(r,g,bl,edge).endVertex();
            wr.pos(end[0]+Math.cos(b)*outer,py,end[2]+Math.sin(b)*outer).color(r,g,bl,edge).endVertex();
            wr.pos(end[0]+Math.cos(b)*inner,py,end[2]+Math.sin(b)*inner).color(r,g,bl,alpha).endVertex();
        }
        tess.draw();
    }
    private static double trailHeight(double remaining) {
        return END_HEIGHT+(HEIGHT-END_HEIGHT)*RouteCurve.ease(remaining/4.0);
    }
    private static float channel(int a,int b,double t,double wave) { return (float)(((a&255)+((b&255)-(a&255))*t)/255.0*wave); }
    private static double distance(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
}
