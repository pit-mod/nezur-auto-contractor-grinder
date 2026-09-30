package com.nezurstandalone.contract;

/** Pure slot planning; the caller must still verify every server GUI transition. */
public final class ContractPerkPlan {
    private ContractPerkPlan() { }

    public static boolean protectedForKungFu(String name) {
        return "Vampire".equalsIgnoreCase(name) || "Golden Heads".equalsIgnoreCase(name);
    }

    public static boolean gappleCooldownReady(long now,long lastSuccessfulDeath,long cooldown) {
        return lastSuccessfulDeath==0 || now-lastSuccessfulDeath>=Math.max(12000,cooldown);
    }
    public static boolean gappleOofReady(long now,long launch,long lastSuccessfulDeath,long delay,long cooldown,boolean airborne) {
        return airborne && launch>0 && now-launch>=delay && gappleCooldownReady(now,lastSuccessfulDeath,cooldown);
    }
    /** 0=apple ready, 1=natural deaths, 2=bounded failure. Apple availability wins over death guidance. */
    public static int gappleInitialAction(boolean apple,int deaths,int maximum) {
        return apple?0:deaths>=maximum?2:1;
    }
    public static boolean gappleComplete(int current,int target){return target>0 && current>=target;}
    public static boolean gappleEligible(String[] perks) {
        if(perks==null)return false;
        for(String p:perks)if(protectedForKungFu(p))return true;
        return false;
    }
    public static String[] gappleLoadout(String[] perks) {
        String[] result=snapshot(perks);
        for(int i=0;i<result.length;i++)if(protectedForKungFu(result[i]))result[i]=null;
        return result;
    }

    public static int kungFuSlot(String[] equipped, String[] configured, boolean[] usable) {
        if (equipped.length != configured.length || equipped.length != usable.length) {
            throw new IllegalArgumentException("perk slot arrays differ");
        }
        for (int i = 0; i < equipped.length; i++) {
            if ("Kung Fu Knowledge".equalsIgnoreCase(equipped[i])) return i;
        }
        for (int i = 0; i < equipped.length; i++) {
            if (usable[i] && !protectedForKungFu(equipped[i])
                    && !protectedForKungFu(configured[i])) return i;
        }
        return -1;
    }

    /** Null means this exact slot was originally empty. */
    public static String[] snapshot(String[] equipped) {
        return equipped.clone();
    }
    /** Recognize a pad launch even if the first high-velocity update was missed. */
    public static boolean gappleLaunchDetected(boolean grounded,double motionY,double heightAbovePad,double horizontalSq){
        return !grounded && horizontalSq<36 && (motionY>.25 || heightAbovePad>1.25);
    }
    public static boolean gappleWaitReached(double horizontalSq,double heightError,double padHorizontalSq,boolean grounded){
        return grounded && horizontalSq<=2.25 && Math.abs(heightError)<=1.25 && padHorizontalSq>=36;
    }

}
