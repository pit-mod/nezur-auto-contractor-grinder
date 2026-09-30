package com.nezurstandalone.contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Vile is filtered before objective priority. Unknown offers may only be accepted for an idle refresh, never executed. */
public final class ContractSelector {
    public static final List<ContractOffer.Type> DEFAULT_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            ContractOffer.Type.GOLDEN_APPLES, ContractOffer.Type.KILL_PLAYERS, ContractOffer.Type.FIST_MID_KILLS,
            ContractOffer.Type.SNEAK_ATTACK_KILLS, ContractOffer.Type.NO_ARMOR_KILLS,
            ContractOffer.Type.NO_PERK_KILLS,
            ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW, ContractOffer.Type.CHAIN_KILLS,
            ContractOffer.Type.CLAIM_BOUNTIES, ContractOffer.Type.COLLECT_GOLD_INGOTS));

    public enum Mode { PREFERRED, FALLBACK, NONE }
    public static final class Choice {
        public final Mode mode;
        public final ContractOffer offer;
        Choice(Mode mode, ContractOffer offer) { this.mode=mode; this.offer=offer; }
    }

    public static Choice choose(List<ContractOffer> offers, List<ContractOffer.Type> priority,
                                List<ContractOffer.Type> executable, boolean allowFallback, Random random) {
        // The server streams this menu over several ticks. Never choose from a partial frame.
        if (offers.size() != 3 || !hasThreeDistinctChoices(offers)) return new Choice(Mode.NONE,null);
        ContractOffer best=null;
        int bestRank=Integer.MAX_VALUE;
        for(ContractOffer offer:offers) {
            if(offer==null || offer.vileAmount<=0 || !offer.clickable || offer.type==ContractOffer.Type.UNKNOWN) continue;
            int rank=priority.indexOf(offer.type);
            if(rank>=0 && executable.contains(offer.type)) {
                if(rank<bestRank) {best=offer;bestRank=rank;}
            }
        }
        if(best!=null) return new Choice(Mode.PREFERRED,best);
        if(allowFallback) {
            List<ContractOffer> fallback=new ArrayList<ContractOffer>();
            for(ContractOffer offer:offers)
                if(offer!=null && offer.clickable && offer.vileAmount>0)fallback.add(offer);
            if(!fallback.isEmpty())return new Choice(Mode.FALLBACK,fallback.get(random.nextInt(fallback.size())));
        }
        return new Choice(Mode.NONE,null);
    }

    private static boolean hasThreeDistinctChoices(List<ContractOffer> offers) {
        boolean[] seen=new boolean[4];
        for(ContractOffer offer:offers) {
            if(offer==null || offer.name==null) return false;
            String name=offer.name.trim();
            if(!name.matches("(?i)Choice\\s*#\\s*[123]")) return false;
            int choice=Integer.parseInt(name.substring(name.length()-1));
            if(seen[choice]) return false;
            seen[choice]=true;
        }
        return seen[1] && seen[2] && seen[3];
    }

}
