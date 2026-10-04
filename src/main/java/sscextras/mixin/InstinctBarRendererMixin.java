package sscextras.mixin;

import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = InstinctBarRenderer.class, remap = false)
public abstract class InstinctBarRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBase;getPhase()Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormPhase;"))
    private PlayerFormPhase sscExtras$showOriginalGauge(PlayerFormBase form) {
        return RegPlayerForms.ORIGINAL_SHIFTER.equals(form) ? PlayerFormPhase.PHASE_0 : form.getPhase();
    }
}
