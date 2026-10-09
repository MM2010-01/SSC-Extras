package sscextras.guardian;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import virtuoel.pehkui.api.ScaleTypes;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class GuardianBodyPower extends Power {
    private final int stage;
    private static final EntityAttribute[] ATTRIBUTES = {EntityAttributes.GENERIC_ARMOR, EntityAttributes.GENERIC_MAX_HEALTH,
            EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, EntityAttributes.GENERIC_MOVEMENT_SPEED,
            EntityAttributes.GENERIC_ATTACK_DAMAGE, EntityAttributes.GENERIC_ATTACK_SPEED};

    public GuardianBodyPower(PowerType<?> type, LivingEntity entity, int stage) {
        super(type, entity); this.stage = stage; setTicking();
    }
    @Override public void onAdded() { refresh(); resize(); }
    @Override public void onRespawn() { refresh(); resize(); }
    @Override public void tick() { refresh(); }

    public void resize() {
        if (entity.getWorld().isClient) return;
        // The mesh retains its authored proportions; these scales control only physical reach and clearance.
        float[] height = {1.8f, 1.95f, 2.15f, 2.7f};
        float[] width = {.6f, .72f, .9f, 1.1f};
        setScale(ScaleTypes.WIDTH, 1); setScale(ScaleTypes.HEIGHT, 1);
        setScale(ScaleTypes.HITBOX_WIDTH, width[stage] / .6f);
        setScale(ScaleTypes.HITBOX_HEIGHT, height[stage] / 1.8f);
        setScale(ScaleTypes.EYE_HEIGHT, height[stage] * .91f / 1.62f);
    }
    private void setScale(virtuoel.pehkui.api.ScaleType type, float amount) {
        var scale = type.getScaleData(entity); scale.setScale(amount); scale.setPersistence(true);
    }
    public void refresh() {
        if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player)) return;
        var state = RuneboundGuardian.state(player);
        boolean charged = stage >= 2 && state.fraction() > .8;
        boolean overdrive = stage == 3 && state.overdrive && state.energy > 0;
        boolean protecting = stage == 3 && player.isSprinting() && GuardianVillages.sprintingToHelp(player);
        double speed = (sunlit(player) ? new double[]{.85, .75, .65, .5} : new double[]{.95, .9, .85, .85})[stage];
        if (protecting) speed = 1.2;
        if (stage >= 2 && state.energy <= 0) speed = .25;
        if (overdrive) speed *= 2;
        set(0, new double[]{1, 5, 10, 15}[stage], false, "base");
        set(1, stage * 2, false, "base");
        set(2, new double[]{.1, .35, .6, 1}[stage], false, "base");
        set(3, speed - 1, true, "base");
        set(4, player.getInventory().getMainHandStack().isEmpty() ? new double[]{1, 4, 5, 6}[stage] : 0, false, "base");
        set(4, (charged ? 1.5 : 1) * (overdrive ? 2 : 1) - 1, true, "boost");
        double efficiency = GuardianCombat.toolEfficiency(player, player.getInventory().getMainHandStack());
        set(5, efficiency * (charged ? 1.3 : 1) * (overdrive ? 2 : 1) - 1, true, "base");
    }
    private static UUID modifier(int index, String name) {
        return UUID.nameUUIDFromBytes(("ssc-extras:guardian/" + index + "/" + name).getBytes(StandardCharsets.UTF_8));
    }
    private void set(int index, double amount, boolean multiply, String name) {
        var attribute = entity.getAttributeInstance(ATTRIBUTES[index]);
        if (attribute == null) return;
        UUID id = modifier(index, name);
        var old = attribute.getModifier(id);
        if (old != null && old.getValue() == amount) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addTemporaryModifier(new EntityAttributeModifier(id, "Runebound Guardian", amount,
                multiply ? EntityAttributeModifier.Operation.MULTIPLY_TOTAL : EntityAttributeModifier.Operation.ADDITION));
    }
    @Override public void onRemoved() {
        if (entity.getWorld().isClient) return;
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            var attribute = entity.getAttributeInstance(ATTRIBUTES[i]);
            if (attribute != null) { attribute.removeModifier(modifier(i, "base")); attribute.removeModifier(modifier(i, "boost")); }
        }
        setScale(ScaleTypes.HITBOX_WIDTH, 1);
        setScale(ScaleTypes.HITBOX_HEIGHT, 1); setScale(ScaleTypes.EYE_HEIGHT, 1);
        if (entity instanceof PlayerEntity player) flight(player, false);
    }
    public static boolean sunlit(PlayerEntity player) {
        var world = player.getWorld();
        return world.getDimension().hasSkyLight() && world.isDay() && !world.isRaining()
                && world.isSkyVisible(BlockPos.ofFloored(player.getX(), player.getEyeY(), player.getZ()));
    }
    public static void flight(PlayerEntity player, boolean enabled) {
        if (player.getWorld().isClient) return;
        var state = RuneboundGuardian.state(player);
        for (var power : io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player).getPowers(io.github.apace100.apoli.power.PlayerAbilityPower.class, true)) {
            if (!power.getType().getIdentifier().equals(RuneboundGuardian.id("guardian/flight"))) continue;
            if (enabled && !power.hasAbility()) power.grantAbility();
            else if (!enabled && power.hasAbility()) power.revokeAbility();
        }
        state.flightGranted = enabled;
    }
    public static PowerFactory<GuardianBodyPower> factory() {
        return new PowerFactory<>(RuneboundGuardian.id("guardian_body"), new SerializableData().add("stage", SerializableDataTypes.INT),
                data -> (type, entity) -> new GuardianBodyPower(type, entity, data.getInt("stage")));
    }
}
