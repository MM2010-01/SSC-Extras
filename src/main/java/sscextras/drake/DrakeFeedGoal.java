package sscextras.drake;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import java.util.Comparator;
import java.util.EnumSet;

public final class DrakeFeedGoal extends Goal {
    private static final Item[] MEAT = {Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.RABBIT, Items.CHICKEN};
    private final PillagerEntity pillager;
    private PlayerEntity player;
    private DrakeOutpostOwnership.Claim feedingClaim;
    private ItemStack meal = ItemStack.EMPTY;
    private int nextSearch, started, nextBite, feedingTicks;
    private boolean thrown, handFeeding, announced, catalyst;

    public DrakeFeedGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static void treat(PillagerEntity pillager, PlayerEntity player, int hungerLost) {
        var food = new ItemStack(MEAT[pillager.getRandom().nextInt(MEAT.length)], 1 + (Math.max(0, hungerLost) + 2) / 3);
        throwFood(pillager, player, food);
        DrakeDialogue.say(player, "reward");
        player.sendMessage(net.minecraft.text.Text.translatable("message.ssc-extras.drake.battle_treat")
                .formatted(net.minecraft.util.Formatting.YELLOW), false);
    }

    private static void throwFood(PillagerEntity pillager, PlayerEntity player, ItemStack meal) {
        var food = new ItemEntity(pillager.getWorld(), pillager.getX(), pillager.getEyeY() + .25, pillager.getZ(), meal.copy());
        Vec3d delta = player.getPos().subtract(food.getPos());
        food.setVelocity(delta.x / 15, .4 + Math.max(0, delta.y) / 15, delta.z / 15);
        food.setOwner(player.getUuid()); food.setPickupDelay(10);
        pillager.getWorld().spawnEntity(food);
    }

    private boolean inStall(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return player.isAlive() && !player.isSpectator() && player.getWorld() == pillager.getWorld()
                && claim != null && claim.world.equals(pillager.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && !BondOfTheBeastCompat.hasOwner(player);
    }

    private static boolean earlyStage(PlayerEntity player) {
        int stage = EarthenDrake.stage(player);
        return stage == 0 || stage == 1;
    }

    private static int rawMeatSlot(PlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            var stack = player.getInventory().getStack(slot);
            for (var meat : MEAT) if (stack.isOf(meat)) return slot;
        }
        return -1;
    }

    private static boolean canFeedCatalyst(PlayerEntity player) {
        return (earlyStage(player) || DrakeLeashing.originalWithReins(player))
                && !player.isCreative() && !player.isSleeping() && !player.hasVehicle() && !player.hasPassengers()
                && !DrakeSoulbinding.ritualActive(player);
    }

