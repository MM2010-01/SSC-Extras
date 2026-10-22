package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformRelatedItems;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.CreatureInstinct;
import sscextras.SscExtrasGameRules;

@Mixin(value = TransformRelatedItems.class, remap = false)
public abstract class CollarCureMixin {
    @Inject(method = "OnUseCureFinal", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$revertPermanentForm(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity wearer) sscextras.trainer.QuadrupedalTrainer.cure(wearer);
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !player.getWorld().getGameRules().getBoolean(SscExtrasGameRules.UNLOCKED_PERMANENT_FORM_REVERSION)) return;
        var form = FormAbilityManager.getForm(player);
        if (form.getIndex() != 3 || form.getGroup() == null || !form.getGroup().hasForm(2)
                || TransformManager.getPlayerTransformData(player).isTransforming) return;
        var previous = form.getGroup().getForm(2);
        if (previous == null) return;
        player.sendMessage(Text.translatable("info.shape-shifter-curse.max_form_used_cure_final").formatted(Formatting.GRAY));
        ShapeShifterCurseFabric.ON_TRANSFORM_BY_CURE_FINAL.trigger(serverPlayer);
        ShapeShifterCurseFabric.ON_TRANSFORM_BY_CURE.trigger(serverPlayer);
        TransformManager.handleDirectTransform(player, previous, true);
        ci.cancel();
    }

    @Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$calmInstinct(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;
        var form = FormAbilityManager.getForm(player);
        if (form == RegPlayerForms.ORIGINAL_SHIFTER || CreatureInstinct.permanentTarget(player) != null) {
            var instinct = RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
            instinct.instinctValue = Math.max(0, instinct.instinctValue - 25);
            RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
            player.sendMessage(Text.translatable("info.ssc-extras.inhibitor_calmed_instinct").formatted(Formatting.GRAY));
            ci.cancel();
        }
    }
}
