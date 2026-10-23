import com.nezurstandalone.utils.SidebarText;
import com.nezurstandalone.engine.LoadoutPlanner;
import com.nezurstandalone.engine.LoadoutGoldObserver;
import java.util.*;

public final class SidebarTextTest {
 private static int checks;
 private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static String line(String prefix,String entry,String suffix){return LoadoutPlanner.clean(prefix+SidebarText.entry(entry,true)+suffix);}
 public static void main(String[] args){
  // Exact prefix, entry and suffix captured from the running Forge client.
  String real=line("Gold: \u00a765,699.54","\u26bd","\u00a76g");
  ok(real.equals("Gold: 5,699.54g"),"live football entry between decimal and currency");
  ok(Double.isNaN(LoadoutPlanner.scoreboardGold(Arrays.asList("Gold: 5,699.54\u26bdg"))),"old canonical raw line reproduces the failure");
  for(String marker:new String[]{"\u26bd","\uD83D\uDC79","\uD83C\uDFC0","\uD83D\uDC7E","\u26bd\uFE0F"}) {
   String expected="Gold: 5,699.54g";
   for(int cut=0;cut<=expected.length();cut++){
    String text=line(expected.substring(0,cut),marker,expected.substring(cut));
    ok(text.equals(expected),"marker can occur anywhere between team fragments");
    LoadoutPlanner p=new LoadoutPlanner(log->{});p.context("test");p.requirements(55,false);
    p.define(LoadoutPlanner.Kind.PERK,"Golden Heads",500,10,0);p.configure(LoadoutPlanner.Kind.PERK,1,"Golden Heads",true);
    new LoadoutGoldObserver().observe(p,new Object(),new Object(),new Object(),true,true,Arrays.asList(text),100);
    ok(p.balance(100)==5699.54&&p.next(null,100)!=null,"real sidebar amount enables affordable NPC trip");
   }
  }
  ok(line("Status: Bounti","\uD83D\uDC79","ed").equals("Status: Bountied"),"status reconstructed");
  ok(line("Status: Over","\uD83D\uDC79","drive").equals("Status: Overdrive"),"Overdrive status reconstructed");
  ok(line("Level: [55","\uD83D\uDC7E","]").equals("Level: [55]"),"level brackets reconstructed");
  ok(line("Gold: 5,529.","\u00a76\u00a7r","\u00a7694g").equals("Gold: 5,529.94g"),"Classic format-only key preserves suffix");
  ok(SidebarText.entry("5,699.54g",true).equals("5,699.54g"),"real numeric entry retained");
  ok(SidebarText.entry("player_name",true).equals("player_name"),"real textual entry retained");
  ok(SidebarText.entry("12\u26bd34",true).equals("12\u26bd34"),"mixed numeric entry never silently joins amounts");
  ok(SidebarText.entry("\u26bd",false).equals("\u26bd"),"un-teamed row is not assumed to be a dummy key");
  System.out.println("SidebarTextTest PASS checks="+checks);
 }
}
