package sscextras.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.world.World;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFaction;
import sscextras.drake.DrakeRiding;
import sscextras.drake.DrakeCaptureGoal;

@Mixin(PillagerEntity.class)
public abstract class DrakePillagerGoalMixin extends IllagerEntity implements DrakeFaction.EquipmentDisplay, DrakeCaptureGoal.Captor, sscextras.drake.DrakeBattleGoal.Rider {
    @Unique private static final TrackedData<ItemStack> SSC_EXTRAS_OFFERED_GEAR = DataTracker.registerData(
            PillagerEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    @Unique private DrakeFaction.DefendGoal sscExtras$defendGoal;
    @Unique private DrakeCaptureGoal sscExtras$captureGoal;
    @Unique private DrakeFaction.EquipGoal sscExtras$equipGoal;
    @Unique private sscextras.drake.DrakeBattleGoal sscExtras$battleGoal;
    protected DrakePillagerGoalMixin(EntityType<? extends IllagerEntity> type, World world) { super(type, world); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$gearDisplay(CallbackInfo ci) { dataTracker.startTracking(SSC_EXTRAS_OFFERED_GEAR, ItemStack.EMPTY); }

    public void sscExtras$showEquipment(ItemStack stack) {
        if (!ItemStack.areEqual(dataTracker.get(SSC_EXTRAS_OFFERED_GEAR), stack)) dataTracker.set(SSC_EXTRAS_OFFERED_GEAR, stack);
    }
    public void sscExtras$defend(PlayerEntity player, LivingEntity enemy) { sscExtras$defendGoal.offer(player, enemy); }
    public DrakeCaptureGoal sscExtras$captureGoal() { return sscExtras$captureGoal; }
    public sscextras.drake.DrakeBattleGoal sscExtras$battleGoal() { return sscExtras$battleGoal; }
    public PlayerEntity sscExtras$recruiting() { return sscExtras$equipGoal == null ? null : sscExtras$equipGoal.wearer(); }

    @Inject(method = "shoot", at = @At("RETURN"))
    private void sscExtras$battleShot(LivingEntity target, ItemStack crossbow, net.minecraft.entity.projectile.ProjectileEntity projectile,
            float spray, CallbackInfo ci) {
        if (sscExtras$battleGoal != null) sscExtras$battleGoal.shot(target);
    }

    @Override public boolean canTarget(LivingEntity target) {
        return !(target instanceof PlayerEntity player && DrakeFaction.missingPiece(player) != null
                && getWorld().getTime() - net.onixary.shapeShifterCurseFabric.util.AttackEntityDataTracker
                .lastAttackPillagerTimeMap.getOrDefault(player.getUuid(), -1200L) >= 1200) && super.canTarget(target);
    }

    @Override protected net.minecraft.entity.ai.pathing.EntityNavigation createNavigation(World world) {
        return new sscextras.drake.DrakeStableNavigation(this, world);
    }

    @Override protected void mobTick() {
        if (getTarget() instanceof PlayerEntity player && DrakeFaction.friendly(player)) {
            setTarget(null); clearActiveItem(); ((PillagerEntity)(Object)this).setCharging(false);
        }
        super.mobTick();
        DrakeRiding.tickPillager((PillagerEntity)(Object)this);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void sscExtras$saveGuardHome(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) {
        if (navigation instanceof sscextras.drake.DrakeStableNavigation stableNavigation) stableNavigation.writeHome(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void sscExtras$loadGuardHome(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) {
        if (navigation instanceof sscextras.drake.DrakeStableNavigation stableNavigation) stableNavigation.readHome(nbt);
    }

    @Override public ItemStack getMainHandStack() {
        if (sscextras.drake.DrakeSoulbinding.role((PillagerEntity)(Object)this) == sscextras.drake.DrakeSoulbinding.CHANTING) return ItemStack.EMPTY;
        ItemStack offered = dataTracker.get(SSC_EXTRAS_OFFERED_GEAR);
        return offered.isEmpty() ? super.getMainHandStack() : offered;
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void sscExtras$completeDrakeHarness(CallbackInfo ci) {
        goalSelector.add(-1, new sscextras.drake.DrakeRitualGoal((PillagerEntity)(Object)this));
        sscExtras$battleGoal = new sscextras.drake.DrakeBattleGoal((PillagerEntity)(Object)this);
        goalSelector.add(0, sscExtras$battleGoal);
        sscExtras$equipGoal = new DrakeFaction.EquipGoal((PillagerEntity)(Object)this);
        goalSelector.add(1, sscExtras$equipGoal);
        sscExtras$captureGoal = new DrakeCaptureGoal((PillagerEntity)(Object)this);
        goalSelector.add(1, sscExtras$captureGoal);
        goalSelector.add(1, new sscextras.drake.DrakeFeedGoal((PillagerEntity)(Object)this));
        goalSelector.add(2, new sscextras.drake.DrakeGuardGoal((PillagerEntity)(Object)this, true));
        goalSelector.add(3, new sscextras.drake.DrakeStableRepairGoal((PillagerEntity)(Object)this));
        goalSelector.add(4, new sscextras.drake.DrakePetGoal((PillagerEntity)(Object)this));
        goalSelector.add(5, new sscextras.drake.DrakeGuardGoal((PillagerEntity)(Object)this));
        sscExtras$defendGoal = new DrakeFaction.DefendGoal((PillagerEntity)(Object)this);
        targetSelector.add(0, sscExtras$defendGoal);
    }
}
