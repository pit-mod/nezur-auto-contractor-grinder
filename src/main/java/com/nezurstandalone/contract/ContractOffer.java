package com.nezurstandalone.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable interpretation of one Contractor choice. Does not assume a GUI slot. */
public final class ContractOffer {
    public enum Type {
        KILL_PLAYERS, FIST_MID_KILLS, SNEAK_ATTACK_KILLS, NO_ARMOR_KILLS, NO_PERK_KILLS,
        DIAMOND_SWORD_FINAL_BLOW, CHAIN_KILLS, CLAIM_BOUNTIES, COLLECT_GOLD_INGOTS,
        KILL_STREAK, GRASS_KILLS, LAVA_KILLS, OBSIDIAN_KILLS, BOW_TAG_KILLS,
        GOLDEN_APPLES, BOW_SHOTS, FISH_DIAMOND_SWORD, UNKNOWN
    }

    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,]*)");
    private static final Pattern VILE = Pattern.compile("(?i)(?:bonus\\s*:\\s*)?\\+?\\s*(\\d+)\\s+chunks?\\s+of\\s+vile");
    private static final Pattern GOLD = Pattern.compile("(?i)reward\\s*:\\s*([\\d,]+)g");
    public final int slot, itemId, metadata, required, goldReward, vileAmount;
    public final Type type;
    public final String name, task;
    public final boolean clickable;
    public final List<String> lore;

    private ContractOffer(int slot, int itemId, int metadata, int required, int goldReward,
                          int vileAmount, Type type, String name, String task, boolean clickable,
                          List<String> lore) {
        this.slot=slot; this.itemId=itemId; this.metadata=metadata; this.required=required;
        this.goldReward=goldReward; this.vileAmount=vileAmount; this.type=type;
        this.name=name; this.task=task; this.clickable=clickable;
        this.lore=Collections.unmodifiableList(new ArrayList<String>(lore));
    }

    public static ContractOffer parse(int slot, int itemId, int metadata, String name, List<String> lore) {
        if (name==null || !name.matches("(?i)choice\\s*#\\s*\\d+")) return null;
        List<String> clean = new ArrayList<String>();
        for(String line:lore) clean.add(strip(line));
        StringBuilder task = new StringBuilder();
        boolean inTask=false, clickable=false;
        int vile=0, gold=0;
        for(String line:clean) {
            String lower=line.toLowerCase(Locale.ROOT);
            if(lower.equals("task:")) {inTask=true; continue;}
            if(inTask && (lower.startsWith("time limit:") || lower.startsWith("reward:") || lower.startsWith("bonus:") || lower.startsWith("click to"))) inTask=false;
            if(inTask && !line.isEmpty()) task.append(line).append(' ');
            Matcher vm=VILE.matcher(line); if(vm.find()) vile=Integer.parseInt(vm.group(1));
            Matcher gm=GOLD.matcher(line); if(gm.find()) gold=Integer.parseInt(gm.group(1).replace(",",""));
            if(lower.contains("click to pick")) clickable=true;
        }
        vile=vileReward(String.join(" ",clean));
        String objective=task.toString().trim();
        Matcher amount=NUMBER.matcher(objective);
        int required=amount.find()?Integer.parseInt(amount.group(1).replace(",","")):0;
        return new ContractOffer(slot,itemId,metadata,required,gold,vile,classify(objective),name,objective,clickable,clean);
    }

    public static boolean shouldStartQuest(String name,String lore,boolean daily,boolean weekly) {
        String n=strip(name);
        return ((daily && n.startsWith("Daily Quest:")) || (weekly && n.startsWith("Weekly Quest:")))
                && strip(lore).toLowerCase(Locale.ROOT).contains("click to start this quest.");
    }
    public static boolean dailyLimit(String text) {
        String s=strip(text);
        return s.equalsIgnoreCase("You already did all of the contracts you could do today!")
                || s.toLowerCase(Locale.ROOT).contains("reached daily limit!");
    }
    public static boolean completedMessage(String text) {
        return strip(text).matches("(?i)^CONTRACT COMPLETED!(?:\\s.*)?$");
    }
    public static int vileReward(String text) {
        Matcher reward=VILE.matcher(strip(text).replace('\u00a0',' '));
        return reward.find()?Integer.parseInt(reward.group(1)):0;
    }
    private static String strip(String line) { return line==null?"":line.replaceAll("(?i)§[0-9a-fk-or]","").trim(); }
    private static Type classify(String task) {
        String s=task.toLowerCase(Locale.ROOT);
        // Ranged/composite objectives must be identified before generic kill labels.
        if(s.contains("shoot") || s.contains("shot") || s.contains("bow") || s.contains("arrow")) {
            if(s.contains("kill") || s.contains("tag")) return Type.BOW_TAG_KILLS;
            return Type.BOW_SHOTS;
        }
        if(s.contains("fish")) return Type.FISH_DIAMOND_SWORD;
        if(s.contains("killing blow") && s.contains("fist") && (s.contains("pit")||s.contains("mid")||s.contains("center"))) return Type.FIST_MID_KILLS;
        if(s.contains("sneak") && s.contains("hit")) return Type.SNEAK_ATTACK_KILLS;
        if((s.contains("no perk") || s.contains("without perk") || s.contains("zero perk"))
                && (s.contains("kill") || s.contains("blow"))) return Type.NO_PERK_KILLS;
        if(s.contains("without") && s.contains("armor")) return Type.NO_ARMOR_KILLS;
        if(s.contains("diamond sword") && (s.contains("kill")||s.contains("blow"))) return Type.DIAMOND_SWORD_FINAL_BLOW;
        if(s.contains("chain") && s.contains("kill")) return Type.CHAIN_KILLS;
        if(s.contains("bount")) return Type.CLAIM_BOUNTIES;
        if(s.contains("gold ingot") || s.matches(".*\\bcollect\\s+[0-9,]+\\s+ingots?\\b.*")) return Type.COLLECT_GOLD_INGOTS;
        if(s.matches(".*\\bkill(?:s)?\\s*streak\\b.*")) return Type.KILL_STREAK;
        if(s.contains("grass")) return Type.GRASS_KILLS;
        if(s.contains("lava")) return Type.LAVA_KILLS;
        if(s.contains("obsidian")) return Type.OBSIDIAN_KILLS;
        if(s.contains("tag") && s.contains("bow")) return Type.BOW_TAG_KILLS;
        if(s.contains("golden apple")) return Type.GOLDEN_APPLES;
        if(s.contains("arrow shot") || s.contains("bow shot")) return Type.BOW_SHOTS;
        if(s.contains("fish") && s.contains("diamond sword")) return Type.FISH_DIAMOND_SWORD;
        if(s.matches(".*kill\\s+\\d+\\s+players?.*")) return Type.KILL_PLAYERS;
        return Type.UNKNOWN;
    }
}
