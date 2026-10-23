package com.nezurstandalone.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Target ordering and switching, independent of the Minecraft runtime. */
public final class AuraTargeting<T> {
    public enum Mode { SINGLE, SWITCH }
    public enum Sort { DISTANCE, HEALTH, HURT_TIME, FOV, SMART }

    public static final class Candidate<T> {
        public final T entity;
        public final int id;
        public final double distance, health, angle, score;
        public final int hurtTime;

        public Candidate(T entity, int id, double distance, double health, int hurtTime,
                         double angle, double score) {
            this.entity = entity;
            this.id = id;
            this.distance = distance;
            this.health = health;
            this.hurtTime = hurtTime;
            this.angle = angle;
            this.score = score;
        }

        private boolean valid() {
            return entity != null && Double.isFinite(distance) && distance >= 0
                    && Double.isFinite(health) && health > 0 && Double.isFinite(angle)
                    && Double.isFinite(score);
        }
    }

    private T current;
    private long decidedAt;
    private boolean attackPending;

    /** A confirmed native attack permits one switch; swings and elapsed time alone do not. */
    public void attacked(T entity) {
        if (entity == current) attackPending = true;
    }

    /** Call with freshly filtered candidates. A lost target never survives the switch delay. */
    public T select(List<Candidate<T>> candidates, Mode mode, Sort sort, double reach,
                    long switchDelayMs, long nowNanos) {
        List<Candidate<T>> ordered = new ArrayList<>();
        boolean hasReachable = false;
        for (Candidate<T> candidate : candidates) {
            if (!candidate.valid()) continue;
            ordered.add(candidate);
            if (candidate.distance <= reach) hasReachable = true;
        }
        if (hasReachable) ordered.removeIf(candidate -> candidate.distance > reach);
        if (ordered.isEmpty()) {
            reset();
            return null;
        }
        ordered.sort(comparator(sort));
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).entity == current) { index = i; break; }
        }
        if (index < 0) return choose(ordered.get(0).entity, nowNanos);

        long delayNanos = Math.max(0, Math.min(1000, switchDelayMs)) * 1_000_000L;
        if (nowNanos - decidedAt < delayNanos) return current;
        decidedAt = nowNanos;
        if (mode == Mode.SINGLE) return choose(ordered.get(0).entity, nowNanos);
        if (attackPending) return choose(ordered.get((index + 1) % ordered.size()).entity, nowNanos);
        return current;
    }

    private T choose(T entity, long nowNanos) {
        current = entity;
        decidedAt = nowNanos;
        attackPending = false;
        return current;
    }

    private Comparator<Candidate<T>> comparator(Sort sort) {
        return (a, b) -> {
            int order;
            switch (sort) {
                case HEALTH: order = Double.compare(a.health, b.health); break;
                case HURT_TIME: order = Integer.compare(a.hurtTime, b.hurtTime); break;
                case FOV: order = Double.compare(a.angle, b.angle); break;
                case SMART: order = Double.compare(a.score, b.score); break;
                default: order = Double.compare(a.distance, b.distance);
            }
            if (order == 0) order = Double.compare(a.distance, b.distance);
            return order != 0 ? order : Integer.compare(a.id, b.id);
        };
    }

    public void reset() {
        current = null;
        decidedAt = 0;
        attackPending = false;
    }
}
