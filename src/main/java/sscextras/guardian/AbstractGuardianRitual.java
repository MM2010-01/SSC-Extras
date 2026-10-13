package sscextras.guardian;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import sscextras.drake.DrakeOutpostOwnership;
import sscextras.rituals.AbstractRitual;
import sscextras.rituals.RitualComposition;
import sscextras.rituals.RitualProgress;
import sscextras.rituals.RitualStage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adapter for Guardian rituals using a saved pedestal claim and living villager workers. */
public abstract class AbstractGuardianRitual<C> extends AbstractRitual<C> {
    protected final ServerPlayerEntity player;
    protected final GuardianWorldState.Village village;
    protected final ServerWorld world;
    protected final Vec3d center;
    protected final RitualComposition composition;
    private final List<UUID> actors;
    private final List<UUID> participants;
    protected List<VillagerEntity> villagers = List.of();

    protected AbstractGuardianRitual(ServerPlayerEntity player, GuardianWorldState.Village village,
            RitualComposition composition, List<RitualStage<C>> definition, RitualProgress progress) {
        super(definition, progress);
        this.player = player; this.village = village; this.composition = composition;
        world = player.getServer().getWorld(village.world);
        center = GuardianPedestal.position(village.pedestal);
        actors = List.copyOf(village.attendants);
        participants = new ArrayList<>(actors); participants.add(player.getUuid());
    }
    final boolean assign() {
        return actors.size() == composition.size() && reserve(DrakeOutpostOwnership.get(player.getServer()).assignments(), participants);
    }
    protected final boolean resolveActors() {
        if (!player.isAlive() || world == null || player.getWorld() != world || !GuardianPedestal.intact(world, village.pedestal)) return false;
        villagers = actors.stream().map(world::getEntity).filter(e -> e instanceof VillagerEntity && e.isAlive())
                .map(e -> (VillagerEntity)e).toList();
        return villagers.size() == composition.size();
    }
    protected final GuardianState state() { return RuneboundGuardian.state(player); }
    protected final boolean positionActors() {
        boolean ready = true;
        for (int i = 0; i < villagers.size(); i++) {
            var villager = villagers.get(i);
            Vec3d target = GuardianRitual.safeStation(villager, composition.position(center, 1, i));
            ready &= target != null && GuardianRitual.walk(villager, target, .8);
            villager.getLookControl().lookAt(center.x, center.y + 1.6, center.z);
        }
        return ready;
    }
    @Override protected void releaseParticipants() {
        if (owns(player.getUuid())) GuardianRitual.stopEscort(player);
        if (world != null) for (var id : actors)
            if (owns(id) && world.getEntity(id) instanceof VillagerEntity villager) GuardianRitual.releaseWorker(villager);
    }
}
