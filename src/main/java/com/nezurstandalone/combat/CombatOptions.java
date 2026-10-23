package com.nezurstandalone.combat;

import com.nezurstandalone.settings.*;

/** Saved per-bot combat choices. This is not a module or an event subscriber. */
public final class CombatOptions {
    public final BooleanSetting useKillAura = new BooleanSetting("Use KillAura", true);
    public final ModeSetting preset = new ModeSetting("Combat Preset", "Custom", "Custom", "Conservative", "Balanced", "Aggressive") {
        @Override public boolean isVisible(){return useKillAura.enabled;}
    };
    public final NumberSetting cpsMin = number("KillAura CPS Min",12,1,14,0);
    public final NumberSetting cpsMax = number("KillAura CPS Max",14,1,14,0);
    public final NumberSetting attackRange = number("KillAura Attack Range",3,1,3,1);
    public final NumberSetting targetRange = number("KillAura Target Range",4.5,3,6,1);
    public final NumberSetting rotationSpeed = number("KillAura Rotation Speed",15,5,25,1);
    public final NumberSetting fov = number("KillAura FOV",360,30,360,0);
    public final NumberSetting switchDelay = number("KillAura Switch Delay",150,0,1000,0);
    public final ModeSetting mode = mode("KillAura Target Mode","Single","Single","Switch");
    public final ModeSetting sort = mode("KillAura Target Sort","Health","Distance","Health","Hurt Time","FOV","Smart");
    public final BooleanSetting teams = flag("KillAura Team Check",false);
    public final BooleanSetting botCheck = flag("KillAura Bot Check",true);
    public final NumberSetting legacyMin=legacyNumber("CPS Min",8,1,14,0),legacyMax=legacyNumber("CPS Max",12,1,14,0),
            legacyRange=legacyNumber("Attack Reach",3,1,3,1),legacySpeed=legacyNumber("Combat Aim Speed",15,5,30,1);
    public final BooleanSetting legacyTeams=new BooleanSetting("Team Check (TDM)",true){@Override public boolean isVisible(){return !useKillAura.enabled;}};
    public final NumberSetting ttkSpeed=legacyNumber("TTK Movement Speed",5.6,1,8,1),ttkInterval=legacyNumber("TTK Hit Interval",.5,.5,1.5,2),
            ttkDamage=legacyNumber("TTK Fallback Damage",6,1,20,1),ttkSwitch=legacyNumber("TTK Switch Improvement %",20,5,50,0);
    public final BooleanSetting ttkDebug=new BooleanSetting("TTK Debug",false){@Override public boolean isVisible(){return !useKillAura.enabled;}},
            legacyHealth=new BooleanSetting("Target Fastest Kill",true){@Override public boolean isVisible(){return !useKillAura.enabled;}};
    private boolean applying;

    public CombatOptions(){
        preset.addChangeListener(s->{if(!Setting.isRestoring())applyPreset(preset.getMode());});
        for(Setting setting:settings())if(setting!=preset&&setting!=useKillAura)
            setting.addChangeListener(s->{if(!applying&&!Setting.isRestoring())preset.setMode("Custom");});
    }
    private NumberSetting number(String name,double value,double min,double max,int decimals){
        return new NumberSetting(name,value,min,max,decimals){@Override public boolean isVisible(){return useKillAura.enabled;}};
    }
    private ModeSetting mode(String name,String value,String... values){
        return new ModeSetting(name,value,values){@Override public boolean isVisible(){return useKillAura.enabled;}};
    }
    private BooleanSetting flag(String name,boolean value){
        return new BooleanSetting(name,value){@Override public boolean isVisible(){return useKillAura.enabled;}};
    }
    private NumberSetting legacyNumber(String name,double value,double min,double max,int decimals){
        return new NumberSetting(name,value,min,max,decimals){@Override public boolean isVisible(){return !useKillAura.enabled;}};
    }
    public double minCps(){return useKillAura.enabled?cpsMin.value:legacyMin.value;}
    public double maxCps(){return useKillAura.enabled?cpsMax.value:legacyMax.value;}
    public double range(){return useKillAura.enabled?attackRange.value:legacyRange.value;}
    public double speed(){return useKillAura.enabled?rotationSpeed.value:legacySpeed.value;}
    public Setting[] settings(){return new Setting[]{useKillAura,preset,mode,sort,cpsMin,cpsMax,attackRange,targetRange,rotationSpeed,fov,switchDelay,teams,botCheck,legacyMin,legacyMax,legacyRange,legacySpeed,legacyTeams,ttkSpeed,ttkInterval,ttkDamage,ttkSwitch,ttkDebug,legacyHealth};}
    /** Profiles tune scheduling/selection, never extend native attack reach or bypass validation. */
    public void applyPreset(String name){
        if("Custom".equals(name))return;
        applying=true;
        try{
            boolean conservative="Conservative".equals(name),aggressive="Aggressive".equals(name);
            cpsMin.setValue(conservative?6:aggressive?12:8);
            cpsMax.setValue(conservative?8:aggressive?14:12);
            rotationSpeed.setValue(conservative?8:aggressive?20:12);
            attackRange.setValue(3);targetRange.setValue(conservative?3.5:aggressive?5:4.5);
            fov.setValue(conservative?180:360);mode.setMode("Single");sort.setMode("Health");
            switchDelay.setValue(conservative?250:150);botCheck.setEnabled(true);
        }finally{applying=false;}
    }
}
