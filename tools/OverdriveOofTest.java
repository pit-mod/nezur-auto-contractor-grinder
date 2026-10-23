import com.nezurstandalone.control.OverdriveOof;
import com.nezurstandalone.control.ServerMessages;
import java.util.Arrays;

public final class OverdriveOofTest {
    private static int checks,sends;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static String message(String name){return "MEGASTREAK! [52] "+name+" activated OVERDRIVE";}
    public static void main(String[] args) {
        check(ServerMessages.overdrive(message("down5bands"),"down5bands"),"user's captured message");
        for(String name:new String[]{"Alice","another_user","A123","DifferentName"})
            check(ServerMessages.overdrive(message(name),name),"current username may vary");
        check(ServerMessages.overdrive("MEGASTREAK! [IV-120] Alice activated OVERDRIVE!","Alice"),"prestige level and punctuation");
        check(!ServerMessages.overdrive(message("Alice2"),"Alice"),"username must match exactly");
        check(!ServerMessages.overdrive("Bob: "+message("Alice"),"Alice"),"player chat prefix cannot activate option");
        check(!ServerMessages.overdrive("MEGASTREAK! [52] Alice activated BEASTMODE","Alice"),"other megastreak ignored");
        check(!ServerMessages.overdrive(message("Alice")+" extra","Alice"),"unrecognized suffix ignored");
        check(!ServerMessages.overdrive(null,"Alice")&&!ServerMessages.overdrive(message("Alice"),null),"missing text/identity ignored");
        OverdriveOof flow=new OverdriveOof();
        check(!flow.message(message("Alice"),"Alice",false,true,0),"disabled option does nothing");
        check(!flow.message(message("Alice"),"Alice",true,false,0),"non-system chat ignored");
        check(!flow.message(message("Bob"),"Alice",true,true,0),"another player's activation ignored");
        check(flow.message(message("Alice"),"Alice",true,true,0)&&flow.pending(),"own activation queued");
        check(!flow.message(message("Alice"),"Alice",true,true,1),"duplicate activation not requeued");
        check(!flow.tick(true,true,false,false,false,1,()->{sends++;return true;})&&sends==0,"GUI/healing/lease guard defers command");
        check(flow.tick(true,true,false,false,true,2,()->{sends++;return false;})&&flow.pending(),"command gate refusal preserves pending request");
        check(flow.tick(true,true,false,false,true,3,()->{sends++;return true;})&&flow.sent()&&!flow.pending(),"accepted command pauses combat until respawn");
        int once=sends;
        for(int i=4;i<30;i++)check(flow.tick(true,true,false,false,true,i,()->{sends++;return true;})&&sends==once,"sent request never repeats on ticks");
        check(!flow.tick(true,true,true,false,true,30,()->true)&&!flow.sent(),"spawn arrival clears pending gameplay barrier");
        check(flow.message(message("Alice"),"Alice",true,true,31),"next life's activation can queue again");
        check(!flow.tick(true,true,false,true,true,32,()->true)&&!flow.pending(),"natural death cancels pending command");
        flow.message(message("Alice"),"Alice",true,true,100);
        check(!flow.tick(false,true,false,false,true,101,()->true)&&!flow.pending(),"turning off option cancels request");
        flow.message(message("Alice"),"Alice",true,true,200);
        check(!flow.tick(true,false,false,false,true,201,()->true)&&!flow.pending(),"leaving Pit cancels request");
        flow.message(message("Alice"),"Alice",true,true,300);flow.reset();
        check(!flow.tick(true,true,false,false,true,301,()->true),"disable/disconnect reset cannot issue stale command");
        flow.message(message("Alice"),"Alice",true,true,400);
        check(!flow.tick(true,true,false,false,true,45400,()->true)&&!flow.pending(),"deferred command expires instead of killing a later life");
        check(!flow.message(message("Alice"),"Alice",true,true,45401),"late duplicate cannot restart expired activation");
        flow.reset();flow.message(message("Alice"),"Alice",true,true,50000);flow.tick(true,true,false,false,true,50001,()->true);
        flow.rejected(50002);check(flow.pending()&&!flow.sent(),"server throttle restores request for normal command backoff");
        check(!flow.tick(true,true,false,false,false,50003,()->true),"throttle safety guard prevents immediate retry");
        check(flow.tick(true,true,false,false,true,65003,()->true)&&flow.sent(),"retry after server backoff");
        check(!flow.tick(true,true,false,false,true,75003,()->true)&&!flow.sent(),"missing death response has a bounded pause");
        check(!flow.message(message("Alice"),"Alice",true,true,75004),"unacknowledged command never loops into repeated deaths");
        flow.reset();
        check(flow.sidebar(Arrays.asList("THE PIT CLASSIC","Level: [85]","Gold: 10,065.62g","\u00a7fStatus: \u00a7cOverdrive","Streak: 53"),true,true,false,false,100000),"user screenshot: sidebar queues missed Overdrive activation");
        check(flow.tick(true,true,false,false,true,100001,()->true)&&flow.sent(),"sidebar queues actual command through the same gate");
        check(!flow.message(message("Alice"),"Alice",true,true,100002),"later announcement cannot duplicate sidebar command");
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,110002),"persistent Overdrive after response timeout never requeues");
        flow.lifeEnded();
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,110003),"stale status after death cannot kill the next life");
        check(!flow.sidebar(Arrays.asList("Gold: 300"),true,true,false,false,110004),"missing status does not rearm a stale life");
        check(!flow.sidebar(Arrays.asList("Status: Fighting"),true,true,false,false,110005),"new non-Overdrive life rearms sidebar without queuing");
        check(flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,110006),"new life's Overdrive triggers again");
        flow.reset();
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),false,true,false,false,0),"disabled option ignores sidebar");
        check(flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,1),"enabling during an active Overdrive works");
        flow.reset();
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,false,false,false,0),"non-Pit sidebar ignored");
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,true,false,0),"spawn sidebar cannot issue oof");
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,1),"stale spawn status remains suppressed after leaving spawn");
        flow.reset();
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,true,0),"dead player cannot queue command");
        flow.reset();
        for(String text:new String[]{"Status: Beastmode","Status: Idling","Overdrive","Streak: 53","Status: Overdrive soon","Bob: Status: Overdrive"})
            check(!flow.sidebar(Arrays.asList(text),true,true,false,false,0),"exact own sidebar status required: "+text);
        check(!flow.sidebar(Arrays.asList("Status: Overdrive","Status: Fighting"),true,true,false,false,0),"conflicting sidebar status rejected");
        check(!flow.sidebar(Arrays.asList("Status: Overdrive","Status: Overdrive"),true,true,false,false,0),"duplicate sidebar status rejected");
        check(flow.sidebar(Arrays.asList("\u00a7fStatus:\u00a0\u00a7cOverdrive"),true,true,false,false,0),"formatted nonbreaking space accepted");
        check(!flow.tick(true,true,false,false,false,1,()->{throw new AssertionError("unsafe command");}),"sidebar follows GUI and healing deferral");
        flow.tick(true,true,false,false,true,2,()->true);flow.rejected(3);
        check(flow.pending()&&!flow.sent(),"sidebar command retains recognized throttle recovery");
        flow.reset();flow.message(message("Alice"),"Alice",true,true,0);
        check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,1),"chat-first plus sidebar queues one activation");
        flow.reset();check(flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,0),"fresh sidebar activation before correction");
        flow.clear();check(!flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,1),"ordinary correction/session cleanup preserves the activation latch");
        check(!flow.pending(),"ordinary cleanup cannot leave queued death work");
        flow.sidebar(Arrays.asList("Status: Fighting"),true,true,false,false,2);
        check(flow.sidebar(Arrays.asList("Status: Overdrive"),true,true,false,false,3),"new non-Overdrive to Overdrive transition can rearm after cleanup");
        System.out.println("OverdriveOof: "+checks+" chat/sidebar, option, deduplication, command backoff and lifecycle checks passed.");
    }
}
