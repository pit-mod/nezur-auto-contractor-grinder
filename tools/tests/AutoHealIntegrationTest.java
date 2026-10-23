import com.nezurstandalone.module.impl.player.AutoHeal;
import com.nezurstandalone.input.*;
import com.nezurstandalone.control.*;
import com.nezurstandalone.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/** Production AutoHeal + GuardedInput + NativeActionGate + inventory lease integration. */
public final class AutoHealIntegrationTest {
    private static final Minecraft mc=Minecraft.getMinecraft();
    private static int checks;
    private static AutoHeal heal;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void setup(Item item,String name,int damage){
        if(heal!=null)heal.enabled(false);
        GuardedInput.clear();ClientSession.token++;Clock.Time.now+=10000;
        mc.thePlayer=new EntityPlayer();mc.theWorld=new Object();mc.thePlayer.worldObj=mc.theWorld;mc.currentScreen=null;
        mc.playerController=new Minecraft.Controller();mc.objectMouseOver=new net.minecraft.util.MovingObjectPosition();
        mc.thePlayer.inventory.mainInventory[0]=new ItemStack(new Item(276,0),"sword",1,0);
        mc.thePlayer.inventory.mainInventory[1]=new ItemStack(item,name,2,damage);
        NativeActionGate.attackKeyOwner=()->true;NativeActionGate.inventoryTransactionAllowed=()->true;
        Mouse.leftDown=true;Mouse.rightDown=false;KeyBinding.use=false;
        heal=new AutoHeal();
        for(Setting setting:heal.settings){
            if(setting.name.equals("Randomize Timings"))((BooleanSetting)setting).enabled=false;
            if(setting.name.equals("Reaction Time"))((NumberSetting)setting).value=0;
        }
        heal.enabled(true);
    }
    private static void tick(boolean dispatch){
        Clock.Time.now+=50;NativeActionGate.beginTick();NativeActionGate.physicalInput();
        heal.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        if(dispatch)dispatch();
    }
    private static void dispatch(){
        // Same native input phase handoff as MixinMinecraftInput.
        if(NativeActionGate.priorityItemUseAllowed.getAsBoolean()&&mc.playerController.digging)mc.playerController.resetBlockRemoving();
        GuardedInput.dispatchPermit=NativeActionGate::available;
        if(NativeActionGate.manual())GuardedInput.clear();
        else NativeActionGate.automation(()->GuardedInput.dispatchCamera(()->{throw new AssertionError("Unexpected attack");},()->{throw new AssertionError("Heal must use held item directly");}));
    }
    private static void confirm(){
        ItemStack stack=ItemStack.copyItemStack(mc.thePlayer.inventory.getStackInSlot(1));stack.stackSize--;
        mc.thePlayer.inventory.mainInventory[1]=stack;InventoryEvidence.slot(0,37,stack);mc.thePlayer.using=false;
    }
    private static void restored(String context){
        check(!AutoHeal.isHealing(),context+": transaction terminates");
        check(mc.thePlayer.inventory.currentItem==0,context+": previous weapon restored");
        check(!InventoryOwner.holdingUse()&&!KeyBinding.use,context+": held use released");
        check(!NativeActionGate.priorityItemUseAllowed.getAsBoolean(),context+": combat handoff released");
    }
    public static void main(String[] args){
        setup(new ItemSkull(),"\u00a76Golden Head",0);mc.thePlayer.absorption=4;tick(true);
        restored("Existing absorption");check(mc.playerController.calls==0,"Existing absorption cannot select or consume a head");
        setup(new ItemSkull(),"\u00a76Golden Head",0);
        for(Setting setting:heal.settings)if(setting.name.equals("Reaction Time"))((NumberSetting)setting).value=100;
        tick(false);mc.thePlayer.absorption=4;tick(true);
        restored("Absorption during reaction");check(mc.playerController.calls==0,"Reaction cannot swap after absorption appears");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);mc.thePlayer.absorption=4;dispatch();
        check(mc.playerController.calls==0,"Queued native head use rechecks absorption at dispatch");tick(true);restored("Absorption before use");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(true);mc.thePlayer.absorption=4;tick(true);
        restored("Absorption before slot acknowledgement");
        for(int i=0;i<40;i++){
            tick(true);
            check(!AutoHeal.isHealing()&&mc.thePlayer.inventory.currentItem==0,"Remaining absorption cannot reselect the head");
        }
        check(mc.playerController.calls==1,"Head is not retried while absorption remains");
        mc.thePlayer.absorption=0;tick(true);
        check(mc.playerController.calls==2&&AutoHeal.isHealing(),"A new head is eligible after absorption runs out");
        for(Item item:new Item[]{new ItemSkull(),new ItemSoup(),new ItemFood(423),new Item(383,0),new Item(378,0),new ItemFood(393)}){
            setup(item,item instanceof ItemSkull?"\u00a76Golden Head":"heal",item.id==383?96:0);
            tick(false);
            check(mc.thePlayer.inventory.currentItem==1&&AutoHeal.isHealing(),"Healing item selected");
            NativeActionGate.attackKeyOwner=()->false; // Enemy dies/moves away before native use.
            check(!NativeActionGate.manual(),"Selected heal retains held attack handoff without a combat target");
            check(!NativeActionGate.allow(true),"Physical left click cannot swing the selected heal");
            NativeActionGate.automation(()->check(!NativeActionGate.allow(true),"Combat cannot consume the heal's native action"));
            mc.playerController.digging=true;dispatch();
            check(mc.playerController.calls==1&&!mc.playerController.digging,"Native use runs after mining stops despite lost target");
            check(NativeActionGate.priorityItemUseAllowed.getAsBoolean(),"Heal retains priority throughout result/hold phase");
            if(item.duration>0){
                check(InventoryOwner.holdingUse()&&KeyBinding.use,"Sustained heal keeps use held");
                for(int i=0;i<32;i++){
                    tick(true);
                    check(AutoHeal.isHealing()&&mc.thePlayer.inventory.currentItem==1,"Native eating remains selected until completion");
                    check(NativeActionGate.allow(false),"Sustained native use survives held left input without combat ownership");
                    check(!NativeActionGate.available(true),"Held food and combat cannot attack concurrently");
                }
            }
            confirm();tick(true);if(AutoHeal.isHealing())tick(true);
            restored("Confirmed item "+item.id);
        }
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);
        NativeActionGate.attackKeyOwner=()->false;
        NativeActionGate.inventoryTransactionAllowed=()->false;
        for(int i=0;i<10;i++)tick(true);
        check(mc.playerController.calls==0&&AutoHeal.isHealing(),"Temporary inventory guard blocks use without losing slot ownership");
        NativeActionGate.inventoryTransactionAllowed=()->true;tick(true);
        check(mc.playerController.calls==1,"Use resumes when temporary guard clears");confirm();tick(true);tick(true);restored("Temporary guard");

        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);NativeActionGate.attackKeyOwner=()->false;
        NativeActionGate.inventoryTransactionAllowed=()->false;
        for(int i=0;i<31&&AutoHeal.isHealing();i++)tick(true);
        restored("Dispatch timeout");check(mc.playerController.calls==0,"Blocked use never invokes a heal");

        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(true);
        for(int i=0;i<90&&AutoHeal.isHealing();i++)tick(true);
        restored("Missing head consumption");check(mc.playerController.calls==1,"Unknown instant use is never replayed as a retry");
        for(int i=0;i<50;i++)tick(true);
        check(mc.playerController.calls==1,"A new transaction cannot bypass the unknown-result slot guard");
        confirm();mc.thePlayer.absorption=4;tick(true);
        check(mc.playerController.calls==1,"Late consumption/absorption cannot trigger a duplicate head");

        setup(new ItemFood(423),"steak",0);tick(true);
        for(int i=0;i<100&&AutoHeal.isHealing();i++){
            // Interrupt late, then keep the retry using: a new per-attempt timer must not extend the lease.
            if(i==59)mc.thePlayer.using=false;
            tick(true);
        }
        restored("Interrupted food");check(mc.playerController.calls==1,"Unknown sustained use is not replayed");
        check("AUTOHEAL_UNCONFIRMED_NO_REPLAY".equals(GrinderDiagnostics.lastEvent),"Interrupted result restores control and records ambiguity");

        setup(new ItemFood(423),"steak",0);tick(true);
        // Vanilla may predict consumption without an authoritative server slot update.
        mc.thePlayer.inventory.mainInventory[1].stackSize--;mc.thePlayer.using=false;
        for(int i=0;i<100;i++)tick(true);
        check(mc.playerController.calls==1,"Local food prediction alone cannot unblock an uncertain request");

        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(true);AutoHeal.interrupt();ClientSession.token++;
        for(int i=0;i<50;i++)tick(true);
        check(mc.playerController.calls==1&&!AutoHeal.isHealing(),"Same-world correction preserves the uncertain-use guard across session invalidation");
        confirm();mc.thePlayer.absorption=4;tick(true);
        check(mc.playerController.calls==1,"Late server consumption after correction cannot duplicate the heal");

        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);mc.thePlayer.inventory.currentItem=4;tick(true);
        check(!AutoHeal.isHealing()&&mc.thePlayer.inventory.currentItem==4,"Manual slot override preserved");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);NativeActionGate.manualSlotSelection();
        GuardedInput.clear();mc.thePlayer.inventory.currentItem=1;tick(true);
        check(!AutoHeal.isHealing()&&mc.thePlayer.inventory.currentItem==1&&mc.playerController.calls==0,"Explicitly reselecting the heal slot revokes automatic restoration and pending use");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);mc.currentScreen=new Object();tick(true);restored("Screen interruption");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);heal.enabled(false);restored("Module disable");
        setup(new ItemSkull(),"\u00a76Golden Head",0);tick(false);ClientSession.token++;mc.thePlayer=new EntityPlayer();mc.thePlayer.inventory.currentItem=4;mc.theWorld=new Object();tick(true);
        check(!AutoHeal.isHealing()&&mc.thePlayer.inventory.currentItem==4,"New session/player never receives obsolete slot restoration");
        System.out.println("AutoHealIntegration: "+checks+" actual-module held-input, consumption, ambiguity and cleanup checks passed");
    }
}
