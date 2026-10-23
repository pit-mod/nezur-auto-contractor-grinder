package com.nezurstandalone.engine;

/** Binds a generic confirmation to one exact chooser click and its server menu response. */
public final class LoadoutConfirmation {
    private Object operation, source, contents, transitioned;

    public void begin(Object operation, Object source) {
        if(operation==null||source==null)throw new IllegalArgumentException("missing selection");
        clear();this.operation=operation;this.source=source;
    }
    /** Called only for a populated, active server confirmation inventory packet. */
    public void contents(Object container) {
        if(operation!=null&&container!=null&&container!=source)contents=container;
    }
    /** Called only when the outstanding chooser transition completes into a confirmation. */
    public void transitioned(Object container) {
        if(operation!=null&&container!=null&&container!=source)transitioned=container;
    }
    public boolean matches(Object operation,Object container) {
        return operation!=null&&this.operation==operation&&container!=null
                &&container!=source&&container==contents&&container==transitioned;
    }
    public void clear(){operation=source=contents=transitioned=null;}

    /** Explicit identity text must agree even when a generic Confirm is otherwise correlated. */
    public static boolean labelsMatch(String desired,Iterable<String> lore) {
        for(String line:lore) {
            String text=LoadoutPlanner.clean(line).trim();
            for(String prefix:new String[]{"Perk:","Killstreak:","Purchase:","Buying:"}) {
                if(text.regionMatches(true,0,prefix,0,prefix.length())
                        &&!LoadoutPlanner.normalize(text.substring(prefix.length()).trim())
                                .equalsIgnoreCase(LoadoutPlanner.normalize(desired)))return false;
            }
        }
        return true;
    }
}
