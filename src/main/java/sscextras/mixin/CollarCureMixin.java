package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformRelatedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.collar.Collars;
import sscextras.CreatureInstinct;

@Mixin(value = TransformRelatedItems.class, remap = false)
public abstract class CollarCureMixin {
    @Inject(method = {"OnUseCure", "OnUseCureFinal"}, at = @At("HEAD"), cancellable = true)
    private static void sscExtras$releaseCollar(PlayerEntity player, CallbackInfo ci) {
        if (Collars.release(player)) ci.cancel();
    }

    @Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$calmStageTwo(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity
                && CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player)) != null) {
            var instinct = RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
            instinct.instinctValue = Math.max(0, instinct.instinctValue - 25);
            RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
            player.sendMessage(Text.translatable("info.ssc-extras.inhibitor_calmed_instinct").formatted(Formatting.GRAY));
            ci.cancel();
        }
    }
}
