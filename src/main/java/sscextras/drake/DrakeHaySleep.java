package sscextras.drake;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DrakeHaySleep {
    private DrakeHaySleep() { }

    public static BlockPos patch(World world, BlockPos pos) {
        for (int x = 0; x >= -1; x--) for (int z = 0; z >= -1; z--) {
            var corner = pos.add(x, 0, z);
            if (world.getBlockState(corner).isOf(Blocks.HAY_BLOCK) && world.getBlockState(corner.east()).isOf(Blocks.HAY_BLOCK)
                    && world.getBlockState(corner.south()).isOf(Blocks.HAY_BLOCK) && world.getBlockState(corner.east().south()).isOf(Blocks.HAY_BLOCK))
                return corner;
        }
        return null;
    }

    public static boolean isHayBed(LivingEntity entity, BlockPos pos) {
        return entity instanceof PlayerEntity player && EarthenDrake.stage(player) >= 0 && pos != null
                && entity.getWorld().getBlockState(pos).isOf(Blocks.HAY_BLOCK) && patch(entity.getWorld(), pos) != null;
    }

    public static Vec3d position(BlockPos corner) { return new Vec3d(corner.getX() + 1, corner.getY() + 1.01, corner.getZ() + 1); }

    public static boolean sleep(ServerPlayerEntity player, BlockPos clicked) {
        var world = player.getServerWorld();
        var corner = patch(world, clicked);
        if (EarthenDrake.stage(player) < 0 || !player.isAlive() || player.isSpectator() || player.isSleeping()) return false;
        if (corner == null) { player.sendMessage(Text.translatable("message.ssc-extras.drake.hay_patch"), true); return false; }
        PlayerEntity.SleepFailureReason failure = null;
        if (!world.getDimension().natural()) failure = PlayerEntity.SleepFailureReason.NOT_POSSIBLE_HERE;
        else if (player.squaredDistanceTo(Vec3d.ofCenter(clicked)) > 16) failure = PlayerEntity.SleepFailureReason.TOO_FAR_AWAY;
        else if (world.isDay()) failure = PlayerEntity.SleepFailureReason.NOT_POSSIBLE_NOW;
        else if (player.hasPassengers() || !world.isSpaceEmpty(player, player.getDimensions(player.getPose()).getBoxAt(position(corner))))
            failure = PlayerEntity.SleepFailureReason.OBSTRUCTED;
        else if (!player.isCreative() && !world.getEntitiesByClass(HostileEntity.class,
                new Box(corner).expand(8, 5, 8), entity -> entity.isAngryAt(player) && !entity.isTeammate(player)).isEmpty())
            failure = PlayerEntity.SleepFailureReason.NOT_SAFE;
        if (failure != null) {
            if (failure.getMessage() != null) player.sendMessage(failure.getMessage(), true);
            return false;
        }
        player.sleep(corner);
        player.incrementStat(Stats.SLEEP_IN_BED);
        Criteria.SLEPT_IN_BED.trigger(player);
        world.updateSleepingPlayers();
        return true;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || player.isSpectator() || player.isSneaking() || EarthenDrake.stage(player) < 0
                    || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.HAY_BLOCK)) return ActionResult.PASS;
            if (!world.isClient) sleep((ServerPlayerEntity)player, hit.getBlockPos());
            return ActionResult.SUCCESS;
        });
    }
}
