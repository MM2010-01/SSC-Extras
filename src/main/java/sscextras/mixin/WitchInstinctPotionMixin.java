package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.CreatureInstinct;

@Mixin(value = WitchEntity.class, priority = 900)
public abstract class WitchInstinctPotionMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$continueFamiliarPotions(LivingEntity target, float pullProgress, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity player)
                || FormAbilityManager.getForm(player).equals(RegPlayerForms.ORIGINAL_SHIFTER)
                || !CreatureInstinct.canGain(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT)) return;
        WitchEntity witch = (WitchEntity) (Object) this;
        var velocity = target.getVelocity();
        double dx = target.getX() + velocity.x - witch.getX();
        double dy = target.getEyeY() - 1.1f - witch.getY();
        double dz = target.getZ() + velocity.z - witch.getZ();
        PotionEntity potion = new PotionEntity(witch.getWorld(), witch);
        potion.setItem(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), RegCustomPotions.FAMILIAR_FOX_FORM_POTION));
        potion.setPitch(potion.getPitch() + 20);
        potion.setVelocity(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 0.75f, 8);
        if (!witch.isSilent()) {
            witch.getWorld().playSound(null, witch.getX(), witch.getY(), witch.getZ(),
                    SoundEvents.ENTITY_WITCH_THROW, witch.getSoundCategory(), 1, 0.8f);
        }
        witch.getWorld().spawnEntity(potion);
        ci.cancel();
    }
}
