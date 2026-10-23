package com.nezurstandalone.engine;

import com.nezurstandalone.combat.AuraTargeting;
import java.util.List;

/** Prioritize attackable enemies and short approaches, with modest focus hysteresis. */
public final class SpireTargeting<T> {
    private T current;
    public T select(List<AuraTargeting.Candidate<T>> candidates,double reach) {
        AuraTargeting.Candidate<T> best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(AuraTargeting.Candidate<T> candidate:candidates) {
            if(candidate.entity==null || !Double.isFinite(candidate.distance) || candidate.distance<0
                    || !Double.isFinite(candidate.health) || candidate.health<=0)continue;
            double score=(candidate.distance<=reach?0:100)+candidate.distance*3
                    +Math.min(40,candidate.health)*.12+Math.abs(candidate.angle)*.003
                    -(candidate.entity==current?.9:0);
            if(score<bestScore || score==bestScore && (best==null || candidate.id<best.id)) {
                best=candidate;bestScore=score;
            }
        }
        current=best==null?null:best.entity;return current;
    }
    public void reset(){current=null;}
}
