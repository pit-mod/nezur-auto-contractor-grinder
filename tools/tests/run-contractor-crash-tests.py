from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/contractor-crash-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
j=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
contractor=(root/'src/main/java/com/nezurstandalone/module/impl/player/AutoContractor.java').read_text(encoding='utf-8')
engine=(root/'src/main/java/com/nezurstandalone/engine/GrinderEngine.java').read_text(encoding='utf-8')
def extract(s,prefix):
 a=s.index(prefix);i=s.index('{',a)+1;depth=1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[a:i]
def write(name,text):
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
write(Path('com/nezurstandalone/contract/ContractCombatPolicy.java'),'package com.nezurstandalone.contract;public class ContractCombatPolicy{public static void install(ContractOffer.Type t,long d,double r){}}')
write(Path('com/nezurstandalone/control/Clock.java'),'package com.nezurstandalone.control;public class Clock{public static long millis(){return 10000;}}')
write(Path('ContractorCrashTest.java'),'''import com.nezurstandalone.contract.*;
public class ContractorCrashTest {
 enum State{PREPARING_CONTRACT,EXECUTING_CONTRACT,FALLBACK_REFRESH_MODE,CHECKING_SCOREBOARD}
 static class Num{double value=100;}Num sneakDelay=new Num(),sneakRadius=new Num();State state=State.PREPARING_CONTRACT;long actionSession=1,stateSince=0;boolean toggled=true,ready,executable=true;int interruption,moves,starts;
 ContractScoreboard.Snapshot scoreboard=board();
 static ContractScoreboard.Snapshot board(){return new ContractScoreboard.Snapshot(true,true,true,ContractOffer.Type.NO_ARMOR_KILLS,0,10,300,"No armor kills");}
 static class KillAura{static double configuredAttackRange(){return 3;}}
 boolean isToggled(){return toggled;}boolean isExecutable(ContractOffer.Type t){return executable;}
 boolean prepareObjective(ContractOffer.Type t){
  if(interruption==1){toggled=false;scoreboard=null;}
  if(interruption==2)scoreboard=board();
  if(interruption==3)actionSession++;
  if(interruption==4)state=State.CHECKING_SCOREBOARD;
  return ready;
 }
 void startGrinder(){starts++;if(interruption==5){toggled=false;scoreboard=null;}}
 void stopGrinder(){}void log(String s){}void move(State s){state=s;moves++;}
'''+extract(contractor,'    private void prepareActiveObjective(')+'''
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(boolean ready:new boolean[]{false,true})for(int interruption=1;interruption<=4;interruption++){
   ContractorCrashTest c=new ContractorCrashTest();c.ready=ready;c.interruption=interruption;c.prepareActiveObjective(10000);
   check(c.moves==0&&c.starts==0,"Interrupted preparation cannot continue: "+interruption+" ready="+ready);
  }
  ContractorCrashTest c=new ContractorCrashTest();c.ready=true;c.prepareActiveObjective(10000);
  check(c.state==State.EXECUTING_CONTRACT&&c.starts==1,"Successful armor preparation starts normally");
  c=new ContractorCrashTest();c.prepareActiveObjective(1000);check(c.moves==0,"Pending armor transaction waits");
  c=new ContractorCrashTest();c.prepareActiveObjective(10000);check(c.state==State.FALLBACK_REFRESH_MODE,"Bounded preparation timeout retained");
  c=new ContractorCrashTest();c.ready=true;c.interruption=5;c.prepareActiveObjective(1000);check(c.moves==0,"Grinder startup interruption cannot reenter execution");
  c=new ContractorCrashTest();c.scoreboard=null;c.prepareActiveObjective(10000);check(c.moves==0,"Missing scoreboard cannot crash");
  System.out.println("Contractor interruption regression: "+checks+" checks passed");
 }
}''')
write(Path('ContractorLoadoutTest.java'),'''public class ContractorLoadoutTest {
 boolean allowed,refreshed;Loadout loadout=new Loadout();
 static class Loadout{int queries;Object next(Object kind,long now){queries++;return new Object();}}
 boolean normalLoadoutAllowed(){return allowed;}void refreshLoadoutRequirements(){refreshed=true;}void refreshLoadoutGold(){}
'''+extract(engine,'    private boolean managedActionReady()')+'''
 public static void main(String[] args){
  ContractorLoadoutTest c=new ContractorLoadoutTest();
  if(c.managedActionReady()||c.refreshed||c.loadout.queries!=0)throw new AssertionError("Contractor must block normal perk and killstreak scheduling");
  c.allowed=true;if(!c.managedActionReady()||!c.refreshed||c.loadout.queries!=1)throw new AssertionError("Normal scheduling must resume after Contractor releases control");
  System.out.println("Contractor normal-loadout suppression: 2 checks passed");
 }
}''')
assert 'applyContractorProfile(grinder);' in extract(contractor,'    @Override protected void onEnable()')
assert 'loadout.suspend(hasTemporaryPerkLease()||suppressed)' in engine
assert 'perks[i].getMode(),autoPerk.isEnabled()&&!suppressed)' in engine
assert 'streaks[i].getMode(),autoKillstreak.isEnabled()&&!suppressed)' in engine
assert 'if(managedVisit&&!hasTemporaryPerkLease()&&!normalLoadoutAllowed())return false;' in extract(engine,'    private boolean clickMenu(ContainerChest container, int slot, Runnable')
paths=[*src.rglob('*.java'),root/'src/main/java/com/nezurstandalone/contract/ContractOffer.java',root/'src/main/java/com/nezurstandalone/contract/ContractScoreboard.java']
subprocess.run([str(j/'javac.exe'),'-encoding','UTF-8','-d',str(out),*[str(p) for p in paths]],check=True)
for test in ['ContractorCrashTest','ContractorLoadoutTest']:subprocess.run([str(j/'java.exe'),'-cp',str(out),test],check=True)
