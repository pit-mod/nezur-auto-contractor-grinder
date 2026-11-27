from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/contractor-fresh-pants-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
j=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
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
write(Path('net/minecraft/client/gui/inventory/GuiInventory.java'),'package net.minecraft.client.gui.inventory;public class GuiInventory{public GuiInventory(Object p){}}')
write(Path('net/minecraft/item/ItemStack.java'),'package net.minecraft.item;public class ItemStack{public boolean fresh=true;}')
write(Path('com/nezurstandalone/control/GuiLease.java'),'package com.nezurstandalone.control;public class GuiLease{public static boolean owns(Object o){return true;}public static boolean acquire(Object o){return true;}public static void release(Object o){}}')
write(Path('com/nezurstandalone/control/InventoryOwner.java'),'package com.nezurstandalone.control;public class InventoryOwner{public static boolean acquire(Object o){return true;}}')
write(Path('com/nezurstandalone/input/NativeActionGate.java'),'package com.nezurstandalone.input;public class NativeActionGate{public static boolean manual(){return false;}}')
write(Path('com/nezurstandalone/input/WindowActions.java'),'''package com.nezurstandalone.input;public class WindowActions{
 public static boolean pending,ack;public static int clicks,slot,mode;
 public static boolean pending(Object o){return pending;}
 public static void click(Object o,int window,int s,int button,int m){if(pending)throw new AssertionError("Duplicate click");pending=true;clicks++;slot=s;mode=m;}
 public static void reset(){pending=ack=false;clicks=0;}
}''')
write(Path('FreshPantsTest.java'),'''import com.nezurstandalone.contract.*;import com.nezurstandalone.input.WindowActions;import java.util.*;import net.minecraft.item.ItemStack;
public class FreshPantsTest {
 enum State{CHECKING_SCOREBOARD,CONTRACT_COMPLETE,RESTORING_PERKS,PREPARING_CONTRACT,FINDING_CONTRACTOR}
 State state=State.CHECKING_SCOREBOARD;void move(State next){state=next;}
 boolean restoreFreshPantsAfterNoArmor,freshPantsDispatched,halted;int source=0,armorScreenRecoveries,freshPantsSwapSource=-1,freshPantsSwapHotbar;MC mc=new MC();
 ContractScoreboard.Snapshot scoreboard,selected;
 net.minecraft.client.gui.inventory.GuiInventory armorScreen;
 static class AutoHeal{static boolean isHealing(){return false;}}
 static class Container{int windowId;}
 static class Inventory{int currentItem;ItemStack[] armorInventory=new ItemStack[4], main=new ItemStack[36];ItemStack getStackInSlot(int slot){return main[slot];}Object getItemStack(){return null;}}
 static class Player{Inventory inventory=new Inventory();Container inventoryContainer=new Container(),openContainer=inventoryContainer;boolean isUsingItem(){return false;}}
 static class MC{Player thePlayer=new Player();Object currentScreen;void displayGuiScreen(Object s){currentScreen=s;}}
 boolean isFreshPants(ItemStack item){return item!=null&&item.fresh;}
 int freshPantsSlot(){return source;}void pauseNavigation(){}void log(String s){}
 boolean canStowArmor(){return true;}void stopGrinder(){}
 boolean inventoryActionReady(){if(WindowActions.pending&&!WindowActions.ack)return false;WindowActions.pending=false;return true;}
 void cancelInventoryOperation(){armorScreen=null;mc.currentScreen=null;freshPantsDispatched=false;WindowActions.pending=false;}
 void uncertainAction(String s){halted=true;cancelInventoryOperation();scoreboard=null;}
'''+extract('    private void queueFreshPantsAfterContract()')+'\n'+extract('    private void resumeCompletedEquipment()')+'\n'+extract('    private boolean resumeClosedArmorScreen()')+'\n'+extract('    private boolean ensureNoArmor()')+'\n'+extract('    private boolean finishFreshPantsRestore()')+'''
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static boolean fresh(String name,String...lore){return ContractFreshPants.matches(true,1,false,name,Arrays.asList(lore));}
 static ContractScoreboard.Snapshot board(ContractOffer.Type type){return new ContractScoreboard.Snapshot(true,true,true,type,10,10,5,"");}
 public static void main(String[] args){
  for(String color:new String[]{"Red","Orange","Yellow","Green","Blue"}){
   check(fresh(color+" Pants","Used in the Mystic Well"),"Any fresh color is accepted: "+color);
   check(fresh("§aFresh "+color+" Pants"),"Fresh name supported: "+color);
  }
  check(!fresh("Leather Pants"),"Plain leather pants are not fresh mystics");
  check(!fresh("Blue Pants","Tier: I","Used in the Mystic Well"),"Enchanted mystic tier rejected even without vanilla enchant tag");
  check(!fresh("Fresh Pants","Lives: 5/5"),"Lives reject enchanted mystics");
  check(!ContractFreshPants.matches(true,1,true,"Fresh Pants",Collections.emptyList()),"Vanilla enchanted pants rejected");
  check(!ContractFreshPants.matches(false,1,false,"Fresh Pants",Collections.emptyList()),"Wrong item rejected");
  check(!ContractFreshPants.matches(true,0,false,"Fresh Pants",Collections.emptyList()),"Zero-count phantom item rejected");
  FreshPantsTest c=new FreshPantsTest();c.scoreboard=board(ContractOffer.Type.NO_ARMOR_KILLS);c.queueFreshPantsAfterContract();
  check(c.restoreFreshPantsAfterNoArmor,"No-armor completion queues restoration");
  c=new FreshPantsTest();c.scoreboard=board(ContractOffer.Type.KILL_PLAYERS);c.queueFreshPantsAfterContract();check(!c.restoreFreshPantsAfterNoArmor,"Other contracts do not queue restoration");
  c=new FreshPantsTest();c.selected=board(ContractOffer.Type.NO_ARMOR_KILLS);c.queueFreshPantsAfterContract();check(c.restoreFreshPantsAfterNoArmor,"Selected identity works when scoreboard is missing");
  WindowActions.reset();c=new FreshPantsTest();c.restoreFreshPantsAfterNoArmor=true;c.source=-1;
  check(c.finishFreshPantsRestore()&&!c.restoreFreshPantsAfterNoArmor&&WindowActions.clicks==0,"Missing pants skip immediately without a click");
  c=new FreshPantsTest();c.mc.thePlayer.inventory.armorInventory[1]=new net.minecraft.item.ItemStack();
  check(c.finishFreshPantsRestore()&&WindowActions.clicks==0,"Existing pants are preserved");
  WindowActions.reset();c=new FreshPantsTest();c.source=9;
  Inventory inv=c.mc.thePlayer.inventory;inv.armorInventory[1]=new ItemStack();inv.armorInventory[1].fresh=false;
  ItemStack freshPants=new ItemStack();inv.main[9]=freshPants;
  check(!c.finishFreshPantsRestore()&&!c.finishFreshPantsRestore()&&WindowActions.slot==9&&WindowActions.mode==2,"Stage fresh pants from the main inventory");
  inv.main[1]=freshPants;WindowActions.ack=true;
  check(!c.finishFreshPantsRestore()&&WindowActions.slot==7&&WindowActions.clicks==2,"Swap staged pants directly with current leggings");
  inv.main[1]=inv.armorInventory[1];inv.armorInventory[1]=freshPants;
  check(!c.finishFreshPantsRestore()&&WindowActions.slot==9&&WindowActions.clicks==3,"Return displaced leggings to the original source slot");
  check(c.finishFreshPantsRestore()&&!c.restoreFreshPantsAfterNoArmor,"Finish after the final inventory acknowledgement");
  for(int slot:new int[]{0,8,9,35}){
   WindowActions.reset();c=new FreshPantsTest();c.source=slot;c.restoreFreshPantsAfterNoArmor=true;
   check(!c.finishFreshPantsRestore()&&c.armorScreen!=null&&WindowActions.clicks==0,"Open owned inventory before dispatch");
   check(!c.finishFreshPantsRestore()&&WindowActions.clicks==1&&WindowActions.slot==(slot<9?slot+36:slot)&&WindowActions.mode==1,"Single native quick-move maps the correct inventory slot");
   c.mc.thePlayer.inventory.armorInventory[1]=new net.minecraft.item.ItemStack();
   check(!c.finishFreshPantsRestore()&&WindowActions.clicks==1&&c.restoreFreshPantsAfterNoArmor,"Predicted local equip cannot skip server confirmation");
   WindowActions.ack=true;
   check(c.finishFreshPantsRestore()&&!c.restoreFreshPantsAfterNoArmor&&c.armorScreen==null,"Confirmed equip releases inventory and allows next contract");
  }
  WindowActions.reset();c=new FreshPantsTest();c.finishFreshPantsRestore();c.finishFreshPantsRestore();c.mc.currentScreen=null;
  Object ownedScreen=c.armorScreen;
  check(!c.finishFreshPantsRestore()&&!c.halted&&c.mc.currentScreen==ownedScreen&&WindowActions.clicks==1,"A closed player inventory resumes the identical pending equip without replay");
  check(!c.finishFreshPantsRestore()&&WindowActions.clicks==1,"Reopening cannot substitute for the matching ACK");
  c.mc.thePlayer.inventory.armorInventory[1]=new net.minecraft.item.ItemStack();WindowActions.ack=true;
  check(c.finishFreshPantsRestore()&&!c.halted&&WindowActions.clicks==1,"Pending equip completes after ACK without a duplicate click");
  WindowActions.reset();c=new FreshPantsTest();c.mc.thePlayer.inventory.armorInventory[3]=new net.minecraft.item.ItemStack();
  check(!c.ensureNoArmor()&&c.armorScreen!=null,"Armor preparation opens owned inventory");
  check(!c.ensureNoArmor()&&WindowActions.clicks==1&&WindowActions.slot==5,"Armor preparation dispatches one helmet quick-move");
  c.mc.currentScreen=null;ownedScreen=c.armorScreen;
  check(!c.ensureNoArmor()&&!c.halted&&c.mc.currentScreen==ownedScreen&&WindowActions.clicks==1,"Late acceptance close preserves pending armor action and does not disable Contractor");
  check(!c.ensureNoArmor()&&WindowActions.clicks==1,"Waiting for armor ACK cannot repeat quick-move");
  c.mc.thePlayer.inventory.armorInventory[3]=null;WindowActions.ack=true;
  check(c.ensureNoArmor()&&!c.halted&&c.armorScreen==null&&WindowActions.clicks==1,"Acknowledged removal completes no-armor preparation");
  WindowActions.reset();c=new FreshPantsTest();c.finishFreshPantsRestore();c.finishFreshPantsRestore();c.mc.currentScreen=new Object();
  check(!c.finishFreshPantsRestore()&&c.halted,"Unrelated visible screen still stops uncertain pending equip");
  WindowActions.reset();c=new FreshPantsTest();c.finishFreshPantsRestore();c.finishFreshPantsRestore();c.mc.currentScreen=null;
  c.finishFreshPantsRestore();c.mc.currentScreen=null;
  check(!c.finishFreshPantsRestore()&&c.halted&&WindowActions.clicks==1,"Repeated interference stays bounded without replaying the pending equip");
  WindowActions.reset();c=new FreshPantsTest();c.finishFreshPantsRestore();c.finishFreshPantsRestore();c.mc.currentScreen=null;c.mc.thePlayer.openContainer=new Container();
  check(!c.finishFreshPantsRestore()&&c.halted&&WindowActions.clicks==1,"A different actual container cannot be recovered as the old player inventory");
  for(State interrupted:new State[]{State.CHECKING_SCOREBOARD,State.FINDING_CONTRACTOR,State.PREPARING_CONTRACT}){
   c=new FreshPantsTest();c.scoreboard=board(ContractOffer.Type.NO_ARMOR_KILLS);c.queueFreshPantsAfterContract();
   c.state=interrupted;c.resumeCompletedEquipment();check(c.state==State.CONTRACT_COMPLETE&&c.restoreFreshPantsAfterNoArmor,"Completion equipment takes priority after death/respawn navigation reset");
   c.source=-1;check(c.finishFreshPantsRestore(),"Missing fresh pants skip after respawn");c.state=State.FINDING_CONTRACTOR;c.resumeCompletedEquipment();check(c.state==State.FINDING_CONTRACTOR,"After restoration/skip the next contract is allowed");
  }
  c=new FreshPantsTest();c.restoreFreshPantsAfterNoArmor=true;c.state=State.RESTORING_PERKS;c.resumeCompletedEquipment();check(c.state==State.RESTORING_PERKS,"Pending perk restoration keeps priority before fresh pants");
  System.out.println("Contractor fresh pants: "+checks+" checks passed");
 }
}''')
assert 'queueFreshPantsAfterContract();' in extract('    private void finishContract(')
assert 'if(restoreFreshPantsAfterNoArmor&&!finishFreshPantsRestore())return;' in source
tick=extract('    @SubscribeEvent public void tick(')
assert tick.index('resumeCompletedEquipment();')<tick.index('if(!scoreboard.active && now<sidebarSettleUntil)')<tick.index('switch(state)')
paths=[*src.rglob('*.java'),root/'src/main/java/com/nezurstandalone/contract/ContractFreshPants.java',root/'src/main/java/com/nezurstandalone/contract/ContractOffer.java',root/'src/main/java/com/nezurstandalone/contract/ContractScoreboard.java']
subprocess.run([str(j/'javac.exe'),'-encoding','UTF-8','-d',str(out),*[str(p) for p in paths]],check=True)
subprocess.run([str(j/'java.exe'),'-cp',str(out),'FreshPantsTest'],check=True)
