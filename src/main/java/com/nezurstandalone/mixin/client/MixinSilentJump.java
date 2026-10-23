package com.nezurstandalone.mixin.client;

import com.nezurstandalone.combat.ServerRotations;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityLivingBase.class)
public abstract class MixinSilentJump {
    @Redirect(method = {"jump", "func_70664_aZ"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float jumpYaw(EntityLivingBase entity) { return ServerRotations.yaw(entity, entity.rotationYaw); }

    @Redirect(method = {"jump", "func_70664_aZ"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;field_70177_z:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float jumpYawSrg(EntityLivingBase entity) { return ServerRotations.yaw(entity, entity.rotationYaw); }
}
