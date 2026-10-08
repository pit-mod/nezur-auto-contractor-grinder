package com.nezurstandalone.engine;

import com.nezurstandalone.engine.BlockheadPowerups;
import com.nezurstandalone.engine.BlockheadPowerups.Powerup;
import com.nezurstandalone.engine.BlockheadPowerups.PowerupType;
import com.nezurstandalone.utils.PitMapManager;
import com.nezurstandalone.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;
import java.util.*;

/** Powerup observations and painting decisions; the grinder owns movement, never event combat. */
public final class BlockheadController {
    public enum GoalKind { PAINT, POWERUP }
    public static final class Goal {
        public final BlockPos position;
        public final GoalKind kind;
        public final PowerupType pickup;
        public final Vec3 pickupPosition, titlePosition;
        public final double cooldown;
        Goal(BlockPos position, GoalKind kind, PowerupType pickup) {
            this.position=position; this.kind=kind; this.pickup=pickup;
            this.pickupPosition=new Vec3(position.getX()+.5,position.getY(),position.getZ()+.5);
            this.titlePosition=pickupPosition; this.cooldown=0;
        }
        Goal(Powerup powerup) {
            position=new BlockPos(powerup.pos); kind=GoalKind.POWERUP; pickup=powerup.type;
            pickupPosition=powerup.pos; titlePosition=powerup.titlePos; cooldown=powerup.cooldown;
        }
    }
    private final Minecraft mc = Minecraft.getMinecraft();
    private final LinkedHashMap<Long,Long> visited = new LinkedHashMap<>();
    private final Map<BlockPos,Long> failed = new HashMap<>();
    private Goal goal;
    private List<Powerup> observedPowerups=Collections.emptyList();
    private long powerupScanAt;
    private long quicktrailUntil, planAt, goalSince, lastProgressAt, pickupWaitAt;
    private double lastX, lastZ;
    private boolean running;
    private static Object liveWorld;
    private static int liveTick=-1;
    private static boolean liveCached;

