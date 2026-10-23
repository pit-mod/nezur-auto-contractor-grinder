package com.nezurstandalone.utils;

/** Protect an active package from population-driven travel, scoped to the current world. */
public final class CarePackageTravelHold {
    private Object world;
    private long until=Long.MIN_VALUE;
    public void context(Object value){if(world!=value){world=value;until=Long.MIN_VALUE;}}
    public void message(String text,long now){if(world!=null&&CarePackageSignal.announcement(text))until=Math.max(until,now+30000);}
    public boolean active(boolean live,long now){
        if(world==null)return false;
        if(live)until=Math.max(until,now+5000);
        return now<until;
    }
}
