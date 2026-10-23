package sscextras.statue;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** One authoritative occupant per crate identity. Items carry references, never a second entity snapshot. */
public final class StatueWorldState extends PersistentState {
    public enum Status { PACKED, RELEASED, DESTROYED }
    public static final class Occupant {
        public UUID id, entity, holder;
        public boolean player;
        public NbtCompound body = new NbtCompound(), pose = new NbtCompound();
        public RegistryKey<World> world = World.OVERWORLD;
        public Vec3d position = Vec3d.ZERO;
        public String location = "item", mode = "survival", name = "";
        public float yaw, width, height;
        public Status status = Status.PACKED;
    }
    private final Map<UUID, Occupant> occupants = new HashMap<>();
    private final Map<UUID, UUID> players = new HashMap<>();
    public static StatueWorldState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(StatueWorldState::read, StatueWorldState::new, "ssc_extras_statue_crates");
    }
    public Occupant get(UUID id) { return occupants.get(id); }
    public Occupant player(UUID player) { return get(players.get(player)); }
    public boolean isEmpty() { return occupants.isEmpty(); }
    public void add(Occupant occupant) { occupants.put(occupant.id, occupant); if (occupant.player) players.put(occupant.entity, occupant.id); markDirty(); }
    public void remove(Occupant occupant) { occupants.remove(occupant.id); if (occupant.player) players.remove(occupant.entity, occupant.id); markDirty(); }
    public static StatueWorldState read(NbtCompound tag) {
        var state = new StatueWorldState(); var list = tag.getList("Occupants", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            var n = list.getCompound(i); if (!n.containsUuid("Id") || !n.containsUuid("Entity")) continue;
            var o = new Occupant(); o.id = n.getUuid("Id"); o.entity = n.getUuid("Entity"); o.player = n.getBoolean("Player");
            o.holder = n.containsUuid("Holder") ? n.getUuid("Holder") : null;
            var dimension = Identifier.tryParse(n.getString("World"));
            if (dimension != null) o.world = RegistryKey.of(RegistryKeys.WORLD, dimension);
            o.position = new Vec3d(n.getDouble("X"), n.getDouble("Y"), n.getDouble("Z"));
            o.body = n.getCompound("Body"); o.pose = n.getCompound("Pose");
            o.location = n.getString("Location"); o.mode = n.getString("Mode"); o.name = n.getString("Name");
            o.yaw = n.getFloat("Yaw"); o.width = n.getFloat("Width"); o.height = n.getFloat("Height");
            int status = n.getInt("Status"); o.status = Status.values()[Math.max(0, Math.min(2, status))]; state.add(o);
        }
        state.setDirty(false); return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        var list = new NbtList();
        for (var o : occupants.values()) {
            var n = new NbtCompound(); n.putUuid("Id", o.id); n.putUuid("Entity", o.entity); n.putBoolean("Player", o.player);
            if (o.holder != null) n.putUuid("Holder", o.holder);
            n.putString("World", o.world.getValue().toString()); n.putDouble("X", o.position.x); n.putDouble("Y", o.position.y); n.putDouble("Z", o.position.z);
            n.put("Body", o.body); n.put("Pose", o.pose); n.putString("Location", o.location); n.putString("Mode", o.mode); n.putString("Name", o.name);
            n.putFloat("Yaw", o.yaw); n.putFloat("Width", o.width); n.putFloat("Height", o.height); n.putInt("Status", o.status.ordinal()); list.add(n);
        }
        tag.put("Occupants", list); return tag;
    }
}
