import com.nezurstandalone.engine.TdmAssistTracker;
import com.nezurstandalone.combat.AuraTargeting.Candidate;
import java.util.*;

public final class TdmAssistTrackerTest {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static Candidate<Object> c(Object player,int id,double distance,double health,int hurt){return new Candidate<>(player,id,distance,health,hurt,0,0);}
    public static void main(String[] args){
        TdmAssistTracker t=new TdmAssistTracker();UUID a=UUID.randomUUID(),b=UUID.randomUUID();Object first=new Object(),second=new Object();
        check(t.allows(a),"Fresh opponent can be targeted");
        check(!t.hurt(a,first,100),"Another player's damage cannot create a tag without our outgoing attack");
        t.sent(a,first,100);check(t.allows(a),"An attack attempt alone never blocks an opponent");
        check(t.hurt(a,first,200)&&!t.allows(a),"A matching recent hurt response tags opponent life");
        check(!t.hurt(a,first,210)&&t.totalTags()==1,"Duplicate hurt cannot create duplicate tags");
        t.sent(a,first,220);check(!t.hurt(a,first,230),"Repeated input cannot tag the same life again");
        check(t.allows(b),"Other opponents remain available");
        t.sent(b,second,1000);check(!t.hurt(b,second,750002000L)&&t.allows(b),"Stale damage response does not count");
        t.sent(b,second,2000);check(!t.hurt(b,new Object(),2010)&&t.allows(b),"A replaced actor cannot inherit pending attack evidence");
        t.sent(b,second,3000);check(!t.hurt(b,second,2990),"Backwards time cannot confirm contact");
        check(!t.allows(a),"An opponent remains tagged without a confirmed death; no timeout fallback");
        t.death(a);check(t.allows(a)&&t.aliveTagged()==0,"Death or observed respawn releases that life");
        t.sent(a,first,4000);check(t.hurt(a,first,4100)&&t.totalTags()==2,"A new life can be tagged again");
        t.sent(b,second,4200);t.invalidatePending();
        check(!t.allows(a)&&!t.hurt(b,second,4210),"Own player replacement cancels pending hits but retains other players' life tags");
        t.sent(b,second,5000);t.death(b);check(!t.hurt(b,second,5010),"Death cancels pre-respawn pending hits");
        Object low=new Object(),healthy=new Object(),far=new Object();
        check(t.select(Arrays.asList(c(low,1,2.0,3,0),c(healthy,2,2.3,20,0)),3)==healthy,"Prefer tagging a healthy nearby enemy over a finishing blow");
        check(t.select(Arrays.asList(c(healthy,2,2.3,20,8),c(low,1,2.5,10,0)),3)==low,"Prefer hittable enemies over one currently in damage immunity");
        check(t.select(Arrays.asList(c(low,1,2.5,10,0),c(far,3,12,20,0)),3)==low,"Nearby fresh enemy wins over distant chase");
        t.reset();check(t.select(Arrays.asList(c(first,1,2,20,0),c(second,2,2.3,20,0)),3)==first,"Select nearest healthy fresh opponent");
        check(t.select(Arrays.asList(c(first,1,2.3,20,0),c(second,2,2.0,20,0)),3)==first,"Small distance changes do not thrash targets before a hit");
        check(t.select(Collections.<Candidate<Object>>emptyList(),3)==null,"No fresh candidates means no fallback attack");
        t.reset();check(t.aliveTagged()==0&&t.totalTags()==0&&t.allows(a),"Event/world/session reset clears all observations");
        check(!TdmAssistTracker.survivesTag(8,6,1),"Avoid likely lethal unarmored hits, including critical-hit margin");
        check(TdmAssistTracker.survivesTag(20,6,1),"Healthy unarmored opponent can receive a tag");
        check(TdmAssistTracker.survivesTag(5,6,.4),"Armor can make a low-health opponent safe to tag");
        check(!TdmAssistTracker.survivesTag(Double.NaN,6,1),"Unknown health cannot authorize a finishing blow");
        Object replacement=new Object();t.sent(a,replacement,10000);t.hurt(a,replacement,10100);t.death(a,first);
        check(!t.allows(a),"An old actor's late death cannot clear the replacement life's tag");
        t.death(a,replacement);check(t.allows(a),"The replacement life's own death releases its tag");
        t.sent(a,first,11000);t.hurt(a,first,11100);t.seen(a,replacement);
        check(!t.allows(a),"Ordinary entity reload retains the UUID life tag");
        t.death(a,replacement);check(t.allows(a),"Death after entity reload still ends the observed life");
        System.out.println("TdmAssistTracker: "+checks+" attribution, life, expiry, reset and assist-selection checks passed");
    }
}
