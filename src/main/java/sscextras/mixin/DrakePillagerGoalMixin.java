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

@Mixin(PillagerEntity.class)
public abstract class DrakePillagerGoalMixin extends IllagerEntity implements DrakeFaction.EquipmentDisplay {
    @Unique private static final TrackedData<ItemStack> SSC_EXTRAS_OFFERED_GEAR = DataTracker.registerData(
            PillagerEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    @Unique private DrakeFaction.DefendGoal sscExtras$defendGoal;
    protected DrakePillagerGoalMixin(EntityType<? extends IllagerEntity> type, World world) { super(type, world); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$gearDisplay(CallbackInfo ci) { dataTracker.startTracking(SSC_EXTRAS_OFFERED_GEAR, ItemStack.EMPTY); }

    public void sscExtras$showEquipment(ItemStack stack) { dataTracker.set(SSC_EXTRAS_OFFERED_GEAR, stack); }
    public void sscExtras$defend(PlayerEntity player, LivingEntity enemy) { sscExtras$defendGoal.offer(player, enemy); }

    @Override protected void mobTick() {
        super.mobTick();
        DrakeRiding.tickPillager((PillagerEntity)(Object)this);
    }

    @Override public ItemStack getMainHandStack() {
        ItemStack offered = dataTracker.get(SSC_EXTRAS_OFFERED_GEAR);
        return offered.isEmpty() ? super.getMainHandStack() : offered;
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void sscExtras$completeDrakeHarness(CallbackInfo ci) {
        goalSelector.add(1, new DrakeFaction.EquipGoal((PillagerEntity)(Object)this));
        sscExtras$defendGoal = new DrakeFaction.DefendGoal((PillagerEntity)(Object)this);
        targetSelector.add(0, sscExtras$defendGoal);
    }
}
