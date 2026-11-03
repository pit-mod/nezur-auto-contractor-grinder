package com.nezurstandalone.utils;

import com.nezurstandalone.utils.map.MapLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;

/** Roni map/region definitions behind Nezur's existing integration API. */
public final class PitMapManager {
    public enum PitMap { GENESIS, ABYSS, CASTLE, ELEMENTS, FOUR_SEASONS, CUSTOM, UNKNOWN, CORALS }
    private static PitMap cachedMap=PitMap.UNKNOWN;
    private static Object cachedWorld;
    private static int checkedTick=Integer.MIN_VALUE;
    private static int customSpawnY=98,customMidY=67,customMidX,customMidZ;
    private static final BlockPos CENTER=new BlockPos(-13,115,0),SEASONS_TABLE=new BlockPos(-14,115,0),
        ELEMENTS_CHEST=new BlockPos(-13,114,7),ABYSS_CHEST=new BlockPos(-12,114,6),
        CASTLE_TABLE=new BlockPos(-14,96,0),GENESIS_TABLE=new BlockPos(-17,87,0);

    public static void detectMap(){
        Minecraft mc=Minecraft.getMinecraft();
        if(cachedWorld!=mc.theWorld){reset();cachedWorld=mc.theWorld;}
        if(mc.theWorld==null||mc.thePlayer==null||cachedMap!=PitMap.UNKNOWN)return;
        if(checkedTick==mc.thePlayer.ticksExisted)return;
        checkedTick=mc.thePlayer.ticksExisted;
        // Original Roni marker positions and priority; never force an unloaded chunk read.
        Block center=marker(mc,CENTER);
        if(center==Blocks.enchanting_table&&marker(mc,ELEMENTS_CHEST)==Blocks.ender_chest)cachedMap=PitMap.ELEMENTS;
        else if(marker(mc,SEASONS_TABLE)==Blocks.enchanting_table)cachedMap=PitMap.FOUR_SEASONS;
        else if(center==Blocks.enchanting_table&&marker(mc,ABYSS_CHEST)==Blocks.ender_chest)cachedMap=PitMap.ABYSS;
        else if(marker(mc,CASTLE_TABLE)==Blocks.enchanting_table)cachedMap=PitMap.CASTLE;
        else if(marker(mc,GENESIS_TABLE)==Blocks.enchanting_table)cachedMap=PitMap.GENESIS;
        else cachedMap=legacyMap(mc);
    }
    /** Legacy Nezur markers remain useful when a server revision moves the Roni furniture. */
    private static PitMap legacyMap(Minecraft mc){
        if(marker(mc,new BlockPos(14,85,-13))==Blocks.nether_brick)return PitMap.GENESIS;
        if(markerMeta(mc,new BlockPos(18,95,-9),Blocks.double_stone_slab,8))return PitMap.CASTLE;
        if(marker(mc,new BlockPos(-7,113,-10))==Blocks.sandstone)return PitMap.ELEMENTS;
        if(markerMeta(mc,new BlockPos(-87,85,-51),Blocks.planks,2))return PitMap.FOUR_SEASONS;
        if(marker(mc,new BlockPos(-2,113,11))==Blocks.sand)return PitMap.ABYSS;
        return PitMap.UNKNOWN;
    }
    private static boolean markerMeta(Minecraft mc,BlockPos pos,Block block,int meta){
        return marker(mc,pos)==block&&block.getMetaFromState(mc.theWorld.getBlockState(pos))==meta;
    }
    private static Block marker(Minecraft mc,BlockPos pos){return mc.theWorld.isBlockLoaded(pos)?mc.theWorld.getBlockState(pos).getBlock():null;}
    public static PitMap getCurrentMap(){detectMap();return cachedMap;}
    public static void reset(){cachedMap=PitMap.UNKNOWN;checkedTick=Integer.MIN_VALUE;}
    public static void setPitMap(PitMap map){cachedWorld=Minecraft.getMinecraft().theWorld;cachedMap=map==PitMap.CORALS?PitMap.ABYSS:map;checkedTick=Integer.MIN_VALUE;}
    public static void setCustomCoords(int spawnY,int midY,int midX,int midZ){customSpawnY=spawnY;customMidY=midY;customMidX=midX;customMidZ=midZ;}
    public static int getSpawnY(){
        switch(getCurrentMap()){
            case CASTLE:return 95;case GENESIS:return 86;
            case ABYSS:case CORALS:case ELEMENTS:case FOUR_SEASONS:return 114;
            case CUSTOM:return customSpawnY;default:return 0;
        }
    }
    public static int getMidY(){
        switch(getCurrentMap()){
            case CASTLE:return 71;case GENESIS:return 43;
            case ABYSS:case CORALS:case ELEMENTS:case FOUR_SEASONS:return 82;
            case CUSTOM:return customMidY;default:return 0;
        }
    }
    public static int getMidCenterX(){return getCurrentMap()==PitMap.CUSTOM?customMidX:0;}
    public static int getMidCenterZ(){return getCurrentMap()==PitMap.CUSTOM?customMidZ:0;}
    public static BlockPos getMidCenter(){return new BlockPos(getMidCenterX(),getMidY(),getMidCenterZ());}
    /** All callers share the same ordered Roni regions, including the roof and faction spawns. */
    public static boolean isInSpawn(double x,double y,double z){
        String zone=getZone(x,y,z);
        return isMainSpawnZone(zone)||zone.equals("Angel")||zone.equals("Demon");
    }
    public static boolean isInMainSpawn(double x,double y,double z){return isMainSpawnZone(getZone(x,y,z));}
    private static boolean isMainSpawnZone(String zone){return zone.equals("Spawn")||zone.equals("Overspawn");}

