package com.nezurstandalone.control;
import java.util.*;
/** Compact list anchors with a common saved top-to-bottom order for every HUD. */
public final class HudListLayout {
    public static final class Box {
        public final String key;public final int x,y,width,height;public final boolean list,visible;
        public Box(String key,int x,int y,int width,int height,boolean list,boolean visible){this.key=key;this.x=x;this.y=y;this.width=Math.max(1,width);this.height=Math.max(0,height);this.list=list;this.visible=visible;}
    }
    private static final int GAP=5;
    public static Map<String,Integer> arrange(List<Box> boxes,int screenHeight){
        Map<String,Integer> preferred=new HashMap<>(),positions=new HashMap<>();
        Map<Integer,List<Box>> columns=new HashMap<>();
        List<Box> ordered=new ArrayList<>();
        for(Box box:boxes){
            if(box.list)columns.computeIfAbsent(Math.floorDiv(box.x,80),k->new ArrayList<>()).add(box);
            if(box.visible&&box.height>0)ordered.add(box);
        }
        Comparator<Box> order=Comparator.comparingInt((Box b)->b.y).thenComparing(b->b.key);
        // Hidden list headers still provide an anchor, without reserving empty rows.
        for(List<Box> column:columns.values()){
            column.sort(order);int y=Math.max(0,column.get(0).y);
            for(Box box:column)if(box.visible&&box.height>0){preferred.put(box.key,y);y+=box.height+GAP;}
        }
        // Neither list membership, X column nor registration order may outrank saved Y.
        ordered.sort(order);
        for(int i=0;i<ordered.size();i++){
            Box box=ordered.get(i);int y=Math.max(0,preferred.getOrDefault(box.key,box.y));
            for(int j=0;j<i;j++){
                Box prior=ordered.get(j);
                if(overlapsX(box,prior))y=Math.max(y,positions.get(prior.key)+prior.height+GAP);
            }
            positions.put(box.key,y);
        }
        // Fit connected columns as a group. Never move a lower HUD above its predecessor
        // just because it reaches the bottom; an overfull stack extends off screen.
        int[] group=new int[ordered.size()];for(int i=0;i<group.length;i++)group[i]=i;
        for(int i=0;i<ordered.size();i++)for(int j=0;j<i;j++)if(overlapsX(ordered.get(i),ordered.get(j)))group[root(group,i)]=root(group,j);
        int[] top=new int[group.length],bottom=new int[group.length];Arrays.fill(top,Integer.MAX_VALUE);
        for(int i=0;i<ordered.size();i++){
            Box box=ordered.get(i);int g=root(group,i),y=positions.get(box.key);
            top[g]=Math.min(top[g],y);bottom[g]=Math.max(bottom[g],y+box.height);
        }
        for(int i=0;i<ordered.size();i++){
            Box box=ordered.get(i);int g=root(group,i);
            int shift=Math.min(top[g],Math.max(0,bottom[g]-screenHeight));
            positions.put(box.key,positions.get(box.key)-shift);
        }
        return positions;
    }
    private static boolean overlapsX(Box a,Box b){return a.x<(long)b.x+b.width&&(long)a.x+a.width>b.x;}
    private static int root(int[] group,int i){while(group[i]!=i){group[i]=group[group[i]];i=group[i];}return i;}
}
