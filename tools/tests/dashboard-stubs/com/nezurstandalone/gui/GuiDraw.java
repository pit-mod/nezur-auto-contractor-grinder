package com.nezurstandalone.gui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Pixel-font preview sink. Runs the production dashboard layout without launching Minecraft. */
public final class GuiDraw {
    public static Graphics2D g;
    private static BufferedImage font;
    private static final int[] widths=new int[256];
    public static float maxTextX,maxTextY;
    public static void loadFont(String path)throws Exception{
        font=ImageIO.read(new File(path));int cell=font.getWidth()/16;
        for(int c=0;c<256;c++){
            int last=cell-1;
            outer:for(;last>=0;last--)for(int y=0;y<cell;y++)if((font.getRGB((c%16)*cell+last,(c/16)*cell+y)>>>24)>0)break outer;
            widths[c]=c==32?4:(int)((last+1)*8f/cell)+1;
        }
    }
    public static int withAlpha(int color,float alpha){return color&0xffffff|Math.round((color>>>24)*Math.max(0,Math.min(1,alpha)))<<24;}
    public static int lerpColor(int a,int b,float t){
        int out=0;for(int shift=0;shift<=24;shift+=8)out|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;return out;
    }
    public static void shadow(float x,float y,float w,float h,float r,int spread,int alpha){
        for(int i=spread;i>0;i--)roundedRect(x-i,y-i,x+w+i,y+h+i,r+i,(alpha/(spread*3))<<24);
    }
    public static void rect(float a,float b,float c,float d,int color){g.setColor(new Color(color,true));g.fill(new Rectangle2D.Float(a,b,c-a,d-b));}
    public static void roundedRect(float a,float b,float c,float d,float radius,int color){g.setColor(new Color(color,true));g.fill(new RoundRectangle2D.Float(a,b,c-a,d-b,radius*2,radius*2));}
    public static void roundedRect(float a,float b,float c,float d,float radius,int fill,int border){roundedRect(a,b,c,d,radius,border);roundedRect(a+1,b+1,c-1,d-1,Math.max(0,radius-1),fill);}
    public static void circle(float x,float y,float radius,int color){g.setColor(new Color(color,true));g.fill(new Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2));}
    public static float textWidthScaled(String s,float scale){float width=0;for(char c:s.toCharArray())width+=c<256?widths[c]:6;return width*scale;}
    public static void textScaled(String s,float x,float y,float scale,int color,boolean shadow){
        if((color>>>24)<4)return;
        maxTextX=Math.max(maxTextX,x+textWidthScaled(s,scale));maxTextY=Math.max(maxTextY,y+8*scale);
        int cell=font.getWidth()/16;
        for(char c:s.toCharArray()){
            int code=c<256?c:'?';BufferedImage tinted=new BufferedImage(cell,cell,BufferedImage.TYPE_INT_ARGB);
            for(int py=0;py<cell;py++)for(int px=0;px<cell;px++){
                int sample=font.getRGB((code%16)*cell+px,(code/16)*cell+py);
                int alpha=(sample>>>24)*(color>>>24)/255;
                tinted.setRGB(px,py,alpha<<24|color&0xffffff);
            }
            AffineTransform tx=AffineTransform.getTranslateInstance(x,y);tx.scale(8d/cell*scale,8d/cell*scale);
            g.drawImage(tinted,tx,null);x+=(c<256?widths[c]:6)*scale;
        }
    }
}
