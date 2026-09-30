package com.nezurstandalone.control;
/** Native invocation starts a transaction; only a later authoritative result completes it. */
public final class HealTransaction {
    public enum State { SELECT,BEGIN_USE,HOLDING,WAITING_RESULT,SUCCESS,INTERRUPTED,FAILED,CANCELLED }
    private State state=State.SELECT;private long session,started,ended;private boolean sustained;
    public HealTransaction(long session,boolean sustained){this.session=session;this.sustained=sustained;}
    public State state(){return state;}
    public void selected(){if(state==State.SELECT)state=State.BEGIN_USE;}
    public void invoked(long now){if(state==State.BEGIN_USE){started=now;state=sustained?State.HOLDING:State.WAITING_RESULT;ended=now;}}
    public boolean terminal(){return state==State.SUCCESS||state==State.INTERRUPTED||state==State.FAILED||state==State.CANCELLED;}
    public void cancel(){if(!terminal())state=State.CANCELLED;}
    public void observe(long token,long now,boolean using,boolean wasUsing,boolean authoritativeResult){
        if(terminal())return;
        if(token!=session){state=State.CANCELLED;return;}
        if(state==State.SELECT||state==State.BEGIN_USE)return;
        if(authoritativeResult){state=State.SUCCESS;return;}
        if(now-started>=4000){state=State.FAILED;return;}
        if(state==State.HOLDING&&wasUsing&&!using){state=State.WAITING_RESULT;ended=now;}
        if(state==State.WAITING_RESULT&&now-ended>=800)state=sustained?State.INTERRUPTED:State.FAILED;
    }
}
