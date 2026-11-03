package com.nezurstandalone.engine;
/** Require settled world evidence and sustained low population before leaving a lobby. */
public final class LobbyPopulationGate {
 private Object world,player;private long joinedAt,lowAt=-1;
 public void reset(){world=player=null;lowAt=-1;}
 public boolean low(Object w,Object p,long now,int ticks,boolean known,int count,int minimum,long delay){
  if(w==null||p==null){reset();return false;}
  if(world!=w||player!=p){world=w;player=p;joinedAt=now;lowAt=-1;return false;}
  if(now-joinedAt<1500||ticks<30||!known||minimum<=0||count>=minimum){lowAt=-1;return false;}
  if(lowAt<0||now<lowAt){lowAt=now;return false;}
  return now-lowAt>=Math.max(1000,delay);
 }
}
