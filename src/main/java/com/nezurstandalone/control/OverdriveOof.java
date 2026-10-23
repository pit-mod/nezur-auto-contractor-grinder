package com.nezurstandalone.control;

import java.util.function.BooleanSupplier;
import java.util.List;

/** One explicit Overdrive command per life, bounded deferral and lifecycle cancellation. */
public final class OverdriveOof {
    private static final long REQUEST_MS=45000, RESPONSE_MS=10000;
    private boolean activationSeen, pending, sent;
    private boolean sidebarReady=true;
    private long expires, responseUntil;

    public boolean message(String text,String self,boolean enabled,boolean system,long now) {
        if(!enabled||!system||activationSeen||!ServerMessages.overdrive(text,self))return false;
        return activate(now);
    }
    private boolean activate(long now) {
        activationSeen=true;sidebarReady=false;pending=true;sent=false;expires=now+REQUEST_MS;return true;
    }
    private static String sidebarStatus(List<String> lines) {
        if(lines==null)return null;
        String status=null;
        for(String line:lines) {
            if(line==null)continue;
            String text=line.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "")
                    .replace('\u00a0',' ').replace('\u202f',' ').replace("\u200b", "").trim();
            if(!text.regionMatches(true,0,"Status:",0,7))continue;
            if(status!=null)return null;
            status=text.substring(7).trim();
        }
        return status==null||status.isEmpty()?null:status;
    }
    /** The active player's sidebar also recovers a missed announcement or enabling mid-streak. */
    public boolean sidebar(List<String> lines,boolean enabled,boolean inPit,boolean inSpawn,boolean dead,long now) {
        if(!enabled||!inPit){clear();return false;}
        String status=sidebarStatus(lines);
        if(inSpawn||dead){lifeEnded();return false;}
        if(status==null)return false;
        if(!status.equalsIgnoreCase("Overdrive")){sidebarReady=true;return false;}
        if(!sidebarReady||activationSeen)return false;
        return activate(now);
    }
    /** A stale Overdrive line after respawn must clear before sidebar activation can rearm. */
    public void lifeEnded(){activationSeen=pending=sent=false;expires=responseUntil=0;sidebarReady=false;}
    /** True while a live request needs exclusive gameplay; refused/throttled sends remain pending. */
    public boolean tick(boolean enabled,boolean inPit,boolean inSpawn,boolean dead,
                        boolean safe,long now,BooleanSupplier send) {
        if(!enabled||!inPit){clear();return false;}
        if(inSpawn||dead){lifeEnded();return false;}
        if(sent) {if(now<responseUntil)return true;sent=false;return false;}
        if(!pending)return false;
        if(now>=expires){pending=false;return false;}
        if(!safe)return false;
        if(send.getAsBoolean()){pending=false;sent=true;responseUntil=now+RESPONSE_MS;}
        return true;
    }
    public void rejected(long now) {
        if(sent&&now<expires){sent=false;pending=true;}
    }
    public boolean pending(){return pending;}
    public boolean sent(){return sent;}
    /** Ordinary interruption cancels work but retains the sidebar activation latch. */
    public void clear(){activationSeen=pending=sent=false;expires=responseUntil=0;}
    /** An explicit owner restart may detect the currently active streak again. */
    public void reset(){clear();sidebarReady=true;}
}
