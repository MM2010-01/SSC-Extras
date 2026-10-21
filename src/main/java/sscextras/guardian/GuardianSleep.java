package sscextras.guardian;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class GuardianSleep {
    private GuardianSleep() { }
    public static boolean daytime(PlayerEntity player) {
        return RuneboundGuardian.stage(player) >= 1 && RuneboundGuardian.state(player).daySleep && player.isSleeping();
    }
    public static boolean statue(PlayerEntity player) {
        return RuneboundGuardian.stage(player) >= 1 && RuneboundGuardian.state(player).pedestalSleeping && player.isSleeping();
    }
    public static boolean canSleepNow(PlayerEntity player) {
        return RuneboundGuardian.stage(player) < 2 || player.getWorld().isDay();
    }
    public static boolean pedestal(ServerPlayerEntity player, BlockPos corner) {
        var world = player.getServerWorld();
        PlayerEntity.SleepFailureReason failure = null;
        if (RuneboundGuardian.stage(player) < 1 || player.isSleeping() || !player.isAlive() || player.isSpectator()) return false;
        if (!world.getDimension().natural()) failure = PlayerEntity.SleepFailureReason.NOT_POSSIBLE_HERE;
        else if (!intactAndNear(player, corner)) failure = PlayerEntity.SleepFailureReason.TOO_FAR_AWAY;
        else if (!canSleepNow(player)) { player.sendMessage(Text.translatable("message.ssc-extras.guardian.day_sleep"), true); return false; }
        else if (player.hasVehicle() || player.hasPassengers() || !world.isSpaceEmpty(player,
                player.getDimensions(EntityPose.STANDING).getBoxAt(GuardianPedestal.position(corner)))) failure = PlayerEntity.SleepFailureReason.OBSTRUCTED;
        else if (!player.isCreative() && !world.getEntitiesByClass(HostileEntity.class, new Box(corner).expand(8, 5, 8),
                entity -> entity.isAngryAt(player)).isEmpty()) failure = PlayerEntity.SleepFailureReason.NOT_SAFE;
        if (failure != null) {
            if (failure.getMessage() != null) player.sendMessage(failure.getMessage(), true);
            return false;
        }
        var state = RuneboundGuardian.state(player);
        state.pedestalSleeping = true; state.daySleep = world.isDay(); state.sleepTicks = 0;
        player.sleep(corner); player.setPosition(GuardianPedestal.position(corner)); player.setOnGround(true);
        player.setPose(EntityPose.STANDING);
        player.incrementStat(Stats.SLEEP_IN_BED); Criteria.SLEPT_IN_BED.trigger(player);
        world.updateSleepingPlayers(); state.sync(); return true;
    }
    private static boolean intactAndNear(PlayerEntity player, BlockPos corner) {
        return GuardianPedestal.intact(player.getWorld(), corner) && player.squaredDistanceTo(GuardianPedestal.position(corner)) <= 16;
    }
    public static void tick(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        if (!player.isSleeping()) {
            if (state.pedestalSleeping || state.daySleep) { state.pedestalSleeping = state.daySleep = false; state.sleepTicks = 0; state.sync(); }
            return;
        }
        if (statue(player)) {
            var pos = player.getSleepingPosition().orElse(null);
            if (pos == null || !GuardianPedestal.intact(player.getWorld(), pos)) { player.wakeUp(); return; }
            player.setPose(EntityPose.STANDING); player.setPosition(GuardianPedestal.position(pos)); player.setOnGround(true);
        }
    }
    public static long skipTarget(ServerWorld world, long vanillaTarget) {
        if (world.isDay() && world.getPlayers().stream().anyMatch(GuardianSleep::daytime))
            return Math.floorDiv(world.getTimeOfDay(), 24000) * 24000 + 13000;
        return vanillaTarget;
    }
    public static void skipped(ServerWorld world, long target) {
        long amount = Math.max(0, target - world.getTimeOfDay());
        for (var player : world.getPlayers()) {
            var state = RuneboundGuardian.state(player);
            if (state.rechargeOnPedestal(amount)) state.sync();
        }
    }
}
