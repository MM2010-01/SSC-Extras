package sscextras.guardian;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/** Player-owned reserves and presentation; core identity and village service live in world saved data. */
public final class GuardianState implements AutoSyncedComponent, ServerTickingComponent {
    public final PlayerEntity owner;
    public double energy = 100;
    public boolean vision = true, overdrive, detached, protecting, flightGranted;
    public String guardianName = "";
    public UUID coreId;
    public int ritualTicks, weatherTicks, sleepTicks;
    public boolean pedestalSleeping, daySleep;
    public double foodRemainder;
    public int previousFood = 20;
    public float previousSaturation = 5;
    public boolean replacedHunger;
    public long magicInstinctAt = Long.MIN_VALUE;
    public int lastStage = -1;
    public Vec3d previousPosition;
    public Vec3d cuffPosition;
    public int soulboundStage = -2;
    public GuardianProtectBrain brain;
    public GuardianEscort escort;
    private int lastEnergy = -1;
    private boolean lastDetached, lastProtecting;

    public GuardianState(PlayerEntity owner) { this.owner = owner; }
    public double capacity() { return RuneboundGuardian.stage(owner) == 3 ? 200 : 100; }
    public double fraction() { return MathHelper.clamp(energy / capacity(), 0, 1); }
    public void charge(double amount) { energy = MathHelper.clamp(energy + amount, 0, capacity()); }
    public void spend(double amount) { charge(-amount * (overdrive ? 2 : 1)); }
    public void sync() { if (!owner.getWorld().isClient) GuardianComponents.KEY.sync(owner); }

    @Override public void serverTick() {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        GuardianEquipment.tick(player);
        GuardianOffering.standing(player);
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0 && lastStage < 0 && coreId == null && !flightGranted && !replacedHunger) return;
        if (stage != lastStage) {
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
            if (stage < 3) { overdrive = false; GuardianVillages.stopProtecting(player); }
            if (stage < 2 && ritualTicks > 0) {
                var village=GuardianVillages.claim(player);
                if(village!=null) GuardianRitual.cancel(player,village);
                else { GuardianRitual.stopEscort(player); ritualTicks=0; }
            }
            if (stage < 3 && coreId != null && ritualTicks == 0) GuardianCores.revert(player);
            if (stage < 0) { weatherTicks = 0; vision = true; GuardianCores.revert(player); }
            previousPosition = null;
            lastStage = stage;
            sync();
        }
        if (stage < 0) { GuardianBodyPower.flight(player, false); return; }
        if (!player.isAlive()) return;
        GuardianCores.tick(player);
        if (detached) {
            var village = GuardianVillages.claim(player);
            if (village != null && village.ritualTicks > 0) GuardianRitual.tick(player, village);
            syncChanged(player); return;
        }
        if (stage >= 2) {
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(0);
            if (!player.isCreative() && !player.isSpectator()) {
                double cost = 100.0 / 24000;
                if (previousPosition != null && previousPosition.squaredDistanceTo(player.getPos()) < 16)
                    cost += player.getPos().subtract(previousPosition).length() * (player.isSprinting() ? .008 : .003);
                if (player.getAbilities().flying) cost += stage == 3 ? .03 : .06;
                if (protecting && player.isSprinting()) cost *= 1.2;
                if (!GuardianPedestal.standing(player)) spend(cost);
                else if (GuardianBodyPower.sunlit(player)) charge(.025);
                if (energy <= 0) {
                    overdrive = false;
                    if (!GuardianPedestal.standing(player) && player.age % 80 == 0 && player.getHealth() > 1)
                        player.setHealth(Math.max(1, player.getHealth() - 1));
                }
            }
            GuardianBodyPower.flight(player, energy > 0 && ritualTicks == 0 && !player.isSleeping());
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
        if (detached != lastDetached || protecting != lastProtecting || player.age % 20 == 0
                && (rounded != lastEnergy || ritualTicks > 0 || weatherTicks > 0)) {
            lastEnergy = rounded; lastDetached = detached; lastProtecting = protecting; sync();
        }
    }

    @Override public void readFromNbt(NbtCompound nbt) {
        energy = nbt.contains("Energy") ? nbt.getDouble("Energy") : 100;
        if (!Double.isFinite(energy)) energy = 100;
        energy = MathHelper.clamp(energy, 0, 200);
        vision = !nbt.contains("Vision") || nbt.getBoolean("Vision");
        overdrive = nbt.getBoolean("Overdrive"); detached = nbt.getBoolean("Detached");
        flightGranted = nbt.getBoolean("FlightGranted");
        protecting = nbt.getBoolean("Protecting"); guardianName = nbt.getString("Name");
        coreId = nbt.containsUuid("Core") ? nbt.getUuid("Core") : null;
        ritualTicks = nbt.getInt("RitualTicks"); weatherTicks = MathHelper.clamp(nbt.getInt("WeatherTicks"), 0, 72000);
        previousFood = nbt.contains("PreviousFood") ? nbt.getInt("PreviousFood") : 20;
        previousSaturation = nbt.getFloat("PreviousSaturation"); replacedHunger = nbt.getBoolean("ReplacedHunger");
        foodRemainder = nbt.getDouble("FoodRemainder");
        soulboundStage = nbt.contains("SoulboundStage") ? MathHelper.clamp(nbt.getInt("SoulboundStage"), -2, 3) : -2;
        pedestalSleeping = nbt.getBoolean("PedestalSleeping"); daySleep = nbt.getBoolean("DaySleep");
    }
    @Override public void writeToNbt(NbtCompound nbt) {
        nbt.putDouble("Energy", energy); nbt.putBoolean("Vision", vision); nbt.putBoolean("Overdrive", overdrive);
        nbt.putBoolean("FlightGranted", flightGranted);
        nbt.putBoolean("Detached", detached); nbt.putBoolean("Protecting", protecting); nbt.putString("Name", guardianName);
        if (coreId != null) nbt.putUuid("Core", coreId);
        nbt.putInt("RitualTicks", ritualTicks); nbt.putInt("WeatherTicks", weatherTicks);
        nbt.putInt("PreviousFood", previousFood); nbt.putFloat("PreviousSaturation", previousSaturation);
        nbt.putBoolean("ReplacedHunger", replacedHunger); nbt.putDouble("FoodRemainder", foodRemainder);
        nbt.putInt("SoulboundStage", soulboundStage);
        nbt.putBoolean("PedestalSleeping", pedestalSleeping); nbt.putBoolean("DaySleep", daySleep);
    }
}
