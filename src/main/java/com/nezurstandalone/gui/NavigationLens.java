package com.nezurstandalone.gui;

import com.nezurstandalone.utils.ScreenScale;
import org.lwjgl.opengl.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Copies the capsule and its nearby backdrop so protruding glass refracts real content. */
public final class NavigationLens {
    private static int texture, program, textureW, textureH;
    private static int source, origin, dimensions, center, size, radius, zoom, lift, opacity,scale, material, tint;
    private static int copyX, copyY, viewportX, viewportY, viewportH;
    private static float pixelScale;
    private static boolean failed, captured;
    private static final java.nio.IntBuffer VIEWPORT=org.lwjgl.BufferUtils.createIntBuffer(16);
    private NavigationLens() {}
    public static void capture(float x,float y,float width,float height) {
        captured=false;
        if(failed || !GLContext.getCapabilities().OpenGL20)return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if(program==0)initialize();
            java.nio.IntBuffer viewport=VIEWPORT;viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
            viewportX=viewport.get(0);viewportY=viewport.get(1);viewportH=viewport.get(3);
            int viewportW=viewport.get(2);
            pixelScale=viewportW/(float)ScreenScale.get().getScaledWidth();
            int left=Math.max(0,(int)Math.floor(x*pixelScale));
            int top=Math.max(0,(int)Math.floor(y*pixelScale));
            int right=Math.min(viewportW,(int)Math.ceil((x+width)*pixelScale));
            int bottom=Math.min(viewportH,(int)Math.ceil((y+height)*pixelScale));
            int w=right-left,h=bottom-top;
            if(w<=0 || h<=0)return;
            if(texture==0)texture=GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            if(w!=textureW || h!=textureH) {
                textureW=w;textureH=h;
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            }
            copyX=viewportX+left;copyY=viewportY+viewportH-bottom;
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,copyX,copyY,w,h);
            captured=true;
        } catch(RuntimeException e) {
            dispose();
            failed = true;
            System.err.println("[Nezur] Navigation lens unavailable: " + e.getMessage());
        } finally {GL11.glPopAttrib();GL13.glActiveTexture(active);}
    }

    public static boolean draw(float x,float y,float width,float height,float magnification,float held,float appear) {
        return drawSurface(x,y,width,height,magnification,held,appear,false,0);
    }

    public static boolean drawBackdrop(float x,float y,float width,float height,float appear,int color) {
        // Preserve the existing Panel Opacity preference, including fully transparent glass.
        return drawSurface(x,y,width,height,1,0,appear*((color>>>24)/255f),true,color);
    }

    private static boolean drawSurface(float x,float y,float width,float height,float magnification,float held,float appear,boolean backdrop,int color) {
        if(!captured)return false;
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,1,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL20.glUseProgram(program);
            GL20.glUniform1i(source,0);GL20.glUniform2f(origin,copyX,copyY);GL20.glUniform2f(dimensions,textureW,textureH);
            GL20.glUniform2f(center,viewportX+(x+width/2)*pixelScale,viewportY+viewportH-(y+height/2)*pixelScale);
            GL20.glUniform2f(size,width,height);GL20.glUniform1f(radius,Math.min(width,height)/2);
            GL20.glUniform1f(zoom,magnification);GL20.glUniform1f(lift,held);GL20.glUniform1f(opacity,appear);GL20.glUniform1f(scale,pixelScale);
            GL20.glUniform1f(material,backdrop?1:0);
            GL20.glUniform3f(tint,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+width,y);
            GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+width,y+height);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+height);GL11.glEnd();
            return true;
        } finally {GL20.glUseProgram(previous);GL11.glPopAttrib();GL13.glActiveTexture(active);}
    }

    private static void initialize() {
        int vertex=0,fragment=0;
        try {
            vertex=shader(GL20.GL_VERTEX_SHADER,"glass.vert");fragment=shader(GL20.GL_FRAGMENT_SHADER,"navigation-lens.frag");
            program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,1024));
            source=uniform("content");origin=uniform("captureOrigin");dimensions=uniform("captureSize");center=uniform("lensCenter");
            size=uniform("size");radius=uniform("radius");zoom=uniform("magnification");lift=uniform("held");opacity=uniform("appear");scale=uniform("pixelsPerUnit");
            material=uniform("backdrop");tint=uniform("tint");
        } finally {if(vertex!=0)GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);}
    }
    private static int uniform(String name){return GL20.glGetUniformLocation(program,name);}
    private static int shader(int type,String name) {
        try(InputStream in=NavigationLens.class.getResourceAsStream("/assets/nezurstandalone/shaders/"+name)) {
            if(in==null)throw new IOException("Missing "+name);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;
            while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
            int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,new String(bytes.toByteArray(),StandardCharsets.UTF_8));GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(shader,1024);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}
            return shader;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static void dispose(){if(texture!=0)GL11.glDeleteTextures(texture);if(program!=0)GL20.glDeleteProgram(program);texture=program=textureW=textureH=0;captured=false;failed=false;}
}
