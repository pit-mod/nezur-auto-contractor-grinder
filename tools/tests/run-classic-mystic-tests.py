from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/classic-mystic-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
def write(p,s):
 p=src/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
s=(root/'src/main/java/com/nezurstandalone/engine/GrinderEngine.java').read_text(encoding='utf-8');a=s.index('    private void recordMysticDrop(');b=s.index('{',a)+1;depth=1
while depth:
 if s[b]=='{':depth+=1
 elif s[b]=='}':depth-=1
 b+=1
method=s[a:b]
write(Path('com/nezurstandalone/control/Clock.java'),'package com.nezurstandalone.control;public class Clock {public static long now=1000;public static long millis(){return now;}}')
write(Path('com/nezurstandalone/control/GrinderDiagnostics.java'),'package com.nezurstandalone.control;public class GrinderDiagnostics {public static void record(String a,String b){}}')
write(Path('ClassicMysticTest.java'),'''public class ClassicMysticTest {
 enum State {FIGHTING,PRESTIGE_NAVIGATING,PICKING_UP_MYSTIC} State currentState=State.FIGHTING;
 static class Player {double posX=1,posY=82,posZ=2;}static class MC {Object theWorld=new Object();Player thePlayer=new Player();}MC mc=new MC();
 static class Setting {boolean isEnabled(){return true;}}Setting mysticPickup=new Setting();
 long lastMysticCountMs,lastMysticChatTime,mysticRepathAt,lastMysticTraceTime,mysticMissingSince;int sessionMystics,mysticTargetId;
 boolean mysticPickupPending,sawMysticItem;Object mysticPendingWorld,mysticPickupStack;double mysticDropX,mysticDropY,mysticDropZ;
 boolean isBusyFlow(){return currentState==State.PRESTIGE_NAVIGATING||currentState==State.PICKING_UP_MYSTIC;}void log(String s){}
 static void check(boolean b){if(!b)throw new AssertionError();}
'''+method+'''
 public static void main(String[] args){ClassicMysticTest a=new ClassicMysticTest();a.currentState=State.PRESTIGE_NAVIGATING;
 a.recordMysticDrop("MYSTIC ITEM! dropped from killing [101] Player!",true,1);
 check(a.sessionMystics==1&&a.mysticPickupPending&&a.currentState==State.PRESTIGE_NAVIGATING);
 check(a.mysticPendingWorld==a.mc.theWorld&&a.mysticDropX==1&&a.mysticDropZ==2);
 a.recordMysticDrop("duplicate",true,1);check(a.sessionMystics==1);
 ClassicMysticTest h=new ClassicMysticTest();h.currentState=State.PRESTIGE_NAVIGATING;h.recordMysticDrop("legacy",false,0);
 check(h.sessionMystics==1&&!h.mysticPickupPending&&h.currentState==State.PRESTIGE_NAVIGATING&&h.lastMysticChatTime==0&&h.mysticDropX==0);
 com.nezurstandalone.control.Clock.now+=1000;h.currentState=State.FIGHTING;h.recordMysticDrop("legacy",false,0);
 check(!h.mysticPickupPending&&h.currentState==State.PICKING_UP_MYSTIC);
 System.out.println("Classic mystic queue: 5 checks passed; legacy Hypixel dispatch retained");}
}''')
j=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin');classes=out/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(j/'javac.exe'),'-encoding','UTF-8','-d',str(classes)]+[str(p) for p in src.rglob('*.java')],check=True)
subprocess.run([str(j/'java.exe'),'-cp',str(classes),'ClassicMysticTest'],check=True)
