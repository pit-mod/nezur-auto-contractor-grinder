package com.nezurstandalone.control;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
public final class Lifecycle {
    private long token=Long.MIN_VALUE;
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;
        long now=ClientSession.current();
        if(token!=now){token=now;SessionResets.all();com.nezurstandalone.utils.NearestEggScan.allChunksChanged();net.minecraft.entity.boss.BossStatus.bossName=null;net.minecraft.entity.boss.BossStatus.statusBarTime=0;net.minecraft.entity.boss.BossStatus.healthScale=0;com.nezurstandalone.input.GuardedInput.clear();com.nezurstandalone.input.ClickSimulator.invalidate();com.nezurstandalone.utils.PitMapManager.reset();}
        InventoryOwner.refresh();
        MovementKeys.refresh();
        JumpController.refresh();
        CommandCoordinator.tick();
    }
}
