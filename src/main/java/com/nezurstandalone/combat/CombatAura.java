package com.nezurstandalone.combat;

import com.nezurstandalone.input.GuardedInput;
import com.nezurstandalone.utils.RotationManager;
import com.nezurstandalone.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * A self-contained melee driver: aim through the shared {@link RotationManager}, attack through
 * the human-shaped {@link AutoClicker}. This is the combat half the autogrinder will sit on —
 * it replaces the old external Myau bridge entirely, so every attack now comes from the client's
 * own rotation model and its own click timing rather than commands fired at another mod.
 *
 * <p>It is a driver, not a module: no event bus, no toggle. The owning module picks a target,
 * pumps {@link #onRenderFrame()} every frame for smooth sub-tick aim and click timing, and calls
 * {@link #stop()} when it is done. The rotation request carries a per-frame TTL, so simply
 * ceasing to pump releases the camera on its own.
 *
 * <h2>What it does and does not decide</h2>
 * It decides <em>how</em> to fight the target it is given — where to look and when to click — and
 * gates clicks on the target actually being in reach and in front of the crosshair. It does not
 * decide <em>who</em> or <em>whether</em> to fight; target selection and engage/disengage belong
 * to the module, which knows the context (a care-package run defending itself, a grinder working
 * a mob) the driver has no view of.
 */
public final class CombatAura {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Object inputOwner = new Object();
    private final String rotationOwner = "combat." + java.util.UUID.randomUUID();
    private java.util.function.BooleanSupplier activityAllowed = () -> true;

    private java.util.function.BooleanSupplier unlistedPitNpcsAllowed = () -> false;

    /** Opt-in per consumer; AutoGrinder enables this only on Pit Classic. */
    public CombatAura allowUnlistedPitNpcsWhen(java.util.function.BooleanSupplier allowed) {
        unlistedPitNpcsAllowed = allowed;
        return this;
    }

    private boolean isAllowedUnlistedPitNpc(EntityPlayer p) {
        return unlistedPitNpcsAllowed.getAsBoolean()
                && "THE PIT CLASSIC".equalsIgnoreCase(com.nezurstandalone.utils.Utils.getScoreboardTitle().trim())
                && "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(p.posX, p.posY, p.posZ));
    }

    public CombatAura allowedWhen(java.util.function.BooleanSupplier allowed) {
        activityAllowed = allowed;
        return this;
    }

    // No attention-lapse pauses in combat: while a target is genuinely in range and in front, the
    // hand should keep swinging, not take a 300-850ms break mid-fight. The natural CPS jitter and
    // the occasional double-tap stay for a human cadence; only the long stall is removed, so it
    // always spams while a target is in reach.
    private final AutoClicker clicker = new AutoClicker()
            .button(AutoClicker.Button.LEFT)
            .cps(8, 12)
            .pauseChance(0.0)
            .emitter(this::queueAttack);

    /** Aim aggression handed to the rotation manager; 10 is unscaled human pace. */
    private float rotationSpeed = 15.0f;

    /** Maximum eye-to-raycast-hit distance; never extends native survival reach. */
    private double reach = 3.0;
    private double nearbyFirstRange;

    /** Opt-in: select TTK winners from the nearby group before considering farther players. */
    public CombatAura nearbyFirst(double range) {nearbyFirstRange=Math.max(0,range);return this;}
    public static int proximityTier(double distance,double range) {return range>0 && distance>range?1:0;}

    /** Half-angle, in degrees, the crosshair must be within before a click is allowed. */
    private double fovToClick = 35.0;

    /**
     * Distance, in blocks, inside which the clicker runs continuously - independent of reach or
     * where the crosshair is. Holding the click down while a target is near means the hit lands
     * the instant they step into melee, which is the first-strike advantage a person gets by
     * pre-clicking rather than reacting. Aim still tracks the target the whole time.
     *
     * <p>Derived from {@link #reach} rather than configured independently: continuous clicking
     * starts just past true melee reach (reach + 0.6), so the pre-click window exists but never
     * stretches far enough to produce a long stream of swings at empty air while closing
     * distance - constant swing-animation-at-nothing is visible to anyone watching and pads the
     * swing statistics oddly.
     */
    private double clickRange = reach + 0.6;

    public enum Stage { NO_TARGET, TRACKING, ALIGNING, VALID_RAYCAST, ATTACK_READY, COOLDOWN }
    private Stage stage = Stage.NO_TARGET;
    private long targetGeneration;
    public Stage getStage() { return stage; }
    private Entity target;

    // --- Aim-point wander -----------------------------------------------------
    // Real aim does not sit on the exact geometric centre frame after frame; it drifts over a
    // small patch of the body. An Ornstein-Uhlenbeck walk over a world-space offset keeps the
    // aim point moving smoothly around the target inside its hitbox, so the rotation stream is
    // not a perfectly repeatable "lock the centre" signature.
    private final java.util.Random rng = new java.util.Random();
    private double aimOffX, aimOffY, aimOffZ;
    private long lastWanderNanos;
    // Kept deliberately small: a wide wander reads as a shaking crosshair. This is just enough to
    // pull the aim off the exact geometric centre without the camera visibly vibrating.
    private static final double WANDER_THETA = 1.4;   // reversion strength, per second (lower = smoother drift)
    private static final double WANDER_H = 0.11;      // horizontal wander cap, blocks
    private static final double WANDER_V = 0.14;      // vertical wander cap, blocks

    // ------------------------------------------------------------------ config

    public CombatAura cps(double min, double max) {
        clicker.cps(min, max);
        com.nezurstandalone.input.NativeActionGate.maxCps(Math.max(min,max));
        return this;
    }

    public CombatAura reach(double blocks) {
        this.reach = Math.max(1.0, Math.min(3.0, blocks));
        // Keep the pre-click window tied to reach (see clickRange javadoc).
        this.clickRange = this.reach + 0.6;
        return this;
    }

    public CombatAura rotationSpeed(float speed) {
        this.rotationSpeed = speed;
        return this;
    }

    public CombatAura fovToClick(double degrees) {
        this.fovToClick = Math.max(1.0, degrees);
        return this;
    }

    public CombatAura clickRange(double blocks) {
        // Clamped to at most reach + 0.6: widening the continuous-click band past that just
        // produces swings at air (see the clickRange javadoc).
        this.clickRange = Math.max(1.0, Math.min(blocks, this.reach + 0.6));
        return this;
    }

    /** Exposes the underlying clicker for finer tuning (jitter, pauses, one-per-tick, ...). */
    public AutoClicker clicker() {
        return clicker;
    }

    // ------------------------------------------------------------------ driving

    public void setTarget(Entity target) {
        if (this.target != target) {
            targetGeneration++;
            GuardedInput.cancel(inputOwner);
            if (!com.nezurstandalone.engine.GrinderEngine.isMidPressureActive(this)) clicker.reset();
            RotationManager.getInstance().clearTarget(rotationOwner);
            aimOffX = aimOffY = aimOffZ = 0; lastWanderNanos = 0;
            stage = target == null ? Stage.NO_TARGET : Stage.TRACKING;
        }
        this.target = target;
    }

    public Entity getTarget() {
        return target;
    }

    /**
     * Drives aim and clicks for one render frame. Aims at the current target whenever there is
     * one, and lets the clicker fire only while that target is genuinely in reach and inside the
     * click cone — so a click never leaves while the crosshair is still swinging onto a target,
     * which is both correct and the shape of a real hit.
     */
    public void onRenderFrame() { renderFrame(false); }

    /** Tracking only during AutoHeal: cancel pending attacks without dropping the aim target. */
    public void onAimOnlyFrame() { renderFrame(true); }

    private void renderFrame(boolean aimOnly) {
        if (aimOnly) { GuardedInput.cancel(inputOwner); clicker.reset(); }
        if ((!aimOnly && !activityAllowed.getAsBoolean()) || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null
                || (!aimOnly && mc.thePlayer.isUsingItem()) || target == null || target.isDead
                || target.worldObj != mc.theWorld) {
            stop();
            return;
        }

        advanceWander();
        float[] rot = RotationUtils.getRotations(target, aimOffX, aimOffY, aimOffZ);
        // Combat priority + responsive tracking: fast, direct follow of a moving target.
        RotationManager.getInstance().setTargetRotation(
                rotationOwner, RotationManager.PRIORITY_COMBAT,
                rot[0], rot[1], rotationSpeed, true);

        if (aimOnly) { stage = Stage.TRACKING; return; }

        // Grinder owns an independent region click pump; this driver supplies aim only there.
        if (com.nezurstandalone.engine.GrinderEngine.usesPitClicker(this)) {
            GuardedInput.cancel(inputOwner);
            clicker.reset();
            return;
        }

        // The native raycast must identify this target; an angular cone is insufficient.
        boolean allowed = isCurrentHit(target);
        stage = allowed ? Stage.VALID_RAYCAST : Stage.ALIGNING;
        if (!allowed) GuardedInput.cancel(inputOwner);
        // Keep the mid cadence running across aim gaps; dispatch still requires an actual reachable hit.
        clicker.onRenderFrame(allowed || com.nezurstandalone.engine.GrinderEngine.isMidPressureActive(this));
    }

    private boolean isCurrentHit(Entity entity) {
        if (entity instanceof EntityPlayer && selectionFilter != null && !selectionFilter.test((EntityPlayer) entity)) return false;
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        return activityAllowed.getAsBoolean() && entity != null && mc.thePlayer != null && mc.currentScreen == null
                && com.nezurstandalone.contract.ContractCombatPolicy.allowsAttack(entity)
                && !mc.thePlayer.isUsingItem() && !entity.isDead && entity.worldObj == mc.theWorld
                && hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit == entity && hit.hitVec != null
                && mc.thePlayer.getPositionEyes(1.0F).distanceTo(hit.hitVec) <= reach;
    }

    private void queueAttack() {
        if (!isCurrentHit(target)) return;
        stage = Stage.ATTACK_READY;
        final long generation = targetGeneration;
        final Entity intended = target;
        GuardedInput.attack(inputOwner, intended, () -> generation == targetGeneration && target == intended && isCurrentHit(intended));
        GuardedInput.watch(inputOwner, status -> {
            if (generation != targetGeneration) return;
            if (status == GuardedInput.Status.NATIVE_INVOKED) stage = Stage.COOLDOWN;
            else if (status == GuardedInput.Status.DROPPED || status == GuardedInput.Status.CANCELLED)
                stage = target == null ? Stage.NO_TARGET : Stage.ALIGNING;
        });
    }

    /** Cancels this driver's input and rotation without affecting other activities. */
    public void stop() {
        setTarget(null);
        clicker.reset();
        com.nezurstandalone.control.Cleanup.run(
            () -> GuardedInput.cancel(inputOwner),
            () -> RotationManager.getInstance().clearTarget(rotationOwner));
    }

    public boolean hasTarget() {
        return target != null && !target.isDead;
    }

    // ------------------------------------------------------------------ internals

    /** Advances the Ornstein-Uhlenbeck aim-point wander one frame. */
    private void advanceWander() {
        long now = System.nanoTime();
        double dt = lastWanderNanos == 0 ? 0.0 : Math.min(0.1, (now - lastWanderNanos) / 1_000_000_000.0);
        lastWanderNanos = now;
        if (dt <= 0.0) {
            return;
        }
        double a = Math.exp(-WANDER_THETA * dt);
        double driveH = Math.sqrt(1.0 - a * a);
        aimOffX = clamp(aimOffX * a + rng.nextGaussian() * driveH * (WANDER_H * 0.7), WANDER_H);
        aimOffY = clamp(aimOffY * a + rng.nextGaussian() * driveH * (WANDER_V * 0.7), WANDER_V);
        aimOffZ = clamp(aimOffZ * a + rng.nextGaussian() * driveH * (WANDER_H * 0.7), WANDER_H);
    }

    private static double clamp(double v, double lim) {
        return v < -lim ? -lim : (v > lim ? lim : v);
    }

    /** Angle between the current view and the aim point, degrees. */
    private double angleToTarget(float targetYaw, float targetPitch) {
        float dYaw = net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float dPitch = targetPitch - mc.thePlayer.rotationPitch;
        return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }

    /** Convenience: closest attackable player within reach+slack, or null. Target-selection help. */
    public EntityPlayer closestPlayer(double maxDist) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        EntityPlayer best = null;
        double bestDist = maxDist;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.isInvisible()) {
                continue;
            }
            if (!mc.thePlayer.canEntityBeSeen(p)) continue;
            if (com.nezurstandalone.module.impl.render.Focus.isHidden(p)) continue;
            double d = mc.thePlayer.getDistanceToEntity(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // ================================================================= target selection
    // A richer picker than closestPlayer, ported from the standalone grinder combat so the
    // shared combat driver itself carries the smart target logic: team filter (scoreboard team
    // + name-colour match, only while TDM is live), low-HP prioritisation, friend/Focus/NPC
    // filters, line-of-sight, a vertical tolerance, and a sticky current target that only gives
    // way to a meaningfully closer or diamond-armoured one. The owning module chooses the
    // policy per call; this decides *who*, the driver still decides *how* to fight them.

    private EntityPlayer stickyTarget;
    private java.util.function.Predicate<EntityPlayer> selectionFilter;
    private java.util.function.ToDoubleFunction<EntityPlayer> selectionScore;
    private int sameTargetTicks;
    private int tickCollided;

    /**
     * Picks the best player to fight under the given policy, or null. Keeps a sticky target so
     * aim does not flicker between two equidistant players every tick, but drops it for one that
     * is clearly closer, one that has just shown a diamond chestplate inside melee reach, or when
     * it dies / vanishes. Call once per tick.
     *
     * @param aimReach          farthest distance a candidate may sit at, in blocks
     * @param attackReach       distance inside which the in-reach ordering (angle-first) applies
     * @param pitZoneOnly       when true, only players standing in the Pit zone qualify
     * @param prioritizeHealth  order by lowest health first, then distance/angle
     * @param teamCheckTdm      skip scoreboard/colour teammates while a TDM event is running
     */
    public EntityPlayer pickTarget(double aimReach, double attackReach, boolean pitZoneOnly,
                                   boolean prioritizeHealth, boolean teamCheckTdm) {
        return pickTarget(aimReach, attackReach, pitZoneOnly, prioritizeHealth, teamCheckTdm, null);
    }

    /** Owner-specific eligibility also applies to queued attacks, including armor changes. */
    public EntityPlayer pickTarget(double aimReach, double attackReach, boolean pitZoneOnly,
                                   boolean prioritizeHealth, boolean teamCheckTdm,
                                   java.util.function.Predicate<EntityPlayer> filter) {
        return pickTarget(aimReach,attackReach,pitZoneOnly,prioritizeHealth,teamCheckTdm,filter,null);
    }

    public EntityPlayer pickTarget(double aimReach, double attackReach, boolean pitZoneOnly,
                                   boolean prioritizeHealth, boolean teamCheckTdm,
                                   java.util.function.Predicate<EntityPlayer> filter,
                                   java.util.function.ToDoubleFunction<EntityPlayer> score) {
        selectionFilter = filter;
        selectionScore = score;
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        // Drop a sticky target that is no longer legitimate BEFORE anything else. Without this the
        // lock survived every filter - so a player who stepped behind a Spire pillar stayed the
        // target and the bot kept aiming and swinging through the wall.
        if (stickyTarget != null && !isStillValidTarget(stickyTarget, aimReach, pitZoneOnly, teamCheckTdm)) {
            stickyTarget = null;
            sameTargetTicks = 0;
        }

        EntityPlayer fresh=findBest(aimReach,attackReach,null,pitZoneOnly,true,teamCheckTdm);
        if(stickyTarget==null)stickyTarget=fresh;
        else if(fresh!=null && fresh!=stickyTarget && (contractTier(fresh)<contractTier(stickyTarget)
                || (selectionScore!=null?selectionScore.applyAsDouble(fresh)<selectionScore.applyAsDouble(stickyTarget)
                    :TtkMath.better(evaluation(fresh,attackReach).ttk,
                        evaluation(stickyTarget,attackReach).ttk,ttkImprovement))))stickyTarget=fresh;
        if(ttkDebug && stickyTarget!=null && System.currentTimeMillis()-lastTtkLog>2000){
            lastTtkLog=System.currentTimeMillis();System.out.println("[TargetScore] "+stickyTarget.getName()+" "+evaluation(stickyTarget,attackReach));
        }

        return stickyTarget;
    }

    /**
     * Whether a target we already locked onto still qualifies: alive, visible, in range, on the
     * right side, and — the part that matters on Spire — still in genuine line of sight. Losing
     * sight of someone must drop the lock, never keep swinging at a wall.
     */
    private boolean isStillValidTarget(EntityPlayer p, double aimReach, boolean pitZoneOnly, boolean teamCheckTdm) {
        if (p != null && selectionFilter != null && !selectionFilter.test(p)) return false;
        if (p == null || p == mc.thePlayer || p.worldObj != mc.theWorld
                || Math.abs(p.posY - mc.thePlayer.posY) > 8.0
                || (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(p.getUniqueID()) == null
                    && !isAllowedUnlistedPitNpc(p)) || p.isDead || p.getHealth() <= 0 || p.isInvisible()) return false;
        if (!com.nezurstandalone.contract.ContractCombatPolicy.allowsTarget(p)) return false;
        if (Math.abs(p.posY-mc.thePlayer.posY)>8) return false;
        if (mc.thePlayer.getDistanceToEntity(p) > aimReach) return false;
        if (!mc.thePlayer.canEntityBeSeen(p)) return false;                 // behind a wall
        if (com.nezurstandalone.utils.FriendManager.isFriend(p)) return false;
        if (com.nezurstandalone.module.impl.render.Focus.isHidden(p)) return false;
        if (shouldSkipTeammate(p, teamCheckTdm)) return false;
        if (pitZoneOnly && !com.nezurstandalone.utils.PitMapManager.getZone(p.posX, p.posY, p.posZ).equals("Pit")) return false;
        return true;
    }

    /** Clears the sticky target state (call when combat ends). */
    public void resetTargeting() {
        stickyTarget = null;
        sameTargetTicks = 0;
        tickCollided = 0;
    }

    private EntityPlayer findBest(double aimReach, double attackReach, EntityPlayer skip, boolean pitZoneOnly,
                                  boolean prioritizeHealth, boolean teamCheckTdm) {
        EntityPlayer best=null;double score=Double.POSITIVE_INFINITY;int bestTier=Integer.MAX_VALUE;
        for(EntityPlayer player:mc.theWorld.playerEntities){
            if(player==skip || !isStillValidTarget(player,aimReach,pitZoneOnly,teamCheckTdm))continue;
            int tier=contractTier(player);
            double value=selectionScore==null?evaluation(player,attackReach).ttk:selectionScore.applyAsDouble(player);
            if(tier<bestTier || (tier==bestTier && (value<score || (value==score && best!=null && player.getEntityId()<best.getEntityId())))){bestTier=tier;score=value;best=player;}
        }
        return best;
    }
    // Contractor searches the nearest occupied 5-block band; normal grinder uses all valid candidates.
    private int contractTier(EntityPlayer player){
        if(com.nezurstandalone.contract.ContractCombatPolicy.type()==null)
            return proximityTier(mc.thePlayer.getDistanceToEntity(player),nearbyFirstRange);
        return Math.max(0,(int)Math.ceil(mc.thePlayer.getDistanceToEntity(player)/5.0)-1);
    }
    private double ttkSpeed=5.6,ttkInterval=.5,ttkDamage=6,ttkImprovement=20;
    private boolean ttkDebug;private long lastTtkLog;
    public CombatAura ttk(double speed,double interval,double damage,double improvement,boolean debug){
        ttkSpeed=speed;ttkInterval=interval;ttkDamage=damage;ttkImprovement=improvement;ttkDebug=debug;return this;
    }
    private TargetEvaluation evaluation(EntityPlayer player,double reach){return new TargetEvaluation(mc.thePlayer,player,reach,ttkSpeed,ttkInterval,ttkDamage);}

    // --- Team detection (scoreboard team + name-colour), gated on a live TDM event -----------

    public static boolean isTdmEventActive() {
        for (String line : com.nezurstandalone.utils.Utils.getScoreboardLines()) {
            if (line.contains("Event: TDM")) return true;
        }
        return false;
    }

    public static boolean shouldSkipTeammate(EntityPlayer player, boolean teamCheckTdm) {
        return teamCheckTdm && isTdmEventActive()
                && isTeammate(Minecraft.getMinecraft().thePlayer, player);
    }

    /** Teammates share a scoreboard team, or failing that, the same name colour. */
    public static boolean isTeammate(EntityPlayer self, EntityPlayer other) {
        net.minecraft.client.Minecraft m = Minecraft.getMinecraft();
        if (self == null || other == null || m.theWorld == null) {
            return false;
        }
        net.minecraft.scoreboard.Scoreboard sb = m.theWorld.getScoreboard();
        net.minecraft.scoreboard.Team ts = sb.getPlayersTeam(self.getName());
        net.minecraft.scoreboard.Team to = sb.getPlayersTeam(other.getName());
        if (ts != null && to != null && ts.isSameTeam(to)) {
            return true;
        }
        String cs = playerColor(self);
        String co = playerColor(other);
        return cs != null && co != null && cs.equals(co);
    }

    private static String playerColor(EntityPlayer player) {
        if (player == null || Minecraft.getMinecraft().theWorld == null) return null;
        String formatted = player.getDisplayName().getFormattedText();
        String real = player.getName();
        if (formatted == null || real == null || real.isEmpty()) return null;
        int nameIndex = formatted.lastIndexOf(real);
        if (nameIndex <= 0) {
            if (formatted.startsWith("§") && formatted.length() >= 2) return formatted.substring(0, 2);
            return null;
        }
        for (int i = nameIndex - 1; i >= 0; i--) {
            if (formatted.charAt(i) == '§' && i + 1 < formatted.length()) {
                char ch = formatted.charAt(i + 1);
                if (isColorCode(ch)) return "§" + ch;
            }
        }
        return null;
    }

    private static boolean isColorCode(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private static boolean hasDiamondChestplate(EntityPlayer player) {
        for (net.minecraft.item.ItemStack stack : player.inventory.armorInventory) {
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemArmor) {
                net.minecraft.item.ItemArmor armor = (net.minecraft.item.ItemArmor) stack.getItem();
                if (armor.getArmorMaterial() == net.minecraft.item.ItemArmor.ArmorMaterial.DIAMOND && armor.armorType == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
