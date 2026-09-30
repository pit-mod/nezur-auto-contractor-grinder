package com.nezurstandalone.module.impl.player;

import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemSoup;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

public class AutoHeal extends Module {

    private static AutoHeal instance;
    public static void interrupt() { if (instance != null) instance.abortHeal(); }
    private int useAttempts;
    private long queueDeadline;
    private long lastDropTrace;
    private boolean observedUse;
    private com.nezurstandalone.control.HealTransaction transaction;
    private long evidenceRevision,leaseGeneration;
    public com.nezurstandalone.control.HealTransaction.State result(){return transaction==null?com.nezurstandalone.control.HealTransaction.State.CANCELLED:transaction.state();}

    private final NumberSetting skullHealth = new NumberSetting("Skull Health", 28.0, 1.0, 40.0, 1);
    private final NumberSetting soupHealth = new NumberSetting("Soup Health", 28.0, 1.0, 40.0, 1);
    private final NumberSetting steakHealth = new NumberSetting("Steak Health", 28.0, 1.0, 40.0, 1);
    private final NumberSetting eggHealth = new NumberSetting("Egg Health", 28.0, 1.0, 40.0, 1);
    private final NumberSetting potatoHealth = new NumberSetting("Potato Health", 50.0, 1.0, 60.0, 1);
    private final NumberSetting spireSoulHealth = new NumberSetting("Spire Soul Health", 28.0, 1.0, 40.0, 1);

    private final NumberSetting cooldownMs = new NumberSetting("Cooldown (ms)", 500.0, 50.0, 2000.0, 0);
    private final NumberSetting reactionTimeMs = new NumberSetting("Reaction Time", 75.0, 0.0, 500.0, 0);
    private final NumberSetting holdTimeMs = new NumberSetting("Hold Time", 50.0, 0.0, 300.0, 0);
    private final NumberSetting steakCooldownS = new NumberSetting("Steak Cooldown (s)", 10.0, 0.0, 60.0, 1);

    private final BooleanSetting useSkulls = new BooleanSetting("Use Skulls", true);
    private final BooleanSetting skullNoAbsorption = new BooleanSetting("Skull No Absorb", true);
    private final BooleanSetting useSoups = new BooleanSetting("Use Soups", true);
    private final BooleanSetting useSteaks = new BooleanSetting("Use Steaks", true);
    private final BooleanSetting useEggs = new BooleanSetting("Use Eggs", true);
    private final BooleanSetting usePotato = new BooleanSetting("Rage Pit Potato", true);
    private final BooleanSetting potatoNoAbsorption = new BooleanSetting("Potato No Absorb", true);
    private final BooleanSetting useSpireSouls = new BooleanSetting("Spire Soul", true);
    private final BooleanSetting randomizeAll = new BooleanSetting("Randomize Timings", true);
    private final BooleanSetting delayedUse = new BooleanSetting("Delayed Use", true);

    private long lastHealTime;
    private long lastSkullConsumptionTime;
    private int originalSlot = -1;
    private int healingSlot = -1;
    private long lastSteakTime;
    private boolean usedSteak;
    private final Object inputOwner = new Object();
    private Object healingWorld;
    private net.minecraft.entity.player.EntityPlayer healingPlayer;
    private ItemStack selectedHeal;

    private boolean mayUseHeal() {
        return isToggled() && state == State.USE && com.nezurstandalone.control.InventoryOwner.owns(this) && leaseGeneration==com.nezurstandalone.control.InventoryOwner.generation() && mc.thePlayer == healingPlayer
                && mc.theWorld == healingWorld && mc.thePlayer != null && mc.currentScreen == null
                && mc.thePlayer.inventory.currentItem == healingSlot
                && ItemStack.areItemStacksEqual(selectedHeal, mc.thePlayer.getHeldItem());
    }

    private enum State {
        IDLE, REACTION, SWITCH, SWITCH_SETTLE, USE_DELAY, USE, HOLD, SWAP_BACK
    }

    private State state = State.IDLE;
    private long stateTime;

    private final Random random = new Random();
    private float currentHealthOffset;
    private long currentCooldownOffset;
    private long currentReactionOffset;
    private long currentHoldOffset;

    public AutoHeal() {
        super("AutoHeal", "Uses hotbar heals with human-like delays.", Category.AUTO);
        instance = this;
        com.nezurstandalone.control.SessionResets.register(this,()->{resetState();lastHealTime=lastSteakTime=lastSkullConsumptionTime=0;});
        markDangerous();
        addSettings(skullHealth, soupHealth, steakHealth, eggHealth, potatoHealth, spireSoulHealth,
                cooldownMs, reactionTimeMs, holdTimeMs, steakCooldownS,
                useSkulls, skullNoAbsorption, useSoups, useSteaks, useEggs, usePotato, potatoNoAbsorption,
                useSpireSouls, randomizeAll, delayedUse);
    }

