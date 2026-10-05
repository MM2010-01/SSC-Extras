package sscextras.drake;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.util.AttackEntityDataTracker;
import net.onixary.shapeShifterCurseFabric.util.ModTags;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.SscExtrasGameRules;
import sscextras.collar.Collars;
import java.util.Comparator;
import java.util.EnumSet;

public final class DrakeFaction {
    public interface EquipmentDisplay {
        void sscExtras$showEquipment(ItemStack stack);
        void sscExtras$defend(PlayerEntity player, LivingEntity enemy);
        PlayerEntity sscExtras$recruiting();
    }
    private DrakeFaction() { }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!blocksAttack(player, entity)) return ActionResult.PASS;
            AttackEntityDataTracker.lastAttackPillagerTimeMap.remove(player.getUuid());
            return ActionResult.FAIL;
        });
    }

    public static boolean member(Entity entity) {
        return entity instanceof StableDrakeEntity || entity instanceof LivingEntity living && (living.getGroup() == EntityGroup.ILLAGER
                || living.getType().isIn(ModTags.Illager_Tag));
    }

    public static boolean harnessed(PlayerEntity player) {
        return !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty()
                && !DrakeEquipment.saddle(player).isEmpty();
    }

    public static boolean friendly(PlayerEntity player) {
        if (harnessed(player)) return true;
        return EarthenDrake.stage(player) == 3 && player.getWorld().getTime()
                - AttackEntityDataTracker.lastAttackPillagerTimeMap.getOrDefault(player.getUuid(), -1200L) >= 1200;
    }

    public static boolean blocksAttack(PlayerEntity player, Entity target) {
        return member(target) && harnessed(player);
    }

    public static boolean fighting(PillagerEntity pillager) {
        var target = pillager.getTarget();
        return target != null && target.isAlive() && !pillager.isTeammate(target)
                && !(target instanceof PlayerEntity player && friendly(player));
    }

    public static boolean blocksDamage(Entity target, DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof PlayerEntity player && blocksAttack(player, target)
                || target instanceof PlayerEntity wearer && member(attacker) && harnessed(wearer);
    }

    public static void damaged(Entity target, DamageSource source) {
        if (target.getWorld().isClient) return;
        if (source.getAttacker() instanceof PlayerEntity player
                && EarthenDrake.stage(player) == 3 && member(target))
            AttackEntityDataTracker.onPlayerAttack(player, target, player.getWorld());
        if (source.getAttacker() instanceof PlayerEntity player && target instanceof LivingEntity enemy)
            defend(player, enemy);
        if (target instanceof PlayerEntity player && source.getAttacker() instanceof LivingEntity enemy)
            defend(player, enemy);
        if (target instanceof LivingEntity mount && (mount instanceof StableDrakeEntity
                || mount instanceof PlayerEntity player && DrakeOutpostOwnership.claim(player) != null)
                && source.getAttacker() instanceof LivingEntity enemy) DrakeBattleGoal.alert(mount, enemy);
    }

    private static boolean cursedHarness(PlayerEntity player) {
        return !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty()
                && !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
    }

    private static boolean canDefend(PillagerEntity pillager, PlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.getWorld() == pillager.getWorld()
                && (!pillager.hasVehicle() || pillager.getVehicle() == player)
                && (pillager.getVehicle() == player && DrakeRiding.canCarryPillager(player)
                || cursedHarness(player) && pillager.squaredDistanceTo(player) <= 32 * 32);
    }

    private static void defend(PlayerEntity player, LivingEntity enemy) {
        if (enemy == player || !enemy.isAlive() || member(enemy) || player.isTeammate(enemy)) return;
        if (cursedHarness(player)) {
            for (var pillager : player.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(16),
                    candidate -> candidate.isAlive() && !candidate.isAiDisabled() && candidate.squaredDistanceTo(player) <= 16 * 16))
                ((EquipmentDisplay)pillager).sscExtras$defend(player, enemy);
        } else if (player.getFirstPassenger() instanceof PillagerEntity pillager)
            ((EquipmentDisplay)pillager).sscExtras$defend(player, enemy);
    }

    public static final class DefendGoal extends TrackTargetGoal {
        private final PillagerEntity pillager;
        private PlayerEntity player;
        private boolean defending;
        private long offeredAt;
        private int nextSearch;
        private final TargetPredicate predicate = TargetPredicate.createAttackable().setBaseMaxDistance(32);

        public DefendGoal(PillagerEntity pillager) {
            super(pillager, true);
            this.pillager = pillager;
            setControls(EnumSet.of(Control.TARGET));
        }

        public void offer(PlayerEntity player, LivingEntity enemy) {
            if (!canDefend(pillager, player) || member(enemy) || !pillager.canTarget(enemy)
                    || pillager.isTeammate(enemy)) return;
            this.player = player;
            target = enemy;
            offeredAt = pillager.getWorld().getTime();
            if (defending) pillager.setTarget(enemy);
        }

        @Override public boolean canStart() {
            if (!canDefend(pillager, player) || pillager.getWorld().getTime() - offeredAt > 100
                    || target == null || !target.isAlive()) {
                player = null; target = null;
                if (pillager.age < nextSearch) return false;
                nextSearch = pillager.age + 20;
                for (var enemy : pillager.getWorld().getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,
                        pillager.getBoundingBox().expand(32), enemy -> enemy.isAlive() && !member(enemy)
                                && enemy.getTarget() instanceof PlayerEntity victim && canDefend(pillager, victim)
                                && pillager.getVisibilityCache().canSee(enemy))) {
                    offer((PlayerEntity)enemy.getTarget(), enemy);
                    if (target != null) break;
                }
                if (target == null) return false;
            }
            return canTrack(target, predicate);
        }

        @Override public void start() { defending = true; pillager.setTarget(target); super.start(); }

        @Override public boolean shouldContinue() {
            return canDefend(pillager, player) && target != null && target.isAlive()
                    && !member(target) && !player.isTeammate(target) && super.shouldContinue();
        }

        @Override public void stop() { defending = false; player = null; super.stop(); }
    }

    public static DrakeAccessoryItem missingPiece(PlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return null;
        boolean reins = !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty();
        boolean saddle = !DrakeEquipment.saddle(player).isEmpty();
        boolean upgrade = BlindingRein.needsUpgrade(player);
        if (reins && saddle && !upgrade) return null;
        boolean cursedPiece = reins || !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
        var pending = sscextras.CreatureInstinct.getTarget(player);
        boolean pendingDrake = FormAbilityManager.getForm(player).getIndex() < 0
                && (pending != null && pending.getGroup() == EarthenDrake.GROUP
                || player.hasStatusEffect(EarthenDrake.CURSE) || player.hasStatusEffect(EarthenDrake.POTION_EFFECT));
        var rules = player.getWorld().getGameRules();
        boolean drakeRecruiting = rules.getBoolean(SscExtrasGameRules.PILLAGER_RECRUIT_DRAKE)
                && (EarthenDrake.stage(player) >= 0 || cursedPiece || pendingDrake);
        boolean uncursedRecruiting = rules.getBoolean(SscExtrasGameRules.PILLAGER_RECRUIT_UNCURSED)
                && (!Collars.isCursed(player) || (cursedPiece || pendingDrake) && FormAbilityManager.getForm(player).getIndex() < 0);
        if (!drakeRecruiting && !uncursedRecruiting) return null;
        if (upgrade) return DrakeEquipment.BLINDING_REIN;
        if (!reins && DrakeEquipment.stacks(player, DrakeEquipment.REINS).stream().anyMatch(ItemStack::isEmpty))
            return DrakeEquipment.REINS;
        if (!saddle && DrakeEquipment.stacks(player, DrakeEquipment.SADDLE).stream().anyMatch(ItemStack::isEmpty))
            return DrakeEquipment.SADDLE;
        return null;
    }

    public static final class EquipGoal extends Goal {
        private final PillagerEntity pillager;
        private PlayerEntity wearer;
        private int nextSearch;
        private int nextEquip;
        private boolean equipping;

        public EquipGoal(PillagerEntity pillager) {
            this.pillager = pillager;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            if (pillager.hasVehicle() || pillager.age < nextSearch || fighting(pillager) || DrakeSoulbinding.attendee(pillager) != null) return false;
            var rules = pillager.getWorld().getGameRules();
            if (!rules.getBoolean(SscExtrasGameRules.PILLAGER_RECRUIT_DRAKE)
                    && !rules.getBoolean(SscExtrasGameRules.PILLAGER_RECRUIT_UNCURSED)) return false;
            nextSearch = pillager.age + 20;
            wearer = pillager.getWorld().getPlayers().stream()
                    .filter(player -> missingPiece(player) != null && inRange(pillager, player) && isNearest(player))
                    .min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
            return wearer != null;
        }

        @Override public boolean shouldContinue() {
            return wearer != null && (!pillager.hasVehicle() || DrakeBattleGoal.of(pillager).pursuing(wearer))
                    && !fighting(pillager) && missingPiece(wearer) != null
                    && wearer.getWorld() == pillager.getWorld() && inRange(pillager, wearer);
        }

        public PlayerEntity wearer() { return equipping ? wearer : null; }

        private static boolean inRange(PillagerEntity pillager, PlayerEntity player) {
            if (pillager.squaredDistanceTo(player) > (DrakeCaptureGoal.RANGE * 2 + 24) * (DrakeCaptureGoal.RANGE * 2 + 24)) return false;
            var stable = ((DrakeStableNavigation)pillager.getNavigation()).stable();
            return stable != null ? DrakeCaptureGoal.near(stable, player.getPos()) : pillager.squaredDistanceTo(player) <= 144;
        }

        private boolean isNearest(PlayerEntity player) {
            var candidates = pillager.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(DrakeCaptureGoal.RANGE * 2 + 24),
                    candidate -> candidate.isAlive() && !(candidate instanceof DrakeVisitorEntity) && !candidate.isAiDisabled()
                            && (!candidate.hasVehicle() || DrakeBattleGoal.of(candidate).pursuing(player))
                            && inRange(candidate, player));
            for (var candidate : candidates)
                if (((EquipmentDisplay)candidate).sscExtras$recruiting() == player) return candidate == pillager;
            return candidates.stream().filter(candidate -> ((EquipmentDisplay)candidate).sscExtras$recruiting() == null)
                    .sorted(Comparator.<PillagerEntity>comparingDouble(player::squaredDistanceTo).thenComparingInt(Entity::getId))
                    .filter(candidate -> ((DrakeStableNavigation)candidate.getNavigation()).reaches(player.getBlockPos()))
                    .findFirst().orElse(null) == pillager;
        }

        @Override public void start() {
            equipping = true;
            nextEquip = pillager.age;
            pillager.clearActiveItem();
            pillager.setCharging(false);
            pillager.setTarget(null);
            var missing = missingPiece(wearer);
            if (missing != null) ((EquipmentDisplay)pillager).sscExtras$showEquipment(new ItemStack(missing));
            pillager.getNavigation().startMovingTo(wearer, 1.1);
        }

        @Override public void tick() {
            pillager.getLookControl().lookAt(wearer, 30, 30);
            if (DrakeBattleGoal.of(pillager).pursue(wearer)) return;
            if (!DrakeBattleGoal.of(pillager).movePursuit(wearer, null, 0, false)) pillager.getNavigation().startMovingTo(wearer, 1.1);
            if (pillager.age < nextEquip || !DrakeRiding.inInteractionReach(pillager, wearer) || !pillager.getVisibilityCache().canSee(wearer)) return;
            var missing = missingPiece(wearer);
            if (missing != null && (missing == DrakeEquipment.BLINDING_REIN ? BlindingRein.upgrade(wearer)
                    : DrakeEquipment.tryEquip(wearer, new ItemStack(missing), false))) {
                wearer.sendMessage(Text.translatable("message.ssc-extras.drake.pillager_equipped",
                        new ItemStack(missing).getName()).formatted(Formatting.YELLOW), false);
                var next = missingPiece(wearer);
                ((EquipmentDisplay)pillager).sscExtras$showEquipment(next == null ? ItemStack.EMPTY : new ItemStack(next));
                nextEquip = pillager.age + 10;
                if (pillager.getTarget() == wearer) pillager.setTarget(null);
                pillager.getNavigation().stop();
            }
        }

        @Override public void stop() {
            if (wearer != null && missingPiece(wearer) == null && DrakeCaptureGoal.eligible(wearer))
                ((DrakeCaptureGoal.Captor)pillager).sscExtras$captureGoal().recallAfterDismount();
            else DrakeBattleGoal.of(pillager).stopPursuit();
            equipping = false; wearer = null;
            ((EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
            pillager.getNavigation().stop();
        }
    }
}
