package sscextras.drake;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import java.util.Comparator;
import java.util.EnumSet;

final class FeralForageGoal extends Goal {
    private final MobEntity mob;
    private final ServerPlayerEntity player;
    private ItemEntity food;
    private int nextSearch, nextPath, deadline;

    FeralForageGoal(MobEntity mob, ServerPlayerEntity player) {
        this.mob = mob;
        this.player = player;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    static void encourageRoaming(MobEntity mob) {
        for (var goal : ((FeralMobBrain.Bridge)mob).sscExtras$feralGoals().getGoals()) {
            if (goal.getGoal() instanceof WanderAroundGoal wander) {
                wander.setChance(20);
                wander.ignoreChanceOnce();
            }
        }
    }

    static boolean edible(MobEntity mob, ItemStack stack) {
        if (!stack.isFood() || stack.isOf(BeastizationCatalyst.ITEM) || stack.isOf(SentientCatalyst.ITEM)) return false;
        if (mob.getType() == DrakeStable.DRAKE) return DrakeFeralization.rawFood(stack);
        if (mob instanceof AxolotlEntity) return stack.isOf(Items.COD) || stack.isOf(Items.SALMON) || stack.isOf(Items.TROPICAL_FISH);
        if (mob instanceof CatEntity || mob instanceof OcelotEntity) return stack.isOf(Items.COD) || stack.isOf(Items.SALMON);
        if (mob instanceof WolfEntity) return stack.getItem().getFoodComponent().isMeat();
        if (mob instanceof FoxEntity) return stack.getItem().getFoodComponent().isMeat()
                || stack.isOf(Items.SWEET_BERRIES) || stack.isOf(Items.GLOW_BERRIES);
        if (mob instanceof BatEntity) return false;
        return !(mob instanceof AnimalEntity animal) || animal.isBreedingItem(stack);
    }

    static void eatHeldFood(MobEntity mob, ServerPlayerEntity player) {
        if (!player.canConsume(false) || player.isUsingItem() || player.isSleeping()) return;
        for (var hand : Hand.values()) if (edible(mob, player.getStackInHand(hand))) {
            player.setCurrentHand(hand);
            return;
        }
    }

    private boolean hungry() {
        return player.canConsume(false) && !player.isUsingItem() && !player.isSleeping()
                && mob.getTarget() == null && !mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET);
    }

    private boolean reachable(ItemEntity item) {
        var holder = DrakeLeashing.holder(player);
        return item.isAlive() && edible(mob, item.getStack()) && mob.isInWalkTargetRange(item.getBlockPos())
                && (holder == null || holder.squaredDistanceTo(item) <= 25);
    }

    @Override public boolean canStart() {
        if (mob.age < nextSearch || !hungry()) return false;
        nextSearch = mob.age + 20;
        food = mob.getWorld().getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(8), this::reachable).stream()
                .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        return food != null && (player.squaredDistanceTo(food) < 2.25 || mob.getNavigation().findPathTo(food, 0) != null);
    }

    @Override public void start() { deadline = mob.age + 200; nextPath = 0; }
    @Override public boolean shouldRunEveryTick() { return true; }
    @Override public boolean shouldContinue() {
        return food != null && hungry() && reachable(food) && mob.age < deadline && player.squaredDistanceTo(food) < 100;
    }

    @Override public void tick() {
        if (food == null || !food.isAlive() || !hungry()) return;
        mob.getLookControl().lookAt(food);
        if (player.squaredDistanceTo(food) < 2.25) {
            mob.getNavigation().stop();
            var meal = food;
            var bite = meal.getStack().copyWithCount(1);
            var remainder = bite.finishUsing(player.getWorld(), player);
            meal.getStack().decrement(1);
            if (meal.getStack().isEmpty()) meal.discard();
            if (!remainder.isEmpty()) player.dropItem(remainder, false);
            food = null;
            nextSearch = mob.age + 32;
        } else if (mob.age >= nextPath) {
            nextPath = mob.age + 10;
            if (!mob.getNavigation().startMovingTo(food, .8)) food = null;
        }
    }

    @Override public void stop() { food = null; mob.getNavigation().stop(); }
}
