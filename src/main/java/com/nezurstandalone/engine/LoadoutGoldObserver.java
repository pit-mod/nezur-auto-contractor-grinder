package com.nezurstandalone.engine;

import java.util.*;

/** Reads the currently connected sidebar, regardless of packet assembly order or module toggle. */
public final class LoadoutGoldObserver {
    private Object world, connection, objective;
    private List<String> lines=Collections.emptyList();
    private double parsed=Double.NaN;
    private boolean captured;

    public void observe(LoadoutPlanner planner,Object world,Object connection,Object objective,
                        boolean connected,boolean inPit,List<String> current,long now) {
        if(!connected||!inPit||world==null||connection==null||objective==null||current==null) {
            clear();planner.invalidateGold();return;
        }
        if(this.world!=world||this.connection!=connection||this.objective!=objective) {
            clear();this.world=world;this.connection=connection;this.objective=objective;
        }
        // Sidebar state is retained by vanilla. No new gold packet is required for an unchanged
        // balance to remain authoritative. Parse only when the visible contents actually change.
        if(!captured||!lines.equals(current)) {
            lines=new ArrayList<>(current);parsed=LoadoutPlanner.scoreboardGold(lines);captured=true;
        }
        planner.gold(parsed,now);
    }
    public void clear(){world=connection=objective=null;lines=Collections.emptyList();parsed=Double.NaN;captured=false;}
}
