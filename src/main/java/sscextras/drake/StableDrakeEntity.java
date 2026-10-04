package sscextras.drake;

import mod.azure.azurelib.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.util.AzureLibUtil;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
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
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import sscextras.CreatureInstinct;
import sscextras.SscExtrasConfig;

public final class StableDrakeEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Boolean> SADDLED = DataTracker.registerData(StableDrakeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> REINED = DataTracker.registerData(StableDrakeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> VANILLA_SADDLE = DataTracker.registerData(StableDrakeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CHESTED = DataTracker.registerData(StableDrakeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);
    private int contactCooldown;
    private ItemStack saddle = ItemStack.EMPTY, reins = ItemStack.EMPTY;
    private ItemStack chest = ItemStack.EMPTY;
    private boolean naturalSaddle = true, naturalReins = true;
    private net.minecraft.util.math.BlockBox homeStable;
    private int homeStall;
    private String homeWorld = "";

    public StableDrakeEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setStepHeight(1);
        saddle = new ItemStack(DrakeEquipment.SADDLE);
        reins = new ItemStack(DrakeEquipment.REINS);
        dataTracker.set(SADDLED, true);
        dataTracker.set(REINED, true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 35.2)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, .25).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, .5);
    }

    public void setStableHome(DrakeStablePiece stable, int stall) {
        homeStable = stable.getBoundingBox(); homeStall = stall; homeWorld = getWorld().getRegistryKey().getValue().toString();
    }

    public int homeStall() { return homeStall; }

    public boolean belongsTo(DrakeStablePiece stable) {
        if (homeStable == null && stable.getBoundingBox().contains(getBlockPos()) && hasCustomName()) {
            if (getCustomName().getString().equals(stable.firstName())) setStableHome(stable, 0);
            else if (getCustomName().getString().equals(stable.secondName())) setStableHome(stable, 1);
        }
        return homeStable != null && homeStable.equals(stable.getBoundingBox())
                && homeWorld.equals(getWorld().getRegistryKey().getValue().toString());
    }

    private boolean riderControls() {
        return DrakeBattleGoal.assigned(this)
                || getFirstPassenger() instanceof net.minecraft.entity.LivingEntity rider && DrakeRiding.canControl(this, rider);
    }

    @Override protected net.minecraft.entity.ai.pathing.EntityNavigation createNavigation(World world) {
        return new DrakeStableNavigation(this, world);
    }

    @Override protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(SADDLED, false);
        dataTracker.startTracking(REINED, false);
        dataTracker.startTracking(VANILLA_SADDLE, false);
        dataTracker.startTracking(CHESTED, false);
    }

    @Override protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canStart() { return !riderControls() && super.canStart(); }
            @Override public boolean shouldContinue() { return !riderControls() && super.shouldContinue(); }
        });
        goalSelector.add(5, new WanderAroundFarGoal(this, .65) {
            @Override public boolean canStart() { return !riderControls() && super.canStart(); }
            @Override public boolean shouldContinue() { return !riderControls() && super.shouldContinue(); }
        });
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8));
        goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 20, true, false,
                entity -> entity instanceof PlayerEntity player && eligible(player) && !isTeammate(player) && !hasPassenger(player)));
    }

    public static boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && (CreatureInstinct.matchesForm(player, EarthenDrake.CURSE)
                || FormAbilityManager.getForm(player) == RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
    }

    @Override public boolean tryAttack(Entity target) {
        if (target instanceof PlayerEntity player && eligible(player)) {
            if (!getWorld().isClient && contactCooldown <= 0) {
                if (FormAbilityManager.getForm(player) == RegPlayerForms.ORIGINAL_BEFORE_ENABLE) {
                    if (!TransformManager.getPlayerTransformData(player).isTransforming)
                        TransformManager.handleDirectTransform(player, RegPlayerForms.ORIGINAL_SHIFTER, false);
                } else CreatureInstinct.add(player, EarthenDrake.CURSE);
                contactCooldown = 100;
            }
            return true;
        }
        // Friendly to completed drakes, including after their transformation finishes mid-attack.
        if (target instanceof PlayerEntity player && EarthenDrake.stage(player) == 3) return false;
        return super.tryAttack(target);
    }

    @Override public void tickMovement() {
        if (contactCooldown > 0) contactCooldown--;
        if (riderControls()) {
            if (!(getFirstPassenger() instanceof net.minecraft.entity.mob.PillagerEntity)) getNavigation().stop();
            setTarget(null);
        }
        if (getTarget() instanceof PlayerEntity player && !eligible(player)) setTarget(null);
        super.tickMovement();
    }

    @Override public void travel(Vec3d movement) {
        if (DrakeRiding.input(this) != null) setMovementSpeed((float)getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        super.travel(DrakeRiding.movement(this, movement));
    }

    public void riderJump() { jump(); }
    public boolean isSaddled() { return dataTracker.get(SADDLED); }
    public boolean hasReins() { return dataTracker.get(REINED); }
    public boolean hasVanillaSaddle() { return dataTracker.get(VANILLA_SADDLE); }
    public boolean hasChest() { return dataTracker.get(CHESTED); }
    public ItemStack chest() { return chest; }

    @Override public boolean canBeLeashedBy(PlayerEntity player) { return !isLeashed(); }

    @Override protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player.isSpectator()) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(DrakeEquipment.RIDERS_CHEST) && !hasChest()) {
            if (!getWorld().isClient) {
                chest = stack.copyWithCount(1);
                dataTracker.set(CHESTED, true);
                if (!player.isCreative()) stack.decrement(1);
                playSound(SoundEvents.ENTITY_DONKEY_CHEST, 1, 1);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (hasChest() && player.isSneaking()) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) RiderChestInventory.open(serverPlayer, this);
            return ActionResult.success(getWorld().isClient);
        }
        if (DrakeEquipment.isSaddle(stack) && !isSaddled() || stack.isOf(DrakeEquipment.REINS) && !hasReins()) {
            if (!getWorld().isClient) {
                if (DrakeEquipment.isSaddle(stack)) {
                    saddle = stack.copyWithCount(1); naturalSaddle = false; dataTracker.set(SADDLED, true);
                    dataTracker.set(VANILLA_SADDLE, stack.isOf(net.minecraft.item.Items.SADDLE));
                } else {
                    reins = stack.copyWithCount(1); naturalReins = false; dataTracker.set(REINED, true);
                }
                if (!player.isCreative()) stack.decrement(1);
                playSound(SoundEvents.ENTITY_HORSE_SADDLE, 1, 1);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (isSaddled() && !player.isSneaking() && !hasPassengers() && !player.hasVehicle()) {
            if (!getWorld().isClient) player.startRiding(this);
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    @Override protected boolean canAddPassenger(Entity entity) {
        return isSaddled() && (entity instanceof PlayerEntity || hasReins() && entity instanceof net.minecraft.entity.mob.PillagerEntity) && !hasPassengers();
    }
    @Override public double getMountedHeightOffset() { return 1.15; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public boolean isDisallowedInPeaceful() { return false; }

    @Override public boolean damage(DamageSource source, float amount) {
        return super.damage(source, source.isIn(DamageTypeTags.BYPASSES_ARMOR) ? amount : amount * .25f);
    }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_RAVAGER_DEATH; }

    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("DrakeSaddle", saddle.writeNbt(new NbtCompound()));
        nbt.put("DrakeReins", reins.writeNbt(new NbtCompound()));
        nbt.put("DrakeChest", chest.writeNbt(new NbtCompound()));
        nbt.putInt("CurseContactCooldown", contactCooldown);
        nbt.putBoolean("DrakeNaturalSaddle", naturalSaddle);
        nbt.putBoolean("DrakeNaturalReins", naturalReins);
        if (homeStable != null) {
            nbt.putIntArray("StableHome", new int[]{homeStable.getMinX(), homeStable.getMinY(), homeStable.getMinZ(),
                    homeStable.getMaxX(), homeStable.getMaxY(), homeStable.getMaxZ()});
            nbt.putInt("HomeStall", homeStall); nbt.putString("HomeWorld", homeWorld);
        }
    }

    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        saddle = ItemStack.fromNbt(nbt.getCompound("DrakeSaddle"));
        reins = ItemStack.fromNbt(nbt.getCompound("DrakeReins"));
        chest = ItemStack.fromNbt(nbt.getCompound("DrakeChest"));
        if (!chest.isOf(DrakeEquipment.RIDERS_CHEST)) chest = ItemStack.EMPTY;
        dataTracker.set(CHESTED, !chest.isEmpty());
        naturalSaddle = nbt.getBoolean("DrakeNaturalSaddle");
        naturalReins = nbt.getBoolean("DrakeNaturalReins");
        if (!nbt.contains("DrakeNaturalSaddle") && saddle.isEmpty()) {
            saddle = new ItemStack(DrakeEquipment.SADDLE); naturalSaddle = true;
        }
        if (!nbt.contains("DrakeNaturalReins") && reins.isEmpty()) {
            reins = new ItemStack(DrakeEquipment.REINS); naturalReins = true;
        }
        dataTracker.set(SADDLED, DrakeEquipment.isSaddle(saddle));
        dataTracker.set(VANILLA_SADDLE, saddle.isOf(net.minecraft.item.Items.SADDLE));
        dataTracker.set(REINED, reins.isOf(DrakeEquipment.REINS));
        contactCooldown = nbt.getInt("CurseContactCooldown");
        int[] box = nbt.getIntArray("StableHome");
        if (box.length == 6) {
            homeStable = new net.minecraft.util.math.BlockBox(box[0], box[1], box[2], box[3], box[4], box[5]);
            homeStall = Math.max(0, Math.min(1, nbt.getInt("HomeStall")));
            homeWorld = nbt.getString("HomeWorld");
        }
    }

    @Override protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        if (!saddle.isEmpty() && (!naturalSaddle || allowDrops && dropsNaturalGear("stableDrakeSaddleDropChance", lootingMultiplier))) dropStack(saddle);
        if (!reins.isEmpty() && (!naturalReins || allowDrops && dropsNaturalGear("stableDrakeReinsDropChance", lootingMultiplier))) dropStack(reins);
        if (!chest.isEmpty()) dropStack(chest);
        chest = ItemStack.EMPTY;
        dataTracker.set(CHESTED, false);
        saddle = reins = ItemStack.EMPTY;
    }

    private boolean dropsNaturalGear(String key, int lootingMultiplier) {
        float chance = SscExtrasConfig.lootChance(key);
        return chance > 0 && random.nextFloat() < chance + lootingMultiplier * .01f;
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
