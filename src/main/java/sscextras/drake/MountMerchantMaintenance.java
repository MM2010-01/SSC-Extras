package sscextras.drake;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class MountMerchantMaintenance {
    private MountMerchantMaintenance() { }

    public static BlockBox bounds(BlockPos site) { return BlockBox.create(site, site.add(12, 6, 10)); }

    public static BlockState expected(BlockPos site, BlockPos pos) {
        if (!bounds(site).contains(pos)) return null;
        int x = pos.getX() - site.getX(), y = pos.getY() - site.getY(), z = pos.getZ() - site.getZ();
        if (y == 0) return (x >= 1 && x <= 11 && z >= 5 && z <= 9 ? Blocks.HAY_BLOCK : Blocks.COBBLESTONE).getDefaultState();
        if (y >= 1 && y <= 4 && (x == 0 || x == 5) && (z == 0 || z == 3)) return Blocks.DARK_OAK_LOG.getDefaultState();
        if (y == 5 && x <= 5 && z <= 4) return Blocks.BROWN_WOOL.getDefaultState();
        if (y == 1 && x >= 1 && x <= 4 && z == 0) return Blocks.DARK_OAK_SLAB.getDefaultState();
        if (x == 2 && y == 4 && z == 1) return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        if (x == 11 && y == 1 && z == 9) return Blocks.WATER_CAULDRON.getDefaultState();
        if (y == 1 && z == 2 && (x == 6 || x == 10 || x == 11))
            return Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 8);
        if (y == 1 && z == 3 && (x == 8 || x == 9))
            return Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH);
        if (x == 6 && y == 1 && z == 7 || y >= 1 && y <= 2 && (z == 10 || z == 3 && x != 8 && x != 9
                || (x == 0 || x == 12) && z >= 3)) return Blocks.DARK_OAK_FENCE.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    public static boolean repair(ServerWorld world, MountMerchantEntity merchant, BlockPos pos) {
        if (!DrakeStableMaintenance.repair(world, pos, expected(merchant.site(), pos), merchant)) return false;
        merchant.updateSigns();
        return true;
    }
}
