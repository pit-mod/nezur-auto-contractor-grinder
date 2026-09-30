package com.nezurstandalone.module.impl.render;

import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.utils.FriendManager;
import com.nezurstandalone.utils.PitMapManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Focus extends Module {

    private static Focus instance;
    private static final Object CLEANUP_COMMANDS=new Object();

    private final BooleanSetting showFriends = new BooleanSetting("Show Friends", true);
    private final BooleanSetting showAttackers = new BooleanSetting("Show Attackers", false);
    private final NumberSetting revealDuration = new NumberSetting("Reveal Duration (s)", 10, 3, 60, 0);
    private final BooleanSetting showNearby = new BooleanSetting("Show Nearby", false);
    private final NumberSetting nearbyRange = new NumberSetting("Nearby Range", 30, 10, 100, 0);
    private final BooleanSetting nonsOnly = new BooleanSetting("Hide Diamond (Pit)", false);
    private final BooleanSetting onlyInsidePit = new BooleanSetting("Only Inside Pit", false);
    private final BooleanSetting myauSync = new BooleanSetting("Myau Support", false);

    private final Map<UUID, Long> revealedPlayers = new ConcurrentHashMap<>();
    private final Set<String> myauFriendCache = ConcurrentHashMap.newKeySet();
    private final LinkedList<String> commandQueue = new LinkedList<>();
    private long lastCommandTime;
    private int cleanupTickCounter = 0;
    private boolean pausedForSpire = false;
    private boolean restoreToggledAfterSpire = false;

    public Focus() {
        super("Focus", "Filters players by friends, attackers, proximity and Pit rules", Category.RENDER);
        instance = this;
        com.nezurstandalone.control.SessionResets.register(Focus.class,()->{commandQueue.clear();revealedPlayers.clear();myauFriendCache.clear();com.nezurstandalone.control.CommandCoordinator.cancel(Focus.class);com.nezurstandalone.control.CommandCoordinator.cancel(CLEANUP_COMMANDS);});
        addSettings(showFriends, showAttackers, revealDuration, showNearby, nearbyRange, nonsOnly, onlyInsidePit, myauSync);
    }

    public void pauseForSpire() {
        if (pausedForSpire) return;
        pausedForSpire = true;
        restoreToggledAfterSpire = isToggled();
        if (isToggled()) {
            setToggled(false);
        }
    }

    public void resumeAfterSpire() {
        if (!pausedForSpire) return;
        pausedForSpire = false;
        if (restoreToggledAfterSpire) {
            setToggled(true);
        }
        restoreToggledAfterSpire = false;
    }

    public boolean isPausedForSpire() {
        return pausedForSpire;
    }

    @Override
    public void setToggled(boolean toggled) {
        if (pausedForSpire && toggled) return;
        super.setToggled(toggled);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        // Stop the producer and discard additions before issuing terminal removals.
        commandQueue.clear();
        com.nezurstandalone.control.CommandCoordinator.cancel(Focus.class);
        revealedPlayers.clear();
        java.util.List<String> removals=new java.util.ArrayList<>();
        for(String name:myauFriendCache)removals.add(".friend remove "+name);
        myauFriendCache.clear();
        if(mc.thePlayer!=null && !removals.isEmpty())com.nezurstandalone.control.CommandCoordinator.enqueue(CLEANUP_COMMANDS,removals.toArray(new String[0]));
    }

    /**
     * @return true if the player should be hidden (not rendered / not counted).
     */
    public static boolean isHidden(EntityPlayer player) {
        if (instance == null || !instance.isToggled()) {
            return false;
        }
        if (player == Minecraft.getMinecraft().thePlayer) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(player.getUniqueID()) == null) {
            return false;
        }

        if (instance.onlyInsidePit.enabled) {
            String zone = PitMapManager.getZone(player.posX, player.posY, player.posZ);
            if (!"Pit".equals(zone)) {
                return true;
            }
        }

        boolean allowed = false;
        boolean allowedStrong = false;

        if (instance.showFriends.enabled && FriendManager.isFriend(player)) {
            allowed = true;
            allowedStrong = true;
        }
        if (instance.showAttackers.enabled && instance.isRevealed(player.getUniqueID())) {
            allowed = true;
            allowedStrong = true;
        }
        if (instance.showNearby.enabled
                && mc.thePlayer.getDistanceToEntity(player) <= instance.nearbyRange.value) {
            allowed = true;
        }

        if (instance.nonsOnly.enabled) {
            boolean isNon = false;
            if (PitMapManager.getZone(player.posX, player.posY, player.posZ).equals("Pit")) {
                int diamondCount = 0;
                for (int i = 1; i <= 4; i++) {
                    ItemStack armor = player.getEquipmentInSlot(i);
                    if (armor != null && armor.getItem() instanceof ItemArmor) {
                        if (((ItemArmor) armor.getItem()).getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND) {
                            diamondCount++;
                        }
                    }
                }
                if (diamondCount >= 2) {
                    isNon = true;
                }
            }
            return isNon && !allowedStrong;
        }

        return !allowed;
    }

    private static boolean isHiddenWithSync(EntityPlayer player) {
        boolean hidden = isHidden(player);

        if (instance != null && instance.isToggled() && instance.myauSync.enabled) {
            String name = player.getName();
            boolean inPit = "Pit".equals(PitMapManager.getZone(player.posX, player.posY, player.posZ));

            if (!inPit) {
                if (instance.myauFriendCache.contains(name)) {
                    instance.myauFriendCache.remove(name);
                    instance.queueCommand(".friend remove " + name);
                }
            } else if (hidden && !instance.myauFriendCache.contains(name)) {
                instance.myauFriendCache.add(name);
                instance.queueCommand(".friend add " + name);
            } else if (!hidden && instance.myauFriendCache.contains(name)) {
                instance.myauFriendCache.remove(name);
                instance.queueCommand(".friend remove " + name);
            }
        }

        return hidden;
    }

    private void queueCommand(String command) {
        if (!commandQueue.contains(command)) {
            commandQueue.add(command);
        }
    }

    private void sendNextCommand() {
        if (commandQueue.isEmpty() || mc.thePlayer == null) {
            return;
        }
        if (System.currentTimeMillis() - lastCommandTime < 500) {
            return;
        }
        String command = commandQueue.poll();
        if (command != null) {
            sendCommandDirect(command);
            lastCommandTime = System.currentTimeMillis();
        }
    }

    private static void sendCommandDirect(String command) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || command == null) {
            return;
        }
        com.nezurstandalone.control.CommandCoordinator.enqueue(Focus.class, command);
    }

    private boolean isRevealed(UUID uuid) {
        Long hitTime = revealedPlayers.get(uuid);
        if (hitTime == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - hitTime;
        if (elapsed > (long) (revealDuration.value * 1000L)) {
            revealedPlayers.remove(uuid);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (!showAttackers.enabled || mc.thePlayer == null) {
            return;
        }
        if (event.entity != mc.thePlayer) {
            return;
        }
        if (event.source != null && event.source.getEntity() instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) event.source.getEntity();
            if (attacker != mc.thePlayer) {
                revealedPlayers.put(attacker.getUniqueID(), System.currentTimeMillis());
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!isToggled() || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        sendNextCommand();

        if (showAttackers.enabled && !revealedPlayers.isEmpty()) {
            long now = System.currentTimeMillis();
            long duration = (long) (revealDuration.value * 1000L);
            revealedPlayers.entrySet().removeIf(e -> now - e.getValue() > duration);
        }

        if (myauSync.enabled && !myauFriendCache.isEmpty()) {
            if (++cleanupTickCounter >= 40) {
                cleanupTickCounter = 0;
                Iterator<String> it = myauFriendCache.iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    boolean stillHidden = false;
                    for (EntityPlayer player : mc.theWorld.playerEntities) {
                        if (player.getName().equals(name) && isHidden(player)) {
                            stillHidden = true;
                            break;
                        }
                    }
                    if (!stillHidden) {
                        it.remove();
                        queueCommand(".friend remove " + name);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre<?> event) {
        if (!isToggled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        if (isHiddenWithSync(player)) {
            event.setCanceled(true);
        }
    }
}
