package sscextras.guardian;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each villager remembers the nearby service of individual permanent Guardians. */
public final class GuardianVillagerProtection {
    public static final long DAY = 24000;
    private static final long MAX_TICKS = 16 * DAY;
    private final Map<UUID, Service> guardians = new HashMap<>();

    public interface Access {
        GuardianVillagerProtection sscExtras$guardianProtection();
    }

    private static final class Service {
        long ticks;
        long lastTick = Long.MIN_VALUE, lastDayTime;
        WeakReference<ServerPlayerEntity> player = new WeakReference<>(null);
    }

    public static GuardianVillagerProtection get(VillagerEntity villager) {
        return ((Access)villager).sscExtras$guardianProtection();
    }

    public void sample(VillagerEntity villager, ServerPlayerEntity player) {
        if (RuneboundGuardian.stage(player) != 3 || !player.isAlive() || player.isCreative()
                || player.isSpectator() || RuneboundGuardian.state(player).detached || !villager.isAlive()
                || villager.getWorld() != player.getWorld()
                || villager.squaredDistanceTo(player) > GuardianVillages.RADIUS * GuardianVillages.RADIUS) return;
        var service = guardians.computeIfAbsent(player.getUuid(), key -> new Service());
        long now = villager.getWorld().getTime(), dayTime = villager.getWorld().getTimeOfDay();
        if (service.player.get() == player && service.lastTick != Long.MIN_VALUE
                && now >= service.lastTick && now - service.lastTick <= 20) {
            long elapsed = Math.max(0, dayTime - service.lastDayTime);
            service.ticks += Math.min(MAX_TICKS - service.ticks, elapsed);
        }
        service.lastTick = now;
        service.lastDayTime = dayTime;
        if (service.player.get() != player) service.player = new WeakReference<>(player);
    }

    public int discount(PlayerEntity player) {
        var service = guardians.get(player.getUuid());
        return service == null || RuneboundGuardian.stage(player) != 3
                || RuneboundGuardian.state(player).detached ? 0 : (int)(service.ticks / DAY) * 5;
    }

    public void prepareOffers(VillagerEntity villager, PlayerEntity player) {
        if (villager.getWorld().isClient) return;
        int percent = discount(player);
        if (percent == 0) return;
        for (var offer : villager.getOffers())
            offer.increaseSpecialPrice(-offer.getOriginalFirstBuyItem().getCount() * percent / 100);
    }

    public void read(NbtCompound nbt) {
        guardians.clear();
        var entries = nbt.getList("SscExtrasGuardianProtection", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (!entry.containsUuid("Guardian")) continue;
            var service = new Service();
            service.ticks = Math.max(0, Math.min(MAX_TICKS, entry.getLong("Ticks")));
            guardians.put(entry.getUuid("Guardian"), service);
        }
    }

    public void write(NbtCompound nbt) {
        var entries = new NbtList();
        guardians.forEach((id, service) -> {
            if (service.ticks == 0) return;
            var entry = new NbtCompound();
            entry.putUuid("Guardian", id);
            entry.putLong("Ticks", service.ticks);
            entries.add(entry);
        });
        nbt.put("SscExtrasGuardianProtection", entries);
    }
}
