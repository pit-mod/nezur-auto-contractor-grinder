import com.nezurstandalone.engine.PitCombatArea;
import com.nezurstandalone.engine.SpireTargeting;
import com.nezurstandalone.combat.AuraTargeting.Candidate;
import java.util.*;
public final class GrinderMovementTest {
 private static int checks;
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static Candidate<String> e(String s,int id,double d,double hp){return new Candidate<>(s,id,d,hp,0,0,hp);}
 public static void main(String[] args){
  String[] maps={"GENESIS","CASTLE","CORALS"};double[] radius={55,40,39};
  for(int i=0;i<maps.length;i++){
   check(PitCombatArea.contains(maps[i],radius[i],70,0),"inclusive +20 margin");
   check(!PitCombatArea.contains(maps[i],radius[i]+.01,70,0),"beyond margin");
   check(PitCombatArea.contains(maps[i],-radius[i],70,0),"negative margin");
   check(!PitCombatArea.contains(maps[i],radius[i],70,radius[i]),"diagonal distance");
  }
  for(String map:new String[]{"ELEMENTS","FOUR_SEASONS"}){
   check(PitCombatArea.contains(map,33,90,0),"rectangle east +20");
   check(!PitCombatArea.contains(map,33.01,90,0),"rectangle east beyond20");
   check(PitCombatArea.contains(map,-32,90,0),"rectangle west +20");
   check(!PitCombatArea.contains(map,33,90,34),"rounded diagonal margin");
   check(!PitCombatArea.contains(map,0,140,0),"no vertical expansion into spawn");
   check(!PitCombatArea.contains(map,0,60,0),"no vertical expansion underground");
  }
  check(!PitCombatArea.contains("UNKNOWN",0,60,0),"unknown map");
  check(!PitCombatArea.contains("CASTLE",Double.NaN,60,0),"invalid coordinate");
  check(PitCombatArea.mayReturn(false,true,true),"core idle retreat");
  check(!PitCombatArea.mayReturn(false,false,true),"no snapback in margin");
  check(PitCombatArea.mayReturn(false,false,false),"return beyond margin");
  for(boolean core:new boolean[]{false,true})for(boolean roam:new boolean[]{false,true})check(!PitCombatArea.mayReturn(true,core,roam),"no Rage return");
  check(PitCombatArea.sameSpireFloor(100,100),"same floor");
  check(PitCombatArea.sameSpireFloor(101.25,100),"jump retains target");
  check(PitCombatArea.sameSpireFloor(100,103.5),"knockback tolerance");
  check(!PitCombatArea.sameSpireFloor(100,110),"upper floor excluded");
  check(!PitCombatArea.sameSpireFloor(100,90),"lower floor excluded");
  SpireTargeting<String> p=new SpireTargeting<>();
  check("near".equals(p.select(Arrays.asList(e("far",1,15,1),e("near",2,2,20)),3)),"reachable beats distant low hp");
  check("near".equals(p.select(Arrays.asList(e("near",2,2,20),e("jitter",3,1.9,20)),3)),"stable focus");
  check("finish".equals(p.select(Arrays.asList(e("near",2,2,20),e("finish",4,2,2)),3)),"finish low hp at equal reach");
  check("hit".equals(p.select(Arrays.asList(e("finish",4,3.1,2),e("hit",5,2.9,20)),3)),"attackable beats out of range focus");
  check(p.select(Collections.emptyList(),3)==null,"clear absent target");
  check("closer".equals(p.select(Arrays.asList(e("weak",6,20,1),e("closer",7,5,20)),3)),"short approach");p.reset();
  check("a".equals(p.select(Arrays.asList(e("b",9,2,20),e("a",8,2,20)),3)),"stable tie");
  check(p.select(Arrays.asList(e("dead",10,1,0),e("bad",11,Double.NaN,20)),3)==null,"reject dead/invalid");
  System.out.println("GrinderMovementTest PASS checks="+checks);
 }
}
