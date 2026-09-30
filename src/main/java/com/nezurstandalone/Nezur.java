package com.nezurstandalone;


import com.nezurstandalone.module.ModuleManager;
import com.nezurstandalone.utils.LobbyPlayerIndex;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = Nezur.MODID, version = Nezur.VERSION, name = Nezur.NAME)
public class Nezur {
    public static final String MODID = "nezur_acg";
    public static final String VERSION = "1.5";
    public static final String NAME = "Nezur Auto Contractor + Grinder";
    
    public static ModuleManager moduleManager;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        moduleManager = new ModuleManager();
        com.nezurstandalone.utils.ConfigManager.loadConfig();
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.NotificationOverlay());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.gui.GuiExitAnimator());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.HudStackManager());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.HudDragHandler());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.ConfigLifecycleHandler());
        
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.ConfigSaveDebouncer());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.HudPositionSaveDebouncer());
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.utils.GuiDumper());
        MinecraftForge.EVENT_BUS.register(LobbyPlayerIndex.INSTANCE);
        com.nezurstandalone.utils.KeybindRegistry.markStale();
        com.nezurstandalone.utils.RotationManager.getInstance().init();



        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.nezurstandalone.utils.ConfigSaveDebouncer.flushNow();
            com.nezurstandalone.utils.HudPositionSaveDebouncer.flushNow();
        }));
        
        
        System.out.println("Nezur Mod Initialized!");
    }

}
