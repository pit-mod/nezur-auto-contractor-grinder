package com.nezurstandalone.control;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
/** The sole automated jump writer, including spawn, path, recovery and swimming. */
public final class JumpController {
    public static java.util.function.BooleanSupplier movementAllowed = () -> true;
    public static String ownerName(){return String.valueOf(LEASE.owner());}
    private static final JumpLease LEASE=new JumpLease();

    private static boolean grounded(Minecraft mc){return mc.thePlayer!=null && (mc.thePlayer.onGround || mc.thePlayer.isInWater());}
    /** One input sample; a restored physical/foreign key never counts as this source's grant. */
    public static boolean pulse(String source){
        try{
            request(source,true);
            return LEASE.down() && source.equals(LEASE.owner());
        }finally{request(source,false);}
    }
    public static void request(String source,boolean down){
        if(down&&!movementAllowed.getAsBoolean())return;
        Minecraft mc=Minecraft.getMinecraft(); long now=Clock.SYSTEM.nanos();
        if(!down)LEASE.cancel(source,now);
        else if(mc.theWorld!=null && mc.thePlayer!=null && !mc.thePlayer.isDead && mc.currentScreen==null){
            boolean clear=mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().addCoord(0,1.25,0)).isEmpty();
            LEASE.request(source,ClientSession.current(),now,grounded(mc),clear);
        }
        refresh();
    }
    public static void authorizationLost(){
        LEASE.cancel(LEASE.owner(),Clock.SYSTEM.nanos());Minecraft mc=Minecraft.getMinecraft();
        if(mc!=null&&mc.gameSettings!=null){int key=mc.gameSettings.keyBindJump.getKeyCode();KeyBinding.setKeyBindState(key,com.nezurstandalone.input.NativeActionGate.physical(key));}
    }
    public static void refresh(){

        Minecraft mc=Minecraft.getMinecraft();
        LEASE.observe(ClientSession.current(),Clock.SYSTEM.nanos(),grounded(mc));
        if(mc.thePlayer==null || mc.thePlayer.isDead || mc.currentScreen!=null)LEASE.cancel(LEASE.owner(),Clock.SYSTEM.nanos());
        int key=mc.gameSettings.keyBindJump.getKeyCode();
        KeyBinding.setKeyBindState(key,mc.currentScreen==null
                && (LEASE.down()||com.nezurstandalone.input.NativeActionGate.physical(key)));
    }
}
