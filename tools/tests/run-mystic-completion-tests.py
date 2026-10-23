from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/mystic-completion-tests';src=out/'src';src.mkdir(parents=True,exist_ok=True)
def write(p,s):
 p=src/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
s=(root/'src/main/java/com/nezurstandalone/engine/GrinderEngine.java').read_text(encoding='utf-8')
def extract(prefix):
 a=s.index(prefix);i=s.index('{',a)+1;depth=1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[a:i]
write(Path('net/minecraft/item/Item.java'),'package net.minecraft.item;public class Item {int id;public Item(int n){id=n;}public static int getIdFromItem(Item i){return i.id;}}')
write(Path('net/minecraft/item/ItemStack.java'),'''package net.minecraft.item;public class ItemStack {
 final Item item;final int damage;final String name,tag;public int stackSize=1;
 public ItemStack(Item i,int d,String n,String t){item=i;damage=d;name=n;tag=t;}
 public Item getItem(){return item;}public int getItemDamage(){return damage;}public String getDisplayName(){return name;}
 public static boolean areItemStackTagsEqual(ItemStack a,ItemStack b){return a.tag.equals(b.tag);}}
''')
write(Path('com/nezurstandalone/control/GrinderDiagnostics.java'),'package com.nezurstandalone.control;public class GrinderDiagnostics {public static void record(String a,String b){}}')
write(Path('com/nezurstandalone/control/MovementKeys.java'),'package com.nezurstandalone.control;public class MovementKeys {public static boolean released;public static void release(String owner){released=true;}}')
write(Path('MysticCompletionTest.java'),'''import net.minecraft.item.*;import com.nezurstandalone.engine.ClassicMysticPickup;
public class MysticCompletionTest {
 enum State {PICKING_UP_MYSTIC,FIGHTING,IDLE}State currentState=State.PICKING_UP_MYSTIC;
 boolean classic=true,sawMysticItem=true;long mysticMissingSince=1,mysticRepathAt=100,mysticPickupStarted=100;int mysticTargetId=12,mysticInventoryBefore=0;ItemStack mysticPickupStack;
 static final String MOVEMENT_ROTATION_OWNER="movement";
 static class Inventory {ItemStack[] mainInventory=new ItemStack[36];}static class Player {Inventory inventory=new Inventory();}static class MC {Player thePlayer=new Player();Object theWorld=new Object();}MC mc=new MC();Object engineWorld=mc.theWorld,enginePlayer=mc.thePlayer;boolean enabled=true,roniStopped;boolean isToggled(){return enabled;}void stopRoniMovement(){roniStopped=true;}
 static class Utils {static boolean spawn;static boolean isInSpawn(){return spawn;}}
 static class PathfinderManager {static boolean cleared;static void clear(Object o,boolean b){cleared=true;}}
 static class RotationManager {static final RotationManager I=new RotationManager();boolean cleared;static RotationManager getInstance(){return I;}void clearTarget(String s){cleared=true;}}
 boolean classicRuntime(){return classic;}static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
'''+extract('    private int countMatchingMystics(')+'\n'+extract('    private boolean completeMysticPickupOnInventory()')+'\n'+extract('    private void finishMysticPickup()')+'''
 public static void main(String[] args){
 check(ClassicMysticPickup.named("§aFresh Green Pants"),"Recognizes captured fresh label");
 check(ClassicMysticPickup.named("§bMystic Bow"),"Recognizes captured bow label");
 check(!ClassicMysticPickup.named("§7Iron §fBoots"),"Cannot replace collected fresh pants with ordinary iron boots");
 check(!ClassicMysticPickup.named("Bow"),"Ordinary bows excluded on Classic");
 MysticCompletionTest g=new MysticCompletionTest();Item item=new Item(300);
 ItemStack ground=new ItemStack(item,0,"§aFresh Green Pants","ground-side-id");
 g.mc.thePlayer.inventory.mainInventory[20]=new ItemStack(item,0,"§aFresh Green Pants","inventory-side-id");
 check(g.countMatchingMystics(ground)==1,"Production inventory count accepts server NBT change after collection");
 g.mc.thePlayer.inventory.mainInventory[21]=new ItemStack(item,0,"§cFresh Red Pants","inventory-side-id");
 check(g.countMatchingMystics(ground)==1,"Different pants color does not fake collection");
 g.classic=false;check(g.countMatchingMystics(ground)==0,"Hypixel keeps strict item-NBT matching");g.classic=true;
 g.mysticPickupStack=ground;
 check(g.completeMysticPickupOnInventory(),"Server inventory update finishes pickup without waiting for the macro tick");
 check(g.currentState==State.FIGHTING,"Collected Classic item immediately returns to fighting");
 check(g.mysticRepathAt==0&&g.mysticPickupStarted==0,"Clears direct movement and any pending pickup repath deadline");
 check(!g.completeMysticPickupOnInventory(),"Duplicate inventory callbacks cannot clear a new fighting route");
 check(g.mysticTargetId==-1&&g.mysticPickupStack==null&&!g.sawMysticItem,"No stale drop target remains");
 check(PathfinderManager.cleared&&RotationManager.I.cleared&&com.nezurstandalone.control.MovementKeys.released,"Releases route, downward aim and movement");
 g.classic=false;g.currentState=State.PICKING_UP_MYSTIC;g.mysticPickupStack=ground;
 check(!g.completeMysticPickupOnInventory(),"Hypixel does not use new server-inventory cancellation");
 g.classic=true;g.engineWorld=new Object();check(!g.completeMysticPickupOnInventory(),"Stale world callback cannot clear current navigation");g.engineWorld=g.mc.theWorld;
 g.enabled=false;check(!g.completeMysticPickupOnInventory(),"Disabled macro cannot clear navigation");g.enabled=true;
 g.classic=false;g.finishMysticPickup();check(g.currentState==State.IDLE,"Hypixel completion behavior unchanged");
 g.classic=true;Utils.spawn=true;g.finishMysticPickup();check(g.currentState==State.IDLE,"Spawn cannot enter fighting from pickup cleanup");
 System.out.println("Mystic completion: "+checks+" production identity/cleanup and host-isolation checks passed");
 }
}
''')
j=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin');classes=out/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(j/'javac.exe'),'-encoding','UTF-8','-d',str(classes)]+[str(p) for p in src.rglob('*.java')]+[str(root/'src/main/java/com/nezurstandalone/engine/ClassicMysticPickup.java')],check=True)
subprocess.run([str(j/'java.exe'),'-cp',str(classes),'MysticCompletionTest'],check=True)