    public static boolean isHealing() {
        return instance != null && instance.isToggled() && instance.state != State.IDLE;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        resetState();
        generateOffsets();
    }

    @Override
    protected void onDisable() {
        abortHeal();
        super.onDisable();
    }

    private void resetState() {
        if(transaction!=null)transaction.cancel();
        com.nezurstandalone.control.InventoryOwner.release(this, true);
        com.nezurstandalone.input.GuardedInput.cancel(inputOwner);
        healingWorld = null;
        healingPlayer = null;
        selectedHeal = null;
        state = State.IDLE;
        useAttempts = 0;
        observedUse = false;
        originalSlot = -1;
        healingSlot = -1;
        usedSteak = false;
    }

    private void generateOffsets() {
        if (randomizeAll.enabled) {
            // Bounded variation: small configured timings cannot acquire long random tails.
            currentHealthOffset = (float) ((random.nextDouble() - random.nextDouble()) * 0.5);
            currentCooldownOffset = (long) (random.nextDouble() * Math.min(25.0, cooldownMs.value * 0.1));
            currentReactionOffset = (long) ((random.nextDouble() - random.nextDouble()) * Math.min(15.0, reactionTimeMs.value * 0.2));
            currentHoldOffset = (long) ((random.nextDouble() - random.nextDouble()) * Math.min(10.0, holdTimeMs.value * 0.2));
        } else {
            currentHealthOffset = 0;
            currentCooldownOffset = 0;
            currentReactionOffset = 0;
            currentHoldOffset = 0;
        }
    }

    private boolean isBlockedByScreen() {
        return mc.currentScreen != null;
    }

    private boolean isBusy() {
        if (mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
            ItemStack usingItem = mc.thePlayer.getItemInUse();
            if (usingItem != null) {
                Item item = usingItem.getItem();
                if (item instanceof net.minecraft.item.ItemBow 
                    || item instanceof net.minecraft.item.ItemPotion 
                    || item instanceof net.minecraft.item.ItemFood) {
                    return true;
                }
            }
        }
        return false;
    }

    private void abortHeal() {
        if (originalSlot != -1 && mc.thePlayer != null && mc.thePlayer == healingPlayer
                && mc.theWorld == healingWorld && mc.thePlayer.inventory.currentItem == healingSlot) {
            com.nezurstandalone.control.InventoryOwner.release(this, true);
        }
        resetState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Queue before the native input phase instead of waiting an extra client tick.
        if (!isToggled() || event.phase != TickEvent.Phase.START) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead
                || (state != State.IDLE && (mc.thePlayer != healingPlayer || mc.theWorld != healingWorld))) {
            resetState();
            return;
        }
        if (isBlockedByScreen() || (isBusy() && state != State.HOLD) || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            if (state != State.IDLE) {
                abortHeal();
            }
            return;
        }

        long currentTime = com.nezurstandalone.control.Clock.millis();
        float currentHealth = mc.thePlayer.getHealth();

