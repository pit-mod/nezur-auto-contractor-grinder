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
        Cell(World world, BlockPos p, Predicate<BlockPos> bounds) {
            IBlockState state = world.getBlockState(p); Block b = state.getBlock();
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
    private final Map<BlockPos,Cell> cells = new HashMap<>();
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
        x=minX;y=minY;z=minZ;
        revision=WorldCapture.begin(world,minX,minY-1,minZ,maxX,maxY,maxZ);
    }
    boolean prepare(int budget) {
        if (!revision.valid()) throw new IllegalStateException("Collision capture invalidated by world revision");
        if (source==null) return true;
        if(!valid())throw new IllegalStateException("Collision capture identity changed");
        while (budget-->0 && x<=maxX) {
            BlockPos p=new BlockPos(x,y,z);
            if(source.isBlockLoaded(p)) cells.put(p,new Cell(source,p,bounds));
            if(++z>maxZ){z=minZ;if(++y>maxY){y=minY;x++;}}
        }
        if(x>maxX){source=null;bounds=null;return true;} return false;
    }
    Cell cell(BlockPos p){return cells.get(p);}
    boolean loaded(BlockPos p){return cells.containsKey(p);}
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
            Cell c=cell(new BlockPos(x,y,z));if(c==null)return null;
            if(c.hazard||c.liquid)result.add(new AxisAlignedBB(x,y,z,x+1,y+1,z+1));
            java.util.Collections.addAll(result,c.boxes);
        }return result;
    }
    boolean clear(double x,double y,double z){Vec3 p=new Vec3(x,y,z);return PlayerGeometry.segment(this::boxes,p,p);}
    boolean segment(Vec3 from,Vec3 to){return PlayerGeometry.segment(this::boxes,from,to);}
}
