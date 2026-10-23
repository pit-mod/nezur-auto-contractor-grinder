import com.nezurstandalone.contract.*;import java.nio.file.*;import java.nio.charset.*;import java.util.*;
public final class PitClassicMenusTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Menu {String title;List<Slot> slots=new ArrayList<>();}static class Slot {int index,id,meta;String name;List<String> lore=new ArrayList<>();}
 public static void main(String[] args)throws Exception{
 List<Menu> menus=new ArrayList<>();Menu menu=null;Slot slot=null;
 for(String raw:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)){
  String line=raw.trim();
  if(line.startsWith("MENU TITLE")){menu=new Menu();menu.title=line.substring(line.indexOf('"')+1,line.lastIndexOf('"'));menus.add(menu);slot=null;}
  else if(line.startsWith("SLOT ")){slot=new Slot();slot.index=Integer.parseInt(line.substring(5));menu.slots.add(slot);}
  else if(slot!=null&&line.startsWith("id=")){String[] parts=line.split("\\s+");slot.id=Integer.parseInt(parts[0].substring(3));slot.meta=Integer.parseInt(parts[1].substring(5));}
  else if(slot!=null&&line.startsWith("name="))slot.name=line.substring(6,line.length()-1);
  else if(slot!=null&&line.startsWith("lore:"))slot.lore.add(line.substring(5).trim());
 }
 check(menus.size()==4,"Three original captures and one latest capture loaded");
 check(menus.get(0).title.equals("Quests & Contracts"),"Classic root title matches supported menu");
 int starts=0;for(Slot x:menus.get(0).slots)if(ContractOffer.shouldStartQuest(x.name,String.join("\n",x.lore),true,true))starts++;
 check(starts==3,"All three initial quests recognized");
 Slot confirm=menus.get(1).slots.get(0);
 check(menus.get(1).title.equals("Are you sure?")&&confirm.name.equals("Confirm")&&confirm.lore.contains("Purchase: Big Time Contract")&&confirm.lore.contains("Cost: 1,000g"),"Classic purchase confirmation matches existing fields");
 List<ContractOffer> offers=new ArrayList<>();for(Slot x:menus.get(2).slots)offers.add(ContractOffer.parse(x.index,x.id,x.meta,x.name,x.lore));
 check(offers.size()==3&&offers.get(0).type==ContractOffer.Type.FISH_DIAMOND_SWORD&&offers.get(0).required==1,"Wrapped fishing objective parsed");
 check(offers.get(1).type==ContractOffer.Type.COLLECT_GOLD_INGOTS&&offers.get(1).required==25&&offers.get(1).vileAmount==2&&offers.get(1).goldReward==2674,"Wrapped gold objective/rewards parsed");
 check(offers.get(2).type==ContractOffer.Type.CHAIN_KILLS&&offers.get(2).required==3&&offers.get(2).vileAmount==0,"Chain option correctly has no Vile reward");
 ContractSelector.Choice choice=ContractSelector.choose(offers,ContractSelector.DEFAULT_PRIORITY,Arrays.asList(ContractOffer.Type.COLLECT_GOLD_INGOTS,ContractOffer.Type.CHAIN_KILLS),true,new Random(1));
 check(choice.offer==offers.get(1)&&choice.mode==ContractSelector.Mode.PREFERRED,"Playable Vile-paying gold offer wins over unsupported fishing and no-Vile chain");
 int restarts=0;boolean pending=false;for(Slot x:menus.get(3).slots){if(ContractOffer.shouldStartQuest(x.name,String.join("\n",x.lore),true,true))restarts++;if(x.name.replaceFirst("\\.+$", "").equalsIgnoreCase("Pending choice")&&x.lore.contains("Click to view choices!"))pending=true;}
 check(restarts==0,"Already-started quests are not clicked again");check(pending,"Latest pending choice recognized without buying another contract");
 System.out.println("Pit Classic F6 menus: "+checks+" captured-menu checks passed");
 }
}
