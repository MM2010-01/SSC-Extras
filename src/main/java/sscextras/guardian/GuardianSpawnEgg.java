package sscextras.guardian;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** A resident and its home are placed together through the existing pedestal/resident APIs. */
public final class GuardianSpawnEgg extends SpawnEggItem {
    public static final GuardianSpawnEgg GUARDIAN = new GuardianSpawnEgg(false, false);
    public static final GuardianSpawnEgg WEATHERED_GUARDIAN = new GuardianSpawnEgg(false, true);
    public static final GuardianSpawnEgg SENTRY = new GuardianSpawnEgg(true, false);
    public static final GuardianSpawnEgg WEATHERED_SENTRY = new GuardianSpawnEgg(true, true);
    public static final java.util.List<GuardianSpawnEgg> ALL = java.util.List.of(GUARDIAN, WEATHERED_GUARDIAN, SENTRY, WEATHERED_SENTRY);
    private final boolean sentry, weathered;

    private GuardianSpawnEgg(boolean sentry, boolean weathered) {
        super(GuardianCores.SHELL, weathered ? 0x64765c : 0x878994, sentry ? 0x303541 : 0x75deed, new Item.Settings());
        this.sentry = sentry; this.weathered = weathered;
    }
    public static void register() {
        for (var egg : ALL) {
            String name = (egg.weathered ? "weathered_" : "") + (egg.sentry ? "guardian_sentry" : "village_guardian") + "_spawn_egg";
            Registry.register(Registries.ITEM, RuneboundGuardian.id(name), egg);
            DispenserBlock.registerBehavior(egg, new ItemDispenserBehavior() {
                @Override protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
                    var facing = pointer.getBlockState().get(DispenserBlock.FACING);
                    var at = pointer.getPos().offset(facing);
                    if (egg.place(new AutomaticItemPlacementContext(pointer.getWorld(), at, facing, stack, Direction.UP), at)) stack.decrement(1);
                    return stack;
                }
            });
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> ALL.forEach(entries::add));
    }
    @Override public ActionResult useOnBlock(ItemUsageContext usage) {
        if (usage.getWorld().isClient) return ActionResult.SUCCESS;
        if (!place(new ItemPlacementContext(usage), usage.getBlockPos())) {
            if (usage.getPlayer() != null) usage.getPlayer().sendMessage(Text.translatable("message.ssc-extras.guardian.egg_space"), true);
            return ActionResult.FAIL;
        }
        if (usage.getPlayer() == null || !usage.getPlayer().getAbilities().creativeMode) usage.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
    // These village constructs require a supported, dry pedestal instead of the native water-spawn path.
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return TypedActionResult.pass(player.getStackInHand(hand));
    }
    private boolean place(ItemPlacementContext context, BlockPos clicked) {
        if (!(context.getWorld() instanceof ServerWorld world)) return false;
        var clickedState = world.getBlockState(clicked);
        boolean existing = clickedState.isOf(GuardianPedestal.BLOCK);
        var corner = existing ? GuardianPedestal.corner(clicked, clickedState) : context.getBlockPos();
        var player = context.getPlayer();
        for (int i = 0; i < 4; i++) {
            var at = GuardianPedestal.part(corner, i);
            if (!world.getWorldBorder().contains(at) || player != null && (!world.canPlayerModifyAt(player, at)
                    || !player.canPlaceOn(at, context.getSide(), context.getStack()))) return false;
        }
        BlockState[] previous = new BlockState[4];
        if (!existing) {
            var state = GuardianPedestal.BLOCK.getPlacementState(context);
            var center = GuardianPedestal.position(corner);
            if (state == null || !world.isSpaceEmpty(GuardianCores.SHELL.getDimensions().getBoxAt(center))) return false;
            for (int i = 0; i < 4; i++) {
                var at = GuardianPedestal.part(corner, i);
                previous[i] = world.getBlockState(at);
                if (!world.getFluidState(at).isEmpty()) return false;
            }
            world.setBlockState(corner, state, Block.NOTIFY_ALL);
            GuardianPedestal.BLOCK.onPlaced(world, corner, state, player, GuardianPedestal.ITEM.getDefaultStack());
        }
        var offering = world.getBlockEntity(corner) instanceof GuardianOffering value ? value : null;
        var body = offering == null ? null : offering.spawnResident(world, sentry ? 2 : 1, weathered);
        if (body == null) {
            if (!existing) for (int i = 0; i < 4; i++) world.setBlockState(GuardianPedestal.part(corner, i), previous[i], Block.NOTIFY_ALL);
            return false;
        }
        if (context.getStack().hasCustomName()) body.setCustomName(context.getStack().getName());
        body.setYaw(context.getPlayerYaw() + 180); body.bodyYaw = body.getYaw(); body.setHeadYaw(body.getYaw());
        world.emitGameEvent(player, GameEvent.ENTITY_PLACE, corner);
        return true;
    }
}
