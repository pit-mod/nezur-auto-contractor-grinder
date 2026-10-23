package com.nezurstandalone.input;
import net.minecraft.client.Minecraft;
/** All automation shares one budget. Physical calls run first and are never rate limited. */
public final class NativeActionGate {
    public static java.util.function.BooleanSupplier automationAllowed = () -> true;
    public static java.util.function.BooleanSupplier combatOperationAllowed = () -> true;
    private static final com.nezurstandalone.control.ActionBudget budget=new com.nezurstandalone.control.ActionBudget(com.nezurstandalone.control.Clock.SYSTEM);
    private static boolean synthetic,physicalThisTick,physicalActionThisTick;
    public static java.util.function.BooleanSupplier attackKeyOwner=()->false;
    public static java.util.function.BooleanSupplier cameraAttackAllowed=()->true;
    public static java.util.function.BooleanSupplier nativeAttackCadence=()->false;
    public static java.util.function.BooleanSupplier inventoryTransactionAllowed=()->true;
    public static java.util.function.BooleanSupplier priorityItemUseAllowed=()->false;
    public static void maxCps(double cps){budget.maxCps(cps);}
    public static void physicalInput(){physicalThisTick=true;}
    public static void manualSlotSelection(){
        physicalThisTick=physicalActionThisTick=true;
        com.nezurstandalone.control.InventoryOwner.manualSelection();
    }
    public static void beginTick(){budget.beginTick();physicalThisTick=false;physicalActionThisTick=false;synthetic=false;}
    public static boolean isAutomation(){return synthetic;}
    /** Called for actual outbound attacks, including manual/foreign calls during ownership handoffs. */
    public static void attackPacket(){if(!synthetic){physicalActionThisTick=true;budget.observeAttack();}}
    public static boolean available(boolean attack){return automationAllowed.getAsBoolean()&&inventoryTransactionAllowed.getAsBoolean()&&(!attack||!priorityItemUseAllowed.getAsBoolean())&&(attack||!attackKeyOwner.getAsBoolean()||priorityItemUseAllowed.getAsBoolean())&&!manual()&&budget.available(attack,attack&&nativeAttackCadence.getAsBoolean());}
    public static void automation(Runnable operation){boolean prior=synthetic;synthetic=true;try{operation.run();}finally{com.nezurstandalone.control.CombatPacketAudit.onEnd();synthetic=prior;}}
    public static boolean allow(boolean attack){
        if(!inventoryTransactionAllowed.getAsBoolean())return false;
        boolean owned=attackKeyOwner.getAsBoolean();
        boolean healing=priorityItemUseAllowed.getAsBoolean();
        boolean priorityUse=!attack&&healing;
        if(attack&&healing)return false;
        if(synthetic&&!attack&&owned&&!priorityUse)return false;
        if(!synthetic&&attack&&(owned||!cameraAttackAllowed.getAsBoolean()))return false;
        boolean physical=heldPhysical();
        boolean heldAutomation=!attack&&com.nezurstandalone.control.InventoryOwner.holdingUse();
        if ((synthetic || heldAutomation) && !automationAllowed.getAsBoolean()) return false;
        if(!synthetic&&(!heldAutomation||physical||physicalThisTick&&!owned&&!healing)){physicalThisTick=physicalActionThisTick=true;return true;}
        return !physicalActionThisTick&&(!heldAutomation||com.nezurstandalone.control.InventoryOwner.authorizedUse())&&(!physicalThisTick||owned&&attack||priorityUse)&&!physical&&budget.invoke(attack,attack&&nativeAttackCadence.getAsBoolean());
    }
    private static boolean heldPhysical(){Minecraft mc=Minecraft.getMinecraft();return !attackKeyOwner.getAsBoolean()&&!priorityItemUseAllowed.getAsBoolean()&&physical(mc.gameSettings.keyBindAttack.getKeyCode())||physical(mc.gameSettings.keyBindUseItem.getKeyCode());}
    public static boolean manual(){return physicalActionThisTick||physicalThisTick&&!attackKeyOwner.getAsBoolean()&&!priorityItemUseAllowed.getAsBoolean()||heldPhysical();}
    public static boolean physical(int key){return key<0?org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(key+100):org.lwjgl.input.Keyboard.isCreated()&&org.lwjgl.input.Keyboard.isKeyDown(key);}
}