    /** Locate a walkable mid floor near this map's actual height, never under the spawn platform. */
    public static BlockPos findMidLanding(double x,double z){
        Minecraft mc=Minecraft.getMinecraft();
        if(getCurrentMap()==PitMap.UNKNOWN||mc.theWorld==null||!Double.isFinite(x)||!Double.isFinite(z))return null;
        int top=Math.min(253,Math.min(getMidY()+8,getSpawnY()-4));
        int bottom=Math.max(1,getMidY()-12);
        for(int y=top;y>=bottom;y--){
            BlockPos floor=new BlockPos(x,y,z);
            if(!mc.theWorld.isBlockLoaded(floor)||!mc.theWorld.isBlockLoaded(floor.up(2)))return null;
            if(mc.theWorld.getBlockState(floor).getBlock().getMaterial().isSolid()
                    &&mc.theWorld.isAirBlock(floor.up())&&mc.theWorld.isAirBlock(floor.up(2)))return floor.up();
        }
        return null;
    }
    public static boolean isInSpawnRegion(double x,double y,double z,int baseY,int radius){
        PitMap map=getCurrentMap();
        if(map==PitMap.UNKNOWN||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        if(map==PitMap.GENESIS&&(isInBox(x,y,z,60,82,62,80,-82,-58)||isInBox(x,y,z,-82,-60,62,75,60,82)))return true;
        if(map==PitMap.CUSTOM)return (int)y>getSpawnY()-2&&(int)y<getSpawnY()+12;
        return x>=-radius&&x<=radius&&z>=-radius&&z<=radius&&y>baseY-2&&y<baseY+12;
    }
    public static MapLocation getLocation(double x,double y,double z){return MapLocation.at(getCurrentMap(),x,y,z);}
    public static boolean isInSewer(double x,double y,double z){return getLocation(x,y,z)==MapLocation.CASTLE_SEWERS;}

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

    public static String getZone(double x,double y,double z){
        if(getCurrentMap()==PitMap.CUSTOM){
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return "Unknown";
            int by=(int)Math.floor(y);
            if(by>customSpawnY-2&&by<customSpawnY+12)return "Spawn";
            if(by>=customSpawnY+12)return "Overspawn";
            return Math.abs(x-customMidX)<=20&&Math.abs(z-customMidZ)<=20?"Pit":"Outskirts";
        }
        MapLocation location=getLocation(x,y,z);
        return location==null?"Unknown":location.getZoneName();
    }
    public static boolean isInCombatArea(double x,double y,double z){
        String zone=getZone(x,y,z);
        if(isInSpawn(x,y,z)||zone.equals("Spawn")||zone.equals("Overspawn")||zone.equals("Sewer")||zone.equals("Angel")||zone.equals("Demon"))return false;
        return zone.equals("Pit")||com.nezurstandalone.engine.PitCombatArea.contains(getCurrentMap().name(),x,y,z);
    }
}
