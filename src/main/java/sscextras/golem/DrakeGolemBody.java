package sscextras.golem;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import sscextras.player.AuthoredFormDimensions;

/** Apoli owns persistence and tracking synchronization for this form's appearance. */
public final class DrakeGolemBody extends Power {
    private boolean weathered, runesOff, coreAbsent, manualStatue;
    public DrakeGolemBody(PowerType<?> type, LivingEntity entity) { super(type, entity); }
    public static DrakeGolemBody get(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, DrakeGolemBody.class).stream().findFirst().orElse(null);
    }
    public boolean weathered() { return weathered; }
    public boolean statue() { return manualStatue || runesOff || coreAbsent; }
    public boolean runesOn() { return !statue() && !entity.isInPose(net.minecraft.entity.EntityPose.SLEEPING); }
    public boolean toggleStatue() {
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player) || !player.isAlive()
                || player.isSpectator() || player.isSleeping() || player.hasVehicle() || player.hasPassengers()
                || sscextras.statue.StatueCrates.packed(player) || !manualStatue && !player.isOnGround()) return false;
        manualStatue = !manualStatue;
        player.clearActiveItem(); player.closeHandledScreen();
        sscextras.petrification.Petrification.state(player).updateLock();
        PowerHolderComponent.syncPower(entity, getType());
        player.sendMessage(net.minecraft.text.Text.translatable("message.ssc-extras.golem." + (statue() ? "statue_on" : "statue_off")), true);
        return true;
    }
    public boolean corePresent() { return !coreAbsent; }
    public void appearance(boolean weathered, boolean runesOn, boolean corePresent) {
        if (entity.getWorld().isClient) return;
        this.weathered = weathered; runesOff = !runesOn || !corePresent; coreAbsent = !corePresent;
        sscextras.petrification.Petrification.state(entity).updateLock();
        PowerHolderComponent.syncPower(entity, getType());
    }
    public void resize() { AuthoredFormDimensions.apply(entity, 1.14f, 1.97f, 1.67f); }
    @Override public void onAdded() { resize(); }
    @Override public void onRespawn() { resize(); }
    @Override public void onRemoved() { AuthoredFormDimensions.reset(entity); }
    @Override public NbtElement toTag() {
        var tag = new NbtCompound(); tag.putBoolean("Weathered", weathered);
        tag.putBoolean("RunesOff", runesOff); tag.putBoolean("CoreAbsent", coreAbsent); tag.putBoolean("StatueMode", manualStatue); return tag;
    }
    @Override public void fromTag(NbtElement value) {
        if (value instanceof NbtCompound tag) {
            weathered = tag.getBoolean("Weathered"); runesOff = tag.getBoolean("RunesOff"); coreAbsent = tag.getBoolean("CoreAbsent");
            manualStatue = tag.getBoolean("StatueMode");
        }
    }
    public static PowerFactory<DrakeGolemBody> factory() {
        return new PowerFactory<>(DrakeGolem.id("drake_golem_body"), new SerializableData(), data -> DrakeGolemBody::new);
    }
}
