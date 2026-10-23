package com.nezurstandalone.mixin.client;

import com.nezurstandalone.input.GuardedInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftInput {
    @Shadow(remap = false, aliases = {"field_71429_W"}) private int leftClickCounter;
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
    private void hotbarPress(net.minecraft.client.settings.KeyBinding key,boolean pressed){
        Minecraft mc=Minecraft.getMinecraft();
        if(!pressed || mc.thePlayer==null || mc.currentScreen!=null)return;
        for(net.minecraft.client.settings.KeyBinding hotbar:mc.gameSettings.keyBindsHotbar)
            if(key==hotbar){manualSlotSelection();break;}
    }
    private void manualSlotSelection(){
        com.nezurstandalone.input.NativeActionGate.manualSlotSelection();GuardedInput.clear();
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("MANUAL_SLOT_SELECTION");
    }
    @Redirect(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/settings/KeyBinding;isPressed()Z"),remap=false,require=0)
    private boolean hotbarPressMcp(net.minecraft.client.settings.KeyBinding key){boolean pressed=key.isPressed();hotbarPress(key,pressed);return pressed;}
    @Redirect(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/settings/KeyBinding;func_151468_f()Z"),remap=false,require=0)
    private boolean hotbarPressSrg(net.minecraft.client.settings.KeyBinding key){boolean pressed=key.isPressed();hotbarPress(key,pressed);return pressed;}
    @Inject(method={"runTick","func_71407_l"},at={
            @At(value="INVOKE",target="Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"),
            @At(value="INVOKE",target="Lnet/minecraft/entity/player/InventoryPlayer;func_70453_c(I)V")
    },remap=false,require=1)
    private void scrollHotbar(CallbackInfo ci){manualSlotSelection();}
    @Inject(method={"runTick","func_71407_l"},at={
            @At(value="INVOKE",target="Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"),
            @At(value="INVOKE",target="Lnet/minecraft/client/renderer/EntityRenderer;func_78473_a(F)V")
    },remap=false,require=1)
    private void reconcileBeforeRaycast(CallbackInfo ci){}
    @Inject(method={"runTick","func_71407_l"},at=@At("HEAD"),remap=false)
    private void beginInputTick(CallbackInfo ci){raycastReady=false;automationPhaseDone=false;com.nezurstandalone.input.NativeActionGate.beginTick();com.nezurstandalone.engine.GrinderEngine.recordInputPhase("TICK_START");}
    @Inject(method={"clickMouse","func_147116_af"},at=@At("HEAD"),cancellable=true,remap=false)
    private void nativeAttack(CallbackInfo ci){
        if(!com.nezurstandalone.input.NativeActionGate.allow(true)){ci.cancel();return;}
        Minecraft mc=Minecraft.getMinecraft();
        if(com.nezurstandalone.combat.GrinderCombat.blocksMining() && mc.objectMouseOver!=null
                && mc.objectMouseOver.typeOfHit==net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK){ci.cancel();return;}
        if(mc.thePlayer!=null && mc.objectMouseOver!=null
                && mc.objectMouseOver.typeOfHit==net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY)
            com.nezurstandalone.control.CombatPacketAudit.onBegin(mc.thePlayer.ticksExisted,
                    com.nezurstandalone.control.ClientSession.current(),com.nezurstandalone.input.NativeActionGate.isAutomation());
        // A prior air swing's ten-tick miss delay must not suppress a validated server entity click.
        if(com.nezurstandalone.input.NativeActionGate.isAutomation() && com.nezurstandalone.combat.ServerRotations.attackActive()
                && mc.objectMouseOver!=null && mc.objectMouseOver.typeOfHit==net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY)
            leftClickCounter=0;
    }
    @Inject(method={"clickMouse","func_147116_af"},at=@At("RETURN"),remap=false)
    private void nativeAttackFinished(CallbackInfo ci){com.nezurstandalone.control.CombatPacketAudit.onEnd();}
    @Inject(method={"rightClickMouse","func_147121_ag"},at=@At("HEAD"),cancellable=true,remap=false)
    private void nativeUse(CallbackInfo ci){if(!com.nezurstandalone.input.NativeActionGate.allow(false))ci.cancel();}

    @Inject(method={"sendClickBlockToController","func_147115_a"},at=@At("HEAD"),cancellable=true,remap=false)
    private void ownedBlockClick(boolean held,CallbackInfo ci) {
        if(held && (com.nezurstandalone.combat.GrinderCombat.blocksMining()
                || com.nezurstandalone.input.NativeActionGate.priorityItemUseAllowed.getAsBoolean()
                || com.nezurstandalone.input.NativeActionGate.attackKeyOwner.getAsBoolean()
                || !com.nezurstandalone.input.NativeActionGate.cameraAttackAllowed.getAsBoolean())) ci.cancel();
    }

    private boolean automationPhaseDone;
    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/WorldClient;updateEntities()V",shift=At.Shift.BEFORE),remap=false,require=0)
    private void beforeEntitiesMcp(CallbackInfo ci){beforeEntities();}
    @Inject(method={"runTick","func_71407_l"},at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/WorldClient;func_72939_s()V",shift=At.Shift.BEFORE),remap=false,require=0)
    private void beforeEntitiesSrg(CallbackInfo ci){beforeEntities();}
    private void beforeEntities(){
        if(automationPhaseDone)return;
        automationPhaseDone=true;
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("BEFORE_SYNTHETIC_INPUT");
        if(raycastReady){
            Minecraft.getMinecraft().entityRenderer.getMouseOver(1.0F);
            com.nezurstandalone.combat.GrinderCombat.prepareNativeInput();
            com.nezurstandalone.combat.ServerRotations.prepareNativeInput();
            Minecraft mc=Minecraft.getMinecraft();
            if(com.nezurstandalone.input.NativeActionGate.priorityItemUseAllowed.getAsBoolean()
                    && mc.playerController!=null && mc.playerController.getIsHittingBlock())
                mc.playerController.resetBlockRemoving();
            dispatch();
            // Vanilla attacks/swing animations precede this tick's movement packet.
            // Use the last reported server ray, not next-frame aim, in the same input phase.
            dispatchServer();
        }
        com.nezurstandalone.engine.GrinderEngine.recordInputPhase("AFTER_SYNTHETIC_INPUT");
    }
    @Inject(method={"runTick","func_71407_l"},at=@At("RETURN"),remap=false)
    private void afterPhysicalQueue(CallbackInfo ci){
        // Cleanup only: issuing an attack here would put it after the movement packet.
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
        com.nezurstandalone.input.NativeActionGate.automation(() -> GuardedInput.dispatchCamera(() -> clickMouse(), () -> rightClickMouse()));
    }

    private void dispatchServer() {
        GuardedInput.dispatchPermit = com.nezurstandalone.input.NativeActionGate::available;
        if(com.nezurstandalone.input.NativeActionGate.manual()){GuardedInput.clear();return;}
        com.nezurstandalone.input.NativeActionGate.automation(() -> GuardedInput.dispatchServer(() -> clickMouse(), () -> rightClickMouse()));
    }
}
