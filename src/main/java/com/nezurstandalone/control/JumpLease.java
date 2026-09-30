package com.nezurstandalone.control;
/** Single airborne cycle shared by all request sources. Pure, monotonic-clock state. */
public final class JumpLease {
    public enum State { IDLE, PRESSED, AIRBORNE, COOLDOWN }
    private Object owner, landingOwner; private long session, generation, deadline, next;
    private State state=State.IDLE;
    public State state(){return state;}
    public long generation(){return generation;}
    public Object owner(){return owner;}
    public void observe(long token,long now,boolean grounded){
        if(token!=session){session=token;owner=null;landingOwner=null;state=State.IDLE;next=0;generation++;}
        if(state==State.PRESSED && !grounded){state=State.AIRBORNE;landingOwner=owner;owner=null;}
        else if(state==State.PRESSED && now>=deadline){owner=null;state=State.COOLDOWN;next=now+250_000_000L;}
        if(state==State.AIRBORNE && grounded){state=State.COOLDOWN;next=now+250_000_000L;}
        if(state==State.COOLDOWN && now>=next && grounded){state=State.IDLE;landingOwner=null;}
    }
    public boolean request(Object who,long token,long now,boolean grounded,boolean collisionClear){
        if(who==null)throw new IllegalArgumentException("jump source");
        observe(token,now,grounded);
        if(state==State.PRESSED){if(owner.equals(who)&&!collisionClear)cancel(who,now);return owner!=null&&owner.equals(who)&&collisionClear;}
        // A completed jump may repeat for its source without an extra landing pause.
        // Other sources, failed takeoffs, and cancelled presses retain the cooldown.
        if(state==State.COOLDOWN && grounded && collisionClear && who.equals(landingOwner)){
            state=State.IDLE;landingOwner=null;
        }
        if(state!=State.IDLE || !grounded || !collisionClear)return false;
        owner=who;landingOwner=null;generation++;state=State.PRESSED;deadline=now+150_000_000L;return true;
    }
    public void cancel(Object who,long now){
        if(owner!=null && owner.equals(who)){owner=null;generation++;state=State.COOLDOWN;next=now+250_000_000L;}
    }
    public boolean down(){return state==State.PRESSED&&owner!=null;}
}
