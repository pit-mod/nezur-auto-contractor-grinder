package com.nezurstandalone.gui;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/** Vector-drawn navigation glyphs cached at high resolution for smooth small-scale edges. */
public final class NavigationIcons {
    private static int texture;
    private NavigationIcons() {}
    public static void preload(){upload();}
    private static void upload() {
        if(texture!=0)return;
        BufferedImage atlas=new BufferedImage(480,96,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=atlas.createGraphics();g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int icon=0;icon<5;icon++) {
            g.translate(icon*96,0);
            if(icon==0){for(int i=0;i<2;i++)for(int j=0;j<2;j++)g.fill(new RoundRectangle2D.Float(13+i*38,13+j*38,30,30,8,8));}
            else if(icon==1){
                g.setStroke(new BasicStroke(6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new RoundRectangle2D.Float(12,20,72,56,10,10));
                g.drawLine(36,22,36,74);g.drawLine(38,48,82,48);
            } else if(icon==2){
                g.setStroke(new BasicStroke(6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for(int i=0;i<3;i++){int y=24+i*24,cx=i==1?60:36;g.drawLine(13,y,83,y);g.fillOval(cx-10,y-10,20,20);}
            } else if(icon==4) {
                g.fillOval(33,10,30,30);
                g.fill(new RoundRectangle2D.Float(18,47,60,35,24,24));
            } else {
                g.fillOval(36,10,24,24);g.fillOval(9,24,20,20);g.fillOval(67,24,20,20);
                g.fill(new RoundRectangle2D.Float(27,42,42,35,23,23));
                g.fill(new RoundRectangle2D.Float(3,49,29,24,15,15));
                g.fill(new RoundRectangle2D.Float(64,49,29,24,15,15));
            }
            g.translate(-icon*96,0);
        }
        g.dispose();ByteBuffer bytes=BufferUtils.createByteBuffer(480*96*4);
        for(int y=0;y<96;y++)for(int x=0;x<480;x++){int c=atlas.getRGB(x,y);bytes.put((byte)255).put((byte)255).put((byte)255).put((byte)(c>>>24));}bytes.flip();
        texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,480,96,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
    }
    public static void draw(int icon,float cx,float cy,float size,int color) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        try {
            upload();GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,(color>>>24)/255f);
            float u=icon/5f,r=size/2;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u,0);GL11.glVertex2f(cx-r,cy-r);GL11.glTexCoord2f(u+.2f,0);GL11.glVertex2f(cx+r,cy-r);
            GL11.glTexCoord2f(u+.2f,1);GL11.glVertex2f(cx+r,cy+r);GL11.glTexCoord2f(u,1);GL11.glVertex2f(cx-r,cy+r);GL11.glEnd();
        } finally {GL11.glPopAttrib();}
    }
    public static void dispose(){if(texture!=0)GL11.glDeleteTextures(texture);texture=0;}
}
