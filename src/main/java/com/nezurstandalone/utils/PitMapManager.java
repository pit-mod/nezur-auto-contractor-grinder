package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;

public class PitMapManager {
    public enum PitMap {
        GENESIS, CORALS, CASTLE, ELEMENTS, FOUR_SEASONS, UNKNOWN
    }

    private static PitMap cachedMap = PitMap.UNKNOWN;
    private static boolean hasChecked = false;
    private static Object cachedWorld;
    private static long checkedAt;

    public static void detectMap() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            cachedMap = PitMap.UNKNOWN;
            hasChecked = false;
            return;
        }

        Block genesisCheck = mc.theWorld.getBlockState(new BlockPos(14, 85, -13)).getBlock();
        if (genesisCheck == Blocks.nether_brick) {
            cachedMap = PitMap.GENESIS;
            hasChecked = true;
            return;
        }

        BlockPos castlePos = new BlockPos(18, 95, -9);
        Block castleCheck = mc.theWorld.getBlockState(castlePos).getBlock();
        if (castleCheck == Blocks.double_stone_slab) {
            int meta = castleCheck.getMetaFromState(mc.theWorld.getBlockState(castlePos));
            if (meta == 8) {
                cachedMap = PitMap.CASTLE;
                hasChecked = true;
                return;
            }
        }

        Block elementsCheck = mc.theWorld.getBlockState(new BlockPos(-7, 113, -10)).getBlock();
        if (elementsCheck == Blocks.sandstone) {
            cachedMap = PitMap.ELEMENTS;
            hasChecked = true;
            return;
        }

        Block seasonsCheck = mc.theWorld.getBlockState(new BlockPos(-87, 85, -51)).getBlock();
        if (seasonsCheck == Blocks.planks) {
            int seasonsMeta = mc.theWorld.getBlockState(new BlockPos(-87, 85, -51)).getBlock()
                    .getMetaFromState(mc.theWorld.getBlockState(new BlockPos(-87, 85, -51)));
            if (seasonsMeta == 2) {
                cachedMap = PitMap.FOUR_SEASONS;
                hasChecked = true;
                return;
            }
        }

        Block coralsCheck = mc.theWorld.getBlockState(new BlockPos(-2, 113, 11)).getBlock();
        if (coralsCheck == Blocks.sand) {
            cachedMap = PitMap.CORALS;
            hasChecked = true;
            return;
        }

        cachedMap = PitMap.UNKNOWN;
    }

    public static PitMap getCurrentMap() {
        Object world = Minecraft.getMinecraft().theWorld;
        long now = com.nezurstandalone.control.Clock.millis();
        if (world != cachedWorld || now - checkedAt > 5000L) {
            reset(); cachedWorld = world; checkedAt = now;
        }
        if (!hasChecked) {
            detectMap();
        }
        return cachedMap;
    }

    public static void reset() {
        cachedMap = PitMap.UNKNOWN;
        hasChecked = false;
    }

    public static boolean isInSpawn(double x, double y, double z) {
        if (getCurrentMap() == PitMap.FOUR_SEASONS) {
            return isInBox(x, y, z, 50, -50, 111, 138, -50, 50);
        } else if (getCurrentMap() == PitMap.GENESIS) {
            return isInBox(x, y, z, -50, 50, 85, 108, -50, 50);
        } else if (getCurrentMap() == PitMap.ELEMENTS) {
            return isInBox(x, y, z, -50, 50, 111, 122, -50, 50);
        } else if (getCurrentMap() == PitMap.CASTLE) {
            return isInBox(x, y, z, -22, 25, 90, 115, -24, 23);
        } else if (getCurrentMap() == PitMap.CORALS) {
            return isInBox(x, y, z, -18, 18, 114, 123, -21, 20);
        }
        return false;
    }

    public static boolean isInSewer(double x, double y, double z) {
        return getZone(x, y, z).equals("Sewer");
    }
    
    /**
     * The sewer chest that can only be reached by the jump across the gap, and how far from
     * that point a chest still counts as being that chest.
     *
     * <p>The radius is deliberately loose: the recorded position is approximate, and every
     * chest that lands in this corner of the sewer needs the same jump regardless of exactly
     * where in the room it sits.
     */
    private static final double PARKOUR_CHEST_X = 80;
    private static final double PARKOUR_CHEST_Y = 60;
    private static final double PARKOUR_CHEST_Z = 65;
    private static final double PARKOUR_CHEST_RADIUS = 10.0;

    /** Far edge of the gap: anything past this Z can only be reached by jumping. */
    private static final double PARKOUR_GAP_Z = 59.5;

    /**
     * True when a chest at this position is behind the sewer parkour gap.
     *
     * <p>The radius alone would also reach back over the gap onto the approach side, where
     * chests are simply walked to — so the far-side test is what actually decides it, and the
     * radius only bounds how far into that side the room extends.
     */
    public static boolean isParkourChest(double x, double y, double z) {
        if (getCurrentMap() != PitMap.CASTLE || z < PARKOUR_GAP_Z) {
            return false;
        }
        double dx = x - PARKOUR_CHEST_X;
        double dy = y - PARKOUR_CHEST_Y;
        double dz = z - PARKOUR_CHEST_Z;
        return dx * dx + dy * dy + dz * dz <= PARKOUR_CHEST_RADIUS * PARKOUR_CHEST_RADIUS;
    }

    /**
     * A sewer chest that spawns on a ledge and can only be looted from one specific block: the
     * pathfinder is sent to {@link #REDIRECT_CHEST_TARGET} rather than at the chest, and ChestAura
     * opens it from there. Like the parkour chest, its exact spawn wobbles, so a small radius
     * around the recorded approximate position claims it.
     */
    private static final BlockPos REDIRECT_CHEST_1_APPROX = new BlockPos(105, 60, 13);
    private static final BlockPos REDIRECT_CHEST_1_TARGET = new BlockPos(103, 58, 13);
    
    private static final BlockPos REDIRECT_CHEST_2_APPROX = new BlockPos(99, 63, 46);
    private static final BlockPos REDIRECT_CHEST_2_TARGET = new BlockPos(99, 58, 47);
    
    private static final double REDIRECT_CHEST_RADIUS = 5.0;

    /** 
     * Returns the target block the bot should walk to if the given coordinates belong to a redirect chest.
     * Returns null if it is not a redirect chest.
     */
    public static BlockPos getRedirectTarget(double x, double y, double z) {
        if (getCurrentMap() != PitMap.CASTLE) {
            return null;
        }
        
        double dx = x - REDIRECT_CHEST_1_APPROX.getX();
        double dy = y - REDIRECT_CHEST_1_APPROX.getY();
        double dz = z - REDIRECT_CHEST_1_APPROX.getZ();
        if (dx * dx + dy * dy + dz * dz <= REDIRECT_CHEST_RADIUS * REDIRECT_CHEST_RADIUS) {
            return REDIRECT_CHEST_1_TARGET;
        }
        
        dx = x - REDIRECT_CHEST_2_APPROX.getX();
        dy = y - REDIRECT_CHEST_2_APPROX.getY();
        dz = z - REDIRECT_CHEST_2_APPROX.getZ();
        if (dx * dx + dy * dy + dz * dz <= REDIRECT_CHEST_RADIUS * REDIRECT_CHEST_RADIUS) {
            return REDIRECT_CHEST_2_TARGET;
        }
        
        return null;
    }

    public static boolean isParkourRoom(double x, double y, double z) {
        if (getCurrentMap() != PitMap.CASTLE) return false;
        // The chest is around 80 60 65. The setup block is 81 58 58.
        // The jump gap starts around Z=59.
        // We blacklist the area starting from Z=59.5 to Z=70 to prevent A* from pathfinding across the gap or inside the room walls.
        return isInBox(x, y, z, 77, 83, 58, 65, PARKOUR_GAP_Z, 70);
    }

    private static boolean isInBox(double x, double y, double z, double x1, double x2, double y1, double y2, double z1,
            double z2) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public static String getZone(double x, double y, double z) {
        PitMap map = getCurrentMap();

        if (map == PitMap.FOUR_SEASONS) {
            if (isInBox(x, y, z, 18, -16, 111, 138, -20, 17)) {
                return "Spawn";
            }
            if (isInBox(x, y, z, -104, 102, 172, 500, -99, 110)) {
                return "Overspawn";
            }
            if (isInBox(x, y, z, -12, 13, 78, 111, -12, 13)) {
                return "Pit";
            }
            if (z > 0) {
                return (x >= 0) ? "Winter" : "Spring";
            } else {
                return (x >= 0) ? "Autumn" : "Summer";
            }
        }

        if (map == PitMap.GENESIS) {
            if (isInBox(x, y, z, -30, 22, 85, 108, -34, 20)) {
                return "Spawn";
            }
            if (isInBox(x, y, z, -78, -61, 64, 70, 62, 78)) {
                return "Angel";
            }
            if (isInBox(x, y, z, 57, 81, 64, 81, -56, -81)) {
                return "Demon";
            }
            if (isInBox(x, y, z, -93, 106, 126, 500, -90, 88)) {
                return "Overspawn";
            }
            double distSqCenter = x * x + z * z;
            if (distSqCenter > 35 * 35) {
                if (z > 0) {
                    return (x > 0) ? "Garden" : "Palace";
                } else {
                    return (x > 0) ? "Fortress" : "Badlands";
                }
            }
            return "Pit";
        }

        if (map == PitMap.ELEMENTS) {
            if (isInBox(x, y, z, -16, 20, 111, 122, -16, 23)) {
                return "Spawn";
            }
            if (isInBox(x, y, z, -98, 105, 145, 500, -96, 99)) {
                return "Overspawn";
            }
            if (isInBox(x, y, z, -12, 13, 79, 111, -13, 14)) {
                return "Pit";
            }
            if (z > 0) {
                return (x >= 0) ? "Lava" : "Sky";
            } else {
                return (x >= 0) ? "Mountains" : "Water";
            }
        }

        if (map == PitMap.CASTLE) {
            // #1 Spawn
            if (isInBox(x, y, z, -22, 25, 90, 115, -24, 23)) {
                return "Spawn";
            }
            // #2 Sewer
            if (isInBox(x, y, z, -10, 131, 44, 62, 9, 87)) {
                return "Sewer";
            }
            // #3 Region (Distance Filter)
            if (getDistSq(x, z, 0, 0) > 20 * 20) {
                if (z > 0) {
                    return (x >= 0) ? "City" : "Port";
                } else {
                    return (x >= 0) ? "Farm" : "Forest";
                }
            }
            // #4 OverSpawn
            if (y >= 124) {
                return "Overspawn";
            }
            // #5 Pit
            return "Pit";
        }

        if (map == PitMap.CORALS) {
            // #1 Spawn
            if (isInBox(x, y, z, -18, 18, 114, 123, -21, 20)) {
                return "Spawn";
            }
            // #2 Region
            if (getDistSq(x, z, 0, 0) > 19 * 19) {
                if (z > 0) {
                    return (x >= 0) ? "Geyser" : "Shipwreck";
                } else {
                    return (x >= 0) ? "Temple" : "Seaweed";
                }
            }
            // #3 Overspawn
            if (isInBox(x, y, z, -98, 91, 145, 500, -119, 124)) {
                return "Overspawn";
            }
            // #4 Pit
            return "Pit";
        }

        return "???";
    }

    private static double getDistSq(double x, double z, double tx, double tz) {
        return (x - tx) * (x - tx) + (z - tz) * (z - tz);
    }
}