    public static String letters(String text) {
        return StringUtils.stripControlCodes(text == null ? "" : text).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
    public static boolean live() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer==null || mc.theWorld==null) return false;
        if(liveWorld==mc.theWorld && liveTick==mc.thePlayer.ticksExisted)return liveCached;
        liveWorld=mc.theWorld;liveTick=mc.thePlayer.ticksExisted;liveCached=false;
        for(String line:Utils.getScoreboardLines()) if(letters(line).contains("eventblockhead")) {liveCached=true;return true;}
        // Holograms are authoritative when the active sidebar is temporarily replaced.
        for(Entity e:mc.theWorld.loadedEntityList) if(e instanceof EntityArmorStand && e.hasCustomName()) {
            String name=letters(e.getCustomNameTag());
            if(name.equals("quicktrail") || name.equals("diamondarmor") || name.equals("diamondarmour")) {liveCached=true;return true;}
        }
        return false;
    }
    public void reset() {
        visited.clear(); failed.clear(); goal=null;
        quicktrailUntil=planAt=goalSince=lastProgressAt=pickupWaitAt=0;
        running=false;
        observedPowerups=Collections.emptyList();powerupScanAt=0;
    }
    public void chat(String text,long now) {
        String normalized=letters(text);
        if(normalized.startsWith("poweruppickedupquicktrailpowerup")) {quicktrailUntil=now+20000; goal=null; planAt=0;}
        else if(normalized.startsWith("poweruppickedup")) {goal=null;planAt=0;}
        else if(normalized.equals("death")) {quicktrailUntil=0;goal=null;planAt=0;}
    }
    public void begin(long now) {
        if(!running) {running=true;lastProgressAt=now;lastX=mc.thePlayer.posX;lastZ=mc.thePlayer.posZ;}
        long cell=cell(mc.thePlayer.posX,mc.thePlayer.posZ);
        visited.put(cell,now);
        if(visited.size()>2048) visited.remove(visited.keySet().iterator().next());
        failed.entrySet().removeIf(e->e.getValue()<=now);
    }
    private static long cell(double x,double z) {
        return ((long)((int)Math.floor(x/3))<<32)^(((int)Math.floor(z/3))&0xffffffffL);
    }
    public static boolean diamond(EntityPlayer player) {
        for(ItemStack stack:player.inventory.armorInventory) if(stack!=null && stack.getItem() instanceof ItemArmor
                && ((ItemArmor)stack.getItem()).getArmorMaterial()==ItemArmor.ArmorMaterial.DIAMOND) return true;
        return false;
    }
    public void interrupted() {goal=null;planAt=0;lastProgressAt=0;pickupWaitAt=0;quicktrailUntil=0;powerupScanAt=0;}
    public void failed(long now) {
        if(goal!=null) failed.put(goal.position,now+20000);
        if(failed.size()>128) failed.clear();
        goal=null;planAt=0;
    }
    private boolean usableGround(BlockPos feet) {
        if(!mc.theWorld.isBlockLoaded(feet) || !mc.theWorld.isBlockLoaded(feet.down())) return false;
        String zone=PitMapManager.getZone(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
        if(!zone.equals("Pit") && !zone.equals("Angel") && !zone.equals("Demon")) return false;
        Block floor=mc.theWorld.getBlockState(feet.down()).getBlock();
        return floor.getMaterial().isSolid() && !(floor instanceof net.minecraft.block.BlockSign)
                && !mc.theWorld.getBlockState(feet).getBlock().getMaterial().blocksMovement()
                && !mc.theWorld.getBlockState(feet.up()).getBlock().getMaterial().blocksMovement()
                && !mc.theWorld.getBlockState(feet).getBlock().getMaterial().isLiquid()
                && !mc.theWorld.getBlockState(feet.up()).getBlock().getMaterial().isLiquid();
    }
    public Goal plan(long now) {
        // Refresh from live holograms, including respawn countdown and exact title coordinates.
        if(now>=powerupScanAt) {observedPowerups=BlockheadPowerups.scanPowerups(mc);powerupScanAt=now+250;}
        List<Powerup> powerups=observedPowerups;
        if(goal!=null && goal.kind==GoalKind.POWERUP) {
            Powerup observed=null;
            for(Powerup p:powerups)if(new BlockPos(p.pos).equals(goal.position) && p.type==goal.pickup) {observed=p;break;}
            if(observed==null || (goal.cooldown==0 && observed.cooldown>0)) {
                // A respawn transition means this pickup was consumed; do not walk into its empty spot.
                goal=null;planAt=0;pickupWaitAt=0;
            } else goal=new Goal(observed);
        }
        if(goal!=null) {
            double distance=Math.hypot(mc.thePlayer.posX-goal.pickupPosition.xCoord,mc.thePlayer.posZ-goal.pickupPosition.zCoord);
            if(Math.hypot(mc.thePlayer.posX-lastX,mc.thePlayer.posZ-lastZ)>.35) {
                lastX=mc.thePlayer.posX;lastZ=mc.thePlayer.posZ;lastProgressAt=now;
            }
            if(goal.kind==GoalKind.POWERUP && distance<=2 && goal.cooldown>0) {
                lastProgressAt=now;
                if(now-goalSince<30000)return goal;
            }
            if(goal.kind==GoalKind.POWERUP && distance<.3 && goal.cooldown==0) {
                if(pickupWaitAt==0)pickupWaitAt=now;
                if(now-pickupWaitAt<1800)return goal;
            }
            if(distance<(goal.kind==GoalKind.POWERUP?.3:1.2) || now-goalSince>30000 || (lastProgressAt>0 && now-lastProgressAt>4500)) {
                // Arrival without a server pickup must not repeatedly select the same stand.
                failed.put(goal.position,now+(goal.kind==GoalKind.POWERUP?6000:3000));goal=null;planAt=0;
            } else if(!usableGround(goal.position)) {
                goal=null;planAt=0;
            } else if(goal.kind==GoalKind.POWERUP || now<planAt)return goal;
        }
        if(now<planAt) return goal;
        planAt=now+750;
        Goal best=null; double bestScore=Double.NEGATIVE_INFINITY;
        for(Powerup p:powerups) {
            BlockPos feet=new BlockPos(p.pos);
            if(failed.containsKey(feet) || !usableGround(feet)) continue;
            double distance=mc.thePlayer.getPositionVector().distanceTo(p.pos);
            if(distance>60 || p.cooldown>distance/4.3+10) continue;
            double value;
            if(p.type==PowerupType.QUICKTRAIL) {if(quicktrailUntil-now>6000)continue;value=55;}
            else if(p.type==PowerupType.DIAMOND_ARMOR) {if(diamond(mc.thePlayer))continue;value=30;}
            else {if(mc.thePlayer.getHealth()>=15)continue;value=mc.thePlayer.getHealth()<9?90:40;}
            value-=distance*.8+Math.max(0,p.cooldown-distance/5.5)*12;
            if(value>bestScore) {bestScore=value;best=new Goal(p);}
        }
        // Any useful observed powerup outranks painting, even when it is respawning nearby.
        if(best!=null) {
            goal=best;pickupWaitAt=0;goalSince=lastProgressAt=now;lastX=mc.thePlayer.posX;lastZ=mc.thePlayer.posZ;
            return goal;
        }
        // Paint with direct movement over actual nearby ground. Never submit these tiles to A*.
        int px=(int)Math.floor(mc.thePlayer.posX),pz=(int)Math.floor(mc.thePlayer.posZ),py=(int)Math.floor(mc.thePlayer.posY);
        double angle=Math.toRadians(mc.thePlayer.rotationYaw);
        for(int dx=-6;dx<=6;dx+=2) for(int dz=-6;dz<=6;dz+=2) {
            double distance=Math.hypot(dx,dz);
            if(distance<3 || distance>7)continue;
            BlockPos feet=null;
            for(int y=py+1;y>=Math.max(1,py-1);y--) {
                BlockPos candidate=new BlockPos(px+dx,y,pz+dz);
                if(usableGround(candidate) && directPaintGround(candidate)) {feet=candidate;break;}
            }
            if(feet==null || failed.containsKey(feet))continue;
            Long last=visited.get(cell(feet.getX()+.5,feet.getZ()+.5));
            double revisit=last==null?0:Math.max(0,30-(now-last)/1000.0);
            double heading=(-Math.sin(angle)*dx+Math.cos(angle)*dz)/distance;
            double value=18+heading*4-distance*.2-revisit*2;
            if(value>bestScore) {bestScore=value;best=new Goal(feet,GoalKind.PAINT,null);}
        }
        // Hysteresis keeps a route stable until a materially better objective appears.
        if(goal!=null && best!=null && goal.kind==best.kind && goal.position.equals(best.position))return goal;
        if(goal!=null && goal.kind==GoalKind.PAINT)return goal;
        if(best!=null) {goal=best;pickupWaitAt=0;goalSince=lastProgressAt=now;lastX=mc.thePlayer.posX;lastZ=mc.thePlayer.posZ;}
        return goal;
    }

    private boolean directPaintGround(BlockPos destination) {
        double dx=destination.getX()+.5-mc.thePlayer.posX, dz=destination.getZ()+.5-mc.thePlayer.posZ;
        int steps=(int)Math.ceil(Math.hypot(dx,dz)*2), y=(int)Math.floor(mc.thePlayer.posY);
        for(int step=1;step<=steps;step++) {
            double fraction=(double)step/steps;
            int x=(int)Math.floor(mc.thePlayer.posX+dx*fraction), z=(int)Math.floor(mc.thePlayer.posZ+dz*fraction);
            boolean supported=false;
            for(int nextY=y+1;nextY>=Math.max(1,y-1);nextY--)if(usableGround(new BlockPos(x,nextY,z))) {
                y=nextY;supported=true;break;
            }
            if(!supported)return false;
        }
        return y==destination.getY();
    }
    public java.util.function.Predicate<BlockPos> routeBounds() {
        // Pathfinder predicates also run off-thread: capture immutable coordinates, never world lists.
        final double maximumY=mc.thePlayer.posY+8;
        return feet->{
            if(feet==null || feet.getY()<=0 || feet.getY()>maximumY
                    || Math.abs(feet.getX())>=160 || Math.abs(feet.getZ())>=160)return false;
            return true;
        };
    }
}
