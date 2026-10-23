import com.nezurstandalone.control.LowestClassicPit;
import com.nezurstandalone.control.CommandGate;
import com.nezurstandalone.control.Clock;
import java.util.*;
import java.util.function.Predicate;

public final class LowestClassicPitTest {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final String LIST="Available Pit lobbies:\n• au-pit-1 — 3 players\n• eu-pit-1 — 6 players\n• eu-pit-2 — 6 players\n• eu-pit-3 — 8 players\n• pit-1 — 10 players\n• pit-2 — 6 players\n• pit-3 — 4 players\n• pit-4 — 5 players";
    private static LowestClassicPit start(List<String> sent){
        LowestClassicPit f=new LowestClassicPit();f.context(new Object(),true);
        check(f.tick(true,true,false,true,null,0,c->{sent.add(c);return true;}),"list owns lobby recovery");
        check(sent.equals(Arrays.asList("/play pit list")),"request list first");return f;
    }
    private static boolean tick(LowestClassicPit f,long now,List<String> sent){return f.tick(true,true,false,true,null,now,c->{sent.add(c);return true;});}
    public static void main(String[] args) {
        List<String> sent=new ArrayList<>();LowestClassicPit f=start(sent);f.message(LIST,100);
        check(tick(f,199,sent)&&sent.size()==1,"briefly collect all streamed rows before choosing");
        check(tick(f,200,sent)&&sent.get(1).equals("/play pit au-pit-1"),"captured screenshot chooses au-pit-1 within 100ms of final row");
        for(int i=0;i<50;i++)check(tick(f,1101+i,sent)&&sent.size()==2,"no duplicate transfer while waiting");
        check(!f.tick(false,true,true,false,"au-pit-1",1200,c->{throw new AssertionError(c);}),"selected instance arrival completes");
        check(!f.tick(true,true,true,false,"au-pit-1",30000,c->{throw new AssertionError(c);}),"no periodic hopping after success");
        check(tick(f,31000,sent)&&sent.get(2).equals("/play pit list"),"later lobby recovery requests a fresh list");

        sent=new ArrayList<>();f=start(sent);f.message(LIST,10);
        check(!f.tick(true,true,true,false,"au-pit-1",1010,c->{throw new AssertionError(c);}),"already on lowest skips transfer");
        sent=new ArrayList<>();f=start(sent);f.message("Available Pit lobbies:\n• eu-pit-1 — 3 players\n• pit-4 — 3 players",1);
        check(!f.tick(true,true,true,false,"pit-4",1001,c->{throw new AssertionError(c);}),"equal minimum retains current server");
        sent=new ArrayList<>();f=start(sent);f.message("Available Pit lobbies:\n• eu-pit-1 — 0 players\n• au-pit-1 — 1 player",1);tick(f,1001,sent);
        check(sent.get(1).equals("/play pit eu-pit-1"),"zero players is valid and beats one player");

        sent=new ArrayList<>();f=start(sent);f.message("• au-pit-1 — 0 players",1);
        check(!tick(f,8000,sent)&&sent.size()==1,"rows without our response header cannot trigger a join; bounded fallback");
        f.message(LIST,8001);check(!tick(f,9001,sent)&&sent.size()==1,"late response cannot revive timed-out work");
        sent=new ArrayList<>();f=start(sent);f.message("Available Pit lobbies:",1);
        for(String bad:Arrays.asList("Bob: • au-pit-1 — 0 players","• au-pit-1; /oof — 0 players","• eu-lobby — 0 players","• au-pit-1 — -1 players","• au-pit-1 — 99999 players","• pit-0 — 0 players"))f.message(bad,2);
        check(!tick(f,8000,sent)&&sent.size()==1,"invalid, spoof-prefixed and injected commands never become candidates");

        sent=new ArrayList<>();f=start(sent);f.message("§eAvailable Pit lobbies:\n§b• au-pit-1 — §73 players",1);tick(f,1001,sent);
        check(sent.get(1).equals("/play pit au-pit-1"),"colored rows parsed");
        sent=new ArrayList<>();f=start(sent);f.message("Available Pit lobbies:\n• au-pit-1 — 3 players",1);f.message("• pit-4 — 1 player",900);
        check(tick(f,999,sent)&&sent.size()==1,"later streamed row resets settle interval");tick(f,1000,sent);
        check(sent.get(1).equals("/play pit pit-4"),"later lower population included");

        f=new LowestClassicPit();Object connection=new Object();f.context(connection,true);int[] refused={0};
        check(f.tick(true,true,false,true,null,0,c->{refused[0]++;return false;}),"list refusal remains pending");
        f.message(LIST,1);check(f.tick(true,true,false,true,null,2000,c->false),"response to refused request cannot advance");
        List<String> commands=new ArrayList<>();f.tick(true,true,false,true,null,2001,c->{commands.add(c);return true;});
        check(commands.equals(Arrays.asList("/play pit list")),"retry sends list, never speculative server command");
        f.message(LIST,2002);f.tick(true,true,false,true,null,3002,c->false);
        check(f.tick(true,true,false,true,null,3003,c->{commands.add(c);return true;})&&commands.get(1).equals("/play pit au-pit-1"),"join gate refusal retains exact selected target");

        sent=new ArrayList<>();f=start(sent);f.message(LIST,1);
        check(f.tick(true,false,false,true,null,1001,c->{throw new AssertionError(c);}),"GUI/item/lease guard defers target command");
        check(!f.tick(true,true,false,true,null,10000,c->{throw new AssertionError(c);}),"selection expires rather than using an old population later");
        f.context(null,false);f.message(LIST,1);check(!tick(f,2,new ArrayList<>()),"disabled/disconnected flow cannot send");
        f.context(connection,true);check(f.belongsTo(connection)&&!f.belongsTo(new Object()),"connection identity bounds chat evidence");
        f.context(new Object(),true);f.message(LIST,3);commands.clear();
        f.tick(true,true,false,true,null,1003,c->{commands.add(c);return true;});
        check(commands.equals(Arrays.asList("/play pit list")),"new connection discards old list/target");

        sent=new ArrayList<>();f=start(sent);
        for(int i=0;i<3;i++) {
            long now=i*32000L;f.message(LIST,now+1);tick(f,now+1001,sent);tick(f,now+21001,sent);
            if(i<2)tick(f,now+32000,sent);
        }
        check(!tick(f,100000,sent)&&sent.size()==6,"three unconfirmed transfers stop and allow ordinary recovery");
        check(LowestClassicPit.classicHost("mc.pitclassic.net:25565")&&!LowestClassicPit.classicHost("hypixel.net")&&!LowestClassicPit.classicHost("pitclassic.net.evil.com"),"restricted to Classic Pit hosts");
        check("au-pit-1".equals(LowestClassicPit.currentServer(Arrays.asList("10/17/26 au-pit-1","Streak: 50"))),"current server from own scoreboard");
        check(LowestClassicPit.currentServer(Arrays.asList("10/17/26 pit-1","10/17/26 pit-2"))==null,"ambiguous current instance ignored");
        check(LowestClassicPit.currentServer(Arrays.asList("Bob: 10/17/26 pit-1"))==null,"unrecognized current-server text ignored");
        final long[] nanos={0};Clock clock=()->nanos[0];CommandGate gate=new CommandGate(clock);Object owner=new Object();
        LowestClassicPit actual=new LowestClassicPit();actual.context(new Object(),true);List<String> dispatched=new ArrayList<>();
        Predicate<String> dispatch=c->{if(!gate.acquire(owner,c,1,true))return false;dispatched.add(c);return true;};
        actual.tick(true,true,false,true,null,0,dispatch);actual.message(LIST,1);nanos[0]=101_000_000L;
        actual.tick(true,true,false,true,null,101,dispatch);
        check(dispatched.equals(Arrays.asList("/play pit list")),"list-to-join waits for the requested four-second delay");
        nanos[0]=3_999_000_000L;actual.tick(true,true,false,true,null,3999,dispatch);
        check(dispatched.size()==1,"no target join before four seconds");
        nanos[0]=4_000_000_000L;actual.tick(true,true,false,true,null,4000,dispatch);
        check(dispatched.equals(Arrays.asList("/play pit list","/play pit au-pit-1")),"exact target joins at four seconds without expiring its selection");
        check(!gate.acquire(owner,"/play pit pit-3",1,true),"repeated Classic commands cannot bypass four-second spacing");
        check(!gate.acquire(owner,"/play pit list",2,true),"session changes cannot bypass the Classic request delay");
        gate.throttle(15_000_000_000L);check(!gate.acquire(owner,"/play pit list",1,true),"explicit server throttle remains authoritative");
        CommandGate ordinary=new CommandGate(clock);
        check(ordinary.acquire(owner,"/play pit list",1)&&!ordinary.acquire(owner,"/play pit pit-3",1),"ordinary server behavior retains its normal travel gate");
        check(!CommandGate.classicPitCommand("/play pit pit-1; /oof")&&!CommandGate.classicPitCommand("/oof"),"fast path only accepts exact Pit list/join commands");
        System.out.println("LowestClassicPit: "+checks+" screenshot, list parsing, minimum choice, timing, gate, connection and recovery checks passed.");
    }
}
