package com.nezurstandalone.utils;
import com.nezurstandalone.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.*;
/** Shared tick-budgeted index. Block updates are visible even before a chunk's first scan. */
public final class NearestEggScan {
    private static final EggLocations INDEX=new EggLocations();
    private static final Map<Long,Scan> scans=new LinkedHashMap<>();
    private static Object world,player;
    private static long session;
    private static int scannedTick=Integer.MIN_VALUE;
    private static final class Scan {
        final Chunk chunk;
        final int[] order;
        int section,index;
        Scan(Chunk c,int nearY){
            chunk=c;order=new int[c.getBlockStorageArray().length];
            int center=Math.max(0,Math.min(order.length-1,nearY)),n=0;
            for(int r=0;n<order.length;r++){
                if(center+r<order.length)order[n++]=center+r;
                if(r>0&&center-r>=0)order[n++]=center-r;
            }
        }
    }
    private static void identity(){
        Minecraft mc=Minecraft.getMinecraft();long s=ClientSession.current();
        if(world!=mc.theWorld||session!=s){world=mc.theWorld;session=s;scans.clear();INDEX.clear();scannedTick=Integer.MIN_VALUE;}
        if(player!=mc.thePlayer){player=mc.thePlayer;scannedTick=Integer.MIN_VALUE;}
    }
    public static void blockChanged(Object w,int x,int y,int z){
        identity();if(w!=world||world==null)return;
        BlockPos pos=new BlockPos(x,y,z);
        DragonEggTracker.blockChanged(w,pos);
        INDEX.update(x,y,z,Minecraft.getMinecraft().theWorld.getBlockState(pos).getBlock()==Blocks.dragon_egg);
    }
    public static void chunkChanged(int x,int z){identity();scans.remove(EggLocations.key(x,z));INDEX.unload(x,z);scannedTick=Integer.MIN_VALUE;}
    public static void allChunksChanged(){identity();scans.clear();INDEX.clear();scannedTick=Integer.MIN_VALUE;}
    public BlockPos step(int budget){
        identity();Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return null;
        if(scannedTick!=mc.thePlayer.ticksExisted){
            scannedTick=mc.thePlayer.ticksExisted;
            int px=(int)Math.floor(mc.thePlayer.posX),py=(int)Math.floor(mc.thePlayer.posY),pz=(int)Math.floor(mc.thePlayer.posZ);
            int cx=px>>4,cz=pz>>4;
            Set<Long> present=new HashSet<>();
            for(int x=cx-5;x<=cx+5;x++)for(int z=cz-5;z<=cz+5;z++)if(mc.theWorld.getChunkProvider().chunkExists(x,z)){
                long key=EggLocations.key(x,z);present.add(key);Chunk chunk=mc.theWorld.getChunkFromChunkCoords(x,z);
                Scan old=scans.get(key);
                if(old==null||old.chunk!=chunk){
                    // Do not discard block events received before the first discovery pass.
                    if(old!=null)INDEX.unload(x,z);
                    scans.put(key,new Scan(chunk,py>>4));
                }
            }
            Iterator<Map.Entry<Long,Scan>> it=scans.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry<Long,Scan> e=it.next();if(!present.contains(e.getKey())){
                    INDEX.unload(e.getValue().chunk.xPosition,e.getValue().chunk.zPosition);it.remove();
                }
            }
            // Nearby fallback every tick, shared by all consumers. Reuse one position for air.
            BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
            for(int x=px-8;x<=px+8;x++)for(int z=pz-8;z<=pz+8;z++)for(int y=Math.max(0,py-6);y<=Math.min(255,py+6);y++){
                at.set(x,y,z);
                if(mc.theWorld.isBlockLoaded(at)&&mc.theWorld.getBlockState(at).getBlock()==Blocks.dragon_egg)INDEX.update(x,y,z,true);
            }
            List<Scan> ordered=new ArrayList<>(scans.values());
            ordered.sort(Comparator.comparingInt(s -> Math.abs(s.chunk.xPosition-cx)+Math.abs(s.chunk.zPosition-cz)));
            int remaining=Math.max(0,Math.min(16384,budget));
            for(Scan scan:ordered){
                ExtendedBlockStorage[] sections=scan.chunk.getBlockStorageArray();
                while(scan.section<scan.order.length&&remaining>0){
                    int sectionY=scan.order[scan.section];ExtendedBlockStorage section=sections[sectionY];
                    if(section==null||section.isEmpty()){scan.section++;scan.index=0;continue;}
                    int n=scan.index++,x=n&15,z=(n>>4)&15,y=(n>>8)&15;remaining--;
                    if(section.get(x,y,z).getBlock()==Blocks.dragon_egg)
                        INDEX.update(scan.chunk.xPosition*16+x,sectionY*16+y,scan.chunk.zPosition*16+z,true);
                    if(scan.index==4096){scan.index=0;scan.section++;}
                }
                if(remaining==0)break;
            }
        }
        return nearest(Double.POSITIVE_INFINITY,false);
    }
    public BlockPos nearestReachable(double reach){identity();return nearest(reach,true);}
    public BlockPos nearestReachable(double reach,java.util.function.Predicate<BlockPos> allowed){identity();return nearest(reach,true,allowed);}
    private BlockPos nearest(double reach,boolean eyes){
        return nearest(reach,eyes,null);
    }
    private BlockPos nearest(double reach,boolean eyes,java.util.function.Predicate<BlockPos> allowed){
        Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return null;
        Vec3 origin=eyes?mc.thePlayer.getPositionEyes(1):new Vec3(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        EggLocations.Position p=INDEX.nearest(origin.xCoord,origin.yCoord-.5,origin.zCoord,at->{
            double dx=at.x+.5-origin.xCoord,dy=at.y+.5-origin.yCoord,dz=at.z+.5-origin.zCoord;
            BlockPos pos=new BlockPos(at.x,at.y,at.z);
            return dx*dx+dy*dy+dz*dz<=reach*reach&&DragonEggTracker.usable(pos)&&(allowed==null||allowed.test(pos));
        });
        return p==null?null:new BlockPos(p.x,p.y,p.z);
    }
}
