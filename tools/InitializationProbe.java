import com.nezurstandalone.*;
import com.nezurstandalone.module.ModuleManager;
import com.nezurstandalone.gui.*;
import com.nezurstandalone.utils.*;
public class InitializationProbe {
 public static void main(String[] args)throws Exception{
  Nezur.moduleManager=new ModuleManager();
  if(Nezur.moduleManager.getModules().size()!=8)throw new AssertionError("registry count");
  for(com.nezurstandalone.module.Module m:Nezur.moduleManager.getModules()){
   if(m.getSettings().isEmpty())throw new AssertionError("settings "+m.getName());
   System.out.println("initialized="+m.getName()+" settings="+m.getSettings().size());
  }
  com.nezurstandalone.module.Module standalone = new com.nezurstandalone.module.Module("Probe", "No license required", com.nezurstandalone.module.Category.AUTO);
  standalone.setToggledFromConfig(true);
  if(!standalone.isToggled())throw new AssertionError("saved enabled state restricted");
  standalone.setToggledFromConfig(false);
  if(standalone.isToggled())throw new AssertionError("saved disabled state");
  System.out.println("module_enabled_without_auth=true");
  if(!ConfigPresets.DEFAULT_CONFIG_CODE.startsWith("Nezur-"))throw new AssertionError("Nezur config prefix");
  System.out.println("config_code_prefix=Nezur-");
  for(com.nezurstandalone.module.Module module:Nezur.moduleManager.getModules()){
   if(module.getName().equals("Players List"))throw new AssertionError("deleted module registered");
   for(com.nezurstandalone.settings.Setting setting:module.getSettings())if(setting.name.equals("Show KOS"))throw new AssertionError("deleted Focus setting");
  }
  if(GuiChrome.Tab.values().length!=3)throw new AssertionError("deleted Guilds tab");
  for(String removed:new String[]{"module.impl.render.KOSList","utils.KOSManager","utils.GuildManager","gui.GuildManagerGUI","settings.GuildAddSetting"}){
   try{Class.forName("com.nezurstandalone."+removed);throw new AssertionError("removed class present");}catch(ClassNotFoundException expected){}
  }
  System.out.println("kos_guild_removed=PASS");
  com.google.gson.JsonObject friendData=new com.google.gson.JsonObject();friendData.addProperty("00000000000000000000000000000001","ProbeFriend");
  PresetTransaction.prepare(Nezur.moduleManager.getModules(),new com.google.gson.JsonObject(),friendData,new com.google.gson.JsonObject(),new com.google.gson.JsonObject()).apply();
  if(!FriendManager.getFriends().containsValue("ProbeFriend"))throw new AssertionError("friends preset lost");
  FriendManager.clear();
  System.out.println("friends_preset_without_kos=PASS");
  new ConfigGUI(null);
  System.out.println("config_gui_constructor=OK");
  ClickGUI gui=new ClickGUI();gui.width=1280;gui.height=720;
  java.lang.reflect.Method layout=ClickGUI.class.getDeclaredMethod("buildLayout");layout.setAccessible(true);layout.invoke(gui);
  java.lang.reflect.Field frames=ClickGUI.class.getDeclaredField("FRAMES");frames.setAccessible(true);
  System.out.println("gui_layout_panels="+((java.util.List<?>)frames.get(null)).size());
  System.out.println("gui_constructor="+gui.getClass().getName());
  java.lang.reflect.Method snap=ConfigManager.class.getDeclaredMethod("moduleSnapshot");snap.setAccessible(true);
  com.google.gson.JsonObject config=(com.google.gson.JsonObject)snap.invoke(null);
  if(config.entrySet().size()!=8)throw new AssertionError("serialized registry");
  String json=new com.google.gson.Gson().toJson(config);
  if(new com.google.gson.JsonParser().parse(json).getAsJsonObject().entrySet().size()!=8)throw new AssertionError("config roundtrip");
  System.out.println("config_module_roundtrip=8");
  if(PresetCode.decode(ConfigPresets.DEFAULT_CONFIG_CODE).getAsJsonObject("modules").entrySet().size()!=8)throw new AssertionError("filtered defaults");
  System.out.println("preset_default_roundtrip=8");
 }
}
