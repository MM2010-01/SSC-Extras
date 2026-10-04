package sscextras.effigy;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class EffigyBlock extends HorizontalFacingBlock {
    private static final VoxelShape BASE = VoxelShapes.union(Block.createCuboidShape(2, 0, 2, 14, 2, 14),
            Block.createCuboidShape(6, 2, 6, 10, 5, 10), Block.createCuboidShape(4, 5, 4, 12, 6, 12));
    private static final VoxelShape NORTH = VoxelShapes.union(BASE, Block.createCuboidShape(3.5, 6, 6.5, 12.5, 16, 9.5));
    private static final VoxelShape EAST = VoxelShapes.union(BASE, Block.createCuboidShape(6.5, 6, 3.5, 9.5, 16, 12.5));

    public EffigyBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.Z ? NORTH : EAST;
    }

    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, wearer) -> new EffigyScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)),
                Text.translatable("block.ssc-extras.feral_effigy")));
        return ActionResult.success(world.isClient);
    }
}
