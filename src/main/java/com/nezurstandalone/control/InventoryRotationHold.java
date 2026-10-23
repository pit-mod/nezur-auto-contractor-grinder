package com.nezurstandalone.control;

/** Last native reported pose, held only for one inventory transaction and its actor/session. */
public final class InventoryRotationHold {
    private static Object owner,actor,world,observedActor,observedWorld;
    private static long session,observedSession;
    private static float yaw,pitch,observedYaw,observedPitch;
    private InventoryRotationHold(){}
    public static void observe(Object player,Object level,long token,float y,float p){
        if(player==null||level==null||!Float.isFinite(y)||!Float.isFinite(p))return;
        observedActor=player;observedWorld=level;observedSession=token;observedYaw=y;observedPitch=p;
    }
    public static void begin(Object source,Object player,Object level,long token,float cameraYaw,float cameraPitch){
        owner=source;actor=player;world=level;session=token;
        boolean observed=player==observedActor&&level==observedWorld&&token==observedSession;
        yaw=observed?observedYaw:cameraYaw;pitch=observed?observedPitch:cameraPitch;
    }
    public static boolean owns(Object player,Object level,long token){return owner!=null&&player==actor&&level==world&&token==session;}
    public static float yaw(Object player,Object level,long token,float fallback){return owns(player,level,token)?yaw:fallback;}
    public static float pitch(Object player,Object level,long token,float fallback){return owns(player,level,token)?pitch:fallback;}
    public static void end(Object source){if(source==owner){owner=actor=world=null;}}
}
