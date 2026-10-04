package sscextras.drake;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.onixary.shapeShifterCurseFabric.util.AttackEntityDataTracker;
import net.onixary.shapeShifterCurseFabric.util.ModTags;
import java.util.Comparator;
import java.util.EnumSet;

public final class DrakeFaction {
    private DrakeFaction() { }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!blocksAttack(player, entity)) return ActionResult.PASS;
            AttackEntityDataTracker.lastAttackPillagerTimeMap.remove(player.getUuid());
            return ActionResult.FAIL;
        });
    }

    public static boolean member(Entity entity) {
        return entity instanceof LivingEntity living && (living.getGroup() == EntityGroup.ILLAGER
                || living.getType().isIn(ModTags.Illager_Tag));
    }

    public static boolean harnessed(PlayerEntity player) {
        return !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty()
                && !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
    }

    public static boolean friendly(PlayerEntity player) {
        if (harnessed(player)) return true;
        return EarthenDrake.stage(player) == 3 && player.getWorld().getTime()
                - AttackEntityDataTracker.lastAttackPillagerTimeMap.getOrDefault(player.getUuid(), -1200L) >= 1200;
    }

    public static boolean blocksAttack(PlayerEntity player, Entity target) {
        return member(target) && harnessed(player);
    }

    public static boolean blocksDamage(Entity target, DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof PlayerEntity player && blocksAttack(player, target)
                || target instanceof PlayerEntity wearer && member(attacker) && harnessed(wearer);
    }

    public static void damaged(Entity target, DamageSource source) {
        if (!target.getWorld().isClient && source.getAttacker() instanceof PlayerEntity player
                && EarthenDrake.stage(player) == 3 && member(target))
            AttackEntityDataTracker.onPlayerAttack(player, target, player.getWorld());
    }

    public static DrakeAccessoryItem missingPiece(PlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return null;
        boolean reins = !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty();
        boolean saddle = !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
        if (reins == saddle) return null;
        var missing = reins ? DrakeEquipment.SADDLE : DrakeEquipment.REINS;
        return DrakeEquipment.stacks(player, missing).stream().anyMatch(ItemStack::isEmpty) ? missing : null;
    }

    public static final class EquipGoal extends Goal {
        private final PillagerEntity pillager;
        private PlayerEntity wearer;
        private int nextSearch;

        public EquipGoal(PillagerEntity pillager) {
            this.pillager = pillager;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            if (pillager.age < nextSearch) return false;
            nextSearch = pillager.age + 20;
            wearer = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, pillager.getBoundingBox().expand(12),
                    player -> missingPiece(player) != null && pillager.getVisibilityCache().canSee(player))
                    .stream().min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
            return wearer != null;
        }

        @Override public boolean shouldContinue() {
            return wearer != null && missingPiece(wearer) != null && pillager.squaredDistanceTo(wearer) <= 256;
        }

        @Override public void start() {
            pillager.clearActiveItem();
            pillager.setCharging(false);
            pillager.getNavigation().startMovingTo(wearer, 1.1);
        }

        @Override public void tick() {
            pillager.getLookControl().lookAt(wearer, 30, 30);
            pillager.getNavigation().startMovingTo(wearer, 1.1);
            if (pillager.squaredDistanceTo(wearer) > 4 || !pillager.getVisibilityCache().canSee(wearer)) return;
            var missing = missingPiece(wearer);
            if (missing != null && DrakeEquipment.tryEquip(wearer, new ItemStack(missing), false)) {
                if (pillager.getTarget() == wearer) pillager.setTarget(null);
                pillager.getNavigation().stop();
            }
        }

        @Override public void stop() {
            wearer = null;
            pillager.getNavigation().stop();
        }
    }
}
