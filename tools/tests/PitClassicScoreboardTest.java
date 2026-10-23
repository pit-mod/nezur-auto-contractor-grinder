import com.nezurstandalone.contract.*;import java.util.*;
public final class PitClassicScoreboardTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
 List<String> captured=Arrays.asList("10/21/26 eu-pit-2","","Prestige: X","Level: [82]","Needed XP: 786","","Gold: 1,647.39g","","Status: Idling","","mc.pitclassic.net");
 ContractScoreboard.Snapshot s=ContractScoreboard.parse("§e§lTH§f§lE§e§l PIT CLASSIC",captured,true);
 check(s.validPit&&s.readable&&!s.active,"Captured Classic idle board accepted without inventing a contract");
 check(ContractScoreboard.normalSidebar(captured),"Normal Classic sidebar ready");
 check(!ContractScoreboard.parse("THE PIT CLASSIC",captured).validPit,"Default Hypixel parser unchanged");
 check(!ContractScoreboard.parse("HYPIXEL",captured,true).validPit,"Lobby title remains invalid");
 s=ContractScoreboard.parse("THE PIT CLASSIC",Arrays.asList("Kills: 2/10","Contract: 04:28"),true);
 check(s.active&&s.current==2&&s.target==10&&s.remainingSeconds==268,"Classic active contract progress recognized");
 s=ContractScoreboard.parse("THE HYPIXEL PIT",Arrays.asList("Lava kills: 2/7","Contract: 04:28"));
 check(s.validPit&&s.active&&s.type==ContractOffer.Type.LAVA_KILLS,"Hypixel contract grammar retained");
 System.out.println("Pit Classic scoreboard: "+checks+" captured-board and isolation checks passed");
 }
}
