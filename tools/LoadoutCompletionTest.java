import com.nezurstandalone.engine.*;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import com.nezurstandalone.utils.MenuTransition;
import java.util.*;

/** Paid unlock is distinct from equipment. Uses the production route vocabulary and planner. */
public final class LoadoutCompletionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static List<String> lore(String... s){return Arrays.asList(s);}
    private static LoadoutPlanner start(Kind kind) {
        LoadoutPlanner p=new LoadoutPlanner(s->{});p.context("Alice@Pit");p.requirements(100,false);
        p.define(kind,"Second Gapple",1500,10,0);p.configure(kind,1,"Second Gapple",true);
        p.observeSlot(kind,1,lore("Selected: No Perk"),0);p.gold(1700,0);
        p.observeChoice(kind,"Second Gapple",lore("Cost: 1,500g","Click to purchase!"));
        check(p.begin(p.next(null,0),0),"affordable action starts");p.applied(0);
        check(!p.canApply(0),"dispatched choice cannot be clicked twice");return p;
    }
    private static void purchased(LoadoutPlanner p){p.confirmationOpened();check(p.canConfirm(1),"purchase eligible");p.confirmed(1);}
    public static void main(String[] args) {
        for(Kind kind:Kind.values()) {
            String chooserKind=kind==Kind.PERK?"choosePerk":"chooseStreak";
            String title=kind==Kind.PERK?"Choose a perk":"Choose a killstreak";
            check(LoadoutMenus.kind(title).equals(chooserKind),"live title identifies correct chooser");
            LoadoutPlanner p=start(kind);Slot slot=p.pending();purchased(p);
            Object confirmation=new Object(),chooser=new Object();MenuTransition gate=new MenuTransition();
            gate.beginServerContents(confirmation,"confirm","after prediction",0,LoadoutMenus.afterConfirmation(kind));
            check(gate.observe(chooser,chooserKind,"free choice",1)==MenuTransition.Result.WAITING,"new screen alone is not authoritative");
            gate.serverContents(chooser);
            p.observeChoice(kind,"Second Gapple",lore("Click to select!"));
            check(gate.observe(chooser,chooserKind,"free choice",2)==MenuTransition.Result.CHANGED,"purchase returning to chooser completes without 15s timeout");
            check(p.entry(kind,"Second Gapple").ownership==Ownership.OWNED&&!slot.verified,"purchase confirms ownership but not equipment");
            check(p.prepareOwnedSelection(2)&&p.pendingPrice()==0&&p.canApply(2),"exactly one free equip follows the paid unlock even before debit arrives");
            check(Double.isNaN(p.balance(2)),"free follow-up preserves outstanding purchase debit");
            p.applied(2);check(!p.prepareOwnedSelection(3)&&!p.canApply(3),"cannot repeat the free equip while awaiting its response");
            gate.beginServerContents(chooser,chooserKind,"native prediction",3,LoadoutMenus.afterSelection(kind));
            gate.serverContents(chooser);p.observeChoice(kind,"Second Gapple",lore("Already selected!"));
            check(gate.observe(chooser,chooserKind,"updated selected lore",4)==MenuTransition.Result.CHANGED,"in-place server chooser refresh completes");
            check(p.choiceSelectedAfterDispatch()&&!slot.verified,"selected chooser triggers return to parent for exact slot verification");
            p.observeSlot(kind,1,lore("Selected: Second Gapple"),5);p.gold(200,5);
            check(p.pending()==null&&slot.verified&&p.next(null,5)==null,"verified final selection ends the menu flow");
            check(p.balance(5)==200,"only one 1500g debit");

            p=start(kind);purchased(p);p.observeChoice(kind,"Second Gapple",lore("Already selected!"));
            check(p.choiceSelectedAfterDispatch()&&!p.prepareOwnedSelection(2),"purchase that auto-equips goes back without another select click");
            p.interrupt(3,"back route unavailable");
            check(p.entry(kind,"Second Gapple").ownership==Ownership.OWNED,"verified purchase ownership survives later menu failure");

            p=start(kind);purchased(p);p.inspectChoice(kind,"Second Gapple",lore("Click to select!"));
            check(!p.prepareOwnedSelection(2),"local lore alone cannot authorize post-purchase recovery");
            p.observeChoice(kind,"Other",lore("Click to select!"));check(!p.prepareOwnedSelection(2),"unrelated choice cannot resolve operation");

            p=start(kind);purchased(p);p.interrupt(2,"no response");p.gold(1700,60002);
            check(p.entry(kind,"Second Gapple").ownership==Ownership.UNKNOWN&&p.next(null,60002)==null,"missing paid response still prevents duplicate buying");

            p=start(kind);p.observeChoice(kind,"Second Gapple",lore("Click to select!"));
            check(p.prepareOwnedSelection(1),"direct purchase without confirmation also supports free equip");
            p=start(kind);purchased(p);p.observeSlot(kind,1,lore("Selected: Second Gapple"),2);
            check(p.pending()==null&&p.slot(kind,1).verified,"existing direct root verification still completes");

            p=start(kind);p.confirmationOpened();p.observeSlot(kind,1,lore("Required level: [120]"),1);
            check(!p.canConfirm(1),"new slot lock blocks confirmation despite ample wallet");
            p=start(kind);p.confirmationOpened();p.missingSlot(kind,1);check(!p.canConfirm(1),"removed slot blocks confirmation");
            p=start(kind);p.confirmationOpened();p.observeSlot(kind,2,lore("Selected: Second Gapple"),1);
            check(!p.canConfirm(1),"new duplicate in another slot blocks confirmation");

            p=start(kind);purchased(p);p.observeChoice(kind,"Second Gapple",lore("Click to select!"));
            p.interrupt(2,"navigation failed");p.gold(200,60002);
            check(p.next(null,60002)!=null&&p.slot(kind,1).state==State.NEEDS_EQUIP,"known purchase recovers as free equip after failure, even below price");
            p=start(kind);purchased(p);p.observeChoice(kind,"Second Gapple",lore("Click to select!"));p.context("Bob@Pit");
            check(!p.prepareOwnedSelection(2)&&p.pending()==null,"account replacement discards follow-up evidence");
        }
        check(LoadoutMenus.slotNumber(Kind.KILLSTREAK,"\u00a7aKillstreak Slot #1")==1,"killstreak-specific slot labels supported");
        check(LoadoutMenus.slotNumber(Kind.KILLSTREAK," Perk Slot #2 ")==2,"Classic shared slot label supported");
        check(LoadoutMenus.slotNumber(Kind.KILLSTREAK,"Perk Slot #3")==-1,"Megastreak/third slot excluded");
        check(LoadoutMenus.slotNumber(Kind.PERK,"Killstreak Slot #1")==-1,"killstreak labels cannot contaminate perk slots");
        check(LoadoutMenus.slotNumber(Kind.PERK,"Perk Slot #4")==4,"fourth perk supported");
        check(!LoadoutMenus.responseEvidence("chooseStreak","Second Gapple","Go Back",lore("Click!")),"streamed navigation icon cannot release pending chooser action");
        check(!LoadoutMenus.responseEvidence("chooseStreak","Second Gapple","Feast",lore("Click to select!")),"unrelated item update cannot release pending chooser action");
        check(LoadoutMenus.responseEvidence("chooseStreak","Second Gapple","Second Gapple",lore("Already selected!")),"exact chosen item supplies response evidence");
        check(!LoadoutMenus.responseEvidence("upgrades","Second Gapple","Perk Slot #1",lore("Loading...")),"unresolved streamed root slot cannot release transition");
        check(LoadoutMenus.responseEvidence("killstreaks","Second Gapple","Killstreak Slot #1",lore("Selected: Second Gapple")),"resolved server root supplies evidence");
        check(!LoadoutMenus.responseEvidence("confirm","Second Gapple","Cancel",lore("Click!")),"cancel-only partial confirmation waits for its Confirm item");
        check(LoadoutMenus.responseEvidence("confirm","Second Gapple","Confirm",lore()),"populated Confirm supplies evidence");
        MenuTransition gate=new MenuTransition();Object source=new Object();
        gate.beginServerContents(source,"chooseStreak","unchanged",0,LoadoutMenus.afterSelection(Kind.KILLSTREAK));
        check(gate.observe(source,"chooseStreak","local change",1)==MenuTransition.Result.WAITING,"local prediction cannot acknowledge same-menu action");
        gate.serverContents(source);
        check(gate.observe(source,"chooseStreak","unchanged",2)==MenuTransition.Result.CHANGED,"unchanged authoritative reply can be evaluated instead of timing out");
        gate.beginServerContents(source,"confirm","old",0,LoadoutMenus.afterConfirmation(Kind.KILLSTREAK));
        gate.serverContents(source);check(gate.observe(source,"confirm","old",1)==MenuTransition.Result.WAITING,"old confirmation cannot repeat Confirm");
        check(gate.observe(source,"confirm","old",15_000_000_000L)==MenuTransition.Result.TIMED_OUT,"missing expected result still has bounded timeout");
        gate.clear();check(gate.observe(source,"chooseStreak","late",2)==MenuTransition.Result.IDLE,"late response after reset cannot resume work");
        System.out.println("LoadoutCompletion: "+checks+" purchase/equip, routing, packet, debit, retry and slot eligibility checks passed.");
    }
}
