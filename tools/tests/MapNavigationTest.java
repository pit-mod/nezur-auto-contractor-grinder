import com.nezurstandalone.utils.PitMapManager;
import com.nezurstandalone.utils.PitMapManager.PitMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.util.BlockPos;
import net.minecraft.init.Blocks;

/** Executes the actual route/centre methods with a recording pathfinder. */
public class MapNavigationTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static class Routes {
  Minecraft mc=Minecraft.getMinecraft();boolean returnAllowed=true;
  long midRepathAt,lastCenterUpdate;double centerX,centerZ;
  boolean mayReturnToMid(){return returnAllowed;}
  static class PathfinderManager {
   static int walks,clears;static double x,y,z;static boolean active;
   static boolean isPathing(){return active;}
   static void clear(Object owner,boolean stop){clears++;active=false;}
   static void walkTo(Object owner,double nx,double ny,double nz,boolean sprint){walks++;x=nx;y=ny;z=nz;active=true;}
  }
  // GRINDER_METHODS
 }
 static class Contractor {
  Minecraft mc=Minecraft.getMinecraft();
  // CONTRACTOR_METHOD
 }
 public static void main(String[] args){
  Minecraft mc=Minecraft.getMinecraft();
  for(PitMap map:new PitMap[]{PitMap.GENESIS,PitMap.CASTLE,PitMap.ABYSS,PitMap.ELEMENTS,PitMap.FOUR_SEASONS,PitMap.CUSTOM}){
   World w=new World();w.columnLoaded=true;mc.theWorld=w;
   PitMapManager.setCustomCoords(100,68,18,-12);PitMapManager.setPitMap(map);
   int cx=PitMapManager.getMidCenterX(),cz=PitMapManager.getMidCenterZ(),midY=PitMapManager.getMidY();
   for(int x=cx-3;x<=cx+3;x++)for(int z=cz-3;z<=cz+3;z++)w.blocks.put(new BlockPos(x,midY-1,z),Blocks.sandstone);
   Routes r=new Routes();Routes.PathfinderManager.active=false;Routes.PathfinderManager.walks=0;
   com.nezurstandalone.control.Clock.now=10000;
   r.navigateContractMid(r);
   check(Routes.PathfinderManager.walks==1&&Routes.PathfinderManager.y==midY,"Route targets real map floor "+map);
   check(Math.abs(Routes.PathfinderManager.x-cx)<=1.5&&Math.abs(Routes.PathfinderManager.z-cz)<=1.5,"Wander stays around configured centre "+map);
   r.navigateContractMid(r);check(Routes.PathfinderManager.walks==1,"Do not flood existing path "+map);
   mc.thePlayer.posX=cx;mc.thePlayer.posZ=cz;check(r.distanceFromMid()==0,"Arrival uses configured centre "+map);
   mc.thePlayer.posX=cx+4;check(r.distanceFromMid()==4,"Offset distance "+map);
   r.returnAllowed=false;r.navigateContractMid(r);check(!Routes.PathfinderManager.active,"Existing return policy respected "+map);
   r.returnAllowed=true;w.blocks.clear();com.nezurstandalone.control.Clock.now+=2000;r.navigateContractMid(r);
   check(Routes.PathfinderManager.walks==1&&!Routes.PathfinderManager.active,"Never route underground without a loaded floor "+map);
  }
  Contractor c=new Contractor();
  PitMapManager.setPitMap(PitMap.CASTLE);mc.thePlayer.posX=0;mc.thePlayer.posZ=0;
  mc.thePlayer.posY=92.5;check(c.inMainSpawn(),"Contractor accepts canonical lower spawn ledge");
  mc.thePlayer.posX=26;mc.thePlayer.posY=130;check(c.inMainSpawn(),"Contractor roof uses same region as grinder");
  PitMapManager.setPitMap(PitMap.GENESIS);mc.thePlayer.posX=70;mc.thePlayer.posY=70;mc.thePlayer.posZ=-70;
  check(!c.inMainSpawn(),"Contractor returns from faction spawn instead of searching for absent NPC");
  mc.thePlayer=null;check(!c.inMainSpawn(),"Null player during transfer is safe");
  System.out.println("Map grinder/contractor integration: "+checks+" checks passed");
 }
}
