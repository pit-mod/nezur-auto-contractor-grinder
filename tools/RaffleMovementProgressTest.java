import com.nezurstandalone.engine.RaffleMovementProgress;
public final class RaffleMovementProgressTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        RaffleMovementProgress p=new RaffleMovementProgress();
        check(!p.stalled("ticket",0,0,0,0));
        check(!p.stalled("ticket",1199,0,0,0));
        check(p.stalled("ticket",1200,0,0,0));
        check(!p.stalled("ticket",1201,0,0,0));
        check(!p.stalled("ticket",2200,.2,0,0));
        check(!p.stalled("ticket",3300,.2,0,0));
        check(p.stalled("ticket",3400,.2,0,0));
        check(!p.stalled("closer ticket",3401,.2,0,0));
        p.reset();check(!p.stalled("closer ticket",9000,.2,0,0));
        check(!p.stalled("closer ticket",10100,.3,0,0));
        check(!p.stalled("closer ticket",10101,.4,0,0));
        check(!p.stalled("closer ticket",11000,.4,0,0));
        check(p.stalled("closer ticket",11301,.4,0,0));
        System.out.println("RAFFLE_MOVEMENT_PROGRESS PASS 13 checks");
    }
}
