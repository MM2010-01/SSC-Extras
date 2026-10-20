package sscextras.guardian;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import java.util.Locale;

/** Player-owned reserves and presentation; core identity and village service live in world saved data. */
public final class GuardianState implements AutoSyncedComponent, ServerTickingComponent {
    public final PlayerEntity owner;
    public double energy = 100;
    public boolean vision = true, overdrive, detached, protecting, flightGranted, deactivated, statueMode;
    public float frozenYaw, frozenPitch;
    public int recoveryBlackout;
    public boolean recoveryHintPending;
    public boolean traderRecoveryHint, canGiveUp, coreDeath;
    GuardianRepairEvent repair;
    public String guardianName = "";
    public UUID coreId;
    public UUID loadingCoreId;
    public int ritualTicks, weatherTicks, sleepTicks;
    public boolean pedestalSleeping, daySleep;
    public double foodRemainder;
    public int previousFood = 20;
    public float previousSaturation = 5;
    public boolean replacedHunger;
    public long magicInstinctAt = Long.MIN_VALUE;
    public long nextClawSwipe;
    public int lastStage = -1;
    public boolean stageTwoHintShown;
    public Vec3d previousPosition;
    public Vec3d cuffPosition;
    public int soulboundStage = -2;
    public GuardianProtectBrain brain;
    public GuardianEscort escort;
    public int ritualGuideId;
    private int lastEnergy = -1, lastWeather = -1;
    private boolean lastDetached, lastProtecting, lastDeactivated, lastFlying, syncedFlying;

    public GuardianState(PlayerEntity owner) { this.owner = owner; }
    public double capacity() { return RuneboundGuardian.stage(owner) == 3 ? 200 : 100; }
    public double fraction() { return MathHelper.clamp(energy / capacity(), 0, 1); }
    public void charge(double amount) { energy = MathHelper.clamp(energy + amount, 0, capacity()); }
    public void spend(double amount) { charge(-amount * (overdrive ? 2 : 1)); }
    public void sync() { if (!owner.getWorld().isClient) GuardianComponents.KEY.sync(owner); }
    public boolean ritualRestrained() { return ritualTicks > 1 || ritualTicks == 1 && ritualGuideId != 0; }
    public boolean flying() {
        return RuneboundGuardian.stage(owner) >= 2 && energy > 0 && !GuardianCombat.locked(owner)
                && !owner.isSleeping() && !pedestalSleeping && !owner.isSpectator()
                && (owner.getWorld().isClient ? syncedFlying : owner.getAbilities().flying);
    }

    public boolean toggleStatueMode() {
        if (!(owner instanceof ServerPlayerEntity player) || RuneboundGuardian.stage(player) != 3
                || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || detached || ritualTicks > 0 || player.hasVehicle() || player.hasPassengers()) return false;
        if (!statueMode) {
            var claim = GuardianVillages.claim(player);
            if (claim == null || !claim.world.equals(player.getWorld().getRegistryKey())
                    || !GuardianPedestal.standing(player) || !claim.pedestal.equals(GuardianPedestal.beneath(player))) return false;
        }
        statueMode = !statueMode;
        if (statueMode) deactivate(player);
        else if (energy >= 2 || player.isCreative()) deactivated = false;
        systemMessage(player, statueMode ? "statue_on" : "statue_off");
        if (!statueMode && deactivated) systemMessage(player, "power_empty");
        sync();
        return true;
    }

    private void deactivate(ServerPlayerEntity player) {
        if (!deactivated) { frozenYaw = player.getYaw(); frozenPitch = player.getPitch(); }
        deactivated = true; overdrive = false;
        player.clearActiveItem(); player.closeHandledScreen(); player.setSprinting(false); player.setSneaking(false);
        player.setVelocity(0, player.getVelocity().y, 0);
        GuardianBodyPower.flight(player, false);
        GuardianVillages.stopProtecting(player);
    }

    private void systemMessage(ServerPlayerEntity player, String line, Object... arguments) {
        player.sendMessage(Text.translatable("message.ssc-extras.guardian." + line, arguments).formatted(Formatting.AQUA), false);
    }

