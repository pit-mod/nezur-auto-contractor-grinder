package com.nezurstandalone.control;
import net.minecraft.client.settings.KeyBinding;
import java.util.*;
/** Automated key ownership never clears another owner's or the user's held key. */
public final class MovementKeys {
    private static final class Press {String owner; long session, expires;}
    private static final Map<Integer,Press> presses=new HashMap<>();
    public static String walkerOwner(){return "walker";}
    public static void set(String owner,int key,boolean down){
        if(key==net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindJump.getKeyCode()){JumpController.request(owner,down);return;}
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        if(down&&(key==mc.gameSettings.keyBindAttack.getKeyCode()||key==mc.gameSettings.keyBindUseItem.getKeyCode()))throw new IllegalArgumentException("Native actions require GuardedInput, not movement keys");
        refresh();Press p=presses.get(key);
        if(down){
            if(p!=null&&!p.owner.equals(owner))return;
            if(p==null){p=new Press();p.owner=owner;presses.put(key,p);}
            p.session=ClientSession.current();p.expires=Clock.SYSTEM.nanos()+250_000_000L;
            KeyBinding.setKeyBindState(key,true);
        }else if(p!=null&&p.owner.equals(owner)){
            presses.remove(key);KeyBinding.setKeyBindState(key,com.nezurstandalone.input.NativeActionGate.physical(key));
        }
    }
    public static void release(String owner){
        JumpController.request(owner,false);
        for(Integer key:new ArrayList<>(presses.keySet())){Press p=presses.get(key);if(p.owner.equals(owner))set(owner,key,false);}
    }
    public static void refresh(){
        long session=ClientSession.current(),now=Clock.SYSTEM.nanos();
        Iterator<Map.Entry<Integer,Press>> it=presses.entrySet().iterator();
        while(it.hasNext()){Map.Entry<Integer,Press> e=it.next();Press p=e.getValue();
            if(p.session!=session||now>=p.expires){KeyBinding.setKeyBindState(e.getKey(),com.nezurstandalone.input.NativeActionGate.physical(e.getKey()));it.remove();}
        }
    }
}
