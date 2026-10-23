package com.nezurstandalone.control;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
/** Local, bounded diagnostic telemetry. No network, account, chat or item-NBT collection. */
public final class GrinderDiagnostics {
    private static volatile boolean active;
    private static final Object legacyOwner=new Object();
    private static final java.util.Set<Object> enabled=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
    private static final java.util.ArrayDeque<String> history=new java.util.ArrayDeque<>();
    private static File directory;
    private static long nextSample, nextRoutineCapture;
    private static final AtomicLong sequence=new AtomicLong(), dropped=new AtomicLong();
    private static final int LIMIT=4*1024*1024;
    private static boolean warned;
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(256), r->{Thread t=new Thread(r,"Nezur-Diagnostics");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static { Runtime.getRuntime().addShutdownHook(new Thread(()->{writer.shutdown();try{writer.awaitTermination(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Nezur-Diagnostics-Flush")); }
    private GrinderDiagnostics(){}
    public static void setActive(boolean value,File dir){
        configure(legacyOwner,value,dir);
    }
    public static void configure(Object owner,boolean value,File dir){
        if(directory==null)directory=dir;
        if(value)enabled.add(owner);else enabled.remove(owner);
        boolean requested=!enabled.isEmpty();
        if(requested==active)return;
        if(requested){active=true;nextSample=0;nextRoutineCapture=0;record("SESSION_START","schema=2 correction_is_not_confirmed_anticheat=true");}
        else {record("SESSION_END","diagnostics_disabled=true");active=false;history.clear();}
    }
    public static boolean sampleDue(){long now=System.nanoTime();if(!active||now<nextSample)return false;nextSample=now+250000000L;return true;}
    public static boolean isActive(){return active;}
    public static void record(String event,String details){
        if(!active||directory==null)return;
        String line=System.currentTimeMillis()+"\t"+System.nanoTime()+"\t"+sequence.incrementAndGet()+"\t"+safe(event)+"\t"+safe(details)+"\tdropped="+dropped.get()+"\n";
        if(history.size()==128)history.removeFirst();history.addLast(line);
        try{writer.execute(()->append(line));}catch(RejectedExecutionException e){dropped.incrementAndGet();}
    }
    /** Snapshot an immutable bounded prelude before the asynchronous writer sees it. */
    public static void capture(String event,String details){
        if(!active)return;
        String prelude=String.join("",history);
        try{writer.execute(()->append("HISTORY_BEGIN\n"+prelude+"HISTORY_END\n"));}catch(RejectedExecutionException e){dropped.incrementAndGet();}
        record(event,details);
    }
    /** Keep every routine event, but bound repeated full preludes so they cannot erase a correction in seconds. */
    public static void captureRoutine(String event,String details){
        if(!active||directory==null)return;
        long now=System.nanoTime();
        if(nextRoutineCapture!=0 && now-nextRoutineCapture<0){record(event,details);return;}
        nextRoutineCapture=now+TimeUnit.SECONDS.toNanos(5);
        capture(event,details);
    }
    private static String safe(String s){if(s==null)return "null";s=s.replace("\\","\\\\").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");return s.length()>8192?s.substring(0,8192):s;}
    private static void append(String line){
        try{
            Files.createDirectories(directory.toPath());Path file=directory.toPath().resolve("autogrinder.log");
            byte[] bytes=line.getBytes(StandardCharsets.UTF_8);
            if(Files.exists(file)&&Files.size(file)+bytes.length>LIMIT){
                Path one=directory.toPath().resolve("autogrinder.1.log"),two=directory.toPath().resolve("autogrinder.2.log");
                if(Files.exists(one))Files.move(one,two,StandardCopyOption.REPLACE_EXISTING);
                Files.move(file,one,StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(file,bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException|SecurityException e){dropped.incrementAndGet();if(!warned){warned=true;System.err.println("[Nezur Diagnostics] Cannot write local logs: "+e.getClass().getSimpleName());}}
    }
    /** Test/support barrier only; never called by the game tick. */
    public static void flush(long timeoutMillis)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        FutureTask<Void> barrier=new FutureTask<Void>(()->null);
        writer.prestartCoreThread();
        if(!writer.getQueue().offer(barrier,timeoutMillis,TimeUnit.MILLISECONDS))throw new TimeoutException("Diagnostic writer queue stayed full");
        barrier.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
    }

    public static void gameDump(String json){
        if(!active||directory==null)return;
        final File targetDirectory=directory;
        try{writer.execute(()->{try{Files.createDirectories(targetDirectory.toPath());
            Files.write(targetDirectory.toPath().resolve("autogrinder-game-dump.json"),json.getBytes(StandardCharsets.UTF_8));
        }catch(IOException|SecurityException error){dropped.incrementAndGet();}});}catch(RejectedExecutionException full){dropped.incrementAndGet();}
    }
}