    @Override public void serverTick() {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        GuardianEquipment.tick(player);
        GuardianOffering.standing(player);
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0 && lastStage < 0 && coreId == null && !detached && !flightGranted && !replacedHunger) return;
        if (stage != lastStage) {
            GuardianVillages.stopRepair(player);
            GuardianDialogue.stop(player);
            if (stage < 2) stageTwoHintShown = false;
            var service = GuardianVillages.claim(player);
            if (service != null) { service.lastServiceTick = Long.MIN_VALUE; service.eligible = false; }
            if (stage >= 2 && lastStage < 2 && !replacedHunger) {
                previousFood = player.getHungerManager().getFoodLevel();
                previousSaturation = player.getHungerManager().getSaturationLevel();
                replacedHunger = true;
            }
            if (stage < 2 && replacedHunger) {
                player.getHungerManager().setFoodLevel(previousFood);
                player.getHungerManager().setSaturationLevel(previousSaturation);
                replacedHunger = false;
            }
            if (stage < 3) { overdrive = false; deactivated = false; statueMode = false; GuardianVillages.stopProtecting(player); }
            if (stage < 2 && ritualTicks > 0) {
                var village=GuardianVillages.claim(player);
                if(village!=null) GuardianRitual.cancel(player,village);
                else { GuardianRitual.stopEscort(player); ritualTicks=0; }
            }
            if (stage < 3 && coreId != null && ritualTicks == 0) GuardianCores.revert(player);
            if (stage < 0) { weatherTicks = 0; vision = true; GuardianCores.revert(player); }
            GuardianCores.updateSlot(player);
            previousPosition = null;
            lastStage = stage;
            sync();
        }
        if (stage < 0) {
            if (detached) GuardianCores.revert(player);
            GuardianBodyPower.flight(player, false); return;
        }
        if (!player.isAlive()) { GuardianDialogue.stop(player); return; }
        if (recoveryBlackout > 0) { recoveryBlackout--; if (recoveryBlackout == 0) sync(); }
        GuardianCores.tick(player);
        if (detached) {
            var village = GuardianVillages.claim(player);
            if (village != null && village.ritualTicks > 0) GuardianRitual.tick(player, village);
            syncChanged(player); return;
        }
        if (player.isSpectator()) statueMode = false;
        if (stage >= 2) {
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(0);
            if (!player.isCreative() && !player.isSpectator()) {
                double cost = 100.0 / 24000;
                if (previousPosition != null && previousPosition.squaredDistanceTo(player.getPos()) < 16)
                    cost += player.getPos().subtract(previousPosition).length() * (player.isSprinting() ? .008 : .003);
                if (player.getAbilities().flying) cost += stage == 3 ? .03 : .06;
                if (protecting && player.isSprinting()) cost *= 1.2;
                boolean pedestal = GuardianPedestal.standing(player);
                if (!pedestal && !deactivated) spend(cost);
                if (stage == 3 && energy <= 0 && !deactivated) {
                    deactivate(player);
                    systemMessage(player, "power_empty");
                }
                if (GuardianBodyPower.sunlit(player) && (pedestal || stage == 3)) charge(pedestal ? .025 : .0125);
                if (deactivated && !statueMode && energy >= 2) {
                    deactivated = false;
                    systemMessage(player, "power_online", (guardianName.isBlank() ? player.getName().getString() : guardianName).toUpperCase(Locale.ROOT));
                }
                if (energy <= 0) {
                    overdrive = false;
                    if (stage == 2 && !pedestal && player.age % 80 == 0 && player.getHealth() > 1)
                        player.setHealth(Math.max(1, player.getHealth() - 1));
                }
            } else deactivated = statueMode && !player.isSpectator();
            if (deactivated) {
                player.setYaw(frozenYaw); player.setHeadYaw(frozenYaw); player.setPitch(frozenPitch);
                player.bodyYaw = frozenYaw;
                player.setVelocity(0, player.getVelocity().y, 0);
            }
            GuardianBodyPower.flight(player, energy > 0 && !deactivated && !ritualRestrained() && !player.isSleeping());
        } else GuardianBodyPower.flight(player, false);
        previousPosition = player.getPos();
        GuardianVillages.tick(player);
        GuardianSleep.tick(player);
        if (stage == 3 && player.age % 20 == 0 && GuardianBodyPower.sunlit(player)) {
            var biome = player.getWorld().getBiome(player.getBlockPos()).value();
            if (biome.hasPrecipitation() && biome.getTemperature() > .15f && biome.getTemperature() < 1)
                weatherTicks = Math.min(72000, weatherTicks + 20);
        }
        syncChanged(player);
    }

    private void syncChanged(ServerPlayerEntity player) {
        int rounded = (int)(energy * 10);
        boolean flying = flying();
        if (flying != lastFlying || detached != lastDetached || protecting != lastProtecting || deactivated != lastDeactivated || player.age % 20 == 0
                && (rounded != lastEnergy || ritualTicks > 0 || weatherTicks != lastWeather)) {
            lastEnergy = rounded; lastWeather = weatherTicks;
            lastDetached = detached; lastProtecting = protecting; lastDeactivated = deactivated; lastFlying = flying; sync();
        }
    }

    @Override public void readFromNbt(NbtCompound nbt) {
        energy = nbt.contains("Energy") ? nbt.getDouble("Energy") : 100;
        if (!Double.isFinite(energy)) energy = 100;
        energy = MathHelper.clamp(energy, 0, 200);
        vision = !nbt.contains("Vision") || nbt.getBoolean("Vision");
        overdrive = nbt.getBoolean("Overdrive"); detached = nbt.getBoolean("Detached");
        deactivated = nbt.getBoolean("Deactivated"); frozenYaw = nbt.getFloat("FrozenYaw"); frozenPitch = nbt.getFloat("FrozenPitch");
        statueMode = nbt.getBoolean("StatueMode");
        recoveryBlackout = nbt.getInt("RecoveryBlackout");
        recoveryHintPending = nbt.getBoolean("RecoveryHintPending");
        traderRecoveryHint = nbt.getBoolean("TraderRecoveryHint");
        canGiveUp = nbt.getBoolean("CanGiveUp"); coreDeath = nbt.getBoolean("CoreDeath");
        flightGranted = nbt.getBoolean("FlightGranted");
        syncedFlying = owner.getWorld().isClient && nbt.getBoolean("Flying");
        protecting = nbt.getBoolean("Protecting"); guardianName = nbt.getString("Name");
        coreId = nbt.containsUuid("Core") ? nbt.getUuid("Core") : null;
        ritualTicks = nbt.getInt("RitualTicks"); weatherTicks = MathHelper.clamp(nbt.getInt("WeatherTicks"), 0, 72000);
        ritualGuideId = owner.getWorld().isClient && ritualTicks == 1 ? nbt.getInt("RitualGuide") : 0;
        previousFood = nbt.contains("PreviousFood") ? nbt.getInt("PreviousFood") : 20;
        previousSaturation = nbt.getFloat("PreviousSaturation"); replacedHunger = nbt.getBoolean("ReplacedHunger");
        foodRemainder = nbt.getDouble("FoodRemainder");
        soulboundStage = nbt.contains("SoulboundStage") ? MathHelper.clamp(nbt.getInt("SoulboundStage"), -2, 3) : -2;
        stageTwoHintShown = nbt.getBoolean("StageTwoHintShown");
        pedestalSleeping = nbt.getBoolean("PedestalSleeping"); daySleep = nbt.getBoolean("DaySleep");
    }
    @Override public void writeToNbt(NbtCompound nbt) {
        nbt.putDouble("Energy", energy); nbt.putBoolean("Vision", vision); nbt.putBoolean("Overdrive", overdrive);
        nbt.putBoolean("FlightGranted", flightGranted);
        nbt.putBoolean("Flying", flying());
        nbt.putBoolean("Detached", detached); nbt.putBoolean("Protecting", protecting); nbt.putString("Name", guardianName);
        nbt.putBoolean("Deactivated", deactivated); nbt.putFloat("FrozenYaw", frozenYaw); nbt.putFloat("FrozenPitch", frozenPitch);
        nbt.putBoolean("StatueMode", statueMode);
        nbt.putInt("RecoveryBlackout", recoveryBlackout);
        nbt.putBoolean("RecoveryHintPending", recoveryHintPending);
        nbt.putBoolean("TraderRecoveryHint", traderRecoveryHint);
        nbt.putBoolean("CanGiveUp", canGiveUp); nbt.putBoolean("CoreDeath", coreDeath);
        if (coreId != null) nbt.putUuid("Core", coreId);
        nbt.putInt("RitualTicks", ritualTicks); nbt.putInt("WeatherTicks", weatherTicks);
        nbt.putInt("RitualGuide", ritualGuideId);
        nbt.putInt("PreviousFood", previousFood); nbt.putFloat("PreviousSaturation", previousSaturation);
        nbt.putBoolean("ReplacedHunger", replacedHunger); nbt.putDouble("FoodRemainder", foodRemainder);
        nbt.putInt("SoulboundStage", soulboundStage);
        nbt.putBoolean("StageTwoHintShown", stageTwoHintShown);
        nbt.putBoolean("PedestalSleeping", pedestalSleeping); nbt.putBoolean("DaySleep", daySleep);
    }
}
