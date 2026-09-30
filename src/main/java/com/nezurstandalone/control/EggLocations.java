package com.nezurstandalone.control;
import java.util.*;
/** Chunk-owned locations; selection never retains a scan-origin distance. */
public final class EggLocations {
    private final Map<Long,Set<Position>> chunks=new HashMap<>();
    public static final class Position {
        public final int x,y,z;
        public Position(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public boolean equals(Object p){return p instanceof Position&&((Position)p).x==x&&((Position)p).y==y&&((Position)p).z==z;}
        public int hashCode(){return (x*31+y)*31+z;}
    }
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public void update(int x,int y,int z,boolean egg){
        long key=key(x>>4,z>>4);Set<Position> positions=chunks.get(key);Position p=new Position(x,y,z);
        if(egg){if(positions==null){positions=new HashSet<>();chunks.put(key,positions);}positions.add(p);}
        else if(positions!=null)positions.remove(p);
    }
    public void unload(int x,int z){chunks.remove(key(x,z));}
    public void clear(){chunks.clear();}
    public Position nearest(double x,double y,double z){
        Position best=null;double distance=Double.POSITIVE_INFINITY;
        for(Set<Position> ps:chunks.values())for(Position p:ps){double dx=p.x+.5-x,dy=p.y-y,dz=p.z+.5-z;double d=dx*dx+dy*dy+dz*dz;
            if(d<distance||(d==distance&&(best==null||p.x<best.x||(p.x==best.x&&(p.y<best.y||(p.y==best.y&&p.z<best.z)))))){best=p;distance=d;}}
        return best;
    }
}
