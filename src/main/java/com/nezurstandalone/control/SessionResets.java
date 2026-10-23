package com.nezurstandalone.control;
import java.util.*;
public final class SessionResets {
    private static final Map<Object,Runnable> resets=Collections.synchronizedMap(new IdentityHashMap<>());
    public static void register(Object owner,Runnable reset){resets.put(owner,reset);}
    public static void reset(Object owner){Runnable reset=resets.get(owner);if(reset!=null)try{reset.run();}catch(Throwable e){System.err.println("[Nezur] Session reset failed: "+e);}}
    public static void all(){List<Runnable> snapshot;synchronized(resets){snapshot=new ArrayList<>(resets.values());}for(Runnable reset:snapshot)try{reset.run();}catch(Throwable e){System.err.println("[Nezur] Session reset failed: "+e);}}
}
