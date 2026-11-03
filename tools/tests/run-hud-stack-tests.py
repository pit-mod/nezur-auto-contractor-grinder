"""Test the production runtime adapter and interface, not only the rectangle solver."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=root/'build/hud-stack-tests'
out.mkdir(parents=True,exist_ok=True)
fixtures={
'com/nezurstandalone/utils/PlayerListFrameCache.java':'package com.nezurstandalone.utils;public class PlayerListFrameCache{public static void beginFrame(){}}',
'com/nezurstandalone/module/Module.java': '''package com.nezurstandalone.module;public class Module{public boolean enabled=true;public boolean isToggled(){return enabled;}}''',
'com/nezurstandalone/module/ModuleManager.java': '''package com.nezurstandalone.module;public class ModuleManager{public java.util.List<Module> modules=new java.util.ArrayList<>();public java.util.List<Module> getModules(){return modules;}}''',
'com/nezurstandalone/Nezur.java': '''package com.nezurstandalone;public class Nezur{public static com.nezurstandalone.module.ModuleManager moduleManager=new com.nezurstandalone.module.ModuleManager();}''',
'com/nezurstandalone/gui/hud/HudEditorScreen.java': '''package com.nezurstandalone.gui.hud;public class HudEditorScreen{}''',
'com/nezurstandalone/utils/ScreenScale.java': '''package com.nezurstandalone.utils;public class ScreenScale{public static int w=400,h=240;public static int width(){return w;}public static int height(){return h;}public static net.minecraft.client.gui.ScaledResolution get(){return new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());}}''',
'com/nezurstandalone/utils/HudPositionManager.java': '''package com.nezurstandalone.utils;public class HudPositionManager{public static double getX(String k,double x){return x;}public static double getY(String k,double y){return y;}}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;public class Minecraft{private static final Minecraft ONE=new Minecraft();public Object thePlayer=new Object(),currentScreen;public static Minecraft getMinecraft(){return ONE;}}''',
'net/minecraft/client/gui/ScaledResolution.java': '''package net.minecraft.client.gui;public class ScaledResolution{public ScaledResolution(net.minecraft.client.Minecraft mc){}public int getScaledWidth(){return com.nezurstandalone.utils.ScreenScale.w;}public int getScaledHeight(){return com.nezurstandalone.utils.ScreenScale.h;}}''',
'net/minecraftforge/fml/common/eventhandler/SubscribeEvent.java': '''package net.minecraftforge.fml.common.eventhandler;public @interface SubscribeEvent{}''',
'net/minecraftforge/fml/common/gameevent/TickEvent.java': '''package net.minecraftforge.fml.common.gameevent;public class TickEvent{public enum Phase{START,END}public static class RenderTickEvent{public Phase phase=Phase.START;}}''',
'HudStackTest.java': '''import com.nezurstandalone.Nezur;import com.nezurstandalone.module.DraggableHud;import com.nezurstandalone.utils.*;import net.minecraft.client.Minecraft;
public class HudStackTest {
static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
static class Widget extends com.nezurstandalone.module.Module implements DraggableHud{
 final String key;int x,y,w,h;boolean center,visible=true;
 Widget(String key,int x,int y,int w,int h){this.key=key;this.x=x;this.y=y;this.w=w;this.h=h;}
 public String getHudKey(){return key;}public boolean isHudVisible(){return visible;}
 public double getDefaultHudX(){return x;}public double getDefaultHudY(){return y;}
 public int getHudWidth(){return w;}public int getHudHeight(){return h;}public boolean isHudCenterAnchored(){return center;}
}
public static void main(String[] args){
 HudStackManager manager=new HudStackManager();net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent frame=new net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent();
 Widget normal=new Widget("a-normal",200,50,50,20),center=new Widget("b-center",0,50,50,20);center.center=true;
 Nezur.moduleManager.modules.add(normal);Nezur.moduleManager.modules.add(center);manager.onRenderTick(frame);
 check(center.getRenderY()>=normal.getRenderY()+26,"center-relative and absolute HUDs collide in the same screen space");
 normal.h=70;check(center.getRenderY()==126,"resized predecessor is measured even if its draw callback runs later");
 int n=normal.getRenderY();check(center.getRenderY()>=n+76,"growing module displaces following content");
 normal.h=5;normal.getRenderY();check(center.getRenderY()==61,"shrinking module releases its extra height immediately");
 normal.visible=false;normal.getRenderY();check(center.getRenderY()==50,"hidden module reserves no rectangle");
 center.x=-180;normal.x=200;normal.visible=true;manager.onRenderTick(frame);check(normal.getRenderY()==50&&center.getRenderY()==50,"separate screen columns stay independent");
 Nezur.moduleManager.modules.clear();Widget bottom=new Widget("a-bottom",10,200,60,30),next=new Widget("b-next",10,220,60,15);
 Nezur.moduleManager.modules.add(bottom);Nezur.moduleManager.modules.add(next);manager.onRenderTick(frame);
 check(next.getRenderY()>=bottom.getRenderY()+bottom.h+6,"bottom-edge fitting preserves saved vertical order");
 check(next.getRenderY()>=0&&bottom.getRenderY()+bottom.h<=ScreenScale.height(),"both widgets remain on screen when space exists");
 Widget auxiliary=new Widget("debug",10,200,60,20);HudStackManager.registerAuxiliary(auxiliary);manager.onRenderTick(frame);
 int debug=auxiliary.getRenderY();check(debug>=bottom.getRenderY()+36&&next.getRenderY()>=debug+26,"auxiliary HUDs preserve the same vertical order");
 Nezur.moduleManager.modules.clear();
 Widget upper=new Widget("upper",200,20,10,20),lower=new Widget("lower",309,21,10,20);
 Nezur.moduleManager.modules.add(lower);Nezur.moduleManager.modules.add(upper);manager.onRenderTick(frame);
 check(lower.getRenderY()>=upper.getRenderY()+26,"one-pixel editor-box overlap counts even when live content is narrower");
 upper.h=70;check(lower.getRenderY()>=upper.getRenderY()+76,"dynamic growth keeps upper HUD first across placement boxes");
 Minecraft.getMinecraft().currentScreen=new com.nezurstandalone.gui.hud.HudEditorScreen();check(next.getRenderY()==220,"editor preserves the editable saved anchor");
 Minecraft.getMinecraft().currentScreen=null;Minecraft.getMinecraft().thePlayer=null;manager.onRenderTick(frame);
 System.out.println("HUD stack runtime adapter: "+checks+" checks passed");
}}
'''
}
paths=[]
for name,source in fixtures.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');paths.append(str(p))
paths += [str(root/'src/main/java/com/nezurstandalone'/name) for name in ('module/DraggableHud.java','utils/HudBounds.java','utils/HudStackManager.java','control/HudListLayout.java')]
java=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(out),*paths],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'HudStackTest'],check=True)
