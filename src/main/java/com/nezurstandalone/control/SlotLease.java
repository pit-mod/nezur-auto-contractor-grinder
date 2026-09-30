package com.nezurstandalone.control;
/** Explicit preemption never transfers restoration rights to an obsolete generation. */
public final class SlotLease {
    private Object owner; private long session,generation; private int original,selected,priority;
    private void refresh(long token,int current){if(owner!=null&&(session!=token||selected!=current)){owner=null;generation++;}}
    public Object owner(){return owner;}
    public long generation(){return generation;}
    public boolean available(Object who,long token,int current){refresh(token,current);return owner==null||owner==who;}
    public boolean acquire(Object who,long token,int current){return acquire(who,token,current,0,false);}
    public boolean acquire(Object who,long token,int current,int requestedPriority,boolean preempt){
        if(who==null)throw new IllegalArgumentException("lease owner");refresh(token,current);
        if(owner==who)return true;
        if(owner!=null&&(!preempt||requestedPriority<=priority))return false;
        // Restore the root slot after an explicit preemption chain, not the interrupted action's temporary slot.
        int root=owner==null?current:original;
        owner=who;session=token;original=root;selected=current;priority=requestedPriority;generation++;return true;
    }
    public boolean owns(Object who,long token,int current){refresh(token,current);return who!=null&&owner==who;}
    public boolean owns(Object who,long token,int current,long expectedGeneration){return generation==expectedGeneration&&owns(who,token,current);}
    public void selected(int slot){if(owner==null)throw new IllegalStateException("no lease");selected=slot;}
    public int release(Object who,long token,int current,boolean restore){
        if(!owns(who,token,current))return current;
        owner=null;generation++;return restore?original:current;
    }
}
