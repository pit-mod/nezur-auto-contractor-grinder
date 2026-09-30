package com.nezurstandalone.contract;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative sidebar reader: an unreadable board is not evidence of expiry. */
public final class ContractScoreboard {
    private static final Pattern TIMER=Pattern.compile("(?i)contract\\s*:\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern PROGRESS=Pattern.compile("(?i)^(.+?)\\s*(\\d+)\\s*/\\s*(\\d+)\\s*$");
    public static final class Snapshot {
        public final boolean validPit, readable, active;
        public final ContractOffer.Type type;
        public final int current, target, remainingSeconds;
        public final String objective;
        public Snapshot(boolean validPit, boolean readable, boolean active, ContractOffer.Type type,
                 int current, int target, int remainingSeconds, String objective) {
            this.validPit=validPit;this.readable=readable;this.active=active;this.type=type;
            this.current=current;this.target=target;this.remainingSeconds=remainingSeconds;this.objective=objective;
        }
    }
    public static Snapshot parse(String title,List<String> lines) {
        boolean valid=isPitTitle(title);
        if(!valid || lines==null || lines.isEmpty()) return new Snapshot(valid,false,false,ContractOffer.Type.UNKNOWN,0,0,-1,"");
        int seconds=-1, current=0, target=0;
        String objective="";
        boolean found=false;
        for(int i=0;i<lines.size();i++) {
            String line=clean(lines.get(i));
            Matcher tm=TIMER.matcher(line);
            if(tm.find()) {
                found=true;seconds=Integer.parseInt(tm.group(1))*60+Integer.parseInt(tm.group(2));
                break;
            }
        }
        // Team/score ordering can differ from render order. Progress is identified by
        // its known objective label, independently of its position beside the timer.
        for(String raw:lines) {
            Matcher pm=PROGRESS.matcher(clean(raw));
            if(pm.matches() && classify(pm.group(1))!=ContractOffer.Type.UNKNOWN) {
                objective=pm.group(1).replaceFirst(":$", "").trim();
                current=Integer.parseInt(pm.group(2));target=Integer.parseInt(pm.group(3));
                found=true;break;
            }
        }
        return new Snapshot(valid,true,found,classify(objective),current,target,seconds,objective);
    }
    /** Active sidebar fields only; scheduled event overlays are not active events. */
    public static String majorEvent(List<String> lines) {
        if(lines==null)return "";
        boolean eventStatus=false;
        for(String raw:lines) {
            String line=clean(raw);
            if(line.equalsIgnoreCase("Status: Event"))eventStatus=true;
            if(line.toLowerCase(Locale.ROOT).startsWith("event:")) {
                String name=line.substring(line.indexOf(':')+1).trim();
                String key=name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
                if(key.matches("BLOCKHEAD|RAGEPIT|ROBBERY|SPIRE|SQUADS|TEAMDEATHMATCH|BEAST|PIZZA|RAFFLE"))return name;
            }
        }
        return eventStatus?"Major event":"";
    }
    public static boolean normalSidebar(List<String> lines) {
        if(lines==null || !majorEvent(lines).isEmpty())return false;
        boolean level=false,status=false;
        for(String raw:lines) {
            String line=clean(raw).toLowerCase(Locale.ROOT);
            level|=line.startsWith("level:");status|=line.startsWith("status:");
        }
        return level && status;
    }

    public static Snapshot activeMenu(List<String> lore) {
        Snapshot progress=parse("THE HYPIXEL PIT",lore);
        StringBuilder text=new StringBuilder();
        for(String raw:lore){
            String line=clean(raw);
            if(PROGRESS.matcher(line).matches())break;
            if(line.isEmpty() || line.equalsIgnoreCase("Task:"))continue;
            if(line.toLowerCase(Locale.ROOT).matches("^(reward|bonus|time limit):.*"))break;
            if(text.length()>0)text.append(' ');
            text.append(line);
        }
        String task=text.toString();
        ContractOffer.Type taskType=classify(task);
        // Task wording disambiguates a generic Kills progress line in the same item.
        return new Snapshot(true,true,true,taskType!=ContractOffer.Type.UNKNOWN?taskType:progress.type,
                progress.current,progress.target,-1,task);
    }
    private static boolean isPitTitle(String title) {
        if(title==null)return false;
        String normalized=clean(title);
        return "THE HYPIXEL PIT".equalsIgnoreCase(normalized);
    }
    private static String clean(String s) {return s==null?"":s.replaceAll("(?i)§[0-9a-fk-or]","")
            .replaceAll("[\\p{Cf}\\p{Cc}\\p{So}]", "").replace('\u00a0',' ').trim();}
    private static ContractOffer.Type classify(String objective) {
        String s=objective.toLowerCase(Locale.ROOT);
        // Ranged/composite objectives must be identified before generic kill labels.
        if(s.contains("shoot") || s.contains("shot") || s.contains("bow") || s.contains("arrow")) {
            if(s.contains("kill") || s.contains("tag")) return ContractOffer.Type.BOW_TAG_KILLS;
            return ContractOffer.Type.BOW_SHOTS;
        }
        if(s.contains("fish")) return ContractOffer.Type.FISH_DIAMOND_SWORD;
        if(s.contains("fist"))return ContractOffer.Type.FIST_MID_KILLS;
        if(s.contains("sneak"))return ContractOffer.Type.SNEAK_ATTACK_KILLS;
        if(s.contains("no perk") || s.contains("without perk") || s.contains("zero perk"))return ContractOffer.Type.NO_PERK_KILLS;
        if(s.contains("armor"))return ContractOffer.Type.NO_ARMOR_KILLS;
        if(s.contains("diamond sword"))return ContractOffer.Type.DIAMOND_SWORD_FINAL_BLOW;
        if(s.contains("chain"))return ContractOffer.Type.CHAIN_KILLS;
        if(s.contains("bount"))return ContractOffer.Type.CLAIM_BOUNTIES;
        if(s.contains("apple"))return ContractOffer.Type.GOLDEN_APPLES;
        if(s.contains("gold") || s.contains("ingot"))return ContractOffer.Type.COLLECT_GOLD_INGOTS;
        if(s.contains("streak"))return ContractOffer.Type.KILL_STREAK;
        if(s.contains("grass"))return ContractOffer.Type.GRASS_KILLS;
        if(s.contains("lava"))return ContractOffer.Type.LAVA_KILLS;
        if(s.contains("obsidian"))return ContractOffer.Type.OBSIDIAN_KILLS;
        if(s.contains("bow")||s.contains("arrow"))return ContractOffer.Type.BOW_SHOTS;
        if(s.contains("apple"))return ContractOffer.Type.GOLDEN_APPLES;
        if(s.contains("fish"))return ContractOffer.Type.FISH_DIAMOND_SWORD;
        if(s.contains("kill"))return ContractOffer.Type.KILL_PLAYERS;
        return ContractOffer.Type.UNKNOWN;
    }
}
