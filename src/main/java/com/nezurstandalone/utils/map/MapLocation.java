package com.nezurstandalone.utils.map;

import com.nezurstandalone.utils.PitMapManager.PitMap;

/** Roni map regions in their original first-match order, adapted for Nezur callers. */

import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum MapLocation {
    CASTLE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-23, 92, -23, 23, 107, 23))), EnumChatFormatting.DARK_GREEN + "SPAWN", PitMap.CASTLE),
    CASTLE_ABOVE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-28, 107, -28, 28, 255, 28))), EnumChatFormatting.AQUA + "Above Spawn", PitMap.CASTLE),
    CASTLE_PIT(new ArrayList<>(Arrays.asList(new LocationBox(-23, 0, -23, 23, 255, 23))), EnumChatFormatting.YELLOW + "Pit", PitMap.CASTLE),
    CASTLE_SEWERS(new ArrayList<>(Arrays.asList(
            new LocationBox(11, 49, 26, 80, 60, 87),
            new LocationBox(53, 52, 11, 117, 61, 60))), EnumChatFormatting.DARK_AQUA + "Sewers", PitMap.CASTLE),
    CASTLE_PORT(new ArrayList<>(Arrays.asList(
            new LocationBox(-130, 0, 8, -23, 255, 130),
            new LocationBox(-23, 0, 23, 0, 255, 130))), EnumChatFormatting.RED + "Port", PitMap.CASTLE),
    CASTLE_KINGS_HALLWAY(new ArrayList<>(Arrays.asList(new LocationBox(-160, 0, -6, -20, 255, 8))), EnumChatFormatting.GOLD + "King's Hallway", PitMap.CASTLE),
    CASTLE_FOREST(new ArrayList<>(Arrays.asList(
            new LocationBox(-23, 0, -160, 0, 255, -23),
            new LocationBox(-160, 0, -160, -23, 255, -6))), EnumChatFormatting.GREEN + "Forest", PitMap.CASTLE),
    CASTLE_CITY(new ArrayList<>(Arrays.asList(
            new LocationBox(0, 0, 23, 23, 255, 160),
            new LocationBox(23, 0, 0, 160, 255, 160))), EnumChatFormatting.AQUA + "City", PitMap.CASTLE),
    CASTLE_FARMS(new ArrayList<>(Arrays.asList(
            new LocationBox(23, 0, -23, 160, 255, 0),
            new LocationBox(0, 0, -160, 160, 255, -23))), EnumChatFormatting.GOLD + "Farms", PitMap.CASTLE),
    ABYSS_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-20, 114, -20, 20, 129, 20))), EnumChatFormatting.DARK_GREEN + "SPAWN", PitMap.ABYSS),
    ABYSS_ABOVE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-25, 129, -25, 25, 255, 25))), EnumChatFormatting.AQUA + "Above Spawn", PitMap.ABYSS),
    ABYSS_PIT(new ArrayList<>(Arrays.asList(new LocationBox(-20, 0, -20, 20, 255, 20))), EnumChatFormatting.YELLOW + "Pit", PitMap.ABYSS),
    ABYSS_SHIPWRECK(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, 0, -20, 255, 160),
            new LocationBox(-20, 0, 20, 0, 255, 160))), EnumChatFormatting.YELLOW + "Shipwreck", PitMap.ABYSS),
    ABYSS_GEYSER(new ArrayList<>(Arrays.asList(
            new LocationBox(0, 0, 20, 20, 255, 160),
            new LocationBox(20, 0, 0, 160, 255, 160))), EnumChatFormatting.RED + "Geyser", PitMap.ABYSS),
    ABYSS_TEMPLE(new ArrayList<>(Arrays.asList(
            new LocationBox(20, 0, -20, 160, 255, 0),
            new LocationBox(0, 0, -160, 160, 255, -20))), EnumChatFormatting.YELLOW + "Temple", PitMap.ABYSS),
    ABYSS_SEAWEED(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, -160, -20, 255, 0),
            new LocationBox(-20, 0, -160, 0, 255, -20))), EnumChatFormatting.DARK_GREEN + "Seaweed", PitMap.ABYSS),
    ELEMENTS_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-20, 114, -20, 20, 129, 20))), EnumChatFormatting.DARK_GREEN + "SPAWN", PitMap.ELEMENTS),
    ELEMENTS_ABOVE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-25, 129, -25, 25, 255, 25))), EnumChatFormatting.AQUA + "Above Spawn", PitMap.ELEMENTS),
    ELEMENTS_PIT(new ArrayList<>(Arrays.asList(new LocationBox(-20, 0, -20, 20, 255, 20))), EnumChatFormatting.YELLOW + "Pit", PitMap.ELEMENTS),
    ELEMENTS_SKY(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, 0, -20, 255, 160),
            new LocationBox(-20, 0, 20, 0, 255, 160))), EnumChatFormatting.WHITE + "Sky", PitMap.ELEMENTS),
    ELEMENTS_LAVA(new ArrayList<>(Arrays.asList(
            new LocationBox(0, 0, 20, 20, 255, 160),
            new LocationBox(20, 0, 0, 160, 255, 160))), EnumChatFormatting.RED + "Lava", PitMap.ELEMENTS),
    ELEMENTS_MOUNTAINS(new ArrayList<>(Arrays.asList(
            new LocationBox(20, 0, -20, 160, 255, 0),
            new LocationBox(0, 0, -160, 160, 255, -20))), EnumChatFormatting.GRAY + "Mountains", PitMap.ELEMENTS),
    ELEMENTS_WATER(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, -160, -20, 255, 0),
            new LocationBox(-20, 0, -160, 0, 255, -20))), EnumChatFormatting.AQUA + "Water", PitMap.ELEMENTS),
    FOUR_SEASONS_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-20, 114, -20, 20, 129, 20))), EnumChatFormatting.DARK_GREEN + "SPAWN", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_ABOVE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-25, 129, -25, 25, 255, 25))), EnumChatFormatting.AQUA + "Above Spawn", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_PIT(new ArrayList<>(Arrays.asList(new LocationBox(-20, 0, -20, 20, 255, 20))), EnumChatFormatting.YELLOW + "Pit", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_SPRING(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, 0, -20, 255, 160),
            new LocationBox(-20, 0, 20, 0, 255, 160))), EnumChatFormatting.LIGHT_PURPLE + "Spring", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_WINTER(new ArrayList<>(Arrays.asList(
            new LocationBox(0, 0, 20, 20, 255, 160),
            new LocationBox(20, 0, 0, 160, 255, 160))), EnumChatFormatting.WHITE + "Winter", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_AUTUMN(new ArrayList<>(Arrays.asList(
            new LocationBox(20, 0, -20, 160, 255, 0),
            new LocationBox(0, 0, -160, 160, 255, -20))), EnumChatFormatting.GOLD + "Autumn", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_SUMMER_CAVE(new ArrayList<>(Arrays.asList(new LocationBox(-300, 0, -300, 35, 100, -120))), EnumChatFormatting.YELLOW + "Summer Cave", PitMap.FOUR_SEASONS),
    FOUR_SEASONS_SUMMER(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, -160, -20, 255, 0),
            new LocationBox(-20, 0, -160, 0, 255, -20))), EnumChatFormatting.YELLOW + "Summer", PitMap.FOUR_SEASONS),
    GENESIS_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-20, 86, -20, 20, 101, 20))), EnumChatFormatting.DARK_GREEN + "SPAWN", PitMap.GENESIS),
    GENESIS_ABOVE_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-36, 101, -36, 36, 255, 36))), EnumChatFormatting.AQUA + "Above Spawn", PitMap.GENESIS),
    GENESIS_ANGEL_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(-82, 62, 60, -60, 75, 82))), EnumChatFormatting.AQUA + "\u2646 SPAWN \u2646", PitMap.GENESIS),
    GENESIS_DEMON_SPAWN(new ArrayList<>(Arrays.asList(new LocationBox(60, 62, -82, 82, 80, -58))), EnumChatFormatting.RED + "\u2668 SPAWN \u2668", PitMap.GENESIS),
    GENESIS_PIT(new ArrayList<>(Arrays.asList(new LocationBox(-20, 0, -20, 20, 255, 20))), EnumChatFormatting.YELLOW + "Pit", PitMap.GENESIS),
    GENESIS_PALACE(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, 0, -20, 255, 160),
            new LocationBox(-20, 0, 20, 0, 255, 160))), EnumChatFormatting.WHITE + "Palace", PitMap.GENESIS),
    GENESIS_GARDEN(new ArrayList<>(Arrays.asList(
            new LocationBox(0, 0, 20, 20, 255, 160),
            new LocationBox(20, 0, 0, 160, 255, 160))), EnumChatFormatting.GREEN + "Garden", PitMap.GENESIS),
    GENESIS_FORTRESS(new ArrayList<>(Arrays.asList(
            new LocationBox(20, 0, -20, 160, 255, 0),
            new LocationBox(0, 0, -160, 160, 255, -20))), EnumChatFormatting.RED + "Fortress", PitMap.GENESIS),
    GENESIS_BADLANDS(new ArrayList<>(Arrays.asList(
            new LocationBox(-160, 0, -160, -20, 255, 0),
            new LocationBox(-20, 0, -160, 0, 255, -20))), EnumChatFormatting.GOLD + "Badlands", PitMap.GENESIS);

    public final List<LocationBox> boxes;
    private final String displayName;
    private final String zoneName;
    private final Set<PitMap> maps = EnumSet.noneOf(PitMap.class);

    MapLocation(List<LocationBox> boxes, String displayName, PitMap... maps) {
        this.boxes = boxes;
        this.displayName = displayName;
        String key=name();
        this.zoneName=key.endsWith("_ABOVE_SPAWN")?"Overspawn":key.endsWith("_ANGEL_SPAWN")?"Angel":key.endsWith("_DEMON_SPAWN")?"Demon":key.endsWith("_SPAWN")?"Spawn":key.equals("CASTLE_SEWERS")?"Sewer":key.equals("CASTLE_FARMS")?"Farm":displayName.replaceAll("\u00a7.","");
        this.maps.addAll(Arrays.asList(maps));
    }

    public String getFormattedName() {
        if (displayName.length() < 3) {
            return EnumChatFormatting.BOLD + displayName;
        }
        return displayName.substring(0, 2) + EnumChatFormatting.BOLD + displayName.substring(2);
    }

    private static final java.util.Map<PitMap,List<MapLocation>> BY_MAP = new java.util.EnumMap<>(PitMap.class);
    static {
        for(PitMap map:PitMap.values()){
            List<MapLocation> locations=new ArrayList<>();
            for(MapLocation location:values())if(location.maps.contains(map))locations.add(location);
            BY_MAP.put(map,java.util.Collections.unmodifiableList(locations));
        }
    }
    public static List<MapLocation> getLocationsForMap(PitMap map) {
        return BY_MAP.get(map==PitMap.CORALS?PitMap.ABYSS:map);
    }
    public static MapLocation at(PitMap map,double x,double y,double z){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return null;
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);
        for(MapLocation location:getLocationsForMap(map))for(LocationBox box:location.boxes)
            if(box.contains(bx,by,bz))return location;
        return null;
    }
    /** Compatibility keys used by Nezur's existing HUD, contract and automation code. */
    public String getZoneName(){return zoneName;}
}
