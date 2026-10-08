package com.nezurstandalone.engine;

import com.nezurstandalone.utils.Utils;
import com.nezurstandalone.utils.PitMapManager;
import com.nezurstandalone.utils.ScoreboardHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StringUtils;
import java.util.*;
import java.util.regex.*;

/** Robbery ranking hysteresis and visible bounty observations, with no input side effects. */
public final class RobberyController {
    private static final Pattern RANK=Pattern.compile("(?:#|(?:rank|position|place)\\s*:\\s*#?)\\s*(\\d+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern GOLD=Pattern.compile("(?<![\\d.])(\\d[\\d,]*(?:\\.\\d+)?)([km]?)\\s*g(?:\\b|$)",Pattern.CASE_INSENSITIVE);
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<Integer,Double> bounties=new HashMap<>();
    private long scanAt;
    private boolean holding;
    public void reset(){holding=false;scanAt=0;bounties.clear();}
    public static boolean live(){for(String line:Utils.getScoreboardLines())if(BlockheadController.letters(line).contains("eventrobbery"))return true;return false;}
    public static int rank(String line) {
        String clean=StringUtils.stripControlCodes(line==null?"":line).replaceAll("[^\\x20-\\x7e]", "");
        Matcher m=RANK.matcher(clean);
        if(!m.find())return -1;
        return com.nezurstandalone.utils.SafeNumbers.nonNegativeInt(m.group(1),Integer.MAX_VALUE);
    }
    public static boolean hold(boolean previous,int rank){
        if(rank<=0)return previous;
        return previous?rank<=20:rank<=10;
    }
    public boolean updateHold(){
        int rank=-1;
        for(String line:Utils.getScoreboardLines()){int parsed=rank(line);if(parsed>0){rank=parsed;break;}}
        holding=hold(holding,rank);
        return holding;
    }
    public boolean holding(){return holding;}
    public boolean canTarget(EntityPlayer player) {
        return !holding && player!=null && mc.thePlayer!=null && mc.theWorld!=null
                && player.worldObj==mc.theWorld && mc.thePlayer.getDistanceToEntity(player)<=5;
    }
    public static double gold(String text){
        Matcher m=GOLD.matcher(StringUtils.stripControlCodes(text==null?"":text));double largest=0;
        while(m.find())try {
            double amount=Double.parseDouble(m.group(1).replace(",", ""));
            if(m.group(2).equalsIgnoreCase("k"))amount*=1000;
            if(m.group(2).equalsIgnoreCase("m"))amount*=1000000;
            if(Double.isFinite(amount))largest=Math.max(largest,amount);
        }catch(NumberFormatException ignored){}
        return largest;
    }
    public void scan(long now){
        if(now<scanAt)return;
        scanAt=now+500;bounties.clear();
        for(EntityPlayer p:mc.theWorld.playerEntities){
            double amount=gold(p.getDisplayName().getFormattedText());
            ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(p.getName());
            amount=Math.max(amount,gold(ScoreboardHelper.canonicalName(team,p.getName())));
            bounties.put(p.getEntityId(),amount);
        }
        for(Entity e:mc.theWorld.loadedEntityList)if(e instanceof EntityArmorStand && e.hasCustomName()){
            double amount=gold(e.getCustomNameTag());
            if(amount<=0)continue;
            EntityPlayer nearest=null;double best=1.44;
            for(EntityPlayer p:mc.theWorld.playerEntities){
                double dy=e.posY-p.posY;
                if(p==mc.thePlayer || p.isDead || dy<.5 || dy>4)continue;
                double d=(p.posX-e.posX)*(p.posX-e.posX)+(p.posZ-e.posZ)*(p.posZ-e.posZ);
                if(d<best){nearest=p;best=d;}
            }
            if(nearest!=null)bounties.put(nearest.getEntityId(),Math.max(amount,bounties.get(nearest.getEntityId())));
        }
    }
    public double targetScore(EntityPlayer p){
        // Bounty is the primary key; distance breaks ties. All ordinary validity filters still apply.
        return -bounties.getOrDefault(p.getEntityId(),0.0)*1000+mc.thePlayer.getDistanceToEntity(p);
    }
    public BlockPos retreat(){
        BlockPos best=null;double bestScore=-Double.MAX_VALUE;
        int y=(int)Math.floor(mc.thePlayer.posY);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            int x=(int)Math.floor(mc.thePlayer.posX+Math.cos(a)*10),z=(int)Math.floor(mc.thePlayer.posZ+Math.sin(a)*10);
            for(int h=y+2;h>=Math.max(1,y-3);h--){
                BlockPos feet=new BlockPos(x,h,z);
                if(!mc.theWorld.isBlockLoaded(feet) || !PitMapManager.getZone(x+.5,h,z+.5).equals("Pit"))continue;
                if(!mc.theWorld.getBlockState(feet.down()).getBlock().getMaterial().isSolid()
                        || mc.theWorld.getBlockState(feet).getBlock().getMaterial().blocksMovement()
                        || mc.theWorld.getBlockState(feet.up()).getBlock().getMaterial().blocksMovement()
                        || mc.theWorld.getBlockState(feet).getBlock().getMaterial().isLiquid())continue;
                double score=Math.hypot(x,z)*.1;
                for(EntityPlayer p:mc.theWorld.playerEntities)if(p!=mc.thePlayer&&!p.isDead&&Math.abs(p.posY-h)<4)
                    score-=Math.max(0,9-Math.hypot(p.posX-x,p.posZ-z))*5;
                if(score>bestScore){bestScore=score;best=feet;}break;
            }
        }
        return best;
    }
}
