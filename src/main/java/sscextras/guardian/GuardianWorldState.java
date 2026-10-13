package sscextras.guardian;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import sscextras.rituals.RitualProgress;
import java.util.*;

public final class GuardianWorldState extends PersistentState {
    public static final long SERVICE_TICKS = 10 * 24000L;
    public final Map<UUID, Village> villages = new HashMap<>();
    public final Map<UUID, Core> cores = new HashMap<>();
    public static final class Village {
        public RegistryKey<World> world = World.OVERWORLD;
        public BlockPos pedestal = BlockPos.ORIGIN;
        public long serviceTicks, readyDay = -1;
        public boolean serviceOffered;
        public int serviceHint;
        public long serviceHintDay = -1;
        public UUID speaker;
        public int speakerTicks;
        public int ritualTicks, rebuildTicks, recoveryTicks;
        public RitualProgress awakening;
        GuardianAwakening awakeningRun;
        public final List<UUID> attendants = new ArrayList<>();
        public UUID body;
        public BlockPos bodyPos;
        public boolean eligible;
        public long lastServiceTick = Long.MIN_VALUE;
        public void resetService() {
            serviceTicks = 0; readyDay = -1; serviceHint = 0; serviceHintDay = -1;
        }
    }
    public static final class Core {
        public UUID id, owner, holder;
        public UUID rescuer, recoveryBody;
        GuardianCoreRecoveryEvent recoveryRun;
        public int recoveryTicks, insertionTicks;
        public int traderTicks;
        public boolean traderReturned;
        public RegistryKey<World> world = World.OVERWORLD;
        public BlockPos pos = BlockPos.ORIGIN;
        public String location = "body", name = "";
        public String previousMode = "survival";
        public boolean destroyed;
        public long lastSeen;
    }
    public static GuardianWorldState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(GuardianWorldState::read,
                GuardianWorldState::new, "ssc_extras_guardians");
    }
    private static RegistryKey<World> world(NbtCompound tag) {
        var id = Identifier.tryParse(tag.getString("World"));
        return id == null ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, id);
    }
    private static GuardianWorldState read(NbtCompound tag) {
        var state = new GuardianWorldState();
        var villages = tag.getList("Villages", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < villages.size(); i++) {
            var nbt = villages.getCompound(i); if (!nbt.containsUuid("Owner")) continue;
            var village = new Village(); village.world = world(nbt); village.pedestal = BlockPos.fromLong(nbt.getLong("Pedestal"));
            village.serviceTicks = Math.max(0, Math.min(SERVICE_TICKS, nbt.getLong("Service")));
            village.readyDay = nbt.contains("ReadyDay") ? nbt.getLong("ReadyDay") : -1;
            village.serviceOffered = nbt.getBoolean("ServiceOffered");
            village.serviceHint = nbt.contains("ServiceHint") ? nbt.getInt("ServiceHint")
                    : village.serviceTicks >= 8 * 24000L ? 8 : village.serviceTicks >= 5 * 24000L ? 5 : 0;
            village.serviceHintDay = nbt.contains("ServiceHintDay") ? nbt.getLong("ServiceHintDay") : -1;
            village.ritualTicks = nbt.getInt("Ritual"); village.rebuildTicks = nbt.getInt("Rebuild");
            if (village.ritualTicks > 0) village.awakening = nbt.contains("Awakening", NbtElement.COMPOUND_TYPE)
                    ? RitualProgress.read(nbt.getCompound("Awakening")) : GuardianAwakening.legacyProgress(village.ritualTicks);
            village.recoveryTicks = nbt.getInt("RecoveryTicks");
            if (nbt.containsUuid("Body")) village.body = nbt.getUuid("Body");
            if (nbt.contains("BodyPos")) village.bodyPos = BlockPos.fromLong(nbt.getLong("BodyPos"));
            var attendants = nbt.getList("Attendants", NbtElement.COMPOUND_TYPE);
            for (int a = 0; a < attendants.size(); a++) if (attendants.getCompound(a).containsUuid("Id")) village.attendants.add(attendants.getCompound(a).getUuid("Id"));
            state.villages.put(nbt.getUuid("Owner"), village);
        }
        var cores = tag.getList("Cores", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < cores.size(); i++) {
            var nbt = cores.getCompound(i); if (!nbt.containsUuid("Id") || !nbt.containsUuid("Owner")) continue;
            var core = new Core(); core.id = nbt.getUuid("Id"); core.owner = nbt.getUuid("Owner"); core.world = world(nbt);
            core.pos = BlockPos.fromLong(nbt.getLong("Pos")); core.location = nbt.getString("Location");
            core.destroyed = nbt.getBoolean("Destroyed"); core.name = nbt.getString("Name"); core.previousMode = nbt.getString("Mode");
            if (nbt.containsUuid("Holder")) core.holder = nbt.getUuid("Holder");
            if (nbt.containsUuid("Rescuer")) core.rescuer = nbt.getUuid("Rescuer");
            if (nbt.containsUuid("RecoveryBody")) core.recoveryBody = nbt.getUuid("RecoveryBody");
            core.recoveryTicks = nbt.getInt("RecoveryTicks"); core.insertionTicks = nbt.getInt("InsertionTicks");
            core.traderTicks = nbt.getInt("TraderTicks"); core.traderReturned = nbt.getBoolean("TraderReturned");
            state.cores.put(core.id, core);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        var villages = new NbtList();
        this.villages.forEach((owner, village) -> {
            var nbt = new NbtCompound(); nbt.putUuid("Owner", owner); nbt.putString("World", village.world.getValue().toString());
            nbt.putLong("Pedestal", village.pedestal.asLong()); nbt.putLong("Service", village.serviceTicks); nbt.putLong("ReadyDay", village.readyDay);
            nbt.putBoolean("ServiceOffered", village.serviceOffered); nbt.putInt("ServiceHint", village.serviceHint);
            nbt.putLong("ServiceHintDay", village.serviceHintDay);
            nbt.putInt("Ritual", village.ritualTicks); nbt.putInt("Rebuild", village.rebuildTicks);
            if (village.awakening != null) nbt.put("Awakening", village.awakening.write());
            nbt.putInt("RecoveryTicks", village.recoveryTicks);
            if (village.body != null) nbt.putUuid("Body", village.body);
            if (village.bodyPos != null) nbt.putLong("BodyPos", village.bodyPos.asLong());
            var attendants = new NbtList();
            for (var id : village.attendants) { var a = new NbtCompound(); a.putUuid("Id", id); attendants.add(a); }
            nbt.put("Attendants", attendants); villages.add(nbt);
        });
        tag.put("Villages", villages);
        var cores = new NbtList();
        this.cores.values().forEach(core -> {
            var nbt = new NbtCompound(); nbt.putUuid("Id", core.id); nbt.putUuid("Owner", core.owner);
            nbt.putString("World", core.world.getValue().toString()); nbt.putLong("Pos", core.pos.asLong());
            nbt.putString("Location", core.location); nbt.putString("Name", core.name); nbt.putString("Mode", core.previousMode);
            nbt.putBoolean("Destroyed", core.destroyed); if (core.holder != null) nbt.putUuid("Holder", core.holder);
            if (core.rescuer != null) nbt.putUuid("Rescuer", core.rescuer);
            if (core.recoveryBody != null) nbt.putUuid("RecoveryBody", core.recoveryBody);
            nbt.putInt("RecoveryTicks", core.recoveryTicks); nbt.putInt("InsertionTicks", core.insertionTicks); cores.add(nbt);
            nbt.putInt("TraderTicks", core.traderTicks); nbt.putBoolean("TraderReturned", core.traderReturned);
        });
        tag.put("Cores", cores); return tag;
    }
}
