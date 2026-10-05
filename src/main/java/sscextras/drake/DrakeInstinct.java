package sscextras.drake;

import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.status_effects.attachment.EffectManager;
import sscextras.CreatureInstinct;

public final class DrakeInstinct {
    private DrakeInstinct() { }

    public static float stallGain(PlayerEntity player, float amount) {
        if (amount <= 0 || !(player.getWorld() instanceof ServerWorld world)) return amount;
        int stage = EarthenDrake.stage(player);
        if (stage == 3) return amount;
        if (stage >= 0 && DrakeSoulbinding.drakeSoul(player)) amount *= 2;
        if (stage < 0) {
            var form = FormAbilityManager.getForm(player);
            var target = CreatureInstinct.getTarget(player);
            var curse = EffectManager.getTransformativeEffect(player);
            if (form.getIndex() >= 0 || !(target != null && target.getGroup() == EarthenDrake.GROUP
                    || target == null && curse != null && curse.getTransformativeEffectType() == EarthenDrake.CURSE)) return amount;
        }
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && claim.world.equals(world.getRegistryKey()) && insideStall(claim.stable, player.getPos())) return amount * 1.5f;
        var outpost = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "pillager_outpost"));
        var mansion = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "mansion"));
        for (var start : world.getStructureAccessor().getStructureStarts(new ChunkPos(player.getBlockPos()), structure -> structure == outpost || structure == mansion))
            for (var piece : start.getChildren())
                if (piece instanceof DrakeStablePiece && insideStall(piece.getBoundingBox(), player.getPos())) return amount * 1.5f;
        return amount;
    }

    private static boolean insideStall(BlockBox box, Vec3d pos) {
        double x = pos.x - box.getMinX();
        return x >= 1 && x < box.getBlockCountX() - 1 && x % 7 >= 1
                && (DrakeStableLayout.facingRows(box)
                    ? pos.z >= box.getMinZ() + 1 && pos.z < box.getMinZ() + 9
                        || pos.z >= box.getMinZ() + 18 && pos.z < box.getMaxZ()
                    : pos.z >= box.getMinZ() + 4 && pos.z < box.getMaxZ())
                && pos.y >= box.getMinY() + 1 && pos.y < box.getMinY() + 5;
    }

    public static void register() {
        var claws = new ConditionFactory<Entity>(EarthenDrake.id("claw_instinct"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player && canGain(player)
                        && player.getInventory().main.get(player.getInventory().selectedSlot).isEmpty());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, claws.getSerializerId(), claws);
        var quadruped = new ConditionFactory<Entity>(EarthenDrake.id("quadruped_instinct"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player && canGain(player)
                        && player.isOnGround() && !player.hasVehicle() && EarthenDrake.onAllFours(player));
        Registry.register(ApoliRegistries.ENTITY_CONDITION, quadruped.getSerializerId(), quadruped);
        var ridden = new ConditionFactory<Entity>(EarthenDrake.id("ridden_drake"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player && EarthenDrake.stage(player) == 2 && player.hasPassengers());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, ridden.getSerializerId(), ridden);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (hand == Hand.MAIN_HAND && canGain(player) && player.canModifyBlocks()
                    && player.getInventory().main.get(player.getInventory().selectedSlot).isEmpty()) {
                var block = world.getBlockState(pos);
                if (!block.isAir() && block.getHardness(world, pos) >= 0) {
                    InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_DRAKE_DIG", 0.8f);
                }
            }
            return ActionResult.PASS;
        });
    }

    public static void pet(PlayerEntity player) {
        if (!player.getWorld().isClient && EarthenDrake.stage(player) < 3 && canGain(player))
            InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_DRAKE_PET", .5f);
    }

    public static void handFed(PlayerEntity player) {
        if (!player.getWorld().isClient && EarthenDrake.stage(player) <= 1 && canGain(player))
            InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_DRAKE_HAND_FED", 2);
    }

    private static boolean canGain(PlayerEntity player) {
        return !InstinctTicker.isPausing && EarthenDrake.stage(player) >= 0
                && CreatureInstinct.canGain(player, EarthenDrake.CURSE);
    }
}
