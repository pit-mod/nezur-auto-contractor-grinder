import com.nezurstandalone.engine.LoadoutPlanner;
import com.nezurstandalone.engine.LoadoutConfirmation;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import com.nezurstandalone.utils.MenuTransition;
import java.util.*;

/** Reproduces chooser -> generic Confirm -> root, plus cancellation and lost paid responses. */
public final class LoadoutConfirmationTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static List<String> lore(String... text){return Arrays.asList(text);}
    private static LoadoutPlanner planner(Kind kind,double gold) {
        LoadoutPlanner p=new LoadoutPlanner(message -> {});p.context("Alice@Pit");p.requirements(120,false);
        p.define(kind,"Golden Heads",500,10,0);p.define(kind,"Other",2000,10,0);
        p.configure(kind,1,"Golden Heads",true);p.configure(kind,2,"Other",true);
        p.observeSlot(kind,1,lore("Click to choose perk!"),0);p.observeSlot(kind,2,lore("Click to choose perk!"),0);
        p.observeChoice(kind,"Golden Heads",lore("Cost: 500g","Click to purchase!"));
        p.observeChoice(kind,"Other",lore("Cost: 2000g","Click to purchase!"));p.gold(gold,0);
        check(p.begin(p.next(null,0),0),"affordable selection begins");p.applied(0);return p;
    }
    public static void main(String[] args) {
        for(Kind kind:Kind.values()) {
            LoadoutPlanner p=planner(kind,700);Slot operation=p.pending();Object chooser=new Object(),confirm=new Object();
            LoadoutConfirmation binding=new LoadoutConfirmation();binding.begin(operation,chooser);
            MenuTransition gate=new MenuTransition();gate.begin(chooser,"choosePerk","native chooser prediction",0,"confirm","upgrades","killstreaks");
            check(!binding.matches(operation,confirm),"unobserved generic confirm is unrelated");
            binding.contents(confirm);check(!binding.matches(operation,confirm),"server packet alone is insufficient");
            check(gate.observe(confirm,"confirm","Confirm / Cancel",1)==MenuTransition.Result.CHANGED,"expected generic confirmation is a completed chooser transition");
            binding.transitioned(confirm);check(binding.matches(operation,confirm),"exact chooser response binds generic Confirm without a repeated perk name");
            check(!binding.matches(new Object(),confirm)&&!binding.matches(operation,chooser),"another operation or source inventory cannot inherit the binding");
            check(LoadoutConfirmation.labelsMatch("Golden Heads",lore("Click to confirm!")),"generic confirmation text supported");
            check(LoadoutConfirmation.labelsMatch("Golden Heads",lore("\u00a7ePerk: Golden Heads","Purchase: Golden Heads")),"matching formatted labels supported");
            check(!LoadoutConfirmation.labelsMatch("Golden Heads",lore("Killstreak: Other")),"explicit different identity rejected");
            check(Double.isNaN(p.balance(1)),"unknown chooser response temporarily reserves debit");
            p.confirmationOpened();check(p.balance(1)==700,"confirmation proves nothing has been charged yet");
            check(p.pending()==operation&&!operation.verified&&p.canConfirm(1),"700g remains eligible for Golden Heads confirmation");
            check(p.confirmationPriceMatches(lore("Cost: 500g"))&&!p.confirmationPriceMatches(lore("Cost: 501g")),"confirmation rechecks the live cost");
            p.confirmed(1);check(!p.canConfirm(1)&&Double.isNaN(p.balance(1)),"Confirm commits once and reserves the real debit");
            binding.clear();check(!binding.matches(operation,confirm),"binding consumed after Confirm dispatch");
            p.confirmationOpened();check(!p.canConfirm(1),"consumed confirmation cannot be reopened by planner alone");
            p.observeSlot(kind,1,lore("Selected: Golden Heads"),2);p.gold(200,2);
            check(operation.verified&&p.pending()==null&&p.balance(2)==200,"server slot and debit verify the purchase");
            check(p.next(null,2)==null,"remaining 200g does not cause another paid trip");

            p=planner(kind,3000);p.confirmationOpened();p.interrupt(1,"closed before Confirm");p.gold(3000,60001);
            check(p.balance(60001)==3000&&p.entry(kind,"Golden Heads").ownership==Ownership.UNOWNED,"canceled unpaid confirmation preserves ownership and wallet");
            check(p.next(null,60001)==p.slot(kind,1),"unpaid cancellation retries after bounded backoff");
            check(p.begin(p.next(null,60001),60001),"retry can begin without manual evidence refresh");p.applied(60001);p.confirmationOpened();
            check(p.canConfirm(60001),"retry remains eligible");p.confirmed(60001);p.interrupt(60002,"lost purchase response");p.gold(3000,120002);
            check(p.entry(kind,"Golden Heads").ownership==Ownership.UNKNOWN&&p.next(null,120002)==null,"lost response after Confirm remains ambiguous and cannot double-buy");

            p=planner(kind,3000);p.confirmationOpened();p.gold(499,2);
            check(!p.canConfirm(2),"external spending below 500g cancels confirmation");p.interrupt(3,"gold changed");
            check(p.entry(kind,"Golden Heads").ownership==Ownership.UNOWNED&&p.balance(3)==499,"failed affordability before Confirm cannot poison purchase evidence");
            p=planner(kind,700);p.confirmationOpened();check(!p.canConfirm(30001),"stale isolated wallet cannot confirm");
            p.gold(700,30002);check(p.canConfirm(30002),"fresh unchanged active sidebar restores confirmation");
            p.confirmationOpened();check(p.balance(30002)==700,"opening same confirmation twice is idempotent");
            p=planner(kind,700);p.confirmationOpened();p.configure(kind,1,"Other",true);
            check(p.pending()==null&&p.balance(1)==700&&p.entry(kind,"Golden Heads").ownership==Ownership.UNOWNED,"setting changed before Confirm cancels without uncertainty");
            p=planner(kind,700);p.confirmationOpened();p.suspend(true);check(!p.canConfirm(1),"Contractor cannot confirm a normal loadout");

            p=planner(kind,3000);Slot paid=p.pending();p.observeSlot(kind,1,lore("Selected: Golden Heads"),1);
            p.define(kind,"Free",0,10,0);p.configure(kind,2,"Free",true);p.observeChoice(kind,"Free",lore("Click to select!"));
            Slot free=p.next(null,1);check(free==p.slot(kind,2)&&p.begin(free,1),"free equip can follow a paid operation while wallet catches up");
            p.applied(1);p.confirmationOpened();check(p.canConfirm(1)&&Double.isNaN(p.balance(1)),"free confirmation does not release the preceding paid debit");
            p.confirmed(1);p.observeSlot(kind,2,lore("Selected: Free"),2);p.gold(2500,2);
            check(paid.verified&&free.verified&&p.balance(2)==2500,"free confirmation preserves proper settlement of the original purchase");
        }
        Object operation=new Object(),source=new Object(),a=new Object(),b=new Object();LoadoutConfirmation binding=new LoadoutConfirmation();
        binding.begin(operation,source);binding.transitioned(a);binding.contents(b);
        check(!binding.matches(operation,a)&&!binding.matches(operation,b),"packet and transition must refer to the same inventory");
        binding.contents(a);check(binding.matches(operation,a),"packet order may follow transition");
        binding.clear();check(!binding.matches(operation,a),"session/menu reset clears correlation");
        binding.begin(operation,source);binding.contents(source);binding.transitioned(source);
        check(!binding.matches(operation,source),"local chooser prediction cannot authorize Confirm");
        binding.begin(new Object(),b);check(!binding.matches(operation,a),"new chooser click discards old correlation");
        System.out.println("LoadoutConfirmation: "+checks+" assertions passed (generic menus, 700g, cancel, debit, identity and session isolation).");
    }
}
