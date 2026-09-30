package com.nezurstandalone.input;
import com.nezurstandalone.control.ClientSession;
import net.minecraft.item.ItemStack;
/** Server-only hotbar observations. Native local prediction never advances these revisions. */
public final class InventoryEvidence {
    private static final ItemStack[] slots=new ItemStack[9];private static final long[] revisions=new long[9];
    private static long session,serial;
    private static void refresh(){long s=ClientSession.current();if(s!=session){session=s;java.util.Arrays.fill(slots,null);java.util.Arrays.fill(revisions,0);serial++;}}
    public static long revision(int slot){refresh();return revisions[slot];}
    public static void slot(int window,int slot,ItemStack stack){refresh();int hotbar=window==0?slot-36:window==-2?slot:-1;if(hotbar>=0&&hotbar<9){slots[hotbar]=ItemStack.copyItemStack(stack);revisions[hotbar]=++serial;}}
    public static boolean consumed(int slot,long after,ItemStack before){refresh();if(revisions[slot]<=after||before==null)return false;ItemStack now=slots[slot];
        return now==null || (now.getItem()==before.getItem()&&now.getItemDamage()==before.getItemDamage()&&now.stackSize<before.stackSize)
            || (before.getItem() instanceof net.minecraft.item.ItemSoup && now.getItem()==net.minecraft.init.Items.bowl);
    }
}