    static int catalystDelay(PlayerEntity player) { return 1200 + player.getRandom().nextInt(2401); }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.age < nextSearch || DrakeFaction.fighting(pillager)) return false;
        nextSearch = pillager.age + 40;
        player = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, pillager.getBoundingBox().expand(12), candidate ->
                inStall(candidate) && !candidate.isUsingItem()
                && (candidate.getHungerManager().getFoodLevel() < 10 || canFeedCatalyst(candidate)
                    && (DrakeOutpostOwnership.claim(candidate).escapeCatalystDue
                        || DrakeOutpostOwnership.claim(candidate).nextCatalyst <= pillager.getWorld().getTime()))
                && DrakeOutpostOwnership.claim(candidate).nextMeal <= pillager.getWorld().getTime()).stream()
                .min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
        return player != null;
    }

    @Override public void start() {
        started = pillager.age; nextBite = started + 40; thrown = announced = false;
        feedingTicks = 0;
        var claim = DrakeOutpostOwnership.claim(player);
        feedingClaim = claim;
        catalyst = canFeedCatalyst(player) && (claim.escapeCatalystDue
                || player.getHungerManager().getFoodLevel() >= 10 && claim.nextCatalyst <= pillager.getWorld().getTime());
        if (catalyst) claim.nextCatalyst = pillager.getWorld().getTime() + catalystDelay(player);
        claim.nextMeal = pillager.getWorld().getTime() + 200;
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        int slot = rawMeatSlot(player);
        handFeeding = catalyst || earlyStage(player) && player.getHungerManager().getFoodLevel() <= 6 && slot >= 0
                && !player.isCreative() && !player.isSleeping() && !player.hasVehicle() && !player.hasPassengers();
        meal = catalyst ? new ItemStack(RegCustomItem.CATALYST) : handFeeding ? player.getInventory().getStack(slot).copyWithCount(1)
                : new ItemStack(MEAT[pillager.getRandom().nextInt(MEAT.length)], 1 + pillager.getRandom().nextInt(2));
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(meal);
    }

    @Override public boolean shouldContinue() {
        if (thrown || player == null || !inStall(player) || DrakeOutpostOwnership.claim(player) != feedingClaim
                || pillager.hasVehicle() || DrakeFaction.fighting(pillager)) return false;
        if (catalyst) return canFeedCatalyst(player) && pillager.age - started < 600;
        return handFeeding ? earlyStage(player) && player.canConsume(false) && !player.isCreative() && !player.isSleeping()
                && !player.hasVehicle() && !player.hasPassengers() && pillager.age - started < 600
                : player.getHungerManager().getFoodLevel() < 10 && pillager.age - started < 160;
    }

    @Override public void tick() {
        if (!shouldContinue()) return;
        pillager.getLookControl().lookAt(player, 30, 30);
        if (handFeeding) { feedByHand(); return; }
        if (pillager.squaredDistanceTo(player) > 25) { pillager.getNavigation().startMovingTo(player, .8); return; }
        pillager.getNavigation().stop();
        if (pillager.age - started < 20) return;
        throwFood(pillager, player, meal);
        DrakeDialogue.say(player, "feed");
        thrown = true;
    }

    @Override public boolean shouldRunEveryTick() { return catalyst; }

    static void chew(PlayerEntity player, ItemStack food, int ticks) {
        if (ticks < 8 || ticks % 4 != 0) return;
        var mouth = DrakeView.atHead(player, 1, player.getEyePos());
        ((ServerWorld)player.getWorld()).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, food),
                mouth.x, mouth.y - .15, mouth.z, 5, .12, .08, .12, .01);
        var random = player.getRandom();
        player.getWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EAT,
                net.minecraft.sound.SoundCategory.PLAYERS, .5f + .5f * random.nextInt(2),
                1 + (random.nextFloat() - random.nextFloat()) * .2f);
    }

    private void pauseFeeding() {
        nextBite = pillager.age + 40;
        feedingTicks = 0;
        if (catalyst) DrakeSoulbinding.role(pillager, 0);
    }

    private void feedByHand() {
        feedingClaim.nextMeal = pillager.getWorld().getTime() + 200;
        if (!feedingClaim.stall().contains(pillager.getPos()) || pillager.squaredDistanceTo(player) > 4
                || !pillager.getVisibilityCache().canSee(player)) {
            pauseFeeding();
            if (pillager.getNavigation().isIdle() || (pillager.age - started) % 10 == 0)
                pillager.getNavigation().startMovingAlong(pillager.getNavigation().findPathTo(player.getBlockPos(), 0), .8);
            return;
        }
        pillager.getNavigation().stop();
        if (player.isUsingItem()) { pauseFeeding(); return; }
        if (!announced) { DrakeDialogue.say(player, catalyst ? "catalyst_feed" : "hand_feed"); announced = true; }
        if (catalyst) {
            DrakeSoulbinding.role(pillager, DrakeSoulbinding.FEEDING);
            chew(player, meal, ++feedingTicks);
            if (feedingTicks < DrakeSoulbinding.FEED_TICKS) return;
        } else if (pillager.age < nextBite) return;
        nextBite = pillager.age + 40;
        int slot = catalyst ? -1 : rawMeatSlot(player);
        var food = slot < 0 ? meal.copyWithCount(1) : player.getInventory().getStack(slot);
        meal = food.copyWithCount(1);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(meal);
        var remaining = food.finishUsing(player.getWorld(), player);
        if (slot >= 0) {
            player.getInventory().setStack(slot, remaining);
            player.getInventory().markDirty();
        }
        if (catalyst) {
            thrown = true;
            feedingClaim.escapeCatalystDue = false;
            feedingClaim.nextCatalyst = pillager.getWorld().getTime() + catalystDelay(player);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        else DrakeInstinct.handFed(player);
        pillager.swingHand(Hand.MAIN_HAND);
        ((ServerWorld)player.getWorld()).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, meal),
                player.getX(), player.getEyeY() - .15, player.getZ(), 6, .15, .1, .15, .02);
    }

    @Override public void stop() {
        if (handFeeding && player != null) DrakeOutpostOwnership.get(player.getServer()).markDirty();
        if (catalyst && DrakeSoulbinding.role(pillager) == DrakeSoulbinding.FEEDING) DrakeSoulbinding.role(pillager, 0);
        player = null; feedingClaim = null; meal = ItemStack.EMPTY; handFeeding = catalyst = false;
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        pillager.getNavigation().stop();
    }
}
