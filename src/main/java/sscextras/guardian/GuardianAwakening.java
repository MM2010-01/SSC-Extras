package sscextras.guardian;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import sscextras.rituals.RitualComposition;
import sscextras.rituals.RitualProgress;
import sscextras.rituals.RitualStage;
import sscextras.rituals.RitualStageRunner;
import java.util.List;
import java.util.Objects;

import static sscextras.guardian.GuardianRitual.*;

/** Villager/core adapter; the shared runner owns advancement and committed effects. */
final class GuardianAwakening extends AbstractGuardianRitual<GuardianAwakening> {
    static final RitualComposition COMPOSITION = new RitualComposition(List.of(
            new RitualComposition.Participant("guide", new Vec3d(.5, -.75, -2.5), true),
            new RitualComposition.Participant("left_carver", new Vec3d(-2.5, -.75, -.5), false),
            new RitualComposition.Participant("right_carver", new Vec3d(2.5, -.75, -.5), false)));
    private static final List<RitualStage<GuardianAwakening>> STAGES = List.of(
            RitualStage.afterAndWhen("escort", 1, r -> true, GuardianAwakening::arrive),
            RitualStage.afterAndWhen("binding", SOUL_START - 1, r -> true, GuardianAwakening::beginSoul),
            RitualStage.afterAndWhen("extraction", SOUL_SEALED - SOUL_START, r -> true, GuardianAwakening::sealSoul),
            RitualStage.afterAndWhen("encasing", ENCASED - SOUL_SEALED, r -> true, GuardianAwakening::beginCarving),
            RitualStage.afterAndWhen("carving", SCULPTED - ENCASED, r -> true, GuardianAwakening::returnSoul),
            RitualStage.afterAndWhen("release", COMPLETE - SCULPTED, r -> true, r -> {
                r.player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_complete", r.state().guardianName), false);
                return true;
            }));

    private boolean failed;

    GuardianAwakening(ServerPlayerEntity player, GuardianWorldState.Village village) {
        super(player, village, COMPOSITION, STAGES, progress(village));
    }

    private static RitualProgress progress(GuardianWorldState.Village village) {
        if (village.awakening == null) village.awakening = legacyProgress(village.ritualTicks);
        return village.awakening;
    }

    static RitualProgress legacyProgress(int nextTick) {
        return RitualProgress.fromTimeline(STAGES, Math.max(0, Math.min(COMPLETE, nextTick - 1)));
    }
    int ticks() { return village.awakening.finished() ? 0 : stages.timeline() + 1; }
    private GuardianShellEntity shell() {
        return village.body != null && world.getEntity(village.body) instanceof GuardianShellEntity body && body.isAlive() ? body : null;
    }

    RitualStageRunner.Result tick() {
        if (!resolveActors()) return RitualStageRunner.Result.CANCELLED;
        String stage = stages.current().id();
        if ((stage.equals("encasing") || stage.equals("carving")) && shell() == null) return RitualStageRunner.Result.CANCELLED;
        boolean ready = assign();
        if (ready) ready = stage.equals("escort") ? approach() : positionActors();
        if (ready) { resume(); present(stage); } else pause();
        var result = stages.tick(this, 1, ready);
        return failed ? RitualStageRunner.Result.CANCELLED : result;
    }

