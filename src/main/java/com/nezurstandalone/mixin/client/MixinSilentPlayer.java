package com.nezurstandalone.mixin.client;

import com.nezurstandalone.combat.ServerRotations;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Override reads in vanilla's movement packet builder, never the player's camera fields. */
@Mixin(EntityPlayerSP.class)
public abstract class MixinSilentPlayer {
    @Shadow(remap = false, aliases = {"field_175164_bL"}) private float lastReportedYaw;
    @Shadow(remap = false, aliases = {"field_175165_bM"}) private float lastReportedPitch;

    @Inject(method = {"onUpdate", "func_70071_h_"}, at = @At("HEAD"), remap = false)
    private void freezeServerRotation(CallbackInfo ci) { ServerRotations.beginTick(); }

    @Redirect(method = {"onUpdateWalkingPlayer", "func_175161_p"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;rotationYaw:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float packetYaw(EntityPlayerSP player) { return ServerRotations.yaw(player, player.rotationYaw); }

    @Redirect(method = {"onUpdateWalkingPlayer", "func_175161_p"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;field_70177_z:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float packetYawSrg(EntityPlayerSP player) { return ServerRotations.yaw(player, player.rotationYaw); }

    @Redirect(method = {"onUpdateWalkingPlayer", "func_175161_p"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;rotationPitch:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float packetPitch(EntityPlayerSP player) { return ServerRotations.pitch(player, player.rotationPitch); }

    @Redirect(method = {"onUpdateWalkingPlayer", "func_175161_p"}, at =
            @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;field_70125_A:F", opcode = Opcodes.GETFIELD), remap = false, require = 0)
    private float packetPitchSrg(EntityPlayerSP player) { return ServerRotations.pitch(player, player.rotationPitch); }

    @Inject(method = {"onUpdateWalkingPlayer", "func_175161_p"}, at = @At("RETURN"), remap = false)
    private void rotationReported(CallbackInfo ci) { ServerRotations.report(lastReportedYaw, lastReportedPitch); }

    @Inject(method = {"onLivingUpdate", "func_70636_d"}, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/util/MovementInput;func_78898_a()V", shift = At.Shift.AFTER)
    }, remap = false, require = 1)
    private void fixMovementInput(CallbackInfo ci) {
        ServerRotations.correctMovement();
    }
}
