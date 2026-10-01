package com.nezurstandalone.loader;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Coremod entry point: boots Mixin and registers {@code mixins.nezur_acg.json}.
 *
 * <h2>Why there is no refmap</h2>
 * A Minecraft member has a different name depending on where the mod is running. In this
 * workspace the mappings are {@code stable_22}, so it is the MCP name
 * ({@code handlePlayerPosLook}). In production, Forge's deobfuscation transformer rewrites the
 * client to SRG names, so it is {@code func_147258_a}. A refmap normally bridges that gap:
 * the annotation processor records what you wrote and Mixin translates it at runtime.
 *
 * <p>This project does the translation by hand instead. Every mixin sets {@code remap = false}
 * and lists <em>both</em> names, so Mixin takes them literally and tries each in turn - one
 * matches in the development client, the other in production:
 *
 * <pre>
 * &#64;Inject(method = {"handlePlayerPosLook", "func_147258_a"}, at = &#64;At("HEAD"), remap = false)
 * &#64;Shadow(aliases = "func_82833_r", remap = false)
 * </pre>
 *
 * <p>The generated {@code mixins.nezur.refmap.json} is therefore empty, is not declared in the
 * mixin config, and is not shipped in the jar. That is deliberate, not an oversight - but it
 * means <strong>the dual-name convention above is mandatory for every new mixin</strong>. A
 * mixin that lists only one name compiles fine and then silently fails to apply in whichever
 * environment it did not name, which is the worst way for this to go wrong.
 */
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.Name("Nezur ACG Mixin Loader")
@IFMLLoadingPlugin.TransformerExclusions({"com.nezurstandalone.loader."})
public class NezurMixinLoader implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        try {
            prepareBundledMixin();
            org.spongepowered.asm.launch.MixinBootstrap.init();
            org.spongepowered.asm.mixin.Mixins.addConfiguration("mixins.nezur_acg.json");
        } catch (Throwable failure) {
            throw new IllegalStateException("Nezur mandatory initialization failed; automation must not start", failure);
        }
    }

    /** Mixin's service packages delegate to the LaunchWrapper parent, not its mod URLs. */
    private static void prepareBundledMixin() throws Exception {
        ClassLoader parent = net.minecraft.launchwrapper.Launch.class.getClassLoader();
        if (!(parent instanceof java.net.URLClassLoader)) {
            throw new IllegalStateException("Nezur requires the Java 8 LaunchWrapper classloader");
        }
        java.net.URL source = NezurMixinLoader.class.getProtectionDomain().getCodeSource().getLocation();
        if ("jar".equals(source.getProtocol())) {
            source = ((java.net.JarURLConnection) source.openConnection()).getJarFileURL();
        }
        java.net.URLClassLoader urls = (java.net.URLClassLoader) parent;
        boolean present = false;
        for (java.net.URL url : urls.getURLs()) {
            if (url.equals(source)) { present = true; break; }
        }
        if (!present) {
            java.lang.reflect.Method addUrl = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(urls, source);
        }
        // Establish a single parent-loaded Mixin implementation before touching its bootstrap.
        net.minecraft.launchwrapper.Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.");
        Class.forName("org.spongepowered.asm.service.IMixinService", false, parent);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
