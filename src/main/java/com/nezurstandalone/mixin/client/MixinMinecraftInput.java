package com.nezurstandalone.mixin.client;

import com.nezurstandalone.input.GuardedInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftInput {
    @Shadow(remap = false, aliases = {"func_147116_af"})
    private void clickMouse() { throw new AssertionError(); }

    @Shadow(remap = false, aliases = {"func_147121_ag"})
    private void rightClickMouse() { throw new AssertionError(); }

    // Explicit MCP and SRG targets match the project's remap=false loader convention.
    // If neither target exists, no intent is consumed; there is no unguarded fallback.
    @Inject(method = {"runTick", "func_71407_l"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V",
            shift = At.Shift.AFTER), remap = false, require = 0)
    private void afterRaycastMcp(CallbackInfo ci) { raycastReady=true; }

    @Inject(method = {"runTick", "func_71407_l"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78473_a(F)V",
            shift = At.Shift.AFTER), remap = false, require = 0)
    private void afterRaycastSrg(CallbackInfo ci) { raycastReady=true; }

    private boolean raycastReady;
    @Inject(method={"runTick","func_71407_l"},at=@At("HEAD"),remap=false)
    private void beginInputTick(CallbackInfo ci){raycastReady=false;automationPhaseDone=false;com.nezurstandalone.input.NativeActionGate.beginTick();com.nezurstandalone.engine.GrinderEngine.recordInputPhase("TICK_START");}
    @Inject(method={"clickMouse","func_147116_af"},at=@At("HEAD"),cancellable=true,remap=false)
    private void nativeAttack(CallbackInfo ci){if(!com.nezurstandalone.input.NativeActionGate.allow(true))ci.cancel();}
    @Inject(method={"rightClickMouse","func_147121_ag"},at=@At("HEAD"),cancellable=true,remap=false)
    private void nativeUse(CallbackInfo ci){if(!com.nezurstandalone.input.NativeActionGate.allow(false))ci.cancel();}

    private boolean automationPhaseDone;
    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/WorldClient;updateEntities()V",shift=At.Shift.BEFORE),remap=false,require=0)
    private void beforeEntitiesMcp(CallbackInfo ci){beforeEntities();}
    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/WorldClient;func_72939_s()V",shift=At.Shift.BEFORE),remap=false,require=0)
    private void beforeEntitiesSrg(CallbackInfo ci){beforeEntities();}
    private void beforeEntities(){
        if(automationPhaseDone)return;
        automationPhaseDone=true;
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("BEFORE_SYNTHETIC_INPUT");
        if(raycastReady){Minecraft.getMinecraft().entityRenderer.getMouseOver(1.0F);dispatch();}
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("AFTER_SYNTHETIC_INPUT");
    }
    @Inject(method={"runTick","func_71407_l"},at=@At("RETURN"),remap=false)
    private void afterPhysicalQueue(CallbackInfo ci){
        // No late fallback: never send a synthetic action after this tick's movement update.
        if(!automationPhaseDone){GuardedInput.clear();com.nezurstandalone.engine.GrinderEngine.recordInputPhase("INPUT_PHASE_NOT_REACHED");}
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("TICK_END");
    }

    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/settings/KeyBinding;onTick(I)V"),remap=false,require=0)
    private void physicalPressMcp(CallbackInfo ci){com.nezurstandalone.input.NativeActionGate.physicalInput();}
    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/settings/KeyBinding;func_74507_a(I)V"),remap=false,require=0)
    private void physicalPressSrg(CallbackInfo ci){com.nezurstandalone.input.NativeActionGate.physicalInput();}
    private void dispatch() {
        GuardedInput.dispatchPermit = com.nezurstandalone.input.NativeActionGate::available;
        if(com.nezurstandalone.input.NativeActionGate.manual()){GuardedInput.clear();return;}
        // Mixin 0.7 remaps shadow aliases in instructions, but not direct method-reference handles.
        com.nezurstandalone.input.NativeActionGate.automation(() -> GuardedInput.dispatch(() -> clickMouse(), () -> rightClickMouse()));
    }
}
