package com.nezurstandalone.pathfinder;
import net.minecraft.world.World;
import net.minecraft.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import java.util.*;
import java.util.function.Predicate;
/** Incrementally captured on the client thread. No World/Block/config calls from compute. */
final class SearchSnapshot {
    static final class Cell {
        private static final AxisAlignedBB[] NO_BOXES = new AxisAlignedBB[0];
        final boolean solid, liquid, climb, allowed, hazard;
        final AxisAlignedBB[] boxes;
        final double top;
        private Cell(boolean allowed) {
            solid=liquid=climb=hazard=false;this.allowed=allowed;boxes=NO_BOXES;top=Double.NaN;
        }
        private static final Cell AIR=new Cell(true), EXCLUDED_AIR=new Cell(false);
        Cell(World world, BlockPos p, Predicate<BlockPos> bounds) {
            this(world,p,bounds,world.getBlockState(p));
        }
        private Cell(World world, BlockPos p, Predicate<BlockPos> bounds,IBlockState state) {
            Block b = state.getBlock();
            if (b == net.minecraft.init.Blocks.air) {
                // The overwhelming majority of a search box is sky. An air cell has no
                // collisions and no material, and skipping the collision query here is what
                // lets prepare() sweep long-range boxes in a tick or two instead of the
                // seven-plus seconds that made distant targets look unpathable.
                solid = false; liquid = false; climb = false; hazard = false;
                allowed = bounds == null || bounds.test(p);
                boxes = NO_BOXES; top = Double.NaN;
                return;
            }
            liquid = b.getMaterial().isLiquid(); climb = b instanceof BlockLadder || b instanceof BlockVine;
            hazard = b == net.minecraft.init.Blocks.web || b.getMaterial() == Material.lava;
            allowed = bounds == null || bounds.test(p);
            List<AxisAlignedBB> list = new ArrayList<>();
            b.addCollisionBoxesToList(world, p, state, new AxisAlignedBB(p.getX()-2,p.getY()-2,p.getZ()-2,p.getX()+3,p.getY()+3,p.getZ()+3), list, null);
            boxes = list.toArray(new AxisAlignedBB[0]); solid = boxes.length > 0;
            double t = Double.NaN; for (AxisAlignedBB box: boxes) t = Double.isNaN(t) ? box.maxY : Math.max(t,box.maxY);
            top = t;
        }
    }
    private World source;
    private final com.nezurstandalone.control.CaptureRevision revision;
    boolean valid(){return WorldCapture.valid(revision);}
    private Predicate<BlockPos> bounds;
    private final Cell[] cells;
    private final int sizeY,sizeZ;
    private final BlockPos.MutableBlockPos capturePos=new BlockPos.MutableBlockPos();
    private final int minX,minY,minZ,maxX,maxY,maxZ;
    private int x,y,z;
    SearchSnapshot(World world, BlockPos start, BlockPos goal, Predicate<BlockPos> bounds) {
        source=world;this.bounds=bounds;
        minX=Math.min(start.getX(),goal.getX())-12;maxX=Math.max(start.getX(),goal.getX())+12;
        minZ=Math.min(start.getZ(),goal.getZ())-12;maxZ=Math.max(start.getZ(),goal.getZ())+12;
        // Depth below the lower endpoint used to be 42, sized for the deepest maxFall drop
        // even though drops that deep below the lower endpoint never happen on routes that
        // end at the lower endpoint. Every layer cut here shrinks the whole box, and long
        // spawn-to-camp boxes were brushing the one-million-cell construction limit at
        // roughly 100 blocks out — the search threw before it ever ran.
        minY=Math.max(0,Math.min(start.getY(),goal.getY())-24);maxY=Math.min(255,Math.max(start.getY(),goal.getY())+5);
        if ((long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)>1_000_000L) throw new IllegalArgumentException("Search snapshot exceeds bounded region");
        sizeY=maxY-minY+1;sizeZ=maxZ-minZ+1;
        cells=new Cell[(maxX-minX+1)*sizeY*sizeZ];
        x=minX;y=minY;z=minZ;
        revision=WorldCapture.begin(world,minX,minY-1,minZ,maxX,maxY,maxZ);
    }
    boolean prepare(int budget) {
        if (!revision.valid()) throw new IllegalStateException("Collision capture invalidated by world revision");
        if (source==null) return true;
        if(!valid())throw new IllegalStateException("Collision capture identity changed");
        long deadline=System.nanoTime()+2_000_000L;
        int captured=0;
        while (budget-->0 && x<=maxX) {
            capturePos.set(x,y,z);
            if(source.isBlockLoaded(capturePos)) {
                Cell cell;
                IBlockState state=source.getBlockState(capturePos);
                if(state.getBlock()==net.minecraft.init.Blocks.air) {
                    boolean allowed=bounds==null||bounds.test(new BlockPos(x,y,z));
                    cell=allowed?Cell.AIR:Cell.EXCLUDED_AIR;
                } else cell=new Cell(source,new BlockPos(x,y,z),bounds,state);
                cells[((x-minX)*sizeY+(y-minY))*sizeZ+(z-minZ)]=cell;
            }
            if(++z>maxZ){z=minZ;if(++y>maxY){y=minY;x++;}}
            if((++captured&255)==0 && System.nanoTime()>=deadline)break;
        }
        if(x>maxX){source=null;bounds=null;return true;} return false;
    }
    Cell cell(int x,int y,int z){
        if(x<minX||x>maxX||y<minY||y>maxY||z<minZ||z>maxZ)return null;
        return cells[((x-minX)*sizeY+(y-minY))*sizeZ+(z-minZ)];
    }
    Cell cell(BlockPos p){return cell(p.getX(),p.getY(),p.getZ());}
    long stateKey(int x,int y,int z,int dx,int dz){
        return (((long)(x-minX)*sizeY+(y-minY))*sizeZ+(z-minZ))*9+(dx+1)*3+dz+1;
    }
    boolean loaded(BlockPos p){return cell(p)!=null;}
    boolean solid(BlockPos p){Cell c=cell(p);return c!=null&&c.solid;}
    boolean liquid(BlockPos p){Cell c=cell(p);return c!=null&&c.liquid;}
    boolean climb(BlockPos p){Cell c=cell(p);return c!=null&&c.climb;}
    boolean allowed(BlockPos p){Cell c=cell(p);return c!=null&&c.allowed;}
    double top(BlockPos p){Cell c=cell(p);return c==null?Double.NaN:c.top;}
    java.util.List<AxisAlignedBB> boxes(AxisAlignedBB region){
        java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>();
        for(int x=(int)Math.floor(region.minX);x<Math.ceil(region.maxX);x++)
        for(int y=(int)Math.floor(region.minY)-1;y<Math.ceil(region.maxY);y++)
        for(int z=(int)Math.floor(region.minZ);z<Math.ceil(region.maxZ);z++){
            Cell c=cell(x,y,z);if(c==null)return null;
            if(c.hazard||c.liquid)result.add(new AxisAlignedBB(x,y,z,x+1,y+1,z+1));
            java.util.Collections.addAll(result,c.boxes);
        }return result;
    }
    boolean clear(double x,double y,double z){return segment(x,y,z,x,y,z);}
    boolean segment(Vec3 from,Vec3 to){return segment(from.xCoord,from.yCoord,from.zCoord,to.xCoord,to.yCoord,to.zCoord);}
    private boolean segment(double ax,double ay,double az,double bx,double by,double bz){
        for(int x=(int)Math.floor(Math.min(ax,bx)-.3);x<Math.ceil(Math.max(ax,bx)+.3);x++)
        for(int y=(int)Math.floor(Math.min(ay,by))-1;y<Math.ceil(Math.max(ay,by)+PlayerGeometry.HEIGHT);y++)
        for(int z=(int)Math.floor(Math.min(az,bz)-.3);z<Math.ceil(Math.max(az,bz)+.3);z++) {
            Cell c=cell(x,y,z);if(c==null)return false;
            if((c.hazard||c.liquid) && PlayerGeometry.intersects(ax,ay,az,bx,by,bz,x,y,z,x+1,y+1,z+1))return false;
            for(AxisAlignedBB box:c.boxes)
                if(PlayerGeometry.intersects(ax,ay,az,bx,by,bz,box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ))return false;
        }
        return true;
    }
    /** Straighten only supported, level runs; steps, drops and ladders keep their original nodes. */
    List<Vec3> shorten(List<Vec3> path) {
        if(path.size()<3)return path;
        int size=path.size();
        double[] cost=new double[size];int[] previous=new int[size],levelStart=new int[size];
        Arrays.fill(cost,Double.POSITIVE_INFINITY);cost[0]=0;
        for(int end=1;end<size;end++) {
            if(Thread.currentThread().isInterrupted())return path;
            levelStart[end]=Math.abs(path.get(end).yCoord-path.get(end-1).yCoord)<1e-6?levelStart[end-1]:end;
            previous[end]=end-1;
            cost[end]=cost[end-1]+path.get(end-1).distanceTo(path.get(end));
            for(int from=Math.max(levelStart[end],end-32);from<end-1;from++) {
                double candidate=cost[from]+path.get(from).distanceTo(path.get(end));
                if(candidate+1e-8<cost[end] && supportedSegment(path.get(from),path.get(end))) {
                    cost[end]=candidate;previous[end]=from;
                }
            }
        }
        List<Integer> anchors=new ArrayList<>();
        for(int end=size-1;end>0;end=previous[end])anchors.add(end);
        Collections.reverse(anchors);
        List<Vec3> result=new ArrayList<>(size);result.add(path.get(0));
        int fromIndex=0;
        for(int end:anchors) {
            Vec3 from=path.get(fromIndex),to=path.get(end);
            if(end==fromIndex+1)result.add(to);
            else {
                int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)));
                for(int n=1;n<=steps;n++) {
                    double t=(double)n/steps;
                    result.add(n==steps?to:new Vec3(from.xCoord+(to.xCoord-from.xCoord)*t,from.yCoord,from.zCoord+(to.zCoord-from.zCoord)*t));
                }
            }
            fromIndex=end;
        }
        return result;
    }
    List<Vec3> directPath(Vec3 from,Vec3 to) {
        if(!supportedSegment(from,to))return null;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)));
        List<Vec3> result=new ArrayList<>(steps+1);result.add(from);
        for(int n=1;n<=steps;n++) {
            double t=(double)n/steps;
            result.add(n==steps?to:new Vec3(from.xCoord+(to.xCoord-from.xCoord)*t,from.yCoord,from.zCoord+(to.zCoord-from.zCoord)*t));
        }
        return result;
    }
    boolean supportedSegment(Vec3 a,Vec3 b) {
        if(Math.abs(a.yCoord-b.yCoord)>1e-6 || !segment(a,b))return false;
        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*4));
        for(int n=0;n<=steps;n++) {
            if((n&63)==0 && Thread.currentThread().isInterrupted())return false;
            double t=(double)n/steps,x=a.xCoord+(b.xCoord-a.xCoord)*t,z=a.zCoord+(b.zCoord-a.zCoord)*t,y=a.yCoord;
            int floorY=(int)Math.floor(y-1e-6);
            Cell support=cell((int)Math.floor(x),floorY,(int)Math.floor(z));
            if(support==null||support.hazard||support.liquid)return false;
            boolean grounded=false;
            for(AxisAlignedBB box:support.boxes)if(Math.abs(box.maxY-y)<1e-6
                    &&x>=box.minX&&x<=box.maxX&&z>=box.minZ&&z<=box.maxZ){grounded=true;break;}
            if(!grounded)return false;
            for(int px=(int)Math.floor(x-.3);px<Math.ceil(x+.3);px++)
            for(int pz=(int)Math.floor(z-.3);pz<Math.ceil(z+.3);pz++) {
                Cell c=cell(px,(int)Math.floor(y),pz);
                if(c==null||!c.allowed)return false;
            }
        }
        return true;
    }
}
