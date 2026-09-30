package com.nezurstandalone.control;
/** Immutable capture identity plus monotonic invalidation. No World access from workers. */
public final class CaptureRevision {
    private final Object world,player; private final long session,generation;
    private final int minX,minY,minZ,maxX,maxY,maxZ;
    private volatile boolean valid=true;
    public CaptureRevision(Object w,Object p,long s,long g,int x0,int y0,int z0,int x1,int y1,int z1){world=w;player=p;session=s;generation=g;minX=x0;minY=y0;minZ=z0;maxX=x1;maxY=y1;maxZ=z1;}
    public boolean valid(Object w,Object p,long s,long g){return valid&&w==world&&p==player&&s==session&&g==generation;}
    public boolean valid(){return valid;}
    public void cancel(){valid=false;}
    public void changed(Object w,int x0,int y0,int z0,int x1,int y1,int z1){
        if(w==world&&x0<=maxX&&x1>=minX&&y0<=maxY&&y1>=minY&&z0<=maxZ&&z1>=minZ)valid=false;
    }
}
