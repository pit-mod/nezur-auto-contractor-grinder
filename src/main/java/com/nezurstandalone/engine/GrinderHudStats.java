package com.nezurstandalone.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only counters. Rewards are recorded only after the engine accepts their server message. */
public final class GrinderHudStats {
    private static final Pattern GOAL=Pattern.compile("^NIGHT QUEST! Kill ([0-9]+) players[.!]?$");
    private static final Pattern KILL=Pattern.compile("^(?:(?:DOUBLE|TRIPLE|QUADRA|PENTA|MULTI|MEGA) )?KILL! on .+\\+[0-9,]+\\s*XP.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIST=Pattern.compile("^ASSIST! [0-9]+% on .+\\+[0-9,]+\\s*XP.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASSIC_GOAL=Pattern.compile("^NIGHT QUEST! Kill ([0-9]+) players(?: without mystic items)?(?: without dying or returning to spawn)?[.!]?$");
    private static final Pattern XP=Pattern.compile("\\+([0-9,]+) XP!",Pattern.CASE_INSENSITIVE);
    private static final Pattern VILE=Pattern.compile("\\+([0-9,]+) Chunks? of Vile",Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASSIC_KILL=Pattern.compile("^(?:(?:DOUBLE|TRIPLE|QUADRA|PENTA|MULTI|MEGA) )?KILL!(?: \\([0-9]+\\))? on .+\\+[0-9,]+\\s*XP.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASSIC_ASSIST=Pattern.compile("^ASSIST! [0-9]+% on .+(?:\\+[0-9,]+\\s*XP.*|\\+[0-9,.]+g)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PICKUP=Pattern.compile("^GOLD PICKUP! from the ground ([0-9,]+(?:\\.[0-9]+)?)g$",Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_REWARD=Pattern.compile("^Your rewards: (?:\\+[0-9,.]+\\s*(?:XP|g|Renown)\\s*)+$",Pattern.CASE_INSENSITIVE);
    public static boolean rewardMessage(String message){return CLASSIC_KILL.matcher(message).matches()||CLASSIC_ASSIST.matcher(message).matches()
            ||EVENT_REWARD.matcher(message).matches()||com.nezurstandalone.control.ServerMessages.questDone(message,true);}
    public static double pickupGold(String message){Matcher m=PICKUP.matcher(message);if(!m.matches())return 0;
        try{return Double.parseDouble(m.group(1).replace(",",""));}catch(NumberFormatException bad){return 0;}}
    public int questGoal,questKills,questVile,questXP,deaths,kills,assists;
    public long lastQuestDuration;
    private boolean wasDead;
    private long deathAt=Long.MIN_VALUE;
    private boolean awaitingDeadTick;
    public void life(boolean dead){life(dead,System.nanoTime()/1000000L);}
    public void life(boolean dead,long now){
        if(dead&&!wasDead){
            if(!awaitingDeadTick||deathAt==Long.MIN_VALUE||now-deathAt<0||now-deathAt>1500)deaths++;
            deathAt=now;awaitingDeadTick=false;
        }
        wasDead=dead;
    }
    /** A server death message/status covers Pit respawns that never expose a dead client tick. */
    public void serverDeath(long now){
        if(deathAt!=Long.MIN_VALUE&&now-deathAt>=0&&now-deathAt<=1500)return;
        deaths++;deathAt=now;awaitingDeadTick=true;
    }
    public void questStart(String message){questStart(message,false);}
    public void questStart(String message,boolean classic){Matcher m=(classic?CLASSIC_GOAL:GOAL).matcher(message);questGoal=m.matches()?number(m.group(1)):0;questKills=0;}
    public void chat(String message,boolean questActive){
        chat(message,questActive,System.nanoTime()/1000000L);
    }
    public void chat(String message,boolean questActive,long now){chat(message,questActive,now,false);}
    public void chat(String message,boolean questActive,long now,boolean classic){
        if(message.equals("DEATH!")||message.startsWith("DEATH! "))serverDeath(now);
        if((classic?CLASSIC_KILL:KILL).matcher(message).matches()){kills++;if(questActive)questKills++;}
        else if((classic?CLASSIC_ASSIST:ASSIST).matcher(message).matches())assists++;
    }
    public void questDone(String message,long duration){
        questVile+=amount(VILE,message);questXP+=amount(XP,message);lastQuestDuration=Math.max(0,duration);
    }
    private static int number(String value){try{return Integer.parseInt(value.replace(",",""));}catch(NumberFormatException invalid){return 0;}}
    private static int amount(Pattern pattern,String message){Matcher m=pattern.matcher(message);return m.find()?number(m.group(1)):0;}
    public static String time(long millis){
        long seconds=Math.max(0,millis)/1000,h=seconds/3600,m=(seconds%3600)/60;
        return h>0?String.format(java.util.Locale.ROOT,"%dh %02dm",h,m):String.format(java.util.Locale.ROOT,"%02d:%02d",m,seconds%60);
    }
    public static String number(double value){
        if(!Double.isFinite(value))return "--";
        if(value>=1_000_000)return String.format(java.util.Locale.ROOT,"%.2fM",value/1_000_000);
        if(value>=10_000)return String.format(java.util.Locale.ROOT,"%.1fk",value/1000);
        if(value>=1000)return String.format(java.util.Locale.ROOT,"%.2fk",value/1000);
        return String.format(java.util.Locale.ROOT,value==Math.rint(value)?"%.0f":"%.1f",value);
    }
}
