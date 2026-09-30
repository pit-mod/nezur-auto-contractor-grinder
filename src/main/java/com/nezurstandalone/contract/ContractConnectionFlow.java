package com.nezurstandalone.contract;

import java.util.Locale;

/** AutoGrinder's observed lobby/limbo command sequence with explicit retry timing. */
public final class ContractConnectionFlow {
    public enum Location { UNKNOWN, PIT, LOBBY, LIMBO, DISCONNECTED }
    public enum Action { NONE, LEAVE, JOIN, RETURN_TO_SPAWN, RECONNECT }
    private Location location=Location.UNKNOWN;
    private long nextAt;
    private int attempts;
    private boolean swapping;
    public static boolean hypixelHost(String address) {
        if(address==null)return false;
        String host=address.trim().toLowerCase(Locale.ROOT).split(":",2)[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
    public void reset(){location=Location.UNKNOWN;nextAt=0;attempts=0;swapping=false;}
    public void observe(Location value,long now,long delay) {
        if(value==location)return;
        location=value;attempts=0;nextAt=now+delay;
        if(value==Location.PIT)swapping=false;
    }
    public void requestSwap(long now) {
        if(location!=Location.PIT || swapping)return;
        swapping=true;attempts=0;nextAt=now+1000;
    }
    public boolean swapping(){return swapping;}
    public long nextAt(){return nextAt;}
    public Action action(long now,boolean inSpawn) {
        if(now<nextAt)return Action.NONE;
        switch(location) {
            case PIT:return swapping?(inSpawn?Action.LEAVE:Action.RETURN_TO_SPAWN):Action.NONE;
            case LOBBY:return Action.JOIN;
            case LIMBO:return Action.LEAVE;
            case DISCONNECTED:return Action.RECONNECT;
            default:return Action.NONE;
        }
    }
    /** Advance only after CommandCoordinator accepted the action. */
    public void sent(long now){nextAt=now+(++attempts>=3?30000:10000);}
}
