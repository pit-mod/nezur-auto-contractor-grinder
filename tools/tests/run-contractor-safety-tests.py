from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/contractor-safety-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
java=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
source=(root/'src/main/java/com/nezurstandalone/module/impl/player/AutoContractor.java').read_text(encoding='utf-8')
def extract(prefix):
 a=source.index(prefix);i=source.index('{',a)+1;depth=1
 while depth:
  if source[i]=='{':depth+=1
  elif source[i]=='}':depth-=1
  i+=1
 return source[a:i]
def write(name,text):
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
write(Path('com/nezurstandalone/control/Clock.java'),'package com.nezurstandalone.control;public class Clock{public static long millis(){return 10000;}}')
write(Path('com/nezurstandalone/control/ClientSession.java'),'package com.nezurstandalone.control;public class ClientSession{public static long current(){return 2;}}')
write(Path('com/nezurstandalone/control/CommandCoordinator.java'),'package com.nezurstandalone.control;public class CommandCoordinator{public static void cancel(Object o){}}')
write(Path('com/nezurstandalone/contract/ContractCombatPolicy.java'),'package com.nezurstandalone.contract;public class ContractCombatPolicy{public static void clear(){}}')
assert 'if(!selectedFallback && selected.type==ContractOffer.Type.GOLDEN_APPLES&&!ensureInitialAppleBeforeAcceptance(now))return;' in source
click=extract('    private boolean click(ContainerChest')
assert click.index('!ensureInitialAppleBeforeAcceptance(now)')<click.index('mc.playerController.windowClick')
assert 'gapplePendingOof=true;gappleOofSentAt=now;gappleLastOof=now;' in source
assert 'gap'+'pleOofRateLimited(message)' in source
write(Path('ContractorSafetyTest.java'),'''import com.nezurstandalone.contract.ContractPerkPlan;
import com.nezurstandalone.utils.ContractorConfigMigration;import com.google.gson.*;
public class ContractorSafetyTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
'''+extract('    public enum State')+'''
 State state=State.GAPPLE_ENTERING_MID;long actionSession,noOfferRetryAt,nextPoll,nextAction,stuckRespawnUntil,gappleRespawnAt=50,gappleRespawnSettleUntil;
 boolean gappleActive=true,gapplePreAccept=true,gapplePendingOof=true,gappleDeathSeen=true,purchaseConfirmed,pendingClick,pendingQuestClick,classicMenuAction,selectedFallback;
 int classicActionSlot,attempts,apples,gappleEatingSlot,gappleDeaths=1,begins,cancels,restores;String classicActionName,purchaseName;Object selected=new Object(),world,gappleFollow=new Object(),gapplePad=new Object(),gappleWait=new Object(),gappleStage=new Object();java.util.Set<Object> rejectedGapplePads=new java.util.HashSet<>();
 boolean unconfirmed,halted;MC mc=new MC();Flag goldenApples=new Flag();Reset offerGate=new Reset(),stuckWatch=new Reset(),menuAction=new Reset();AutoGrinder engine=new AutoGrinder();
 static class Flag{boolean enabled=true;}
 static class Player{boolean isDead;float getHealth(){return isDead?0:20;}}
 static class MC{Player thePlayer=new Player();Object theWorld=new Object();}
 static class Reset{void reset(){}void clear(){}}
 static class AutoGrinder{boolean eligible=true,ready=true,lease=true,restoring;int inspections;
  boolean gappleEligible(){return eligible;}boolean gapplePerksReady(){return ready;}boolean hasTemporaryPerkLease(){return lease;}
  boolean isRestoringTemporaryPerk(){return restoring;}void requestGappleInspection(){inspections++;}}
 boolean isToggled(){return true;}boolean hasUnconfirmedOperation(){return unconfirmed;}
 void uncertainAction(String s){halted=true;}void cancelGapple(){cancels++;gappleActive=gapplePreAccept=false;}
 boolean gapplePastInitialApple(){return false;}
 void cancelInventoryOperation(){}void closeOwnedMenu(){}void cleanupNavigation(){}void stopGrinder(){}void startGrinder(){}
 void restorePerks(){restores++;}void logImportant(String s){}void move(State s){state=s;}
 void gapplePhase(State s,long now,String msg){state=s;}AutoGrinder grinder(){return engine;}
 int goldenAppleCount(){return apples;}void beginGapple(long now){begins++;gappleActive=true;state=State.GAPPLE_PREPARING;}
'''+extract('    private boolean ensureInitialAppleBeforeAcceptance(')+'\n'+extract('    private void resetActionSession()')+'\n'+extract('    private static double contractPurchaseCost(')+'''
 public static void main(String[] args)throws Exception{
  String price=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);
  check(contractPurchaseCost(price)==1000,"Captured confirmation cost parses correctly");
  check(Double.isNaN(contractPurchaseCost("Confirm unknown cost")),"Missing price cannot fabricate debit evidence");
  ContractorSafetyTest c=new ContractorSafetyTest();
  check(!c.ensureInitialAppleBeforeAcceptance(1000)&&c.begins==1&&c.gapplePreAccept,"No apple starts preparation instead of accepting");
  c.apples=1;check(c.ensureInitialAppleBeforeAcceptance(2000)&&c.begins==1,"An actual apple allows acceptance without restarting prep");
  c.resetActionSession();
  check(c.gappleActive&&c.gapplePreAccept&&c.cancels==0,"Preaccept intent survives player/session replacement");
  check(c.state==State.GAPPLE_WAIT_APPLE&&c.selected==null,"Respawn returns to initial apple wait, not Contractor acceptance");
  check(c.gappleDeaths==1&&!c.gappleDeathSeen,"Natural death count preserved without counting live replacement twice");
  check(c.gapplePad==null&&c.gappleWait==null&&c.gappleFollow==null,"Old-world navigation references cleared");
  c=new ContractorSafetyTest();c.engine.ready=false;c.engine.lease=false;c.resetActionSession();
  check(c.state==State.GAPPLE_PREPARING&&c.engine.inspections==1,"Missing perk lease rechecks server loadout");
  c=new ContractorSafetyTest();c.unconfirmed=true;c.resetActionSession();check(c.halted&&c.cancels==0,"Unconfirmed operation still halts on session change");
  c=new ContractorSafetyTest();c.engine.eligible=false;
  check(!c.ensureInitialAppleBeforeAcceptance(1000)&&c.begins==0&&c.restores==1,"Unavailable apple preparation cannot accept a fallback offer");
  check(c.state==State.NO_VILE_OFFERS&&c.noOfferRetryAt==301000,"Unavailable preparation uses bounded retry");
  check(!ContractPerkPlan.gappleCooldownReady(20499,10000,10500),"No activation before dispatch plus 10.5 seconds");
  check(ContractPerkPlan.gappleCooldownReady(20500,10000,10500),"Ready exactly 10.5 seconds after dispatch");
  check(ContractPerkPlan.gappleCooldownReady(22000,10000,10500),"Late eating completion does not add another 10.5 seconds");
  check(!ContractPerkPlan.gappleCooldownReady(20499,10000,0),"Configured low value cannot skip 10.5-second minimum");
  check(!ContractPerkPlan.gappleCooldownReady(20500,10000,15000),"Explicit longer custom cooldown remains respected");
  check(!ContractPerkPlan.gappleFlightOofReady(20499,19500,10000,10500,false,-.2),"Descending flight cannot bypass cooldown");
  check(ContractPerkPlan.gappleFlightOofReady(20500,19500,10000,10500,false,-.2),"Descending flight can use the remaining airborne window");
  check(!ContractPerkPlan.gappleFlightOofReady(20500,19500,10000,10500,true,-.2),"Must still be airborne");
  JsonObject config=new JsonObject(),module=new JsonObject(),settings=new JsonObject();
  rootConfig(config,module,settings,12000);ContractorConfigMigration.migrate(config);
  check(settings.get("Gapple Oof Cooldown ms").getAsInt()==10500,"Existing old default migrates to requested 10.5 seconds");
  settings.addProperty("Gapple Oof Cooldown ms",15000);ContractorConfigMigration.migrate(config);
  check(settings.get("Gapple Oof Cooldown ms").getAsInt()==15000,"Custom longer delay preserved");
  System.out.println("Contractor acceptance, respawn and timing: "+checks+" checks passed");
 }
 static void rootConfig(JsonObject root,JsonObject module,JsonObject settings,int delay){
  settings.addProperty("Gapple Oof Cooldown ms",delay);module.add("settings",settings);root.add("AutoContractor",module);
 }
}''')
gson=next((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').rglob('gson-*.jar'))
files=[*src.rglob('*.java'),root/'src/main/java/com/nezurstandalone/contract/ContractPerkPlan.java',root/'src/main/java/com/nezurstandalone/utils/ContractorConfigMigration.java']
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',str(gson),'-d',str(out),*[str(f) for f in files]],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out)+';'+str(gson),'ContractorSafetyTest',str(root/'tools/tests/fixtures/contractor-price-confirm-20261101.txt')],check=True)
