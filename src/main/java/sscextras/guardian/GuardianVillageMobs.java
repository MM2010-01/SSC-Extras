package sscextras.guardian;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import sscextras.drake.DrakeOutpostOwnership;

/** Village NPC policy. Player souls and player Guardian protection keep their existing owners. */
public final class GuardianVillageMobs {
    public static final Identifier STRUGGLE = RuneboundGuardian.id("guardian_capture_struggle");
    private GuardianVillageMobs() { }
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity server && housing(server.getServerWorld(), pos, state))
                witnessed(server, Vec3d.ofCenter(pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(STRUGGLE, (server, player, handler, buffer, sender) -> server.execute(() -> {
            var event = DrakeOutpostOwnership.get(server).assignments().owner(player.getUuid());
            if (event instanceof GuardianCaptureEvent capture) capture.struggle();
        }));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            var event = DrakeOutpostOwnership.get(world.getServer()).assignments().owner(entity.getUuid());
            if (event instanceof GuardianCaptureEvent capture) capture.finish();
        });
    }
    public static void harmed(VillagerEntity villager, Entity attacker) {
        if (attacker instanceof ServerPlayerEntity player) witnessed(player, villager.getPos());
    }
    public static void witnessed(ServerPlayerEntity player, Vec3d crime) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
        var world = player.getServerWorld();
        for (var guardian : world.getEntitiesByClass(GuardianShellEntity.class, new Box(BlockPos.ofFloored(crime)).expand(8),
                g -> g.resident() && g.isAlive() && g.squaredDistanceTo(crime) <= 64
                        && Vec3d.ofCenter(g.home()).squaredDistanceTo(crime) <= 128 * 128)) guardian.alarm(player);
    }
    /** Only a successfully broken housing block, never crops, paths, leaves or ordinary terrain. */
    public static boolean housing(ServerWorld world, BlockPos pos, BlockState broken) {
        if (broken.isOf(GuardianPedestal.BLOCK)) return true;
        if (!broken.isIn(BlockTags.BEDS) && !broken.isIn(BlockTags.DOORS) && !broken.isIn(BlockTags.PLANKS)
                && !broken.isIn(BlockTags.LOGS) && !broken.isIn(BlockTags.STAIRS) && !broken.isIn(BlockTags.SLABS)
                && !broken.isIn(BlockTags.WALLS) && !broken.isIn(BlockTags.STONE_BRICKS) && !broken.isIn(BlockTags.FENCES)
                && !broken.isIn(BlockTags.TRAPDOORS) && !broken.isIn(BlockTags.TERRACOTTA) && !broken.isIn(BlockTags.ICE)
                && !(broken.getBlock() instanceof net.minecraft.block.AbstractGlassBlock)
                && !(broken.getBlock() instanceof net.minecraft.block.PaneBlock)
                && !broken.isOf(net.minecraft.block.Blocks.COBBLESTONE) && !broken.isOf(net.minecraft.block.Blocks.SANDSTONE)
                && !broken.isOf(net.minecraft.block.Blocks.CUT_SANDSTONE) && !broken.isOf(net.minecraft.block.Blocks.SMOOTH_SANDSTONE)
                && !broken.isOf(net.minecraft.block.Blocks.CHISELED_SANDSTONE) && !broken.isOf(net.minecraft.block.Blocks.BRICKS)
                && !broken.isOf(net.minecraft.block.Blocks.SNOW_BLOCK)) return false;
        var start = world.getStructureAccessor().getStructureContaining(pos, StructureTags.VILLAGE);
        if (!start.hasChildren()) return false;
        // Bed removal itself has already removed the POI; it still counts inside its village house.
        if (broken.isIn(BlockTags.BEDS)) return true;
        var homes = world.getPointOfInterestStorage().getInCircle(type -> type.matchesKey(PointOfInterestTypes.HOME), pos, 16,
                PointOfInterestStorage.OccupationStatus.ANY).map(poi -> poi.getPos()).toList();
        return start.getChildren().stream().filter(piece -> piece.getBoundingBox().contains(pos))
                .anyMatch(piece -> homes.stream().anyMatch(home -> piece.getBoundingBox().contains(home))
                        || piece.toNbt(net.minecraft.structure.StructureContext.from(world)).getCompound("pool_element")
                                .getString("location").contains("/houses/"));
    }
}
