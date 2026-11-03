"""Exercise the real PitMapManager with loaded/unloaded marker chunks and world changes."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
pkg='com.nezurstandalone' if root.name=='auto contractor and grinder' else 'com.nezurstandalone'
prefix=pkg.replace('.','/')
java=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
files={
'net/minecraft/util/BlockPos.java':"package net.minecraft.util;public class BlockPos{final int x,y,z;public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}public BlockPos(double x,int y,double z){this((int)Math.floor(x),y,(int)Math.floor(z));}public BlockPos up(){return up(1);}public BlockPos up(int n){return new BlockPos(x,y+n,z);}public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public boolean equals(Object p){return p instanceof BlockPos&&((BlockPos)p).x==x&&((BlockPos)p).y==y&&((BlockPos)p).z==z;}public int hashCode(){return (x*31+y)*31+z;}}",
'net/minecraft/block/Block.java':"package net.minecraft.block;public class Block{public int meta;public Block(int meta){this.meta=meta;}public int getMetaFromState(net.minecraft.world.State s){return meta;}public net.minecraft.block.material.Material getMaterial(){return new net.minecraft.block.material.Material(this!=net.minecraft.init.Blocks.air);}}",
'net/minecraft/block/material/Material.java':"package net.minecraft.block.material;public class Material{boolean solid;public Material(boolean s){solid=s;}public boolean isSolid(){return solid;}}",
'net/minecraft/init/Blocks.java':"package net.minecraft.init;import net.minecraft.block.Block;public class Blocks{public static final Block nether_brick=new Block(0),double_stone_slab=new Block(8),sandstone=new Block(0),planks=new Block(2),sand=new Block(0),enchanting_table=new Block(0),ender_chest=new Block(0),air=new Block(0);}",
'net/minecraft/world/State.java':"package net.minecraft.world;public class State{net.minecraft.block.Block block;State(net.minecraft.block.Block b){block=b;}public net.minecraft.block.Block getBlock(){return block;}}",
'net/minecraft/world/World.java':"package net.minecraft.world;import net.minecraft.util.BlockPos;import net.minecraft.block.Block;public class World{public java.util.Map<BlockPos,Block> blocks=new java.util.HashMap<>();public int reads,probes;public boolean columnLoaded;public boolean isBlockLoaded(BlockPos p){probes++;return columnLoaded||blocks.containsKey(p);}public State getBlockState(BlockPos p){reads++;if(!columnLoaded&&!blocks.containsKey(p))throw new AssertionError(\"unloaded chunk read\");return new State(blocks.containsKey(p)?blocks.get(p):net.minecraft.init.Blocks.air);}public boolean isAirBlock(BlockPos p){return getBlockState(p).getBlock()==net.minecraft.init.Blocks.air;}}",
'net/minecraft/client/Minecraft.java':"package net.minecraft.client;public class Minecraft{static final Minecraft M=new Minecraft();public net.minecraft.world.World theWorld;public Player thePlayer=new Player();public static class Player{public int ticksExisted;public double posX,posY,posZ;}public static Minecraft getMinecraft(){return M;}}",
prefix+'/control/Clock.java':"package PKG.control;public class Clock{public static long now=1000;public static long millis(){return now;}}",
prefix+'/utils/map/MapLocation.java':(root/'src/main/java'/prefix/'utils/map/MapLocation.java').read_text(encoding='utf-8'),
prefix+'/utils/map/LocationBox.java':(root/'src/main/java'/prefix/'utils/map/LocationBox.java').read_text(encoding='utf-8'),
'net/minecraft/util/EnumChatFormatting.java':"package net.minecraft.util;public enum EnumChatFormatting{DARK_GREEN,AQUA,YELLOW,DARK_AQUA,RED,GOLD,GREEN,GRAY,DARK_RED,DARK_PURPLE,LIGHT_PURPLE,WHITE,BOLD;public String toString(){return \"\\u00a7a\";}}",
'MapCacheTest.java':(root/'tools/tests/RoniMapTest.java').read_text(encoding='utf-8').replace('com.nezurstandalone',pkg)

}
def extract(source,prefix):
 start=source.index(prefix);end=source.index('{',start)+1;depth=1
 while depth:
  if source[end]=='{':depth+=1
  elif source[end]=='}':depth-=1
  end+=1
 return source[start:end]
engine=(root/'src/main/java'/prefix/'engine/GrinderEngine.java').read_text(encoding='utf-8')
contractor=(root/'src/main/java'/prefix/'module/impl/player/AutoContractor.java').read_text(encoding='utf-8')
files['MapNavigationTest.java']=(root/'tools/tests/MapNavigationTest.java').read_text(encoding='utf-8').replace('// GRINDER_METHODS',"\n".join(extract(engine,x) for x in ['    public void navigateContractMid(', '    private double[] centerSpot(', '    private double clampGauss(', '    private double distanceFromMid('])).replace('// CONTRACTOR_METHOD',extract(contractor,'    private boolean inMainSpawn(')).replace('com.nezurstandalone',pkg)
with tempfile.TemporaryDirectory(prefix='nezur-map-cache-') as temp:
 folder=Path(temp)
 for name,code in files.items():
  f=folder/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(code.replace('PKG',pkg),encoding='utf-8')
 for name in ['utils/PitMapManager.java','engine/PitCombatArea.java']:
  f=folder/prefix/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((root/'src/main/java'/prefix/name).read_bytes())
 out=folder/'classes';out.mkdir()
 argfile=folder/'javac.args';cmd=['-encoding','UTF-8','-d',str(out),*map(str,folder.rglob('*.java'))]
 argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in cmd),encoding='utf-8')
 subprocess.run([str(java/'javac.exe'),'@'+str(argfile)],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(out),'MapCacheTest'],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(out),'MapNavigationTest'],check=True)
