package com.nezurstandalone.utils;
import com.nezurstandalone.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.*;
/** Shared, bounded chunk-section index. No chunk loads or retained scan-origin selection. */
public final class NearestEggScan {
    private static final EggLocations INDEX=new EggLocations();
    private static final Map<Long,Scan> scans=new LinkedHashMap<>();
    private static Object world; private static long session;
    private static final class Scan {final Chunk chunk;int section,index;Scan(Chunk c){chunk=c;}}
    private static void identity(){Minecraft mc=Minecraft.getMinecraft();long s=ClientSession.current();if(world!=mc.theWorld||session!=s){world=mc.theWorld;session=s;scans.clear();INDEX.clear();}}
    public static void blockChanged(Object w,int x,int y,int z){identity();if(w==world&&scans.containsKey(EggLocations.key(x>>4,z>>4)))INDEX.update(x,y,z,Minecraft.getMinecraft().theWorld.getBlockState(new BlockPos(x,y,z)).getBlock()==Blocks.dragon_egg);}
    public static void chunkChanged(int x,int z){identity();scans.remove(EggLocations.key(x,z));INDEX.unload(x,z);}
    public static void allChunksChanged(){identity();scans.clear();INDEX.clear();}
    public BlockPos step(int budget){
        identity();Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return null;
        int cx=((int)Math.floor(mc.thePlayer.posX))>>4,cz=((int)Math.floor(mc.thePlayer.posZ))>>4;
        Set<Long> present=new HashSet<>();
        // Bounded discovery: 11x11 loaded chunk columns, and only nonempty sections are scanned.
        for(int x=cx-5;x<=cx+5;x++)for(int z=cz-5;z<=cz+5;z++)if(mc.theWorld.getChunkProvider().chunkExists(x,z)){
            long key=EggLocations.key(x,z);present.add(key);Chunk chunk=mc.theWorld.getChunkFromChunkCoords(x,z);
            Scan old=scans.get(key);if(old==null||old.chunk!=chunk){INDEX.unload(x,z);scans.put(key,new Scan(chunk));}
        }
        Iterator<Map.Entry<Long,Scan>> it=scans.entrySet().iterator();
        while(it.hasNext()){Map.Entry<Long,Scan> e=it.next();if(!present.contains(e.getKey())){INDEX.unload(e.getValue().chunk.xPosition,e.getValue().chunk.zPosition);it.remove();}}
        int remaining=Math.max(0,Math.min(16384,budget));
        for(Scan scan:scans.values()){
            ExtendedBlockStorage[] sections=scan.chunk.getBlockStorageArray();
            while(scan.section<sections.length && remaining>0){
                ExtendedBlockStorage section=sections[scan.section];
                if(section==null||section.isEmpty()){scan.section++;scan.index=0;continue;}
                int n=scan.index++,x=n&15,z=(n>>4)&15,y=(n>>8)&15;remaining--;
                INDEX.update(scan.chunk.xPosition*16+x,scan.section*16+y,scan.chunk.zPosition*16+z,section.get(x,y,z).getBlock()==Blocks.dragon_egg);
                if(scan.index==4096){scan.index=0;scan.section++;}
            }
            if(remaining==0)break;
        }
        EggLocations.Position p=INDEX.nearest(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        if(p==null)return null;BlockPos result=new BlockPos(p.x,p.y,p.z);
        if(!mc.theWorld.isBlockLoaded(result)||mc.theWorld.getBlockState(result).getBlock()!=Blocks.dragon_egg){INDEX.update(p.x,p.y,p.z,false);return null;}
        return result;
    }
}
