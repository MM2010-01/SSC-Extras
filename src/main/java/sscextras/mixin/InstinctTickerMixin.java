package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctEffect;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InstinctTicker.class, remap = false)
public abstract class InstinctTickerMixin {
    @Shadow private static void processImmediateEffects(PlayerInstinctComponent comp) { throw new AssertionError(); }

    @Inject(method = "clearInstinct", at = @At("HEAD"))
    private static void sscExtras$clearTarget(PlayerEntity player, CallbackInfo ci) {
        CreatureInstinct.clearTarget(player);
    }

    @Inject(method = "judgeInstinctGrowRate", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$permanentBaseRate(PlayerEntity player, CallbackInfoReturnable<Float> cir) {
        if (CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player)) != null) {
            cir.setReturnValue(5.5555557E-4f / CreatureInstinct.PERMANENT_INSTINCT_COST);
        }
    }

    @Redirect(method = "calculateCurrentRate", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/InstinctEffect;getRateModifier()F"))
    private static float sscExtras$scaleSustainedEffect(InstinctEffect effect, PlayerEntity player,
                                                       PlayerInstinctComponent comp) {
        return effect.getRateModifier() / CreatureInstinct.costMultiplier(FormAbilityManager.getForm(player));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/InstinctTicker;processImmediateEffects(Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/PlayerInstinctComponent;)V"))
    private static void sscExtras$scaleImmediateEffects(PlayerInstinctComponent comp, ServerPlayerEntity player) {
        if (CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player)) == null) {
            processImmediateEffects(comp);
            return;
        }
        while (!comp.immediateEffects.isEmpty()) {
            InstinctEffect effect = comp.immediateEffects.poll();
            comp.instinctValue = MathHelper.clamp(comp.instinctValue
                    + effect.getValue() / CreatureInstinct.PERMANENT_INSTINCT_COST, 0.0f, 100.0f);
        }
    }

    @Inject(method = "checkThreshold", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$transformToPermanent(ServerPlayerEntity player, PlayerInstinctComponent comp,
                                                       CallbackInfo ci) {
        PlayerFormBase target = CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player));
        if (target != null) {
            if (comp.instinctValue >= 100.0f && !InstinctTicker.isPausing && !InstinctTicker.isUnderCursedMoon
                    && player.isAlive() && !player.isCreative() && !player.isSpectator()
                    && !TransformManager.getPlayerTransformData(player).isTransforming) {
                TransformManager.handleDirectTransform(player, target, false);
                comp.instinctValue = 0.0f;
            }
            ci.cancel();
        }
    }
}
