package sscextras.drake;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import java.util.Comparator;
import java.util.EnumSet;

public final class DrakeFeedGoal extends Goal {
    private static final Item[] MEAT = {Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.RABBIT, Items.CHICKEN};
    private final PillagerEntity pillager;
    private PlayerEntity player;
    private ItemStack meal = ItemStack.EMPTY;
    private int nextSearch, started;
    private boolean thrown;

    public DrakeFeedGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static void treat(PillagerEntity pillager, PlayerEntity player, int hungerLost) {
        var food = new ItemStack(MEAT[pillager.getRandom().nextInt(MEAT.length)], 1 + (Math.max(0, hungerLost) + 2) / 3);
        throwFood(pillager, player, food);
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

    private boolean hungry(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return player.isAlive() && !player.isSpectator() && player.getHungerManager().getFoodLevel() < 10
                && claim != null && claim.world.equals(pillager.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && !BondOfTheBeastCompat.hasOwner(player);
    }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.age < nextSearch) return false;
        nextSearch = pillager.age + 40;
        player = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, pillager.getBoundingBox().expand(12), candidate ->
                hungry(candidate) && DrakeOutpostOwnership.claim(candidate).nextMeal <= pillager.getWorld().getTime()).stream()
                .min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
        return player != null;
    }

    @Override public void start() {
        started = pillager.age; thrown = false;
        var claim = DrakeOutpostOwnership.claim(player);
        claim.nextMeal = pillager.getWorld().getTime() + 200;
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        meal = new ItemStack(MEAT[pillager.getRandom().nextInt(MEAT.length)], 1 + pillager.getRandom().nextInt(2));
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(meal);
    }

    @Override public boolean shouldContinue() { return !thrown && player != null && hungry(player) && pillager.age - started < 160; }

    @Override public void tick() {
        pillager.getLookControl().lookAt(player, 30, 30);
        if (pillager.squaredDistanceTo(player) > 25) { pillager.getNavigation().startMovingTo(player, .8); return; }
        pillager.getNavigation().stop();
        if (pillager.age - started < 20) return;
        throwFood(pillager, player, meal);
        thrown = true;
    }

    @Override public void stop() {
        player = null; meal = ItemStack.EMPTY;
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        pillager.getNavigation().stop();
    }
}
