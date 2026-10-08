package com.nezurstandalone.utils;
/** Bounded decimal input. -1 is an invalid value, never an actionable zero. */
public final class SafeNumbers {
    private SafeNumbers() {}
    public static int nonNegativeInt(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > 10 || maximum < 0) return -1;
        for (int i=0;i<value.length();i++) if(value.charAt(i)<'0'||value.charAt(i)>'9') return -1;
        try { int n=Integer.parseInt(value); return n<=maximum ? n : -1; }
        catch (NumberFormatException invalid) { return -1; }
    }
}
