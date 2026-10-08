package com.nezurstandalone.utils;

import com.nezurstandalone.contract.ContractConnectionFlow.Location;
import java.util.List;
import java.util.Locale;

/** Sidebar transfers are temporarily unknown; a persistent missing sidebar is Limbo. */
public final class PitLocationTracker {
    private Object world;
    private long emptySince=-1;
    public static boolean supportedHost(String address){
        if(address==null)return false;
        String host=address.trim().toLowerCase(Locale.ROOT).split(":",2)[0];
        return host.equals("hypixel.net")||host.endsWith(".hypixel.net")
                ||host.equals("pitclassic.net")||host.endsWith(".pitclassic.net");
    }
    public Location observe(Object currentWorld,boolean supported,String title,List<String> lines,long now){
        if(currentWorld==null||!supported){world=null;emptySince=-1;return Location.UNKNOWN;}
        if(world!=currentWorld){world=currentWorld;emptySince=-1;}
        String heading=clean(title);
        if(heading.isEmpty()){
            if(emptySince<0)emptySince=now;
            return now-emptySince>=5000?Location.LIMBO:Location.UNKNOWN;
        }
        emptySince=-1;
        if(heading.equals("LIMBO"))return Location.LIMBO;
        if(heading.equals("THE HYPIXEL PIT")||heading.equals("THE PIT CLASSIC")
                ||heading.equals("SPIRE")||heading.startsWith("SPIRE FLOOR"))return Location.PIT;
        if(heading.equals("HYPIXEL")||heading.contains("LOBBY"))return Location.LOBBY;
        if(heading.equals("PIT CLASSIC"))for(String line:lines)
            if(clean(line).contains("NETWORK LOBBY"))return Location.LOBBY;
        return Location.UNKNOWN;
    }
    private static String clean(String value){return value==null?"":value.replaceAll("§.","").trim().toUpperCase(Locale.ROOT);}
}
