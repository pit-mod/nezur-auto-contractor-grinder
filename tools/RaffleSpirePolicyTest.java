import com.nezurstandalone.engine.RaffleController;
import com.nezurstandalone.engine.SpireEntryPolicy;
import com.nezurstandalone.combat.CombatAura;
import java.util.Arrays;

public final class RaffleSpirePolicyTest {
    private static int checks;
    private static void check(boolean condition,String reason) {checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args) {
        check(RaffleController.eventLine("Event: §e§lRAF💣FLE"),"Split Raffle sidebar");
        check(!RaffleController.eventLine("Raffle [32:00]"),"Scheduled Raffle is not active");
        check(!RaffleController.deposit(false,8),"Collect until nine");
        check(RaffleController.deposit(false,9),"Start deposit at nine");
        check(RaffleController.deposit(false,14),"Excess pickup starts deposit");
        check(RaffleController.deposit(true,2),"Partial deposit keeps depositing");
        check(!RaffleController.deposit(true,0),"Empty inventory returns to collection");
        check(!RaffleController.deposit(false,2),"New cycle starts collecting");
        check(SpireEntryPolicy.active(Arrays.asList("Event: §dSPI💣RE")),"Split active Spire");
        check(!SpireEntryPolicy.active(Arrays.asList("Spire [43:55]")),"Scheduled Spire is not active");
        check(SpireEntryPolicy.startSeconds("§dSPIRE! STARTING IN 1:20",100)==80,"Visible countdown");
        check(SpireEntryPolicy.startSeconds("SPIRE! STARTING IN 1:20",0)==-1,"Expired boss cannot hold spawn");
        check(SpireEntryPolicy.startSeconds("SPIRE! STARTING IN 999999999999:20",100)==-1,"Malformed timer rejected");
        check(SpireEntryPolicy.startSeconds("SPIRE! STARTING IN 1:99",100)==-1,"Invalid seconds rejected");
        check(SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("§dFLO💣OR: §f3")),"Split floor identifies entry");
        check(SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("Spire Floor: VII")),"Roman floor identifies entry");
        check(SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("Event: SPIRE","Floor: §cONE","Souls: 0 (#47)")),"Screenshot FLOOR ONE switches from rushing to combat");
        check(SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("Floor: NINE")),"Spelled-out upper floor identifies entry");
        check(!SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("Floor: --","Event: SPIRE")),"Empty floor is not entry evidence");
        check(!SpireEntryPolicy.inside("THE HYPIXEL PIT",Arrays.asList("Event: SPIRE")),"Global event does not mean inside");
        check(CombatAura.proximityTier(5,5)==0,"Five blocks is in nearby group");
        check(CombatAura.proximityTier(5.01,5)>CombatAura.proximityTier(4.9,5),"Far TTK winner cannot outrank nearby player");
        check(CombatAura.proximityTier(30,5)==1,"Far candidates remain available as fallback");
        check(CombatAura.proximityTier(5.1,0)==0,"Other consumers preserve default selection");
        System.out.println("RAFFLE_SPIRE_NEARBY_ASSERTIONS="+checks+" PASS");
    }
}
