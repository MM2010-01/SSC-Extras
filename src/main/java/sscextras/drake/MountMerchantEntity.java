package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import sscextras.rituals.RitualProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MountMerchantEntity extends PillagerEntity {
    public static final double MOVE_SPEED = 1.2;
    private static final int VILLAGE_AGGRO_TICKS = 24000;
    private final java.util.Map<UUID, Long> villageAggressors = new java.util.HashMap<>();
    private static final int BUYER_INTERVAL = MountMarketVisitors.INTERVAL, BUYER_DEADLINE = MountMarketVisitors.DEADLINE;
    private BlockPos site;
    private DrakeStablePiece destination;
    private final List<UUID> stock = new ArrayList<>();
    private final List<UUID> inheritedStock = new ArrayList<>();
    private UUID seller, buyer;
    private final List<UUID> waiting = new ArrayList<>();
    private final java.util.Map<UUID, Integer> escapes = new java.util.HashMap<>();
    private UUID runaway;
    private boolean captured;
    private final java.util.Map<UUID, Integer> quotes = new java.util.HashMap<>();
    private final MountMerchantSigns signs = new MountMerchantSigns();
    private boolean listed;
    public record Listing(UUID id, int entityId, Text name, int price, boolean player) { }
    public int quote(PlayerEntity player) {
        int price = quotes.computeIfAbsent(player.getUuid(), id -> (int)Math.ceil(MountMerchants.PRICE * (110 + random.nextInt(91)) / 100.0));
        return sscextras.collar.TamingCollar.worn(player) ? (price + 1) / 2 : price;
    }
    public List<Listing> listings() {
        var result = new ArrayList<Listing>();
        for (var drake : stock()) result.add(new Listing(drake.getUuid(), drake.getId(), drake.getName(), MountMerchants.PRICE, false));
        var player = seller();
        for (var mount : readySellers()) if (!mount.getUuid().equals(seller))
            result.add(new Listing(mount.getUuid(), mount.getId(), Text.literal(MountMerchantForms.name(mount)), quote(mount), true));
        if (listed && player != null && player.isAlive() && !BondOfTheBeastCompat.hasOwner(player) && pen().contains(player.getPos()))
            result.add(new Listing(player.getUuid(), player.getId(), Text.literal(MountMerchantForms.name(player)), quote(player), true));
        return result;
    }
    public boolean purchasable(UUID id, PlayerEntity customer) {
        if (isMerchandise(customer) || id.equals(customer.getUuid()) || buyer != null) return false;
        if (readySellers().stream().anyMatch(player -> player.getUuid().equals(id))) return true;
        return (seller == null || ready()) && stock().stream().anyMatch(drake -> id.equals(drake.getUuid()));
    }
    public void registerSeller() { listed = true; DrakeOutpostOwnership.release(seller()); updateSigns(); }
    public BlockPos sign(int slot) { return MountMerchantSigns.position(site, slot); }
    public void updateSigns() {
        if (!(getWorld() instanceof ServerWorld world) || site == null) return;
        signs.update(world, site, listings());
    }
    private RitualProgress conversion = new RitualProgress();
    private boolean pretend, stocked, arriving;
    private int nextBuyer = BUYER_INTERVAL, buyerWait;
    private MountConversionRitual ritual;
    private DrakeStableRepairGoal repairGoal;

    public MountMerchantEntity(EntityType<? extends PillagerEntity> type, World world) {
        super(type, world); setPersistent(); arm();
    }
    private void arm() {
        var bow = getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND);
        if (bow.isEmpty()) {
            bow = new ItemStack(Items.CROSSBOW);
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, bow);
        }
        if (!bow.isOf(Items.CROSSBOW)) return;
        var enchantments = net.minecraft.enchantment.EnchantmentHelper.get(bow);
        enchantments.merge(net.minecraft.enchantment.Enchantments.QUICK_CHARGE, 3, Math::max);
        enchantments.merge(net.minecraft.enchantment.Enchantments.UNBREAKING, 3, Math::max);
        net.minecraft.enchantment.EnchantmentHelper.set(enchantments, bow);
    }
    public void setSite(BlockPos site, DrakeStablePiece destination) { this.site = site.toImmutable(); this.destination = destination; }
    public BlockPos site() { return site; }
    public DrakeStablePiece destination() { return destination; }
    void inheritShop(List<UUID> stock, UUID buyer) {
        this.stock.addAll(stock); inheritedStock.addAll(stock); stocked = true; arriving = true;
        this.buyer = buyer;
        recoverStock();
        getNavigation().startMovingTo(counter().x, counter().y, counter().z, MOVE_SPEED);
    }
    public boolean refreshDestination(UUID mount, int requiredStalls) {
        if (!(getWorld() instanceof ServerWorld world) || site == null) return false;
        java.util.function.Predicate<DrakeStablePiece> available = stable -> MountBuyerEntity.hasFreeStalls(world, stable, mount, requiredStalls);
        var nearest = DrakeCaptureGoal.findStable(world, site, 384, available);
        boolean currentAvailable = destination != null && (destination == nearest || available.test(destination));
        if (nearest != null && (!currentAvailable || nearest.getBoundingBox().getCenter().getSquaredDistance(site)
                < destination.getBoundingBox().getCenter().getSquaredDistance(site))) destination = nearest;
        return nearest != null || currentAvailable;
    }
    public boolean hasDemand(PlayerEntity player) { return refreshDestination(player.getUuid(), 1); }
    public boolean holdsOpen(BlockPos gate) {
        return site != null && gate.equals(gate()) && (repairGoal.active() || seller != null && !pen().contains(seller() == null ? Vec3d.ZERO : seller().getPos())
                || (seller == null || ready()) && pen().contains(getPos())
                || getWorld().getPlayers().stream().anyMatch(player -> pen().contains(player.getPos())
                        && (MountMarket.playerOwned(player) && DrakeLeashing.holder(player) instanceof PlayerEntity
                        || !DrakeLeashing.attached(player) && !MountMarket.get(getServer()).busy(player)))
                || !getWorld().getEntitiesByClass(StableDrakeEntity.class, pen(), drake -> drake.isAlive() && drake.merchant() == null
                        && (drake.customer() != null || drake.getHoldingEntity() instanceof MountBuyerEntity)).isEmpty());
    }
    public BlockPos gate() { return site.add(8, 1, 3); }
    public Vec3d counter() { return Vec3d.ofBottomCenter(site.add(3, 1, 1)); }
    public Vec3d customerPosition() { return Vec3d.ofBottomCenter(site.add(3, 1, -1)); }
    public boolean atCounter() { return squaredDistanceTo(counter()) < 1; }
    public BlockPos tie() { return site.add(6, 1, 7); }
    public Vec3d inside() { return Vec3d.ofBottomCenter(site.add(8, 1, 8)).add(.5, 0, 0); }
    public Box pen() { return pen(site); }
    public static Box pen(BlockPos site) { return new Box(site.add(0, 1, 3), site.add(13, 5, 11)).contract(.625, 0, .625); }
    public UUID sellerId() { return seller; }
    public boolean ready() { return seller != null && conversion.finished(); }
    public boolean capturedSeller() { return captured; }
    public boolean hasBuyer() { return buyer != null; }
    public ServerPlayerEntity seller() {
        return seller != null && getWorld() instanceof ServerWorld world && world.getEntity(seller) instanceof ServerPlayerEntity player ? player : null;
    }
    public List<ServerPlayerEntity> readySellers() {
        if (waiting.isEmpty() && !ready()) return List.of();
        var ids = new ArrayList<>(waiting);
        if (ready()) ids.add(seller);
        var world = (ServerWorld)getWorld();
        var bounds = site == null ? null : pen();
        return ids.stream().map(world::getEntity).filter(entity -> entity instanceof ServerPlayerEntity)
                .map(entity -> (ServerPlayerEntity)entity).filter(player -> player.isAlive() && EarthenDrake.stage(player) >= 2
                        && !BondOfTheBeastCompat.hasOwner(player) && bounds.contains(player.getPos())).toList();
    }
    private void beginOffer(ServerPlayerEntity player, boolean pretend) {
        if (ready()) { waiting.add(seller); interrupt(); }
        getNavigation().stop();
        seller = player.getUuid(); this.pretend = pretend; listed = false; conversion = new RitualProgress();
        captured = false;
    }
    public List<StableDrakeEntity> stock() {
        if (!(getWorld() instanceof ServerWorld world) || site == null || stock.isEmpty()) return List.of();
        var bounds = pen().expand(2);
        return stock.stream().map(world::getEntity).filter(entity -> entity instanceof StableDrakeEntity)
                .map(entity -> (StableDrakeEntity)entity).filter(drake -> availableStock(drake, bounds)).toList();
    }
    private boolean availableStock(StableDrakeEntity drake, Box bounds) {
        return drake.isAlive() && getUuid().equals(drake.merchant()) && bounds.contains(drake.getPos()) && !drake.hasPassengers();
    }
    private StableDrakeEntity firstStock() {
        if (!(getWorld() instanceof ServerWorld world) || site == null || stock.isEmpty()) return null;
        var bounds = pen().expand(2);
        for (var id : stock)
            if (world.getEntity(id) instanceof StableDrakeEntity drake && availableStock(drake, bounds)) return drake;
        return null;
    }
    boolean hasStock() { return firstStock() != null; }
    public void addStock(StableDrakeEntity drake) {
        if (!stock.contains(drake.getUuid())) stock.add(drake.getUuid());
        inheritedStock.remove(drake.getUuid());
        drake.clearStableHome(); drake.setMerchant(getUuid(), tie());
        if (getWorld().getBlockState(tie()).isIn(net.minecraft.registry.tag.BlockTags.FENCES))
            drake.attachLeash(LeashKnotEntity.getOrCreate(getWorld(), tie()), true);
        updateSigns();
    }
    public void removeStock(StableDrakeEntity drake) { stock.remove(drake.getUuid()); inheritedStock.remove(drake.getUuid()); updateSigns(); }
    public boolean isMerchandise(PlayerEntity player) {
        var entry = MountMarket.get(getServer()).seller(player.getUuid());
        return entry != null && !entry.sold() && entry.keeper().equals(getUuid());
    }
    public boolean canOffer(PlayerEntity player) {
        return site != null && !arriving && player.isAlive() && !player.isSpectator() && MountMerchantForms.dialogue(player) != 4
                && !MountMerchantForms.villageFaction(player) && !isMerchandise(player);
    }
    private boolean canAccept(PlayerEntity player) {
        return canOffer(player) && MountMerchantForms.convertible(player) && (seller == null || ready()) && buyer == null
                && !player.hasVehicle() && !player.hasPassengers() && !DrakeLeashing.attached(player)
                && !BondOfTheBeastCompat.hasOwner(player) && !MountMarket.playerOwned(player)
                && !MountMarket.get(getServer()).busy(player) && DrakeOutpostOwnership.get(getServer()).assignments().owner(player.getUuid()) == null
                && equipmentAvailable(player, DrakeEquipment.REINS) && equipmentAvailable(player, DrakeEquipment.SADDLE);
    }
    private static boolean equipmentAvailable(PlayerEntity player, DrakeAccessoryItem item) {
        return !DrakeEquipment.equipped(player, item).isEmpty() || DrakeEquipment.stacks(player, item).stream().anyMatch(ItemStack::isEmpty);
    }
    public boolean offer(ServerPlayerEntity player, boolean pretend) {
        if (!canAccept(player) || squaredDistanceTo(player) > 64 || !getVisibilityCache().canSee(player)) return false;
        if (!hasDemand(player)) { say(player, "offer.no_demand"); return false; }
        if (EarthenDrake.stage(player) < 0 && !hasStock()) { say(player, "offer.no_stock"); return false; }
        beginOffer(player, pretend);
        MountMarket.get(getServer()).offer(player, getUuid(), site, false);
        say(player, pretend ? "pretend" : "accepted");
        return true;
    }
    public boolean buy(ServerPlayerEntity player) {
        var first = firstStock();
        return first != null && buy(player, first.getUuid());
    }
    public boolean buy(ServerPlayerEntity player, UUID id) {
        if (site == null || squaredDistanceTo(player) > 64 || !getVisibilityCache().canSee(player) || !purchasable(id, player)) return false;
        var listing = listings().stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
        if (listing == null) return false;
        int price = MountMerchantForms.customerPrice(listing.price(), player);
        if (emeralds(player) < price) return false;
        if (listing.player()) {
            var mount = ((ServerWorld)getWorld()).getEntity(id) instanceof ServerPlayerEntity target ? target : null;
            if (mount == null || BondOfTheBeastCompat.hasOwner(mount) || !DrakeLeashing.attach(mount, player)) return false;
            MountMarket.get(getServer()).sellTo(id, player.getUuid());
            BondOfTheBeastCompat.registerPurchasedPlayer(mount, player, listing.name().getString());
            say(mount, "sold"); removeSeller(id);
        } else {
            var drake = (StableDrakeEntity)((ServerWorld)getWorld()).getEntity(id);
            drake.setMerchant(null, null); drake.setCustomer(player.getUuid()); drake.setTarget(null);
            drake.attachLeash(player, true); removeStock(drake);
        }
        int cost = price;
        for (int slot = 0; slot < player.getInventory().size() && cost > 0; slot++) {
            var stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.EMERALD)) continue;
            int taken = Math.min(cost, stack.getCount()); stack.decrement(taken); cost -= taken;
        }
        ((DrakeStableNavigation)getNavigation()).open(gate()); updateSigns();
        player.getInventory().markDirty();
        player.sendMessage(Text.translatable("message.ssc-extras.merchant.purchase", listing.name(), price), false); return true;
    }
    private void recoverStock() {
        var world = (ServerWorld)getWorld();
        for (var drake : world.getEntitiesByClass(StableDrakeEntity.class, pen(), entity -> entity.isAlive()
                && entity.customer() == null && !entity.hasPassengers())) {
            var holder = drake.getHoldingEntity();
            if (holder instanceof MountBuyerEntity || holder instanceof PlayerEntity) continue;
            if (drake.merchant() != null && !getUuid().equals(drake.merchant()) && !inheritedStock.contains(drake.getUuid())) continue;
            if (inheritedStock.contains(drake.getUuid())) addStock(drake);
            if (getUuid().equals(drake.merchant()) || buyer == null && (holder == null
                    || holder instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(tie()))) {
                if (!stock.contains(drake.getUuid()) || !(holder instanceof LeashKnotEntity knot) || !knot.getDecorationBlockPos().equals(tie())) addStock(drake);
            }
        }
    }
    private void recruit() {
        if (seller != null && !ready() || buyer != null || DrakeFaction.fighting(this)) return;
        for (var player : ((ServerWorld)getWorld()).getPlayers(player -> squaredDistanceTo(player) <= 256
                && !player.isCreative() && canAccept(player) && getVisibilityCache().canSee(player))) {
            var line = MountMerchantForms.recruit(player);
            if (line == null || !hasDemand(player) || EarthenDrake.stage(player) < 0 && !hasStock()) continue;
            beginOffer(player, false);
            captured = true;
            MountMarket.get(getServer()).offer(player, getUuid(), site, false);
            player.sendMessage(Text.translatable("message.ssc-extras.merchant." + line, MountMerchantForms.name(player)), false); break;
        }
    }
    public static int emeralds(PlayerEntity player) { return player.getInventory().count(Items.EMERALD); }
    public static void say(PlayerEntity player, String message) {
        String name = MountMerchantForms.name(player);
        player.sendMessage(Text.translatable("message.ssc-extras.merchant." + message, name, name), false);
    }
    public void sold(MountBuyerEntity buyer) {
        this.buyer = buyer.getUuid();
        for (var purchase : buyer.purchases()) if (purchase.player()) removeSeller(purchase.mount());
        updateSigns();
    }
    private void removeSeller(UUID id) {
        waiting.remove(id);
        if (id.equals(seller)) { interrupt(); seller = null; listed = false; conversion = new RitualProgress(); }
    }
    private void releaseWithoutDemand() {
        if (seller == null && waiting.isEmpty()) return;
        var world = (ServerWorld)getWorld(); var market = MountMarket.get(getServer());
        var candidates = new ArrayList<>(waiting);
        if (seller != null) candidates.add(seller);
        boolean released = false;
        for (var id : candidates) {
            var entry = market.seller(id);
            if (entry == null || entry.sold() || !entry.keeper().equals(getUuid())
                    || !(world.getEntity(id) instanceof ServerPlayerEntity player) || !player.isAlive() || hasDemand(player)) continue;
            removeSeller(id); market.release(id); escapes.remove(id); quotes.remove(id);
            if (id.equals(runaway)) { runaway = null; getNavigation().stop(); }
            var holder = DrakeLeashing.holder(player);
            if (holder == this || holder instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(tie()))
                DrakeLeashing.detach(player, false);
            say(player, "released"); released = true;
        }
        if (released) { ((DrakeStableNavigation)getNavigation()).open(gate()); updateSigns(); }
    }
    public void buyerFinished(UUID id) { if (id.equals(buyer)) { buyer = null; nextBuyer = BUYER_INTERVAL; buyerWait = 0; } }
    public void recoverSeller(ServerPlayerEntity player) {
        if (!player.isAlive() || EarthenDrake.stage(player) < 2 || !pen().contains(player.getPos()) || MountMarket.playerOwned(player)) return;
        if (!player.getUuid().equals(seller) && !waiting.contains(player.getUuid())) waiting.add(player.getUuid());
        MountMarket.get(getServer()).offer(player, getUuid(), site, false);
        DrakeLeashing.attachPillager(player, LeashKnotEntity.getOrCreate(getWorld(), tie())); updateSigns();
    }
    public void cancelOffer() {
        var player = seller();
        interrupt();
        if (seller != null) MountMarket.get(getServer()).release(seller);
        if (player != null) {
            var holder = DrakeLeashing.holder(player);
            if (holder == this || holder instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(tie())) DrakeLeashing.detach(player, false);
            say(player, "interrupted");
        }
        seller = null; runaway = null; listed = false; conversion = new RitualProgress(); updateSigns();
    }
    private boolean chaseRunaway() {
        if (runaway == null && seller == null && waiting.isEmpty()) return false;
        var world = (ServerWorld)getWorld(); var market = MountMarket.get(getServer());
        if (runaway == null) {
            var candidates = new ArrayList<UUID>();
            if (seller != null && listed) candidates.add(seller);
            if (seller == null || ready()) candidates.addAll(waiting);
            for (var id : candidates) {
                if (!(world.getEntity(id) instanceof ServerPlayerEntity player) || !player.isAlive()
                        || player.hasPassengers() || player.hasVehicle() || !market.busy(player)) continue;
                if (pen().contains(player.getPos())) continue;
                if (DrakeLeashing.holder(player) instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(tie()))
                    DrakeLeashing.detach(player, false);
                if (DrakeLeashing.attached(player)) continue;
                runaway = id; interrupt(); say(player, "escape_chase"); break;
            }
        }
        if (runaway == null) return false;
        var id = runaway;
        if (!(world.getEntity(id) instanceof ServerPlayerEntity player) || !market.busy(player)
                || !MountMerchantForms.convertible(player) || player.hasPassengers() || player.hasVehicle()) {
            runaway = null;
            if (id.equals(seller)) cancelOffer(); else { waiting.remove(id); market.release(id); }
            return false;
        }
        if (DrakeLeashing.attached(player)) { runaway = null; return false; }
        ((DrakeStableNavigation)getNavigation()).open(gate());
        getLookControl().lookAt(player, 30, 30);
        if (squaredDistanceTo(player) > 4 || !getVisibilityCache().canSee(player)) {
            if (getNavigation().isIdle() || age % 10 == 0) getNavigation().startMovingTo(player, MOVE_SPEED);
            return true;
        }
        if (!DrakeLeashing.attachPillager(player, this)) return true;
        int count = escapes.merge(id, 1, Integer::sum);
        if (count >= 3 && !sscextras.collar.TamingCollar.worn(player)) {
            String name = MountMerchantForms.name(player);
            if (sscextras.collar.TamingCollar.equip(player)) {
                for (var slot : sscextras.collar.CollarSlots.get(player)) if (slot.stack().isOf(sscextras.collar.Collars.TAMING))
                    slot.stack().setCustomName(Text.translatable("item.ssc-extras.named_cursed_taming_collar", name));
                say(player, "escape_collar");
            }
        } else say(player, "escape_caught");
        if (!id.equals(seller) && ready()) waiting.add(seller);
        waiting.remove(id); seller = id; captured = true; pretend = false; listed = true;
        conversion = new RitualProgress(); runaway = null; updateSigns(); return true;
    }
    public void interrupt() { if (ritual != null) { ritual.finish(); ritual = null; } }
    boolean canRepairStructure() {
        return site != null && isAlive() && !isRemoved() && !isAiDisabled() && !hasVehicle() && !hasPassengers()
                && !arriving && !DrakeFaction.fighting(this) && buyer == null && runaway == null && (seller == null || ready());
    }
    @Override protected void initGoals() {
        super.initGoals(); goalSelector.clear(goal -> true); targetSelector.clear(goal -> true);
        goalSelector.add(0, new SwimGoal(this)); goalSelector.add(1, new CrossbowAttackGoal<>(this, MOVE_SPEED, 16));
        goalSelector.add(3, repairGoal = new DrakeStableRepairGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8)); goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(0, new RevengeGoal(this) {
            @Override public void start() {
                super.start();
                if (villageAggressor(getTarget())) setMaxTimeWithoutVisibility(VILLAGE_AGGRO_TICKS);
            }
        });
        targetSelector.add(1, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false, this::canTarget) {
            @Override public void start() {
                super.start();
                setMaxTimeWithoutVisibility(villageAggressor(getTarget()) ? VILLAGE_AGGRO_TICKS : 60);
            }
        });
    }
    @Override public boolean canTarget(LivingEntity target) {
        return super.canTarget(target) && !DrakeFaction.member(target) && !isTeammate(target)
                && (villageAggressor(target) || target == getAttacker() || site != null && pen().expand(16).contains(target.getPos())
                && DrakeFaction.hostileThreat(target));
    }
    private boolean villageAggressor(LivingEntity target) {
        if (!(target instanceof PlayerEntity)) return false;
        Long seen = villageAggressors.get(target.getUuid());
        return seen != null && getWorld().getTime() - seen < VILLAGE_AGGRO_TICKS;
    }
    @Override public boolean damage(DamageSource source, float amount) {
        boolean damaged = super.damage(source, amount);
        if (damaged && isAlive() && !getWorld().isClient && source.getAttacker() instanceof PlayerEntity player
                && MountMerchantForms.villageFaction(player)) villageAggressors.put(player.getUuid(), getWorld().getTime());
        return damaged;
    }
    private void tickVillageAggressors(ServerWorld world) {
        long now = world.getTime();
        var iterator = villageAggressors.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = world.getPlayerByUuid(entry.getKey());
            if (now - entry.getValue() >= VILLAGE_AGGRO_TICKS || player != null && !player.isAlive()) {
                if (getTarget() != null && entry.getKey().equals(getTarget().getUuid())) setTarget(null);
                if (getAttacker() != null && entry.getKey().equals(getAttacker().getUuid())) setAttacker(null);
                iterator.remove();
            } else if (player != null && DrakeRoaming.canSee(this, player)) entry.setValue(now);
        }
    }
    @Override public void tickMovement() {
        double x = getX(), z = getZ();
        super.tickMovement();
        if (!getWorld().isClient) {
            double dx = getX() - x, dz = getZ() - z;
            setSprinting(dx * dx + dz * dz > 1.0E-4);
        }
    }
    @Override protected void mobTick() {
        if (getWorld() instanceof ServerWorld world) tickVillageAggressors(world);
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world) || site == null) return;
        if (!stocked && world.isRegionLoaded(site, site.add(12, 6, 10))) { recoverStock(); restock(); stocked = true; }
        if (age % 40 == 0) { recoverStock(); updateSigns(); }
        if (DrakeFaction.fighting(this)) {
            interrupt();
            ((DrakeFaction.EquipmentDisplay)(Object)this).sscExtras$showEquipment(ItemStack.EMPTY);
            ((DrakeStableNavigation)getNavigation()).open(gate());
            return;
        }
        if (arriving) {
            returnToCounter();
            if (atCounter()) {
                arriving = false;
                for (var player : world.getPlayers(player -> pen().contains(player.getPos()))) say(player, "new_keeper");
            }
            return;
        }
        if (age % 20 == 0) { releaseWithoutDemand(); recruit(); }
        if (!DrakeFaction.fighting(this) && chaseRunaway()) return;
        if (age % 20 == 0) {
            for (var id : List.copyOf(waiting)) {
                var entry = MountMarket.get(getServer()).seller(id);
                if (entry == null || !entry.keeper().equals(getUuid())) { waiting.remove(id); continue; }
                if (world.getEntity(id) instanceof ServerPlayerEntity player) {
                    if (!MountMarket.get(getServer()).busy(player)) { waiting.remove(id); continue; }
                }
            }
        }
        if (seller != null) {
            var entry = MountMarket.get(getServer()).seller(seller);
            if (entry == null || !entry.keeper().equals(getUuid())) { cancelOffer(); return; }
            var player = seller();
            if (player != null && !MountMarket.get(getServer()).busy(player)) { cancelOffer(); return; }
            if (player != null && player.isAlive() && !ready() && !DrakeFaction.fighting(this)) {
                if (ritual == null) ritual = new MountConversionRitual(this, player, conversion, pretend);
                if (!ritual.tick()) cancelOffer();
            } else {
                interrupt();
            }
        }
        if ((seller == null || ready()) && getTarget() == null && !repairGoal.active()) {
            returnToCounter();
        }
        if (buyer != null) {
            if (!MountMarket.get(getServer()).buyerActive(getUuid(), buyer)) buyerFinished(buyer);
            return;
        }
        buyerWait = Math.min(BUYER_DEADLINE, buyerWait + 1);
        if (--nextBuyer <= 0) {
            nextBuyer = BUYER_INTERVAL;
            if ((seller == null || ready()) && (buyerWait >= BUYER_DEADLINE || random.nextInt(4) == 0)) {
                var spawned = MountBuyerEntity.spawn(this);
                if (spawned != null) { buyer = spawned.getUuid(); buyerWait = 0; }
            }
        }
        if (age % 20 == 0 && seller == null && waiting.isEmpty() && !hasStock()) restock();
    }
    private void returnToCounter() {
        if (!atCounter()) {
            ((DrakeStableNavigation)getNavigation()).open(gate());
            double gateX = site.getX() + 9;
            if (pen().contains(getPos()) || Math.abs(getX() - gateX) < 1 && getZ() > site.getZ() + 2) {
                var point = new Vec3d(gateX, site.getY() + 1,
                        Math.abs(getX() - gateX) < .2 ? site.getZ() + 1.5 : site.getZ() + 5.5);
                getNavigation().stop(); getMoveControl().moveTo(point.x, point.y, point.z, MOVE_SPEED);
            } else if (age % 10 == 0 || getNavigation().isIdle()) getNavigation().startMovingAlong(getNavigation().findPathTo(counter().x, counter().y, counter().z, 0), MOVE_SPEED);
        } else {
            getNavigation().stop();
            if (buyer == null) getLookControl().lookAt(customerPosition().x, customerPosition().y + 1, customerPosition().z);
        }
    }
    private void restock() {
        var world = (ServerWorld)getWorld();
        if (hasStock() || !world.isRegionLoaded(site, site.add(12, 6, 10))
                || stocked && world.getPlayers().stream().anyMatch(player -> player.squaredDistanceTo(counter()) <= 64 * 64)) return;
        // Missing UUIDs may be unloaded, so only retire stock known to be dead or sold.
        stock.removeIf(id -> world.getEntity(id) instanceof StableDrakeEntity drake
                && (!drake.isAlive() || !inheritedStock.contains(id) && !getUuid().equals(drake.merchant())));
        inheritedStock.retainAll(stock);
        if (!stock.isEmpty()) return;
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            var drake = DrakeStable.DRAKE.create(world);
            drake.refreshPositionAndAngles(site.getX() + 3 + i * 6, site.getY() + 1, site.getZ() + 7, 180, 0);
            drake.setCustomName(Text.literal(DrakeMountNames.choose(random)));
            if (world.spawnEntity(drake)) addStock(drake);
        }
    }
    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!isAlive() || player.isSpectator() || getTarget() != null) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (MountMerchantForms.dialogue(player) == 4) { say(player, "greeting.feral"); return ActionResult.CONSUME; }
            var offers = listings();
            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override public Text getDisplayName() { return MountMerchantEntity.this.getDisplayName(); }
                @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity ignored) {
                    return new MountMerchantScreenHandler(syncId, inventory, MountMerchantEntity.this, offers);
                }
                @Override public void writeScreenOpeningData(ServerPlayerEntity customer, PacketByteBuf buf) {
                    MountMerchantScreenHandler.writeListings(buf, offers);
                }
            });
        }
        return ActionResult.success(getWorld().isClient);
    }
    @Override public void onDeath(DamageSource source) {
        if (!getWorld().isClient) {
            MountMarket.get(getServer()).vacateMerchant(this, stock);
            cancelOffer();
            for (var id : waiting) {
                MountMarket.get(getServer()).release(id);
                if (((ServerWorld)getWorld()).getEntity(id) instanceof ServerPlayerEntity player) DrakeLeashing.detach(player, false);
            }
            waiting.clear();
        }
        super.onDeath(source);
    }
    @Override public boolean canJoinRaid() { return false; }
    @Override public boolean canLead() { return false; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public boolean isDisallowedInPeaceful() { return false; }
    @Override public void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        var aggressors = new NbtList();
        villageAggressors.forEach((id, seen) -> { var entry = new NbtCompound(); entry.putUuid("Player", id); entry.putLong("LastSeen", seen); aggressors.add(entry); });
        tag.put("MarketVillageAggressors", aggressors);
        if (site != null) tag.putLong("MarketSite", site.asLong());
        tag.put("MarketDestination", MountMarket.writeStable(destination));
        if (seller != null) tag.putUuid("MarketSeller", seller);
        if (buyer != null) tag.putUuid("MarketBuyer", buyer);
        if (runaway != null) tag.putUuid("MarketRunaway", runaway);
        tag.putBoolean("MarketCaptured", captured);
        var attempts = new NbtList(); escapes.forEach((id, count) -> { var entry = new NbtCompound(); entry.putUuid("Player", id); entry.putInt("Count", count); attempts.add(entry); });
        tag.put("MarketEscapes", attempts);
        var ready = new NbtList(); waiting.forEach(id -> ready.add(NbtHelper.fromUuid(id))); tag.put("MarketWaiting", ready);
        tag.putBoolean("MarketListed", listed);
        var prices = new NbtList(); quotes.forEach((id, price) -> { var entry = new NbtCompound(); entry.putUuid("Player", id); entry.putInt("Price", price); prices.add(entry); });
        tag.put("MarketQuotes", prices);
        signs.write(tag);
        tag.put("MarketConversion", conversion.write()); tag.putBoolean("MarketPretend", pretend);
        tag.putBoolean("MarketStocked", stocked); tag.putBoolean("MarketArriving", arriving); tag.putInt("MarketNextBuyer", nextBuyer);
        tag.putInt("MarketBuyerWait", buyerWait);
        var ids = new NbtList(); stock.forEach(id -> ids.add(NbtHelper.fromUuid(id))); tag.put("MarketStock", ids);
        var inherited = new NbtList(); inheritedStock.forEach(id -> inherited.add(NbtHelper.fromUuid(id))); tag.put("MarketInheritedStock", inherited);
    }
    @Override public void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        villageAggressors.clear();
        for (var element : tag.getList("MarketVillageAggressors", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Player")) villageAggressors.put(entry.getUuid("Player"), entry.getLong("LastSeen"));
        }
        arm();
        site = tag.contains("MarketSite") ? BlockPos.fromLong(tag.getLong("MarketSite")) : null;
        destination = MountMarket.readStable(tag.getCompound("MarketDestination"));
        seller = tag.containsUuid("MarketSeller") ? tag.getUuid("MarketSeller") : null;
        buyer = tag.containsUuid("MarketBuyer") ? tag.getUuid("MarketBuyer") : null;
        runaway = tag.containsUuid("MarketRunaway") ? tag.getUuid("MarketRunaway") : null;
        captured = tag.getBoolean("MarketCaptured");
        escapes.clear(); for (var element : tag.getList("MarketEscapes", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element; if (entry.containsUuid("Player")) escapes.put(entry.getUuid("Player"), Math.max(0, entry.getInt("Count")));
        }
        waiting.clear(); for (var id : tag.getList("MarketWaiting", NbtElement.INT_ARRAY_TYPE)) waiting.add(NbtHelper.toUuid(id));
        conversion = RitualProgress.read(tag.getCompound("MarketConversion")); pretend = tag.getBoolean("MarketPretend");
        listed = tag.getBoolean("MarketListed") || conversion.completed("pen") || conversion.finished();
        quotes.clear(); for (var element : tag.getList("MarketQuotes", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Player")) quotes.put(entry.getUuid("Player"), Math.max(141, Math.min(256, entry.getInt("Price"))));
        }
        signs.read(tag);
        stocked = tag.getBoolean("MarketStocked"); arriving = tag.getBoolean("MarketArriving");
        int savedNextBuyer = tag.contains("MarketNextBuyer") ? Math.max(1, Math.min(BUYER_DEADLINE, tag.getInt("MarketNextBuyer"))) : BUYER_DEADLINE;
        nextBuyer = (savedNextBuyer - 1) % BUYER_INTERVAL + 1;
        buyerWait = tag.contains("MarketBuyerWait") ? Math.max(0, Math.min(BUYER_DEADLINE, tag.getInt("MarketBuyerWait"))) : BUYER_DEADLINE - savedNextBuyer;
        stock.clear(); for (var id : tag.getList("MarketStock", NbtElement.INT_ARRAY_TYPE)) stock.add(NbtHelper.toUuid(id));
        inheritedStock.clear(); for (var id : tag.getList("MarketInheritedStock", NbtElement.INT_ARRAY_TYPE)) {
            var uuid = NbtHelper.toUuid(id); if (stock.contains(uuid)) inheritedStock.add(uuid);
        }
    }
}
