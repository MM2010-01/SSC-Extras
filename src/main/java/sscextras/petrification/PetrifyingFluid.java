package sscextras.petrification;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.*;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.*;
import java.util.Optional;

public abstract class PetrifyingFluid extends FlowableFluid {
    private record ForgeEyes(java.lang.reflect.Method eyeType, java.lang.reflect.Method fluidType) { }
    private static final ForgeEyes FORGE_EYES = forgeEyes();
    private static ForgeEyes forgeEyes() {
        try { Class.forName("net.minecraftforge.fluids.FluidType"); }
        catch (ClassNotFoundException absent) { return null; }
        try {
            return new ForgeEyes(net.minecraft.entity.Entity.class.getMethod("getEyeInFluidType"),
                    Fluid.class.getMethod("getFluidType"));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot bind Forge fluid eye contact", error); }
    }
    /** Native eye contact on both loaders; Forge does not populate arbitrary submerged-fluid tags. */
    public static boolean headSubmerged(net.minecraft.entity.Entity entity) {
        if (FORGE_EYES == null) return entity.isSubmergedIn(Petrification.FLUIDS);
        try {
            Object eyes = FORGE_EYES.eyeType().invoke(entity);
            return eyes == FORGE_EYES.fluidType().invoke(Petrification.STILL)
                    || eyes == FORGE_EYES.fluidType().invoke(Petrification.FLOWING);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read Forge fluid eye contact", error); }
    }
    @Override public Fluid getFlowing() { return Petrification.FLOWING; }
    @Override public Fluid getStill() { return Petrification.STILL; }
    @Override public Item getBucketItem() { return Petrification.BUCKET; }
    @Override protected boolean isInfinite(World world) { return false; }
    @Override protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        Block.dropStacks(state, world, pos, state.hasBlockEntity() ? world.getBlockEntity(pos) : null);
    }
    @Override protected int getFlowSpeed(WorldView world) { return 2; }
    @Override protected int getLevelDecreasePerBlock(WorldView world) { return 2; }
    @Override public int getTickRate(WorldView world) { return 30; }
    @Override protected float getBlastResistance() { return 100; }
    @Override public BlockState toBlockState(FluidState state) { return Petrification.LIQUID.getDefaultState().with(FluidBlock.LEVEL, getBlockStateLevel(state)); }
    @Override public boolean matchesType(Fluid fluid) { return fluid == Petrification.STILL || fluid == Petrification.FLOWING; }
    @Override public boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !matchesType(fluid);
    }
    @Override public Optional<SoundEvent> getBucketFillSound() { return Optional.of(SoundEvents.ITEM_BUCKET_FILL_LAVA); }
    public static final class Still extends PetrifyingFluid {
        @Override public boolean isStill(FluidState state) { return true; }
        @Override public int getLevel(FluidState state) { return 8; }
    }
    public static final class Flowing extends PetrifyingFluid {
        @Override protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) { super.appendProperties(builder); builder.add(LEVEL); }
        @Override public boolean isStill(FluidState state) { return false; }
        @Override public int getLevel(FluidState state) { return state.get(LEVEL); }
    }
}
