package com.nezurstandalone.module;

import org.lwjgl.input.Keyboard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares metadata for Nezur modules cleanly via annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {
    String name();
    String description() default "";
    Category category();
    int keybind() default Keyboard.KEY_NONE;
    boolean dangerous() default false;
}
