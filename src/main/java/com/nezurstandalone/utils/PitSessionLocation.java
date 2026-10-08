package com.nezurstandalone.utils;

import com.nezurstandalone.contract.ContractConnectionFlow.Location;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Shared observation keeps every lobby/reconnect module on the same transfer grace period. */
public final class PitSessionLocation {
    private static final PitLocationTracker TRACKER=new PitLocationTracker();
    private PitSessionLocation(){}
    public static Location current(){
        Minecraft mc=Minecraft.getMinecraft();ServerData server=mc.getCurrentServerData();
        boolean supported=mc.thePlayer!=null&&mc.getNetHandler()!=null&&!mc.isSingleplayer()
                &&server!=null&&PitLocationTracker.supportedHost(server.serverIP);
        return TRACKER.observe(mc.theWorld,supported,Utils.getScoreboardTitle(),
                Utils.getScoreboardLines(),com.nezurstandalone.control.Clock.millis());
    }
}
