package com.nezurstandalone.input;

import com.nezurstandalone.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import java.util.function.BooleanSupplier;

/** Server-specific NPC opening shared by Grinder and Contractor. */
public final class NpcInteractions {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private NpcInteractions() { }

    private static boolean classic() {
        String title=Utils.getScoreboardTitle();
        return title!=null && "THE PIT CLASSIC".equalsIgnoreCase(title.trim());
    }

    public static void request(Object owner,Entity npc,BooleanSupplier allowed) {
        if(classic()) {
            // Match vanilla's entity branch, including its hit-vector interaction first.
            // Do not fall through to sword blocking when the client NPC has no local action.
            GuardedInput.interactEntity(owner,npc,()->classic() && allowed.getAsBoolean(),
                    ()->{if(!MC.playerController.isPlayerRightClickingOnEntity(MC.thePlayer,npc,MC.objectMouseOver))
                        MC.playerController.interactWithEntitySendPacket(MC.thePlayer,npc);});
        } else GuardedInput.attack(owner,npc,()->!classic()&&allowed.getAsBoolean());
    }

    public static void request(Object owner,Entity npc,BooleanSupplier allowed,Runnable invoked) {
        request(owner,npc,allowed);
        GuardedInput.watch(owner,status->{if(status==GuardedInput.Status.NATIVE_INVOKED)invoked.run();});
    }
}
