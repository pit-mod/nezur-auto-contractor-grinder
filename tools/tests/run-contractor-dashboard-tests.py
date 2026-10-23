from pathlib import Path
import subprocess,zipfile
root=Path(__file__).resolve().parents[2]
out=root/'verification/contractor-dashboard-20261021';src=out/'src';classes=out/'classes'
classes.mkdir(parents=True,exist_ok=True)
java=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
def write(p,s):
 p=src/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
source=(root/'src/main/java/com/nezurstandalone/module/impl/player/AutoContractor.java').read_text(encoding='utf-8')
def extract(prefix):
 a=source.index(prefix);i=source.index('{',a)+1;depth=1
 while depth:
  if source[i]=='{':depth+=1
  elif source[i]=='}':depth-=1
  i+=1
 return source[a:i]
assert 'scoreboard=observeContractSidebar(eventBoard,lines,now);' in source
assert 'scoreboard=observeContractSidebar(live,liveLines,now);' in source
write(Path('com/nezurstandalone/control/Clock.java'),'package com.nezurstandalone.control;public class Clock{public static long millis(){return 10000;}}')
write(Path('com/nezurstandalone/control/CommandCoordinator.java'),'package com.nezurstandalone.control;public class CommandCoordinator{public static int cancelled;public static void cancel(Object o){cancelled++;}}')
write(Path('com/nezurstandalone/contract/ContractCombatPolicy.java'),'package com.nezurstandalone.contract;public class ContractCombatPolicy{public static int cleared;public static void clear(){cleared++;}}')
write(Path('ContractorCompletionTest.java'),'''import com.nezurstandalone.contract.*;import com.nezurstandalone.utils.PitStreak;import java.util.*;
public class ContractorCompletionTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
'''+extract('    public enum State')+'''
 State state=State.EXECUTING_CONTRACT;long stateSince,nextPoll,hudSnapshotAt,thinLobbySince,pendingSince=100,activeSince=100,nextAction=500,noOfferRetryAt=100,nextReturnToSpawn=100,stuckRespawnUntil=100;
 boolean completedSidebarSuppressed,serverStreakGoalComplete,pendingClick=true,pendingQuestClick=true,classicMenuAction=true,contractStartedChat=true,acceptedByThisModule=true,inspectingActiveIdentity=true,purchaseConfirmed=true,equipmentPurchasePending=true,selectedFallback=true;
 int hudCompleted,classicActionSlot=1,attempts=2,missingSamples=2,cleanupCalls,restoreCalls,stopCalls;
 Object pendingContainer=new Object(),selected=new Object(),observedContainer=new Object();String pendingContents="old",classicActionName="old",purchaseName="old",observedContents="old";
 ContractScoreboard.Snapshot scoreboard;ContractTracker contractTracker=new ContractTracker();
 static class Reset{int calls;void reset(){calls++;}void cancelSwap(){calls++;}}
 Reset connection=new Reset(),offerGate=new Reset(),stuckWatch=new Reset();
 static class Flag{boolean enabled=true;}Flag debug=new Flag();
 boolean restoring,grinderEnabled=true;String order="";
 void cancelGapple(){order+="cancel ";}void closeOwnedMenu(){order+="close ";}
 void cleanupNavigation(){cleanupCalls++;pendingClick=false;order+="navigation ";}
 void restorePerks(){restoreCalls++;restoring=true;order+="restore ";}
 void stopGrinder(){stopCalls++;if(!restoring)grinderEnabled=false;order+="stop ";}void log(String s){}
'''+extract('    private ContractScoreboard.Snapshot observeContractSidebar(')+ '\n'+extract('    private void finishContract(')+'\n'+extract('    private void move(State next)')+'''
 public static void main(String[] args){
  check(PitStreak.read(Arrays.asList("Streak: 1,234"))==1234,"Shared grinder streak reader handles separators");
  check(PitStreak.read(Arrays.asList("§7Streak: §a9.5"))==9,"Fractions do not turn 9.5 into 95");
  check(PitStreak.read(Arrays.asList("Kill Streak: 2/25","Contract: 04:00"))==-1,"Contract progress cannot masquerade as player streak");
  check(PitStreak.read(Arrays.asList("Streak: 0"))==0,"Death provides a real zero");
  check(PitStreak.read(Arrays.asList("Strength: V"))==-1,"Missing streak remains unknown");
  check(ContractOffer.completedMessage("§aCONTRACT COMPLETED! +2000g",true),"Colored server completion recognized");
  check(ContractOffer.completedMessage("CONTRACT COMPLETE!",true),"Classic complete variant recognized");
  check(!ContractOffer.completedMessage("CONTRACT COMPLETE!",false),"Hypixel grammar preserved");
  check(!ContractOffer.completedMessage("Player: CONTRACT COMPLETED!",true),"Player chat cannot complete contract");
  check(!ContractOffer.completedMessage("CONTRACT FAILED!",true),"Failure is never success");
  ContractorCompletionTest c=new ContractorCompletionTest();
  ContractScoreboard.Snapshot raw=new ContractScoreboard.Snapshot(true,true,true,ContractOffer.Type.KILL_STREAK,0,25,240,"Kill streak");
  c.contractTracker.confirm(raw,0);
  c.scoreboard=c.observeContractSidebar(raw,Arrays.asList("Streak: 9"),100);
  check(c.scoreboard.current==9&&c.scoreboard.target==25,"Current player streak updates active contract");
  c.scoreboard=c.observeContractSidebar(raw,Arrays.asList("Streak: 0"),200);
  check(c.scoreboard.current==0,"Death resets live streak progress");
  c.scoreboard=c.observeContractSidebar(raw,Arrays.asList("Streak: 25"),300);
  check(c.scoreboard.current==25&&!c.serverStreakGoalComplete,"Local goal does not fabricate server completion");
  c.scoreboard=c.observeContractSidebar(raw,Collections.<String>emptyList(),400);
  check(c.scoreboard.current==25,"Temporary missing player streak is not a death");
  c.finishContract("server chat");
  check(c.completedSidebarSuppressed&&c.state==State.CONTRACT_COMPLETE&&!c.scoreboard.active,"Chat completion immediately clears active contract");
  check(c.selected==null&&!c.selectedFallback&&!c.acceptedByThisModule&&!c.contractStartedChat,"Selected and accepted identity fully cleared");
  check(!c.pendingClick&&!c.pendingQuestClick&&c.pendingContainer==null&&c.pendingSince==0,"Old menu action cannot survive completion");
  check(c.purchaseName==null&&!c.purchaseConfirmed&&!c.equipmentPurchasePending&&!c.inspectingActiveIdentity,"Shop and inspection state cleared");
  check(c.attempts==0&&c.missingSamples==0&&c.stuckRespawnUntil==0&&c.nextAction==0,"Recovery counters and deadlines cleared");
  check(c.restoreCalls==1&&c.grinderEnabled&&c.restoring&&c.order.indexOf("restore")<c.order.indexOf("stop"),"Restore requested before stopping combat; restoring engine stays enabled");
  check(c.cleanupCalls==1&&c.connection.calls==1&&com.nezurstandalone.control.CommandCoordinator.cancelled==1,"Owned navigation and pending travel cancelled");
  c.scoreboard=c.observeContractSidebar(raw,Arrays.asList("Streak: 25"),500);
  check(!c.scoreboard.active&&!c.serverStreakGoalComplete,"Stale sidebar cannot revive completed contract");
  c.finishContract("duplicate");check(c.hudCompleted==1&&c.restoreCalls==1,"Duplicate completion does not count or restore twice");
  c.completedSidebarSuppressed=false;c.contractTracker.clear();c.contractTracker.confirm(raw,600);
  c.scoreboard=c.observeContractSidebar(raw,Arrays.asList("Streak: 1"),700);
  check(c.scoreboard.active&&c.scoreboard.current==1,"A newly accepted contract starts independent tracking");
  ContractScoreboard.Snapshot other=new ContractScoreboard.Snapshot(true,true,true,ContractOffer.Type.CHAIN_KILLS,2,3,240,"Chain kills");
  c.contractTracker.clear();c.contractTracker.confirm(other,800);
  check(c.observeContractSidebar(other,Arrays.asList("Streak: 30"),900).current==2,"Chain kills never use player streak");
  ContractorCompletionTest sample=new ContractorCompletionTest();
  sample.contractTracker.confirm(new ContractScoreboard.Snapshot(true,true,true,ContractOffer.Type.KILL_STREAK,0,15,180,"Reach a kill streak of 15"),1000);
  ContractScoreboard.Snapshot generic=ContractScoreboard.parse("THE HYPIXEL PIT",Arrays.asList("Kills: 0/15","Contract: 03:00"));
  ContractScoreboard.Snapshot progress=sample.observeContractSidebar(generic,Arrays.asList("Streak: 7"),1100);
  check(progress.type==ContractOffer.Type.KILL_STREAK&&progress.current==7&&progress.target==15,"Generic Kills 0/15 cannot erase known streak task or freeze its progress");
  check(!sample.contractTracker.needsIdentityInspection(progress),"Known streak task needs no redundant NPC inspection");
  progress=sample.observeContractSidebar(generic,Arrays.asList("Streak: 0"),1200);
  check(progress.current==0&&progress.type==ContractOffer.Type.KILL_STREAK,"Death resets correct streak objective despite generic server label");
  sample.observeContractSidebar(ContractScoreboard.parse("THE HYPIXEL PIT",Arrays.asList("Kills: 15/15","Contract: 02:00")),Arrays.asList("Streak: 0"),1300);
  check(sample.serverStreakGoalComplete,"Server-confirmed goal survives later player streak reset");
  System.out.println("Contract completion and streak: "+checks+" checks passed");
 }
}''')
game=Path.home()/'.gradle/caches/minecraft/net/minecraft/minecraft/1.8.9/minecraft-1.8.9.jar'
with zipfile.ZipFile(game) as z:(out/'ascii.png').write_bytes(z.read('assets/minecraft/textures/font/ascii.png'))
paths=[root/p for p in ['src/main/java/com/nezurstandalone/gui/GrinderDashboard.java','src/main/java/com/nezurstandalone/gui/ContractorDashboard.java','src/main/java/com/nezurstandalone/utils/PitStreak.java','src/main/java/com/nezurstandalone/contract/ContractTracker.java','src/main/java/com/nezurstandalone/contract/ContractOffer.java','src/main/java/com/nezurstandalone/contract/ContractScoreboard.java','tools/tests/dashboard-stubs/com/nezurstandalone/gui/GuiDraw.java','tools/tests/ContractorDashboardTest.java']]+list(src.rglob('*.java'))
argfile=out/'javac.args';argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in ['-encoding','UTF-8','-d',classes,*paths]),encoding='utf-8')
subprocess.run([str(java/'javac.exe'),'@'+str(argfile)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(classes),'ContractorCompletionTest'],check=True)
subprocess.run([str(java/'java.exe'),'-Djava.awt.headless=true','-cp',str(classes),'ContractorDashboardTest',str(out),str(out/'ascii.png')],check=True)
