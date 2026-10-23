package com.nezurstandalone.control;
/** Hard native-action cadence, independent of render/queue scheduling. */
public final class ActionBudget {
    private static final long[] SHORT_WINDOWS={250_000_000L,350_000_000L,500_000_000L};
    private final Clock clock;
    private long lastAttack=Long.MIN_VALUE,lastUse=Long.MIN_VALUE;
    private boolean used;
    private double maxCps=14;
    private long nextNativeAttack=Long.MIN_VALUE;
    private final long[] recentAttacks=new long[32];
    private int recentHead,recentCount;
    public ActionBudget(Clock clock){this.clock=clock;}
    public void beginTick(){used=false;}
    public void maxCps(double cps){
        double rate=Math.max(0.1,Math.min(14,Double.isFinite(cps)?cps:14));
        if(rate!=maxCps&&lastAttack!=Long.MIN_VALUE)nextNativeAttack=lastAttack+(long)Math.ceil(1_000_000_000.0/rate);
        maxCps=rate;
    }
    public boolean available(boolean attack){return available(attack,false);}
    /** Native tick scheduling accounts for phase; all synthetic attacks share the same rolling ceiling. */
    public boolean available(boolean attack,boolean nativeCadence){
        if(used)return false;
        long now=clock.nanos();
        if(!attack)return lastUse==Long.MIN_VALUE||now-lastUse>=50_000_000L;
        expire(now);
        if(recentCount>=(int)Math.ceil(maxCps))return false;
        // A one-second cap alone allows phase debt to concentrate clicks into shorter bursts.
        // Check short native windows too; pacing is measured from actual invocation times.
        if(!paced(now))return false;
        if(!nativeCadence)return lastAttack==Long.MIN_VALUE||now-lastAttack>=(long)Math.ceil(1_000_000_000.0/maxCps);
        return nextNativeAttack==Long.MIN_VALUE||now-nextNativeAttack>=0;
    }
    public boolean invoke(boolean attack){return invoke(attack,false);}
    public boolean invoke(boolean attack,boolean nativeCadence){
        if(!available(attack,nativeCadence))return false;
        long now=clock.nanos();used=true;
        if(attack){
            long period=(long)Math.ceil(1_000_000_000.0/maxCps);
            long allowance=Math.min(49_000_000L,period-1_000_000L);
            long basis=nativeCadence&&lastAttack!=Long.MIN_VALUE&&now-lastAttack<=250_000_000L
                    ?Math.max(nextNativeAttack,now-allowance):now;
            nextNativeAttack=basis+period;lastAttack=now;
            remember(now);
        }else lastUse=now;
        return true;
    }
    /** Physical attacks remain unlimited, but subsequent automation must account for them. */
    public void observeAttack(){
        long now=clock.nanos();lastAttack=now;
        nextNativeAttack=now+(long)Math.ceil(1_000_000_000.0/maxCps);
        remember(now);
    }
    private boolean paced(long now){
        for(long window:SHORT_WINDOWS){
            int count=1;
            for(int i=recentCount-1;i>=0;i--){
                if(now-recentAttacks[(recentHead+i)%recentAttacks.length]>=window)break;
                count++;
            }
            if(count>(int)Math.ceil(maxCps*window/1_000_000_000.0))return false;
        }
        return true;
    }
    private void remember(long now){
        expire(now);
        if(recentCount==recentAttacks.length){recentHead=(recentHead+1)%recentAttacks.length;recentCount--;}
        recentAttacks[(recentHead+recentCount)%recentAttacks.length]=now;recentCount++;
    }
    private void expire(long now){
        while(recentCount>0&&now-recentAttacks[recentHead]>=1_000_000_000L){recentHead=(recentHead+1)%recentAttacks.length;recentCount--;}
    }
}
