package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public class ReflectionUtils {
    public static Field rightClickDelayTimerField;

    static {
        try {
            rightClickDelayTimerField = Minecraft.class.getDeclaredField("rightClickDelayTimer");
            rightClickDelayTimerField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public static Object getField(Class<?> clazz, Object instance, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setField(Class<?> clazz, Object instance, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
