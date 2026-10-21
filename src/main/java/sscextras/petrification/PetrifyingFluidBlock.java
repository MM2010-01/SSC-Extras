package sscextras.petrification;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

final class PetrifyingFluidBlock extends FluidBlock {
    PetrifyingFluidBlock(FlowableFluid fluid, Settings settings) { super(fluid, settings); }
    @Override public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (entity.getBoundingBox().minY >= pos.getY() + state.getFluidState().getHeight(world, pos)) return;
        entity.slowMovement(state, new Vec3d(.5, .5, .5));
        if (entity instanceof LivingEntity living) Petrification.state(living).touch();
    }
}
