package sscextras.guardian;

import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class GuardianAbilityPower extends Power implements Active {
    private Key key = new Key();
    private final boolean overdrive;
    private long nextUse;
    public GuardianAbilityPower(PowerType<?> type, LivingEntity entity, boolean overdrive) {
        super(type, entity); this.overdrive = overdrive;
        key.key = overdrive ? "key.origins.primary_active" : "key.origins.secondary_active";
    }
    @Override public Key getKey() { return key; }
    @Override public void setKey(Key key) { this.key = key; }
    @Override public void onUse() {
        if (!(entity instanceof ServerPlayerEntity player) || !isActive() || !player.isAlive()
                || player.isSpectator() || player.getWorld().getTime() < nextUse) return;
        var state = RuneboundGuardian.state(player);
        if (GuardianCombat.locked(player)) return;
        if (overdrive) {
            int stage = RuneboundGuardian.stage(player);
            if (stage < 2 || state.energy <= 0) return;
            if (stage == 2) {
                GuardianCombat.clawSwipe(player);
                return;
            }
            state.overdrive = !state.overdrive;
        } else {
            if (RuneboundGuardian.stage(player) < 2) return;
            state.vision = !state.vision;
        }
        nextUse = player.getWorld().getTime() + 5;
        player.sendMessage(Text.translatable("message.ssc-extras.guardian." + (overdrive ? "overdrive_" : "vision_")
                + ((overdrive ? state.overdrive : state.vision) ? "on" : "off")), true);
        state.sync();
    }
    public static PowerFactory<GuardianAbilityPower> factory() {
        return new PowerFactory<>(RuneboundGuardian.id("guardian_ability"),
                new SerializableData().add("overdrive", SerializableDataTypes.BOOLEAN, false),
                data -> (type, entity) -> new GuardianAbilityPower(type, entity, data.getBoolean("overdrive")));
    }
}
