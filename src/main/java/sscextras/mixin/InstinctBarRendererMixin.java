package sscextras.mixin;

import sscextras.CreatureInstinct;
import sscextras.client.InstinctGainFeedback;
import sscextras.client.CuffDurabilityOutline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctBarRenderer;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstinctBarRenderer.class, remap = false)
public abstract class InstinctBarRendererMixin {
    @Shadow private boolean isInstinctLock;
    @Unique private final InstinctGainFeedback sscExtras$gainFeedback = new InstinctGainFeedback();

    @Inject(method = "updateBarTextures", at = @At("HEAD"))
    private void sscExtras$trackGain(PlayerEntity player, CallbackInfo ci) {
        sscExtras$gainFeedback.update(player, RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player));
    }

    @Redirect(method = "updateBarTextures", at = @At(value = "FIELD", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/PlayerInstinctComponent;isInstinctIncreasing:Z"))
    private boolean sscExtras$showGain(PlayerInstinctComponent instinct) {
        return sscExtras$gainFeedback.increasing(instinct);
    }

    @Redirect(method = "updateBarTextures", at = @At(value = "FIELD", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/PlayerInstinctComponent;isInstinctDecreasing:Z"))
    private boolean sscExtras$preserveDecrease(PlayerInstinctComponent instinct) {
        return sscExtras$gainFeedback.decreasing(instinct);
    }

    @Redirect(method = "updateBarTextures", at = @At(value = "FIELD", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/PlayerInstinctComponent;currentInstinctRate:F"))
    private float sscExtras$gainColor(PlayerInstinctComponent instinct) {
        return sscExtras$gainFeedback.rate(instinct);
    }

    @Inject(method = "renderInstinctBar", at = @At("HEAD"))
    private void sscExtras$removeMoonLock(DrawContext context, float tickDelta, int x, int y,
            PlayerEntity player, CallbackInfo ci) {
        PlayerFormBase form = FormAbilityManager.getForm(player);
        if (form.getIndex() < 2 || CreatureInstinct.permanentTarget(form) != null) isInstinctLock = false;
    }

    @Inject(method = "renderInstinctBar", at = @At("TAIL"))
    private void sscExtras$cuffDurability(DrawContext context, float tickDelta, int x, int y,
            PlayerEntity player, CallbackInfo ci) {
        CuffDurabilityOutline.render(context, x, y, player);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBase;getPhase()Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormPhase;"))
    private PlayerFormPhase sscExtras$showOriginalGauge(PlayerFormBase form) {
        return RegPlayerForms.ORIGINAL_SHIFTER.equals(form) ? PlayerFormPhase.PHASE_0 : form.getPhase();
    }

    @Redirect(method = "render", at = @At(value = "FIELD", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBase;FormIndex:I"))
    private int sscExtras$unlockPermanentProgression(PlayerFormBase form) {
        return CreatureInstinct.permanentTarget(form) != null ? 1 : form.FormIndex;
    }
}
