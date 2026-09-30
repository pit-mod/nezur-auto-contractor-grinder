package com.nezurstandalone.pathfinder;
import net.minecraft.util.*;
import java.util.*;
/** Exact swept AABB for every linear trajectory segment, rather than cell-center samples. */
public final class PlayerGeometry {
    public static final double WIDTH=.6,HEIGHT=1.8;
    public interface Space { List<AxisAlignedBB> boxes(AxisAlignedBB region); }
    public static AxisAlignedBB box(double x,double y,double z){return new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+HEIGHT,z+.3);}
    public static boolean segment(Space space,Vec3 from,Vec3 to){
        AxisAlignedBB region=box(from.xCoord,from.yCoord,from.zCoord).union(box(to.xCoord,to.yCoord,to.zCoord));
        List<AxisAlignedBB> boxes=space.boxes(region);if(boxes==null)return false;
        for(AxisAlignedBB obstacle:boxes)if(intersects(from,to,obstacle))return false;return true;
    }
    public static boolean trajectory(Space space,List<Vec3> points){if(points.isEmpty())return false;for(int i=0;i<points.size();i++)if(!segment(space,points.get(Math.max(0,i-1)),points.get(i)))return false;return true;}
    private static boolean intersects(Vec3 a,Vec3 b,AxisAlignedBB obstacle){
        double e=1e-7;double[] min={obstacle.minX-.3+e,obstacle.minY-HEIGHT+e,obstacle.minZ-.3+e};
        double[] max={obstacle.maxX+.3-e,obstacle.maxY-e,obstacle.maxZ+.3-e};
        double[] from={a.xCoord,a.yCoord,a.zCoord},delta={b.xCoord-a.xCoord,b.yCoord-a.yCoord,b.zCoord-a.zCoord};double enter=0,leave=1;
        for(int i=0;i<3;i++){if(Math.abs(delta[i])<1e-12){if(from[i]<min[i]||from[i]>max[i])return false;}
            else {double first=(min[i]-from[i])/delta[i],last=(max[i]-from[i])/delta[i];if(first>last){double t=first;first=last;last=t;}enter=Math.max(enter,first);leave=Math.min(leave,last);if(enter>leave)return false;}}
        return true;
    }
    /** Main-thread adapter: the same native collision boxes as the immutable capture. */
    public static Space world(final net.minecraft.world.World world){return region->{
        if(world==null)return null;List<AxisAlignedBB> boxes=new ArrayList<>();int count=0;
        for(int x=(int)Math.floor(region.minX);x<Math.ceil(region.maxX);x++)
        for(int y=(int)Math.floor(region.minY)-1;y<Math.ceil(region.maxY);y++)
        for(int z=(int)Math.floor(region.minZ);z<Math.ceil(region.maxZ);z++){
            if(++count>4096)return null;BlockPos p=new BlockPos(x,y,z);if(!world.isBlockLoaded(p))return null;
            SearchSnapshot.Cell cell=new SearchSnapshot.Cell(world,p,null);
            if(cell.hazard||cell.liquid)boxes.add(new AxisAlignedBB(x,y,z,x+1,y+1,z+1));
            Collections.addAll(boxes,cell.boxes);
        }return boxes;};}
}
