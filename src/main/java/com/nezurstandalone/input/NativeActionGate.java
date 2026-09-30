package com.nezurstandalone.input;
import net.minecraft.client.Minecraft;
/** All automation shares one budget. Physical calls run first and are never rate limited. */
public final class NativeActionGate {
    private static final com.nezurstandalone.control.ActionBudget budget=new com.nezurstandalone.control.ActionBudget(com.nezurstandalone.control.Clock.SYSTEM);
    private static boolean synthetic,physicalThisTick;
    public static void maxCps(double cps){budget.maxCps(cps);}
    public static void physicalInput(){physicalThisTick=true;}
    public static void beginTick(){budget.beginTick();physicalThisTick=false;synthetic=false;}
    public static boolean available(boolean attack){return !manual()&&budget.available(attack);}
    public static void automation(Runnable operation){boolean prior=synthetic;synthetic=true;try{operation.run();}finally{synthetic=prior;}}
    public static boolean allow(boolean attack){
        boolean physical=heldPhysical();
        boolean heldAutomation=!attack&&com.nezurstandalone.control.InventoryOwner.holdingUse();
        if(!synthetic&&(!heldAutomation||physical||physicalThisTick)){physicalThisTick=true;return true;}
        return !physicalThisTick&&!physical&&budget.invoke(attack);
    }
    private static boolean heldPhysical(){Minecraft mc=Minecraft.getMinecraft();return physical(mc.gameSettings.keyBindAttack.getKeyCode())||physical(mc.gameSettings.keyBindUseItem.getKeyCode());}
    public static boolean manual(){return physicalThisTick||heldPhysical();}
    public static boolean physical(int key){return key<0?org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(key+100):org.lwjgl.input.Keyboard.isCreated()&&org.lwjgl.input.Keyboard.isKeyDown(key);}
}
