package com.nezurstandalone.utils.map;

/** Region bounds adapted from Roni; inclusive edges are intentional. */

public class LocationBox {
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    LocationBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean contains(int x, int y, int z) {
        return minX <= x && x <= maxX && minY <= y && y <= maxY && minZ <= z && z <= maxZ;
    }
}
