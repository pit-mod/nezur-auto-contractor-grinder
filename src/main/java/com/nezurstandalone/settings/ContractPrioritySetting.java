package com.nezurstandalone.settings;

import com.nezurstandalone.contract.ContractOffer.Type;
import com.nezurstandalone.contract.ContractSelector;
import com.nezurstandalone.gui.ContractPriorityScreen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.Minecraft;

/** The list position is the rank. Saved orders tolerate missing or retired entries. */
public final class ContractPrioritySetting extends ButtonSetting {
    private List<Type> order=ContractSelector.DEFAULT_PRIORITY;

    public ContractPrioritySetting() {
        super("Contract Priority", null);
    }

    @Override public void run() {
        Minecraft mc=Minecraft.getMinecraft();
        mc.displayGuiScreen(new ContractPriorityScreen(mc.currentScreen,this));
    }

    public List<Type> getOrder() { return order; }

    public void loadOrder(String saved) {
        LinkedHashSet<Type> types=new LinkedHashSet<>();
        for(String name:saved.split(",")){
            try {
                Type type=Type.valueOf(name.trim());
                if(ContractSelector.DEFAULT_PRIORITY.contains(type))types.add(type);
            } catch(IllegalArgumentException ignored) { }
        }
        types.addAll(ContractSelector.DEFAULT_PRIORITY);
        setOrder(new ArrayList<>(types));
    }

    public String saveOrder() {
        List<String> names=new ArrayList<>(order.size());
        for(Type type:order)names.add(type.name());
        return String.join(",",names);
    }

    public void move(int from,int to) {
        if(from==to || from<0 || to<0 || from>=order.size() || to>=order.size())return;
        List<Type> next=new ArrayList<>(order);
        next.add(to,next.remove(from));
        setOrder(next);
    }

    public void resetOrder() { setOrder(ContractSelector.DEFAULT_PRIORITY); }

    private void setOrder(List<Type> next) {
        if(next.equals(order))return;
        order=Collections.unmodifiableList(new ArrayList<>(next));
        notifyChange();
    }
}
