package com.nezurstandalone.engine;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** Main-thread, bounded loadout evidence and incremental planning. No Minecraft dependency. */
public final class LoadoutPlanner {
    public enum Kind { PERK, KILLSTREAK }
    public enum State { DISABLED, SATISFIED, NEEDS_EQUIP, NEEDS_PURCHASE, NEEDS_REVALIDATION, WAITING_FOR_GOLD,
        LEVEL_LOCKED, RENOWN_LOCKED, UNKNOWN_PRICE, UNKNOWN_OWNERSHIP, UNKNOWN_EQUIPMENT,
        UNAVAILABLE, IN_PROGRESS, VERIFYING, TEMPORARY_FAILURE }
    public enum Ownership { UNKNOWN, OWNED, UNOWNED }
    public static final String NONE = "No Perk";
    private static final long GOLD_AGE_MS = 30000, RETRY_MS = 60000;
    private static final String NUMBER = "(?:[0-9]+|[0-9]{1,3}(?:[,\\u00a0\\u202f ][0-9]{3})+)(?:\\.[0-9]+)?";
    private static final Pattern MONEY = Pattern.compile("^("+NUMBER+")\\s*([kmb]?)\\s*g?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOLD = Pattern.compile("^Gold\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COST = Pattern.compile("^(?:gold\\s+)?cost\\b\\s*:?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUIRED = Pattern.compile("(?:required level|requires? level|too low level)\\s*:?\\s*\\[?(\\d+)", Pattern.CASE_INSENSITIVE);

    public static final class Entry {
        public final Kind kind;
        public final String name;
        public final double definitionPrice;
        public final int minLevel, renown;
        public Ownership ownership = Ownership.UNKNOWN;
        public double observedPrice = Double.NaN;
        public boolean freeSelect, purchasable;
        // An uncertain paid operation must be resolved from server evidence, never bootstrapped again.
        private boolean uncertainPurchase;
        public State restriction;
        public String restrictionReason;
        public int observedLevel;
        private int lockObservedLevel;
        private Entry(Kind kind, String name, double price, int minLevel, int renown) {
            this.kind=kind;this.name=name;this.definitionPrice=price;this.minLevel=minLevel;this.renown=renown;
        }
        public double price() { return Double.isFinite(observedPrice)?observedPrice:definitionPrice; }
    }
    public static final class Slot {
        public final Kind kind;
        public final int number, unlockLevel;
        public String desired=NONE, equipped;
        public boolean enabled, equipmentKnown, present, unlocked, verified;
        public State state=State.DISABLED;
        public String reason="automation disabled";
        private int liveRequired;
        private int lockObservedLevel;
        private long retryAt;
        private int failures;
        private String logged="";
        private Slot(Kind kind,int number,int level){this.kind=kind;this.number=number;this.unlockLevel=level;}
    }
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final List<Slot> slots=new ArrayList<>();
    private final EnumSet<Kind> canClear=EnumSet.noneOf(Kind.class);
    private final Consumer<String> logger;
    private String context="";
    private int level;
    private boolean theWay, suspended;
    private double gold=Double.NaN, spendCeiling=Double.NaN;
    private boolean awaitingDebit;
    private boolean rejectedGold;
    private double rejectedBalance=Double.NaN;
    private long goldAt=-1, revision, evidenceRevision, pendingRevision;
    private Slot pending;
    private String pendingDesired;
    private double pendingCost;
    private boolean confirmationOpen, confirmationSeen, pendingMayHaveSpent;
    private boolean choiceOwnedAfterDispatch, choiceSelectedAfterDispatch;
    private boolean dirty=true;
    private long nextExpiry=Long.MAX_VALUE;

    public LoadoutPlanner(Consumer<String> logger) {
        this.logger=logger;
        int[] levels={10,35,70,100};
        for(int i=0;i<4;i++)slots.add(new Slot(Kind.PERK,i+1,levels[i]));
        slots.add(new Slot(Kind.KILLSTREAK,1,10));slots.add(new Slot(Kind.KILLSTREAK,2,75));
    }
    private static String key(Kind k,String name){return k+":"+name.toLowerCase(Locale.ROOT);}
    public void define(Kind k,String name,double price,int level,int renown){entries.put(key(k,name),new Entry(k,name,price,level,renown));}
    public Entry entry(Kind k,String name){return name==null?null:entries.get(key(k,name));}
    public Slot slot(Kind k,int number){for(Slot s:slots)if(s.kind==k&&s.number==number)return s;throw new IllegalArgumentException("slot");}
    public List<Slot> slots(){return Collections.unmodifiableList(slots);}
    public void context(String identity) {
        if(identity.equals(context))return;
        context=identity;resetEvidence();
    }
    /** Ownership is never assumed to survive a prestige or an unidentified profile reset. */
    public void resetEvidence() {
        for(Entry e:entries.values()){e.ownership=Ownership.UNKNOWN;e.observedPrice=Double.NaN;e.freeSelect=e.purchasable=false;e.uncertainPurchase=false;e.restriction=null;e.restrictionReason=null;e.observedLevel=0;}
        for(Slot s:slots){s.equipmentKnown=s.present=s.unlocked=s.verified=false;s.equipped=null;s.liveRequired=0;s.retryAt=0;s.failures=0;s.logged="";}
        canClear.clear();pending=null;pendingDesired=null;pendingCost=0;confirmationOpen=confirmationSeen=pendingMayHaveSpent=false;awaitingDebit=false;spendCeiling=Double.NaN;
        choiceOwnedAfterDispatch=choiceSelectedAfterDispatch=false;
        rejectedGold=false;rejectedBalance=Double.NaN;invalidateGold();changed();
    }
    public void configure(Kind k,int number,String desired,boolean enabled) {
        Slot s=slot(k,number);desired=normalize(desired);
        if(s.enabled==enabled&&s.desired.equalsIgnoreCase(desired))return;
        if(pending==s)interrupt(0,"configuration changed during operation");
        s.desired=desired;s.enabled=enabled;s.verified=false;s.retryAt=0;s.failures=0;changed();
    }
    public void requirements(int level,boolean way) {
        if(this.level==level&&this.theWay==way)return;
        if(this.level>0&&level>0&&level<this.level)resetEvidence();
        this.level=level;theWay=way;changed();
    }
    public void suspend(boolean value){if(suspended!=value){suspended=value;changed();}}
    private void changed(){dirty=true;revision++;}
    public long revision(){return revision;}
    public void invalidateGold(){if(Double.isNaN(gold)&&goldAt<0)return;gold=Double.NaN;goldAt=-1;changed();}
    private void rejectGold(){rejectedGold=true;rejectedBalance=gold;invalidateGold();}
    /** A fresh packet or a read of the active connection's current sidebar can refresh evidence. */
    public void gold(double amount,long now) {
        if(!Double.isFinite(amount)||amount<0){invalidateGold();return;}
        // Re-reading the unchanged sidebar cannot contradict a live menu's insufficient-gold reply.
        if(rejectedGold&&Double.isFinite(rejectedBalance)&&Double.compare(amount,rejectedBalance)==0){invalidateGold();return;}
        rejectedGold=false;rejectedBalance=Double.NaN;
        // A changed server wallet reconciles our conservative debit; unrelated packets cannot.
        if(Double.compare(gold,amount)!=0){
            if(!awaitingDebit || amount<=spendCeiling){spendCeiling=Double.NaN;awaitingDebit=false;}
            gold=amount;changed();
        }
        boolean wasStale=goldAt<0||now-goldAt>GOLD_AGE_MS;
        goldAt=now;if(wasStale)changed();
        nextExpiry=Math.min(nextExpiry,now+GOLD_AGE_MS+1);
    }
    public double balance(long now){return !awaitingDebit&&goldAt>=0&&now>=goldAt&&now-goldAt<=GOLD_AGE_MS?
        (Double.isFinite(spendCeiling)?Math.min(gold,spendCeiling):gold):Double.NaN;}
    private String goldBlockReason() {
        if(rejectedGold)return "server rejected the last gold balance; waiting for it to change";
        if(awaitingDebit)return "waiting for purchase balance update; no further paid action";
        if(goldAt<0||!Double.isFinite(gold))return "Gold line missing or invalid in the active Pit sidebar; no NPC trip";
        return "no current connected sidebar observation; no NPC trip";
    }
    public static double money(String text) {
        if(text==null)return Double.NaN;
        Matcher m=MONEY.matcher(clean(text).trim());if(!m.matches())return Double.NaN;
        try {double n=Double.parseDouble(m.group(1).replaceAll("[,\\u00a0\\u202f ]", ""));
            String suffix=m.group(2).toLowerCase(Locale.ROOT);double scale=suffix.equals("k")?1000:suffix.equals("m")?1000000:suffix.equals("b")?1000000000:1;
            double value=n*scale;return Double.isFinite(value)&&value>=0?value:Double.NaN;
        }catch(NumberFormatException bad){return Double.NaN;}
    }
    public static double scoreboardGold(List<String> lines) {
        double result=Double.NaN;boolean seen=false;
        for(String line:lines){Matcher m=GOLD.matcher(clean(line).trim());if(!m.matches())continue;
            if(seen)return Double.NaN;seen=true;
            // Some servers use an emoji as the unique scoreboard entry after the team prefix.
            // It renders as decoration (or nothing) but is still part of the canonical line.
            // Strip only terminal Unicode symbols, never digits, signs, words or punctuation.
            result=money(m.group(1).replaceAll("[\\p{So}\\p{Sk}\\uFE0E\\uFE0F\\u200D\\s]+$", ""));}
        return result;
    }
    public static boolean goldLine(String line){return line!=null&&GOLD.matcher(clean(line).trim()).matches();}
    public static String clean(String text){return text==null?"":text.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "")
            .replace('\u00a0',' ').replace('\u202f',' ').replace("\u200b", "").replace("\ufeff", "");}
    public static String normalize(String name){return name==null||name.trim().isEmpty()||name.equalsIgnoreCase("None")||name.equalsIgnoreCase("Empty")||name.equalsIgnoreCase("No killstreak")||name.equalsIgnoreCase(NONE)?NONE:name.trim();}
    private static boolean has(List<String> lore,String needle){for(String line:lore)if(clean(line).toLowerCase(Locale.ROOT).contains(needle))return true;return false;}
    public static int requiredLevel(List<String> lore){for(String line:lore){Matcher m=REQUIRED.matcher(clean(line));if(m.find())try{return Integer.parseInt(m.group(1));}catch(NumberFormatException bad){return 0;}}return 0;}
    public static boolean resolved(List<String> lore){return has(lore,"selected:")||has(lore,"click to choose perk")||has(lore,"click to choose killstreak")||has(lore,"select a perk")||has(lore,"select a killstreak")||has(lore,"required level");}
    public static String selected(List<String> lore){for(String line:lore){String t=clean(line).trim();if(t.toLowerCase(Locale.ROOT).startsWith("selected:"))return normalize(t.substring(9));}return NONE;}

    /** Authoritative packet contents, including manual menus; unresolved lore is never empty evidence. */
    public void observeSlot(Kind k,int number,List<String> lore,long now) {
        Slot s=slot(k,number);if(!resolved(lore))return;
        evidenceRevision++;s.present=true;s.equipmentKnown=true;s.equipped=selected(lore);
        s.liveRequired=requiredLevel(lore);s.unlocked=!(s.equipped.equals(NONE)&&has(lore,"required level"));s.lockObservedLevel=level;
        Entry e=entry(k,s.equipped);if(e!=null){e.ownership=Ownership.OWNED;e.uncertainPurchase=false;/* Free reselection still requires chooser evidence. */}
        if(pending==s&&s.unlocked&&s.equipped.equalsIgnoreCase(pendingDesired)&&evidenceRevision>pendingRevision) {
            s.verified=true;s.failures=0;s.retryAt=0;
            if(e!=null){e.ownership=Ownership.OWNED;e.restriction=null;}
            logger.accept(tag(s)+pendingDesired+" successfully equipped and verified.");
            pending=null;pendingDesired=null;confirmationOpen=confirmationSeen=pendingMayHaveSpent=false;
        }
        s.verified=s.enabled&&s.equipped.equalsIgnoreCase(s.desired);changed();
    }
    public void missingSlot(Kind k,int number){Slot s=slot(k,number);s.equipmentKnown=true;s.present=false;s.unlocked=false;s.verified=false;changed();}
    public void observeChoice(Kind k,String name,List<String> lore) {
        inspectChoice(k,name,lore);
        if(pending!=null&&pending.kind==k&&pendingDesired.equalsIgnoreCase(normalize(name))
                &&pending.state==State.VERIFYING&&pendingMayHaveSpent) {
            Entry e=entry(k,name);
            choiceSelectedAfterDispatch=has(lore,"already selected");
            choiceOwnedAfterDispatch=normalize(name).equals(NONE)?choiceSelectedAfterDispatch:
                    e!=null&&e.ownership==Ownership.OWNED&&e.restriction==null&&(e.freeSelect||choiceSelectedAfterDispatch);
        }
    }
    /** Local lore recheck never constitutes a post-dispatch server response. */
    public void inspectChoice(Kind k,String name,List<String> lore) {
        if(normalize(name).equals(NONE)) {
            if(has(lore,"click to select")||has(lore,"click to remove")||has(lore,"click to disable"))canClear.add(k);
            else canClear.remove(k);
            changed();return;
        }
        Entry e=entry(k,name);if(e==null)return;
        e.restriction=null;e.restrictionReason=null;e.purchasable=e.freeSelect=false;
        boolean purchase=has(lore,"click to purchase"),select=has(lore,"click to select");
        boolean costLine=false;e.observedLevel=requiredLevel(lore);e.lockObservedLevel=level;
        for(String line:lore){Matcher m=COST.matcher(clean(line).trim());if(m.matches()){costLine=true;e.observedPrice=money(m.group(1));break;}}
        if(costLine&&!Double.isFinite(e.observedPrice)) {e.restriction=State.UNKNOWN_PRICE;e.restrictionReason="invalid server price";}
        else if(has(lore,"unlocked in renown shop")||has(lore,"not unlocked")||has(lore,"requires renown")) {e.restriction=State.RENOWN_LOCKED;e.restrictionReason="requires Renown shop unlock; open a chooser manually after unlocking";}
        else if(has(lore,"too low level")) {e.restriction=State.LEVEL_LOCKED;e.restrictionReason="server level requirement";}
        else if(has(lore,"already selected")) {e.ownership=Ownership.OWNED;e.uncertainPurchase=false;/* resolve duplicate from actual equipment, not a permanent failure */}
        else if(select&&!purchase) {e.ownership=Ownership.OWNED;e.freeSelect=true;e.uncertainPurchase=false;}
        else if(purchase||has(lore,"not enough gold")) {e.ownership=Ownership.UNOWNED;e.purchasable=true;e.uncertainPurchase=false;}
        else {e.restriction=State.UNAVAILABLE;e.restrictionReason="purchase/select availability not confirmed; refresh manually";}
        if(has(lore,"not enough gold")&&pending!=null&&pending.kind==k&&pendingDesired.equalsIgnoreCase(name)) {
            rejectGold();fail(pending,0,"server reported insufficient gold; waiting for a changed wallet balance",false);
        }
        changed();
    }
    public void choiceMissing(Kind k,String name){
        if(normalize(name).equals(NONE)){canClear.remove(k);changed();return;}
        Entry e=entry(k,name);if(e!=null){e.restriction=State.UNAVAILABLE;e.restrictionReason="not in the server chooser; refresh manually";changed();}
    }
    private String tag(Slot s){return s.kind==Kind.PERK?"[AutoPerk] ":"[AutoKillstreak] ";}
    private void set(Slot s,State state,String reason) {
        s.state=state;s.reason=reason;
        // Balance progress is shown on entering WAITING, not for every cent earned.
        String message=state+"|"+s.desired+"|"+(state==State.WAITING_FOR_GOLD&&reason.startsWith("waiting for gold:")?"gold threshold":reason);
        if(!message.equals(s.logged)){s.logged=message;if(s.enabled)logger.accept(tag(s)+s.desired+": "+reason);}
    }
    /**
     * A cold cache must not deadlock autonomous startup. A known non-Renown choice can be
     * revalidated at the NPC when the full possible purchase price fits a fresh wallet.
     * This authorizes only opening its chooser; ownership and clicks still need live lore.
     */
    private void unresolved(Slot s,Entry e,double wallet) {
        if(e==null||!Double.isFinite(e.price())||e.price()<0) {
            set(s,State.UNKNOWN_PRICE,"price unknown; no automatic NPC trip");return;
        }
        if(e.uncertainPurchase) {set(s,State.UNKNOWN_OWNERSHIP,"previous purchase result unknown; refresh server evidence before retrying");return;}
        if(e.restriction!=null && !(e.restriction==State.LEVEL_LOCKED&&level>e.lockObservedLevel&&level>=Math.max(e.minLevel,e.observedLevel))) {
            set(s,e.restriction,e.restrictionReason);return;
        }
        if(e.renown>0 && e.ownership==Ownership.UNKNOWN && !e.purchasable) {
            set(s,State.UNKNOWN_OWNERSHIP,"Renown unlock unknown; open its chooser manually");return;
        }
        int required=Math.max(e.minLevel,e.observedLevel);
        if((!theWay||s.kind==Kind.KILLSTREAK)&&level<required) {set(s,State.LEVEL_LOCKED,"requires level "+required);return;}
        if(e.ownership==Ownership.OWNED&&e.freeSelect) {
            set(s,State.NEEDS_REVALIDATION,"confirmed free selection; verify equipment at next spawn");return;
        }
        if(!Double.isFinite(wallet)) {set(s,State.WAITING_FOR_GOLD,goldBlockReason());return;}
        if(wallet<e.price()) {
            set(s,State.WAITING_FOR_GOLD,String.format(Locale.ROOT,"waiting for gold: %.2f / %.2fg to revalidate unknown selection",wallet,e.price()));return;
        }
        set(s,State.NEEDS_REVALIDATION,"full known price affordable ("+e.price()+"g); verify equipment and ownership at next spawn");
    }
    public void evaluate(long now) {
        if(!dirty&&now<nextExpiry)return;dirty=false;nextExpiry=goldAt<0?Long.MAX_VALUE:goldAt+GOLD_AGE_MS+1;
        if(nextExpiry<=now)nextExpiry=Long.MAX_VALUE;
        double wallet=balance(now);
        for(Slot s:slots) {
            if(pending==s){set(s,s.state==State.VERIFYING?State.VERIFYING:State.IN_PROGRESS,"awaiting server verification");continue;}
            if(!s.enabled||suspended){set(s,State.DISABLED,suspended?"AutoContractor owns temporary loadout":"automation disabled");continue;}
            if(s.retryAt>now){nextExpiry=Math.min(nextExpiry,s.retryAt);set(s,State.TEMPORARY_FAILURE,"interaction retry deferred");continue;}
            if(s.failures>=3){set(s,State.TEMPORARY_FAILURE,"repeated ambiguous failures; refresh this slot manually");continue;}
            if(s.equipmentKnown&&s.present&&s.unlocked&&s.desired.equalsIgnoreCase(s.equipped)){set(s,State.SATISFIED,"already equipped; no NPC trip");continue;}
            int slotLevel=s.liveRequired>0?s.liveRequired:s.unlockLevel;
            if(level<=0||(!theWay||s.kind==Kind.KILLSTREAK)&&level<slotLevel){set(s,State.LEVEL_LOCKED,"requires slot level "+slotLevel);continue;}
            Entry e=entry(s.kind,s.desired);
            if(!s.equipmentKnown){
                if(s.desired.equals(NONE))set(s,State.UNKNOWN_EQUIPMENT,"equipment unknown; no trip solely to inspect an empty desired slot");
                else unresolved(s,e,wallet);
                continue;
            }
            if(!s.present){set(s,State.UNAVAILABLE,"slot absent on this profile; refresh manually");continue;}
            if(!s.unlocked&&!(s.liveRequired>0&&level>=s.liveRequired&&level>s.lockObservedLevel)){set(s,State.LEVEL_LOCKED,"server slot locked; refresh manually after unlock");continue;}
            if(s.desired.equals(NONE)){if(canClear.contains(s.kind))set(s,State.NEEDS_EQUIP,"confirmed free slot removal; queued for next spawn");else set(s,State.UNKNOWN_OWNERSHIP,"removal support unknown; open a chooser manually");continue;}
            if(e==null){set(s,State.UNKNOWN_PRICE,"unknown definition; refusing automatic NPC discovery");continue;}
            int required=Math.max(e.minLevel,e.observedLevel);
            if((!theWay||s.kind==Kind.KILLSTREAK)&&level<required){set(s,State.LEVEL_LOCKED,"requires level "+required);continue;}
            if(e.restriction!=null&&!(e.restriction==State.LEVEL_LOCKED&&level>e.lockObservedLevel&&level>=required)) {set(s,e.restriction,e.restrictionReason);continue;}
            boolean duplicate=false;
            for(Slot other:slots)if(other!=s&&other.kind==s.kind&&other.present&&other.equipmentKnown&&s.desired.equalsIgnoreCase(other.equipped)){duplicate=true;break;}
            if(duplicate){set(s,State.UNAVAILABLE,"currently selected in another slot; change or clear that slot first");continue;}
            if(e.ownership==Ownership.OWNED&&e.freeSelect){set(s,State.NEEDS_EQUIP,"already owned; equip cost: 0g; queued for next spawn");continue;}
            if(e.ownership==Ownership.UNKNOWN||e.ownership==Ownership.OWNED&&!e.freeSelect){unresolved(s,e,wallet);continue;}
            if(!Double.isFinite(e.price())||e.price()<0){set(s,State.UNKNOWN_PRICE,"price unknown; refusing automatic NPC discovery");continue;}
            if(!e.purchasable){set(s,State.UNAVAILABLE,"purchase not confirmed available");continue;}
            if(!Double.isFinite(wallet)){set(s,State.WAITING_FOR_GOLD,goldBlockReason());continue;}
            if(wallet<e.price()){set(s,State.WAITING_FOR_GOLD,String.format(Locale.ROOT,"waiting for gold: %.2f / %.2fg",wallet,e.price()));continue;}
            set(s,State.NEEDS_PURCHASE,"now affordable; queued for next spawn ("+e.price()+"g)");
        }
    }
    public Slot next(Kind kind,long now) {
        evaluate(now);if(pending!=null)return null;
        // Stable order: free changes, then perks 1..4 and streaks 1..2. No total-loadout reservation.
        for(State state:new State[]{State.NEEDS_EQUIP,State.NEEDS_PURCHASE,State.NEEDS_REVALIDATION})for(Slot s:slots)if((kind==null||s.kind==kind)&&s.state==state)return s;
        return null;
    }
    public Slot pending(){return pending;}
    public boolean begin(Slot s,long now) {
        evaluate(now);if(pending!=null||s.state!=State.NEEDS_EQUIP&&s.state!=State.NEEDS_PURCHASE&&s.state!=State.NEEDS_REVALIDATION)return false;
        pending=s;pendingDesired=s.desired;pendingCost=0;confirmationOpen=confirmationSeen=pendingMayHaveSpent=false;choiceOwnedAfterDispatch=choiceSelectedAfterDispatch=false;pendingRevision=evidenceRevision;s.state=State.IN_PROGRESS;changed();return true;
    }
    /** Revalidate immediately before the actual purchase/select click, using freshly observed chooser lore. */
    public boolean canApply(long now) {
        if(pending==null||pending.state!=State.IN_PROGRESS||!pending.enabled||suspended||!pending.desired.equalsIgnoreCase(pendingDesired))return false;
        if(!pendingSlotEligible())return false;
        if(pendingDesired.equals(NONE))return canClear.contains(pending.kind);
        Entry e=entry(pending.kind,pendingDesired);if(e==null||e.restriction!=null)return false;
        if((!theWay||pending.kind==Kind.KILLSTREAK)&&level<Math.max(e.minLevel,e.observedLevel))return false;
        return e.ownership==Ownership.OWNED&&e.freeSelect||e.ownership==Ownership.UNOWNED&&e.purchasable&&Double.isFinite(e.price())&&Double.isFinite(balance(now))&&balance(now)>=e.price();
    }
    public double pendingPrice(){Entry e=pending==null?null:entry(pending.kind,pendingDesired);return e!=null&&e.ownership==Ownership.UNOWNED?e.price():0;}
    private boolean pendingSlotEligible() {
        if(pending==null||!pending.equipmentKnown||!pending.present||!pending.unlocked)return false;
        int required=pending.liveRequired>0?pending.liveRequired:pending.unlockLevel;
        if(level<=0||(!theWay||pending.kind==Kind.KILLSTREAK)&&level<required)return false;
        if(!pendingDesired.equals(NONE))for(Slot s:slots) {
            if(s!=pending&&s.kind==pending.kind&&s.present&&s.equipmentKnown&&pendingDesired.equalsIgnoreCase(s.equipped))return false;
        }
        return true;
    }
    public double committedPrice(){return pendingCost;}
    public boolean choiceSelectedAfterDispatch(){return pending!=null&&choiceOwnedAfterDispatch&&choiceSelectedAfterDispatch;}
    /** Purchase may unlock a choice without equipping it. Authorize exactly one free follow-up. */
    public boolean prepareOwnedSelection(long now) {
        if(pending==null||pending.state!=State.VERIFYING||pendingCost<=0||!pendingMayHaveSpent
                ||!choiceOwnedAfterDispatch||choiceSelectedAfterDispatch)return false;
        Entry e=entry(pending.kind,pendingDesired);
        if(e==null||e.ownership!=Ownership.OWNED||!e.freeSelect||e.restriction!=null)return false;
        pending.state=State.IN_PROGRESS;pendingCost=0;pendingMayHaveSpent=false;
        confirmationOpen=confirmationSeen=choiceOwnedAfterDispatch=choiceSelectedAfterDispatch=false;
        changed();return canApply(now);
    }
    public boolean confirmationPriceMatches(List<String> lore) {
        for(String line:lore) {
            Matcher m=COST.matcher(clean(line).trim());
            if(m.matches()){double cost=money(m.group(1));if(!Double.isFinite(cost)||cost!=pendingCost)return false;}
        }
        return true;
    }
    public boolean canConfirm(long now) {
        if(pending==null||pending.state!=State.VERIFYING||!confirmationOpen||!pending.enabled||suspended
                ||!pending.desired.equalsIgnoreCase(pendingDesired))return false;
        if(!pendingSlotEligible())return false;
        Entry e=entry(pending.kind,pendingDesired);
        if(e!=null&&(e.restriction!=null||(!theWay||pending.kind==Kind.KILLSTREAK)&&level<Math.max(e.minLevel,e.observedLevel)))return false;
        if(pendingPrice()!=pendingCost)return false;
        return pendingCost==0 || Double.isFinite(balance(now))&&balance(now)>=pendingCost;
    }
    /** A correlated server confirmation proves the chooser click has not paid yet. */
    public void confirmationOpened() {
        if(pending==null||pending.state!=State.VERIFYING||confirmationSeen||!pendingMayHaveSpent)return;
        confirmationOpen=confirmationSeen=true;pendingMayHaveSpent=false;
        if(pendingCost>0){awaitingDebit=false;spendCeiling=Double.NaN;}
        changed();
    }
    public void confirmed(long now) {
        if(!canConfirm(now))throw new IllegalStateException("confirmation is not eligible");
        confirmationOpen=false;pendingMayHaveSpent=true;pendingRevision=evidenceRevision;
        choiceOwnedAfterDispatch=choiceSelectedAfterDispatch=false;
        logger.accept(tag(pending)+"Confirming "+pendingDesired+" for "+pendingCost+"g.");
        reservePending(now);changed();
    }
    private void reservePending(long now) {
        if(pendingCost>0){spendCeiling=balance(now)-pendingCost;awaitingDebit=true;}
    }
    public void applied(long now) {
        if(pending==null)throw new IllegalStateException("no operation");
        if(!canApply(now))throw new IllegalStateException("live selection is not eligible");
        pendingCost=pendingPrice();pendingRevision=evidenceRevision;pending.state=State.VERIFYING;pendingMayHaveSpent=true;confirmationOpen=confirmationSeen=false;
        choiceOwnedAfterDispatch=choiceSelectedAfterDispatch=false;
        reservePending(now);
        if(pendingCost>0){logger.accept(tag(pending)+"Selecting "+pendingDesired+" purchase for "+pendingCost+"g; awaiting server response.");}
        else logger.accept(tag(pending)+"Equipping "+pendingDesired+" for 0g.");
        changed();
    }
    public void blocked(long now,String reason){if(pending!=null)fail(pending,now,reason,false);}
    private void fail(Slot s,long now,String reason,boolean ambiguous) {
        if(ambiguous){s.failures++;s.retryAt=now+RETRY_MS*Math.min(3,s.failures);if(pending==s&&pendingCost>0&&pendingMayHaveSpent){Entry e=entry(s.kind,pendingDesired);if(e!=null&&e.ownership!=Ownership.OWNED){e.ownership=Ownership.UNKNOWN;e.purchasable=e.freeSelect=false;e.uncertainPurchase=true;}}}
        logger.accept(tag(s)+s.desired+" blocked: "+reason);pending=null;pendingDesired=null;pendingCost=0;confirmationOpen=confirmationSeen=pendingMayHaveSpent=false;changed();
    }
    public void interrupt(long now,String reason){if(pending!=null)fail(pending,now,reason,true);}
    public void deferVisit(long now,String reason){evaluate(now);for(Slot s:slots)if(s.state==State.NEEDS_EQUIP||s.state==State.NEEDS_PURCHASE||s.state==State.NEEDS_REVALIDATION){s.failures++;s.retryAt=now+RETRY_MS*Math.min(3,s.failures);}interrupt(now,reason);changed();}
    public void retryVerifiedObservation(Kind kind,int number){Slot s=slot(kind,number);if(pending!=s){s.retryAt=0;s.failures=0;changed();}}
}
