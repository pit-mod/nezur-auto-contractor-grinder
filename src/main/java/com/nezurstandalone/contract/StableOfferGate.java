package com.nezurstandalone.contract;

import java.util.List;

/** Requires three complete, unchanged offers for a continuous dwell before allowing a click. */
public final class StableOfferGate {
    private String signature="";
    private long since=-1;

    public boolean ready(List<ContractOffer> offers,long nowMillis,long dwellMillis) {
        if(offers==null || offers.size()!=3) {reset();return false;}
        StringBuilder next=new StringBuilder();
        for(int i=1;i<=3;i++) {
            ContractOffer found=null;
            for(ContractOffer offer:offers) if(offer!=null && ("Choice #"+i).equalsIgnoreCase(offer.name.trim())) found=offer;
            if(found==null || !found.clickable || found.itemId!=403) {reset();return false;}
            next.append(found.slot).append('|').append(found.itemId).append('|').append(found.metadata)
                .append('|').append(found.task).append('|').append(found.goldReward).append('|')
                .append(found.vileAmount).append('|').append(found.lore).append(';');
        }
        String value=next.toString();
        if(!value.equals(signature)) {signature=value;since=nowMillis;return false;}
        return since>=0 && nowMillis-since>=Math.max(0,dwellMillis);
    }

    public void reset(){signature="";since=-1;}
}
