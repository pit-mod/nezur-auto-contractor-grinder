package com.nezurstandalone.combat;

import com.nezurstandalone.control.*;
import com.nezurstandalone.input.NativeActionGate;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.module.impl.player.AutoHeal;
import com.nezurstandalone.pathfinder.PathfinderManager;
import com.nezurstandalone.utils.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import java.util.*;
import java.util.function.*;

/** KillAura's macro targeting/native driver, internal to the bots; no independent toggle/event bus. */
public final class GrinderCombat {
    private static GrinderCombat active;
    private static final String CAMERA="grinder.combat-camera";
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Module owner;
    private final BooleanSupplier allowed;
    private final Predicate<EntityPlayer> filter;
    private final DoubleSupplier cameraSpeed;
    private final Supplier<CombatOptions> settings;
    private final CombatAura reported=new CombatAura("AutoGrinder.KillAura").serverSideRotations().allowedWhen(this::mayTrack);
    private final CombatAura legacy=new CombatAura("AutoGrinder.Legacy").allowedWhen(this::mayTrack)
            .nearbyFirst(5)
            .allowUnlistedPitNpcsWhen(()->"THE PIT CLASSIC".equalsIgnoreCase(Utils.getScoreboardTitle()));
    private final AuraTargeting<EntityPlayer> targeting=new AuraTargeting<>(),navigation=new AuraTargeting<>();
    private final List<AuraTargeting.Candidate<EntityPlayer>> candidates=new ArrayList<>();
    private EntityPlayer target,walkingTarget;
    private CombatOptions options;
    private boolean killAura;
    private long session=-1,walkingUntil;

