package sscextras.drake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MountMarket extends PersistentState {
    public record Seller(UUID keeper, RegistryKey<World> world, BlockPos pen, boolean sold) { }
    public record Stall(UUID mount, String name) { }
    private final Map<UUID, Seller> sellers = new HashMap<>();
    private final Map<GlobalPos, Stall> stalls = new HashMap<>();
    private final Map<UUID, UUID> buyers = new HashMap<>();
    private final Map<UUID, UUID> customers = new HashMap<>();
    private final Map<GlobalPos, Vacancy> vacancies = new HashMap<>();
    private static final class Vacancy {
        final UUID keeper;
        final UUID buyer;
        final DrakeStablePiece destination;
        final java.util.List<UUID> stock;
        int elapsed;
        Vacancy(UUID keeper, UUID buyer, DrakeStablePiece destination, java.util.List<UUID> stock, int elapsed) {
            this.keeper = keeper; this.buyer = buyer; this.destination = destination;
            this.stock = new java.util.ArrayList<>(stock); this.elapsed = elapsed;
        }
    }

    public void vacateMerchant(MountMerchantEntity merchant, java.util.List<UUID> stock) {
        if (merchant.site() == null) return;
        var key = GlobalPos.create(merchant.getWorld().getRegistryKey(), merchant.site());
        vacancies.putIfAbsent(key, new Vacancy(merchant.getUuid(), buyers.get(merchant.getUuid()), merchant.destination(), stock, 0));
        markDirty();
    }

    public void stockDied(UUID mount) {
        for (var vacancy : vacancies.values()) if (vacancy.stock.remove(mount)) markDirty();
    }

    public void tickVacancies(ServerWorld world) {
        var entries = vacancies.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next(); var site = entry.getKey().getPos(); var vacancy = entry.getValue();
            if (!entry.getKey().getDimension().equals(world.getRegistryKey()) || !world.shouldTick(site)
                    || !world.shouldTick(site.add(12, 0, 10)) || !world.isRegionLoaded(site, site.add(12, 6, 10))) continue;
            if (!world.getEntitiesByClass(MountMerchantEntity.class, new net.minecraft.util.math.Box(site).expand(64),
                    merchant -> merchant.isAlive() && site.equals(merchant.site())).isEmpty()) {
                entries.remove(); markDirty(); continue;
            }
            vacancy.elapsed += 20; markDirty();
            if (vacancy.elapsed % MountMarketVisitors.INTERVAL != 0) continue;
            boolean attempt = vacancy.elapsed >= MountMarketVisitors.DEADLINE || world.random.nextInt(4) == 0;
            vacancy.elapsed = Math.min(vacancy.elapsed, MountMarketVisitors.DEADLINE);
            if (!attempt) continue;
            var merchant = MountMerchants.MERCHANT.create(world);
            if (merchant == null) continue;
            merchant.setSite(site, vacancy.destination);
            if (!MountMarketVisitors.spawn(merchant, merchant.customerPosition())) continue;
            UUID buyer = vacancy.buyer != null && buyers.containsValue(vacancy.buyer) ? vacancy.buyer : null;
            if (buyer != null) buyer(merchant.getUuid(), buyer);
            merchant.inheritShop(vacancy.stock, buyer);
            entries.remove(); markDirty();
        }
    }
    public static boolean playerOwned(PlayerEntity player) {
        return player.getServer() != null && get(player.getServer()).customers.containsKey(player.getUuid());
    }
    public UUID customer(UUID mount) { return customers.get(mount); }
    public void sellTo(UUID mount, UUID owner) { customers.put(mount, owner); release(mount); markDirty(); }

    public boolean buyerActive(UUID merchant, UUID buyer) { return buyer.equals(buyers.get(merchant)); }
    public void buyer(UUID merchant, UUID buyer) { buyers.put(merchant, buyer); markDirty(); }
    public void finishBuyer(UUID merchant, UUID buyer) {
        if (buyer.equals(buyers.get(merchant))) { buyers.values().removeIf(buyer::equals); markDirty(); }
    }

    public static MountMarket get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(MountMarket::read, MountMarket::new, "ssc_extras_mount_market");
    }
    public Seller seller(UUID player) { return sellers.get(player); }
    public void offer(PlayerEntity player, UUID keeper, BlockPos pen, boolean sold) {
        sellers.put(player.getUuid(), new Seller(keeper, player.getWorld().getRegistryKey(), pen.toImmutable(), sold)); markDirty();
    }
    public void release(UUID player) { if (sellers.remove(player) != null) markDirty(); }
    public boolean busy(PlayerEntity player) {
        var seller = sellers.get(player.getUuid());
        if (seller == null) return false;
        if (!player.isAlive() || !seller.world().equals(player.getWorld().getRegistryKey())
                || !seller.sold() && !seller.pen().isWithinDistance(player.getPos(), 40)) {
            release(player.getUuid()); return false;
        }
        return true;
    }
    public boolean reserved(ServerWorld world, DrakeStablePiece stable, int stall, UUID except) {
        var entry = stalls.get(key(world, stable, stall));
        return entry != null && !entry.mount().equals(except);
    }
    public String name(ServerWorld world, DrakeStablePiece stable, int stall) {
        var entry = stalls.get(key(world, stable, stall)); return entry == null ? "" : entry.name();
    }
    public boolean reserve(ServerWorld world, DrakeStablePiece stable, int stall, UUID mount, String name) {
        if (!(world.getEntity(mount) instanceof StableDrakeEntity drake) || !stable.stall(stall).contains(drake.getPos())
                || !(drake.getHoldingEntity() instanceof net.minecraft.entity.decoration.LeashKnotEntity knot)
                || !knot.getDecorationBlockPos().equals(stable.tie(stall))) return false;
        if (!DrakeOutpostOwnership.availableFor(world, stable, stall, mount)) return false;
        if (!world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, stable.stall(stall), entity -> entity.isAlive()
                && !entity.isSpectator() && !entity.getUuid().equals(mount) && !(entity instanceof net.minecraft.entity.mob.PillagerEntity)).isEmpty()) return false;
        stalls.put(key(world, stable, stall), new Stall(mount, name)); markDirty(); return true;
    }
    public void vacate(ServerWorld world, DrakeStablePiece stable, int stall, UUID mount) {
        var key = key(world, stable, stall); var entry = stalls.get(key);
        if (entry != null && entry.mount().equals(mount)) { stalls.remove(key); markDirty(); }
    }
    private static GlobalPos key(ServerWorld world, DrakeStablePiece stable, int stall) {
        return GlobalPos.create(world.getRegistryKey(), stable.sign(stall));
    }
    public static NbtCompound writeStable(DrakeStablePiece stable) {
        var tag = new NbtCompound();
        if (stable == null) return tag;
        var box = stable.getBoundingBox();
        tag.putLong("Origin", new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()).asLong());
        tag.putInt("Stalls", stable.stallCount()); tag.putBoolean("Facing", DrakeStableLayout.facingRows(box));
        return tag;
    }
    public static DrakeStablePiece readStable(NbtCompound tag) {
        int stalls = tag.getInt("Stalls");
        if (!tag.contains("Origin") || stalls < 2 || stalls > 32) return null;
        var pos = BlockPos.fromLong(tag.getLong("Origin"));
        return new DrakeStablePiece(pos.getX(), pos.getY(), pos.getZ(), stalls, tag.getBoolean("Facing"));
    }
    public static MountMarket read(NbtCompound tag) {
        var state = new MountMarket();
        for (var element : tag.getList("VacantShops", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (!entry.containsUuid("Keeper")) continue;
            var stock = new java.util.ArrayList<UUID>();
            for (var id : entry.getList("Stock", NbtElement.INT_ARRAY_TYPE)) stock.add(net.minecraft.nbt.NbtHelper.toUuid(id));
            int elapsed = Math.max(0, Math.min(MountMarketVisitors.DEADLINE + MountMarketVisitors.INTERVAL - 20, entry.getInt("Elapsed"))) / 20 * 20;
            state.vacancies.put(GlobalPos.create(dimension(entry), BlockPos.fromLong(entry.getLong("Site"))),
                    new Vacancy(entry.getUuid("Keeper"), entry.containsUuid("Buyer") ? entry.getUuid("Buyer") : null,
                            readStable(entry.getCompound("Destination")), stock, elapsed));
        }
        for (var element : tag.getList("Customers", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Mount") && entry.containsUuid("Owner")) state.customers.put(entry.getUuid("Mount"), entry.getUuid("Owner"));
        }
        for (var element : tag.getList("Buyers", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Merchant") && entry.containsUuid("Buyer")) state.buyers.put(entry.getUuid("Merchant"), entry.getUuid("Buyer"));
        }
        for (var element : tag.getList("Sellers", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Player") && entry.containsUuid("Keeper")) state.sellers.put(entry.getUuid("Player"),
                    new Seller(entry.getUuid("Keeper"), dimension(entry), BlockPos.fromLong(entry.getLong("Pen")), entry.getBoolean("Sold")));
        }
        for (var element : tag.getList("Stalls", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Mount")) state.stalls.put(GlobalPos.create(dimension(entry), BlockPos.fromLong(entry.getLong("Sign"))),
                    new Stall(entry.getUuid("Mount"), entry.getString("Name")));
        }
        return state;
    }
    private static RegistryKey<World> dimension(NbtCompound tag) { return RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("World"))); }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        var shops = new NbtList();
        vacancies.forEach((pos, vacancy) -> {
            var entry = new NbtCompound(); entry.putString("World", pos.getDimension().getValue().toString());
            entry.putLong("Site", pos.getPos().asLong()); entry.putUuid("Keeper", vacancy.keeper);
            if (vacancy.buyer != null) entry.putUuid("Buyer", vacancy.buyer);
            entry.put("Destination", writeStable(vacancy.destination)); entry.putInt("Elapsed", vacancy.elapsed);
            var stock = new NbtList(); vacancy.stock.forEach(id -> stock.add(net.minecraft.nbt.NbtHelper.fromUuid(id)));
            entry.put("Stock", stock); shops.add(entry);
        });
        tag.put("VacantShops", shops);
        var owners = new NbtList();
        customers.forEach((mount, owner) -> {
            var entry = new NbtCompound(); entry.putUuid("Mount", mount); entry.putUuid("Owner", owner); owners.add(entry);
        });
        tag.put("Customers", owners);
        var visitors = new NbtList();
        buyers.forEach((merchant, buyer) -> {
            var entry = new NbtCompound(); entry.putUuid("Merchant", merchant); entry.putUuid("Buyer", buyer); visitors.add(entry);
        });
        tag.put("Buyers", visitors);
        var offers = new NbtList();
        sellers.forEach((id, seller) -> {
            var entry = new NbtCompound(); entry.putUuid("Player", id); entry.putUuid("Keeper", seller.keeper());
            entry.putString("World", seller.world().getValue().toString()); entry.putLong("Pen", seller.pen().asLong());
            entry.putBoolean("Sold", seller.sold()); offers.add(entry);
        });
        var homes = new NbtList();
        stalls.forEach((pos, stall) -> {
            var entry = new NbtCompound(); entry.putString("World", pos.getDimension().getValue().toString());
            entry.putLong("Sign", pos.getPos().asLong()); entry.putUuid("Mount", stall.mount()); entry.putString("Name", stall.name()); homes.add(entry);
        });
        tag.put("Sellers", offers); tag.put("Stalls", homes); return tag;
    }
}
