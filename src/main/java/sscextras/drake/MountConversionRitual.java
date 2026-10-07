package sscextras.drake;

import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import sscextras.events.EventAssignments;
import sscextras.events.NpcEvent;
import sscextras.rituals.RitualProgress;
import sscextras.rituals.RitualStage;
import sscextras.rituals.RitualStageRunner;
import java.util.List;

public final class MountConversionRitual extends NpcEvent {
    private final MountMerchantEntity merchant;
    private final ServerPlayerEntity player;
    private final EventAssignments assignments;
    private final RitualStageRunner<MountConversionRitual> stages;
    private LeashEscortEvent escort;
    private int feeding;
    private boolean atGate;

    public MountConversionRitual(MountMerchantEntity merchant, ServerPlayerEntity player, RitualProgress progress, boolean pretend) {
        this.merchant = merchant; this.player = player;
        assignments = DrakeOutpostOwnership.get(player.getServer()).assignments();
        stages = new RitualStageRunner<>(List.of(
                RitualStage.afterAndWhen("answer", pretend ? 60 : 0, ritual -> ritual.merchant.squaredDistanceTo(ritual.player) <= 4, MountConversionRitual::harness),
                RitualStage.afterAndWhen("pen", 0, ritual -> ritual.merchant.pen().contains(ritual.player.getPos())
                        && ritual.player.getBoundingBox().minZ >= ritual.merchant.gate().getZ() + 1, MountConversionRitual::tie),
                new RitualStage<>("convert", 0, ritual -> EarthenDrake.stage(ritual.player) >= 2 && !ritual.transforming(),
                        RitualStage.Completion.WHEN, 72000, RitualStage.Fallback.pause(), RitualStage.Fallback.cancel(), ritual -> {
                            MountMerchantEntity.say(ritual.player, ritual.merchant.capturedSeller() ? "captured_ready" : "ready"); return true;
                        })), progress);
    }
    private boolean transforming() { return TransformManager.getPlayerTransformData(player).isTransforming; }
    private boolean harness() {
        for (var item : List.of(DrakeEquipment.REINS, DrakeEquipment.SADDLE))
            if (DrakeEquipment.equipped(player, item).isEmpty() && !DrakeEquipment.tryEquip(player, item.getDefaultStack(), false)) return false;
        if (sscextras.collar.CollarSlots.includingLegacy(player).stream().noneMatch(slot -> slot.stack().getItem() instanceof sscextras.collar.CollarItem)
                && sscextras.collar.Collars.equipOwnedDrake(player, DrakeMountNames.choose(player.getRandom())))
            MountMerchantEntity.say(player, "collar");
        merchant.setSprinting(false);
        MountMerchantEntity.say(player, merchant.capturedSeller() ? "captured_enter" : "enter");
        return DrakeLeashing.attachPillager(player, merchant);
    }
    private boolean tie() {
        if (!player.getWorld().getBlockState(merchant.tie()).isIn(net.minecraft.registry.tag.BlockTags.FENCES)) return false;
        if (!DrakeLeashing.attachPillager(player, LeashKnotEntity.getOrCreate(player.getWorld(), merchant.tie()))) return false;
        if (escort != null) { escort.handoff(); escort = null; }
        merchant.getNavigation().stop(); merchant.registerSeller(); return true;
    }
    public boolean tick() {
        if (!player.isAlive() || player.getWorld() != merchant.getWorld() || !merchant.isAlive() || !MountMerchantForms.convertible(player)) return false;
        if (!assignments.acquire(this, List.of(player.getUuid(), merchant.getUuid()))) return true;
        if (status() == Status.PAUSED) { resume(); stages.resume(); }
        String stage = stages.current().id();
        if (stage.equals("answer") && merchant.squaredDistanceTo(player) > 4) {
            merchant.setSprinting(merchant.capturedSeller());
            if (merchant.age % 10 == 0 || merchant.getNavigation().isIdle())
                merchant.getNavigation().startMovingTo(player, merchant.capturedSeller() ? 1.2 : .7);
        }
        if (stage.equals("pen")) {
            if (DrakeLeashing.holder(player) != merchant && !DrakeLeashing.attachPillager(player, merchant)) return false;
            var navigation = (DrakeStableNavigation)merchant.getNavigation(); navigation.open(merchant.gate());
            if (escort == null) escort = LeashEscortEvent.start(merchant, player, merchant.inside(), this);
            var approach = Vec3d.ofBottomCenter(merchant.gate()).add(.5, 0, -.5);
            if (Math.abs(merchant.getX() - approach.x) < .2 && Math.abs(merchant.getZ() - approach.z) < .45 && merchant.squaredDistanceTo(player) < 16
                    || merchant.pen().contains(merchant.getPos()) && merchant.pen().contains(player.getPos())) atGate = true;
            if (escort == null || !escort.move(atGate ? merchant.inside() : approach, .65, atGate || merchant.squaredDistanceTo(approach) < 2.25)) return false;
        }
        if (stage.equals("convert")) {
            if (!merchant.pen().contains(player.getPos()) || !DrakeLeashing.attached(player)) return false;
            ((DrakeStableNavigation)merchant.getNavigation()).close(merchant.gate());
            if (EarthenDrake.stage(player) < 2 && !transforming()) convert();
            else clearFeeding();
        }
        var result = stages.tick(this, 1, true);
        if (result == RitualStageRunner.Result.COMPLETE) finish();
        return result != RitualStageRunner.Result.CANCELLED;
    }
    private void convert() {
        for (var drake : merchant.stock()) {
            drake.getLookControl().lookAt(player, 30, 30);
            if (drake.squaredDistanceTo(player) > 6.25) {
                if (merchant.age % 20 == 0) drake.getNavigation().startMovingTo(player, .6);
            } else if (merchant.age % 20 == 0) { drake.swingHand(Hand.MAIN_HAND); drake.tryAttack(player); }
        }
        merchant.getLookControl().lookAt(player, 30, 30);
        if (merchant.squaredDistanceTo(player) > 4 || !merchant.getVisibilityCache().canSee(player)) {
            if (merchant.getNavigation().isIdle() || merchant.age % 10 == 0) {
                var positions = new java.util.HashSet<net.minecraft.util.math.BlockPos>();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    var pos = player.getBlockPos().add(dx, 0, dz);
                    var point = Vec3d.ofBottomCenter(pos);
                    if (merchant.pen().contains(point) && player.squaredDistanceTo(point) <= 1.25 * 1.25) positions.add(pos);
                }
                merchant.getNavigation().startMovingAlong(merchant.getNavigation().findPathTo(positions, 0), .6);
            }
            clearFeeding(); return;
        }
        merchant.getNavigation().stop();
        if (player.isUsingItem()) { clearFeeding(); return; }
        var catalyst = new ItemStack(RegCustomItem.CATALYST);
        ((DrakeFaction.EquipmentDisplay)(Object)merchant).sscExtras$showEquipment(catalyst);
        DrakeSoulbinding.role(merchant, DrakeSoulbinding.FEEDING);
        DrakeFeedGoal.chew(player, catalyst, ++feeding);
        if (feeding >= DrakeSoulbinding.FEED_TICKS) {
            catalyst.finishUsing(player.getWorld(), player); merchant.swingHand(Hand.MAIN_HAND);
            clearFeeding();
        }
    }
    private void clearFeeding() {
        feeding = 0; DrakeSoulbinding.role(merchant, 0);
        ((DrakeFaction.EquipmentDisplay)(Object)merchant).sscExtras$showEquipment(ItemStack.EMPTY);
    }
    @Override protected void release() {
        if (escort != null) { escort.handoff(); escort = null; }
        clearFeeding(); assignments.release(this); merchant.setSprinting(false); merchant.getNavigation().stop();
        for (var drake : merchant.stock()) { drake.setTarget(null); drake.getNavigation().stop(); }
    }
}
