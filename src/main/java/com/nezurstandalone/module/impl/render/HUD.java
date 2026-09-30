package com.nezurstandalone.module.impl.render;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.module.ModuleManager;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.ModeSetting;
import com.nezurstandalone.gui.GuiDraw;
import com.nezurstandalone.gui.GuiTheme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HUD extends Module {

    private final ModeSetting colorMode = new ModeSetting("Color Mode", "Theme",
            new String[]{"Theme", "Rainbow", "Chroma", "Astolfo"});
    private final NumberSetting colorSpeed = new NumberSetting("Color Speed", 1.0, 0.5, 1.5, 1);
    private final NumberSetting colorSaturation = new NumberSetting("Saturation", 75.0, 0.0, 100.0, 0);
    private final NumberSetting colorBrightness = new NumberSetting("Brightness", 75.0, 0.0, 100.0, 0);

    private final ModeSetting positionX = new ModeSetting("Position X", "Right",
            new String[]{"Left", "Right"});
    private final ModeSetting positionY = new ModeSetting("Position Y", "Top",
            new String[]{"Top", "Bottom"});
    private final NumberSetting offsetX = new NumberSetting("Offset X", 2.0, 0.0, 255.0, 0);
    private final NumberSetting offsetY = new NumberSetting("Offset Y", 2.0, 0.0, 255.0, 0);

    private final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 1.5, 1);
    private final BooleanSetting showBar = new BooleanSetting("Bar", true);
    private final BooleanSetting shadow = new BooleanSetting("Shadow", true);
    private final BooleanSetting lowercase = new BooleanSetting("Lowercase", false);

    private final BooleanSetting hidePlayer = new BooleanSetting("Hide Player", false);
    private final BooleanSetting hideRender = new BooleanSetting("Hide Render", false);
    private final BooleanSetting hideSwapping = new BooleanSetting("Hide Swapping", false);
    private final BooleanSetting hideMisc = new BooleanSetting("Hide Misc", false);
    private final BooleanSetting hideAuto = new BooleanSetting("Hide Auto", false);

    private final BooleanSetting animations = new BooleanSetting("Animations", true);
    private final java.util.Map<Module, Row> rows = new java.util.LinkedHashMap<>();
    private long lastFrame;
    private float panelWidth, panelHeight;
    private static final float ROW_HEIGHT=15f, HEADER_HEIGHT=22f;
    private static final class Row {
        final Module module;
        float appear, y;
        Row(Module module,float y) { this.module=module;this.y=y; }
    }

    private List<Module> activeModules = new ArrayList<>();

    public HUD() {
        super("HUD", "Displays enabled modules on screen", Category.RENDER);
        addSettings(colorMode, colorSpeed, colorSaturation, colorBrightness,
                positionX, positionY, offsetX, offsetY,
                scale, showBar, shadow, lowercase, animations,
                hidePlayer, hideRender, hideSwapping, hideMisc, hideAuto);

        java.util.function.Consumer<com.nezurstandalone.settings.Setting> bump = s -> Module.bumpToggleRevision();
        hidePlayer.addChangeListener(bump);
        hideRender.addChangeListener(bump);
        hideSwapping.addChangeListener(bump);
        hideMisc.addChangeListener(bump);
        hideAuto.addChangeListener(bump);
        lowercase.addChangeListener(bump);
    }

    public Color getColor(long time, long offset) {
        if ("Theme".equals(colorMode.getMode())) return new Color(GuiTheme.ACCENT,true);
        Color color = Color.WHITE;
        String mode = colorMode.getMode();

        switch (mode) {
            case "Rainbow":
                color = fromHSB(getColorCycle(time, offset), 1.0f, 1.0f);
                break;
            case "Chroma":
                color = fromHSB(getColorCycle(time / 3L, 0L), 1.0f, 1.0f);
                break;
            case "Astolfo":
                float cycle = getColorCycle(time, offset);
                if (cycle % 1.0f < 0.5f) {
                    cycle = 1.0f - cycle % 1.0f;
                }
                color = fromHSB(cycle, 1.0f, 1.0f);
                break;
        }

        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * ((float) colorSaturation.value / 100.0f),
                hsb[2] * ((float) colorBrightness.value / 100.0f)
        );
    }

    public Color getColor(long time) {
        return getColor(time, 0L);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        rows.clear();lastFrame=0;panelWidth=panelHeight=0;builtForRevision=-1;
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (!isToggled() || event.type!=RenderGameOverlayEvent.ElementType.ALL) return;
        if (mc.thePlayer==null || mc.theWorld==null) { rows.clear();lastFrame=0;return; }
        if (mc.gameSettings.showDebugInfo) { lastFrame=0;return; }
        refreshActiveModules();
        long now=System.nanoTime();
        float dt=lastFrame==0?1f/60f:Math.min(0.1f,(now-lastFrame)*1e-9f);lastFrame=now;
        ScaledResolution sr=com.nezurstandalone.utils.ScreenScale.get();
        float sf=Math.max(0.5f,Math.min(1.5f,(float)scale.value));
        float screenW=sr.getScaledWidth()/sf, screenH=sr.getScaledHeight()/sf;
        float marginX=Math.min((float)offsetX.value/sf,Math.max(0,screenW-100));
        float marginY=Math.min((float)offsetY.value/sf,Math.max(0,screenH-60));
        int capacity=Math.max(0,(int)((screenH-marginY-HEADER_HEIGHT-19)/ROW_HEIGHT));
        int shown=Math.min(capacity,activeModules.size());
        java.util.List<Module> visible=activeModules.subList(0,shown);
        for(int i=0;i<shown;i++) {
            Module module=visible.get(i);
            if(!rows.containsKey(module)) rows.put(module,new Row(module,HEADER_HEIGHT+i*ROW_HEIGHT));
        }
        float desiredWidth=112;
        for(Module module:visible) desiredWidth=Math.max(desiredWidth,mc.fontRendererObj.getStringWidth(label(module))+24);
        desiredWidth=Math.min(desiredWidth,screenW-marginX-2);
        java.util.Iterator<Row> iterator=rows.values().iterator();
        while(iterator.hasNext()) {
            Row row=iterator.next();
            row.appear=motion(row.appear,visible.contains(row.module)?1:0,15,dt);
            if(row.appear<0.005f && !visible.contains(row.module)) iterator.remove();
        }
        if(rows.isEmpty() && activeModules.isEmpty()) {panelWidth=panelHeight=0;return;}
        float desiredHeight=HEADER_HEIGHT+shown*ROW_HEIGHT+5+(shown<activeModules.size()?12:0);
        if(panelWidth==0) panelWidth=desiredWidth;
        panelWidth=motion(panelWidth,desiredWidth,14,dt);
        panelHeight=motion(panelHeight,desiredHeight,14,dt);
        boolean right="Right".equals(positionX.getMode()),bottom="Bottom".equals(positionY.getMode());
        float px=right?screenW-marginX-panelWidth:marginX;
        float py=bottom?screenH-marginY-panelHeight:marginY;
        int accent=getColor(com.nezurstandalone.gui.GuiAnim.millis(),0).getRGB();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(sf,sf,1);
            if(shadow.enabled) GuiDraw.shadow(px,py,px+panelWidth,py+panelHeight,5,5,75);
            GuiDraw.roundedRect(px,py,px+panelWidth,py+panelHeight,5,0xED101319,0x503D4654);
            GuiDraw.beginClip(px*sf,py*sf,panelWidth*sf,panelHeight*sf);
            try {
                GuiDraw.circle(px+9,py+10,2,accent);
                GuiDraw.textScaled("ACTIVE",px+16,py+6.5f,0.75f,0xFFB3BDCC,false);
                String count=String.valueOf(activeModules.size());
                float countW=GuiDraw.textWidthScaled(count,0.75f);
                GuiDraw.pill(px+panelWidth-countW-16,py+4,countW+10,12,GuiDraw.withAlpha(accent,0.14f));
                GuiDraw.textScaled(count,px+panelWidth-countW-11,py+6.5f,0.75f,accent,false);
                GuiDraw.rect(px+7,py+HEADER_HEIGHT-3,px+panelWidth-7,py+HEADER_HEIGHT-2,0x16FFFFFF);
                for(Row row:rows.values()) {
                    int index=visible.indexOf(row.module);
                    if(index>=0) row.y=motion(row.y,HEADER_HEIGHT+index*ROW_HEIGHT,18,dt);
                    float alpha=row.appear;
                    float rx=px+(right?1:-1)*(1-alpha)*8;
                    float ry=py+row.y;
                    int color=getColor(com.nezurstandalone.gui.GuiAnim.millis(),Math.max(0,index)).getRGB();
                    if(showBar.enabled) GuiDraw.roundedRect(rx+panelWidth-5,ry+3,rx+panelWidth-3,ry+11,1,GuiDraw.withAlpha(color,alpha*0.9f));
                    GuiDraw.textFitScaled(label(row.module),rx+9,ry+3,1,panelWidth-22,
                            GuiDraw.withAlpha(0xFFE3E8EF,alpha),shadow.enabled);
                }
                if(shown<activeModules.size()) GuiDraw.textScaled("+ "+(activeModules.size()-shown)+" more",px+9,
                        py+HEADER_HEIGHT+shown*ROW_HEIGHT+2,0.75f,0xFF8A96A8,false);
            } finally { GuiDraw.endClip(); }
        } finally {
            GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);
        }
    }

    private String label(Module module) {
        return lowercase.enabled?module.getName().toLowerCase(Locale.ROOT):module.getName();
    }

    private float motion(float current,float target,float speed,float dt) {
        return animations.enabled?GuiDraw.approach(current,target,speed,dt):target;
    }
    private int builtForRevision = -1;

    private void refreshActiveModules() {
        ModuleManager mgr = com.nezurstandalone.Nezur.moduleManager;
        if (mgr == null) return;

        int revision = Module.getToggleRevision();
        if (revision == builtForRevision) return;
        builtForRevision = revision;

        activeModules = new ArrayList<>();
        for (Module m : mgr.getModules()) {
            if (m.isToggled() && m != this && !m.isHiddenInHud()) {
                if (m.getCategory() == Category.PLAYER && hidePlayer.enabled) continue;
                if (m.getCategory() == Category.RENDER && hideRender.enabled) continue;
                if (m.getCategory() == Category.SWAPPING && hideSwapping.enabled) continue;
                if (m.getCategory() == Category.MISC && hideMisc.enabled) continue;
                if (m.getCategory() == Category.AUTO && hideAuto.enabled) continue;
                
                activeModules.add(m);
            }
        }
        activeModules.sort(Comparator.comparingInt(
                (Module m) -> mc.fontRendererObj.getStringWidth(lowercase.enabled ? m.getName().toLowerCase(Locale.ROOT) : m.getName())
        ).reversed());
    }

    private float getColorCycle(long time, long offset) {
        long speed = (long) (3000.0 / Math.pow(
                Math.min(Math.max(0.5f, (float) colorSpeed.value), 1.5f), 3.0));
        return 1.0f - (float) (Math.abs(time - offset * 300L) % speed) / (float) speed;
    }

    private static Color fromHSB(float hue, float saturation, float brightness) {
        return Color.getHSBColor(hue, saturation, brightness);
    }
}





