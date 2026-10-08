package com.nezurtest;
public final class ForgeOnlyEntrypoint {
 public static void main(String[] args) throws Exception {
  Class<?> service=Class.forName("org.spongepowered.asm.service.IMixinService",false,net.minecraft.launchwrapper.Launch.class.getClassLoader());
  if(service==null)throw new AssertionError("service missing");
  for(String target:new String[]{"net.minecraft.client.renderer.EntityRenderer","net.minecraft.client.Minecraft","net.minecraft.client.network.NetHandlerPlayClient","net.minecraft.world.World","net.minecraft.client.gui.GuiIngame","net.minecraft.scoreboard.ScorePlayerTeam"}) {
   Class.forName(target,false,net.minecraft.launchwrapper.Launch.classLoader);
   System.out.println("production_mixin_target_loaded="+target);
  }
  Class.forName("net.minecraft.client.Minecraft",false,net.minecraft.launchwrapper.Launch.classLoader).getDeclaredField("raycastReady");
  System.out.println("production_minecraft_input_mixin_applied=PASS");
  Class<?> renderer=Class.forName("net.minecraft.client.renderer.EntityRenderer",false,net.minecraft.launchwrapper.Launch.classLoader);
  int hooks=0;
  for(java.lang.reflect.Method method:renderer.getDeclaredMethods())if(method.getName().contains("beginLookFrame")||method.getName().contains("afterPhysicalLook")||method.getName().contains("finishLookFrame"))hooks++;
  if(hooks!=3)throw new AssertionError("camera hooks="+hooks);
  System.out.println("production_camera_mixin_applied=PASS");
  System.out.println("REAL_FORGE_ONLY_NEZUR_BOOTSTRAP=PASS");
 }
}
