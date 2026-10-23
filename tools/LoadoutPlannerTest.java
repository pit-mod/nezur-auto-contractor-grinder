import com.nezurstandalone.engine.LoadoutPlanner;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import java.util.*;

/** Real planner with authoritative scoreboard/GUI simulations. No Minecraft or network required. */
public final class LoadoutPlannerTest {
    private static int checks;
    private static final List<String> logs=new ArrayList<>();
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static List<String> lore(String... lines){return Arrays.asList(lines);}
    private static LoadoutPlanner planner(){
        LoadoutPlanner p=new LoadoutPlanner(logs::add);p.context("Alice@hypixel|profile-A|X");p.requirements(120,false);
        p.define(Kind.PERK,"A",5000,10,0);p.define(Kind.PERK,"B",10000,20,0);p.define(Kind.PERK,"C",30000,30,0);
        p.define(Kind.PERK,"Vampire",4000,60,0);p.define(Kind.PERK,"Kung Fu Knowledge",10000,100,40);
        p.define(Kind.KILLSTREAK,"Second Gapple",4000,10,0);p.define(Kind.KILLSTREAK,"Rush",25000,110,0);
        for(Slot s:p.slots())p.observeSlot(s.kind,s.number,lore("Click to choose perk!"),0);
        p.observeChoice(Kind.PERK,"No perk",lore("Click to select!"));p.observeChoice(Kind.KILLSTREAK,"No killstreak",lore("Click to select!"));
        return p;
    }
    private static void purchase(LoadoutPlanner p,Kind k,int n,String name,String cost){
        p.configure(k,n,name,true);p.observeChoice(k,name,lore("Cost: "+cost+"g","Click to purchase!"));
    }
    private static void owned(LoadoutPlanner p,Kind k,int n,String name){p.configure(k,n,name,true);p.observeChoice(k,name,lore("Click to select!"));}
    private static void verify(LoadoutPlanner p,Slot s,long now){p.observeSlot(s.kind,s.number,lore("Selected: "+s.desired),now);}
    public static void main(String[] args){
        LoadoutPlanner p=planner();purchase(p,Kind.PERK,1,"A","5,000");p.gold(3000,0);
        ok(p.next(null,0)==null,"1 insufficient gold: no NPC trip");
        p.gold(5000,1);ok(p.next(null,1)==p.slot(Kind.PERK,1),"2 exact threshold eligible");
        p.gold(4999,2);ok(p.next(null,2)==null,"3 balance drop cancels before spawn");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");purchase(p,Kind.PERK,2,"B","10000");purchase(p,Kind.PERK,3,"C","30000");p.gold(5200,0);
        Slot s=p.next(null,0);ok(s.number==1&&p.begin(s,0),"4 one affordable choice among several");p.applied(0);
        ok(p.next(null,1)==null,"pending operation prevents simultaneous purchases");verify(p,s,1);p.gold(200,1);ok(p.next(null,1)==null,"remaining unaffordable selections do not cause a trip");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");purchase(p,Kind.PERK,2,"B","10000");purchase(p,Kind.PERK,3,"C","30000");purchase(p,Kind.KILLSTREAK,1,"Second Gapple","4000");p.gold(20000,0);
        double wallet=20000;StringBuilder order=new StringBuilder();
        while((s=p.next(null,0))!=null){order.append(s.desired).append(',');ok(p.begin(s,0),"begin ordered action");wallet-=p.pendingPrice();p.applied(0);verify(p,s,0);p.gold(wallet,0);}
        ok(wallet==1000&&order.toString().equals("A,B,Second Gapple,"),"5/21 coordinated incremental purchases across both kinds");
        p=planner();owned(p,Kind.PERK,1,"Vampire");p.gold(0,0);s=p.next(null,0);ok(s.state==State.NEEDS_EQUIP&&p.begin(s,0)&&p.pendingPrice()==0,"6/22 owned perk free at zero gold");p.applied(0);verify(p,s,0);
        ok(p.next(null,0)==null&&s.state==State.SATISFIED,"7 already equipped idempotent");
        p=planner();for(Slot slot:p.slots()){p.observeSlot(slot.kind,slot.number,lore("Selected: Old "+slot.number),0);p.configure(slot.kind,slot.number,"No Perk",true);}
        int cleared=0;while((s=p.next(null,0))!=null){ok(p.begin(s,0)&&p.pendingPrice()==0,"clear operation is free");p.applied(0);verify(p,s,0);cleared++;}
        ok(cleared==6&&p.next(null,0)==null,"8 all No Perk clears applicable slots in both groups");
        p=planner();p.observeSlot(Kind.PERK,1,lore("Selected: A"),0);p.configure(Kind.PERK,1,"No Perk",true);ok(p.next(null,0)!=null,"known clearing support queues action");
        p.choiceMissing(Kind.PERK,"No Perk");ok(p.next(null,0)==null,"missing removal choice revokes stale clearing support without loops");
        p=planner();p.requirements(9,false);p.observeSlot(Kind.PERK,1,lore("Required level: [10]"),0);purchase(p,Kind.PERK,1,"A","5000");p.gold(100000,0);ok(p.next(null,0)==null&&p.slot(Kind.PERK,1).state==State.LEVEL_LOCKED,"9 level locked no trip");
        p.requirements(10,false);ok(p.next(null,0)!=null,"slot unlock resumes eligibility");
        p=planner();purchase(p,Kind.PERK,1,"Kung Fu Knowledge","10000");p.observeChoice(Kind.PERK,"Kung Fu Knowledge",lore("Unlocked in Renown shop!"));p.gold(100000,0);
        for(int i=0;i<10;i++)ok(p.next(null,i)==null,"10 Renown restriction persists without retries");
        p.observeChoice(Kind.PERK,"Kung Fu Knowledge",lore("Cost: 10000g","Click to purchase!"));ok(p.next(null,10)!=null,"manual unlock refresh releases restriction");
        p=planner();p.requirements(109,true);p.observeSlot(Kind.KILLSTREAK,1,lore("Click to choose killstreak!"),0);purchase(p,Kind.KILLSTREAK,1,"Rush","25000");p.gold(30000,0);ok(p.next(null,0)==null,"The Way never bypasses killstreak levels");p.requirements(110,true);
        ok(p.next(null,0)!=null,"11 killstreak definition recognizes actual unlock level");
        p=planner();owned(p,Kind.PERK,1,"A");p.requirements(1,false);p.gold(100000,0);ok(p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN&&p.next(null,0)==null,"12 prestige level reset invalidates ownership");
        p=planner();owned(p,Kind.PERK,1,"A");p.suspend(true);ok(p.next(null,0)==null,"13 Contractor suppresses normal loadout");p.observeSlot(Kind.PERK,1,lore("Selected: Kung Fu Knowledge"),0);p.suspend(false);ok(p.next(null,0)!=null,"normal desired loadout restored after lease release");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");p.gold(5000,0);s=p.next(null,0);p.begin(s,0);p.applied(0);p.interrupt(1,"timeout");p.gold(5000,1);
        ok(p.next(null,60001)==null&&p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN,"14 ambiguous paid failure never purchases twice");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");p.gold(5000,0);p.begin(p.next(null,0),0);p.observeChoice(Kind.PERK,"A",lore("Cost: 5000g","Not enough gold!"));
        ok(p.pending()==null&&p.next(null,1)==null,"15 live insufficient gold contradicts wallet and stops operation");p.gold(5000,2);ok(p.next(null,2)==null,"unchanged wallet cannot override live insufficient gold");p.gold(5001,3);ok(p.next(null,3)!=null,"changed authoritative wallet permits re-evaluation");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");p.gold(Double.NaN,0);ok(p.next(null,0)==null,"16 missing gold fails safely");p.gold(5000,0);ok(p.next(null,30001)==null,"stale gold rejected");p.gold(5000,30002);ok(p.next(null,30002)!=null,"fresh unchanged authoritative gold restores readiness");
        p=planner();p.configure(Kind.PERK,1,"A",true);p.gold(999999,0);s=p.next(null,0);
        ok(s!=null&&s.state==State.NEEDS_REVALIDATION&&p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN,"17 affordable definition queues revalidation without inventing ownership");
        p.begin(s,0);ok(!p.canApply(0),"unknown ownership can open chooser but cannot click purchase");p.blocked(0,"test interrupted before chooser");
        p.define(Kind.PERK,"Unknown",Double.NaN,10,0);purchase(p,Kind.PERK,1,"Unknown","bad");ok(p.next(null,0)==null&&p.slot(Kind.PERK,1).state==State.UNKNOWN_PRICE,"invalid live price cannot fall back to table");
        p=planner();owned(p,Kind.PERK,1,"A");p.observeSlot(Kind.PERK,1,lore("Selected: A"),0);for(int i=0;i<5;i++){p.configure(Kind.PERK,1,"A",false);p.configure(Kind.PERK,1,"A",true);ok(p.next(null,i)==null,"18 repeated toggles no NPC trip");}
        p.context("Bob@hypixel|profile-A|X");ok(p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN&&p.next(null,0)==null,"19 account cache isolation");
        p=planner();owned(p,Kind.PERK,1,"A");p.context("Alice@another-server|profile-A|X");ok(p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN,"server cache isolation");
        p=planner();owned(p,Kind.PERK,1,"A");p.context("Alice@hypixel|profile-B|X");ok(p.entry(Kind.PERK,"A").ownership==Ownership.UNKNOWN,"profile cache isolation");
        p=planner();owned(p,Kind.PERK,1,"A");s=p.next(null,0);p.begin(s,0);p.applied(0);p.evaluate(1500);ok(p.pending()==s&&!s.verified,"20 time and local click never verify success");p.observeSlot(Kind.PERK,2,lore("Selected: A"),1501);ok(p.pending()==s,"unrelated slot packet never verifies success");p.observeSlot(Kind.PERK,1,lore("loading..."),1502);ok(p.pending()==s,"unresolved lore never verifies or clears equipment");verify(p,s,1503);ok(p.pending()==null&&s.verified,"exact server slot evidence verifies delayed operation");
        p=planner();ok(p.next(null,0)==null,"23 no actionable selections zero interactions");
        purchase(p,Kind.PERK,1,"A","5000");p.gold(6000,0);ok(p.next(null,0)!=null,"ready before external spending");p.gold(4000,1);ok(p.next(null,1)==null,"24 external spending immediately invalidates affordability");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");purchase(p,Kind.PERK,2,"B","10000");p.gold(20000,0);s=p.next(null,0);p.begin(s,0);p.applied(0);verify(p,s,1);
        ok(p.next(null,1)==null,"lag: no second paid action until wallet debit observed");p.gold(20000,2);ok(p.next(null,2)==null,"repeated stale wallet amount cannot settle debit");p.gold(21000,3);ok(p.next(null,3)==null,"unrelated income before debit cannot settle pending spend");p.gold(15000,4);ok(p.next(null,4)!=null,"authoritative debit enables next purchase");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");purchase(p,Kind.PERK,2,"B","10000");p.gold(20000,0);s=p.next(null,0);p.begin(s,0);p.applied(0);
        ok(!p.canConfirm(1),"chooser click alone cannot authorize a confirmation");p.confirmationOpened();
        ok(p.canConfirm(1),"correlated confirmation accepts fresh adequate funds");p.gold(4000,2);ok(!p.canConfirm(2),"external spending cancels unaffordable confirmation");
        ok(p.confirmationPriceMatches(lore("Cost: 5000g")),"confirmation accepts exact committed price");ok(!p.confirmationPriceMatches(lore("Cost 8000g")),"confirmation rejects contradictory price even without colon");ok(!p.confirmationPriceMatches(lore("Cost: invalid")),"malformed confirmation price fails safely");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");purchase(p,Kind.PERK,2,"B","10000");p.gold(20000,0);s=p.next(null,0);p.begin(s,0);p.applied(0);verify(p,s,1);
        p.invalidateGold();p.gold(20000,2);ok(p.next(null,2)==null,"missing gold line cannot erase an unsettled debit");p.gold(15000,3);ok(p.next(null,3)!=null,"post-gap authoritative debit releases ledger");
        p=planner();p.requirements(9,false);p.observeSlot(Kind.PERK,1,lore("Required level: [10]"),0);purchase(p,Kind.PERK,1,"A","5000");p.gold(5000,0);p.requirements(10,false);
        ok(p.next(null,0)!=null,"known unlock threshold queues a visit");p.observeSlot(Kind.PERK,1,lore("Required level: [10]"),1);ok(p.next(null,1)==null,"fresh contradictory server lock prevents repeated visits");
        p=planner();owned(p,Kind.PERK,1,"A");s=p.next(null,0);p.begin(s,0);p.configure(Kind.PERK,1,"B",true);ok(p.pending()==null&&!p.canApply(0),"configuration change cancels queued old selection");
        p=planner();purchase(p,Kind.PERK,1,"A","5000");p.observeChoice(Kind.PERK,"A",lore("Cost: 8000g","Click to purchase!"));p.gold(5000,0);ok(p.next(null,0)==null,"live price overrides definition");
        p=planner();owned(p,Kind.PERK,1,"A");p.gold(Double.NaN,0);ok(p.next(null,0)!=null,"confirmed free equip requires no wallet");
        p=planner();owned(p,Kind.PERK,1,"A");for(int i=0;i<3;i++){p.deferVisit(i*180000L,"NPC unavailable");p.evaluate((i+1)*180000L);}ok(p.next(null,900000)==null,"bounded NPC failures cannot form an infinite loop");
        p.observeSlot(Kind.PERK,1,lore("Selected: No Perk"),900000);p.retryVerifiedObservation(Kind.PERK,1);ok(p.next(null,900000)!=null,"manual fresh slot evidence releases bounded failure");
        ok(LoadoutPlanner.money("5,200.25g")==5200.25,"comma money");ok(LoadoutPlanner.money("12.5k")==12500,"abbreviated gold");ok(LoadoutPlanner.money("1\u202f000g")==1000,"narrow space grouping");
        for(String bad:new String[]{"-5g","5,20g","NaN","Infinity","5000g garbage","1.2.3g",""})ok(Double.isNaN(LoadoutPlanner.money(bad)),"invalid gold grammar: "+bad);
        ok(Double.isNaN(LoadoutPlanner.scoreboardGold(lore("Gold: 5000g","Gold: 9000g"))),"ambiguous duplicate gold rejected");
        ok(LoadoutPlanner.scoreboardGold(lore("\u00a76Gold: \u00a7e1.5kg"))==1500,"colored abbreviated gold");
        int before=logs.size();p=planner();purchase(p,Kind.PERK,1,"A","5000");p.gold(100,0);p.next(null,0);int logged=logs.size();for(int i=1;i<500;i++){p.gold(100+i,i);p.next(null,i);}ok(logs.size()==logged,"waiting gold logs deduplicated across hundreds of updates");
        System.out.println("LoadoutPlanner: "+checks+" assertions passed; all 24 requested scenarios plus conservative ledger, manual evidence and parsing cases.");
    }
}
