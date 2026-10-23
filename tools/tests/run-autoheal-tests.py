"""Exercise production healing, slot leases, evidence and native input with a deterministic client."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[2]
out = root / 'build/autoheal-tests'
files = {
    'com/nezurstandalone/control/Clock.java': 'package com.nezurstandalone.control;public interface Clock{long nanos();Clock SYSTEM=()->millis()*1000000L;static long millis(){return Time.now;}class Time{public static long now=10000;} }',
    'com/nezurstandalone/control/ClientSession.java': 'package com.nezurstandalone.control;public class ClientSession{public static long token=1;public static long current(){return token;}}',
    'com/nezurstandalone/control/SessionResets.java': 'package com.nezurstandalone.control;public class SessionResets{public static void register(Object owner,Runnable callback){}}',
    'com/nezurstandalone/control/GrinderDiagnostics.java': 'package com.nezurstandalone.control;public class GrinderDiagnostics{public static String lastEvent;public static void record(String event,String details){lastEvent=event;}}',
    'com/nezurstandalone/module/Category.java': 'package com.nezurstandalone.module;public enum Category{AUTO}',
    'com/nezurstandalone/module/Module.java': '''package com.nezurstandalone.module;import net.minecraft.client.Minecraft;import com.nezurstandalone.settings.Setting;import java.util.*;public class Module{protected final Minecraft mc=Minecraft.getMinecraft();public final List<Setting> settings=new ArrayList<>();private boolean active;public Module(String n,String d,Category c){}public boolean isToggled(){return active;}public void enabled(boolean b){active=b;if(b)onEnable();else onDisable();}protected void onEnable(){}protected void onDisable(){}protected void markDangerous(){}protected void addSettings(Setting...s){settings.addAll(Arrays.asList(s));}}''',
    'com/nezurstandalone/settings/Setting.java': 'package com.nezurstandalone.settings;public class Setting{public final String name;public Setting(String n){name=n;}}',
    'com/nezurstandalone/settings/BooleanSetting.java': 'package com.nezurstandalone.settings;public class BooleanSetting extends Setting{public boolean enabled;public BooleanSetting(String n,boolean b){super(n);enabled=b;}}',
    'com/nezurstandalone/settings/NumberSetting.java': 'package com.nezurstandalone.settings;public class NumberSetting extends Setting{public double value;public NumberSetting(String n,double v,double min,double max,int decimals){super(n);value=v;}}',
    'net/minecraft/entity/Entity.java': 'package net.minecraft.entity;public class Entity{public Object worldObj;public boolean isDead;}',
    'net/minecraft/entity/player/EntityPlayer.java': '''package net.minecraft.entity.player;import net.minecraft.entity.Entity;import net.minecraft.item.ItemStack;import net.minecraft.util.Vec3;public class EntityPlayer extends Entity{public Inventory inventory=new Inventory();public Object inventoryContainer=new Object(),openContainer=inventoryContainer;public boolean using;public float health=5,absorption;public boolean isUsingItem(){return using;}public float getHealth(){return health;}public float getAbsorptionAmount(){return absorption;}public ItemStack getHeldItem(){return inventory.getStackInSlot(inventory.currentItem);}public ItemStack getItemInUse(){return using?getHeldItem():null;}public Vec3 getPositionEyes(float f){return new Vec3();}public static class Inventory{public int currentItem;public ItemStack[] mainInventory=new ItemStack[36];public ItemStack getStackInSlot(int s){return mainInventory[s];}}}''',
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;import net.minecraft.entity.player.EntityPlayer;import net.minecraft.item.ItemStack;import net.minecraft.client.settings.KeyBinding;import net.minecraft.util.MovingObjectPosition;public class Minecraft{static final Minecraft MC=new Minecraft();public static Minecraft getMinecraft(){return MC;}public EntityPlayer thePlayer;public Object theWorld,currentScreen;public Settings gameSettings=new Settings();public Controller playerController=new Controller();public MovingObjectPosition objectMouseOver;public static class Settings{public KeyBinding keyBindAttack=new KeyBinding(-100),keyBindUseItem=new KeyBinding(-99);}public static class Controller{public boolean digging,acceptUse=true;public int calls;public boolean getIsHittingBlock(){return digging;}public void resetBlockRemoving(){digging=false;}public boolean sendUseItem(EntityPlayer p,Object world,ItemStack item){calls++;if(acceptUse&&item.getMaxItemUseDuration()>0)p.using=true;return acceptUse;}}}''',
    'net/minecraft/client/settings/KeyBinding.java': 'package net.minecraft.client.settings;public class KeyBinding{public static boolean use;private int code;public KeyBinding(int c){code=c;}public int getKeyCode(){return code;}public static void setKeyBindState(int key,boolean b){if(key==-99)use=b;}}',
    'net/minecraft/item/Item.java': 'package net.minecraft.item;public class Item{public final int id,duration;public Item(int i,int d){id=i;duration=d;}public static int getIdFromItem(Item i){return i.id;}}',
    'net/minecraft/item/ItemStack.java': '''package net.minecraft.item;public class ItemStack{private Item item;private String name;private int damage;public int stackSize;public ItemStack(Item i,String n,int size,int d){item=i;name=n;stackSize=size;damage=d;}public Item getItem(){return item;}public String getDisplayName(){return name;}public boolean hasDisplayName(){return name!=null;}public int getItemDamage(){return damage;}public int getMaxItemUseDuration(){return item.duration;}public static ItemStack copyItemStack(ItemStack s){return s==null?null:new ItemStack(s.item,s.name,s.stackSize,s.damage);}public static boolean areItemStacksEqual(ItemStack a,ItemStack b){return a==b||a!=null&&b!=null&&a.item==b.item&&a.damage==b.damage&&a.stackSize==b.stackSize&&java.util.Objects.equals(a.name,b.name);}}''',
    'net/minecraft/item/ItemSkull.java': 'package net.minecraft.item;public class ItemSkull extends Item{public ItemSkull(){super(397,0);}}',
    'net/minecraft/item/ItemSoup.java': 'package net.minecraft.item;public class ItemSoup extends Item{public ItemSoup(){super(282,32);}}',
    'net/minecraft/item/ItemFood.java': 'package net.minecraft.item;public class ItemFood extends Item{public ItemFood(int id){super(id,32);}}',
    'net/minecraft/item/ItemBow.java': 'package net.minecraft.item;public class ItemBow extends Item{public ItemBow(){super(261,72000);}}',
    'net/minecraft/item/ItemPotion.java': 'package net.minecraft.item;public class ItemPotion extends Item{public ItemPotion(){super(373,32);}}',
    'net/minecraft/init/Items.java': 'package net.minecraft.init;import net.minecraft.item.Item;public class Items{public static final Item bowl=new Item(281,0);}',
    'net/minecraft/util/Vec3.java': 'package net.minecraft.util;public class Vec3{public double distanceTo(Vec3 v){return 0;}}',
    'net/minecraft/util/BlockPos.java': 'package net.minecraft.util;public class BlockPos{}',
    'net/minecraft/util/MovingObjectPosition.java': 'package net.minecraft.util;import net.minecraft.entity.Entity;public class MovingObjectPosition{public enum MovingObjectType{BLOCK,ENTITY,MISS}public MovingObjectType typeOfHit=MovingObjectType.MISS;public Entity entityHit;public Vec3 hitVec;public BlockPos getBlockPos(){return null;}}',
    'com/nezurstandalone/engine/GrinderEngine.java': 'package com.nezurstandalone.engine;public class GrinderEngine{}',
    'com/nezurstandalone/module/impl/swapping/BulletTimeSwap.java': 'package com.nezurstandalone.module.impl.swapping;public class BulletTimeSwap{}',
    'com/nezurstandalone/module/impl/swapping/MLBSwap.java': 'package com.nezurstandalone.module.impl.swapping;public class MLBSwap{}',
    'com/nezurstandalone/swap/SwapEngine.java': 'package com.nezurstandalone.swap;public class SwapEngine{}',
}
# Reuse only API boundaries, not a substitute healing/input implementation.
for relative in ['org/lwjgl/input/Mouse.java', 'org/lwjgl/input/Keyboard.java']:
    files[relative] = (root / 'tools/tests/native-stubs' / relative).read_text(encoding='utf-8')
for relative in ['net/minecraftforge/fml/common/eventhandler/SubscribeEvent.java', 'net/minecraftforge/fml/common/eventhandler/EventPriority.java', 'net/minecraftforge/fml/common/gameevent/TickEvent.java']:
    files[relative] = (root / 'tools/tests/aura-stubs' / relative).read_text(encoding='utf-8')
for relative, code in files.items():
    path = out / 'src' / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(code, encoding='utf-8')
production = [root / 'src/main/java' / path for path in [
    'com/nezurstandalone/module/impl/player/AutoHeal.java',
    'com/nezurstandalone/control/SlotLease.java', 'com/nezurstandalone/control/InventoryOwner.java',
    'com/nezurstandalone/control/HealTransaction.java', 'com/nezurstandalone/control/ActionBudget.java',
    'com/nezurstandalone/control/CombatPacketAudit.java', 'com/nezurstandalone/input/NativeActionGate.java',
    'com/nezurstandalone/input/GuardedInput.java', 'com/nezurstandalone/input/InventoryEvidence.java',
]]
java = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot')) / 'bin'
if not (java / ('javac.exe' if os.name=='nt' else 'javac')).is_file():
    java = java.parent.parent / 'bin'  # Gradle's Java 8 java.home may point at the JDK's jre.
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
sources = list((out / 'src').rglob('*.java')) + production + [root / 'tools/tests/AutoHealIntegrationTest.java']
subprocess.run([str(java / ('javac.exe' if os.name=='nt' else 'javac')), '-encoding', 'UTF-8', '-d', str(classes)] + [str(p) for p in sources], check=True)
subprocess.run([str(java / ('java.exe' if os.name=='nt' else 'java')), '-cp', str(classes), 'AutoHealIntegrationTest'], check=True)
