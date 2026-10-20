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
import java.util.UUID;

/** An empty vessel has no player consciousness and keeps the body's inventory until occupied again. */
public final class GuardianShellEntity extends PathAwareEntity {
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WORK = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WEATHERED = DataTracker.registerData(GuardianShellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public UUID formerOwner;
    public NbtList inventory = new NbtList(), accessories = new NbtList();
    public ItemStack core = ItemStack.EMPTY;
    public double energy;
    public int weatherTicks;

    public GuardianShellEntity(EntityType<? extends GuardianShellEntity> type, World world) { super(type, world); setPersistent(); setAiDisabled(true); }
    public static DefaultAttributeContainer.Builder attributes() {
        return createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 26).add(EntityAttributes.GENERIC_ARMOR, 15)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0);
    }
    @Override protected void initDataTracker() {
        super.initDataTracker(); dataTracker.startTracking(STAGE, 3); dataTracker.startTracking(WORK, 0); dataTracker.startTracking(WEATHERED, false);
    }
    public int stage() { return dataTracker.get(STAGE); }
    public void stage(int value) { dataTracker.set(STAGE, value); }
    public int work() { return dataTracker.get(WORK); }
    public void work(int value) { dataTracker.set(WORK, value); }
    public boolean weathered() { return dataTracker.get(WEATHERED); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInsideWall() { return work() == 0 && super.isInsideWall(); }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && !core.isEmpty()) {
            GuardianCores.observe(core, this, "shell");
            var owner = GuardianCores.owner(getServer(), core);
            if (owner != null) GuardianCores.occupy(owner, this, core);
        }
        if (!getWorld().isClient) dataTracker.set(WEATHERED, weatherTicks >= 72000);
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
            for (int i = 0; i < inventory.size(); i++) dropStack(ItemStack.fromNbt(inventory.getCompound(i)));
            for (int i = 0; i < accessories.size(); i++) dropStack(ItemStack.fromNbt(accessories.getCompound(i).getCompound("Stack")));
            if (!core.isEmpty()) dropStack(core);
            inventory.clear(); accessories.clear(); core = ItemStack.EMPTY;
        }
        super.onDeath(source);
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (formerOwner != null) nbt.putUuid("FormerOwner", formerOwner);
        nbt.put("BodyItems", inventory); nbt.put("BodyAccessories", accessories); nbt.put("Core", core.writeNbt(new NbtCompound()));
        nbt.putInt("GuardianStage", stage()); nbt.putInt("Work", work()); nbt.putInt("Weather", weatherTicks); nbt.putDouble("Energy", energy);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        formerOwner = nbt.containsUuid("FormerOwner") ? nbt.getUuid("FormerOwner") : null;
        inventory = nbt.getList("BodyItems", 10); accessories = nbt.getList("BodyAccessories", 10); core = ItemStack.fromNbt(nbt.getCompound("Core"));
        stage(nbt.contains("GuardianStage") ? nbt.getInt("GuardianStage") : 3); work(nbt.getInt("Work"));
        weatherTicks = nbt.getInt("Weather"); energy = nbt.getDouble("Energy"); dataTracker.set(WEATHERED, weatherTicks >= 72000);
    }
}