        switch (state) {
            case IDLE:
                if (currentTime - lastHealTime < cooldownMs.value + (randomizeAll.enabled ? currentCooldownOffset : 0L)) {
                    return;
                }
                int slot = findHealingSlot(currentHealth, currentTime);
                if (slot != -1 && mc.thePlayer.inventory.getStackInSlot(slot).getItem() instanceof ItemSkull
                        && currentTime - lastSkullConsumptionTime < 1000L) return;
                if (slot != -1) {
                    generateOffsets();
                    useAttempts = 0;
                    healingSlot = slot;
                    healingPlayer = mc.thePlayer;
                    healingWorld = mc.theWorld;
                    selectedHeal = ItemStack.copyItemStack(mc.thePlayer.inventory.getStackInSlot(slot));
                    transaction=new com.nezurstandalone.control.HealTransaction(com.nezurstandalone.control.ClientSession.current(),selectedHeal.getMaxItemUseDuration()>0);
                    state = State.REACTION;
                    traceHeal("SELECTED");
                    stateTime = currentTime + (delayedUse.enabled
                            ? Math.max(0L, (long) reactionTimeMs.value + currentReactionOffset) : 0L);
                } else return;
                // Advance ready synchronous phases in this tick instead of adding 50ms per state.

            case REACTION:
                if (currentTime >= stateTime) {
                    originalSlot = mc.thePlayer.inventory.currentItem;
                    state = State.SWITCH;
                } else return;

            case SWITCH:
                // Health/absorption may have changed during reaction time. Do not swap
                // to a heal that is no longer needed or during a physical click.
                if (findHealingSlot(currentHealth, currentTime) != healingSlot) { resetState(); return; }
                if (com.nezurstandalone.input.NativeActionGate.manual()) return;
                if (!ItemStack.areItemStacksEqual(selectedHeal, mc.thePlayer.inventory.getStackInSlot(healingSlot))) {
                    abortHeal();
                    return;
                }
                if (originalSlot != mc.thePlayer.inventory.currentItem) { resetState(); return; }
                if (!com.nezurstandalone.control.InventoryOwner.select(this, healingSlot)) { abortHeal(); return; }
                leaseGeneration=com.nezurstandalone.control.InventoryOwner.generation();
                if (useAttempts == 0) evidenceRevision=com.nezurstandalone.input.InventoryEvidence.revision(healingSlot);
                transaction.selected();
                queueDeadline = currentTime + 1500L;
                state = State.USE; // Lease and server revision captured before native dispatch

            case SWITCH_SETTLE:
            case USE_DELAY:
                // Deprecated states, skipped
                state = State.USE;

            case USE:
                if (useAttempts > 0 && com.nezurstandalone.input.InventoryEvidence.consumed(healingSlot,evidenceRevision,selectedHeal)) {
                    transaction.invoked(currentTime);
                    transaction.observe(com.nezurstandalone.control.ClientSession.current(),currentTime,false,false,true);
                    traceHeal("LATE_CONSUMPTION");
                    if (selectedHeal.getItem() instanceof ItemSkull) lastSkullConsumptionTime=currentTime;
                    if (usedSteak) lastSteakTime=currentTime;
                    com.nezurstandalone.control.InventoryOwner.release(this,true); generateOffsets(); resetState(); return;
                }
                if (!mayUseHeal()) { abortHeal(); return; }
                if (currentTime >= queueDeadline) { traceHeal("QUEUE_TIMEOUT"); lastHealTime=currentTime; abortHeal(); return; }
                com.nezurstandalone.input.GuardedInput.useSelectedItem(inputOwner, this::mayUseHeal, () -> {
                    // Cooldown measures use-to-use, not server response plus another cooldown.
                    lastHealTime = com.nezurstandalone.control.Clock.millis();
                    observedUse = mc.thePlayer.isUsingItem();
                    // Native invocation is not proof of consumption or even of a sustained use.
                    transaction = new com.nezurstandalone.control.HealTransaction(com.nezurstandalone.control.ClientSession.current(),
                            selectedHeal.getMaxItemUseDuration() > 0 && observedUse);
                    transaction.selected();
                    transaction.invoked(com.nezurstandalone.control.Clock.millis());
                    useAttempts++;
                    state = State.HOLD;
                    traceHeal("USE_INVOKED");
                    // All healing items retain the selected-slot lease until result/hold completion.
                    // Only sustained food use needs a held use key; instant heads get one native click.
                    if (selectedHeal.getMaxItemUseDuration() > 0 && observedUse)
                        com.nezurstandalone.control.InventoryOwner.hold(this, true);
                    stateTime = com.nezurstandalone.control.Clock.millis() + Math.max(100L, (long) holdTimeMs.value + currentHoldOffset);
                });
                com.nezurstandalone.input.GuardedInput.watch(inputOwner, status -> {
                    if (status == com.nezurstandalone.input.GuardedInput.Status.DROPPED
                            && com.nezurstandalone.control.Clock.millis() - lastDropTrace >= 500L) {
                        lastDropTrace = com.nezurstandalone.control.Clock.millis();
                        traceHeal("USE_DROPPED");
                    }
                });
                break;

            case HOLD:
                boolean instantHeal = selectedHeal.getMaxItemUseDuration() == 0;
                if (!com.nezurstandalone.control.InventoryOwner.owns(this)) { abortHeal(); return; }
                if (leaseGeneration != com.nezurstandalone.control.InventoryOwner.generation()) { abortHeal(); return; }
                boolean using=mc.thePlayer.isUsingItem();
                transaction.observe(com.nezurstandalone.control.ClientSession.current(),currentTime,using,observedUse,
                    com.nezurstandalone.input.InventoryEvidence.consumed(healingSlot,evidenceRevision,selectedHeal));
                observedUse |= using;
                if(transaction.state()!=com.nezurstandalone.control.HealTransaction.State.HOLDING)com.nezurstandalone.control.InventoryOwner.hold(this,false);
                if(transaction.terminal()){
                    if(transaction.state()==com.nezurstandalone.control.HealTransaction.State.SUCCESS) {
                        if(currentTime<stateTime)return;
                        if (selectedHeal.getItem() instanceof ItemSkull) lastSkullConsumptionTime=currentTime;
                        traceHeal("CONSUMED"); state=State.SWAP_BACK;
                    } else if ((transaction.state()==com.nezurstandalone.control.HealTransaction.State.FAILED
                            || transaction.state()==com.nezurstandalone.control.HealTransaction.State.INTERRUPTED)
                            && !using && useAttempts < 3
                            && ItemStack.areItemStacksEqual(selectedHeal,mc.thePlayer.inventory.getStackInSlot(healingSlot))) {
                        // No consumption confirmed: all item types retry while retaining the same slot lease.
                        // Preserve the first evidence revision so a late acknowledgement still completes us.
                        transaction = new com.nezurstandalone.control.HealTransaction(com.nezurstandalone.control.ClientSession.current(), false);
                        transaction.selected(); observedUse=false; queueDeadline=currentTime+1500L; state=State.USE;
                        traceHeal("RETRY_UNCONFIRMED"); return;
                    } else {traceHeal("FAILED");lastHealTime=currentTime;abortHeal();return;}
                } else return;

            case SWAP_BACK:
                if (originalSlot != -1 && mc.thePlayer.inventory.currentItem == healingSlot) {
                    com.nezurstandalone.control.InventoryOwner.release(this, true);
                }
                if (usedSteak) {
                    lastSteakTime = currentTime;
                }
                // Keep the successful native-use timestamp; failed attempts still back off above.
                generateOffsets();
                resetState();
                break;
        }
    }

    private void traceHeal(String event) {
        com.nezurstandalone.control.GrinderDiagnostics.record("AUTOHEAL_"+event,
                "state="+state+" attempts="+useAttempts+" slot="+healingSlot
                +" using="+(mc.thePlayer!=null && mc.thePlayer.isUsingItem())
                +" result="+result()+" revision="+evidenceRevision);
    }

    private int findHealingSlot(float currentHealth, long currentTime) {
        int slot;
        if (useSoups.enabled && currentHealth <= soupHealth.value + currentHealthOffset) {
            slot = findItem(ItemSoup.class, "soup");
            if (slot != -1) {
                return slot;
            }
        }

        if (useSkulls.enabled && currentHealth <= skullHealth.value + currentHealthOffset) {
            boolean meetsAbsorption = !skullNoAbsorption.enabled || mc.thePlayer.getAbsorptionAmount() <= 0;
            if (meetsAbsorption) {
                slot = findSkull();
                if (slot != -1) {
                    return slot;
                }
            }
        }

        if (useSteaks.enabled && currentHealth <= steakHealth.value + currentHealthOffset) {
            if (currentTime - lastSteakTime >= (long) (steakCooldownS.value * 1000)) {
                slot = findItemID(423);
                if (slot != -1) {
                    usedSteak = true;
                    return slot;
                }
            }
        }

        if (useEggs.enabled && currentHealth <= eggHealth.value + currentHealthOffset) {
            slot = findEgg();
            if (slot != -1) {
                return slot;
            }
        }

        if (useSpireSouls.enabled && currentHealth <= spireSoulHealth.value + currentHealthOffset) {
            slot = findItemID(378);
            if (slot != -1) {
                return slot;
            }
        }

        if (usePotato.enabled && currentHealth <= potatoHealth.value + currentHealthOffset) {
            boolean meetsAbsorption = !potatoNoAbsorption.enabled || mc.thePlayer.getAbsorptionAmount() <= 0;
            if (meetsAbsorption) {
                slot = findItemID(393);
                if (slot != -1) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private int findItem(Class<?> clazz, String nameContains) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && (clazz.isInstance(stack.getItem())
                    || stack.getDisplayName().toLowerCase().contains(nameContains))) {
                return i;
            }
        }
        return -1;
    }

    private int findSkull() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemSkull && name.contains("\u00a76") && name.contains("Golden Head")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findItemID(int id) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && Item.getIdFromItem(stack.getItem()) == id) {
                return i;
            }
        }
        return -1;
    }

    private int findEgg() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && Item.getIdFromItem(stack.getItem()) == 383 && stack.getItemDamage() == 96) {
                return i;
            }
        }
        return -1;
    }
}
