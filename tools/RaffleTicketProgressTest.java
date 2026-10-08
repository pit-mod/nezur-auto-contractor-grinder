import com.nezurstandalone.engine.RaffleTicketProgress;

public final class RaffleTicketProgressTest {
    private static void check(boolean ok,String reason) {if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args) {
        check(RaffleTicketProgress.withinPickup(0,0,0,1,0,0),"One block endpoint allowed");
        check(!RaffleTicketProgress.withinPickup(0,0,0,1.01,0,0),"Truncated endpoint rejected");
        check(!RaffleTicketProgress.withinPickup(0,0,0,0,1.01,0),"Unreachable vertical ticket rejected");
        RaffleTicketProgress p=new RaffleTicketProgress();p.begin(100,10);
        check(!p.expired(5099,10),"Allow initial movement");
        check(p.expired(5100,10),"Stationary attempt expires");
        p.begin(100,10);check(!p.expired(4100,8),"Real progress extends movement window");
        check(!p.expired(9099,8),"Extended window remains active");
        check(p.expired(9100,8),"Stall after progress expires");
        p.begin(100,2);check(!p.expired(3099,1),"Allow pickup delay");
        check(p.expired(3100,.3),"Persistent close ticket expires despite movement");
        p.begin(100,2);p.expired(1100,4);
        check(p.expired(3100,2),"Oscillation cannot reset pickup deadline");
        p.begin(100,80);
        for(int i=1;i<30;i++)check(!p.expired(100+i*1000,80-i),"Moving attempt before hard deadline");
        check(p.expired(30100,50),"Hard deadline bounds continually moving attempts");
        p.begin(30100,8);check(!p.expired(30100,8),"New ticket gets independent deadline");
        System.out.println("RAFFLE_TICKET_PROGRESS PASS");
    }
}
