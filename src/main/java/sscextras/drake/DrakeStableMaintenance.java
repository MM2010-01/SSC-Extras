package sscextras.drake;

import net.minecraft.block.*;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class DrakeStableMaintenance {
    public static final int SHOEING_VIOLATIONS = 3;
    private DrakeStableMaintenance() { }

    public static BlockState expected(BlockBox box, BlockPos pos) {
        if (!box.contains(pos)) return null;
        int x = pos.getX() - box.getMinX(), y = pos.getY() - box.getMinY(), z = pos.getZ() - box.getMinZ();
        int end = box.getBlockCountX() - 1, depth = box.getBlockCountZ() - 1;
        boolean facing = DrakeStableLayout.facingRows(box);
        int eaves = facing ? 7 : 5;
        if (y > 0 && y < eaves && x % 7 == 0 && (z == 0 || z == depth)) return Blocks.DARK_OAK_LOG.getDefaultState();
        if (x == 1 && y == 1 && z == (facing ? 13 : 1)) return Blocks.CHEST.getDefaultState();
        for (int stall = 0; stall < DrakeStableLayout.stallCount(box); stall++) {
            if (pos.equals(DrakeStableLayout.sign(box, stall))) return Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION,
                    DrakeStableLayout.inward(box, DrakeStableLayout.gate(box, stall)) < 0 ? 0 : 8);
            int column = DrakeStableLayout.column(box, stall) * 7;
            if (x == column + 5 && y == 1 && z == DrakeStableLayout.rowZ(box, stall, 11)) return Blocks.WATER_CAULDRON.getDefaultState();
            int lanternZ = DrakeStableLayout.rowZ(box, stall, 1);
            if (x == column + 3 && z == lanternZ && y == eaves + Math.min(z, depth - z) / 3 - 1)
                return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
            int hayA = DrakeStableLayout.rowZ(box, stall, 6), hayB = DrakeStableLayout.rowZ(box, stall, 10);
            if (y == 0 && x >= column + 2 && x <= column + 5 && z >= Math.min(hayA, hayB) && z <= Math.max(hayA, hayB))
                return Blocks.HAY_BLOCK.getDefaultState();
        }
        if (y == 0) return Blocks.COBBLESTONE.getDefaultState();
        if (y == eaves + Math.min(z, depth - z) / 3) return Blocks.DARK_OAK_PLANKS.getDefaultState();
        for (int row = 0; row < (facing ? 2 : 1); row++) {
            int front = DrakeStableLayout.rowZ(box, row * 3, 3), back = DrakeStableLayout.rowZ(box, row * 3, 12);
            if (y == 1 && z == front && (x % 7 == 3 || x % 7 == 4)) return Blocks.DARK_OAK_FENCE_GATE.getDefaultState()
                    .with(FenceGateBlock.FACING, back > front ? Direction.SOUTH : Direction.NORTH);
            if (y >= 1 && y <= 2 && (z == back || z == front && x % 7 != 3 && x % 7 != 4
                    || x % 7 == 0 && z >= Math.min(front, back) && z <= Math.max(front, back))) return Blocks.DARK_OAK_FENCE.getDefaultState();
        }
        return Blocks.AIR.getDefaultState();
    }

    public static boolean matches(BlockState state, BlockState expected) {
        if (expected.isOf(Blocks.WATER_CAULDRON)) return state.getBlock() instanceof AbstractCauldronBlock;
        if (expected.isOf(Blocks.CHEST)) return state.getBlock() instanceof ChestBlock;
        return expected.isAir() ? state.isAir() : state.isOf(expected.getBlock());
    }

    public static void caughtBreaking(PlayerEntity player, BlockPos pos) {
        if (!(player.getWorld() instanceof ServerWorld world) || player.isCreative() || player.isSpectator()) return;
        var stable = DrakeCaptureGoal.findStable(world, pos);
        if (stable == null) return;
        var expected = expected(stable.getBoundingBox(), pos);
        if (expected != null && !expected.isAir()) caught(player, stable);
    }

    public static void caughtPlacing(PlayerEntity player, BlockPos pos) {
        if (!(player.getWorld() instanceof ServerWorld world) || player.isCreative() || player.isSpectator()) return;
        var stable = DrakeCaptureGoal.findStable(world, pos);
        if (stable == null || !stable.getBoundingBox().contains(pos)) return;
        var expected = expected(stable.getBoundingBox(), pos);
        if (expected != null && !matches(world.getBlockState(pos), expected)) caught(player, stable);
    }

    private static void caught(PlayerEntity player, DrakeStablePiece stable) {
        if (EarthenDrake.stage(player) < 0 || DrakeShoes.fullyEquipped(player)) return;
        PillagerEntity witness = null;
        for (var guard : player.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(16),
                guard -> guard.isAlive() && !(guard instanceof DrakeVisitorEntity) && !guard.hasActiveRaid() && !guard.isAiDisabled()
                        && guard.getVisibilityCache().canSee(player))) {
            var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
            if (home != null && home.getBoundingBox().equals(stable.getBoundingBox())) { witness = guard; break; }
        }
        if (witness == null) return;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) {
            DrakeOutpostOwnership.capture(player, stable);
            claim = DrakeOutpostOwnership.claim(player);
        }
        if (claim == null || !claim.matches(player.getWorld(), stable) || claim.shoeingDue) return;
        claim.shoeingViolations = Math.min(SHOEING_VIOLATIONS, claim.shoeingViolations + 1);
        claim.shoeingDue = claim.shoeingViolations == SHOEING_VIOLATIONS;
        witness.getLookControl().lookAt(player, 30, 30);
        player.sendMessage((claim.shoeingDue ? Text.translatable("message.ssc-extras.drake.shoeing_caught")
                : Text.translatable("message.ssc-extras.drake.shoeing_warning", claim.shoeingViolations))
                .formatted(Formatting.DARK_PURPLE), false);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    public static boolean repair(ServerWorld world, DrakeStablePiece stable, BlockPos pos, PillagerEntity guard) {
        if (!world.isChunkLoaded(pos)) return false;
        var expected = expected(stable.getBoundingBox(), pos);
        if (expected == null || matches(world.getBlockState(pos), expected)) return false;
        if (!expected.isAir() && !world.canPlace(expected, pos, ShapeContext.absent())) return false;
        if (!world.getBlockState(pos).isAir() && !world.breakBlock(pos, true, guard)) return false;
        if (!expected.isAir()) {
            world.setBlockState(pos, Block.postProcessState(expected, world, pos), Block.NOTIFY_ALL);
            if (expected.isOf(Blocks.DARK_OAK_SIGN)) DrakeOutpostOwnership.restoreSign(world, stable, pos);
        }
        return true;
    }
}
