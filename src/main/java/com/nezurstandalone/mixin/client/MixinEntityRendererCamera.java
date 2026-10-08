package com.nezurstandalone.mixin.client;

import com.nezurstandalone.utils.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla consumes and applies physical mouse deltas first; automation never consumes them. */
@Mixin(EntityRenderer.class)
public class MixinEntityRendererCamera {
    @Inject(method={"updateCameraAndRender","func_181560_a"},at=@At("HEAD"),remap=false,require=1)
    private void beginLookFrame(float partialTicks,long finishTimeNano,CallbackInfo ci) {
        RotationManager.getInstance().beginCameraFrame();
    }

    @Inject(method={"updateCameraAndRender","func_181560_a"},at={
            @At(value="INVOKE",target="Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V",shift=At.Shift.AFTER),
            @At(value="INVOKE",target="Lnet/minecraft/client/entity/EntityPlayerSP;func_70082_c(FF)V",shift=At.Shift.AFTER)
    },remap=false,require=1)
    private void afterPhysicalLook(float partialTicks,long finishTimeNano,CallbackInfo ci) {
        Minecraft mc=Minecraft.getMinecraft();
        RotationManager.getInstance().afterMouseInput(mc.mouseHelper.deltaX,mc.mouseHelper.deltaY);
    }

    @Inject(method={"updateCameraAndRender","func_181560_a"},at=@At("TAIL"),remap=false,require=1)
    private void finishLookFrame(float partialTicks,long finishTimeNano,CallbackInfo ci) {
        RotationManager.getInstance().finishCameraFrame();
    }
}
