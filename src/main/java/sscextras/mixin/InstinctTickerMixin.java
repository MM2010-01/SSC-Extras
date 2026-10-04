package sscextras.mixin;

import sscextras.CreatureInstinct;
import sscextras.collar.Collars;
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

    @Inject(method = "tick", at = @At("HEAD"))
    private static void sscExtras$collarBonus(ServerPlayerEntity player, CallbackInfo ci) {
        Collars.tick(player);
    }

    @Inject(method = "judgeInstinctGrowRate", at = @At("RETURN"), cancellable = true)
    private static void sscExtras$permanentBaseRate(PlayerEntity player, CallbackInfoReturnable<Float> cir) {
        float base = CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player)) != null
                ? 5.5555557E-4f / CreatureInstinct.PERMANENT_INSTINCT_COST : cir.getReturnValue();
        cir.setReturnValue(Collars.gain(player, base));
    }

    @Redirect(method = "calculateCurrentRate", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/InstinctEffect;getRateModifier()F"))
    private static float sscExtras$scaleSustainedEffect(InstinctEffect effect, PlayerEntity player,
                                                       PlayerInstinctComponent comp) {
        return Collars.gain(player, effect.getRateModifier()) / CreatureInstinct.costMultiplier(FormAbilityManager.getForm(player));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/InstinctTicker;processImmediateEffects(Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/PlayerInstinctComponent;)V"))
    private static void sscExtras$scaleImmediateEffects(PlayerInstinctComponent comp, ServerPlayerEntity player) {
        float cost = CreatureInstinct.costMultiplier(FormAbilityManager.getForm(player));
        int multiplier = Math.max(1, Collars.strength(player) * 2);
        if (cost == 1 && multiplier == 1) {
            processImmediateEffects(comp);
            return;
        }
        while (!comp.immediateEffects.isEmpty()) {
            InstinctEffect effect = comp.immediateEffects.poll();
            float value = effect.getValue();
            if (value > 0 && !effect.ID.equals(Collars.BONUS)) value *= multiplier;
            comp.instinctValue = MathHelper.clamp(comp.instinctValue
                    + value / cost, 0.0f, 100.0f);
        }
    }

    @Inject(method = "checkThreshold", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$transformToPermanent(ServerPlayerEntity player, PlayerInstinctComponent comp,
                                                       CallbackInfo ci) {
        PlayerFormBase target = CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player));
        if (target != null) {
            if (comp.instinctValue >= 100.0f && !InstinctTicker.isPausing && !InstinctTicker.isUnderCursedMoon
                    && player.isAlive() && (!player.isCreative() || Collars.strength(player) > 0) && !player.isSpectator()
                    && !TransformManager.getPlayerTransformData(player).isTransforming) {
                TransformManager.handleDirectTransform(player, target, false);
                comp.instinctValue = 0.0f;
            }
            ci.cancel();
        }
    }
}
