package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.CreatureInstinct;
import sscextras.drake.EarthenDrake;

@Mixin(value = CodexData.class, remap = false)
public abstract class CodexInstinctMixin {
    @Inject(method = "getDescText", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$permanentWarning(CodexData.ContentType type, PlayerEntity player,
                                                   CallbackInfoReturnable<Text> cir) {
        if (type == CodexData.ContentType.INSTINCTS
                && CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player)) != null) {
            cir.setReturnValue(Text.translatable("codex.ssc-extras.instincts_2"));
        }
    }

    @Inject(method = "getContentText", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$previousStageHints(CodexData.ContentType type, PlayerEntity player,
                                                     CallbackInfoReturnable<Text> cir) {
        var form = FormAbilityManager.getForm(player);
        if (type == CodexData.ContentType.INSTINCTS && CreatureInstinct.permanentTarget(form) != null
                && form.getGroup() != EarthenDrake.GROUP
                && form.getGroup().hasForm(1)) {
            cir.setReturnValue(form.getGroup().getForm(1).getContentText(type));
        }
    }
}
