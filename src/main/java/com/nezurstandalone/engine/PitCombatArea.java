package com.nezurstandalone.engine;

/** Horizontal roaming margin; canonical map zones and contract scoring stay unchanged. */
public final class PitCombatArea {
    public static final double MARGIN=20;
    private PitCombatArea() {}
    public static boolean contains(String map,double x,double y,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        if("GENESIS".equals(map))return x*x+z*z<=55*55;
        if("CASTLE".equals(map))return x*x+z*z<=40*40;
        if("CORALS".equals(map))return x*x+z*z<=39*39;
        if("ELEMENTS".equals(map))return y>=79&&y<=111&&rectangle(x,z,-12,13,-13,14);
        if("FOUR_SEASONS".equals(map))return y>=78&&y<=111&&rectangle(x,z,-12,13,-12,13);
        return false;
    }
    private static boolean rectangle(double x,double z,double minX,double maxX,double minZ,double maxZ) {
        double dx=Math.max(minX-x,Math.max(0,x-maxX));
        double dz=Math.max(minZ-z,Math.max(0,z-maxZ));
        return dx*dx+dz*dz<=MARGIN*MARGIN;
    }
    public static boolean mayReturn(boolean rage,boolean core,boolean roaming) {
        return !rage && (core || !roaming);
    }
    public static boolean sameSpireFloor(double playerY,double targetY) {
        return Double.isFinite(playerY)&&Double.isFinite(targetY)&&Math.abs(playerY-targetY)<=3.5;
    }
}
