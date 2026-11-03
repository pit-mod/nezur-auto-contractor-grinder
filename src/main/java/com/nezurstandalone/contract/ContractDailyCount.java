package com.nezurstandalone.contract;
import java.util.*;
import java.util.regex.*;
/** Server-menu count with explicitly estimated post-completion updates; no persisted account data. */
public final class ContractDailyCount {
    private Object account;private long day=Long.MIN_VALUE;private int remaining=-1;private boolean estimated;
    private static final Pattern COUNT=Pattern.compile("(?i)^daily contracts(?: remaining)?\\s*:\\s*([0-9]{1,3})\\s*$");
    public void context(Object account,long now){long date=Math.floorDiv(now,86400000L);if(!Objects.equals(this.account,account)||day!=date){this.account=account;day=date;remaining=-1;estimated=false;}}
    public void observe(List<String> lore){for(String raw:lore){
        String line=raw.replaceAll("(?i)\u00a7.","").trim();Matcher match=COUNT.matcher(line);
        if(match.matches()){int count=Integer.parseInt(match.group(1));if(count<=100){remaining=count;estimated=false;return;}}
        if(ContractOffer.dailyLimit(line)){remaining=0;estimated=false;return;}
    }}
    public void completed(){if(remaining>0){remaining--;estimated=true;}}
    public int remaining(){return remaining;}public boolean estimated(){return estimated;}
    public void exhausted(){remaining=0;estimated=false;}
}
