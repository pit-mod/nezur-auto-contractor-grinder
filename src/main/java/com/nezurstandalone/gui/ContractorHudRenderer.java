package com.nezurstandalone.gui;

import com.nezurstandalone.gui.GrinderDashboard.*;

/** Contract-only styles. Measurement and drawing use the same rows, including in the HUD editor. */
public final class ContractorHudRenderer {
    private static final int TEXT=0xFFF0F3FA,MUTED=0xFFBCC6D5;
    private ContractorHudRenderer(){}
    private static Snapshot cached;
    private static java.util.List<Row> compactRows,detailedRows;

    public static int width(Snapshot s,String style){return "Compact".equals(style)?224:GrinderDashboard.WIDTH;}
    private static java.util.List<Row> rows(Snapshot s,String style){
        if(cached!=s){cached=s;compactRows=detailedRows=null;}
        boolean compact="Compact".equals(style);
        if(compact&&compactRows!=null)return compactRows;
        if(!compact&&detailedRows!=null)return detailedRows;
        java.util.List<Row> result=new java.util.ArrayList<>();
        for(Metric metric:s.metrics)result.add(new Row(metric.label,metric.value,metric.color));
        if(!s.target.isEmpty())result.add(new Row(s.target,s.targetDetail,s.accent));
        result.addAll(s.status);
        for(Section section:s.sections){
            if("Compact".equals(style)&&!section.tag.equals("LIVE"))continue;
            if(!"Compact".equals(style))result.add(new Row(section.title,section.tag,section.color));
            int count=0;
            for(Row row:section.rows){result.add(row);if("Compact".equals(style)&&++count==2)break;}
        }
        if(compact)compactRows=result;else detailedRows=result;
        return result;
    }
    public static int height(Snapshot s,String style){
        return "Dashboard".equals(style)?s.height():44+rows(s,style).size()*12;
    }
    public static void render(Snapshot s,String style,float alpha){
        if("Dashboard".equals(style)){GrinderDashboard.render(s,alpha);return;}
        float w=width(s,style),h=height(s,style);
        GuiDraw.roundedRect(0,0,w,h,4,GuiDraw.withAlpha(0xED111722,alpha),GuiDraw.withAlpha(0xFF39475C,alpha));
        GuiDraw.rect(0,0,3,h,GuiDraw.withAlpha(s.accent,alpha));
        text(s.title,9,7,w-18,.85f,TEXT,alpha);
        text(s.state+" / "+s.uptime,9,19,w-18,.65f,s.accent,alpha);
        int y=33;
        for(Row row:rows(s,style)){
            float labelWidth=w*.42f,valueWidth=w-labelWidth-24;
            text(row.label,9,y,labelWidth,.66f,MUTED,alpha);
            String value=fit(row.value,valueWidth,.69f);
            GuiDraw.textScaled(value,w-9-GuiDraw.textWidthScaled(value,.69f),y,.69f,GuiDraw.withAlpha(row.color,alpha),false);
            y+=12;
        }
    }
    private static String fit(String value,float width,float scale){
        String s=value==null?"":value.replaceAll("(?i)\u00a7.","").replace('\n',' ').replace('\r',' ');
        if(GuiDraw.textWidthScaled(s,scale)<=width)return s;
        while(!s.isEmpty()&&GuiDraw.textWidthScaled(s+"...",scale)>width)s=s.substring(0,s.offsetByCodePoints(s.length(),-1));
        return s+"...";
    }
    private static void text(String s,float x,float y,float width,float scale,int color,float alpha){
        GuiDraw.textScaled(fit(s,width,scale),x,y,scale,GuiDraw.withAlpha(color,alpha),false);
    }
}
