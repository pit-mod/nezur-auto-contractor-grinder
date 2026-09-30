package com.nezurstandalone.settings;

import com.google.gson.*;
import java.awt.Color;
import java.util.*;

/** Shared passive-setting representation. decode validates before returning a mutation. */
public final class SettingCodec {
    private SettingCodec() {}
    public static JsonElement encode(Setting s) {
        if(s instanceof BooleanSetting)return new JsonPrimitive(((BooleanSetting)s).isEnabled());
        if(s instanceof NumberSetting)return new JsonPrimitive(((NumberSetting)s).getValue());
        if(s instanceof RangeSetting){RangeSetting r=(RangeSetting)s;JsonObject o=new JsonObject();o.addProperty("min",r.minVal);o.addProperty("max",r.maxVal);return o;}
        if(s instanceof ModeSetting)return new JsonPrimitive(((ModeSetting)s).getMode());
        if(s instanceof KeybindSetting)return new JsonPrimitive(((KeybindSetting)s).code);
        if(s instanceof ColorSetting)return new JsonPrimitive(((ColorSetting)s).getColor().getRGB());
        if(s instanceof InputSetting)return new JsonPrimitive(((InputSetting)s).getContent());
        if(s instanceof ButtonSetting || s instanceof MyauGroupSetting)return JsonNull.INSTANCE;
        throw new IllegalArgumentException("Unsupported setting: "+s.getClass().getName());
    }
    private static String string(JsonElement e) {
        if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");
        String v=e.getAsString();if(v.length()>16384)throw new IllegalArgumentException("Setting text too long");return v;
    }
    private static double number(JsonElement e) {
        if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number");
        double v=e.getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite setting");return v;
    }
    private static int integer(JsonElement e){double v=number(e);if(v!=Math.rint(v) || v<Integer.MIN_VALUE || v>Integer.MAX_VALUE)throw new IllegalArgumentException("Expected integer");return (int)v;}
    public static Runnable decode(Setting s, JsonElement e) {
        if(s instanceof ButtonSetting || s instanceof MyauGroupSetting){if(e==null || !e.isJsonNull())throw new IllegalArgumentException("Action has no persisted value");return ()->{};}
        if(s instanceof BooleanSetting){if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");boolean v=e.getAsBoolean();return ()->((BooleanSetting)s).setEnabled(v);}
        if(s instanceof NumberSetting){NumberSetting n=(NumberSetting)s;double v=NumberSetting.normalize(number(e),n.min,n.max,n.decimalPlaces);return ()->n.setValue(v);}
        if(s instanceof RangeSetting){if(e==null || !e.isJsonObject())throw new IllegalArgumentException("Expected range object");RangeSetting r=(RangeSetting)s;JsonObject o=e.getAsJsonObject();double lo=NumberSetting.normalize(number(o.get("min")),r.minBound,r.maxBound,r.decimalPlaces),hi=NumberSetting.normalize(number(o.get("max")),r.minBound,r.maxBound,r.decimalPlaces);if(lo>hi)throw new IllegalArgumentException("Reversed range");return ()->r.setRange(lo,hi);}
        if(s instanceof ModeSetting){String v=string(e);ModeSetting m=(ModeSetting)s;if(!m.modes.contains(v))throw new IllegalArgumentException("Unknown mode");return ()->m.setMode(v);}
        if(s instanceof KeybindSetting){int v=integer(e);if(v< -100 || v>255)throw new IllegalArgumentException("Invalid key code");return ()->((KeybindSetting)s).setKey(v);}
        if(s instanceof ColorSetting){Color v=new Color(integer(e),true);return ()->((ColorSetting)s).setColor(v);}
        if(s instanceof InputSetting){String v=string(e);return ()->((InputSetting)s).setContent(v);}
        throw new IllegalArgumentException("Unsupported setting: "+s.getClass().getName());
    }
}
