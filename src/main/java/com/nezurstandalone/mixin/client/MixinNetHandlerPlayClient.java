package com.nezurstandalone.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Inject(method = {"handleConfirmTransaction", "func_147239_a"}, at = @At("RETURN"), remap = false)
    private void afterTransaction(net.minecraft.network.play.server.S32PacketConfirmTransaction packet, CallbackInfo ci) {
        com.nezurstandalone.input.GuardedWindowClick.confirm(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
        com.nezurstandalone.input.WindowActions.confirm(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
        com.nezurstandalone.engine.GrinderEngine.onMenuConfirmation(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
    }

    @Inject(method = {"handleWindowItems", "func_147241_a"}, at = @At("RETURN"), remap = false)
    private void afterWindowItems(net.minecraft.network.play.server.S30PacketWindowItems packet, CallbackInfo ci) {
        com.nezurstandalone.input.GuardedWindowClick.contents(packet.func_148911_c());
        if(Minecraft.getMinecraft().thePlayer!=null)com.nezurstandalone.engine.GrinderEngine.onMenuContents(packet.func_148911_c(),Minecraft.getMinecraft().thePlayer.openContainer);
        net.minecraft.item.ItemStack[] stacks=packet.getItemStacks();
        for(int i=0;i<stacks.length;i++){com.nezurstandalone.input.InventoryEvidence.slot(packet.func_148911_c(),i,stacks[i]);com.nezurstandalone.input.WindowActions.slot(packet.func_148911_c(),i,stacks[i]);}
    }

    @Inject(method={"handleChunkData","func_147263_a"},at=@At("RETURN"),remap=false)
    private void chunkChanged(net.minecraft.network.play.server.S21PacketChunkData p,CallbackInfo ci){
        com.nezurstandalone.utils.NearestEggScan.chunkChanged(p.getChunkX(),p.getChunkZ());
        com.nezurstandalone.pathfinder.WorldCapture.changed(Minecraft.getMinecraft().theWorld,p.getChunkX()*16-1,0,p.getChunkZ()*16-1,p.getChunkX()*16+16,255,p.getChunkZ()*16+16);
    }
    @Inject(method={"handleMapChunkBulk","func_147269_a"},at=@At("RETURN"),remap=false)
    private void chunksChanged(net.minecraft.network.play.server.S26PacketMapChunkBulk p,CallbackInfo ci){
        com.nezurstandalone.utils.NearestEggScan.allChunksChanged();
        com.nezurstandalone.pathfinder.WorldCapture.changed(Minecraft.getMinecraft().theWorld,Integer.MIN_VALUE,0,Integer.MIN_VALUE,Integer.MAX_VALUE,255,Integer.MAX_VALUE);
    }
    @Inject(method={"handleSetSlot","func_147266_a"},at=@At("RETURN"),remap=false)
    private void authoritativeSlot(net.minecraft.network.play.server.S2FPacketSetSlot p,CallbackInfo ci){
        com.nezurstandalone.input.InventoryEvidence.slot(p.func_149175_c(),p.func_149173_d(),p.func_149174_e());
        com.nezurstandalone.input.WindowActions.slot(p.func_149175_c(),p.func_149173_d(),p.func_149174_e());
    }
    /** Restore saved yaw/pitch immediately after vanilla sets them from the packet. */
    @Inject(method = {"handlePlayerPosLook", "func_147258_a"}, at = @At("RETURN"), remap = false)
    private void afterHandlePlayerPosLook(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        com.nezurstandalone.engine.GrinderEngine.recordCorrection(packet, false);
        com.nezurstandalone.control.ClientSession.invalidate();
        com.nezurstandalone.module.impl.player.AutoHeal.interrupt();
        com.nezurstandalone.control.InventoryOwner.refresh();
        if (com.nezurstandalone.module.impl.player.AutoGrinder.isRunning()) {
            // Native handling and its acknowledgment have completed. Accept the correction
            // and discard automation based on the previous position/rotation.
            com.nezurstandalone.module.impl.player.AutoGrinder.onServerCorrection();
            return;
        }
    }
}
