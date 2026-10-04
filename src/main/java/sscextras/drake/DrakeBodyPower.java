package sscextras.drake;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

/** The posture is synced through Apoli, including to other players watching a hungry drake. */
public final class DrakeBodyPower extends Power {
    private final int stage;
    private boolean allFours;

    public DrakeBodyPower(PowerType<?> type, LivingEntity entity, int stage) {
        super(type, entity);
        this.stage = stage;
        allFours = stage == 3;
        setTicking();
    }

    public boolean onAllFours() { return allFours; }
    public void refreshSize() { update(true, true); }

    @Override public void onAdded() { update(true, false); }
    @Override public void onRespawn() { update(true, false); }
    @Override public void tick() { update(false, true); }

    private void update(boolean force, boolean sync) {
        if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player)) return;
        boolean next = stage == 3 || stage == 2 && (player.isSprinting() || player.isSneaking()
                || player.hasPassengers() || player.getHungerManager().getFoodLevel() <= 6);
        if (!force && next == allFours) return;
        boolean changed = next != allFours;
        allFours = next;
        float scale = switch (stage) { case 1 -> 2.0f / 1.8f; case 2 -> 2.2f / 1.8f; case 3 -> 1.5f; default -> 1.0f; };
        float height = stage == 3 ? 1.65f : stage == 2 && allFours ? 1.4f : 1.8f * scale;
        float width = switch (stage) { case 1 -> 0.7f; case 2 -> 0.95f; case 3 -> 1.6f; default -> 0.6f; };
        set(ScaleTypes.WIDTH, scale);
        set(ScaleTypes.HEIGHT, scale);
        set(ScaleTypes.HITBOX_WIDTH, width / (0.6f * scale));
        set(ScaleTypes.HITBOX_HEIGHT, height / (1.8f * scale));
        set(ScaleTypes.EYE_HEIGHT, (stage == 3 ? 1.364f : allFours ? height * 0.8f : height * 0.9f) / (1.62f * scale));
        // Origin changes synchronize the whole component after adding its powers.
        if (sync && changed) PowerHolderComponent.syncPower(player, getType());
    }

    private void set(ScaleType type, float value) {
        var data = type.getScaleData(entity);
        data.setScale(value);
        data.setPersistence(true);
    }

    @Override public void onRemoved() {
        if (entity.getWorld().isClient) return;
        // SSC's incoming scale power owns the other four scales and can run before removal.
        set(ScaleTypes.HITBOX_WIDTH, 1.0f);
    }

    @Override public NbtElement toTag() { return NbtByte.of(allFours); }
    @Override public void fromTag(NbtElement tag) { allFours = tag instanceof NbtByte value && value.byteValue() != 0; }

    public static PowerFactory<DrakeBodyPower> factory() {
        return new PowerFactory<>(EarthenDrake.id("drake_body"), new SerializableData()
                .add("stage", SerializableDataTypes.INT), data -> (type, entity) -> new DrakeBodyPower(type, entity, data.getInt("stage")));
    }
}
