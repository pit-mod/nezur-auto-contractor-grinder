package com.nezurstandalone.engine;

import com.nezurstandalone.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import java.util.*;

/** Raffle observations and stable goals; the grinder owns movement, slots and native clicks. */
public final class RaffleController {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<Integer,Long> skipped=new HashMap<>();
    private final List<BlockPos> boxes=new ArrayList<>();
    private EntityItem ticket;
    private long boxScanAt;
    private final RaffleTicketProgress progress=new RaffleTicketProgress();
    private boolean depositing;

    public static boolean eventLine(String line) {
        return BlockheadController.letters(line).contains("eventraffle");
    }
    public static boolean live() {
        for(String line:Utils.getScoreboardLines())if(eventLine(line))return true;
        return false;
    }
    public static boolean isTicket(ItemStack stack) {
        return stack!=null && stack.stackSize>0 && stack.getItem()==Items.name_tag;
    }
    public static int count(ItemStack[] inventory) {
        int count=0;
        for(ItemStack stack:inventory)if(isTicket(stack))count+=stack.stackSize;
        return count;
    }
    public int tickets() {return count(mc.thePlayer.inventory.mainInventory);}
    public static boolean deposit(boolean previous,int count) {return count>0 && (previous || count>=9);}
    public boolean updateDeposit() {
        depositing=deposit(depositing,tickets());
        if(depositing)ticket=null;
        return depositing;
    }
    public int ticketSlot(boolean hotbar) {
        for(int i=hotbar?0:9;i<(hotbar?9:36);i++)
            if(isTicket(mc.thePlayer.inventory.getStackInSlot(i)))return i;
        return -1;
    }
    public void reset() {
        ticket=null;skipped.clear();boxes.clear();boxScanAt=0;
        progress.reset();depositing=false;
    }
    private boolean valid(EntityItem item) {
        return item!=null && !item.isDead && item.worldObj==mc.theWorld
                && isTicket(item.getEntityItem()) && mc.theWorld.getEntityByID(item.getEntityId())==item
                && Math.abs(item.posY-mc.thePlayer.posY)<12 && mc.thePlayer.getDistanceToEntity(item)<80
                && !skipped.containsKey(item.getEntityId());
    }
    public EntityItem findTicket(long now) {
        skipped.entrySet().removeIf(e->e.getValue()<=now);
        if(valid(ticket)) {
            if(!progress.expired(now,mc.thePlayer.getDistanceToEntity(ticket)))return ticket;
            failedTicket(now);
        }
        ticket=null;
        double nearest=Double.MAX_VALUE;
        for(Entity entity:mc.theWorld.loadedEntityList)if(entity instanceof EntityItem && valid((EntityItem)entity)) {
            double distance=mc.thePlayer.getDistanceSqToEntity(entity);
            if(distance<nearest && ticketStand((EntityItem)entity)!=null) {nearest=distance;ticket=(EntityItem)entity;}
        }
        progress.begin(now,ticket==null?Double.MAX_VALUE:Math.sqrt(nearest));
        return ticket;
    }
    public void failedTicket(long now) {
        if(ticket!=null)skipped.put(ticket.getEntityId(),now+8000);
        ticket=null;progress.reset();
    }
    public boolean walkable(BlockPos at) {
        return mc.theWorld.isBlockLoaded(at) && mc.theWorld.isBlockLoaded(at.up()) && mc.theWorld.isBlockLoaded(at.down())
                && !mc.theWorld.getBlockState(at).getBlock().getMaterial().blocksMovement()
                && !mc.theWorld.getBlockState(at.up()).getBlock().getMaterial().blocksMovement()
                && !mc.theWorld.getBlockState(at).getBlock().getMaterial().isLiquid()
                && !mc.theWorld.getBlockState(at.up()).getBlock().getMaterial().isLiquid()
                && mc.theWorld.getBlockState(at.down()).getBlock().getMaterial().isSolid();
    }
    public BlockPos ticketStand(EntityItem item) {
        BlockPos origin=new BlockPos(item.posX,item.posY,item.posZ);
        for(int y=1;y>=-2;y--) {
            BlockPos at=origin.add(0,y,0);
            if(RaffleTicketProgress.withinPickup(at.getX()+.5,at.getY(),at.getZ()+.5,item.posX,item.posY,item.posZ)
                    && walkable(at))return at;
        }
        return null;
    }
    public boolean isBox(BlockPos pos) {
        return pos!=null && boxes.contains(pos) && mc.theWorld.isBlockLoaded(pos)
                && (mc.theWorld.getBlockState(pos).getBlock()==Blocks.jukebox
                    || mc.theWorld.getBlockState(pos).getBlock()==Blocks.noteblock);
    }
    public List<BlockPos> boxes(long now) {
        if(now>=boxScanAt) {
            boxes.clear();boxScanAt=now+1000;
            EntityArmorStand label=null;
            for(Entity entity:mc.theWorld.loadedEntityList)if(entity instanceof EntityArmorStand && entity.hasCustomName()
                    && BlockheadController.letters(entity.getCustomNameTag()).equals("rafflebox")) {
                if(label==null || mc.thePlayer.getDistanceSqToEntity(entity)<mc.thePlayer.getDistanceSqToEntity(label))
                    label=(EntityArmorStand)entity;
            }
            // The photographed box is a cluster: note blocks are valid only under its label
            // or at centre during the active Raffle. Never route into a solid box block.
            int cx=label==null?0:(int)Math.floor(label.posX), cz=label==null?0:(int)Math.floor(label.posZ);
            int top=label==null?(int)Math.floor(mc.thePlayer.posY)+5:(int)Math.floor(label.posY);
            int bottom=label==null?(int)Math.floor(mc.thePlayer.posY)-12:top-10;
            int radius=label==null?8:3;
            for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++)for(int y=bottom;y<=top;y++) {
                BlockPos pos=new BlockPos(x,y,z);
                if(!mc.theWorld.isBlockLoaded(pos))continue;
                net.minecraft.block.Block block=mc.theWorld.getBlockState(pos).getBlock();
                if(block==Blocks.jukebox || (block==Blocks.noteblock && (label!=null || Math.hypot(x+.5,z+.5)<=6)))boxes.add(pos);
            }
        }
        boxes.sort(Comparator.comparingDouble(p->mc.thePlayer.getDistanceSq(p.getX()+.5,p.getY()+.5,p.getZ()+.5)));
        return boxes;
    }
    public BlockPos boxStand(BlockPos box) {
        BlockPos nearest=null;double best=Double.MAX_VALUE;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=1;y++) {
            if(x==0 && z==0)continue;
            BlockPos pos=box.add(x,y,z);
            if(!walkable(pos))continue;
            double distance=mc.thePlayer.getDistanceSq(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            if(distance<best) {best=distance;nearest=pos;}
        }
        return nearest;
    }
}
