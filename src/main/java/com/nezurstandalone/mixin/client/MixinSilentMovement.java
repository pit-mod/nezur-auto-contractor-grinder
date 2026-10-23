package com.nezurstandalone.mixin.client;

import com.nezurstandalone.combat.ServerRotations;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Corrected inputs and local acceleration use the same yaw as the movement packet. */
@Mixin(Entity.class)
public abstract class MixinSilentMovement {
    @Redirect(method = {"moveFlying", "func_70060_a"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float movementYaw(Entity entity) { return ServerRotations.yaw(entity, entity.rotationYaw); }

    @Redirect(method = {"moveFlying", "func_70060_a"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;field_70177_z:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float movementYawSrg(Entity entity) { return ServerRotations.yaw(entity, entity.rotationYaw); }
}
