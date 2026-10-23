package sscextras.guardian;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** An empty vessel has no player consciousness and keeps the body's inventory until occupied again. */
public final class GuardianShellEntity extends PathAwareEntity {
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WORK = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WEATHERED = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> AWAKE = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CORE_PRESENT = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> GRABBING = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> EQUIPMENT = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> FLYING = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> RESERVE = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public UUID formerOwner;
    public NbtList inventory = new NbtList(), accessories = new NbtList();
    public ItemStack core = ItemStack.EMPTY;
    public double energy;
    public int weatherTicks;
    private BlockPos home;
    private boolean sentry, placedStatue;
    private boolean depleted, recharging;
    private int sunlightTicks;
    private net.minecraft.util.math.Vec3d previousPosition;
    final GuardianMobNavigation routes;
    UUID offender;
    long alertUntil, recaptureAfter;
    GuardianCaptureEvent capture;

    public GuardianShellEntity(EntityType<? extends GuardianShellEntity> type, World world) {
        super(type, world); setPersistent(); setAiDisabled(true); setStepHeight(1);
        getNavigation().setCanSwim(true);
        routes = new GuardianMobNavigation(this);
    }
    @Override protected void initGoals() { goalSelector.add(0, new GuardianVillageGoal(this)); }
    public static DefaultAttributeContainer.Builder attributes() {
        return createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 26).add(EntityAttributes.GENERIC_ARMOR, 15)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, .27)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, .3);
    }
    @Override protected void initDataTracker() {
        super.initDataTracker(); dataTracker.startTracking(STAGE, 3); dataTracker.startTracking(WORK, 0); dataTracker.startTracking(WEATHERED, false);
        dataTracker.startTracking(AWAKE, false); dataTracker.startTracking(CORE_PRESENT, false);
        dataTracker.startTracking(GRABBING, false); dataTracker.startTracking(EQUIPMENT, 0);
        dataTracker.startTracking(FLYING, false); dataTracker.startTracking(RESERVE, 0f);
    }
    public int stage() { return dataTracker.get(STAGE); }
    public void stage(int value) { dataTracker.set(STAGE, value); }
    public int work() { return dataTracker.get(WORK); }
    public void work(int value) { dataTracker.set(WORK, value); }
    public boolean weathered() { return dataTracker.get(WEATHERED); }
    public boolean awake() { return dataTracker.get(AWAKE); }
    void awake(boolean value) { dataTracker.set(AWAKE, value && !core.isEmpty()); }
    public boolean corePresent() { return dataTracker.get(CORE_PRESENT); }
    public boolean grabbing() { return dataTracker.get(GRABBING); }
    void grabbing(boolean value) { dataTracker.set(GRABBING, value); }
    public int equipment() { return dataTracker.get(EQUIPMENT); }
    public BlockPos home() { return home; }
    public boolean sentry() { return sentry; }
    public boolean resident() { return home != null; }
    public boolean flying() { return dataTracker.get(FLYING); }
    public double energyReserve() { return getWorld().isClient ? dataTracker.get(RESERVE) : energy; }
    public boolean powered() { return energy > 0 && !depleted; }
    boolean recharging() { return recharging; }
    @Override protected float getOffGroundSpeed() {
        // Use the native flight controller's speed: ordinary mob air acceleration made flight crawl.
        return flying() ? getMovementSpeed() * .25f : super.getOffGroundSpeed();
    }
    void flightMode(boolean enabled) {
        enabled &= resident() && powered() && !core.isEmpty();
        if (enabled != flying()) {
            navigation.stop();
            navigation = enabled ? routes.air : routes.ground;
            moveControl = enabled ? routes.flight : routes.walking;
            dataTracker.set(FLYING, enabled);
        }
        setNoGravity(enabled); if (enabled) fallDistance = 0;
    }
    @Override public ItemStack getPickBlockStack() {
        if (!resident()) return ItemStack.EMPTY;
        return (sentry ? (weathered() ? GuardianSpawnEgg.WEATHERED_SENTRY : GuardianSpawnEgg.SENTRY)
                : (weathered() ? GuardianSpawnEgg.WEATHERED_GUARDIAN : GuardianSpawnEgg.GUARDIAN)).getDefaultStack();
    }
    public boolean statue() { return stage() == 3 && work() == 0 && !awake(); }
    public boolean placedStatue() { return placedStatue; }
    /** Crate placement preserves a resting resident until its next village alarm. */
    public void holdPlacedStatue() {
        if (!resident() || !statue()) return;
        placedStatue = true; routes.stop(true);
    }
    public void villageResident(BlockPos pedestal, boolean statue, boolean weathered) {
        home = pedestal.toImmutable(); sentry = statue; weatherTicks = weathered ? 72000 : 0;
        energy = GuardianEnergy.PERMANENT_CAPACITY; accessories = GuardianEquipment.bodySet();
        core = statue ? ItemStack.EMPTY : GuardianCores.INACTIVE_CORE.getDefaultStack();
        setAiDisabled(false); syncAppearance();
        setCustomName(net.minecraft.text.Text.translatable(statue ? "entity.ssc-extras.guardian_sentry" : "entity.ssc-extras.village_guardian"));
    }
    private void syncAppearance() {
        dataTracker.set(WEATHERED, weatherTicks >= 72000); dataTracker.set(CORE_PRESENT, !core.isEmpty());
        dataTracker.set(EQUIPMENT, GuardianEquipment.bodyVisuals(accessories));
        dataTracker.set(RESERVE, (float)energy);
    }
    public void alarm(ServerPlayerEntity player) {
        if (!resident() || !powered() || core.isEmpty() || recharging || !isAlive() || work() != 0 || GuardianCores.id(core) != null || capture != null) return;
        placedStatue = false; offender = player.getUuid(); alertUntil = getWorld().getTime() + 1200; awake(true);
    }
    void releaseHome() {
        if (home != null && getWorld().getBlockEntity(home) instanceof GuardianOffering offering) offering.releaseResident(getUuid());
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInsideWall() { return work() == 0 && super.isInsideWall(); }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public void tick() {
        if (!getWorld().isClient && resident()) {
            if (core.isEmpty()) {
                if (capture != null) capture.finish();
                if (flying()) routes.stop(true);
            }
            boolean atHome = squaredDistanceTo(GuardianPedestal.position(home)) < .1 && GuardianPedestal.intact(getWorld(), home);
            if (energy < returnReserve()) recharging = true;
            else if (atHome && energy >= 100) recharging = false;
            if (recharging) {
                if (capture != null) capture.finish();
                offender = null;
            }
            awake(powered() && !placedStatue && GuardianCores.id(core) == null && (capture != null || offender != null && getWorld().getTime() <= alertUntil
                    && !recharging || !atHome || !sentry && !getWorld().isDay() && !recharging));
        }
        super.tick();
        if (!getWorld().isClient && resident() && isAlive()) tickEnergy();
        if (!getWorld().isClient && !core.isEmpty()) {
            GuardianCores.observe(core, this, "shell");
            var owner = GuardianCores.owner(getServer(), core);
            if (owner != null) GuardianCores.occupy(owner, this, core);
        }
        if (!getWorld().isClient) syncAppearance();
    }
    private double returnReserve() {
        // Budget the homeward trip at walking pace, allowing for prolonged sunlight and a short detour.
        double ticks = getPos().distanceTo(GuardianPedestal.position(home)) / .1;
        double sunlight = GuardianBodyPower.sunlit(this)
                ? GuardianEnergy.sunlight(GuardianEnergy.PERMANENT_CAPACITY, GuardianEnergy.SUNLIGHT_ESCALATION_TICKS + 1) : 0;
        return 20 + ticks * (GuardianEnergy.activity(3, .1, false, true) + sunlight);
    }
    private void tickEnergy() {
        boolean pedestal = GuardianPedestal.intact(getWorld(), home)
                && squaredDistanceTo(GuardianPedestal.position(home)) < .1;
        if (pedestal && !awake()) energy += GuardianEnergy.PEDESTAL_RECHARGE;
        else if (awake()) {
            double distance = previousPosition != null && squaredDistanceTo(previousPosition) < 16 ? getPos().distanceTo(previousPosition) : 0;
            energy -= GuardianEnergy.activity(3, distance, false, flying());
        }
        sunlightTicks = !pedestal && GuardianBodyPower.sunlit(this)
                ? Math.min(sunlightTicks + 1, GuardianEnergy.SUNLIGHT_ESCALATION_TICKS + 1) : 0;
        energy = GuardianEnergy.clamp(energy - GuardianEnergy.sunlight(GuardianEnergy.PERMANENT_CAPACITY, sunlightTicks), GuardianEnergy.PERMANENT_CAPACITY);
        if (energy <= 0 && !depleted) {
            depleted = true; if (capture != null) capture.finish();
            routes.stop(true); awake(false); offender = null;
        } else if (depleted && energy >= GuardianEnergy.RESTART) depleted = false;
        previousPosition = getPos();
        if (flying()) fallDistance = 0;
    }
    @Override protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        var held = player.getStackInHand(hand);
        if (held.isOf(GuardianCores.CORE) && core.isEmpty() && work() == 0) {
            if (!getWorld().isClient) {
                if (!GuardianCores.valid(getServer(), held)) return ActionResult.FAIL;
                core = held.copyWithCount(1); held.decrement(1); GuardianCores.observe(core, this, "shell");
                var owner = GuardianCores.owner(getServer(), core);
                if (owner != null) GuardianCores.occupy(owner, this, core);
            }
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }
    @Override public void onDeath(DamageSource source) {
        if (!getWorld().isClient) {
            if (capture != null) capture.finish();
            releaseHome();
            for (int i = 0; i < inventory.size(); i++) dropStack(ItemStack.fromNbt(inventory.getCompound(i)));
            for (int i = 0; i < accessories.size(); i++) dropStack(ItemStack.fromNbt(accessories.getCompound(i).getCompound("Stack")));
            if (!core.isEmpty()) dropStack(core);
            inventory.clear(); accessories.clear(); core = ItemStack.EMPTY;
        }
        super.onDeath(source);
    }
    @Override public void remove(RemovalReason reason) {
        if (!getWorld().isClient && capture != null) capture.finish();
        super.remove(reason);
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (formerOwner != null) nbt.putUuid("FormerOwner", formerOwner);
        nbt.put("BodyItems", inventory); nbt.put("BodyAccessories", accessories); nbt.put("Core", core.writeNbt(new NbtCompound()));
        nbt.putInt("GuardianStage", stage()); nbt.putInt("Work", work()); nbt.putInt("Weather", weatherTicks); nbt.putDouble("Energy", energy);
        if (home != null) nbt.putLong("VillageHome", home.asLong());
        nbt.putBoolean("VillageSentry", sentry);
        nbt.putBoolean("VillageAwake", awake()); nbt.putBoolean("VillagePlacedStatue", placedStatue);
        nbt.putBoolean("VillageDepleted", depleted); nbt.putBoolean("VillageRecharging", recharging);
        nbt.putInt("VillageSunlightTicks", sunlightTicks);
        if (offender != null) nbt.putUuid("VillageOffender", offender);
        nbt.putLong("VillageAlertUntil", alertUntil); nbt.putLong("RecaptureAfter", recaptureAfter);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        formerOwner = nbt.containsUuid("FormerOwner") ? nbt.getUuid("FormerOwner") : null;
        inventory = nbt.getList("BodyItems", 10); accessories = nbt.getList("BodyAccessories", 10); core = ItemStack.fromNbt(nbt.getCompound("Core"));
        stage(nbt.contains("GuardianStage") ? nbt.getInt("GuardianStage") : 3); work(nbt.getInt("Work"));
        weatherTicks = nbt.getInt("Weather"); energy = nbt.getDouble("Energy");
        home = nbt.contains("VillageHome") ? BlockPos.fromLong(nbt.getLong("VillageHome")) : null;
        if (resident()) energy = GuardianEnergy.clamp(energy, GuardianEnergy.PERMANENT_CAPACITY);
        depleted = nbt.getBoolean("VillageDepleted") || resident() && energy <= 0;
        recharging = nbt.getBoolean("VillageRecharging") || resident() && energy < 20;
        sunlightTicks = net.minecraft.util.math.MathHelper.clamp(nbt.getInt("VillageSunlightTicks"), 0, GuardianEnergy.SUNLIGHT_ESCALATION_TICKS + 1);
        previousPosition = null; flightMode(false);
        sentry = nbt.getBoolean("VillageSentry"); offender = nbt.containsUuid("VillageOffender") ? nbt.getUuid("VillageOffender") : null;
        placedStatue = resident() && nbt.getBoolean("VillagePlacedStatue"); awake(!placedStatue && nbt.getBoolean("VillageAwake"));
        alertUntil = nbt.getLong("VillageAlertUntil"); recaptureAfter = nbt.getLong("RecaptureAfter");
        setAiDisabled(!resident()); syncAppearance();
    }
}