    private boolean approach() {
        var guide = villagers.get(COMPOSITION.index("guide"));
        prop(guide, Items.LEAD);
        var state = state();
        if (state.escort == null) {
            walk(guide, player.getPos(), 1); guide.getLookControl().lookAt(player);
            if (player.squaredDistanceTo(guide) > 6.25 || !guide.canSee(player)) return false;
            state.escort = new GuardianEscort(player, center);
            state.ritualGuideId = guide.getId();
            player.clearActiveItem(); player.closeHandledScreen(); player.setSprinting(false);
            player.getAbilities().flying = false; player.sendAbilitiesUpdate(); state.sync();
        }
        Vec3d target = safeStation(guide, center, COMPOSITION.index("guide"));
        if (target != null) walk(guide, target, 1);
        if (center.squaredDistanceTo(player.getPos()) > .64) { state.escort.guide(); return false; }
        return true;
    }
    private boolean arrive() {
        stopEscort(player);
        player.teleport(world, center.x, center.y, center.z, 180, 0); player.setOnGround(true);
        return true;
    }
    private void present(String stage) {
        int time = ticks();
        if ((stage.equals("binding") || stage.equals("extraction")) && !state().detached) {
            player.setVelocity(Vec3d.ZERO); player.setPosition(center); player.setYaw(180); player.setHeadYaw(180); player.bodyYaw = 180;
        }
        switch (stage) {
            case "binding" -> {
                prop(villagers.get(1), Items.CHAIN); prop(villagers.get(2), Items.CHAIN);
                if (time == 40) sound(world, center, SoundEvents.BLOCK_CHAIN_PLACE, .7f);
            }
            case "extraction" -> {
                prop(villagers.get(0), GuardianCores.INACTIVE_CORE);
                Vec3d source = center.add(0, 1.5, 0), target = villagers.get(0).getPos().add(0, 1.2, 0);
                Vec3d soul = source.lerp(target, (time - SOUL_START) / (double)(SOUL_SEALED - SOUL_START));
                world.spawnParticles(ParticleTypes.SOUL, soul.x, soul.y, soul.z, 3, .10, .20, .10, .01);
                world.spawnParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.5, center.z, 4, 1, 1, 1, .3);
                if (time % 30 == 0) sound(world, center, SoundEvents.ENTITY_VILLAGER_AMBIENT, .6f);
            }
            case "encasing" -> {
                shell().work(1 + (time - SOUL_SEALED) * 99 / (ENCASED - SOUL_SEALED));
                prop(villagers.get(1), LIQUID_STONE); prop(villagers.get(2), LIQUID_STONE);
                if (time % 5 == 0) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST,
                        net.minecraft.block.Blocks.STONE.getDefaultState()), center.x, center.y + 2.7, center.z, 18, .45, .1, .45, .02);
            }
            case "carving" -> {
                shell().work(100 - (time - ENCASED) * 99 / (SCULPTED - ENCASED));
                for (int i = 1; i < villagers.size(); i++) {
                    prop(villagers.get(i), CHISEL);
                    if (time % 15 == 0) villagers.get(i).swingHand(Hand.MAIN_HAND);
                }
                if (time % 10 == 0) {
                    sound(world, center, SoundEvents.BLOCK_STONE_BREAK, .5f);
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.STONE.getDefaultState()),
                            center.x, center.y + 1.3, center.z, 10, .7, 1, .7, .05);
                }
            }
        }
    }
    private boolean beginSoul() {
        prop(villagers.get(0), GuardianCores.INACTIVE_CORE);
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_soul"), false);
        return true;
    }
    private boolean sealSoul() {
        var record = GuardianCores.create(player);
        var body = GuardianCores.detach(player, false);
        if (body == null) { failed = true; return false; }
        body.work(1); village.body = body.getUuid();
        var bearer = villagers.get(0);
        clearProp(bearer); bearer.equipStack(EquipmentSlot.MAINHAND, GuardianCores.stack(record));
        bearer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
        GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
        sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_stone"), false);
        return true;
    }
    private boolean beginCarving() {
        shell().stage(3); shell().work(100);
        return true;
    }
    private boolean returnSoul() {
        var body = shell(); body.work(0); body.energy = 200; body.setHealth(26);
        var bearer = villagers.get(0); var core = bearer.getMainHandStack();
        if (!Objects.equals(GuardianCores.id(core), state().coreId) || !GuardianCores.occupy(player, body, core)) {
            failed = true; return false;
        }
        bearer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        state().ritualTicks = SCULPTED;
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_commands", state().guardianName), false);
        return true;
    }
}
