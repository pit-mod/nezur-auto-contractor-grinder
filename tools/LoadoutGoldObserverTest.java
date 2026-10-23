import com.nezurstandalone.engine.LoadoutGoldObserver;
import com.nezurstandalone.engine.LoadoutPlanner;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import java.util.*;

/** The production sidebar reader and planner together; packet assembly/toggle/age regressions. */
public final class LoadoutGoldObserverTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static List<String> lines(String... text){return Arrays.asList(text);}
    private static LoadoutPlanner planner(){
        LoadoutPlanner p=new LoadoutPlanner(log -> { });p.context("Alice@Pit|X");p.requirements(70,false);
        p.define(Kind.PERK,"Golden Heads",500,10,0);p.define(Kind.PERK,"Strength-Chaining",2000,20,0);
        p.configure(Kind.PERK,1,"Golden Heads",true);p.configure(Kind.PERK,2,"Strength-Chaining",true);
        return p;
    }
    public static void main(String[] args) {
        Object world=new Object(),connection=new Object(),objective=new Object();
        LoadoutGoldObserver observer=new LoadoutGoldObserver();LoadoutPlanner p=planner();
        List<String> gold=lines("THE HYPIXEL PIT","Level: [70]","\u00a76Gold: \u00a7e700.00g");
        observer.observe(p,world,connection,objective,true,true,gold,0);
        ok(p.balance(0)==700&&p.next(null,0)==p.slot(Kind.PERK,1),"screenshot: current sidebar gold initializes Golden Heads without a new gold-specific packet");
        for(long now=1000;now<=120000;now+=1000) {
            observer.observe(p,world,connection,objective,true,true,gold,now);
            ok(p.balance(now)==700&&p.next(null,now)==p.slot(Kind.PERK,1),"unchanged live sidebar remains valid after 30 seconds");
        }
        observer.observe(p,world,connection,objective,false,true,gold,120001);
        ok(Double.isNaN(p.balance(120001))&&p.next(null,120001)==null,"closed connection invalidates a still-visible old sidebar");
        observer.observe(p,world,connection,objective,true,false,gold,120002);ok(p.next(null,120002)==null,"lobby title cannot authorize a Pit purchase");
        observer.observe(p,world,connection,null,true,true,gold,120003);ok(p.next(null,120003)==null,"missing displayed objective cannot authorize purchase");

        // Teams before scores before display: each intermediate snapshot is safe; the last resolves.
        observer.observe(p,world,connection,null,true,true,lines(),120010);
        observer.observe(p,world,connection,objective,true,true,lines("Level: [70]"),120011);
        ok(p.next(null,120011)==null,"partially assembled sidebar blocks purchase");
        observer.observe(p,world,connection,objective,true,true,gold,120012);
        ok(p.next(null,120012)!=null,"final score/display packet resolves gold even when team creation came first");

        p.configure(Kind.PERK,1,"Golden Heads",false);observer.observe(p,world,connection,objective,true,true,gold,120020);
        p.configure(Kind.PERK,1,"Golden Heads",true);observer.observe(p,world,connection,objective,true,true,gold,120021);
        ok(p.next(null,120021)!=null,"enable after sidebar population needs no packet replay or gold gain");

        observer.observe(p,world,connection,objective,true,true,lines("Gold: 499g"),120030);ok(p.next(null,120030)==null,"external spending immediately cancels eligibility");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 500g"),120031);ok(p.next(null,120031)!=null,"exact threshold becomes eligible");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: NaN"),120032);ok(p.next(null,120032)==null,"invalid number invalidates previous good value");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 500g","Gold: 9000g"),120033);ok(p.next(null,120033)==null,"ambiguous multiple gold lines rejected");
        observer.observe(p,world,connection,objective,true,true,lines("Level: [70]"),120034);ok(p.next(null,120034)==null,"removed gold line does not fall back to cached balance");
        observer.observe(p,world,connection,objective,true,true,lines("\u200bGold\u00a0:\u202f1,250.50g\ufeff"),120035);ok(p.balance(120035)==1250.50,"decorated Unicode spacing and formatting markers accepted");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 2.5kg"),120036);ok(p.balance(120036)==2500,"abbreviated gold accepted");
        observer.observe(p,world,new Object(),new Object(),true,true,lines("Gold: 1g"),120037);ok(p.next(null,120037)==null&&p.balance(120037)==1,"replacement connection/objective cannot keep old parsed gold");

        p=planner();observer.clear();observer.observe(p,world,connection,objective,true,true,lines("Gold: 3000g"),0);
        p.observeSlot(Kind.PERK,1,lines("Click to choose perk!"),0);p.observeSlot(Kind.PERK,2,lines("Click to choose perk!"),0);
        p.observeChoice(Kind.PERK,"Golden Heads",lines("Cost: 500g","Click to purchase!"));
        p.observeChoice(Kind.PERK,"Strength-Chaining",lines("Cost: 2000g","Click to purchase!"));
        Slot slot=p.next(null,0);p.begin(slot,0);p.applied(0);p.observeSlot(Kind.PERK,1,lines("Selected: Golden Heads"),1);
        for(long now=1;now<1000;now++)observer.observe(p,world,connection,objective,true,true,lines("Gold: 3000g"),now);
        ok(p.next(null,999)==null,"polling unchanged sidebar cannot erase pending purchase debit");
        observer.observe(p,world,connection,objective,true,true,lines(),1000);
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 3000g"),1001);
        ok(p.next(null,1001)==null,"disappearing/reappearing old amount cannot acknowledge a purchase debit");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 2500g"),1002);
        ok(p.next(null,1002)==p.slot(Kind.PERK,2),"real debit permits next incremental purchase");

        p=planner();observer.clear();observer.observe(p,world,connection,objective,true,true,gold,0);
        p.observeSlot(Kind.PERK,1,lines("Click to choose perk!"),0);slot=p.next(null,0);p.begin(slot,0);
        p.observeChoice(Kind.PERK,"Golden Heads",lines("Cost: 500g","Not enough gold!"));
        for(long now=1;now<1000;now++)observer.observe(p,world,connection,objective,true,true,gold,now);
        ok(p.pending()==null&&p.next(null,999)==null,"live insufficient gold overrides repeated current-sidebar reads");
        observer.observe(p,world,connection,objective,true,true,lines(),1000);
        observer.observe(p,world,connection,objective,true,true,gold,1001);ok(p.next(null,1001)==null,"a missing frame cannot erase a rejected wallet");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 701g"),1002);ok(p.next(null,1002)!=null,"meaningful new wallet balance releases live denial");

        p.context("Bob@Pit|I");observer.observe(p,new Object(),new Object(),new Object(),true,true,lines(),1003);ok(p.next(null,1003)==null,"account/world change cannot re-use last player's gold");
        long revision=p.revision();for(long now=1004;now<3000;now++)observer.observe(p,null,null,null,false,false,lines(),now);
        ok(p.revision()==revision,"unchanged invalid scenes do not continuously invalidate planner state");
        p=planner();observer.clear();observer.observe(p,world,connection,objective,true,true,gold,0);revision=p.revision();
        for(long now=1;now<3000;now++)observer.observe(p,world,connection,objective,true,true,gold,now);
        ok(p.revision()==revision,"unchanged connected samples renew observation without recomputing the planner");
        // Exact canonical lines captured from the live server: the emoji is the unique score entry.
        p=planner();observer.clear();
        for(String marker:new String[]{"\uD83D\uDC79","\uD83C\uDFC0","\u26BD","\u26BD\uFE0F"}) {
            observer.observe(p,world,connection,objective,true,true,lines("Gold: \u00a7677,078g"+marker),4000);
            ok(p.balance(4000)==77078 && p.next(null,4000)!=null,"server emoji entry cannot block affordable NPC trip");
            observer.observe(p,world,connection,objective,true,true,lines("Gold: 5,699.54g"+marker),4001);
            ok(p.balance(4001)==5699.54,"decimal wallet with trailing score-entry marker");
        }
        observer.observe(p,world,connection,objective,true,true,lines("\u00a7fGold: \u00a765,529.\u00a76\u00a7r\u00a7694g"),4002);
        ok(p.balance(4002)==5529.94,"Classic split prefix/entry/suffix decimal remains valid");
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 5,699.54g"),4003);
        ok(p.balance(4003)==5699.54 && p.next(null,4003)!=null,"screenshot wallet triggers an affordable action");
        for(String invalid:new String[]{"-500g","500g +100g","500g 9000","5,69g","NaN","500g scam","500g\uD83C\uDFC0999"}) {
            observer.observe(p,world,connection,objective,true,true,lines("Gold: "+invalid),4004);
            ok(Double.isNaN(p.balance(4004)),"decorations cannot conceal malformed/ambiguous money: "+invalid);
        }
        observer.observe(p,world,connection,objective,true,true,lines("Gold: 700g\u26BD","Gold: 9000g\uD83D\uDC79"),4005);
        ok(Double.isNaN(p.balance(4005)),"duplicate decorated gold lines still rejected");
        ok(Double.isNaN(LoadoutPlanner.money("700g\u26BD")),"purchase-cost parser remains strict");
        System.out.println("LoadoutGoldObserver: "+checks+" connected-sidebar, packet-order, steady-balance, ledger, denial and lifecycle checks passed.");
    }
}
