package com.nezurstandalone.control;

import java.util.function.Consumer;

/** Bounded local accounting of synthetic native clicks. Observes packets; never changes dispatch. */
public final class CombatPacketAudit {
    private static final CombatPacketAudit LIVE=new CombatPacketAudit(Clock.SYSTEM,System.out::println);
    private final Clock clock;
    private final Consumer<String> output;
    private final long[] recent=new long[128];
    private int head,count,peak,peak350,peak500,tick,lastAttackTick=Integer.MIN_VALUE;
    private long session=Long.MIN_VALUE,reportAt,lastWarning=Long.MIN_VALUE;
    private int swings,attacks,unswung,duplicates,tokens;
    private boolean active,overflow;

    public CombatPacketAudit(Clock clock,Consumer<String> output){this.clock=clock;this.output=output;}
    public static void onBegin(int tick,long session,boolean synthetic){LIVE.begin(tick,session,synthetic);}
    public static void onSwing(){LIVE.swing();}
    public static void onAttack(){LIVE.attack();}
    public static void onEnd(){LIVE.end();}

    public void begin(int tick,long session,boolean synthetic){
        if(this.session!=session){this.session=session;head=count=peak=peak350=peak500=swings=attacks=unswung=duplicates=0;
            lastAttackTick=Integer.MIN_VALUE;reportAt=clock.nanos();lastWarning=Long.MIN_VALUE;overflow=false;}
        active=synthetic;this.tick=tick;tokens=0;
    }
    public void swing(){if(active){swings++;tokens++;}}
    public void attack(){
        if(!active)return;
        long now=clock.nanos();expire(now);attacks++;
        if(tokens==0)unswung++;else tokens--;
        if(lastAttackTick==tick)duplicates++;lastAttackTick=tick;
        if(count==recent.length){head=(head+1)%recent.length;count--;overflow=true;}
        recent[(head+count)%recent.length]=now;count++;peak=Math.max(peak,count);
        peak350=Math.max(peak350,within(now,350_000_000L));
        peak500=Math.max(peak500,within(now,500_000_000L));
    }
    public void end(){
        if(!active)return;active=false;
        long now=clock.nanos();
        boolean anomalous=unswung>0||duplicates>0||peak>14||peak350>5||peak500>7||overflow;
        if(attacks==0||now-reportAt<5_000_000_000L
                &&(!anomalous||lastWarning!=Long.MIN_VALUE&&now-lastWarning<5_000_000_000L))return;
        try{output.accept("[Nezur] Combat packet audit: window_ms="+(now-reportAt)/1_000_000L
                +" swings="+swings+" attacks="+attacks+" attacks_without_prior_swing="+unswung
                +" duplicate_client_tick="+duplicates+" peak_1s_attacks="+peak
                +" peak_350ms_attacks="+peak350+" peak_500ms_attacks="+peak500+" overflow="+overflow);}
        catch(RuntimeException unavailable){/* Diagnostics cannot interrupt a native action. */}
        if(anomalous)lastWarning=now;
        swings=attacks=unswung=duplicates=peak=peak350=peak500=0;overflow=false;reportAt=now;
    }
    private int within(long now,long window){int result=0;for(int i=count-1;i>=0;i--){
        if(now-recent[(head+i)%recent.length]>=window)break;result++;}return result;}
    private void expire(long now){while(count>0&&now-recent[head]>=1_000_000_000L){head=(head+1)%recent.length;count--;}}
}