    public GrinderCombat(Module owner,BooleanSupplier allowed,Predicate<EntityPlayer> filter,DoubleSupplier cameraSpeed,Supplier<CombatOptions> settings){
        this.owner=owner;this.allowed=allowed;this.filter=filter;this.cameraSpeed=cameraSpeed;this.settings=settings;
        SessionResets.register(this,this::resetTargeting);
    }
    private void mode(){
        CombatOptions next=settings.get();boolean enabled=next.useKillAura.enabled;
        if(options!=next||killAura!=enabled||session!=ClientSession.current()){
            stop();options=next;killAura=enabled;session=ClientSession.current();
        }
    }
    private CombatAura driver(){return killAura?reported:legacy;}
    private boolean context(){
        return owner.isToggled()&&allowed.getAsBoolean()&&mc.thePlayer!=null&&mc.theWorld!=null&&mc.playerController!=null
                &&!mc.thePlayer.isDead&&mc.thePlayer.getHealth()>0&&!mc.thePlayer.isSpectator()&&!mc.thePlayer.isRiding()
                &&mc.currentScreen==null&&mc.getRenderViewEntity()==mc.thePlayer
                &&mc.thePlayer.openContainer==mc.thePlayer.inventoryContainer
                &&NativeActionGate.automationAllowed.getAsBoolean()&&NativeActionGate.combatOperationAllowed.getAsBoolean()
                &&NativeActionGate.inventoryTransactionAllowed.getAsBoolean()
                &&(!mc.thePlayer.isUsingItem()||AutoHeal.isHealing())
                &&!NativeActionGate.physical(mc.gameSettings.keyBindUseItem.getKeyCode())
                &&(AutoHeal.isHealing()||InventoryOwner.available(owner))&&PathfinderManager.available(owner);
    }
    private java.util.function.Function<java.util.List<AuraTargeting.Candidate<EntityLivingBase>>,EntityLivingBase> targetSelector;
    private java.util.function.BooleanSupplier targetSelectorAllowed=()->false;
    public GrinderCombat targetSelector(java.util.function.Function<java.util.List<AuraTargeting.Candidate<EntityLivingBase>>,EntityLivingBase> selector){targetSelector=selector;return this;}
    public GrinderCombat targetSelectorWhen(java.util.function.BooleanSupplier allowed){targetSelectorAllowed=allowed;return this;}
    private boolean customSelection(){return targetSelector!=null&&targetSelectorAllowed.getAsBoolean();}
    private EntityPlayer selectCustom() {
        java.util.List<AuraTargeting.Candidate<EntityLivingBase>> choices=new java.util.ArrayList<>();
        for(AuraTargeting.Candidate<EntityPlayer> candidate:candidates)
            choices.add(new AuraTargeting.Candidate<>(candidate.entity,candidate.id,candidate.distance,
                    candidate.health,candidate.hurtTime,candidate.angle,candidate.score));
        EntityLivingBase selected=targetSelector.apply(java.util.Collections.unmodifiableList(choices));
        for(AuraTargeting.Candidate<EntityPlayer> candidate:candidates)
            if(candidate.entity==selected&&selected instanceof EntityPlayer)return (EntityPlayer)selected;
        return null;
    }
    public EntityPlayer pickTarget(){return pick(true);}
    public EntityPlayer pickCombatTarget(){return pick(false);}
    private EntityPlayer pick(boolean allowWalking){
        mode();if(!context()){stop();return null;}
        active=this;refresh();
        if(target==null&&allowWalking){
            collect(30);walkingTarget=customSelection()?selectCustom():navigation.select(candidates,targetMode(),sort(),options.attackRange.value,(long)options.switchDelay.value,Clock.SYSTEM.nanos());
            walkingUntil=Clock.millis()+150;
        }else{walkingTarget=null;navigation.reset();}
        return getTarget();
    }
    private AuraTargeting.Mode targetMode(){return "Switch".equals(options.mode.getMode())?AuraTargeting.Mode.SWITCH:AuraTargeting.Mode.SINGLE;}
    private AuraTargeting.Sort sort(){return AuraTargeting.Sort.values()[options.sort.index];}
    private void refresh(){
        if(!killAura&&customSelection()) {
            collect(30);target=selectCustom();legacy.setTarget(target);return;
        }
        if(!killAura){
            legacy.ttk(options.ttkSpeed.value,options.ttkInterval.value,options.ttkDamage.value,options.ttkSwitch.value,options.ttkDebug.enabled);
            target=legacy.pickTarget(30,options.legacyRange.value,true,options.legacyHealth.enabled,options.legacyTeams.enabled,filter);
            if(target!=null&&!filter.test(target))target=null;legacy.setTarget(target);return;
        }
        collect(options.targetRange.value);
        target=customSelection()?selectCustom():targeting.select(candidates,targetMode(),sort(),options.attackRange.value,(long)options.switchDelay.value,Clock.SYSTEM.nanos());
        reported.setTarget(target);
    }
    private void collect(double range){
        candidates.clear();
        for(Entity entity:mc.theWorld.loadedEntityList)if(entity instanceof EntityPlayer){
            EntityPlayer p=(EntityPlayer)entity;if(!valid(p,range))continue;
            double health=p.getHealth()+p.getAbsorptionAmount();
            double score=sort()==AuraTargeting.Sort.SMART?new TargetEvaluation(mc.thePlayer,p,options.attackRange.value,5.6,.5,6).ttk:health;
            candidates.add(new AuraTargeting.Candidate<>(p,p.getEntityId(),distance(p),health,p.hurtResistantTime,sort()==AuraTargeting.Sort.FOV||customSelection()?angle(p):0,score));
        }
    }
    private boolean valid(EntityPlayer p,double range){
        if(p==null||p==mc.thePlayer||p==mc.getRenderViewEntity()||p==mc.thePlayer.ridingEntity
                ||p.worldObj!=mc.theWorld||mc.theWorld.getEntityByID(p.getEntityId())!=p||p.isDead||p.deathTime>0
                ||p.getHealth()<=0||p.isInvisible()||p.isSpectator()||distance(p)>range
                ||killAura&&options.fov.value<360&&angle(p)>options.fov.value*.5||!filter.test(p)
                ||FriendManager.isFriend(p)||PlayerListStore.containsPlayer(PlayerListStore.ListType.TRUCE,p)
                ||com.nezurstandalone.module.impl.render.Focus.isHidden(p)
                ||!com.nezurstandalone.contract.ContractCombatPolicy.allowsTarget(p))return false;
        if(killAura&&options.botCheck.enabled&&(mc.getNetHandler()==null||mc.getNetHandler().getPlayerInfo(p.getUniqueID())==null))return false;
        if((CombatAura.isTdmEventActive() || (killAura?options.teams.enabled:options.legacyTeams.enabled))&&(mc.thePlayer.isOnSameTeam(p)||CombatAura.shouldSkipTeammate(p,true)))return false;
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);AxisAlignedBB box=p.getEntityBoundingBox();
        double x=(box.minX+box.maxX)*.5,z=(box.minZ+box.maxZ)*.5;
        return box.isVecInside(eyes)||mc.theWorld.rayTraceBlocks(eyes,new Vec3(p.posX,p.posY+p.getEyeHeight()*.85,p.posZ),false,true,false)==null
                ||mc.theWorld.rayTraceBlocks(eyes,new Vec3(x,box.minY+(box.maxY-box.minY)*.5,z),false,true,false)==null
                ||mc.theWorld.rayTraceBlocks(eyes,new Vec3(x,box.minY+(box.maxY-box.minY)*.15,z),false,true,false)==null;
    }
    private double distance(Entity p){
        AxisAlignedBB b=p.getEntityBoundingBox();Vec3 e=mc.thePlayer.getPositionEyes(1);double border=p.getCollisionBorderSize();
        double x=Math.max(b.minX-border,Math.min(b.maxX+border,e.xCoord))-e.xCoord;
        double y=Math.max(b.minY-border,Math.min(b.maxY+border,e.yCoord))-e.yCoord;
        double z=Math.max(b.minZ-border,Math.min(b.maxZ+border,e.zCoord))-e.zCoord;
        return Math.sqrt(x*x+y*y+z*z);
    }
    private double angle(Entity p){return Math.abs(MathHelper.wrapAngleTo180_float((float)(Math.atan2(p.posZ-mc.thePlayer.posZ,p.posX-mc.thePlayer.posX)*180/Math.PI)-90-mc.thePlayer.rotationYaw));}
    private boolean mayTrack(){return options!=null&&session==ClientSession.current()&&context()&&valid(target,killAura?options.targetRange.value:30);}
    public EntityPlayer getTarget(){
        if(options==null||session!=ClientSession.current()||!context())return null;
        if(target!=null&&valid(target,killAura?options.targetRange.value:30))return target;
        return Clock.millis()<walkingUntil&&walkingTarget!=null&&valid(walkingTarget,30)?walkingTarget:null;
    }
    public CombatAura.Stage getStage(){return driver().getStage();}
    public void onRenderFrame(){drive(false);}
    private void drive(boolean nativeInput){
        mode();if(!context()){stop();return;}active=this;
        if(nativeInput)refresh();
        EntityPlayer destination=getTarget();
        if(killAura&&destination!=null&&!PathfinderManager.hasDestination()){
            float[] view=RotationUtils.getRotations(destination,0,0,0);
            RotationManager.getInstance().setMacroTargetRotation(CAMERA,RotationManager.PRIORITY_COMBAT,view[0],view[1],(float)cameraSpeed.getAsDouble());
        }else RotationManager.getInstance().clearTarget(CAMERA);
        if(target==null){driver().stop();return;}
        driver().cps(options.minCps(),options.maxCps()).reach(options.range()).rotationSpeed((float)options.speed());
        driver().setTarget(target);
        if(AutoHeal.isHealing())driver().onAimOnlyFrame();
        else if(nativeInput)driver().onNativeInput();else driver().onRenderFrame();
    }
    public static void prepareNativeInput(){if(active!=null)active.drive(true);}
    public static Entity currentTarget(){return active==null?null:active.getTarget();}
    public static boolean blocksMining(){return active!=null&&active.mayTrack();}
    public void attacked(net.minecraftforge.event.entity.player.AttackEntityEvent e){if(e.entityPlayer==mc.thePlayer&&e.target==target)targeting.attacked(target);}
    public void stop(){
        reported.stop();legacy.stop();reported.resetTargeting();legacy.resetTargeting();target=walkingTarget=null;walkingUntil=0;targeting.reset();navigation.reset();candidates.clear();
        RotationManager.getInstance().clearTarget(CAMERA);if(active==this)active=null;
    }
    public void resetTargeting(){stop();}
}
