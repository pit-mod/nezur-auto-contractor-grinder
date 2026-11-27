package com.nezurstandalone.gui;

import com.nezurstandalone.contract.ContractOffer.Type;
import com.nezurstandalone.settings.ContractPrioritySetting;
import com.nezurstandalone.utils.ConfigManager;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public final class ContractPriorityScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ContractPrioritySetting setting;
    private int left,top,panelWidth,rowHeight,footerY,dragged=-1;

    public ContractPriorityScreen(GuiScreen parent,ContractPrioritySetting setting) {
        this.parent=parent;
        this.setting=setting;
    }

    @Override public void initGui() {
        panelWidth=Math.min(330,width-16);
        rowHeight=Math.max(12,Math.min(24,(height-77)/setting.getOrder().size()));
        left=(width-panelWidth)/2;
        top=(height-(rowHeight*setting.getOrder().size()+69))/2;
        footerY=top+43+rowHeight*setting.getOrder().size();
        dragged=-1;
    }

    @Override public void drawScreen(int mx,int my,float partialTicks) {
        drawDefaultBackground();
        GuiDraw.roundedRect(left,top,left+panelWidth,footerY+25,8,0xFF141C29,0xFF34465F);
        GuiDraw.text("Contract priority",left+12,top+10,0xFFF3F7FF);
        GuiDraw.text("Highest first. Drag a row or use the arrows.",left+12,top+25,0xFFACB9CD);
        List<Type> order=setting.getOrder();
        int drop=rowAt(mx,my);
        for(int i=0;i<order.size();i++){
            int y=top+40+i*rowHeight;
            boolean hovered=drop==i;
            int fill=i==dragged?0xFF2B3F57:hovered?0xFF23354D:0xFF1B283B;
            GuiDraw.roundedRect(left+8,y,left+panelWidth-8,y+rowHeight-2,4,fill);
            if(dragged>=0 && drop==i && drop!=dragged)
                drawRect(left+8,y+(drop>dragged?rowHeight-2:0),left+panelWidth-8,y+(drop>dragged?rowHeight-1:1),0xFF73CEFF);
            int textY=y+(rowHeight-9)/2;
            GuiDraw.text(String.valueOf(i+1),left+15,textY,0xFF73CEFF);
            GuiDraw.text(label(order.get(i)),left+37,textY,0xFFF3F7FF);
            int right=left+panelWidth;
            GuiDraw.text("▲",right-43,textY,i>0?0xFFE2EEFF:0xFF647085);
            GuiDraw.text("▼",right-24,textY,i+1<order.size()?0xFFE2EEFF:0xFF647085);
        }
        footerButton("Reset order",left+8,footerY,75,mx,my);
        footerButton("Done",left+panelWidth-62,footerY,54,mx,my);
    }

    private void footerButton(String label,int x,int y,int w,int mx,int my) {
        GuiDraw.roundedRect(x,y,x+w,y+18,4,within(mx,my,x,y,w,18)?0xFF344C69:0xFF263950);
        GuiDraw.text(label,x+7,y+5,0xFFEAF2FF);
    }

    private int rowAt(int mx,int my) {
        int y=top+40;
        if(!within(mx,my,left+8,y,panelWidth-16,rowHeight*setting.getOrder().size()))return -1;
        return (my-y)/rowHeight;
    }

    private static boolean within(int mx,int my,int x,int y,int w,int h) {
        return mx>=x && mx<x+w && my>=y && my<y+h;
    }

    @Override protected void mouseClicked(int mx,int my,int button) throws IOException {
        if(button!=0)return;
        int row=rowAt(mx,my);
        if(row>=0){
            if(mx>=left+panelWidth-29)setting.move(row,row+1);
            else if(mx>=left+panelWidth-48)setting.move(row,row-1);
            else dragged=row;
        }else if(within(mx,my,left+8,footerY,75,18))setting.resetOrder();
        else if(within(mx,my,left+panelWidth-62,footerY,54,18))mc.displayGuiScreen(parent);
    }

    @Override protected void mouseReleased(int mx,int my,int button) {
        if(button==0 && dragged>=0){
            setting.move(dragged,rowAt(mx,my));
            dragged=-1;
        }
    }

    @Override protected void keyTyped(char ch,int key) throws IOException {
        if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);
        else super.keyTyped(ch,key);
    }

    @Override public void onGuiClosed(){ConfigManager.saveConfig();}
    @Override public boolean doesGuiPauseGame(){return false;}

    private static String label(Type type) {
        switch(type){
            case GOLDEN_APPLES:return "Golden Apples";
            case KILL_PLAYERS:return "Kill Players";
            case FIST_MID_KILLS:return "Fist Kills in Mid";
            case SNEAK_ATTACK_KILLS:return "Sneak Kills";
            case NO_ARMOR_KILLS:return "No Armor Kills";
            case DIAMOND_SWORD_FINAL_BLOW:return "Diamond Sword Kills";
            case CLAIM_BOUNTIES:return "Bounty Claims";
            case COLLECT_GOLD_INGOTS:return "Gold Ingots";
            case NO_PERK_KILLS:return "No Perk Kills";
            case KILL_STREAK:return "Kill Streak";
            case MULTIKILL:return "Multikill (Triple / Quadra)";
            default:return type.name();
        }
    }
}
