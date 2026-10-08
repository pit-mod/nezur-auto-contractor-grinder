import com.nezurstandalone.engine.BlockheadController;
import com.nezurstandalone.engine.RobberyController;

/** Focused decisions that can be verified without a running Minecraft world. */
public final class AutoEventPolicyTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Method combatState=com.nezurstandalone.engine.GrinderEngine.class.getDeclaredMethod(
                "isCombatState",com.nezurstandalone.engine.GrinderEngine.State.class);
        combatState.setAccessible(true);
        for(com.nezurstandalone.engine.GrinderEngine.State state:com.nezurstandalone.engine.GrinderEngine.State.values()) {
            boolean expected=state==com.nezurstandalone.engine.GrinderEngine.State.FIGHTING
                    || state==com.nezurstandalone.engine.GrinderEngine.State.SPIRE_FIGHTING
                    || state==com.nezurstandalone.engine.GrinderEngine.State.ROBBERY_FIGHTING;
            check(((Boolean)combatState.invoke(null,state))==expected,"Combat permission for "+state);
        }
        check(BlockheadController.letters("Event: §9§lBLOCK💣§9§lHEAD").equals("eventblockhead"),"Real split event");
        check(RobberyController.rank("§e#1💣0§f, 500g")==10,"Split top-ten rank");
        check(RobberyController.rank("Position: #21")==21,"Position rank");
        check(RobberyController.rank("Remaining: 02:10")==-1,"Timer is not a rank");
        check(RobberyController.rank("#999999999999999999999")==-1,"Malformed rank rejected");
        boolean holding=RobberyController.hold(false,10);
        check(holding,"Top ten starts holding");
        holding=RobberyController.hold(holding,19);
        check(holding,"Top nineteen keeps holding");
        holding=RobberyController.hold(holding,-1);
        check(holding,"Unreadable rank does not throw away a banked position");
        holding=RobberyController.hold(holding,20);
        check(holding,"Top twenty stays safe");
        check(!RobberyController.hold(holding,21),"Rank twenty-one resumes hunting");
        check(!RobberyController.hold(false,15),"Rank fifteen keeps hunting until top ten");
        check(RobberyController.gold("§6§l2,500g")==2500,"Real bounty nametag");
        check(RobberyController.gold("[3.5kg]")==3500,"Abbreviated bounty");
        check(RobberyController.gold("[20❤] Player123")==0,"Player health/name is not bounty");
        System.out.println("AUTO_EVENT_POLICY_ASSERTIONS="+checks+" PASS");
    }
}
