import com.nezurstandalone.utils.PitMapManager;import com.nezurstandalone.utils.PitMapManager.PitMap;import com.nezurstandalone.utils.map.MapLocation;
import net.minecraft.client.Minecraft;import net.minecraft.world.World;import net.minecraft.util.BlockPos;import net.minecraft.init.Blocks;
public class MapCacheTest{
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static class Expected{final String map,id;final int[] b;Expected(String m,String n,int...v){map=m;id=n;b=v;}boolean contains(double x,double y,double z){x=Math.floor(x);y=Math.floor(y);z=Math.floor(z);return x>=b[0]&&y>=b[1]&&z>=b[2]&&x<=b[3]&&y<=b[4]&&z<=b[5];}}
 static Expected[] expected={new Expected("CASTLE","CASTLE_SPAWN",-23,92,-23,23,107,23),
new Expected("CASTLE","CASTLE_ABOVE_SPAWN",-28,107,-28,28,255,28),
new Expected("CASTLE","CASTLE_PIT",-23,0,-23,23,255,23),
new Expected("CASTLE","CASTLE_SEWERS",11,49,26,80,60,87),
new Expected("CASTLE","CASTLE_SEWERS",53,52,11,117,61,60),
new Expected("CASTLE","CASTLE_PORT",-130,0,8,-23,255,130),
new Expected("CASTLE","CASTLE_PORT",-23,0,23,0,255,130),
new Expected("CASTLE","CASTLE_KINGS_HALLWAY",-160,0,-6,-20,255,8),
new Expected("CASTLE","CASTLE_FOREST",-23,0,-160,0,255,-23),
new Expected("CASTLE","CASTLE_FOREST",-160,0,-160,-23,255,-6),
new Expected("CASTLE","CASTLE_CITY",0,0,23,23,255,160),
new Expected("CASTLE","CASTLE_CITY",23,0,0,160,255,160),
new Expected("CASTLE","CASTLE_FARMS",23,0,-23,160,255,0),
new Expected("CASTLE","CASTLE_FARMS",0,0,-160,160,255,-23),
new Expected("ABYSS","ABYSS_SPAWN",-20,114,-20,20,129,20),
new Expected("ABYSS","ABYSS_ABOVE_SPAWN",-25,129,-25,25,255,25),
new Expected("ABYSS","ABYSS_PIT",-20,0,-20,20,255,20),
new Expected("ABYSS","ABYSS_SHIPWRECK",-160,0,0,-20,255,160),
new Expected("ABYSS","ABYSS_SHIPWRECK",-20,0,20,0,255,160),
new Expected("ABYSS","ABYSS_GEYSER",0,0,20,20,255,160),
new Expected("ABYSS","ABYSS_GEYSER",20,0,0,160,255,160),
new Expected("ABYSS","ABYSS_TEMPLE",20,0,-20,160,255,0),
new Expected("ABYSS","ABYSS_TEMPLE",0,0,-160,160,255,-20),
new Expected("ABYSS","ABYSS_SEAWEED",-160,0,-160,-20,255,0),
new Expected("ABYSS","ABYSS_SEAWEED",-20,0,-160,0,255,-20),
new Expected("ELEMENTS","ELEMENTS_SPAWN",-20,114,-20,20,129,20),
new Expected("ELEMENTS","ELEMENTS_ABOVE_SPAWN",-25,129,-25,25,255,25),
new Expected("ELEMENTS","ELEMENTS_PIT",-20,0,-20,20,255,20),
new Expected("ELEMENTS","ELEMENTS_SKY",-160,0,0,-20,255,160),
new Expected("ELEMENTS","ELEMENTS_SKY",-20,0,20,0,255,160),
new Expected("ELEMENTS","ELEMENTS_LAVA",0,0,20,20,255,160),
new Expected("ELEMENTS","ELEMENTS_LAVA",20,0,0,160,255,160),
new Expected("ELEMENTS","ELEMENTS_MOUNTAINS",20,0,-20,160,255,0),
new Expected("ELEMENTS","ELEMENTS_MOUNTAINS",0,0,-160,160,255,-20),
new Expected("ELEMENTS","ELEMENTS_WATER",-160,0,-160,-20,255,0),
new Expected("ELEMENTS","ELEMENTS_WATER",-20,0,-160,0,255,-20),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SPAWN",-20,114,-20,20,129,20),
new Expected("FOUR_SEASONS","FOUR_SEASONS_ABOVE_SPAWN",-25,129,-25,25,255,25),
new Expected("FOUR_SEASONS","FOUR_SEASONS_PIT",-20,0,-20,20,255,20),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SPRING",-160,0,0,-20,255,160),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SPRING",-20,0,20,0,255,160),
new Expected("FOUR_SEASONS","FOUR_SEASONS_WINTER",0,0,20,20,255,160),
new Expected("FOUR_SEASONS","FOUR_SEASONS_WINTER",20,0,0,160,255,160),
new Expected("FOUR_SEASONS","FOUR_SEASONS_AUTUMN",20,0,-20,160,255,0),
new Expected("FOUR_SEASONS","FOUR_SEASONS_AUTUMN",0,0,-160,160,255,-20),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SUMMER_CAVE",-300,0,-300,35,100,-120),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SUMMER",-160,0,-160,-20,255,0),
new Expected("FOUR_SEASONS","FOUR_SEASONS_SUMMER",-20,0,-160,0,255,-20),
new Expected("GENESIS","GENESIS_SPAWN",-20,86,-20,20,101,20),
new Expected("GENESIS","GENESIS_ABOVE_SPAWN",-36,101,-36,36,255,36),
new Expected("GENESIS","GENESIS_ANGEL_SPAWN",-82,62,60,-60,75,82),
new Expected("GENESIS","GENESIS_DEMON_SPAWN",60,62,-82,82,80,-58),
new Expected("GENESIS","GENESIS_PIT",-20,0,-20,20,255,20),
new Expected("GENESIS","GENESIS_PALACE",-160,0,0,-20,255,160),
new Expected("GENESIS","GENESIS_PALACE",-20,0,20,0,255,160),
new Expected("GENESIS","GENESIS_GARDEN",0,0,20,20,255,160),
new Expected("GENESIS","GENESIS_GARDEN",20,0,0,160,255,160),
new Expected("GENESIS","GENESIS_FORTRESS",20,0,-20,160,255,0),
new Expected("GENESIS","GENESIS_FORTRESS",0,0,-160,160,255,-20),
new Expected("GENESIS","GENESIS_BADLANDS",-160,0,-160,-20,255,0),
new Expected("GENESIS","GENESIS_BADLANDS",-20,0,-160,0,255,-20)};
 static void compare(PitMap map,double x,double y,double z){String want=null;for(Expected e:expected)if(e.map.equals(map.name())&&e.contains(x,y,z)){want=e.id;break;}MapLocation got=MapLocation.at(map,x,y,z);check(java.util.Objects.equals(want,got==null?null:got.name()),"Roni region differential "+map+" "+x+","+y+","+z);}
 public static void main(String[] args){Minecraft mc=Minecraft.getMinecraft();
 int[][] positions={{-17,87,0},{-14,96,0},{-13,115,0},{-14,115,0},{-13,115,0}};
 PitMap[] maps={PitMap.GENESIS,PitMap.CASTLE,PitMap.ELEMENTS,PitMap.FOUR_SEASONS,PitMap.ABYSS};
 for(int i=0;i<maps.length;i++){World w=new World();mc.theWorld=w;w.blocks.put(new BlockPos(positions[i][0],positions[i][1],positions[i][2]),Blocks.enchanting_table);
 if(maps[i]==PitMap.ELEMENTS)w.blocks.put(new BlockPos(-13,114,7),Blocks.ender_chest);
 if(maps[i]==PitMap.ABYSS)w.blocks.put(new BlockPos(-12,114,6),Blocks.ender_chest);
 check(PitMapManager.getCurrentMap()==maps[i],"Roni map marker "+maps[i]);int probes=w.probes;for(int k=0;k<20;k++)PitMapManager.getCurrentMap();check(probes==w.probes,"recognized map cached");}
 World slow=new World();mc.theWorld=slow;check(PitMapManager.getCurrentMap()==PitMap.UNKNOWN,"unloaded map unknown");
 int probes=slow.probes;for(int i=0;i<10;i++)PitMapManager.detectMap();check(probes==slow.probes,"one attempt per tick");
 for(int i=0;i<240;i++){mc.thePlayer.ticksExisted++;PitMapManager.detectMap();}
 check(slow.reads==0,"never reads an unloaded marker");slow.blocks.put(new BlockPos(-17,87,0),Blocks.enchanting_table);mc.thePlayer.ticksExisted++;
 check(PitMapManager.getCurrentMap()==PitMap.GENESIS,"late chunks still detect beyond old timeout");
 PitMapManager.setPitMap(PitMap.CASTLE);check(PitMapManager.getSpawnY()==95&&PitMapManager.getMidY()==71,"Roni castle heights");
 check(PitMapManager.getZone(20,55,40).equals("Sewer"),"real sewer pocket");check(PitMapManager.getZone(0,55,30).equals("Port"),"no old oversized sewer false positive");
 check(PitMapManager.getZone(11,61,26).equals("City"),"above sewer pocket is city");
 check(PitMapManager.getZone(0,107.9,0).equals("Spawn"),"Roni floor and first matching boundary");
 check(PitMapManager.getZone(-23.1,95,0).equals("King's Hallway"),"negative coordinate flooring");
 PitMapManager.setPitMap(PitMap.GENESIS);check(PitMapManager.isInSpawn(70,70,-70)&&PitMapManager.isInSpawn(-70,70,70),"both Genesis faction spawns protected");
 PitMapManager.setCustomCoords(100,68,8,-9);PitMapManager.setPitMap(PitMap.CUSTOM);
 check(PitMapManager.getSpawnY()==100&&PitMapManager.getMidY()==68&&PitMapManager.getMidCenterX()==8&&PitMapManager.getMidCenterZ()==-9,"custom coordinate support");
 check(PitMapManager.isInSpawn(1000,100,1000),"Roni custom Y-only spawn semantics");
 mc.theWorld=new World();check(PitMapManager.getCurrentMap()==PitMap.UNKNOWN,"custom selection clears on world change");
 java.util.Random random=new java.util.Random(71);
 for(PitMap map:maps)for(int i=0;i<5000;i++)compare(map,random.nextDouble()*650-325,random.nextDouble()*280-10,random.nextDouble()*650-325);
 double[] delta={-.01,0,.99,1.01};
 for(Expected e:expected)for(int corner=0;corner<8;corner++)for(double d:delta)compare(PitMap.valueOf(e.map),e.b[(corner&1)==0?0:3]+d,e.b[(corner&2)==0?1:4]+d,e.b[(corner&4)==0?2:5]+d);
 check(MapLocation.at(PitMap.CORALS,40,80,40)==MapLocation.at(PitMap.ABYSS,40,80,40),"legacy Corals aliases Abyss");
 check(MapLocation.at(PitMap.CASTLE,Double.NaN,10,10)==null,"reject invalid coordinates");

 // Regression: the automation spawn predicate used to disagree with the imported region table.
 for(PitMap map:maps){
  PitMapManager.setPitMap(map);
  for(int x=-40;x<=40;x+=4)for(int y=30;y<150;y++){
   String zone=PitMapManager.getZone(x+.5,y+.5,0);
   boolean spawn=zone.equals("Spawn")||zone.equals("Overspawn")||zone.equals("Angel")||zone.equals("Demon");
   check(PitMapManager.isInSpawn(x+.5,y+.5,0)==spawn,"Shared spawn boundary "+map+" "+x+","+y);
   check(PitMapManager.isInMainSpawn(x+.5,y+.5,0)==(zone.equals("Spawn")||zone.equals("Overspawn")),"NPC spawn boundary "+map);
  }
 }
 PitMapManager.setPitMap(PitMap.CASTLE);
 check(PitMapManager.isInSpawn(0,92.5,0),"Lower Castle spawn ledge does not trigger /oof");
 check(PitMapManager.isInSpawn(26,130,0),"Castle roof is safe spawn for perk restoration");
 PitMapManager.setPitMap(PitMap.GENESIS);
 check(!PitMapManager.isInMainSpawn(70,70,-70),"Demon safe area is not Contractor NPC spawn");
 // Both marker sets survive a furniture change, and explicit Roni markers take priority.
 int[][] old={{14,85,-13},{18,95,-9},{-7,113,-10},{-87,85,-51},{-2,113,11}};
 net.minecraft.block.Block[] oldBlocks={Blocks.nether_brick,Blocks.double_stone_slab,Blocks.sandstone,Blocks.planks,Blocks.sand};
 PitMap[] oldMaps={PitMap.GENESIS,PitMap.CASTLE,PitMap.ELEMENTS,PitMap.FOUR_SEASONS,PitMap.ABYSS};
 for(int i=0;i<old.length;i++){
  World w=new World();w.blocks.put(new BlockPos(old[i][0],old[i][1],old[i][2]),oldBlocks[i]);mc.theWorld=w;
  check(PitMapManager.getCurrentMap()==oldMaps[i],"Legacy marker fallback "+oldMaps[i]);
 }
 World priority=new World();priority.blocks.put(new BlockPos(14,85,-13),Blocks.nether_brick);priority.blocks.put(new BlockPos(-14,96,0),Blocks.enchanting_table);mc.theWorld=priority;
 check(PitMapManager.getCurrentMap()==PitMap.CASTLE,"Roni furniture wins over fallback");
 // Old fixed Y=31..75 scan targeted underground on all three maps with a mid around Y=82.
 for(PitMap map:maps){
  World w=new World();w.columnLoaded=true;mc.theWorld=w;PitMapManager.setPitMap(map);
  int floorY=PitMapManager.getMidY()-1;
  w.blocks.put(new BlockPos(0,floorY,0),Blocks.sandstone);
  w.blocks.put(new BlockPos(0,PitMapManager.getSpawnY()-1,0),Blocks.sandstone);
  BlockPos landing=PitMapManager.findMidLanding(.5,.5);
  check(landing!=null&&landing.getY()==floorY+1,"Actual mid floor below spawn "+map);
  w.blocks.put(new BlockPos(0,floorY+2,0),Blocks.sandstone);
  check(PitMapManager.findMidLanding(.5,.5).getY()==floorY+3,"Choose clear top of obstruction "+map);
  w.blocks.clear();check(PitMapManager.findMidLanding(.5,.5)==null,"No underground fallback without floor "+map);
  w.columnLoaded=false;int reads=w.reads;
  check(PitMapManager.findMidLanding(.5,.5)==null&&w.reads==reads,"No unloaded chunk reads for route "+map);
 }
 mc.theWorld=new World();((World)mc.theWorld).columnLoaded=true;
 PitMapManager.setCustomCoords(100,68,8,-9);PitMapManager.setPitMap(PitMap.CUSTOM);
 check(PitMapManager.getZone(8,68,-9).equals("Pit"),"Custom map mid region is usable by contracts");
 check(PitMapManager.getZone(8,100,-9).equals("Spawn"),"Custom map spawn region is usable by contracts");
 check(PitMapManager.getZone(8,115,-9).equals("Overspawn"),"Custom map roof remains safe");
 ((World)mc.theWorld).blocks.put(new BlockPos(8,67,-9),Blocks.sandstone);
 check(PitMapManager.findMidLanding(8.5,-8.5).getY()==68,"Custom landing uses configured mid height and centre");

 System.out.println("Roni map/detection: "+checks+" checks passed");
 }
}
