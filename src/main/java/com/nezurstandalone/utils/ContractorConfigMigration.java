package com.nezurstandalone.utils;
import com.google.gson.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
/** Migrate the previous default while preserving explicitly longer custom delays. */
public final class ContractorConfigMigration {
    private ContractorConfigMigration(){}
    public static void migrate(JsonObject root){
        JsonElement module=root.get("AutoContractor");if(module==null||!module.isJsonObject())return;
        JsonElement settings=module.getAsJsonObject().get("settings");if(settings==null||!settings.isJsonObject())return;
        JsonObject values=settings.getAsJsonObject();JsonElement delay=values.get("Gapple Oof Cooldown ms");
        if(delay!=null&&delay.isJsonPrimitive()&&delay.getAsJsonPrimitive().isNumber()
                && (delay.getAsDouble()==9000 || delay.getAsDouble()==12000))
            values.addProperty("Gapple Oof Cooldown ms",com.nezurstandalone.contract.ContractPerkPlan.GAPPLE_MIN_OOF_COOLDOWN_MS);
        JsonElement offerDelay=values.get("Offer Stable (ms)");
        if(offerDelay!=null&&offerDelay.isJsonPrimitive()&&offerDelay.getAsJsonPrimitive().isNumber()&&offerDelay.getAsDouble()==3000)
            values.addProperty("Offer Stable (ms)",650);
        JsonElement goldRescan=values.get("Gold Rescan (ms)");
        if(goldRescan!=null&&goldRescan.isJsonPrimitive()&&goldRescan.getAsJsonPrimitive().isNumber()&&goldRescan.getAsDouble()==750)
            values.addProperty("Gold Rescan (ms)",300);
        migratePriority(values);
    }

    private static void migratePriority(JsonObject values) {
        if(values.get("Contract Priority")!=null)return;
        String[][] legacy={
                {"GOLDEN_APPLES","Golden Apple Priority","1"},{"KILL_PLAYERS","Kill Priority","1"},
                {"FIST_MID_KILLS","Fist Priority","2"},{"SNEAK_ATTACK_KILLS","Sneak Priority","3"},
                {"NO_ARMOR_KILLS","No Armor Priority","4"},{"NO_PERK_KILLS","No Perk Priority","9"},
                {"DIAMOND_SWORD_FINAL_BLOW","Diamond Sword Priority","5"},{"CLAIM_BOUNTIES","Bounty Priority","7"},
                {"COLLECT_GOLD_INGOTS","Gold Priority","8"},{"KILL_STREAK","Streak Priority","9"}
        };
        List<String[]> order=new ArrayList<>();
        boolean found=false;
        for(String[] entry:legacy){
            order.add(entry);
            if(values.get(entry[1])!=null)found=true;
        }
        if(!found)return;
        order.sort(Comparator.comparingDouble(entry->oldRank(values,entry)));
        List<String> names=new ArrayList<>();
        for(String[] entry:order)names.add(entry[0]);
        values.addProperty("Contract Priority",String.join(",",names));
    }

    private static double oldRank(JsonObject settings,String[] entry) {
        JsonElement rank=settings.get(entry[1]);
        if(rank!=null&&rank.isJsonPrimitive()&&rank.getAsJsonPrimitive().isNumber()){
            double number=rank.getAsDouble();
            if(Double.isFinite(number))return number;
        }
        return Double.parseDouble(entry[2]);
    }
}
