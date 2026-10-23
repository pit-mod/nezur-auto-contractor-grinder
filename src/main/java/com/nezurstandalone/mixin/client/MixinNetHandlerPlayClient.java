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
        if(!loadoutCurrentHandler())return;
        if(!packet.func_148888_e())com.nezurstandalone.control.GrinderDiagnostics.captureRoutine("TRANSACTION_REJECTED","window="+packet.getWindowId()+" action="+packet.getActionNumber());
        com.nezurstandalone.module.impl.player.AutoContractor.confirmTransaction(packet.getWindowId(),packet.getActionNumber(),packet.func_148888_e());
        com.nezurstandalone.input.GuardedWindowClick.confirm(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
        com.nezurstandalone.input.WindowActions.confirm(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
        com.nezurstandalone.engine.GrinderEngine.onMenuConfirmation(packet.getWindowId(), packet.getActionNumber(), packet.func_148888_e());
    }

    @Inject(method = {"handleWindowItems", "func_147241_a"}, at = @At("RETURN"), remap = false)
    private void afterWindowItems(net.minecraft.network.play.server.S30PacketWindowItems packet, CallbackInfo ci) {
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.input.GuardedWindowClick.contents(packet.func_148911_c());
        if(Minecraft.getMinecraft().thePlayer!=null)com.nezurstandalone.engine.GrinderEngine.onMenuContents(packet.func_148911_c(),Minecraft.getMinecraft().thePlayer.openContainer);
        net.minecraft.item.ItemStack[] stacks=packet.getItemStacks();
        if(Minecraft.getMinecraft().thePlayer!=null)com.nezurstandalone.engine.GrinderEngine.onLoadoutContents(packet.func_148911_c(),Minecraft.getMinecraft().thePlayer.openContainer,stacks);
        for(int i=0;i<stacks.length;i++){com.nezurstandalone.input.InventoryEvidence.slot(packet.func_148911_c(),i,stacks[i]);com.nezurstandalone.input.WindowActions.slot(packet.func_148911_c(),i,stacks[i]);}
        com.nezurstandalone.module.impl.player.AutoContractor.serverInventory(packet.func_148911_c());
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
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.onLoadoutSlot(p.func_149175_c(),p.func_149173_d(),p.func_149174_e());
        com.nezurstandalone.input.InventoryEvidence.slot(p.func_149175_c(),p.func_149173_d(),p.func_149174_e());
        com.nezurstandalone.input.WindowActions.slot(p.func_149175_c(),p.func_149173_d(),p.func_149174_e());
        com.nezurstandalone.module.impl.player.AutoContractor.serverInventory(p.func_149175_c());
    }

    /** Save current yaw/pitch just before vanilla overwrites them from the packet. */
    @Inject(method = {"handlePlayerPosLook", "func_147258_a"}, at = @At("HEAD"), remap = false)
    private void beforeHandlePlayerPosLook(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.recordCorrection(packet, true);
        com.nezurstandalone.control.GrinderDiagnostics.record("SERVER_CORRECTION","raw="+packet.getX()+","+packet.getY()+","+packet.getZ()
                +" rotation="+packet.getYaw()+","+packet.getPitch()+" relative="+packet.func_179834_f()+" cause=unknown");

    }

    /** Restore saved yaw/pitch immediately after vanilla sets them from the packet. */
    @Inject(method = {"handlePlayerPosLook", "func_147258_a"}, at = @At("RETURN"), remap = false)
    private void afterHandlePlayerPosLook(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.recordCorrection(packet, false);
        com.nezurstandalone.control.AutomationSafety.corrected();
        if (com.nezurstandalone.module.impl.player.AutoGrinder.isRunning()) {
            // Native handling and its acknowledgment have completed. Accept the correction
            // and discard automation based on the previous position/rotation.
            com.nezurstandalone.module.impl.player.AutoGrinder.onServerCorrection();
            return;
        }

    }

    @Inject(method={"addToSendQueue","func_147297_a"},at=@At("HEAD"),remap=false)
    private void traceOutbound(net.minecraft.network.Packet packet,CallbackInfo ci){
        if(Minecraft.getMinecraft().isCallingFromMinecraftThread()){
            com.nezurstandalone.engine.GrinderEngine.recordOutbound(packet);
            if(packet instanceof net.minecraft.network.play.client.C0APacketAnimation)com.nezurstandalone.control.CombatPacketAudit.onSwing();
            if(packet instanceof net.minecraft.network.play.client.C02PacketUseEntity&&((net.minecraft.network.play.client.C02PacketUseEntity)packet).getAction()==net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK){
                com.nezurstandalone.control.CombatPacketAudit.onAttack();com.nezurstandalone.input.NativeActionGate.attackPacket();
                if(loadoutCurrentHandler()&&Minecraft.getMinecraft().theWorld!=null)
                    com.nezurstandalone.engine.GrinderEngine.serverAttackSent(((net.minecraft.network.play.client.C02PacketUseEntity)packet).getEntityFromWorld(Minecraft.getMinecraft().theWorld));
            }
        }
    }
    @Inject(method={"handleEntityVelocity","func_147244_a"},at=@At("HEAD"),remap=false)
    private void traceVelocityBefore(net.minecraft.network.play.server.S12PacketEntityVelocity packet,CallbackInfo ci){
        if(Minecraft.getMinecraft().isCallingFromMinecraftThread())com.nezurstandalone.engine.GrinderEngine.recordVelocity(packet,true);
    }
    @Inject(method={"handleEntityVelocity","func_147244_a"},at=@At("RETURN"),remap=false)
    private void traceVelocityAfter(net.minecraft.network.play.server.S12PacketEntityVelocity packet,CallbackInfo ci){
        com.nezurstandalone.engine.GrinderEngine.recordVelocity(packet,false);
    }

    /** Late packets from a replaced connection cannot teach the new account/profile cache. */
    private boolean loadoutCurrentHandler(){
        Minecraft client=Minecraft.getMinecraft();
        return client.isCallingFromMinecraftThread() && (Object)client.getNetHandler()==this;
    }
    @Inject(method={"handleTeams","func_147247_a"},at=@At("RETURN"),remap=false)
    private void loadoutTeams(net.minecraft.network.play.server.S3EPacketTeams p,CallbackInfo ci){
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.onLoadoutScoreboard();
    }
    @Inject(method={"handleUpdateScore","func_147250_a"},at=@At("RETURN"),remap=false)
    private void loadoutScore(net.minecraft.network.play.server.S3CPacketUpdateScore p,CallbackInfo ci){
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.onLoadoutScoreboard();
    }
    @Inject(method={"handleScoreboardObjective","func_147291_a"},at=@At("RETURN"),remap=false)
    private void loadoutObjective(net.minecraft.network.play.server.S3BPacketScoreboardObjective p,CallbackInfo ci){
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.onLoadoutScoreboard();
    }
    @Inject(method={"handleDisplayScoreboard","func_147254_a"},at=@At("RETURN"),remap=false)
    private void loadoutDisplay(net.minecraft.network.play.server.S3DPacketDisplayScoreboard p,CallbackInfo ci){
        if(!loadoutCurrentHandler())return;
        com.nezurstandalone.engine.GrinderEngine.onLoadoutScoreboard();
    }

    @Inject(method={"handleEntityStatus","func_147236_a"},at=@At("RETURN"),remap=false)
    private void grinderEntityStatus(net.minecraft.network.play.server.S19PacketEntityStatus packet,CallbackInfo ci){
        if(!loadoutCurrentHandler()||Minecraft.getMinecraft().theWorld==null)return;
        net.minecraft.entity.Entity entity=packet.getEntity(Minecraft.getMinecraft().theWorld);
        if(packet.getOpCode()==2)com.nezurstandalone.engine.GrinderEngine.serverHurtObserved(entity);
        else if(packet.getOpCode()==3)com.nezurstandalone.engine.GrinderEngine.serverDeathObserved(entity);
    }

}
