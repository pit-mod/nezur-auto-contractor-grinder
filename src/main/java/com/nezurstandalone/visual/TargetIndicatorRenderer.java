package com.nezurstandalone.visual;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** One depth-tested batch, no textures, and full state restoration after every target effect. */
public final class TargetIndicatorRenderer {
    private static final int STATE_BITS=GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT
            |GL11.GL_LIGHTING_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT;
    private static final boolean LITTLE_ENDIAN=java.nio.ByteOrder.nativeOrder()==java.nio.ByteOrder.LITTLE_ENDIAN;
    private static final ThreadLocal<Batch> BATCH=ThreadLocal.withInitial(Batch::new);
    private static final class Batch {
        private int[] vertices=new int[0];
        int[] pack(TargetIndicatorGeometry.Mesh mesh) {
            if(vertices.length!=mesh.count*4)vertices=new int[mesh.count*4];
            for(int i=0;i<mesh.count;i++) {
                int p=i*3,v=i*4,c=mesh.colors[i];
                vertices[v]=Float.floatToRawIntBits(mesh.positions[p]);
                vertices[v+1]=Float.floatToRawIntBits(mesh.positions[p+1]);
                vertices[v+2]=Float.floatToRawIntBits(mesh.positions[p+2]);
                vertices[v+3]=LITTLE_ENDIAN?(c&0xFF00FF00)|((c>>>16)&255)|((c&255)<<16):(c<<8)|(c>>>24);
            }
            return vertices;
        }
    }
    private final TargetIndicatorGeometry geometry=new TargetIndicatorGeometry();
    public void draw(TargetIndicatorGeometry.Options options,double time,double x,double y,double z,double width,double height,
                     float viewYaw,float viewPitch,boolean hurt,int seed) {
        drawMesh(geometry.build(options,time,width,height,viewYaw,viewPitch,hurt,seed),x,y,z);
    }
    public static void drawMesh(TargetIndicatorGeometry.Mesh mesh,double x,double y,double z) {
        if(mesh.count==0)return;
        // Preserve the caller's array buffer across this client-memory batch;
        // Lunar's following font pass may reuse its existing binding.
        int previousArrayBuffer=OpenGlHelper.vboSupported?GL11.glGetInteger(0x8894):0;
        GL11.glPushAttrib(STATE_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glPushMatrix();
        try {
            // Forge's ByteBuffer vertex pointers require client memory, not a VBO.
            if(OpenGlHelper.vboSupported)OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER,0);
            GL11.glTranslated(x,y,z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,mesh.primitive==GL11.GL_LINES?GL11.GL_ONE_MINUS_SRC_ALPHA:GL11.GL_ONE);
            if(mesh.primitive==GL11.GL_LINES){GL11.glLineWidth(1.5F);GL11.glEnable(GL11.GL_LINE_SMOOTH);}
            Tessellator tess=Tessellator.getInstance();WorldRenderer buffer=tess.getWorldRenderer();
            buffer.begin(mesh.primitive,DefaultVertexFormats.POSITION_COLOR);
            buffer.addVertexData(BATCH.get().pack(mesh));
            tess.draw();
        } finally {
            GL11.glPopMatrix();GL11.glPopClientAttrib();GL11.glPopAttrib();
            if(OpenGlHelper.vboSupported)OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER,previousArrayBuffer);
            GlStateManager.resetColor();
        }
    }
}
