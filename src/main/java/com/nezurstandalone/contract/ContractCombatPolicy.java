package com.nezurstandalone.contract;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

/** Temporary combat constraint installed only while AutoContractor owns an objective. */
public final class ContractCombatPolicy {
    private static ContractOffer.Type type;
    private static Object world;
    private static long sneakSince;
    private static long sneakDelayMs = 100;
    private static double sneakRadius = 3.5;

    private ContractCombatPolicy() { }

    public static void install(ContractOffer.Type objective, long sneakDelay, double radius) {
        clear();
        type = objective;
        world = Minecraft.getMinecraft().theWorld;
        sneakDelayMs = Math.max(0, sneakDelay);
        sneakRadius = Math.max(1, Math.min(3.5, radius));
    }

    public static void clear() {
        Minecraft mc = Minecraft.getMinecraft();
        if (type == ContractOffer.Type.SNEAK_ATTACK_KILLS && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),
                    com.nezurstandalone.input.NativeActionGate.physical(mc.gameSettings.keyBindSneak.getKeyCode()));
        }
        type = null;
        world = null;
        sneakSince = 0;
    }

    public static ContractOffer.Type type() {
        return type;
    }

    public static boolean allowsTarget(EntityPlayer target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (type == null) return true;
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null || target == null) return false;
        if (type == ContractOffer.Type.COLLECT_GOLD_INGOTS || type == ContractOffer.Type.GOLDEN_APPLES) return false;
        if (type == ContractOffer.Type.CLAIM_BOUNTIES) return hasBountyMarker(target);
        if (type == ContractOffer.Type.FIST_MID_KILLS) {
            return "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(target.posX, target.posY, target.posZ))
                    && "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(
                            mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        }
        return true;
    }

    /** Release stale automated sneak every tick/frame, even when no attack is attempted. */
    public static void updateSneak() {
        if (type != ContractOffer.Type.SNEAK_ATTACK_KILLS) return;
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (!sneakHitReady(hit == null ? null : hit.entityHit)) releaseSneak();
    }

    private static boolean sneakHitReady(Entity intended) {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        return mc.theWorld != null && mc.theWorld == world && mc.thePlayer != null
                && mc.gameSettings != null && mc.currentScreen == null
                && !mc.thePlayer.isDead && mc.thePlayer.getHealth() > 0 && !mc.thePlayer.isUsingItem()
                && intended instanceof EntityPlayer && intended != mc.thePlayer
                && !intended.isDead && intended.worldObj == mc.theWorld
                && ((EntityPlayer) intended).getHealth() > 0
                && mc.thePlayer.getDistanceToEntity(intended) <= sneakRadius
                && "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(
                        mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))
                && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit == intended && hit.hitVec != null
                && mc.thePlayer.getPositionEyes(1.0F).distanceTo(hit.hitVec) <= sneakRadius;
    }

    public static boolean allowsAttack(Entity intended) {
        Minecraft mc = Minecraft.getMinecraft();
        if (type == null) return true;
        if (type == ContractOffer.Type.SNEAK_ATTACK_KILLS && !sneakHitReady(intended)) {
            releaseSneak();
            return false;
        }
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null
                || !(intended instanceof EntityPlayer)) return false;
        if (!allowsTarget((EntityPlayer) intended)) return false;

        switch (type) {
            case FIST_MID_KILLS:
                return mc.thePlayer.getHeldItem() == null;
            case DIAMOND_SWORD_FINAL_BLOW:
                ItemStack held = mc.thePlayer.getHeldItem();
                return held != null && Item.getIdFromItem(held.getItem()) == 276;
            case NO_ARMOR_KILLS:
                for (ItemStack armor : mc.thePlayer.inventory.armorInventory) {
                    if (armor != null) return false;
                }
                return true;
            case NO_PERK_KILLS:
                com.nezurstandalone.module.impl.player.AutoGrinder grinder = com.nezurstandalone.Nezur.moduleManager == null ? null
                        : com.nezurstandalone.Nezur.moduleManager.getModuleByClass(
                                com.nezurstandalone.module.impl.player.AutoGrinder.class);
                return grinder != null && grinder.isTemporaryNoPerkReady();
            case SNEAK_ATTACK_KILLS:
                if (mc.thePlayer.getDistanceToEntity(intended) > sneakRadius) {
                    releaseSneak();
                    return false;
                }
                if (sneakSince == 0) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                    sneakSince = com.nezurstandalone.control.Clock.millis();
                    return false;
                }
                return mc.thePlayer.isSneaking()
                        && com.nezurstandalone.control.Clock.millis() - sneakSince >= sneakDelayMs;
            default:
                return true;
        }
    }

    /** Extra gate for the grinder's region-scoped crosshair clicker. */
    public static boolean allowsCrosshairAttack() {
        // Ordinary kills use the same continuous native Pit clicker as free grinding.
        // Special objectives retain their equipment/target restrictions below.
        if (type == null || type == ContractOffer.Type.KILL_PLAYERS
                || type == ContractOffer.Type.CHAIN_KILLS || type == ContractOffer.Type.KILL_STREAK) return true;
        if(type==ContractOffer.Type.NO_PERK_KILLS) {
            com.nezurstandalone.module.impl.player.AutoGrinder g=com.nezurstandalone.Nezur.moduleManager==null?null:
                    com.nezurstandalone.Nezur.moduleManager.getModuleByClass(com.nezurstandalone.module.impl.player.AutoGrinder.class);
            return g!=null && g.isTemporaryNoPerkReady();
        }
        if (type == ContractOffer.Type.SNEAK_ATTACK_KILLS) {
            Minecraft mc = Minecraft.getMinecraft();
            MovingObjectPosition hit = mc.objectMouseOver;
            return allowsAttack(hit == null ? null : hit.entityHit);
        }
        Minecraft client=Minecraft.getMinecraft();
        if(type==ContractOffer.Type.NO_ARMOR_KILLS && client.thePlayer!=null){
            for(ItemStack item:client.thePlayer.inventory.armorInventory)if(item!=null)return false;
            return true;
        }
        if(type==ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW && client.thePlayer!=null){
            ItemStack held=client.thePlayer.getHeldItem();return held!=null && Item.getIdFromItem(held.getItem())==276;
        }
        if(type==ContractOffer.Type.FIST_MID_KILLS && client.thePlayer!=null)return client.thePlayer.getHeldItem()==null;
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        return hit != null && hit.entityHit != null && allowsAttack(hit.entityHit);
    }

    public static void noTarget() {
        if (type == ContractOffer.Type.SNEAK_ATTACK_KILLS) releaseSneak();
    }

    public static double targetBias(EntityPlayer target) {
        if (type != ContractOffer.Type.CHAIN_KILLS || target == null || target.worldObj == null) return 0;
        int nearby = 0;
        for (EntityPlayer player : target.worldObj.playerEntities) {
            if (player != target && !player.isDead && player.getDistanceToEntity(target) < 5.0) nearby++;
        }
        return -Math.min(nearby, 6) * 0.3;
    }

    private static void releaseSneak() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) {
            int key = mc.gameSettings.keyBindSneak.getKeyCode();
            KeyBinding.setKeyBindState(key, com.nezurstandalone.input.NativeActionGate.physical(key));
        }
        sneakSince = 0;
    }

    private static boolean hasBountyMarker(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityArmorStand) || !entity.hasCustomName()
                    || entity.getDistanceToEntity(player) > 2.5) continue;
            String name = net.minecraft.util.StringUtils.stripControlCodes(entity.getCustomNameTag());
            if (name.matches("(?i).*\\b[1-9][\\d,]*g\\b.*")) return true;
        }
        return false;
    }
}
