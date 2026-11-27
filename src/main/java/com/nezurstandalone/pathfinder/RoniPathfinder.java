package com.nezurstandalone.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.Predicate;

/** Roni's route search with Nezur capture, publication, collision checks and walker. */
public final class RoniPathfinder extends AStarPathfinder {
    private final BlockPos start, goal;
    private final int limit, jump;
    private final double maxFall;
    private boolean partial=true;

    public RoniPathfinder(World world,BlockPos start,BlockPos goal,int iterations,boolean holes,Predicate<BlockPos> bounds){
        super(world,start,goal,iterations,holes,bounds);
        this.start=start;this.goal=goal;limit=Math.min(10000,Math.max(1,iterations));
        net.minecraft.client.entity.EntityPlayerSP player=Minecraft.getMinecraft().thePlayer;
        jump=player!=null&&player.isPotionActive(Potion.jump)?Math.min(5,player.getActivePotionEffect(Potion.jump).getAmplifier()+1):1;
        maxFall=Math.min(10,PathfinderConfig.maxFall.value);
    }

    @Override public boolean isPartial(){return partial;}

    @Override public List<Vec3> compute(){
        SearchSnapshot snapshot=collisionSnapshot();
        if(!snapshot.prepare(0))throw new IllegalStateException("Snapshot is not ready");
        Terrain terrain=new Terrain(snapshot,maxFall);
        List<Vec3> direct=snapshot.directPath(terrain.point(start),terrain.point(goal));
        if(direct!=null){partial=false;return direct;}
        RoniRouteSearch search=new RoniRouteSearch(terrain,jump);
        List<BlockPos> blocks=search.findPath(start,goal,limit);
        List<Vec3> nodes=new ArrayList<>();
        for(BlockPos block:blocks)nodes.add(terrain.point(block));
        partial=blocks.isEmpty()||blocks.get(blocks.size()-1).distanceSq(goal)>1.5;
        return snapshot.shorten(nodes);
    }

    private static final class Terrain implements RoniRouteSearch.Terrain {
        private final SearchSnapshot map;
        private final double maxFall;
        Terrain(SearchSnapshot map,double maxFall){this.map=map;this.maxFall=maxFall;}
        private double height(BlockPos p){
            double below=map.top(p.down()),here=map.top(p);
            if(Double.isFinite(here)&&here>p.getY()&&here<p.getY()+1)return here;
            return Double.isFinite(below)?below:p.getY();
        }
        Vec3 point(BlockPos p){return new Vec3(p.getX()+.5,height(p),p.getZ()+.5);}
        public boolean isWalkable(BlockPos p){
            SearchSnapshot.Cell cell=map.cell(p),support=map.cell(p.down());
            return cell!=null&&support!=null&&cell.allowed&&!cell.hazard&&!cell.liquid
                    &&!support.hazard&&!support.liquid&&(support.solid||cell.solid&&height(p)<p.getY()+1)
                    &&isSpaceFree(p);
        }
        public boolean isAir(BlockPos p){SearchSnapshot.Cell c=map.cell(p);return c!=null&&!c.solid&&!c.liquid&&!c.hazard;}
        public boolean isSpaceFree(BlockPos p){return map.allowed(p)&&map.clear(p.getX()+.5,height(p),p.getZ()+.5);}
        public boolean isSolid(BlockPos p){return !map.loaded(p)||map.solid(p);}
        public boolean isPassable(BlockPos p){return isAir(p);}
        public boolean canTraverse(BlockPos from,BlockPos to){
            if(!isWalkable(to))return false;
            Vec3 a=point(from),b=point(to);
            if(a.yCoord-b.yCoord>maxFall)return false;
            int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
            if(dx!=0&&dz!=0&&(!map.clear(b.xCoord,a.yCoord,a.zCoord)||!map.clear(a.xCoord,a.yCoord,b.zCoord)))return false;
            if(b.yCoord>a.yCoord+.01){
                Vec3 up=new Vec3(a.xCoord,b.yCoord+.25,a.zCoord),over=new Vec3(b.xCoord,b.yCoord+.25,b.zCoord);
                return map.segment(a,up)&&map.segment(up,over)&&map.segment(over,b);
            }
            Vec3 over=new Vec3(b.xCoord,a.yCoord,b.zCoord);
            return map.segment(a,over)&&map.segment(over,b);
        }
    }
}
