package com.nezurstandalone.module;



import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        addModule(new com.nezurstandalone.module.impl.player.AutoContractor());
        addModule(new com.nezurstandalone.module.impl.player.AutoGrinder());
        addModule(new com.nezurstandalone.module.impl.player.AutoHeal());
        addModule(new com.nezurstandalone.module.impl.player.AutoReconnect());
        addModule(new com.nezurstandalone.module.impl.player.Pathfinder());
        addModule(new com.nezurstandalone.module.impl.render.ClickGuiSettings());
        addModule(new com.nezurstandalone.module.impl.render.Focus());
        addModule(new com.nezurstandalone.module.impl.render.HUD());
    }

    public void addModule(Module module) {
        modules.add(module);
    }

    public void applyLoadedModuleStates() {
        for (Module module : modules) {
            module.applyLoadedState();
        }
    }

    public List<Module> getModules() {
        return modules.stream().sorted(java.util.Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).sorted(java.util.Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModuleByClass(Class<T> clazz) {
        for (Module m : modules) {
            if (m.getClass().equals(clazz)) {
                return (T) m;
            }
        }
        return null;
    }

    public Module getModuleByName(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }
}

