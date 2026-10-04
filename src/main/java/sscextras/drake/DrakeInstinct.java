package sscextras.drake;

import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import sscextras.CreatureInstinct;

public final class DrakeInstinct {
    private DrakeInstinct() { }

    public static void register() {
        var claws = new ConditionFactory<Entity>(EarthenDrake.id("claw_instinct"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player && canGain(player)
                        && player.getInventory().main.get(player.getInventory().selectedSlot).isEmpty());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, claws.getSerializerId(), claws);
        var quadruped = new ConditionFactory<Entity>(EarthenDrake.id("quadruped_instinct"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player && canGain(player)
                        && player.isOnGround() && !player.hasVehicle() && EarthenDrake.onAllFours(player));
        Registry.register(ApoliRegistries.ENTITY_CONDITION, quadruped.getSerializerId(), quadruped);
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

    private static boolean canGain(PlayerEntity player) {
        return !InstinctTicker.isPausing && EarthenDrake.stage(player) >= 0
                && CreatureInstinct.canGain(player, EarthenDrake.CURSE);
    }
}
