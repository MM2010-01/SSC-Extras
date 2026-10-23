package sscextras.guardian;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;
import java.util.UUID;

public final class GuardianAnchorEntity extends Entity {
    private static final TrackedData<Integer> SOURCE = DataTracker.registerData(GuardianAnchorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public UUID owner;
    public GuardianAnchorEntity(EntityType<?> type, World world) { super(type, world); noClip = true; setNoGravity(true); }
    @Override public boolean isInsideWall() { return false; }
    @Override protected void initDataTracker() { dataTracker.startTracking(SOURCE, -1); }
    public void source(Entity entity) { dataTracker.set(SOURCE, entity == null ? -1 : entity.getId()); }
    public boolean follows(Entity entity) { return entity.getId() == dataTracker.get(SOURCE); }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) { owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null; }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) { if (owner != null) nbt.putUuid("Owner", owner); }
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && age > 40) {
            var player = owner == null ? null : getServer().getPlayerManager().getPlayer(owner);
            if (player == null || !RuneboundGuardian.state(player).detached && !sscextras.statue.StatueCrates.packed(player)) discard();
        }
    }
}
