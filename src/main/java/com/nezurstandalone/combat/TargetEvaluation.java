package com.nezurstandalone.combat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
public final class TargetEvaluation {
    public final double health,absorption,multiplier,damage,distance,travel,combat,ttk;
    public final int armor,hits;
    public TargetEvaluation(EntityPlayer self,EntityPlayer target,double reach,double speed,double interval,double fallback){
        health=target.getHealth();absorption=target.getAbsorptionAmount();armor=target.getTotalArmorValue();
        double epf=0;for(ItemStack item:target.inventory.armorInventory)if(item!=null){int level=EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId,item);if(level>0)epf+=Math.floor((6+level*level)/3.0);}
        int resist=target.isPotionActive(Potion.resistance)?target.getActivePotionEffect(Potion.resistance).getAmplifier()+1:0;
        multiplier=TtkMath.multiplier(armor,Math.min(20,Math.min(25,epf)*.75),resist);
        // Vanilla attack attribute already includes held weapon and Strength/Weakness modifiers.
        double attack=self.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        attack+=EnchantmentHelper.getModifierForCreature(self.getHeldItem(),target.getCreatureAttribute());
        damage=Double.isFinite(attack)&&attack>0?attack:fallback;
        distance=self.getDistanceToEntity(target);hits=(int)Math.ceil((health+absorption)/multiplier/Math.max(.1,damage)-1e-9);
        travel=Math.max(0,distance-reach)/speed;combat=hits*interval;
        ttk=TtkMath.seconds(health+absorption,multiplier,damage,distance,reach,speed,interval,Math.max(0,target.hurtResistantTime-10)*.05);
    }
    public String toString(){return String.format(java.util.Locale.ROOT,"HP=%.1f absorption=%.1f armor=%d multiplier=%.3f damage=%.2f hits=%d distance=%.2f travel=%.2fs combat=%.2fs TTK=%.2fs",health,absorption,armor,multiplier,damage,hits,distance,travel,combat,ttk);}
}
