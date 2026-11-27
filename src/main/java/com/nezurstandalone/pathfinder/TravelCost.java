package com.nezurstandalone.pathfinder;

/** Nominal vanilla movement time, expressed in sprint-block cost units. */
final class TravelCost {
    private static final double SPEED=5.6;
    private static final double[] FALL=new double[1025],JUMP=new double[25];
    static {
        for(int i=1;i<FALL.length;i++)FALL[i]=verticalTime(i/16.0,false)*SPEED;
        for(int i=1;i<JUMP.length;i++)JUMP[i]=verticalTime(i/16.0,true)*SPEED;
    }
    private static double verticalTime(double height,boolean jump) {
        double y=0,velocity=jump?.42:0;
        for(int tick=1;tick<=200;tick++) {
            y+=velocity;velocity=(velocity-.08)*.98;
            if(jump?velocity<=0&&y<=height:y<=-height)return tick/20.0;
        }
        return 10;
    }
    static double movement(double horizontal,double rise,boolean ladder,double stepAssist) {
        if(ladder)return Math.max(horizontal,Math.abs(rise)*SPEED/2.35);
        if(rise>stepAssist)return Math.max(horizontal,JUMP[Math.min(JUMP.length-1,Math.max(1,(int)Math.ceil(rise*16)))]);
        if(rise<-.01)return Math.max(horizontal,FALL[Math.min(FALL.length-1,Math.max(1,(int)Math.ceil(-rise*16)))]);
        return horizontal;
    }
    static double lowerBound(int x,int y,int z,int ex,int ey,int ez,double up,double down) {
        double dx=Math.abs((double)ex-x),dz=Math.abs((double)ez-z);
        double horizontal=Math.max(0,Math.max(dx,dz)+(Math.sqrt(2)-1)*Math.min(dx,dz)-1);
        double vertical=Math.max(0,Math.abs((double)ey-y)-1)*(ey>y?up:down);
        return horizontal+vertical;
    }
}
