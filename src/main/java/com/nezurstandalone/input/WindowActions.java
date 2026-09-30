package com.nezurstandalone.input;
import com.nezurstandalone.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import java.util.*;
/** One native click, one exact S32 ACK, and a matching predicted result before advancing. */
public final class WindowActions {
    private static final Map<Object,Action> actions=new IdentityHashMap<>();
    private static final class Action {
        long session,started; Container container; Object screen; short number;
        boolean acknowledged,rejected; ItemStack[] expected; int[] changedSlots;
        MenuResultContract contract;
    }
    public static void cancel(Object owner){actions.remove(owner);GuiLease.release(owner);}
    public static boolean ready(Object owner) {
        Action a=actions.get(owner);if(a==null)return true;
        Minecraft mc=Minecraft.getMinecraft();
        MenuResultContract.Result result=a.contract.result(ClientSession.current(),Clock.SYSTEM.nanos(),
            mc.thePlayer==null||a.screen!=mc.currentScreen||a.container!=mc.thePlayer.openContainer?-1:mc.thePlayer.openContainer.windowId);
        if(result==MenuResultContract.Result.UNKNOWN||result==MenuResultContract.Result.REJECTED)
            throw new IllegalStateException("Inventory transaction "+result+"; no authoritative completion");
        if(result!=MenuResultContract.Result.SUCCESS)return false;
        for(int slot:a.changedSlots) {
            if(!ItemStack.areItemStacksEqual(a.expected[slot],a.container.getSlot(slot).getStack()))
                throw new IllegalStateException("Accepted click prediction no longer matches local inventory");
        }
        actions.remove(owner);return true;
    }
    public static void click(Object owner,int window,int slot,int button,int mode) {
        Minecraft mc=Minecraft.getMinecraft();
        if(!GuiLease.owns(owner)||!ready(owner)||mc.thePlayer==null||mc.thePlayer.openContainer.windowId!=window)
            throw new IllegalStateException("No inventory lease or unacknowledged click");
        Action a=new Action();a.session=ClientSession.current();a.container=mc.thePlayer.openContainer;
        a.screen=mc.currentScreen;a.started=Clock.SYSTEM.nanos();
        ItemStack[] before=new ItemStack[a.container.inventorySlots.size()];
        for(int i=0;i<before.length;i++)before[i]=ItemStack.copyItemStack(a.container.getSlot(i).getStack());
        try {
            java.lang.reflect.Field counter=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Container.class,"transactionID","field_75150_e");
            counter.getShort(a.container); // fail before dispatch if inaccessible
            mc.playerController.windowClick(window,slot,button,mode,mc.thePlayer);
            a.number=counter.getShort(a.container);
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
        a.expected=new ItemStack[a.container.inventorySlots.size()];
        for(int i=0;i<a.expected.length;i++)a.expected[i]=ItemStack.copyItemStack(a.container.getSlot(i).getStack());
        java.util.List<Integer> changed=new java.util.ArrayList<>();
        for(int i=0;i<before.length;i++)if(!ItemStack.areItemStacksEqual(before[i],a.expected[i]))changed.add(i);
        int[] expectedSlots=new int[changed.size()];for(int i=0;i<expectedSlots.length;i++)expectedSlots[i]=changed.get(i);
        a.changedSlots=expectedSlots;
        a.contract=MenuResultContract.vanilla(a.session,a.started,window,slot,a.number,expectedSlots);
        actions.put(owner,a);
    }
    public static void slot(int window,int slot,ItemStack stack){
        for(Action a:actions.values())if(slot>=0&&slot<a.expected.length)a.contract.authoritative(ClientSession.current(),window,slot,ItemStack.areItemStacksEqual(a.expected[slot],stack));
    }
    public static void confirm(int window,short number,boolean accepted){
        for(Action a:actions.values()) if(a.session==ClientSession.current()&&a.container.windowId==window&&a.number==number){a.acknowledged=accepted;a.rejected=!accepted;a.contract.acknowledge(ClientSession.current(),window,number,accepted);}
    }
}
