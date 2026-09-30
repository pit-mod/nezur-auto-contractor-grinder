package com.nezurstandalone.contract;

/** Keeps positive server evidence through temporary missing sidebar updates. */
public final class ContractTracker {
    private ContractScoreboard.Snapshot confirmed;
    private long deadline;
    private boolean estimated;
    private int lastSidebarSeconds = -1;
    private boolean timerPaused = true;
    public boolean timerPaused() { return timerPaused; }
    private long absentSince=-1;
    private boolean seenOnSidebar;
    private boolean identityVerified;
    public boolean needsIdentityInspection(ContractScoreboard.Snapshot live){
        return live!=null && live.active && live.type==ContractOffer.Type.KILL_PLAYERS && !identityVerified;
    }

    private static boolean specializedKills(ContractOffer.Type type) {
        switch(type){
            case DIAMOND_SWORD_FINAL_BLOW: case NO_ARMOR_KILLS: case NO_PERK_KILLS:
            case FIST_MID_KILLS: case SNEAK_ATTACK_KILLS: case GRASS_KILLS:
            case LAVA_KILLS: case OBSIDIAN_KILLS: case BOW_TAG_KILLS: case FISH_DIAMOND_SWORD:
                return true;
            default:return false;
        }
    }
    public void clear() { identityVerified=false;confirmed=null;deadline=0;estimated=false;absentSince=-1;seenOnSidebar=false;lastSidebarSeconds=-1;timerPaused=true; }
    public boolean estimated() { return estimated; }
    public void confirm(ContractScoreboard.Snapshot evidence,long now) {
        if(evidence==null || !evidence.active)return;
        identityVerified=evidence.type!=ContractOffer.Type.UNKNOWN;
        update(evidence,now);
    }
    private void update(ContractScoreboard.Snapshot evidence,long now) {
        if(evidence==null || !evidence.active)return;
        absentSince=-1;
        if(evidence.remainingSeconds>=0) {
            deadline=now+evidence.remainingSeconds*1000L;
            estimated=false;
        } else if(confirmed==null) {
            deadline=now+300000L;
            estimated=true;
        }
        if(confirmed!=null && evidence.type==ContractOffer.Type.UNKNOWN && evidence.objective.isEmpty())
            confirmed=new ContractScoreboard.Snapshot(true,true,true,confirmed.type,
                    confirmed.current,confirmed.target,evidence.remainingSeconds,confirmed.objective);
        else confirmed=evidence;
    }
    public ContractScoreboard.Snapshot observe(ContractScoreboard.Snapshot live,long now,boolean completeNormalSidebar) {
        if(live!=null && live.validPit && live.readable && !live.active && completeNormalSidebar && confirmed!=null) {
            if(absentSince<0)absentSince=now;
            // Only a stable normal sidebar missing both contract fields proves disappearance.
            // The sidebar can briefly omit contract fields during refresh or world transfer.
            if(now-absentSince>=8000){clear();return live;}
        } else absentSince=-1;
        return observe(live,now);
    }
    public ContractScoreboard.Snapshot observe(ContractScoreboard.Snapshot live,long now) {
        timerPaused = live == null || !live.validPit || !live.readable || !live.active || live.remainingSeconds < 0;
        if (!timerPaused) lastSidebarSeconds = live.remainingSeconds;
        if(live==null || !live.validPit || !live.readable)return live;
        if(!live.active && confirmed!=null && confirmed.target>0 && confirmed.current>=confirmed.target) {
            clear();return live;
        }
        if(live.active){
            if(confirmed!=null && confirmed.target>0 && live.target>0 && confirmed.target!=live.target)
                identityVerified=false;
            // Generic Kills is progress evidence, not a replacement for an accepted weapon/perk task.
            if(confirmed!=null && live.type==ContractOffer.Type.KILL_PLAYERS
                    && specializedKills(confirmed.type) && (confirmed.target<=0 || confirmed.target==live.target))
                live=new ContractScoreboard.Snapshot(live.validPit,live.readable,true,confirmed.type,
                        live.current,live.target,live.remainingSeconds,confirmed.objective);
            seenOnSidebar=true;update(live,now);
        }
        if(confirmed!=null) {
            int seconds=lastSidebarSeconds;
            return new ContractScoreboard.Snapshot(true,true,true,confirmed.type,
                    confirmed.current,confirmed.target,seconds,confirmed.objective);
        }
        clear();return live;
    }
}
