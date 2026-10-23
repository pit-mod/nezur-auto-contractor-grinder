package com.nezurstandalone.control;
/** Pure movement policy. Facing is supplied by combat aim, never written here. */
public final class CombatMovement {
    public enum State { ALIGN, APPROACH, ENGAGE, RECOVER }
    public State state = State.ALIGN;
    public boolean forward, sprint, back;
    /** Macro chase keeps closing while the camera turns; never walk away or override hazards. */
    public void updatePressure(double heading,double distance,double reach,boolean blocked,boolean hazardous) {
        forward=sprint=back=false;
        if(blocked || hazardous || !Double.isFinite(heading) || !Double.isFinite(distance) || !Double.isFinite(reach) || distance<0 || reach<=0) {
            state=State.RECOVER;return;
        }
        if(Math.abs(heading)>80){state=State.ALIGN;return;}
        state=distance>reach?State.APPROACH:State.ENGAGE;
        if(distance<=.6)return;
        forward=true;
        sprint=Math.abs(heading)<35 && distance>1.25;
    }
    public void update(double heading, double distance, double reach, double closingVelocity,
                       boolean blocked, boolean hazardous) {
        forward = sprint = back = false;
        if (blocked || hazardous) { state = State.RECOVER; return; }
        if (Math.abs(heading) > 45) { state = State.ALIGN; return; }
        double stop = Math.max(1.0, reach - 0.5) + Math.max(0, closingVelocity) * 2;
        if (distance > stop) {
            state = State.APPROACH; forward = true;
            sprint = distance > reach + 1.0 && Math.abs(heading) < 20;
        } else { state = State.ENGAGE; back = distance < 1.0; }
    }
}
