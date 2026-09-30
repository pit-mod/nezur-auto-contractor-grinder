package com.nezurstandalone.control;
/** Authoritative result contract; an ACK alone, local prediction or unrelated update is insufficient. */
public final class MenuResultContract {
    public enum Result { WAITING,SUCCESS,REJECTED,UNKNOWN }
    private final long session,deadline;private final int window,sourceSlot;private final short action;
    private final java.util.Set<Integer> expected=new java.util.HashSet<>(),observed=new java.util.HashSet<>();
    private boolean acknowledged,rejected,wrong;
    private boolean vanilla;
    public MenuResultContract(long session,long now,int window,int sourceSlot,short action,int... changedSlots){
        this.session=session;deadline=now+5_000_000_000L;this.window=window;this.sourceSlot=sourceSlot;this.action=action;
        for(int slot:changedSlots)expected.add(slot);
    }
    /** Vanilla predicts locally; an exact accepted ACK suffices unless contradicted. */
    public static MenuResultContract vanilla(long session,long now,int window,int sourceSlot,short action,int... changedSlots) {
        MenuResultContract c=new MenuResultContract(session,now,window,sourceSlot,action,changedSlots);
        c.vanilla=true; return c;
    }
    public void acknowledge(long token,int window,short action,boolean accepted){if(token==session&&window==this.window&&action==this.action){if(accepted)acknowledged=true;else rejected=true;}}
    public void authoritative(long token,int window,int slot,boolean matches){if(token==session&&window==this.window&&expected.contains(slot)){if(matches)observed.add(slot);else{observed.remove(slot);wrong=true;}}}
    public Result result(long token,long now,int currentWindow){
        if(token!=session||currentWindow!=window||now>=deadline)return Result.UNKNOWN;
        if(rejected)return Result.REJECTED;
        if(wrong)return Result.UNKNOWN;
        return acknowledged&&(vanilla||(!expected.isEmpty()&&observed.containsAll(expected)))?Result.SUCCESS:Result.WAITING;
    }
    public int sourceSlot(){return sourceSlot;}
}
