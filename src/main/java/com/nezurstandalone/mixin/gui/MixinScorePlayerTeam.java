package com.nezurstandalone.mixin.gui;

import com.nezurstandalone.utils.ScoreboardHelper;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScorePlayerTeam.class)
public class MixinScorePlayerTeam {

    @Inject(method = {"getColorPrefix", "func_96668_e"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void nezur$onGetColorPrefix(CallbackInfoReturnable<String> cir) {
        if (ScoreboardHelper.isChecking()) return;
        if (ScoreboardHelper.isFooterTeam((ScorePlayerTeam) (Object) this)) {
            cir.setReturnValue(ScoreboardHelper.getAnimatedText());
        }
    }

    @Inject(method = {"getColorSuffix", "func_96663_f"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void nezur$onGetColorSuffix(CallbackInfoReturnable<String> cir) {
        if (ScoreboardHelper.isChecking()) return;
        if (ScoreboardHelper.isFooterTeam((ScorePlayerTeam) (Object) this)) {
            cir.setReturnValue("");
        }
    }
}
