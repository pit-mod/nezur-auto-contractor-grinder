package com.nezurstandalone.utils;
import com.google.gson.*;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.*;
import java.util.*;
/** Parse/validate every passive value before any live module or list changes. Main-thread only. */
public final class PresetTransaction {
    private final List<Module> modules;
    private final List<Runnable> passive=new ArrayList<>();
    private final Map<Module,Boolean> enabled=new LinkedHashMap<>();
    private final Map<String,String> friends,truce;
    private final Map<String,HudPositionManager.PositionData> hud;
    private PresetTransaction(List<Module> modules,JsonObject root,JsonObject f,JsonObject t,JsonObject h) {
        this.modules=new ArrayList<>(modules);
        friends=players(f);truce=players(t);hud=positions(h);
        for(Module module:modules){
            boolean on=false;
            if(root.has(module.getName())){
                JsonElement value=root.get(module.getName());if(!value.isJsonObject())throw new IllegalArgumentException("Invalid module config");JsonObject o=value.getAsJsonObject();
                if(o.has("toggled")){JsonElement e=o.get("toggled");if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid toggle");on=e.getAsBoolean();}
                if(o.has("keybind"))passive.add(SettingCodec.decode(module.keybind,o.get("keybind")));
                if(o.has("settings")){
                    if(!o.get("settings").isJsonObject())throw new IllegalArgumentException("Invalid settings object");JsonObject settings=o.getAsJsonObject("settings");
                    for(Setting setting:module.settings){String key=settings.has(setting.name)?setting.name:("Hide in HUD".equals(setting.name)&&settings.has("Hid in HUD")?"Hid in HUD":null);if(key!=null)passive.add(SettingCodec.decode(setting,settings.get(key)));}
                }
            }
            enabled.put(module,on&&!module.isSettingsOnly());
        }
    }
    public static PresetTransaction prepare(List<Module> modules,JsonObject root,JsonObject friends,JsonObject truce,JsonObject hud) {
        return new PresetTransaction(modules,root,friends,truce,hud);
    }
    public void apply() {
        List<Runnable> restoreSettings=new ArrayList<>();Map<Module,Boolean> oldEnabled=new LinkedHashMap<>();
        for(Module m:modules){oldEnabled.put(m,m.isEnabledRequested());restoreSettings.add(SettingCodec.decode(m.keybind,SettingCodec.encode(m.keybind)));for(Setting s:m.settings)restoreSettings.add(SettingCodec.decode(s,SettingCodec.encode(s)));}
        Map<String,String> oldFriends=new LinkedHashMap<>(FriendManager.getFriends()),oldTruce=new LinkedHashMap<>(TruceManager.getTrucePlayers());
        Map<String,HudPositionManager.PositionData> oldHud=HudPositionManager.snapshot();
        try {
            for(Module m:modules)m.setToggled(false);
            for(Runnable value:passive)value.run();
            replaceLists(friends,truce);
            for(Map.Entry<String,HudPositionManager.PositionData> e:hud.entrySet()){HudPositionManager.PositionData p=e.getValue();HudPositionManager.set(e.getKey(),p.x,p.y,p.sw,p.sh);}
            for(Map.Entry<Module,Boolean> e:enabled.entrySet()){
                e.getKey().setToggled(e.getValue());
                if(e.getValue() && !e.getKey().isEnabledRequested())throw new IllegalStateException("Preset activation failed: "+e.getKey().getName());
            }
        } catch(Throwable failure) {
            List<Runnable> rollback=new ArrayList<>();for(Module m:modules)rollback.add(()->m.setToggled(false));rollback.addAll(restoreSettings);
            rollback.add(()->replaceLists(oldFriends,oldTruce));rollback.add(()->HudPositionManager.restore(oldHud));
            for(Map.Entry<Module,Boolean> e:oldEnabled.entrySet())rollback.add(()->e.getKey().setToggled(e.getValue()));
            try{com.nezurstandalone.control.Cleanup.run(rollback.toArray(new Runnable[0]));}catch(Throwable restoration){failure.addSuppressed(restoration);}
            if(failure instanceof Error)throw (Error)failure;
            if(failure instanceof RuntimeException)throw (RuntimeException)failure;
            throw new IllegalStateException(failure);
        }
    }
    private static void replaceLists(Map<String,String> f,Map<String,String> t){
        FriendManager.clear();TruceManager.clear();
        for(Map.Entry<String,String> e:f.entrySet())FriendManager.loadEntry(e.getKey(),e.getValue());
        for(Map.Entry<String,String> e:t.entrySet())TruceManager.loadEntry(e.getKey(),e.getValue());
    }
    private static Map<String,String> players(JsonObject o){
        if(o.entrySet().size()>10000)throw new IllegalArgumentException("Player list limit");Map<String,String> result=new LinkedHashMap<>();
        for(Map.Entry<String,JsonElement> e:o.entrySet()){
            if(e.getKey().length()>128 || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid player entry");String v=e.getValue().getAsString();if(v.length()>128)throw new IllegalArgumentException("Player name limit");result.put(e.getKey(),v);
        }return result;
    }
    private static double number(JsonObject o,String key,boolean required){
        if(!o.has(key)){if(required)throw new IllegalArgumentException("Missing HUD coordinate");return 0;}
        JsonElement e=o.get(key);if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid HUD coordinate");double value=e.getAsDouble();if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite HUD coordinate");return value;
    }
    private static Map<String,HudPositionManager.PositionData> positions(JsonObject o){
        if(o.entrySet().size()>1024)throw new IllegalArgumentException("HUD position limit");Map<String,HudPositionManager.PositionData> result=new LinkedHashMap<>();
        for(Map.Entry<String,JsonElement> e:o.entrySet()){
            if(!e.getValue().isJsonObject())throw new IllegalArgumentException("Invalid HUD position");JsonObject v=e.getValue().getAsJsonObject();result.put(e.getKey(),new HudPositionManager.PositionData(number(v,"x",true),number(v,"y",true),number(v,"sw",false),number(v,"sh",false)));
        }return result;
    }
}
