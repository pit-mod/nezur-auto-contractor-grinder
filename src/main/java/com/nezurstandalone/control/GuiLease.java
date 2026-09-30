package com.nezurstandalone.control;
/** Session-scoped inventory operation owner. Native user screens are never closed by this class. */
public final class GuiLease {
    private static Object owner;
    private static long session;
    public static boolean acquire(Object who) {
        long now=ClientSession.current(); if(session!=now){owner=null;session=now;}
        if(owner!=null&&owner!=who)return false;owner=who;return true;
    }
    public static boolean owns(Object who) {
        if(session!=ClientSession.current()){owner=null;return false;}return owner==who;
    }
    public static boolean available(Object who) {
        if(session!=ClientSession.current())owner=null;return owner==null||owner==who;
    }
    public static void release(Object who){if(owner==who)owner=null;}
}
