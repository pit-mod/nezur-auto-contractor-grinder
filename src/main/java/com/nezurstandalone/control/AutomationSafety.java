package com.nezurstandalone.control;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.input.GuardedInput;
import com.nezurstandalone.input.NativeActionGate;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.module.impl.player.*;
import com.nezurstandalone.pathfinder.PathfinderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Small adapter for the existing control leases at correction and manual-input boundaries. */
public final class AutomationSafety {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final CorrectionWindow corrections=new CorrectionWindow();
    private static boolean initialized;

    public static void init(){
        if(initialized)return;initialized=true;
        NativeActionGate.automationAllowed=AutomationSafety::allowed;
        NativeActionGate.combatOperationAllowed=()->{
            AutoContractor contractor=module(AutoContractor.class);
            return contractor==null || !contractor.ownsExclusiveOperation();
        };
        MovementKeys.movementAllowed=JumpController.movementAllowed=PathfinderManager.movementAllowed=
                ()->allowed()&&!manualMovement();
        PathfinderManager.navigationAllowed=AutomationSafety::allowed;
        InventoryOwner.operationAllowed=owner->allowed()&&GuiLease.available(owner)
                &&NativeActionGate.inventoryTransactionAllowed.getAsBoolean();
        GuiLease.operationAllowed=InventoryOwner::available;
        GuardedInput.diagnostic=GrinderDiagnostics::record;
        CommandCoordinator.operationAllowed=AutomationSafety::allowed;
        com.nezurstandalone.utils.RotationManager.cameraAutomationAllowed=AutomationSafety::allowed;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new AutomationSafety());
    }

    public static boolean allowed(){
        corrections.context(MC.theWorld,MC.thePlayer);
        return MC.thePlayer!=null && MC.theWorld!=null
                && corrections.allowed(Clock.SYSTEM.nanos(),MC.thePlayer.ticksExisted);
    }

    public static boolean manualMovement(){
        if(MC.gameSettings==null)return false;
        for(KeyBinding key:new KeyBinding[]{MC.gameSettings.keyBindForward,MC.gameSettings.keyBindBack,
                MC.gameSettings.keyBindLeft,MC.gameSettings.keyBindRight,MC.gameSettings.keyBindJump,
                MC.gameSettings.keyBindSneak,MC.gameSettings.keyBindSprint})
            if(NativeActionGate.physical(key.getKeyCode()))return true;
        return false;
    }

    /** Called only after the current handler's vanilla correction and acknowledgement complete. */
    public static void corrected(){
        if(!MC.isCallingFromMinecraftThread() || MC.thePlayer==null)return;
        corrections.context(MC.theWorld,MC.thePlayer);
        boolean pendingInventory=com.nezurstandalone.input.WindowActions.anyPending() || AutoGrinder.hasUnconfirmedOperation()
                || AutoContractor.hasUnconfirmedOperation();
        corrections.corrected(Clock.SYSTEM.nanos(),MC.thePlayer.ticksExisted);
        snapshot("CORRECTION_CONTEXT");
        GrinderDiagnostics.capture("CORRECTION_INTERRUPTION","unconfirmedInventory="+pendingInventory+" cause=unknown");
        // Restore a valid slot before invalidating its generation; never restore an old world/player.
        AutoHeal.interrupt();
        InventoryOwner.authorizationLost();
        ClientSession.invalidate();
        SessionResets.all();
        GuardedInput.clear();
        MovementKeys.authorizationLost();JumpController.authorizationLost();
        PathfinderManager.authorizationLost();
        com.nezurstandalone.combat.ServerRotations.reset();
        com.nezurstandalone.utils.RotationManager.getInstance().clearAll();
        if(pendingInventory){
            for(Module module:Nezur.moduleManager.getModules())
                if(affected(module)&&module.isToggled())module.setToggled(false);
            com.nezurstandalone.utils.NotificationManager.showInChat("[Nezur] Automation stopped: a server correction interrupted an unconfirmed inventory action. "
                    +"Inspect inventory, local diagnostics and server logs before restarting; correction cause is unknown.");
        }
    }

    private static boolean affected(Module m){
        return m instanceof AutoGrinder || m instanceof AutoContractor || m instanceof AutoHeal;
    }
    public static boolean activeAutomation(){
        if(Nezur.moduleManager==null)return false;
        for(Module m:Nezur.moduleManager.getModules())if(affected(m)&&m.isToggled())return true;
        return false;
    }
    private static <T extends Module> T module(Class<T> type){return Nezur.moduleManager==null?null:Nezur.moduleManager.getModuleByClass(type);}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.START)return;
        if(!allowed() || manualMovement()){
            MovementKeys.authorizationLost();JumpController.authorizationLost();
            if(!allowed() || PathfinderManager.ownsMovement())PathfinderManager.authorizationLost();
        }
        if(GrinderDiagnostics.sampleDue())snapshot("ACTION_SAMPLE");
    }

    public static void snapshot(String event){
        if(!GrinderDiagnostics.isActive() || MC.thePlayer==null)return;
        net.minecraft.client.entity.EntityPlayerSP p=MC.thePlayer;
        StringBuilder modules=new StringBuilder();
        if(Nezur.moduleManager!=null)for(Module m:Nezur.moduleManager.getModules())
            if(m.isToggled()&&affected(m))modules.append(m.getName()).append(',');
        net.minecraft.entity.Entity target=com.nezurstandalone.combat.GrinderCombat.currentTarget();
        AutoContractor contractor=module(AutoContractor.class);
        GrinderDiagnostics.record(event,"tick="+p.ticksExisted+" session="+ClientSession.current()
                +" modules="+modules+" contractor="+(contractor==null?"none":contractor.diagnosticState())
                +" grinder="+AutoGrinder.diagnosticState()+" heal="+AutoHeal.diagnosticState()
                +" rotationOwner="+com.nezurstandalone.combat.ServerRotations.ownerName()
                +" inventoryOwner="+InventoryOwner.ownerName()+" guiOwner="+GuiLease.ownerName()
                +" navigationOwner="+PathfinderManager.ownerName()+" movement="+MovementKeys.owners()+" jumpOwner="+JumpController.ownerName()
                +" slot="+p.inventory.currentItem+" item="+(p.getHeldItem()==null?-1:net.minecraft.item.Item.getIdFromItem(p.getHeldItem().getItem()))
                +" using="+p.isUsingItem()+" gui="+(MC.currentScreen==null?"none":MC.currentScreen.getClass().getSimpleName())
                +" window="+(p.openContainer==null?-1:p.openContainer.windowId)
                +" target="+(target==null?-1:target.getEntityId())+" distance="+(target==null?-1:p.getDistanceToEntity(target))
                +" visible="+(target!=null&&p.canEntityBeSeen(target))+" targetLoaded="+(target!=null&&MC.theWorld!=null&&MC.theWorld.getEntityByID(target.getEntityId())==target)
                +" position="+p.posX+","+p.posY+","+p.posZ+" rotation="+p.rotationYaw+","+p.rotationPitch
                +" motion="+p.motionX+","+p.motionY+","+p.motionZ+" ground="+p.onGround
                +" collision="+p.isCollidedHorizontally+","+p.isCollidedVertically+" sprint="+p.isSprinting());
    }
}
