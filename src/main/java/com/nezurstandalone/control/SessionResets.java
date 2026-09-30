package com.nezurstandalone.control;
import java.util.*;
public final class SessionResets {
    private static final Map<Object,Runnable> resets=new IdentityHashMap<>();
    public static void register(Object owner,Runnable reset){resets.put(owner,reset);}
    public static void reset(Object owner){Runnable reset=resets.get(owner);if(reset!=null)try{reset.run();}catch(RuntimeException e){System.err.println("[Nezur] Session reset failed: "+e);}}
    public static void all(){for(Runnable reset:new ArrayList<>(resets.values()))try{reset.run();}catch(RuntimeException e){System.err.println("[Nezur] Session reset failed: "+e);}}
}
