import com.nezurstandalone.engine.LoadoutPlanner;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import java.util.*;

/** Cold-client startup reproductions: shared unchanged by Forge and Ornithe. */
public final class LoadoutStartupTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static List<String> lore(String... lines){return Arrays.asList(lines);}
    private static LoadoutPlanner cold() {
        LoadoutPlanner p=new LoadoutPlanner(message -> { });
        p.context("Alice@mc.hypixel.net|Pit|prestige-X");p.requirements(60,false);
        p.define(Kind.PERK,"Golden Heads",500,10,0);
        p.define(Kind.PERK,"Strength-Chaining",2000,20,0);
        p.define(Kind.PERK,"Bonk!",2000,35,0);
        p.define(Kind.PERK,"Dirty",8000,80,15);
        p.define(Kind.PERK,"Mystery",Double.NaN,10,0);
        p.define(Kind.KILLSTREAK,"Second Gapple",1500,10,0);
        p.define(Kind.KILLSTREAK,"Feast",4000,30,0);
        String[] names={"Golden Heads","Strength-Chaining","Bonk!","No Perk"};
        for(int i=0;i<4;i++)p.configure(Kind.PERK,i+1,names[i],true);
        p.configure(Kind.KILLSTREAK,1,"Second Gapple",true);
        p.configure(Kind.KILLSTREAK,2,"Feast",true);
        return p;
    }
    private static void roots(LoadoutPlanner p,String first) {
        p.observeSlot(Kind.PERK,1,lore("Selected: "+first),1);
        p.observeSlot(Kind.PERK,2,lore("Click to choose perk!"),1);
        p.observeSlot(Kind.PERK,3,lore("Required level: [70]"),1);
        p.missingSlot(Kind.PERK,4);
        p.observeSlot(Kind.KILLSTREAK,1,lore("Click to choose perk!"),1);
        p.observeSlot(Kind.KILLSTREAK,2,lore("Required level: [75]"),1);
    }
    public static void main(String[] args) {
        LoadoutPlanner p=cold();p.gold(700,0);Slot s=p.next(null,0);
        check(s==p.slot(Kind.PERK,1)&&s.state==State.NEEDS_REVALIDATION,"screenshot: 700g queues Golden Heads from an empty cache");
        check(p.entry(Kind.PERK,"Golden Heads").ownership==Ownership.UNKNOWN&&!s.equipmentKnown,"preflight never invents ownership or equipment");
        roots(p,"No Perk");s=p.next(null,1);check(s==p.slot(Kind.PERK,1),"live UPGRADES observation preserves affordable pending choice");
        check(p.begin(s,1)&&!p.canApply(1),"chooser opens but purchase waits for server choice evidence");
        boolean refused=false;try{p.applied(1);}catch(IllegalStateException expected){refused=true;}
        check(refused&&p.pending()==s&&s.state==State.IN_PROGRESS,"unknown choice cannot reach the debit/purchase stage");
        p.observeChoice(Kind.PERK,"Golden Heads",lore("Cost: 500g","Click to purchase!"));
        check(p.canApply(1)&&p.pendingPrice()==500,"live cost and purchase availability validated");
        p.applied(1);check(s.state==State.VERIFYING&&!s.verified,"click dispatch is not a verified purchase");
        p.observeSlot(Kind.PERK,1,lore("Selected: Golden Heads"),2);p.gold(200,2);
        check(p.next(null,2)==null&&s.verified,"Golden Heads purchased and verified, resume with 200g instead of chasing expensive choices");
        for(int i=0;i<100;i++)check(p.next(null,3+i)==null,"no repeated NPC visits with the same verified loadout");
        p.gold(2000,200);check(p.next(null,200)==p.slot(Kind.PERK,2),"later threshold schedules Strength-Chaining independently");

        p=cold();p.gold(499,0);check(p.next(null,0)==null,"cold cache cannot trigger an unaffordable visit");
        p.gold(500,1);check(p.next(null,1)==p.slot(Kind.PERK,1),"cold cache exact Golden Heads price schedules the visit");
        p.gold(499,2);check(p.next(null,2)==null,"gold falls before navigation: cancel revalidation");
        p.gold(700,3);p.invalidateGold();check(p.next(null,3)==null,"invalid/missing wallet blocks cold startup");
        p.gold(700,4);check(p.next(null,30005)==null,"stale wallet blocks cold startup");

        p=cold();p.gold(700,0);roots(p,"Golden Heads");
        check(p.next(null,1)==null&&p.slot(Kind.PERK,1).state==State.SATISFIED,"already equipped at first menu: no chooser/purchase");
        p=cold();p.gold(700,0);roots(p,"No Perk");p.observeChoice(Kind.PERK,"Golden Heads",lore("Click to select!"));
        p.gold(0,1);s=p.next(null,1);check(s.state==State.NEEDS_EQUIP&&p.begin(s,1)&&p.canApply(1)&&p.pendingPrice()==0,"server-owned Golden Heads equips freely at zero gold");

        p=cold();p.gold(700,0);roots(p,"No Perk");s=p.next(null,1);p.begin(s,1);
        p.observeChoice(Kind.PERK,"Golden Heads",lore("Cost: 1000g","Click to purchase!"));
        check(!p.canApply(1),"higher live server price beats affordable startup table");p.blocked(1,"higher live price");
        check(p.next(null,1)==null,"live price increase stops repeated unaffordable trips");
        p.gold(1000,2);check(p.next(null,2)!=null,"corrected server price sets the new threshold");

        p=cold();p.gold(700,0);roots(p,"No Perk");s=p.next(null,1);p.begin(s,1);
        p.observeChoice(Kind.PERK,"Golden Heads",lore("Cost: 500g","Not enough gold!"));
        check(p.pending()==null&&p.next(null,1)==null,"live insufficient gold cancels bootstrap purchase safely");
        p=cold();p.gold(700,0);roots(p,"No Perk");s=p.next(null,1);p.begin(s,1);p.choiceMissing(Kind.PERK,"Golden Heads");p.blocked(1,"absent");
        p.gold(9000,2);check(p.next(Kind.PERK,2)!=p.slot(Kind.PERK,1),"unavailable bootstrap choice is not retried on every balance change");

        p=cold();p.gold(700,0);roots(p,"No Perk");s=p.next(null,1);p.begin(s,1);p.observeChoice(Kind.PERK,"Golden Heads",lore("Cost: 500g","Click to purchase!"));p.applied(1);p.interrupt(2,"lost response");p.gold(700,3);
        check(p.next(null,180002)==null,"ambiguous paid result cannot be bootstrapped into a duplicate purchase");
        p.gold(700,180003);check(p.next(null,180003)==null,"fresh gold alone cannot release uncertain paid ownership");
        p.observeChoice(Kind.PERK,"Golden Heads",lore("Click to select!"));check(p.next(null,180003)!=null,"actual server choice evidence resolves ambiguous ownership");

        p=cold();p.configure(Kind.PERK,1,"No Perk",true);p.gold(1500,0);
        check(p.next(null,0)==p.slot(Kind.KILLSTREAK,1),"cold killstreak cache schedules affordable Second Gapple");
        p.requirements(10,false);p.gold(1500,1);check(p.next(null,1)==p.slot(Kind.KILLSTREAK,1),"bootstrap works after prestige reset once its real slot/choice requirements are met");
        p=cold();p.requirements(9,false);p.gold(99999,0);check(p.next(null,0)==null,"known slot/choice levels gate startup even with plentiful gold");
        p=cold();p.requirements(100,false);p.configure(Kind.PERK,1,"Dirty",true);p.configure(Kind.PERK,2,"No Perk",false);p.configure(Kind.PERK,3,"No Perk",false);p.configure(Kind.KILLSTREAK,1,"No Perk",false);p.configure(Kind.KILLSTREAK,2,"No Perk",false);p.gold(99999,0);
        check(p.next(null,0)==null,"unknown Renown unlock still requires trustworthy evidence");
        p.configure(Kind.PERK,1,"Mystery",true);check(p.next(null,0)==null,"no discovery trip for undefined price");
        p=cold();p.gold(700,0);p.suspend(true);check(p.next(null,0)==null,"Contractor owns loadout: suppress cold-start visits");p.suspend(false);check(p.next(null,0)!=null,"lease release requeues the affordable startup action");
        p=cold();p.gold(700,0);for(int i=0;i<3;i++) {p.deferVisit(i*180000L,"NPC unreachable");p.gold(700,(i+1)*180000L);}
        check(p.next(null,540000)==null,"cold-start navigation failures remain bounded");
        System.out.println("LoadoutStartup: "+checks+" assertions passed (700g Golden Heads, cold cache, live revalidation, failures and shared-port parity).");
    }
}
