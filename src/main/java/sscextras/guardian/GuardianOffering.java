package sscextras.guardian;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.UUID;

public final class GuardianOffering extends BlockEntity {
    public static final int DURATION = 24;
    public static final BlockEntityType<GuardianOffering> TYPE = FabricBlockEntityTypeBuilder.create(GuardianOffering::new, GuardianPedestal.BLOCK).build();
    public UUID target;
    public long started;

    public GuardianOffering(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
    public static void register() { Registry.register(Registries.BLOCK_ENTITY_TYPE, RuneboundGuardian.id("guardian_offering"), TYPE); }
    public boolean available() { return getCachedState().get(GuardianPedestal.OFFERING); }
    public static void standing(ServerPlayerEntity player) {
        if (!player.isOnGround() || !GuardianEquipment.eligible(player)) return;
        var pos = GuardianPedestal.beneath(player);
        if (pos != null && player.getWorld().getBlockEntity(pos) instanceof GuardianOffering offering) offering.start(player);
    }
    public boolean start(ServerPlayerEntity player) {
        if (world == null || !available() || target != null || !GuardianEquipment.eligible(player) || !GuardianEquipment.hasSlots(player)
                || !GuardianPedestal.intact(world, pos) || player.squaredDistanceTo(GuardianPedestal.position(pos)) > 64) return false;
        if (GuardianWorldState.get(player.getServer()).villages.values().stream()
                .anyMatch(v -> v.world.equals(world.getRegistryKey()) && v.pedestal.equals(pos))) return false;
        target = player.getUuid(); started = world.getTime();
        sync();
        world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1, .7f);
        return true;
    }
    public static void tick(World world, BlockPos pos, BlockState state, GuardianOffering offering) {
        if (offering.target == null) return;
        var player = world.getPlayerByUuid(offering.target) instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
        if (!offering.available() || player == null || player.getWorld() != world || !GuardianEquipment.eligible(player)
                || player.squaredDistanceTo(GuardianPedestal.position(pos)) > 64 || !GuardianPedestal.intact(world, pos)) {
            offering.target = null; offering.sync(); return;
        }
        if (world.getTime() - offering.started < DURATION) return;
        if (GuardianEquipment.fit(player, pos)) {
            for (int i = 0; i < 4; i++) {
                var part = GuardianPedestal.part(pos, i);
                world.setBlockState(part, world.getBlockState(part).with(GuardianPedestal.OFFERING, false), Block.NOTIFY_ALL);
            }
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 1, .65f);
        }
        offering.target = null; offering.sync();
    }
    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    @Override public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        // Only clients resume the visual flight. A server reload releases the unfinished fitting.
        target = world != null && world.isClient && nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
        started = nbt.getLong("Started");
    }
    @Override protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (target != null) nbt.putUuid("Target", target);
        nbt.putLong("Started", started);
    }
    @Override public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
