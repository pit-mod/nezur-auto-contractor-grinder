package com.nezurstandalone.gui;

import java.util.ArrayList;
import java.util.List;

/** Compact dashboard. The same measured layout drives the HUD editor and rendering. */
public final class GrinderDashboard {
    public static final int WIDTH = 264;
    public static final int GREEN=0xFF69D7AD, GOLD=0xFFF3C76B, VIOLET=0xFFBAA3FF, BLUE=0xFF77B9FF;
    private static final int TEXT=0xFFF0F3FA, MUTED=0xFF929DAF, DIM=0xFF68758A;
    public static final class Metric {
        public final String label,value,detail; public final int color;
        public Metric(String label,String value,String detail,int color){this.label=label;this.value=value;this.detail=detail;this.color=color;}
    }
    public static final class Row {
        public final String label,value; public final int color; public final float progress;
        public Row(String label,String value,int color){this(label,value,color,-1);}
        public Row(String label,String value,int color,float progress){this.label=label;this.value=value;this.color=color;this.progress=progress;}
    }
    public static final class Section {
        public final String title,tag; public final int color; public final List<Row> rows=new ArrayList<>();
        public Section(String title,String tag,int color){this.title=title;this.tag=tag;this.color=color;}
        public Section row(String label,String value){return row(label,value,color);}
        public Section row(String label,String value,int color){rows.add(new Row(label,value,color));return this;}
        public Section progress(String label,String value,float progress){rows.add(new Row(label,value,color,progress));return this;}
        public int height(){int h=18;for(Row row:rows)h+=row.progress>=0?17:12;return h+4;}
    }
    public static final class Snapshot {
        public String title="AutoGrinder",state="Idle",location="Waiting for world",uptime="00:00";
        public String target="",targetDetail="",activity="Waiting for a target",footer="Session rewards";
        public float health=-1; public int accent=BLUE;
        public final List<Metric> metrics=new ArrayList<>();
        public final List<Row> status=new ArrayList<>();
        public final List<Section> sections=new ArrayList<>();
        public int height(){int h=35+40+(target.isEmpty()?16:23)+status.size()*12+17;for(Section s:sections)h+=s.height()+4;return h+5;}
    }
    private static String fit(String value,float width,float scale){
        String s=value==null?"":value;
        if(s.indexOf('\u00a7')>=0)s=s.replaceAll("\u00a7.","");
        if(s.indexOf('\n')>=0||s.indexOf('\r')>=0)s=s.replace('\n',' ').replace('\r',' ');
        if(GuiDraw.textWidthScaled(s,scale)<=width)return s;
        while(!s.isEmpty()&&GuiDraw.textWidthScaled(s+"...",scale)>width)s=s.substring(0,s.offsetByCodePoints(s.length(),-1));
        return s+"...";
    }
    private static void text(String s,float x,float y,float scale,float width,int color,float alpha){
        GuiDraw.textScaled(fit(s,width,scale),x,y,scale,GuiDraw.withAlpha(color,alpha),false);
    }
    private static void right(String s,float right,float y,float scale,float width,int color,float alpha){
        String value=fit(s,width,scale);text(value,right-GuiDraw.textWidthScaled(value,scale),y,scale,width,color,alpha);
    }
    private static void bar(float x,float y,float w,float fraction,int color,float alpha){
        GuiDraw.roundedRect(x,y,x+w,y+2,1,GuiDraw.withAlpha(0x26FFFFFF,alpha));
        float fill=Math.max(0,Math.min(1,fraction))*w;
        if(fill>0)GuiDraw.roundedRect(x,y,x+fill,y+2,1,GuiDraw.withAlpha(color,alpha));
    }
    public static void render(Snapshot s,float alpha){
        float h=s.height(),w=WIDTH;
        GuiDraw.shadow(0,0,w,h,7,4,(int)(82*alpha));
        GuiDraw.roundedRect(0,0,w,h,7,GuiDraw.withAlpha(0xEB111722,alpha),GuiDraw.withAlpha(0xFF303C50,alpha));
        GuiDraw.roundedRect(1,1,w-1,31,6,GuiDraw.withAlpha(0xB01A2434,alpha));
        GuiDraw.rect(8,31,w-8,31.5f,GuiDraw.withAlpha(0x20FFFFFF,alpha));
        GuiDraw.circle(10,12,2.2f,GuiDraw.withAlpha(s.accent,alpha));
        String badgeText=s.state.toUpperCase(java.util.Locale.ROOT);
        float badge=Math.min(112,GuiDraw.textWidthScaled(badgeText,.65f)+12);
        float bx=w-8-badge;
        text(s.title,17,7,.91f,bx-23,TEXT,alpha);
        GuiDraw.roundedRect(bx,6,w-8,18,3,GuiDraw.withAlpha(GuiDraw.lerpColor(0xFF192131,s.accent,.16f),alpha));
        right(badgeText,w-14,9,.65f,badge-12,s.accent,alpha);
        text(s.location,9,22,.62f,w-64,MUTED,alpha);
        right(s.uptime,w-9,22,.62f,48,MUTED,alpha);

        int metricCount=Math.max(1,Math.min(4,s.metrics.size()));
        float y=36,cell=(w-9-3*metricCount)/metricCount;
        for(int i=0;i<Math.min(4,s.metrics.size());i++){
            Metric m=s.metrics.get(i);float x=6+i*(cell+3);
            GuiDraw.roundedRect(x,y,x+cell,y+34,3,GuiDraw.withAlpha(0xFF1B2534,alpha));
            text(m.label,x+5,y+4,.60f,cell-10,MUTED,alpha);
            text(m.value,x+5,y+12,1.08f,cell-10,m.color,alpha);
            text(m.detail,x+5,y+25,.60f,cell-10,MUTED,alpha);
        }
        y=76;
        if(!s.target.isEmpty()){
            text(s.target,9,y,.79f,147,TEXT,alpha);
            right(s.targetDetail,w-9,y+1,.62f,90,s.accent,alpha);
            bar(9,y+12,w-18,s.health,s.accent,alpha);y+=23;
        }else{
            GuiDraw.circle(10,y+3,1.5f,GuiDraw.withAlpha(s.accent,alpha));
            text(s.activity,16,y,.69f,w-25,MUTED,alpha);y+=16;
        }
        for(Row row:s.status){
            text(row.label,9,y,.68f,91,MUTED,alpha);
            right(row.value,w-9,y,.69f,w-112,row.color,alpha);y+=12;
        }
        for(Section section:s.sections){
            float bottom=y+section.height();
            GuiDraw.roundedRect(6,y,w-6,bottom,4,GuiDraw.withAlpha(0xFF17202D,alpha));
            GuiDraw.roundedRect(6,y+5,8,bottom-5,1,GuiDraw.withAlpha(section.color,alpha));
            text(section.title.toUpperCase(java.util.Locale.ROOT),13,y+6,.64f,149,section.color,alpha);
            right(section.tag,w-12,y+6,.58f,88,MUTED,alpha);
            float ry=y+18;
            for(Row row:section.rows){
                text(row.label,13,ry,.68f,83,MUTED,alpha);
                right(row.value,w-13,ry,.69f,w-113,row.color,alpha);
                if(row.progress>=0){bar(13,ry+10,w-26,row.progress,section.color,alpha);ry+=17;}else ry+=12;
            }
            y=bottom+4;
        }
        GuiDraw.rect(9,y+2,w-9,y+2.5f,GuiDraw.withAlpha(0x18FFFFFF,alpha));
        text(s.footer,9,y+8,.63f,w-18,DIM,alpha);
    }
}
