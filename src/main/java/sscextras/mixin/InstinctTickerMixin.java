package sscextras.mixin;

import sscextras.CreatureInstinct;
import sscextras.MoonlightInstinct;
import sscextras.collar.Collars;
import sscextras.cuffs.MetalCuffs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctEffect;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.objectweb.asm.Opcodes;
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

    @Inject(method = "calculateCurrentRate", at = @At("RETURN"), cancellable = true)
    private static void sscExtras$bonusRate(PlayerEntity player, PlayerInstinctComponent comp,
                                           CallbackInfoReturnable<Float> cir) {
        float rate = cir.getReturnValue() + Collars.instinctRate(player) + MoonlightInstinct.rate(player);
        cir.setReturnValue(rate + (comp.instinctValue + rate >= 100 ? 0 : CreatureInstinct.cooldownRate(player, comp)));
    }

    @Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, target =
            "Lnet/onixary/shapeShifterCurseFabric/player_form/instinct/InstinctTicker;isUnderCursedMoon:Z"))
    private static boolean sscExtras$allowInstinctDuringCursedMoon() {
        return false;
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
        if (comp.immediateEffects.isEmpty()) return;
        float cost = CreatureInstinct.costMultiplier(FormAbilityManager.getForm(player));
        int multiplier = Math.max(1, Collars.strength(player) * 2);
        if (cost == 1 && multiplier == 1 && MetalCuffs.equipped(player, false, false).isEmpty()
                && MetalCuffs.equipped(player, true, false).isEmpty()) {
            processImmediateEffects(comp);
            return;
        }
        while (!comp.immediateEffects.isEmpty()) {
            InstinctEffect effect = comp.immediateEffects.poll();
            float value = effect.getValue();
            if (value > 0) value *= multiplier;
            comp.instinctValue = MetalCuffs.apply(player, comp.instinctValue, value / cost);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", remap = true))
    private static float sscExtras$cuffPassiveGain(float value, float min, float max, ServerPlayerEntity player) {
        var comp = net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
        return MetalCuffs.apply(player, comp.instinctValue, comp.currentInstinctRate);
    }

    @Inject(method = "checkThreshold", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$transformToPermanent(ServerPlayerEntity player, PlayerInstinctComponent comp,
                                                       CallbackInfo ci) {
        if (comp.instinctValue >= 100) comp.instinctValue = MetalCuffs.apply(player, 99, comp.instinctValue - 99);
        PlayerFormBase target = CreatureInstinct.permanentTarget(FormAbilityManager.getForm(player));
        if (target != null) {
            if (comp.instinctValue >= 100.0f && !InstinctTicker.isPausing
                    && player.isAlive() && (!player.isCreative() || Collars.strength(player) > 0) && !player.isSpectator()
                    && !TransformManager.getPlayerTransformData(player).isTransforming) {
                TransformManager.handleDirectTransform(player, target, false);
                comp.instinctValue = 0.0f;
            }
            ci.cancel();
        }
    }
}
