import os
"""Exercise the real PitMapManager with loaded/unloaded marker chunks and world changes."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
pkg='com.nezurstandalone' if (root/'src/main/java/com/nezurstandalone').is_dir() else 'com.nezur'
prefix=pkg.replace('.','/')
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot'))/'bin'
files={
'net/minecraft/util/BlockPos.java':"package net.minecraft.util;public class BlockPos{final int x,y,z;public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public boolean equals(Object p){return p instanceof BlockPos&&((BlockPos)p).x==x&&((BlockPos)p).y==y&&((BlockPos)p).z==z;}public int hashCode(){return (x*31+y)*31+z;}}",
'net/minecraft/block/Block.java':"package net.minecraft.block;public class Block{public int meta;public Block(int meta){this.meta=meta;}public int getMetaFromState(net.minecraft.world.State s){return meta;}}",
'net/minecraft/init/Blocks.java':"package net.minecraft.init;import net.minecraft.block.Block;public class Blocks{public static final Block nether_brick=new Block(0),double_stone_slab=new Block(8),sandstone=new Block(0),planks=new Block(2),sand=new Block(0),air=new Block(0);}",
'net/minecraft/world/State.java':"package net.minecraft.world;public class State{net.minecraft.block.Block block;State(net.minecraft.block.Block b){block=b;}public net.minecraft.block.Block getBlock(){return block;}}",
'net/minecraft/world/World.java':"package net.minecraft.world;import net.minecraft.util.BlockPos;import net.minecraft.block.Block;public class World{public java.util.Map<BlockPos,Block> blocks=new java.util.HashMap<>();public int reads,probes;public boolean isBlockLoaded(BlockPos p){probes++;return blocks.containsKey(p);}public State getBlockState(BlockPos p){reads++;if(!blocks.containsKey(p))throw new AssertionError(\"unloaded chunk read\");return new State(blocks.get(p));}}",
'net/minecraft/client/Minecraft.java':"package net.minecraft.client;public class Minecraft{static final Minecraft M=new Minecraft();public net.minecraft.world.World theWorld;public Player thePlayer=new Player();public static class Player{public int ticksExisted=1;}public static Minecraft getMinecraft(){return M;}}",
prefix+'/control/Clock.java':"package PKG.control;public class Clock{public static long now=1000;public static long millis(){return now;}}",
'MapCacheTest.java':"""import PKG.utils.PitMapManager;import PKG.utils.PitMapManager.PitMap;import PKG.control.Clock;import net.minecraft.client.Minecraft;import net.minecraft.world.World;import net.minecraft.util.BlockPos;import net.minecraft.init.Blocks;
public class MapCacheTest{
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){Minecraft mc=Minecraft.getMinecraft();
  int[][] markers={{14,85,-13},{18,95,-9},{-7,113,-10},{-87,85,-51},{-2,113,11}};
  net.minecraft.block.Block[] blocks={Blocks.nether_brick,Blocks.double_stone_slab,Blocks.sandstone,Blocks.planks,Blocks.sand};
  PitMap[] maps={PitMap.GENESIS,PitMap.CASTLE,PitMap.ELEMENTS,PitMap.FOUR_SEASONS,PitMap.CORALS};
  for(int i=0;i<maps.length;i++){
   World w=new World();mc.theWorld=w;w.blocks.put(new BlockPos(markers[i][0],markers[i][1],markers[i][2]),blocks[i]);Clock.now+=1000;
   check(PitMapManager.getCurrentMap()==maps[i],"new world detected");
   int reads=w.reads,probes=w.probes;w.blocks.clear();
   for(int n=0;n<100;n++){Clock.now+=5000;check(PitMapManager.getCurrentMap()==maps[i],"same world never loses identified map");}
   PitMapManager.detectMap();check(w.reads==reads&&w.probes==probes,"no marker checks in known world, including explicit detection");
   check(!PitMapManager.getZone(0,80,0).equals("???"),"player lists keep map zones");
  }
  World unknown=new World();mc.theWorld=unknown;Clock.now+=1000;
  check(PitMapManager.getCurrentMap()==PitMap.UNKNOWN,"new empty world cannot reuse previous map");
  check(unknown.reads==0,"unloaded markers never read or loaded");int probes=unknown.probes;
  Clock.now+=100;PitMapManager.getCurrentMap();check(unknown.probes==probes,"unknown retries bounded");
  unknown.blocks.put(new BlockPos(18,95,-9),Blocks.double_stone_slab);Clock.now+=1000;mc.thePlayer.ticksExisted++;
  check(PitMapManager.getCurrentMap()==PitMap.CASTLE,"first chunk arrival resolves unknown world");
  check(PitMapManager.isInCombatArea(39.5,80,0),"shared twenty-block area");
  check(!PitMapManager.isInCombatArea(40.1,80,0),"beyond area");
  check(!PitMapManager.isInCombatArea(0,100,0),"spawn excluded");
  check(!PitMapManager.isInCombatArea(0,55,20),"sewer excluded");
  mc.theWorld=null;check(PitMapManager.getCurrentMap()==PitMap.UNKNOWN,"disconnect clears map");
  mc.theWorld=new World();mc.theWorld.blocks.put(new BlockPos(-2,113,11),Blocks.sand);Clock.now++;
  check(PitMapManager.getCurrentMap()==PitMap.CORALS,"next world detects immediately");
  System.out.println("MapCacheTest PASS checks="+checks);
 }
}"""
}
with tempfile.TemporaryDirectory(prefix='nezur-map-cache-') as temp:
 folder=Path(temp)
 for name,code in files.items():
  f=folder/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(code.replace('PKG',pkg),encoding='utf-8')
 for name in ['utils/PitMapManager.java','engine/PitCombatArea.java']:
  f=folder/prefix/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((root/'src/main/java'/prefix/name).read_bytes())
 out=folder/'classes';out.mkdir()
 argfile=folder/'javac.args';cmd=['-encoding','UTF-8','-d',str(out),*map(str,folder.rglob('*.java'))]
 argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in cmd),encoding='utf-8')
 subprocess.run([str(java/('javac.exe' if os.name=='nt' else 'javac')),'@'+str(argfile)],check=True)
 subprocess.run([str(java/('java.exe' if os.name=='nt' else 'java')),'-cp',str(out),'MapCacheTest'],check=True)
