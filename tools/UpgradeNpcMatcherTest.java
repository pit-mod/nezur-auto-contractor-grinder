import com.nezurstandalone.engine.UpgradeNpcMatcher;

public final class UpgradeNpcMatcherTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for(String label:new String[]{"PRESTIGE","Prestige & Renown","RESETS & RENOWN","Prestige and Renown","\u00a75\u00a7lPRESTIGE & RENOWN"," \u200bPRESTIGE\u00a0 & RENOWN "}) {
            check(UpgradeNpcMatcher.matches(true,label),"prestige label: "+label);
            check(!UpgradeNpcMatcher.matches(false,label),"prestige cannot match upgrades");
        }
        for(String label:new String[]{"UPGRADES","Permanent Upgrades","\u00a7e\u00a7lUPGRADES"}) {
            check(UpgradeNpcMatcher.matches(false,label),"upgrade label: "+label);
            check(!UpgradeNpcMatcher.matches(true,label),"upgrades cannot match prestige");
        }
        for(String label:new String[]{"Prestige to unlock more upgrades!","Requires Prestige IV","PRESTIGE REQUIRED","UPGRADES HERE","Click to open","Prestige & Renown cost",""}) {
            check(!UpgradeNpcMatcher.matches(true,label),"ignore instructional prestige hologram");
            check(!UpgradeNpcMatcher.matches(false,label),"ignore instructional upgrades hologram");
        }
        check(!UpgradeNpcMatcher.matches(true,null),"null label");
        double beneath=UpgradeNpcMatcher.score(.1,2.5,.1,100);
        check(Double.isFinite(beneath),"villager below overhead label");
        check(Double.isInfinite(UpgradeNpcMatcher.score(3,2.5,0,4)),"nearby upgrades villager cannot bind prestige label");
        check(Double.isInfinite(UpgradeNpcMatcher.score(0,-1,0,4)),"ignore label below candidate");
        check(Double.isInfinite(UpgradeNpcMatcher.score(0,6,0,4)),"ignore distant vertical label");
        check(Double.isInfinite(UpgradeNpcMatcher.score(0,2,0,5000)),"ignore unloaded/distant shop");
        check(UpgradeNpcMatcher.score(0,2.5,0,100)<UpgradeNpcMatcher.score(1,2.5,0,1),"alignment wins over player proximity");
        check(Double.isFinite(UpgradeNpcMatcher.score(0,0,0,4)),"directly named villager");
        System.out.println("UpgradeNpcMatcherTest PASS checks="+checks);
    }
}
