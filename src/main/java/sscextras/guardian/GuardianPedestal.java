package sscextras.guardian;

import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.block.entity.*;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import java.util.List;

public final class GuardianPedestal extends BlockWithEntity {
    public static final IntProperty PART = IntProperty.of("part", 0, 3);
    public static final BooleanProperty OFFERING = BooleanProperty.of("offering");
    public static final GuardianPedestal BLOCK = new GuardianPedestal();
    public static final BlockItem ITEM = new BlockItem(BLOCK, new Item.Settings());
    private static final VoxelShape[] SHAPES = new VoxelShape[4];
    static {
        for (int part = 0; part < 4; part++) {
            int x = (part & 1) == 0 ? 2 : 0, z = (part & 2) == 0 ? 2 : 0;
            SHAPES[part] = VoxelShapes.union(createCuboidShape(0, 0, 0, 16, 3, 16),
                    createCuboidShape(x, 3, z, x + 14, 10, z + 14), createCuboidShape(0, 10, 0, 16, 12, 16));
        }
    }
    private GuardianPedestal() {
        super(Settings.create().mapColor(MapColor.STONE_GRAY).strength(3.5f, 9).requiresTool().sounds(BlockSoundGroup.STONE).nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(PART, 0).with(OFFERING, false));
    }
    public static void register() {
        Registry.register(Registries.BLOCK, RuneboundGuardian.id("gargoyle_pedestal"), BLOCK);
        Registry.register(Registries.ITEM, RuneboundGuardian.id("gargoyle_pedestal"), ITEM);
        GuardianOffering.register();
        GuardianPedestalPiece.register();
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(PART, OFFERING); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return state.get(PART) == 0 ? new GuardianOffering(pos, state) : null; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, GuardianOffering.TYPE, GuardianOffering::tick);
    }
    public static BlockPos corner(BlockPos pos, BlockState state) { return pos.add(-(state.get(PART) & 1), 0, -((state.get(PART) >> 1) & 1)); }
    public static BlockPos part(BlockPos corner, int part) { return corner.add(part & 1, 0, (part >> 1) & 1); }
    public static boolean intact(World world, BlockPos corner) {
        for (int i = 0; i < 4; i++) {
            var state = world.getBlockState(part(corner, i));
            if (!state.isOf(BLOCK) || state.get(PART) != i) return false;
        }
        return true;
    }
    public static Vec3d position(BlockPos corner) { return new Vec3d(corner.getX() + 1, corner.getY() + .75, corner.getZ() + 1); }
    public static java.util.UUID reservation(ServerWorld world, BlockPos pos) {
        return java.util.UUID.nameUUIDFromBytes(("guardian-pedestal:" + world.getRegistryKey().getValue() + ":" + pos.asLong())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Loaded, intact, unclaimed and physically empty. The current event may retain its own reservation. */
    public static boolean vacant(ServerWorld world, BlockPos pos, PlayerEntity occupant, sscextras.events.NpcEvent event) {
        if (!world.isChunkLoaded(pos) || !intact(world, pos)
                || !(world.getBlockEntity(pos) instanceof GuardianOffering offering) || offering.hasResident()
                || offering.target != null && (occupant == null || !offering.target.equals(occupant.getUuid()))) return false;
        var owner = sscextras.drake.DrakeOutpostOwnership.get(world.getServer()).assignments().owner(reservation(world, pos));
        if (owner != null && owner != event) return false;
        if (GuardianWorldState.get(world.getServer()).villages.values().stream()
                .anyMatch(v -> v.world.equals(world.getRegistryKey()) && v.pedestal.equals(pos))) return false;
        var feet = position(pos);
        var box = new net.minecraft.util.math.Box(feet.x - .8, feet.y, feet.z - .8, feet.x + .8, feet.y + 3, feet.z + .8);
        return world.getEntitiesByClass(LivingEntity.class, box, e -> e != occupant && e.isAlive() && !e.isSpectator()
                && !(e instanceof GuardianShellEntity shell && event != null && shell.capture == event)).isEmpty();
    }
    public static java.util.List<BlockPos> nearby(ServerWorld world, BlockPos center, int radius) {
        var result = new java.util.ArrayList<BlockPos>();
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                var chunk = world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) if (entry.getValue() instanceof GuardianOffering
                        && entry.getKey().isWithinDistance(center, radius)) result.add(entry.getKey());
            }
        return result;
    }
    public static BlockPos beneath(PlayerEntity player) {
        var pos = BlockPos.ofFloored(player.getX(), player.getY() - .05, player.getZ());
        var state = player.getWorld().getBlockState(pos);
        if (!state.isOf(BLOCK)) return null;
        var corner = corner(pos, state);
        return intact(player.getWorld(), corner) && Math.abs(player.getY() - (corner.getY() + .75)) < .15 ? corner : null;
    }
    public static boolean standing(PlayerEntity player) { return player.isOnGround() && beneath(player) != null; }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(OFFERING) ? VoxelShapes.union(SHAPES[state.get(PART)], createCuboidShape(2,12,2,14,18,14)) : SHAPES[state.get(PART)];
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPES[state.get(PART)]; }
    @Override public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, net.minecraft.entity.ai.pathing.NavigationType type) { return false; }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        var world = context.getWorld(); var pos = context.getBlockPos();
        for (int i = 0; i < 4; i++) {
            var at = part(pos, i);
            if (!world.getWorldBorder().contains(at) || !world.getBlockState(at).canReplace(context)
                    || !world.getBlockState(at.down()).isSideSolidFullSquare(world, at.down(), Direction.UP)) return null;
        }
        return getDefaultState();
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (world.isClient) return;
        for (int i = 1; i < 4; i++) world.setBlockState(part(pos, i), state.with(PART, i), Block.NOTIFY_LISTENERS);
        for (int i = 0; i < 4; i++) world.updateNeighbors(part(pos, i), this);
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        super.onStateReplaced(state, world, pos, next, moved);
        if (world.isClient || state.isOf(next.getBlock())) return;
        var corner = corner(pos, state);
        for (int i = 0; i < 4; i++) {
            var other = part(corner, i);
            if (!other.equals(pos) && world.getBlockState(other).isOf(this)) world.removeBlock(other, false);
        }
        GuardianVillages.pedestalBroken((ServerWorld)world, corner);
    }
    @Override public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) { return List.of(new ItemStack(ITEM)); }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.getStackInHand(hand).getItem() instanceof GuardianSpawnEgg) return ActionResult.PASS;
        if (state.get(OFFERING) && hand == Hand.MAIN_HAND) {
            if (world.isClient) return ActionResult.SUCCESS;
            if (world.getBlockEntity(corner(pos, state)) instanceof GuardianOffering offering && offering.start((ServerPlayerEntity)player)) return ActionResult.CONSUME;
            return ActionResult.PASS;
        }
        if (RuneboundGuardian.stage(player) < 0 || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (GuardianCombat.locked(player)) return ActionResult.FAIL;
        var corner = corner(pos, state);
        if (player.isSneaking()) GuardianVillages.bind((ServerPlayerEntity)player, corner);
        else GuardianSleep.pedestal((ServerPlayerEntity)player, corner);
        return ActionResult.CONSUME;
    }
}
