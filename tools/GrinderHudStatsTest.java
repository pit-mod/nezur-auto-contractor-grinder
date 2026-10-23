import com.nezurstandalone.engine.GrinderHudStats;

public final class GrinderHudStatsTest {
    private static int checks;
    private static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    public static void main(String[] args){
        GrinderHudStats stats=new GrinderHudStats();
        stats.questStart("NIGHT QUEST! Kill 10 players!");
        check(stats.questGoal==10&&stats.questKills==0,"observed quest requirement");
        stats.chat("KILL! on [55] Target +21XP +20.90g",true);
        stats.chat("DOUBLE KILL! on [71] Target2 +12XP +16.50g",true);
        stats.chat("ASSIST! 40% on [57] Target +3XP +5.11g",true);
        check(stats.kills==2&&stats.assists==1&&stats.questKills==2,"quest progress uses kills, excludes assists");
        stats.chat("Bob: KILL! on [55] Target +21XP +20.90g",true);
        stats.chat("KILL! +21XP",true);
        check(stats.kills==2,"chat text and malformed messages cannot inflate kills");
        stats.chat("KILL! on [55] Target +21XP +20.90g",false);
        check(stats.kills==3&&stats.questKills==2,"ordinary kills do not inflate quest progress");
        stats.questDone("DONE! +1,000 XP! +2 Chunks of Vile",65000);
        check(stats.questXP==1000&&stats.questVile==2&&stats.lastQuestDuration==65000,"confirmed completion rewards");
        stats.questStart("NIGHT QUEST! Kill 20 players.");
        check(stats.questGoal==20&&stats.questKills==0&&stats.questVile==2,"new quest resets local progress, retains total rewards");
        stats.questDone("NIGHT QUEST! DONE! +500 XP! +1 Chunk of Vile",40000);
        check(stats.questXP==1500&&stats.questVile==3,"cumulative XP and Vile");
        stats.questDone("DONE! +100 XP!",1000);
        check(stats.questXP==1600&&stats.questVile==3,"no guessed Vile reward");
        check(GrinderHudStats.time(-1).equals("00:00"),"no negative countdown");
        check(GrinderHudStats.time(65000).equals("01:05"),"seconds and minutes");
        check(GrinderHudStats.time(13380000).equals("3h 43m"),"long session time");
        check(GrinderHudStats.number(Double.NaN).equals("--"),"unknown values stay unknown");
        check(GrinderHudStats.number(5699.54).equals("5.70k"),"useful wallet precision");
        check(GrinderHudStats.number(1_250_000).equals("1.25M"),"large totals fit");
        stats.life(false);stats.life(true);stats.life(true);stats.life(true);
        check(stats.deaths==1,"dead ticks count only one death");
        stats.life(false);stats.life(true);
        check(stats.deaths==2,"new life permits another death");
        System.out.println("GrinderHudStatsTest PASS checks="+checks);
    }
}
