package com.nezurstandalone.engine;

import com.nezurstandalone.combat.AuraTargeting;
import java.util.*;
import java.util.regex.*;

/** One named server hit-bar acknowledgment per opponent life; Bonk/hurt animations are not assist evidence. */
public final class TdmAssistTracker {
    private static final int LIMIT=512;
    private static final long RESPONSE_WINDOW=750_000_000L;
    private static final class Attempt {
        final Object actor;final long sentAt;final String name;
        Attempt(Object actor,String name,long sentAt){this.actor=actor;this.name=name;this.sentAt=sentAt;}
    }
    private final Map<UUID,Object> tagged=new HashMap<>();
    private final LinkedHashMap<UUID,Attempt> pending=new LinkedHashMap<>();
    private Object selected;
    private int totalTags;

    public boolean allows(UUID player){return player!=null&&!tagged.containsKey(player)&&tagged.size()<LIMIT;}
    /** Avoid duplicate queued attacks while the last hit is awaiting its server response. */
    public boolean ready(UUID player,long now){
        if(!allows(player))return false;
        Attempt attempt=pending.get(player);
        if(attempt==null)return true;
        if(now-attempt.sentAt>=0&&now-attempt.sentAt<=RESPONSE_WINDOW)return false;
        pending.remove(player);return true;
    }
    public void sent(UUID player,Object actor,long now){sent(player,actor,"",now);}
    public void sent(UUID player,Object actor,String name,long now){
        if(!allows(player)||actor==null)return;
        if(pending.size()>=LIMIT&&!pending.containsKey(player))pending.remove(pending.keySet().iterator().next());
        pending.put(player,new Attempt(actor,name,now));
    }
    public boolean acknowledge(UUID player,Object actor,long now){
        Attempt attempt=pending.remove(player);
        if(attempt==null||attempt.actor!=actor||now-attempt.sentAt<0||now-attempt.sentAt>RESPONSE_WINDOW||!allows(player))return false;
        tagged.put(player,actor);totalTags++;if(selected==actor)selected=null;return true;
    }
    public static String hitBarName(int channel,String text){
        return channel==2&&text!=null&&text.length()<=512?victimName(text):null;
    }
    /** Called once per inbound packet, never by polling the currently rendered action bar. */
    public boolean feedback(int channel,String text,long now){
        String name=hitBarName(channel,text);if(name==null)return false;
        UUID match=null;Attempt evidence=null;
        for(Map.Entry<UUID,Attempt> entry:pending.entrySet()){
            Attempt attempt=entry.getValue();
            if(!name.equalsIgnoreCase(attempt.name)||now-attempt.sentAt<0||now-attempt.sentAt>RESPONSE_WINDOW)continue;
            if(match!=null)return false; // Ambiguous same-name actors cannot confirm either life.
            match=entry.getKey();evidence=attempt;
        }
        return match!=null&&acknowledge(match,evidence.actor,now);
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

    private static final String NAME = "(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)*([A-Za-z0-9_]{1,16})";
    private static final Pattern HIT_BAR = Pattern.compile("^" + NAME + "\\s*[\\u2764\\u2665\\u2661\\s]+$");
    public static String victimName(String text) {
        if (text == null) return null;
        Matcher match = HIT_BAR.matcher(text.replaceAll("\u00a7[0-9A-FK-ORa-fk-or]", "").trim());
        return match.matches() ? match.group(1) : null;
    }
}
