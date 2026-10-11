package sscextras.guardian;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import java.util.Comparator;

public final class GuardianDialogue {
    private GuardianDialogue() { }

    public static void tick(ServerPlayerEntity player) {
        var village = GuardianVillages.claim(player);
        var state = RuneboundGuardian.state(player);
        if (RuneboundGuardian.stage(player) != 2 || !player.isAlive() || player.isSpectator()
                || state.detached || state.ritualTicks > 0
                || TransformManager.getPlayerTransformData(player).isTransforming) {
            stop(player); return;
        }
        if (!state.stageTwoHintShown) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.stage_two_hint").formatted(Formatting.LIGHT_PURPLE), false);
            state.stageTwoHintShown = true;
        }
        if (village == null) return;
        var world = player.getServer().getWorld(village.world);
        long day = world == null ? -1 : Math.floorDiv(world.getTimeOfDay(), 24000);
        if (village.serviceHintDay >= 0 && day > village.serviceHintDay) {
            village.serviceHintDay = -1;
            GuardianWorldState.get(player.getServer()).markDirty();
        }
        boolean offer = !village.serviceOffered;
        boolean reminder = village.serviceHintDay >= 0 && day == village.serviceHintDay;
        if ((!offer && !reminder) || village.serviceTicks >= GuardianWorldState.SERVICE_TICKS
                || world != player.getWorld() || player.isSleeping()
                || player.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) > GuardianEquipment.RANGE * GuardianEquipment.RANGE
                || !GuardianPedestal.intact(world, village.pedestal)) {
            stop(player); return;
        }
        var speaker = village.speaker == null ? null : world.getEntity(village.speaker) instanceof VillagerEntity v ? v : null;
        if (speaker != null && (!speaker.isAlive() || speaker.isSleeping() || speaker.getCustomer() != null
                || speaker.getTarget() != null || ++village.speakerTicks > 400)) {
            stop(player); return;
        }
        if (speaker == null) {
            village.speaker = null;
            if (player.age % 40 != 0) return;
            speaker = GuardianVillages.villagers(world, village.pedestal).stream()
                    .filter(v -> !v.isBaby() && !v.isAiDisabled() && !v.isSleeping() && v.getCustomer() == null
                            && v.getTarget() == null && v.getMainHandStack().isEmpty() && !GuardianRitual.assigned(v))
                    .sorted(Comparator.comparingDouble(v -> v.squaredDistanceTo(player)))
                    .filter(v -> v.squaredDistanceTo(player) <= 9 && v.canSee(player)
                            || v.getNavigation().findPathTo(player, 0) != null).findFirst().orElse(null);
            if (speaker == null) return;
            village.speaker = speaker.getUuid(); village.speakerTicks = 0;
            speaker.addCommandTag("ssc_extras_guardian_worker");
        }
        speaker.getLookControl().lookAt(player);
        if (speaker.squaredDistanceTo(player) > 9 || !speaker.canSee(player)) {
            GuardianRitual.walk(speaker, player.getPos(), .8); return;
        }
        String line = offer ? "service_offer" : "service_day_" + village.serviceHint;
        player.sendMessage(Text.translatable("message.ssc-extras.guardian." + line).formatted(Formatting.GRAY), false);
        if (offer) village.serviceOffered = true;
        else village.serviceHintDay = -1;
        stop(player);
        GuardianWorldState.get(player.getServer()).markDirty();
    }

    public static void stop(ServerPlayerEntity player) {
        var village = GuardianVillages.claim(player);
        if (village == null || village.speaker == null) return;
        var world = player.getServer().getWorld(village.world);
        if (world != null && world.getEntity(village.speaker) instanceof VillagerEntity speaker)
            GuardianRitual.releaseWorker(speaker);
        village.speaker = null; village.speakerTicks = 0;
    }
}
