package com.nezurstandalone.engine;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import java.util.*;
import java.util.regex.*;
/** Read-only Blockhead powerup observations for the grinder controller. */
public final class BlockheadPowerups {
    private BlockheadPowerups() {}
    private static final Pattern RESPAWN_SECONDS = Pattern.compile("respawn\\s*:?\\s*(\\d+)\\s*s");
    public enum PowerupType {
        QUICKTRAIL, DIAMOND_ARMOR, HEAL
    }

    public static class Powerup {
        public PowerupType type;
        public Vec3 pos;
        public final Vec3 titlePos;
        public double cooldown; // seconds

        public Powerup(PowerupType type, Vec3 pos, double cooldown) {
            this(type, pos, cooldown, pos);
        }

        public Powerup(PowerupType type, Vec3 pos, double cooldown, Vec3 titlePos) {
            this.type = type;
            this.pos = pos;
            this.cooldown = cooldown;
            this.titlePos = titlePos;
        }
    }

    /** Shared read-only detector: AutoGrinder does not depend on this module being enabled. */
    public static List<Powerup> scanPowerups(Minecraft mc) {
        List<Powerup> powerups = new ArrayList<>();
        if (mc.theWorld == null) return powerups;
        List<EntityArmorStand> stands = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityArmorStand && entity.hasCustomName()) {
                stands.add((EntityArmorStand) entity);
            }
        }

        for (EntityArmorStand stand : stands) {
            PowerupType type = powerupType(stand.getCustomNameTag());

            if (type != null) {
                // Find the cooldown stand slightly below it
                double cooldown = -1;
                for (EntityArmorStand other : stands) {
                    if (other == stand) continue;
                    if (Math.abs(other.posX - stand.posX) < 0.5 && Math.abs(other.posZ - stand.posZ) < 0.5) {
                        if (Math.abs(stand.posY - other.posY) < 3.5) {
                            String otherName = cleanName(other.getCustomNameTag());
                            if (otherName.replaceAll("\\s+", "").contains("pickup")) {
                                cooldown = 0;
                                break;
                            } else if (otherName.contains("respawn")) {
                                cooldown = -1; // A malformed cooldown is unavailable, not ready.
                                Matcher m = RESPAWN_SECONDS.matcher(otherName);
                                if (m.find()) {
                                    cooldown = com.nezurstandalone.utils.SafeNumbers.nonNegativeInt(m.group(1), Integer.MAX_VALUE);
                                }
                                break;
                            }
                        }
                    }
                }

                // Unknown/missing status is not proof of availability while holograms stream in.
                Vec3 ground = groundPosition(mc, stand);
                if (cooldown >= 0 && ground != null) powerups.add(new Powerup(type, ground, cooldown,
                        new Vec3(stand.posX, stand.posY + stand.height + .5, stand.posZ)));
            }
        }
        return powerups;
    }

    static String cleanName(String text) {
        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return clean == null ? "" : clean.toLowerCase(Locale.ROOT);
    }

    static PowerupType powerupType(String text) {
        String name = cleanName(text).replaceAll("[^a-z]", "");
        if (name.contains("quicktrail")) return PowerupType.QUICKTRAIL;
        if (name.contains("diamondarmor") || name.contains("diamondarmour")) return PowerupType.DIAMOND_ARMOR;
        if (name.contains("superheal")) return PowerupType.HEAL;
        return null;
    }

    private static Vec3 groundPosition(Minecraft mc, EntityArmorStand stand) {
        // Labels float above the pickup; never submit the floating label as a path endpoint.
        BlockPos column = new BlockPos(stand.posX, stand.posY, stand.posZ);
        for (int y = column.getY(); y >= Math.max(0, column.getY() - 8); y--) {
            BlockPos floor = new BlockPos(column.getX(), y, column.getZ());
            if (!mc.theWorld.isBlockLoaded(floor)) return null;
            net.minecraft.block.Block block = mc.theWorld.getBlockState(floor).getBlock();
            if (block.getMaterial().isSolid() && !(block instanceof net.minecraft.block.BlockSign)) {
                if (mc.theWorld.getBlockState(floor.up()).getBlock().getMaterial().blocksMovement()
                        || mc.theWorld.getBlockState(floor.up(2)).getBlock().getMaterial().blocksMovement()) return null;
                return new Vec3(stand.posX, y + 1, stand.posZ);
            }
        }
        return null;
    }

}
