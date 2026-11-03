package com.nezurstandalone.utils;
import com.google.gson.*;
/** Migrate the previous default while preserving explicitly longer custom delays. */
public final class ContractorConfigMigration {
    private ContractorConfigMigration(){}
    public static void migrate(JsonObject root){
        JsonElement module=root.get("AutoContractor");if(module==null||!module.isJsonObject())return;
        JsonElement settings=module.getAsJsonObject().get("settings");if(settings==null||!settings.isJsonObject())return;
        JsonObject values=settings.getAsJsonObject();JsonElement delay=values.get("Gapple Oof Cooldown ms");
        if(delay!=null&&delay.isJsonPrimitive()&&delay.getAsJsonPrimitive().isNumber()&&delay.getAsDouble()==12000)
            values.addProperty("Gapple Oof Cooldown ms",9000);
    }
}
