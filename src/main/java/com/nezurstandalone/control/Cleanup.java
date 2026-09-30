package com.nezurstandalone.control;
/** Runs every independent release, then propagates the first failure with remaining failures attached. */
public final class Cleanup {
    private Cleanup() { }
    public static void run(Runnable... releases) {
        Throwable failure=null;
        for(Runnable release:releases) {
            try { release.run(); }
            catch(Throwable problem) {
                if(failure==null) failure=problem;
                else if(failure!=problem) failure.addSuppressed(problem);
            }
        }
        if(failure instanceof Error) throw (Error)failure;
        if(failure instanceof RuntimeException) throw (RuntimeException)failure;
        if(failure!=null) throw new IllegalStateException("Resource cleanup failed",failure);
    }
}
