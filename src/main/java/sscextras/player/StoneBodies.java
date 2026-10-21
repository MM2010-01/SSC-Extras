package sscextras.player;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.petrification.Petrification;
import java.util.UUID;

/** Shared stone material policies. Form identity, effects and native attributes own all state. */
public final class StoneBodies {
    public static final TagKey<EntityType<?>> TYPES = TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("ssc-extras", "stone_bodies"));
    public static final UUID ARMOR = UUID.fromString("6cb9cd25-e4d8-4930-a380-b4140fad6b30");
    private StoneBodies() { }

    private static StoneGolemForm form(LivingEntity entity) {
        return entity instanceof PlayerEntity player && FormAbilityManager.getForm(player) instanceof StoneGolemForm stone ? stone : null;
    }
    public static boolean material(LivingEntity entity) { return entity.getType().isIn(TYPES) || form(entity) != null; }
    public static boolean natural(LivingEntity entity) {
        var form = form(entity);
        return entity.getType().isIn(TYPES) || form != null && form.solidStone();
    }
    public static boolean physical(LivingEntity entity) { return entity.isAlive() && !entity.isSpectator() && (natural(entity) || Petrification.stone(entity)); }
    public static boolean mineable(LivingEntity entity) { return entity.isAlive() && !entity.isSpectator() && physical(entity); }
    public static SoundEvent sound(LivingEntity entity, SoundEvent original) {
        var effect = entity.getStatusEffect(Petrification.EFFECT);
        return natural(entity) || effect != null && effect.getAmplifier() >= 2 ? SoundEvents.BLOCK_STONE_BREAK : original;
    }
    public static void refreshArmor(LivingEntity entity) {
        var effect = entity.getStatusEffect(Petrification.EFFECT);
        refreshArmor(entity, effect == null ? -1 : effect.getAmplifier());
    }
    /** Called from effect add/remove and form/load lifecycles, never a separate tick owner. */
    public static void refreshArmor(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;
        var attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (attribute == null) return;
        var form = form(entity);
        double supplied = form != null ? form.suppliedStoneArmor() : entity.getType().isIn(TYPES) ? attribute.getBaseValue() : 0;
        double amount = natural(entity) || amplifier >= 2 ? Math.max(0, 10 - supplied) : 0;
        var old = attribute.getModifier(ARMOR);
        if (old != null && old.getValue() == amount) return;
        attribute.removeModifier(ARMOR);
        if (amount > 0) attribute.addTemporaryModifier(new EntityAttributeModifier(ARMOR, "Stone body", amount, EntityAttributeModifier.Operation.ADDITION));
    }
}
