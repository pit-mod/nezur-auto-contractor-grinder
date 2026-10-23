import com.nezurstandalone.Nezur;
import com.nezurstandalone.combat.*;
import com.nezurstandalone.control.*;
import com.nezurstandalone.engine.GrinderEngine;
import com.nezurstandalone.module.ModuleManager;
import com.nezurstandalone.module.impl.player.*;
import com.nezurstandalone.settings.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual module settings, per-owner preference handoff, and user's sidebar reproduction. */
public final class AutomationSyncProbe {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Object field(Object object,Class<?> type,String name)throws Exception{
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Setting setting(GrinderEngine g,String name){
        for(Setting s:g.getSettings())if(name.equals(s.name))return s;throw new AssertionError(name);
    }
    public static void main(String[] args)throws Exception{
        // Populate only the Minecraft singleton identity needed by production session/lease code.
        // No graphical client, world or network is created by this fixture.
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        if(mc==null){
            Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
            mc=(net.minecraft.client.Minecraft)((sun.misc.Unsafe)f.get(null)).allocateInstance(net.minecraft.client.Minecraft.class);
            for(Field singleton:net.minecraft.client.Minecraft.class.getDeclaredFields())
                if(Modifier.isStatic(singleton.getModifiers())&&singleton.getType()==net.minecraft.client.Minecraft.class){singleton.setAccessible(true);singleton.set(null,mc);break;}
            mc.gameSettings=new net.minecraft.client.settings.GameSettings();
        }
        Nezur.moduleManager=new ModuleManager();
        check(Nezur.moduleManager.getModules().size()==8,"No additional module rows");
        check(Nezur.moduleManager.getModuleByName("KillAura")==null,"KillAura remains internal");
        AutoGrinder grinder=Nezur.moduleManager.getModuleByClass(AutoGrinder.class);
        AutoContractor contractor=Nezur.moduleManager.getModuleByClass(AutoContractor.class);
        for(com.nezurstandalone.module.Module module:Nezur.moduleManager.getModules())
            for(Setting setting:module.getSettings())
                check(!setting.name.equals("Stop on Repeated Corrections"),"Removed correction-count stop cannot be restored by any module setting");
        CombatOptions own=(CombatOptions)field(grinder,GrinderEngine.class,"combatOptions");
        CombatOptions borrowed=(CombatOptions)field(contractor,AutoContractor.class,"combatOptions");
        check(own!=borrowed&&own.useKillAura.enabled&&borrowed.useKillAura.enabled,"Both bots default on with independent settings");
        for(CombatOptions options:new CombatOptions[]{own,borrowed}){
            check(options.cpsMin.isVisible()&&!options.legacyMin.isVisible(),"Native controls shown only in KillAura mode");
            options.useKillAura.setEnabled(false);
            for(Setting s:options.settings())if(s.name.startsWith("KillAura "))check(!s.isVisible(),"Hidden when disabled: "+s.name);
            check(options.legacyMin.isVisible(),"Legacy controls remain available");
            options.legacyMin.setValue(7);check(options.minCps()==7,"Legacy values are actually consumed");
            options.useKillAura.setEnabled(true);
            for(String profile:new String[]{"Conservative","Balanced","Aggressive"}){
                options.preset.setMode(profile);
                check(options.minCps()<=options.maxCps()&&options.maxCps()<=14,"Bounded rate: "+profile);
                check(options.range()<=3&&options.botCheck.enabled,"Native reach and validation remain: "+profile);
            }
            options.cpsMin.setValue(9);check(options.preset.getMode().equals("Custom"),"Manual edits mark profile Custom");
        }
        int savedCount=grinder.getSettings().size();
        check(!grinder.isSettingVisible(setting(grinder,"Debug Logging")),"Advanced controls hidden in basic view");
        ((BooleanSetting)setting(grinder,"Show Advanced Settings")).setEnabled(true);
        check(grinder.isSettingVisible(setting(grinder,"Debug Logging")),"Advanced view restores controls");
        check(grinder.getSettings().size()==savedCount,"UI filtering never removes saved settings");
        own.cpsMin.setValue(7);
        SettingCodec.decode(own.preset,new com.google.gson.JsonPrimitive("Aggressive")).run();
        check(own.minCps()==7,"Restoring preset label cannot overwrite saved tuning");
        SettingCodec.decode(own.cpsMin,new com.google.gson.JsonPrimitive(8)).run();
        check(own.preset.getMode().equals("Aggressive"),"Passive restoration preserves stored profile metadata");
        AutoHeal healer=Nezur.moduleManager.getModuleByClass(AutoHeal.class);
        check(!healer.isToggled(),"Healer initially off in fixture");
        SettingCodec.decode(setting(grinder,"Quick Setup"),new com.google.gson.JsonPrimitive("Balanced")).run();
        check(!healer.isToggled(),"Config loading cannot execute setup actions or enable modules");
        ((ModeSetting)setting(grinder,"Quick Setup")).setMode("Conservative");
        check(healer.isToggled()&&own.maxCps()==8,"Explicit quick setup applies profile and enables healing");
        healer.setToggled(false);
        Method active=GrinderEngine.class.getDeclaredMethod("activeCombatOptions");active.setAccessible(true);
        grinder.setToggledFromConfig(true);contractor.setToggledFromConfig(true);
        borrowed.useKillAura.setEnabled(false);own.useKillAura.setEnabled(true);
        grinder.claimCombatOptions(contractor,borrowed);
        check(active.invoke(grinder)==borrowed&&!borrowed.useKillAura.enabled,"Contractor's disabled choice overrides enabled Grinder choice");
        grinder.releaseCombatOptions(contractor);
        check(active.invoke(grinder)==own&&own.useKillAura.enabled,"Release restores untouched saved Grinder choice");
        grinder.claimCombatOptions(contractor,borrowed);ClientSession.invalidate();
        check(active.invoke(grinder)==own,"Correction/session change invalidates borrowed options");
        grinder.claimCombatOptions(contractor,borrowed);contractor.setToggledFromConfig(false);
        check(active.invoke(grinder)==own,"Disabled Contractor cannot retain control");
        for(com.nezurstandalone.module.Module m:Nezur.moduleManager.getModules()){
            Set<String> names=new HashSet<>();for(Setting s:m.getSettings())check(names.add(s.name),"Unique persisted setting: "+m.getName()+"/"+s.name);
        }
        AutoReconnect reconnect=Nezur.moduleManager.getModuleByClass(AutoReconnect.class);
        reconnect.setToggledFromConfig(false);
        // This headless fixture has no Forge LaunchClassLoader. Skip bus registration only;
        // the real setToggled and AutoReconnect.onEnable still run below.
        Field bus=com.nezurstandalone.module.Module.class.getDeclaredField("onEventBus");bus.setAccessible(true);bus.set(reconnect,true);
        Method lobby=GrinderEngine.class.getDeclaredMethod("handleLobbyReconnect");lobby.setAccessible(true);lobby.invoke(grinder);
        check(reconnect.isToggled(),"Grinder lobby recovery enables AutoReconnect instead of sending its own join");
        long revision=((Number)field(reconnect,com.nezurstandalone.module.Module.class,"toggleRevision")).longValue();
        check(AutoReconnect.enableRecovery()&&((Number)field(reconnect,com.nezurstandalone.module.Module.class,"toggleRevision")).longValue()==revision,"Repeated delegation preserves the active list request");
        reconnect.setToggledFromConfig(false);
        mc.setServerData(new net.minecraft.client.multiplayer.ServerData("Classic","mc.pitclassic.net",false));
        Field unsafeGui=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeGui.setAccessible(true);
        mc.currentScreen=(net.minecraft.client.gui.GuiDisconnected)((sun.misc.Unsafe)unsafeGui.get(null)).allocateInstance(net.minecraft.client.gui.GuiDisconnected.class);
        Method recover=AutoContractor.class.getDeclaredMethod("handleConnection",long.class);recover.setAccessible(true);
        check((Boolean)recover.invoke(contractor,0L)&&reconnect.isToggled(),"Contractor disconnect recovery enables AutoReconnect");
        check((Boolean)field(reconnect,AutoReconnect.class,"reconnecting")&&field(reconnect,AutoReconnect.class,"disconnectScreen")==mc.currentScreen,"Delegation after disconnect GUI initialization still starts its reconnect timer");
        mc.currentScreen=null;mc.setServerData(null);reconnect.setToggledFromConfig(false);bus.set(reconnect,false);reconnect.onDisable();
        scoreboard();
        System.out.println("AutomationSync: "+checks+" actual settings, ownership, presets, visibility and Overdrive sidebar checks passed");
    }
    private static void scoreboard(){
        OverdriveOof oof=new OverdriveOof();
        List<String> image=Arrays.asList("Level: [85]","Needed XP: 845","Gold: 10,065.62g","§fStatus: §cOverdrive","Bounty: 1,800g","Streak: 53","Strength: V (5s)");
        check(!oof.sidebar(image,false,true,false,false,1),"Disabled option does not act");
        check(!oof.sidebar(image,true,false,false,false,1),"Non-Pit sidebar cannot arm /oof");
        check(!oof.sidebar(Arrays.asList("Streak: 53","Strength: V (5s)"),true,true,false,false,1),"Streak count and Strength do not imply Overdrive");
        check(!oof.sidebar(Arrays.asList("Status: Beastmode"),true,true,false,false,1),"Other megastreak ignored");
        check(oof.sidebar(image,true,true,false,false,100)&&oof.pending(),"Screenshot arms request without chat");
        int[] sent={0};
        check(!oof.tick(true,true,false,false,false,101,()->{sent[0]++;return true;})&&sent[0]==0,"Healing/GUI unsafe state defers command");
        check(oof.tick(true,true,false,false,true,102,()->false)&&oof.pending(),"Command refusal retains request");
        check(oof.tick(true,true,false,false,true,103,()->{sent[0]++;return true;})&&sent[0]==1,"Exactly one accepted /oof");
        for(int tick=104;tick<1000;tick++)check(!oof.sidebar(image,true,true,false,false,tick),"Repeated sidebar observation cannot requeue");
        check(!oof.tick(true,true,false,false,true,10103,()->{sent[0]++;return true;})&&sent[0]==1,"No response ends pause without repeating /oof");
        check(!oof.message("MEGASTREAK! [85] Alice activated OVERDRIVE","Alice",true,true,10200),"Chat and sidebar share deduplication");
        oof.clear();check(!oof.sidebar(image,true,true,false,false,19000),"Lingering pre-death sidebar cannot queue a second death");
        check(!oof.sidebar(Collections.singletonList("Status: Fighting"),true,true,false,false,19500),"Observed non-Overdrive status resets sidebar latch");
        check(oof.sidebar(image,true,true,false,false,20000),"New observed activation can queue after lifecycle cleanup");
        check(!oof.tick(true,true,false,false,true,65000,()->{sent[0]++;return true;})&&sent[0]==1,"Stale deferred status cannot kill a later life");
    }
}
