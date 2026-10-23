package com.nezurstandalone.engine;

import com.nezurstandalone.combat.AuraTargeting;
import java.util.*;

/** One observed damage response per opponent life; despawning alone never clears a tag. */
public final class TdmAssistTracker {
    private static final int LIMIT=512;
    private static final long RESPONSE_WINDOW=750_000_000L;
    private static final class Attempt {
        final Object actor;final long sentAt;
        Attempt(Object actor,long sentAt){this.actor=actor;this.sentAt=sentAt;}
    }
    private final Map<UUID,Object> tagged=new HashMap<>();
    private final LinkedHashMap<UUID,Attempt> pending=new LinkedHashMap<>();
    private Object selected;
    private int totalTags;

    public boolean allows(UUID player){return player!=null&&!tagged.containsKey(player)&&tagged.size()<LIMIT;}
    public void sent(UUID player,Object actor,long now){
        if(!allows(player)||actor==null)return;
        if(pending.size()>=LIMIT&&!pending.containsKey(player))pending.remove(pending.keySet().iterator().next());
        pending.put(player,new Attempt(actor,now));
    }
    public boolean hurt(UUID player,Object actor,long now){
        Attempt attempt=pending.remove(player);
        if(attempt==null||attempt.actor!=actor||now-attempt.sentAt<0||now-attempt.sentAt>RESPONSE_WINDOW||!allows(player))return false;
        tagged.put(player,actor);totalTags++;if(selected==actor)selected=null;return true;
    }
    public void death(UUID player){tagged.remove(player);pending.remove(player);}
    public void seen(UUID player,Object actor){if(actor!=null&&tagged.containsKey(player))tagged.put(player,actor);}
    public void death(UUID player,Object actor){
        if(tagged.get(player)==actor)tagged.remove(player);
        Attempt attempt=pending.get(player);if(attempt!=null&&attempt.actor==actor)pending.remove(player);
        if(selected==actor)selected=null;
    }
    public int aliveTagged(){return tagged.size();}
    public int totalTags(){return totalTags;}
    public void invalidatePending(){pending.clear();selected=null;}
    public void reset(){tagged.clear();pending.clear();selected=null;totalTags=0;}
    public static boolean survivesTag(double health,double damage,double multiplier){
        return Double.isFinite(health)&&Double.isFinite(damage)&&Double.isFinite(multiplier)
                &&damage>0&&multiplier>0&&health>damage*multiplier*1.5;
    }

    /** Nearby fresh opponents first; prefer hittable, healthier opponents over likely finishing blows. */
    public <T> T select(List<AuraTargeting.Candidate<T>> candidates,double reach){
        boolean close=false;for(AuraTargeting.Candidate<T> c:candidates)if(c.distance<=reach)close=true;
        AuraTargeting.Candidate<T> best=null,current=null;double bestScore=Double.POSITIVE_INFINITY,currentScore=0;
        for(AuraTargeting.Candidate<T> c:candidates){
            if(c.entity==null||c.health<=0||!Double.isFinite(c.health)||!Double.isFinite(c.distance)||c.distance<0||close&&c.distance>reach)continue;
            double score=c.distance+(c.hurtTime>0?6:0)+(c.health<=8?8:0);
            if(score<bestScore||score==bestScore&&best!=null&&c.id<best.id){best=c;bestScore=score;}
            if(c.entity==selected){current=c;currentScore=score;}
        }
        if(current!=null&&currentScore<=bestScore+1.0)best=current;
        selected=best==null?null:best.entity;return best==null?null:best.entity;
    }
}
