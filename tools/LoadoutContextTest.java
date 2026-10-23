import com.nezurstandalone.engine.*;
import com.nezurstandalone.engine.LoadoutPlanner.*;
import java.util.*;

public final class LoadoutContextTest {
 private static int checks;
 private static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 private static List<String> lines(String... a){return Arrays.asList(a);}
 private static LoadoutPlanner planner(){
  LoadoutPlanner p=new LoadoutPlanner(log->{});
  p.define(Kind.PERK,"Golden Heads",500,10,0);p.define(Kind.PERK,"Strength-Chaining",2000,20,0);
  p.define(Kind.PERK,"Bonk!",2000,35,0);p.define(Kind.KILLSTREAK,"Second Gapple",1500,10,0);p.define(Kind.KILLSTREAK,"Feast",3000,75,0);
  p.configure(Kind.PERK,1,"Golden Heads",true);p.configure(Kind.PERK,2,"Strength-Chaining",true);
  p.configure(Kind.PERK,3,"Bonk!",true);p.configure(Kind.PERK,4,"No Perk",true);
  p.configure(Kind.KILLSTREAK,1,"Second Gapple",true);p.configure(Kind.KILLSTREAK,2,"Feast",true);
  return p;
 }
 private static void verified(LoadoutPlanner p,long now){
  p.observeSlot(Kind.PERK,1,lines("Selected: Golden Heads"),now);
  p.observeSlot(Kind.PERK,2,lines("Selected: Strength-Chaining"),now);
  p.observeSlot(Kind.PERK,3,lines("Required level: [70]"),now);
  p.observeSlot(Kind.PERK,4,lines("Required level: [100]"),now);
  p.observeSlot(Kind.KILLSTREAK,1,lines("Selected: Second Gapple"),now);
  p.observeSlot(Kind.KILLSTREAK,2,lines("Required level: [75]"),now);
 }
 public static void main(String[] args){
  for(String title:new String[]{"THE HYPIXEL PIT","THE PIT CLASSIC"}){
   LoadoutPlanner p=planner();LoadoutContext c=new LoadoutContext();Object world=new Object();
   c.observe(p,world,"account@server",title,lines("Prestige: IV","Level: [55]"));p.requirements(55,false);p.gold(5699.54,0);verified(p,0);
   ok(c.identified(),"separate Prestige line identifies profile");
   ok(p.next(null,0)==null,"first NPC scan finds only level-locked actions");
   for(int i=1;i<=60;i++){
    long now=i*1000L;
    // Repeat the observed sequence: death, temporary empty world, new World object, partial sidebar, then same prestige.
    c.disconnected(p);world=new Object();
    c.observe(p,world,"account@server",title,lines("Level: [55]","Gold: 100000g"));p.requirements(55,false);p.gold(100000+i,now);
    ok(p.next(null,now)==null,"same profile respawn cannot queue another inspection");
    c.observe(p,world,"account@server",title,lines("Prestige: IV","Level: [55]"));
    ok(p.next(null,now)==null,"restored metadata and extra gold cannot bypass slot levels");
    ok(p.slot(Kind.PERK,1).state==State.SATISFIED,"Golden Heads remains verified");
    ok(p.slot(Kind.PERK,3).state==State.LEVEL_LOCKED,"Bonk remains level-locked with The Way disabled");
   }
   p.requirements(69,false);p.gold(2000,61000);ok(p.next(null,61000)==null,"level 69 still no NPC trip");
   p.requirements(70,false);p.gold(1999,62000);ok(p.next(null,62000)==null,"new level alone cannot bypass gold");
   p.requirements(71,false);p.gold(2000,63000);ok(p.next(null,63000)==p.slot(Kind.PERK,3),"level and exact gold now permit Bonk trip");
   c.observe(p,world,"account@server",title,lines("Prestige: V","Level: [10]"));
   ok(!p.slot(Kind.PERK,1).equipmentKnown,"real prestige change clears verified loadout");
   p.requirements(10,false);p.gold(1000,64000);ok(p.next(null,64000)!=null,"after prestige eligible action can inspect again");
  }
  LoadoutPlanner p=planner();LoadoutContext c=new LoadoutContext();Object world=new Object();
  c.observe(p,world,"a@server","THE HYPIXEL PIT",lines("Level: [IV-55]"));p.requirements(55,false);verified(p,0);
  ok(c.identified(),"bracket prestige format remains supported");
  c.observe(p,new Object(),"a@server","THE HYPIXEL PIT",lines("Level: [IV-55]"));ok(p.slot(Kind.PERK,1).equipmentKnown,"bracket identity survives world replacement");
  c.observe(p,world,"a@server","THE HYPIXEL PIT",lines("Profile: A","Prestige: IV"));verified(p,0);
  c.observe(p,world,"a@server","THE HYPIXEL PIT",lines("Profile: B","Prestige: IV"));ok(!p.slot(Kind.PERK,1).equipmentKnown,"real profile switch clears equipment");
  verified(p,0);c.observe(p,world,"b@server","THE HYPIXEL PIT",lines("Prestige: IV"));ok(!p.slot(Kind.PERK,1).equipmentKnown,"account change clears equipment");
  verified(p,0);c.observe(p,world,"b@server","THE PIT CLASSIC",lines("Prestige: IV"));ok(!p.slot(Kind.PERK,1).equipmentKnown,"game mode change clears equipment");
  p=planner();c=new LoadoutContext();c.observe(p,world,"a@server","THE HYPIXEL PIT",lines("Level: [55]"));p.requirements(55,false);verified(p,0);
  c.observe(p,new Object(),"a@server","THE HYPIXEL PIT",lines("Level: [55]"));ok(!p.slot(Kind.PERK,1).equipmentKnown,"unidentified profile still revalidates on world replacement");
  System.out.println("LoadoutContextTest PASS checks="+checks);
 }
}
