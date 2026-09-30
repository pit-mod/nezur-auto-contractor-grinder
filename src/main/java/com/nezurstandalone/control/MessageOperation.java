package com.nezurstandalone.control;
/** An expected message cannot acknowledge a different generation, session or state. */
public final class MessageOperation {
    private long generation,session,deadline;private String state;private boolean pending;
    public long begin(long token,String state,long now,long duration){session=token;this.state=state;deadline=now+duration;pending=true;return ++generation;}
    public boolean accept(long id,long token,String state,long now,boolean schema){
        if(!pending||id!=generation||token!=session||!this.state.equals(state)||now>=deadline||!schema)return false;
        pending=false;return true;
    }
    public void cancel(){pending=false;generation++;}
}
