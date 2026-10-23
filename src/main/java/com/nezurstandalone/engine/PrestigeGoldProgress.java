package com.nezurstandalone.engine;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server menu is authoritative; positive wallet changes estimate progress until the next visit. */
public final class PrestigeGoldProgress {
    private static final Pattern PROGRESS=Pattern.compile("(?:grinded|earned)\\s*:?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*/\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*g\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern REWARD=Pattern.compile("\\+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*g\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern PICKUP=Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*g\\b",Pattern.CASE_INSENSITIVE);
    private double earned=Double.NaN,required=Double.NaN,balance=Double.NaN;
    private double balanceGains,chatGains;
    public boolean read(List<String> lore,double currentBalance) {
        for(String line:lore) {
            Matcher match=PROGRESS.matcher(line);
            if(!match.find())continue;
            try {
                double nextEarned=Double.parseDouble(match.group(1).replace(",",""));
                double nextRequired=Double.parseDouble(match.group(2).replace(",",""));
                if(!Double.isFinite(nextEarned)||!Double.isFinite(nextRequired)||nextRequired<=0)continue;
                earned=nextEarned;required=nextRequired;balance=validBalance(currentBalance)?currentBalance:Double.NaN;
                balanceGains=chatGains=0;
                return true;
            } catch(NumberFormatException ignored) { }
        }
        return false;
    }
    public void observe(double currentBalance) {
        if(!known()||!validBalance(currentBalance)){suspend();return;}
        if(validBalance(balance))balanceGains+=Math.max(0,currentBalance-balance);
        balance=currentBalance;
    }
    /** Count recognized server rewards too, so spending in the same tick cannot hide earnings. */
    public void reward(String message) {
        if(!known() || message==null)return;
        String text=message.trim().toUpperCase(java.util.Locale.ROOT);
        boolean pickup=text.startsWith("GOLD PICKUP!");
        if(!pickup && !text.startsWith("KILL!") && !text.startsWith("ASSIST!") && !text.startsWith("BOUNTY CLAIM!"))return;
        Matcher match=(pickup?PICKUP:REWARD).matcher(pickup?text.substring("GOLD PICKUP!".length()):text);
        while(match.find()) {
            try {double amount=Double.parseDouble(match.group(1).replace(",",""));if(Double.isFinite(amount))chatGains+=amount;}
            catch(NumberFormatException ignored) { }
        }
    }
    private static boolean validBalance(double value){return Double.isFinite(value)&&value>=0;}
    public boolean known(){return Double.isFinite(required);}
    public boolean ready(){return known() && earned()+0.000001>=required;}
    public double earned(){return earned+Math.max(balanceGains,chatGains);}
    public double required(){return required;}
    public double remaining(){return known()?Math.max(0,required-earned()):Double.NaN;}
    /** Never count reconnects, respawns, a paused module, or missing scoreboard data as earnings. */
    public void suspend(){balance=Double.NaN;}
    public static boolean denied(String message) {
        return message!=null && "You haven't earned enough gold this prestige!".equalsIgnoreCase(message.trim());
    }
    /** A server denial invalidates inferred gains; the next menu remains authoritative. */
    public void rejected(double currentBalance) {
        balanceGains=chatGains=0;
        if(known() && earned>=required)clear();
        balance=validBalance(currentBalance)?currentBalance:Double.NaN;
    }
    public void clear(){earned=required=balance=Double.NaN;balanceGains=chatGains=0;}
}
