package com.nezurtest;
import net.minecraftforge.fml.common.launcher.FMLTweaker;
public final class ForgeOnlyTweaker extends FMLTweaker {
 @Override public String getLaunchTarget() { return "com.nezurtest.ForgeOnlyEntrypoint"; }
}
