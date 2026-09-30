package com.nezurstandalone.control;
/** Hard native-action cadence, independent of render/queue scheduling. */
public final class ActionBudget {
    private final Clock clock;
    private long lastAttack=Long.MIN_VALUE,lastUse=Long.MIN_VALUE;
    private boolean used;
    private double maxCps=14;
    public ActionBudget(Clock clock){this.clock=clock;}
    public void beginTick(){used=false;}
    public void maxCps(double cps){maxCps=Math.max(0.1,Math.min(14,cps));}
    public boolean available(boolean attack){return !used&&(attack?(lastAttack==Long.MIN_VALUE||clock.nanos()-lastAttack>=(long)Math.ceil(1_000_000_000.0/maxCps)):(lastUse==Long.MIN_VALUE||clock.nanos()-lastUse>=50_000_000L));}
    public boolean invoke(boolean attack){if(!available(attack))return false;used=true;if(attack)lastAttack=clock.nanos();else lastUse=clock.nanos();return true;}
}
