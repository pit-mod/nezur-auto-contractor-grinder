package com.nezurstandalone.mixin.client;
import net.minecraft.world.World;
import net.minecraft.util.BlockPos;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(World.class)
public class MixinWorldRevision {
    @Inject(method={"setBlockState(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z","func_180501_a(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"},at=@At("RETURN"),remap=false)
    private void changed(BlockPos p,IBlockState state,int flags,CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValue()){
            com.nezurstandalone.pathfinder.WorldCapture.changed(this,p.getX()-1,p.getY()-1,p.getZ()-1,p.getX()+1,p.getY()+1,p.getZ()+1);
            if((Object)this==net.minecraft.client.Minecraft.getMinecraft().theWorld)com.nezurstandalone.utils.NearestEggScan.blockChanged(this,p.getX(),p.getY(),p.getZ());
        }
    }
}
