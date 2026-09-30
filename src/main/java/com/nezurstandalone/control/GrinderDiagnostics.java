package com.nezurstandalone.control;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
/** Local, bounded diagnostic telemetry. No network, account, chat or item-NBT collection. */
public final class GrinderDiagnostics {
    private static volatile boolean active;
    private static File directory;
    private static long nextSample;
    private static final AtomicLong sequence=new AtomicLong(), dropped=new AtomicLong();
    private static final int LIMIT=4*1024*1024;
    private static boolean warned;
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(256), r->{Thread t=new Thread(r,"Nezur-Diagnostics");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static { Runtime.getRuntime().addShutdownHook(new Thread(()->{writer.shutdown();try{writer.awaitTermination(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Nezur-Diagnostics-Flush")); }
    private GrinderDiagnostics(){}
    public static void setActive(boolean value,File dir){
        if(directory==null)directory=dir;
        if(value==active)return;
        if(value){active=true;nextSample=0;record("SESSION_START","schema=1 correction_is_not_confirmed_anticheat=true");}
        else {record("SESSION_END","grinder_disabled=true");active=false;}
    }
    public static boolean sampleDue(){long now=System.nanoTime();if(!active||now<nextSample)return false;nextSample=now+250000000L;return true;}
    public static boolean isActive(){return active;}
    public static void record(String event,String details){
        if(!active||directory==null)return;
        String line=System.currentTimeMillis()+"\t"+System.nanoTime()+"\t"+sequence.incrementAndGet()+"\t"+safe(event)+"\t"+safe(details)+"\tdropped="+dropped.get()+"\n";
        try{writer.execute(()->append(line));}catch(RejectedExecutionException e){dropped.incrementAndGet();}
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
    public static void flush(long timeoutMillis)throws Exception{writer.submit(()->{}).get(timeoutMillis,TimeUnit.MILLISECONDS);}
}
