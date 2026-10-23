import com.nezurstandalone.control.*;
import com.nezurstandalone.utils.MenuTransition;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Production interruption/transaction boundaries; no synthetic server acceptance claims. */
public final class AutomationBoundaryTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        CorrectionWindow corrections=new CorrectionWindow();Object world=new Object(),player=new Object();
        corrections.context(world,player);
        check(corrections.allowed(0,20),"Fresh context accepts validated work");
        corrections.corrected(1_000_000_000L,20);
        check(!corrections.allowed(1_249_999_999L,40),"Elapsed time bound survives catch-up ticks");
        check(!corrections.allowed(9_000_000_000L,22),"Elapsed time alone cannot bypass the three-tick settling bound");
        check(corrections.allowed(1_250_000_000L,23),"Resume requires both elapsed time and player ticks");
        // Reproduce correction storms with Grinder disabled and only AutoHeal remaining active.
        // There is no active-module flag, repetition count, threshold result or stop request.
        for(int i=0;i<100;i++){
            long now=2_000_000_000L+i*10_000_000L;int tick=40+i;
            corrections.corrected(now,tick);
            check(!corrections.allowed(now+249_999_999L,tick+4),"Every correction restarts only the settling pause");
            check(!corrections.allowed(now+300_000_000L,tick+2),"Repeated corrections retain the tick bound");
            check(corrections.allowed(now+250_000_000L,tick+3),"Any number of corrections remains resumable without a module restart");
        }
        corrections.context(new Object(),new Object());
        check(corrections.allowed(3_000_000_000L,0),"World/player replacement drops the previous pause");

        Object grinder=new Object(),heal=new Object(),swap=new Object();SlotLease slot=new SlotLease();
        check(slot.acquire(grinder,1,0,20,true),"Grinder begins a temporary weapon operation");slot.selected(2);
        long old=slot.generation();check(slot.acquire(heal,1,2,100,true),"Healing interrupts lower-priority weapon work");slot.selected(4);
        check(!slot.owns(grinder,1,4,old),"Pending attack/restore from old lease is obsolete");
        check(!slot.acquire(swap,1,4,60,true),"Armor swap cannot steal the active healing slot");
        check(slot.release(grinder,1,4,true)==4,"Interrupted cleanup cannot restore over a heal");
        check(slot.release(heal,1,4,true)==0,"Completed heal restores the root slot once");
        check(slot.acquire(heal,2,0,100,true),"Next heal has a new session");slot.selected(4);
        check(slot.release(heal,2,7,true)==7,"Manual slot selection invalidates restoration rights");
        check(slot.acquire(grinder,2,7,20,true),"New operation may use manual selection as its root");slot.selected(2);
        check(slot.release(grinder,3,2,true)==2,"Correction/new world cannot receive an obsolete restore");

        JumpLease jump=new JumpLease();
        check(jump.request("walker",1,0,true,true),"Walker requests a supported jump");
        check(!jump.request("jumpreset",1,1,true,true),"Damage jump cannot replace a pending walker sample");
        jump.cancel("jumpreset",2);check(jump.down(),"Foreign cancellation leaves the legitimate jump held");
        jump.observe(2,3,true);check(!jump.down(),"Correction generation clears pending jump input");
        check(!jump.request("jumpreset",2,4,false,true),"A fresh request cannot replace airborne knockback");

        MenuTransition menu=new MenuTransition();Object source=new Object(),next=new Object();
        menu.beginTracked(source,"chooser","after prediction",0,3,(short)7,"confirm");
        menu.confirm(3,(short)6,true);
        check(menu.observe(next,"confirm","populated",1)==MenuTransition.Result.WAITING,"Unrelated transaction cannot confirm a menu transition");
        menu.confirm(3,(short)7,true);
        check(menu.observe(next,"shop","populated",2)==MenuTransition.Result.WAITING,"Right ACK plus unrelated screen cannot advance the purchase");
        check(menu.observe(next,"confirm","populated",3)==MenuTransition.Result.CHANGED,"Exact ACK plus expected observed menu can advance");
        menu.beginTracked(source,"chooser","prediction",0,3,(short)8,"confirm");menu.confirm(3,(short)8,false);
        check(menu.failed(1),"Explicit server rejection terminates pending work");menu.clear();

        MenuResultContract inventory=MenuResultContract.vanilla(2,0,0,12,(short)11,12,40);
        check(inventory.result(2,1,0)==MenuResultContract.Result.WAITING,"Predicted sword/armor move cannot advance without a result");
        inventory.acknowledge(2,0,(short)11,true);
        check(inventory.result(2,2,0)==MenuResultContract.Result.SUCCESS,"Exact accepted native ACK confirms vanilla prediction");
        inventory.authoritative(2,0,40,false);
        check(inventory.result(2,3,0)==MenuResultContract.Result.UNKNOWN,"Contradicting inventory contents invalidate an accepted prediction");
        check(inventory.result(3,4,0)==MenuResultContract.Result.UNKNOWN,"Correction cancels in-flight inventory completion");
        inventory=MenuResultContract.vanilla(3,0,0,5,(short)12,5,10);
        check(inventory.result(3,5_000_000_000L,0)==MenuResultContract.Result.UNKNOWN,"Missing ACK times out without success or a replay permission");

        diagnostics(Paths.get(args[0]));
        System.out.println("AutomationBoundary: "+checks+" production correction, ownership, result correlation, manual override and bounded diagnostics checks passed");
    }
    private static int primaryEvents(String text,String event){
        boolean history=false;int count=0;
        for(String line:text.split("\n")){
            if(line.equals("HISTORY_BEGIN")){history=true;continue;}
            if(line.equals("HISTORY_END")){history=false;continue;}
            if(!history&&line.contains("\t"+event+"\t"))count++;
        }
        return count;
    }
    private static void diagnostics(Path directory)throws Exception{
        Files.createDirectories(directory);
        for(String name:new String[]{"autogrinder.log","autogrinder.1.log","autogrinder.2.log"})Files.deleteIfExists(directory.resolve(name));
        Object a=new Object(),b=new Object();
        GrinderDiagnostics.configure(a,false,directory.toFile());GrinderDiagnostics.record("OFF","must not write");GrinderDiagnostics.flush(5000);
        check(!Files.exists(directory.resolve("autogrinder.log")),"Diagnostics are opt-in");
        GrinderDiagnostics.configure(a,true,directory.toFile());GrinderDiagnostics.configure(b,true,directory.toFile());
        StringBuilder payload=new StringBuilder();for(int i=0;i<8200;i++)payload.append('x');
        for(int i=0;i<2000;i++)GrinderDiagnostics.record("LOAD","request="+i+" "+payload);
        GrinderDiagnostics.flush(10000);GrinderDiagnostics.record("LAST_REQUEST","id=2001");GrinderDiagnostics.flush(5000);
        GrinderDiagnostics.capture("CORRECTION","cause=unknown");GrinderDiagnostics.flush(5000);
        String text=new String(Files.readAllBytes(directory.resolve("autogrinder.log")),StandardCharsets.UTF_8);
        int start=text.lastIndexOf("HISTORY_BEGIN");
        check(start>=0 && text.substring(start).contains("LAST_REQUEST"),"Correction captures recent ordered request history");
        check(!text.substring(start).contains("request=0 "),"Old request history is evicted");
        long size=0;int files=0;
        try(java.nio.file.DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"*.log")){
            for(Path path:stream){check(Files.size(path)<=4*1024*1024,"Each rotated diagnostic file stays bounded");size+=Files.size(path);files++;}
        }
        check(files<=3&&size<=12*1024*1024,"Rotation bounds total diagnostic storage");
        GrinderDiagnostics.configure(a,false,directory.toFile());check(GrinderDiagnostics.isActive(),"Disabling one module preserves another module's diagnostics lease");
        GrinderDiagnostics.configure(b,false,directory.toFile());GrinderDiagnostics.flush(5000);
        long before=Files.size(directory.resolve("autogrinder.log"));GrinderDiagnostics.record("OFF_AGAIN","ignored");GrinderDiagnostics.flush(5000);
        check(!GrinderDiagnostics.isActive()&&Files.size(directory.resolve("autogrinder.log"))==before,"Last owner disables recording and releases history");
        // Reproduce the live server's confirmation flood without losing individual observations.
        for(String name:new String[]{"autogrinder.log","autogrinder.1.log","autogrinder.2.log"})Files.deleteIfExists(directory.resolve(name));
        GrinderDiagnostics.configure(a,true,directory.toFile());
        GrinderDiagnostics.record("REQUEST_BEFORE_FLOOD","id=kept");
        long began=System.nanoTime();
        for(int i=0;i<100;i++){
            GrinderDiagnostics.captureRoutine("ROUTINE_PING","id="+i);
            if(i%20==19)GrinderDiagnostics.flush(5000);
        }
        GrinderDiagnostics.flush(5000);
        long elapsed=System.nanoTime()-began;
        text=new String(Files.readAllBytes(directory.resolve("autogrinder.log")),StandardCharsets.UTF_8);
        int preludes=text.split("HISTORY_BEGIN",-1).length-1;
        check(primaryEvents(text,"ROUTINE_PING")==100,"Routine throttling retains every event exactly once");
        check(preludes>=1&&preludes<=elapsed/5_000_000_000L+1,"Routine confirmation flood cannot dump a full prelude for every packet");
        check(text.contains("REQUEST_BEFORE_FLOOD"),"Flood retains preceding request evidence");
        GrinderDiagnostics.capture("CRITICAL_CORRECTION","cause=unknown");GrinderDiagnostics.flush(5000);
        text=new String(Files.readAllBytes(directory.resolve("autogrinder.log")),StandardCharsets.UTF_8);
        check(text.split("HISTORY_BEGIN",-1).length-1==preludes+1&&text.contains("CRITICAL_CORRECTION"),"Critical correction snapshots bypass routine throttling");
        GrinderDiagnostics.configure(a,false,directory.toFile());GrinderDiagnostics.flush(5000);

    }
}
