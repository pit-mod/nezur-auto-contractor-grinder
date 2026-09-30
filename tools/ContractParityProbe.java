import com.nezurstandalone.contract.*;
import java.util.*;
public class ContractParityProbe {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println(s+"=PASS");}
 static ContractOffer offer(int i,String task,int vile){return ContractOffer.parse(10+i,403,0,"Choice #"+i,Arrays.asList("Task:",task,"Time limit: 5 minutes","Bonus: "+vile+" Chunk of Vile","Click to pick!"));}
 public static void main(String[]args){
  List<ContractOffer> a=Arrays.asList(offer(1,"Eat 12 golden apples.",2),offer(2,"Kill 10 players.",2),offer(3,"Fish a diamond sword.",0));
  check(a.get(0).type==ContractOffer.Type.GOLDEN_APPLES,"apple_parser");
  check(offer(1,"Get a killing blow using your fist inside the pit.",2).type==ContractOffer.Type.FIST_MID_KILLS,"singular_fist_parser");
  StableOfferGate gate=new StableOfferGate();check(!gate.ready(a,0,3000)&&!gate.ready(a,2999,3000)&&gate.ready(a,3000,3000),"three_offers_stable_three_seconds");
  check(ContractSelector.choose(a,ContractSelector.DEFAULT_PRIORITY,Arrays.asList(ContractOffer.Type.GOLDEN_APPLES,ContractOffer.Type.KILL_PLAYERS),true,new Random(1)).offer.type==ContractOffer.Type.GOLDEN_APPLES,"priority");
  List<ContractOffer> zero=Arrays.asList(offer(1,"Fish a diamond sword.",0),offer(2,"Get a Triple kill.",0),offer(3,"Kill 7 players using lava.",0));
  ContractSelector.Choice none=ContractSelector.choose(zero,ContractSelector.DEFAULT_PRIORITY,Collections.<ContractOffer.Type>emptyList(),true,new Random(1));System.out.println("zero_vile_fallback="+none.mode);
  if(args.length>0 && args[0].equals("strict"))check(none.mode==ContractSelector.Mode.NONE,"zero_vile_rejected");
  List<ContractOffer> fall=Arrays.asList(offer(1,"Fish a diamond sword.",0),offer(2,"Get a Triple kill.",2),offer(3,"Kill 7 players using lava.",0));
  int violations=0;for(int i=0;i<100;i++){ContractSelector.Choice c=ContractSelector.choose(fall,ContractSelector.DEFAULT_PRIORITY,Collections.<ContractOffer.Type>emptyList(),true,new Random(i));if(c.offer.vileAmount==0)violations++;}
  System.out.println("zero_vile_fallback_violations_100="+violations);
  if(args.length>0 && args[0].equals("strict"))check(violations==0,"random_fallback_vile_only");
  String[] original={"Vampire","Strength-Chaining","Golden Heads",null};String[] snapshot=ContractPerkPlan.snapshot(original),during=ContractPerkPlan.gappleLoadout(snapshot);
  check(during[0]==null&&during[2]==null&&"Strength-Chaining".equals(during[1])&&snapshot[0].equals("Vampire")&&snapshot[3]==null,"exact_perk_snapshot_and_blocker_removal");
  check(ContractPerkPlan.kungFuSlot(original,new String[]{null,null,null,null},new boolean[]{true,true,true,true})==1,"kung_fu_protects_healing_perks");
  String[] all=ContractPerkPlan.snapshot(original);Arrays.fill(all,null);check(Arrays.equals(snapshot,original)&&all[0]==null&&all[3]==null,"no_perk_empty_slots_restore_snapshot");
  check(ContractPerkPlan.gappleInitialAction(true,2,8)==0&&ContractPerkPlan.gappleInitialAction(false,3,8)==1&&ContractPerkPlan.gappleInitialAction(false,8,8)==2,"bounded_natural_deaths");
  check(!ContractPerkPlan.gappleOofReady(11499,10000,0,1500,12000,true)&&ContractPerkPlan.gappleOofReady(11500,10000,0,1500,12000,true)&&!ContractPerkPlan.gappleOofReady(11500,10000,0,1500,12000,false),"airborne_oof_1500ms");
  check(!ContractPerkPlan.gappleCooldownReady(21999,10000,12000)&&ContractPerkPlan.gappleCooldownReady(22000,10000,12000),"oof_cooldown_12seconds");
  check(ContractPerkPlan.gappleComplete(12,12),"completion_before_next_cycle");
  ContractTracker tracker=new ContractTracker();tracker.confirm(new ContractScoreboard.Snapshot(true,true,true,ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW,0,10,-1,"Diamond"),1000);
  ContractScoreboard.Snapshot live=ContractScoreboard.parse("THE HYPIXEL PIT",Arrays.asList("Contract: 02:45","Kills: 4/10"));
  tracker.observe(new ContractScoreboard.Snapshot(false,false,false,ContractOffer.Type.UNKNOWN,0,0,-1,""),2000);
  ContractScoreboard.Snapshot back=tracker.observe(live,3000);check(back.type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW&&back.current==4,"lobby_identity_and_progress");
  check(ContractScoreboard.activeMenu(Arrays.asList("Kill 10 players using a diamond sword.","Kills: 4/10")).type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW,"npc_disambiguation");
  ContractConnectionFlow flow=new ContractConnectionFlow();flow.observe(ContractConnectionFlow.Location.PIT,0,0);flow.requestSwap(0);check(flow.action(1000,false)==ContractConnectionFlow.Action.RETURN_TO_SPAWN,"swap_spawn_first");flow.sent(1000);flow.observe(ContractConnectionFlow.Location.LOBBY,12000,0);check(flow.action(12000,true)==ContractConnectionFlow.Action.JOIN,"lobby_rejoin");
  ContractStuckWatch watch=new ContractStuckWatch();watch.blocked(0,true,0,0,0);check(watch.blocked(5000,true,0,0,0),"stuck_watch");watch.dispatched(5000);watch.blocked(5001,true,0,0,0);check(!watch.blocked(10001,true,0,0,0),"stuck_oof_not_spammed");
 }
}