package com.nezurstandalone.utils;

/** Public data endpoints; no licensing gateway or authentication client. */
public final class VpsConfig {
    private VpsConfig() {}
    public static String getPitPandaEndpoint(String path) {
        return "https://pitpanda.rocks/api/" + path;
    }
    public static String getPitPalEndpoint(String path) {
        return "https://pitpal.rocks/api/" + path;
    }
}
