package com.nezurstandalone.gui;

import com.nezurstandalone.gui.GrinderDashboard.*;
import java.util.Locale;

/** Uses the Grinder dashboard renderer; only relevant contract context occupies space. */
public final class ContractorDashboard {
    public static final class Input {
        public String phase="Checking scoreboard",badge="CHECKING",server="",uptime="00:00";
        public boolean active,detailed,paused,restoring,perkLease;
        public String type="",task="",choice="",menu="",shop="",purchase="",event="",equipment="None",perk="";
        public int current,target,seconds=-1,completed,retries,maxRetries,rejected,vile=-1;
        public double gold=Double.NaN,npcDistance=-1;
        public long actionSeconds=-1,retrySeconds=-1;
        public int accent=GrinderDashboard.BLUE;
    }
    public static Snapshot build(Input i){
        Snapshot s=new Snapshot();s.title="AutoContractor";s.state=i.badge;s.accent=i.accent;
        s.location=i.server.isEmpty()?i.phase:i.server+" / "+i.phase;s.uptime=i.uptime;
        s.metrics.add(new Metric("COMPLETED",String.valueOf(i.completed),"this session",GrinderDashboard.GREEN));
        if(i.active){
            s.metrics.add(new Metric("PROGRESS",i.target>0?i.current+"/"+i.target:String.valueOf(i.current),i.type,GrinderDashboard.GREEN));
            s.metrics.add(new Metric("TIME LEFT",i.seconds<0?"Pending":time(i.seconds),i.paused?"paused":"remaining",GrinderDashboard.BLUE));
        }
        s.metrics.add(new Metric("GOLD",Double.isNaN(i.gold)?"Pending":number(i.gold),"balance",GrinderDashboard.GOLD));
        if(!i.active)s.metrics.add(new Metric("RETRIES",i.retries+"/"+i.maxRetries,"recovery",GrinderDashboard.VIOLET));
        if(i.active){
            s.target=i.type;s.targetDetail=i.target>0?i.current+" / "+i.target:"In progress";
            s.health=i.target>0?i.current/(float)i.target:0;
            if(!i.task.isEmpty()){
                Section task=new Section("Contract",i.vile>=0?i.vile+" Vile":"ACTIVE",GrinderDashboard.GREEN);
                taskRows(task,i.task);s.sections.add(task);
            }
        }else s.activity=i.phase;
        if(!i.active && !i.choice.isEmpty()){
            Section choice=new Section("Selected choice",i.vile>=0?i.vile+" Vile":"",GrinderDashboard.VIOLET);
            taskRows(choice,i.choice);s.sections.add(choice);
        }
        if(!i.event.isEmpty())s.status.add(new Row("Event",i.event,GrinderDashboard.GOLD));
        if(i.retrySeconds>=0)s.status.add(new Row("Retry in",time(i.retrySeconds),GrinderDashboard.GOLD));
        if(i.npcDistance>=0 || !i.menu.isEmpty()){
            Section npc=new Section("NPC", "",GrinderDashboard.BLUE);
            if(i.npcDistance>=0)npc.row("Distance",String.format(Locale.ROOT,"%.1f m",i.npcDistance));
            if(!i.menu.isEmpty())npc.row("Menu",i.menu);
            if(i.actionSeconds>0)npc.row("Next action",i.actionSeconds+"s");
            s.sections.add(npc);
        }
        if(i.restoring || i.perkLease || !i.shop.isEmpty() || !i.purchase.isEmpty()){
            Section equipment=new Section(i.restoring?"Restoring perks":"Equipment","",GrinderDashboard.GOLD);
            if(!i.perk.isEmpty())equipment.row("Perks",i.perk);
            if(!i.shop.isEmpty())equipment.row("Needed",i.shop);
            if(!i.purchase.isEmpty())equipment.row("Verifying",i.purchase);
            s.sections.add(equipment);
        }
        if(i.detailed){
            Section debug=new Section("Diagnostics","",GrinderDashboard.BLUE).row("State",i.phase)
                    .row("Equipment",i.equipment).row("Retries",i.retries+" / "+i.maxRetries);
            if(i.rejected>0)debug.row("Rejected NPCs",String.valueOf(i.rejected));
            s.sections.add(debug);
        }
        s.footer=i.restoring?"Restoring previous perk loadout":i.active?"Tracking current contract":"Contract automation";
        return s;
    }
    private static void taskRows(Section section,String text){
        String line="";boolean first=true;
        for(String word:text.replaceAll("(?i)\u00a7.","").trim().split("\\s+")){
            if(!line.isEmpty() && line.length()+word.length()+1>30){section.row(first?"Task":"",line);first=false;line="";}
            line+=line.isEmpty()?word:" "+word;
        }
        if(!line.isEmpty())section.row(first?"Task":"",line);
    }
    private static String time(long seconds){return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static String number(double n){return n>=1000000?String.format(Locale.ROOT,"%.1fm",n/1000000)
            :n>=10000?String.format(Locale.ROOT,"%.1fk",n/1000):String.format(Locale.ROOT,"%.0f",n);}
}
