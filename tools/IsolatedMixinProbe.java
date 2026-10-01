import java.io.File;
import java.net.URL;
import java.util.*;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
public final class IsolatedMixinProbe {
 public static void main(String[] args) throws Exception {
  try { Class.forName("org.spongepowered.asm.launch.MixinBootstrap"); throw new AssertionError("Mixin leaked into parent before test"); }
  catch(ClassNotFoundException expected) { System.out.println("initial_parent_mixin=ABSENT"); }
  Launch.minecraftHome=new File(".");
  Launch.blackboard=new HashMap<String,Object>();
  Launch.blackboard.put("TweakClasses",new ArrayList<String>());
  Launch.blackboard.put("Tweaks",new ArrayList<Object>());
  Launch.classLoader=new LaunchClassLoader(new URL[]{new File(args[0]).toURI().toURL()});
  Launch.classLoader.addClassLoaderExclusion("net.minecraftforge.");
  Launch.classLoader.addClassLoaderExclusion("org.apache.logging.");
  Launch.classLoader.addClassLoaderExclusion("com.google.");
  Launch.classLoader.addClassLoaderExclusion("org.objectweb.asm.");
  Thread.currentThread().setContextClassLoader(Launch.classLoader);
  Object plugin=Launch.classLoader.loadClass("com.nezurstandalone.loader.NezurMixinLoader").newInstance();
  System.out.println("plugin_source="+plugin.getClass().getProtectionDomain().getCodeSource().getLocation());
  try { plugin.getClass().getMethod("injectData",Map.class).invoke(plugin,new HashMap<String,Object>()); }
  catch(java.lang.reflect.InvocationTargetException failure) { failure.getCause().printStackTrace(); System.exit(1); }
  Class<?> service=Class.forName("org.spongepowered.asm.service.IMixinService",false,Launch.class.getClassLoader());
  Class<?> bootstrap=Class.forName("org.spongepowered.asm.launch.MixinBootstrap",false,Launch.class.getClassLoader());
  if(service.getClassLoader()!=bootstrap.getClassLoader())throw new AssertionError("split Mixin classloader");
  System.out.println("parent_mixin_service=AVAILABLE");
  System.out.println("single_mixin_classloader=PASS");
  System.out.println("isolated_coremod_injectData=PASS");
 }
}
