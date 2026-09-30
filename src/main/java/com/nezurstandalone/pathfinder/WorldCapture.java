package com.nezurstandalone.pathfinder;
import com.nezurstandalone.control.*;
import net.minecraft.client.Minecraft;
/** One active capture; later requests revoke earlier worker publications immediately. */
public final class WorldCapture {
    private static CaptureRevision active; private static long generation;
    static CaptureRevision begin(Object world,int x0,int y0,int z0,int x1,int y1,int z1){
        cancel();Minecraft mc=Minecraft.getMinecraft();
        return active=new CaptureRevision(world,mc.thePlayer,ClientSession.current(),generation,x0,y0,z0,x1,y1,z1);
    }
    static boolean valid(CaptureRevision ticket){Minecraft mc=Minecraft.getMinecraft();return active==ticket && ticket.valid(mc.theWorld,mc.thePlayer,ClientSession.current(),generation);}
    static void cancel(){if(active!=null)active.cancel();active=null;generation++;}
    public static void changed(Object world,int x0,int y0,int z0,int x1,int y1,int z1){
        if(active!=null)active.changed(world,x0,y0,z0,x1,y1,z1);
    }
}
