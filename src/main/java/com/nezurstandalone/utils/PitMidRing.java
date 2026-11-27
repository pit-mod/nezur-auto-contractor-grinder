package com.nezurstandalone.utils;

import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared ring placement perimeter and its walkable interior. */
public final class PitMidRing {
    private static final List<BlockPos> GENESIS = new ArrayList<>();
    private static final List<BlockPos> HIGH = new ArrayList<>();
    private static final List<BlockPos> CASTLE = new ArrayList<>();

    private PitMidRing() { }

    static {
        // Genesis ring positions (Y = 43)
        GENESIS.add(new BlockPos(-10, 43, -2));
        GENESIS.add(new BlockPos(-10, 43, -1));
        GENESIS.add(new BlockPos(-10, 43, 0));
        GENESIS.add(new BlockPos(-10, 43, 1));
        GENESIS.add(new BlockPos(-10, 43, 2));
        GENESIS.add(new BlockPos(-10, 43, 3));
        GENESIS.add(new BlockPos(-9, 43, 4));
        GENESIS.add(new BlockPos(-9, 43, 5));
        GENESIS.add(new BlockPos(-8, 43, 6));
        GENESIS.add(new BlockPos(-8, 43, 7));
        GENESIS.add(new BlockPos(-7, 43, 8));
        GENESIS.add(new BlockPos(-6, 43, 9));
        GENESIS.add(new BlockPos(-5, 43, 9));
        GENESIS.add(new BlockPos(-4, 43, 10));
        GENESIS.add(new BlockPos(-3, 43, 10));
        GENESIS.add(new BlockPos(-2, 43, 11));
        GENESIS.add(new BlockPos(-1, 43, 11));
        GENESIS.add(new BlockPos(0, 43, 11));
        GENESIS.add(new BlockPos(1, 43, 11));
        GENESIS.add(new BlockPos(2, 43, 11));
        GENESIS.add(new BlockPos(3, 43, 11));
        GENESIS.add(new BlockPos(4, 43, 10));
        GENESIS.add(new BlockPos(5, 43, 10));
        GENESIS.add(new BlockPos(6, 43, 9));
        GENESIS.add(new BlockPos(7, 43, 9));
        GENESIS.add(new BlockPos(8, 43, 8));
        GENESIS.add(new BlockPos(9, 43, 7));
        GENESIS.add(new BlockPos(9, 43, 6));
        GENESIS.add(new BlockPos(10, 43, 5));
        GENESIS.add(new BlockPos(10, 43, 4));
        GENESIS.add(new BlockPos(11, 43, 3));
        GENESIS.add(new BlockPos(11, 43, 2));
        GENESIS.add(new BlockPos(11, 43, 1));
        GENESIS.add(new BlockPos(11, 43, 0));
        GENESIS.add(new BlockPos(11, 43, -1));
        GENESIS.add(new BlockPos(11, 43, -2));
        GENESIS.add(new BlockPos(10, 43, -3));
        GENESIS.add(new BlockPos(10, 43, -4));
        GENESIS.add(new BlockPos(9, 43, -5));
        GENESIS.add(new BlockPos(9, 43, -6));
        GENESIS.add(new BlockPos(8, 43, -7));
        GENESIS.add(new BlockPos(7, 43, -8));
        GENESIS.add(new BlockPos(6, 43, -8));
        GENESIS.add(new BlockPos(5, 43, -9));
        GENESIS.add(new BlockPos(4, 43, -9));
        GENESIS.add(new BlockPos(3, 43, -10));
        GENESIS.add(new BlockPos(2, 43, -10));
        GENESIS.add(new BlockPos(1, 43, -10));
        GENESIS.add(new BlockPos(0, 43, -10));
        GENESIS.add(new BlockPos(-1, 43, -10));
        GENESIS.add(new BlockPos(-2, 43, -10));
        GENESIS.add(new BlockPos(-3, 43, -9));
        GENESIS.add(new BlockPos(-4, 43, -9));
        GENESIS.add(new BlockPos(-5, 43, -8));
        GENESIS.add(new BlockPos(-6, 43, -8));
        GENESIS.add(new BlockPos(-7, 43, -7));
        GENESIS.add(new BlockPos(-8, 43, -6));
        GENESIS.add(new BlockPos(-8, 43, -5));
        GENESIS.add(new BlockPos(-9, 43, -4));
        GENESIS.add(new BlockPos(-9, 43, -3));

        // High ring positions (Seasons/Corals/Elemental, Y = 82)
        HIGH.add(new BlockPos(-2, 82, 10));
        HIGH.add(new BlockPos(-1, 82, 10));
        HIGH.add(new BlockPos(0, 82, 10));
        HIGH.add(new BlockPos(1, 82, 10));
        HIGH.add(new BlockPos(2, 82, 10));
        HIGH.add(new BlockPos(3, 82, 10));
        HIGH.add(new BlockPos(4, 82, 9));
        HIGH.add(new BlockPos(5, 82, 9));
        HIGH.add(new BlockPos(6, 82, 8));
        HIGH.add(new BlockPos(7, 82, 7));
        HIGH.add(new BlockPos(8, 82, 6));
        HIGH.add(new BlockPos(9, 82, 5));
        HIGH.add(new BlockPos(9, 82, 4));
        HIGH.add(new BlockPos(10, 82, 3));
        HIGH.add(new BlockPos(10, 82, 2));
        HIGH.add(new BlockPos(10, 82, 1));
        HIGH.add(new BlockPos(10, 82, 0));
        HIGH.add(new BlockPos(10, 82, -1));
        HIGH.add(new BlockPos(10, 82, -2));
        HIGH.add(new BlockPos(9, 82, -3));
        HIGH.add(new BlockPos(9, 82, -4));
        HIGH.add(new BlockPos(8, 82, -5));
        HIGH.add(new BlockPos(7, 82, -6));
        HIGH.add(new BlockPos(6, 82, -7));
        HIGH.add(new BlockPos(5, 82, -8));
        HIGH.add(new BlockPos(4, 82, -8));
        HIGH.add(new BlockPos(3, 82, -9));
        HIGH.add(new BlockPos(2, 82, -9));
        HIGH.add(new BlockPos(1, 82, -9));
        HIGH.add(new BlockPos(0, 82, -9));
        HIGH.add(new BlockPos(-1, 82, -9));
        HIGH.add(new BlockPos(-2, 82, -9));
        HIGH.add(new BlockPos(-3, 82, -8));
        HIGH.add(new BlockPos(-4, 82, -8));
        HIGH.add(new BlockPos(-5, 82, -7));
        HIGH.add(new BlockPos(-6, 82, -6));
        HIGH.add(new BlockPos(-7, 82, -5));
        HIGH.add(new BlockPos(-8, 82, -4));
        HIGH.add(new BlockPos(-8, 82, -3));
        HIGH.add(new BlockPos(-9, 82, -2));
        HIGH.add(new BlockPos(-9, 82, -1));
        HIGH.add(new BlockPos(-9, 82, 0));
        HIGH.add(new BlockPos(-9, 82, 1));
        HIGH.add(new BlockPos(-9, 82, 2));
        HIGH.add(new BlockPos(-9, 82, 3));
        HIGH.add(new BlockPos(-8, 82, 4));
        HIGH.add(new BlockPos(-8, 82, 5));
        HIGH.add(new BlockPos(-7, 82, 6));
        HIGH.add(new BlockPos(-6, 82, 7));
        HIGH.add(new BlockPos(-5, 82, 8));
        HIGH.add(new BlockPos(-4, 82, 9));
        HIGH.add(new BlockPos(-3, 82, 9));

        // Mid ring positions (Kings, Y = 71)
        CASTLE.add(new BlockPos(10, 71, 3));
        CASTLE.add(new BlockPos(10, 71, 2));
        CASTLE.add(new BlockPos(10, 71, 1));
        CASTLE.add(new BlockPos(10, 71, 0));
        CASTLE.add(new BlockPos(10, 71, -1));
        CASTLE.add(new BlockPos(10, 71, -2));
        CASTLE.add(new BlockPos(9, 71, -3));
        CASTLE.add(new BlockPos(9, 71, -4));
        CASTLE.add(new BlockPos(8, 71, -5));
        CASTLE.add(new BlockPos(7, 71, -6));
        CASTLE.add(new BlockPos(6, 71, -7));
        CASTLE.add(new BlockPos(5, 71, -8));
        CASTLE.add(new BlockPos(4, 71, -8));
        CASTLE.add(new BlockPos(3, 71, -9));
        CASTLE.add(new BlockPos(2, 71, -9));
        CASTLE.add(new BlockPos(1, 71, -9));
        CASTLE.add(new BlockPos(0, 71, -9));
        CASTLE.add(new BlockPos(-1, 71, -9));
        CASTLE.add(new BlockPos(-2, 71, -9));
        CASTLE.add(new BlockPos(-3, 71, -8));
        CASTLE.add(new BlockPos(-4, 71, -8));
        CASTLE.add(new BlockPos(-5, 71, -7));
        CASTLE.add(new BlockPos(-6, 71, -6));
        CASTLE.add(new BlockPos(-7, 71, -5));
        CASTLE.add(new BlockPos(-8, 71, -4));
        CASTLE.add(new BlockPos(-8, 71, -3));
        CASTLE.add(new BlockPos(-9, 71, -2));
        CASTLE.add(new BlockPos(-9, 71, -1));
        CASTLE.add(new BlockPos(-9, 71, 0));
        CASTLE.add(new BlockPos(-9, 71, 1));
        CASTLE.add(new BlockPos(-9, 71, 2));
        CASTLE.add(new BlockPos(-9, 71, 3));
        CASTLE.add(new BlockPos(-8, 71, 4));
        CASTLE.add(new BlockPos(-8, 71, 5));
        CASTLE.add(new BlockPos(-7, 71, 6));
        CASTLE.add(new BlockPos(-6, 71, 7));
        CASTLE.add(new BlockPos(-5, 71, 8));
        CASTLE.add(new BlockPos(-4, 71, 9));
        CASTLE.add(new BlockPos(-3, 71, 9));
        CASTLE.add(new BlockPos(-2, 71, 10));
        CASTLE.add(new BlockPos(-1, 71, 10));
        CASTLE.add(new BlockPos(0, 71, 10));
        CASTLE.add(new BlockPos(1, 71, 10));
        CASTLE.add(new BlockPos(2, 71, 10));
        CASTLE.add(new BlockPos(3, 71, 10));
        CASTLE.add(new BlockPos(4, 71, 9));
        CASTLE.add(new BlockPos(5, 71, 9));
        CASTLE.add(new BlockPos(6, 71, 8));
        CASTLE.add(new BlockPos(7, 71, 7));
        CASTLE.add(new BlockPos(8, 71, 6));
        CASTLE.add(new BlockPos(9, 71, 5));
        CASTLE.add(new BlockPos(9, 71, 4));
    }

    public static List<BlockPos> perimeter(PitMapManager.PitMap map) {
        if (map == PitMapManager.PitMap.GENESIS) return Collections.unmodifiableList(GENESIS);
        if (map == PitMapManager.PitMap.CASTLE) return Collections.unmodifiableList(CASTLE);
        return Collections.unmodifiableList(HIGH);
    }

    public static boolean contains(double x, double y, double z) {
        PitMapManager.PitMap map = PitMapManager.getCurrentMap();
        if (map == PitMapManager.PitMap.UNKNOWN || map == PitMapManager.PitMap.CUSTOM
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        List<BlockPos> ring = perimeter(map);
        // Allow jumps and the uneven floor around mid without including the platform above.
        int ringY = ring.get(0).getY();
        if (y < ringY - 12 || y > ringY + 9 || PitMapManager.isInSpawn(x, y, z)) return false;

        int row = (int) Math.floor(z);
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        for (BlockPos edge : ring) {
            if (edge.getZ() != row) continue;
            left = Math.min(left, edge.getX());
            right = Math.max(right, edge.getX());
            // Placement blocks are the boundary, including its north and south rows.
            if (x >= edge.getX() && x < edge.getX() + 1) return false;
        }
        return x >= left + 1.0 && x < right;
    }
}
