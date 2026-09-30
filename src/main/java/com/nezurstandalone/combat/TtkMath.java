package com.nezurstandalone.combat;
/** Deterministic 1.8 armor/expected Protection approximation and travel-plus-hit estimate. */
public final class TtkMath {
    private TtkMath(){}
    public static double multiplier(int armor,double protection,int resistance){
        return Math.max(.02,(1-Math.min(20,Math.max(0,armor))*.04)
            *(1-Math.min(20,Math.max(0,protection))*.04)*(1-Math.min(4,Math.max(0,resistance))*.2));
    }
    public static double seconds(double hp,double multiplier,double damage,double distance,double reach,double speed,double hitSeconds,double hurtWait){
        return Math.max(0,distance-reach)/Math.max(.1,speed)+Math.ceil(Math.max(0,hp)/Math.max(.01,multiplier)/Math.max(.1,damage)-1e-9)*hitSeconds+Math.max(0,hurtWait);
    }
    public static boolean better(double candidate,double current,double improvement){return candidate<current*(1-improvement/100);}
}
