"""Exercise production prestige startup/access methods with the real GUI lease."""
from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[2]
out=root/'build/prestige-access-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
def write(name,s):
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
engine=(root/'src/main/java/com/nezurstandalone/engine/GrinderEngine.java').read_text(encoding='utf-8')
def method(name):
 start=engine.index('    private boolean '+name+'(');op=engine.index('{',start);depth=1;i=op+1
 while depth:
  if engine[i]=='{':depth+=1
  elif engine[i]=='}':depth-=1
  i+=1
 return engine[start:i]
write('com/nezurstandalone/control/ClientSession.java','package com.nezurstandalone.control;public class ClientSession {public static long current(){return 1;}}')
write('com/nezurstandalone/control/Clock.java','package com.nezurstandalone.control;public class Clock {public static long now=1000;public static long millis(){return now;}}')
write('com/nezurstandalone/control/InventoryOwner.java','package com.nezurstandalone.control;public class InventoryOwner {public static String ownerName(){return "temporary item operation";}}')
write('PrestigeAccessTest.java','''import com.nezurstandalone.control.GuiLease;
public class PrestigeAccessTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 enum State {IDLE,PRESTIGE_NAVIGATING}
 State currentState=State.IDLE;long npcRetryAfterNanos,npcAccessMessageAt,npcSpotStartedAt,interactStamp,navRepathAt;
 int npcPosIndex,pauses,headings;boolean prestigeConfirmStarted,npcPresent=true;String lastLog="";
 static class Utils {static boolean spawn=true;static boolean isInSpawn(){return spawn;}}
 static class Gold {boolean known,ready=true;boolean known(){return known;}boolean ready(){return ready;}}
 Gold prestigeGold=new Gold();
 static class PathfinderManager {static boolean free=true;static boolean available(Object o){return free;}static void clear(Object o,boolean silent){}static String ownerName(){return free?"none":"other navigation";}}
 void pauseGameplay(){pauses++;}void resetMenuTracking(){GuiLease.release(this);}
 Object findNpc(boolean prestige){return npcPresent?this:null;}
 void log(String s){lastLog=s;if(s.contains("heading to"))headings++;}
'''+method('reserveNpcAccess')+'\n'+method('startPrestigeProcess')+'''
 public static void main(String[] args){
  PrestigeAccessTest a=new PrestigeAccessTest();Object other=new Object();GuiLease.acquire(other);
  check(a.startPrestigeProcess(),"Blocked request remains handled with prestige priority");
  check(a.currentState==State.IDLE&&a.headings==0,"No false heading or navigation before acquisition");
  check(a.npcRetryAfterNanos==0,"Busy access does not impose minute cooldown");
  check(GuiLease.owns(other),"Foreign GUI lease preserved");
  GuiLease.release(other);check(a.startPrestigeProcess(),"Released GUI immediately permits retry");
  check(a.currentState==State.PRESTIGE_NAVIGATING&&GuiLease.owns(a),"Startup retains GUI lease after menu reset");
  check(a.headings==1,"Heading announced once access secured");
  GuiLease.release(a);GuiLease.operationAllowed=o->false;
  check(!a.reserveNpcAccess(),"Temporary inventory or safety denial waits");
  check(a.currentState==State.PRESTIGE_NAVIGATING&&a.npcRetryAfterNanos==0,"Trip survives policy denial without abort cooldown");
  GuiLease.operationAllowed=o->true;check(a.reserveNpcAccess()&&GuiLease.owns(a),"Trip resumes after safety denial clears");
  GuiLease.release(a);PathfinderManager.free=false;check(!a.reserveNpcAccess(),"Foreign movement respected");
  PathfinderManager.free=true;check(a.reserveNpcAccess(),"Movement release resumes trip");
  GuiLease.release(a);a.currentState=State.IDLE;a.prestigeGold.known=true;a.prestigeGold.ready=false;
  check(!a.startPrestigeProcess(),"Insufficient prestige gold does not reserve NPC");
  check(!GuiLease.owns(a),"Ineligible operation cannot hold GUI");
  System.out.println("Prestige access: "+checks+" production startup/reservation checks passed");
 }
}
''')
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot'))/'bin'
classes=out/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(classes)]+[str(x) for x in src.rglob('*.java')]+[str(root/'src/main/java/com/nezurstandalone/control/GuiLease.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(classes),'PrestigeAccessTest'],check=True)
