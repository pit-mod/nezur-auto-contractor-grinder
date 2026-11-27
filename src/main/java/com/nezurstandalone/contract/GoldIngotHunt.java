package com.nezurstandalone.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.AxisAlignedBB;

/** Bounded scoring and short retry cooldowns for the ingot contract. */
public final class GoldIngotHunt {
    private static final int SHORTLIST_SIZE = 12;
    private static final int DENSITY_LIMIT = 20;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Integer, Long> skipped = new HashMap<>();
    private int visible, viable;

    public void reset() { skipped.clear(); visible = viable = 0; }
    public int visible() { return visible; }
    public int viable() { return viable; }
    public void skip(EntityItem item, long now) { skipped.put(item.getEntityId(), now + 3500); }

    public boolean live(EntityItem item) {
        return item != null && mc.theWorld.getEntityByID(item.getEntityId()) == item && isGold(item);
    }

    private boolean isGold(EntityItem item) {
        ItemStack stack = item.getEntityItem();
        return !item.isDead && stack != null && stack.stackSize > 0 && stack.getItem() == Items.gold_ingot;
    }

    public EntityItem select(EntityItem current, double radius, long now) {
        skipped.values().removeIf(until -> until <= now);
        List<EntityItem> drops = new ArrayList<>();
        List<EntityPlayer> players = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (mc.thePlayer.getDistanceSqToEntity(entity) > radiusSq) continue;
            if (entity instanceof EntityItem && isGold((EntityItem) entity)) drops.add((EntityItem) entity);
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && !player.isDead && !player.isSpectator()
                    && mc.thePlayer.getDistanceSqToEntity(player) <= (radius + 4) * (radius + 4)) players.add(player);
        }
        visible = drops.size();
        viable = 0;
        List<EntityItem> shortlist = new ArrayList<>(drops);
        shortlist.removeIf(item -> skipped.containsKey(item.getEntityId()));
        shortlist.sort(Comparator.comparingDouble(this::travelCost).thenComparingInt(Entity::getEntityId));
        EntityItem best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        if (live(current) && !skipped.containsKey(current.getEntityId())
                && mc.thePlayer.getDistanceSqToEntity(current) <= radiusSq && stand(current) != null) {
            best = current;
            bestCost = cost(current, drops, players) - 1.75;
            viable++;
        }
        int checked = 0;
        for (EntityItem item : shortlist) {
            if (item == current || stand(item) == null) continue;
            viable++;
            if(checked++<SHORTLIST_SIZE){
                double cost = cost(item, drops, players);
                if (cost < bestCost) { best = item; bestCost = cost; }
            }
            if (viable>=DENSITY_LIMIT) break;
        }
        return best;
    }

    private double travelCost(EntityItem item) {
        return mc.thePlayer.getDistanceToEntity(item) + 1.5 * Math.abs(mc.thePlayer.posY - item.posY);
    }

    private double cost(EntityItem item, List<EntityItem> drops, List<EntityPlayer> players) {
        int nearbyGold = 0;
        for (EntityItem other : drops) {
            if (other != item && item.getDistanceSqToEntity(other) <= 9 && ++nearbyGold == 4) break;
        }
        int competition = 0;
        for (EntityPlayer player : players) {
            if (item.getDistanceSqToEntity(player) <= 12.25 && ++competition == 3) break;
        }
        int stack = Math.min(4, item.getEntityItem().stackSize);
        return travelCost(item) - nearbyGold * .5 - (stack - 1) * .65 + competition * .75;
    }

    /** Select a legal endpoint within pickup reach, including an adjacent tile near a wall. */
    public BlockPos stand(EntityItem item) {
        BlockPos origin = new BlockPos(item.posX, item.posY, item.posZ);
        BlockPos best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos at = origin.add(dx, dy, dz);
            double x = at.getX() + .5, z = at.getZ() + .5;
            double horizontal = Math.hypot(x - item.posX, z - item.posZ);
            double vertical = Math.abs(at.getY() - item.posY);
            if (horizontal > .85 || vertical > 1 || !walkable(at)) continue;
            double cost = horizontal + vertical * 1.5
                    + .06 * Math.sqrt(mc.thePlayer.getDistanceSq(x, at.getY(), z));
            if (cost < bestCost) { best = at; bestCost = cost; }
        }
        return best;
    }

    private boolean walkable(BlockPos at) {
        if (!mc.theWorld.isBlockLoaded(at.down()) || !mc.theWorld.isBlockLoaded(at.up())) return false;
        net.minecraft.block.Block lower = mc.theWorld.getBlockState(at).getBlock();
        net.minecraft.block.Block upper = mc.theWorld.getBlockState(at.up()).getBlock();
        net.minecraft.block.Block floor = mc.theWorld.getBlockState(at.down()).getBlock();
        return !lower.getMaterial().blocksMovement() && !upper.getMaterial().blocksMovement()
                && !lower.getMaterial().isLiquid() && !upper.getMaterial().isLiquid()
                && lower != Blocks.fire && upper != Blocks.fire && floor != Blocks.cactus
                && floor.getMaterial().isSolid();
    }

    /** Check the player's footprint for both clearance and support on the final approach. */
    public boolean clearApproach(EntityItem item, double distance) {
        if (distance <= .3) return true;
        double dx = (item.posX - mc.thePlayer.posX) / distance;
        double dz = (item.posZ - mc.thePlayer.posZ) / distance;
        for (double step = .35; step <= distance + .2; step += .35) {
            AxisAlignedBB body = mc.thePlayer.getEntityBoundingBox().offset(dx * step, 0, dz * step);
            if (!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, body).isEmpty()) return false;
            AxisAlignedBB floor = new AxisAlignedBB(body.minX, body.minY - .6, body.minZ, body.maxX, body.minY, body.maxZ);
            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, floor).isEmpty()) return false;
            BlockPos feet = new BlockPos(mc.thePlayer.posX + dx * step, mc.thePlayer.posY, mc.thePlayer.posZ + dz * step);
            net.minecraft.block.Block block = mc.theWorld.getBlockState(feet).getBlock();
            if (block == Blocks.fire || block.getMaterial().isLiquid()) return false;
        }
        return true;
    }
}
