package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import sscextras.events.NpcEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MountBuyerEntity extends PillagerEntity {
    public record Purchase(UUID mount, int stall, boolean player, int price) { }
    private UUID merchantId;
    private BlockPos marketSite;
    private DrakeStablePiece destination;
    private final List<Purchase> purchases = new ArrayList<>();
    private boolean traded;
    private int elapsed;
    private NpcEvent run;
    private int conversation, collected;
    private Vec3d directDestination;
    private boolean travelling;
    private boolean atDestinationGate;

    public MountBuyerEntity(EntityType<? extends PillagerEntity> type, World world) { super(type, world); setPersistent(); }
    @Override protected void initGoals() {
        super.initGoals(); goalSelector.clear(goal -> true); targetSelector.clear(goal -> true);
        goalSelector.add(0, new SwimGoal(this));
    }
    public void visit(MountMerchantEntity merchant) {
        merchantId = merchant.getUuid(); marketSite = merchant.site(); destination = merchant.destination();
        ((DrakeStableNavigation)getNavigation()).home(destination);
    }
    public static MountBuyerEntity spawn(MountMerchantEntity merchant) {
        var world = (ServerWorld)merchant.getWorld();
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)
                || merchant.sellerId() != null && (!merchant.ready() || merchant.seller() == null)
                || merchant.readySellers().isEmpty() && merchant.stock().isEmpty()) return null;
        UUID first = !merchant.readySellers().isEmpty() ? merchant.readySellers().get(0).getUuid() : merchant.stock().get(0).getUuid();
        if (!merchant.refreshDestination(first, merchant.readySellers().isEmpty() ? 2 : 1)) return null;
        var buyer = MountMerchants.BUYER.create(world); buyer.visit(merchant);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            var column = BlockPos.ofFloored(merchant.customerPosition().add(Math.cos(angle) * 32, 0, Math.sin(angle) * 32));
            if (!world.isChunkLoaded(column) || !world.shouldTick(column)) continue;
            var pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty()
                    || !world.getFluidState(pos.down()).isEmpty() || !world.getFluidState(pos).isEmpty()) continue;
            buyer.refreshPositionAndAngles(pos, 0, 0);
            buyer.setOnGround(true);
            if (!world.isSpaceEmpty(buyer) || !world.getOtherEntities(buyer, buyer.getBoundingBox()).isEmpty()) continue;
            if (world.spawnEntity(buyer)) {
                buyer.visitCounter(merchant);
                MountMarket.get(world.getServer()).buyer(merchant.getUuid(), buyer.getUuid()); return buyer;
            }
        }
        return null;
    }
    public boolean holdsOpen(BlockPos gate) {
        if (!traded || purchases.isEmpty()) return false;
        if (marketSite != null && gate.equals(marketSite.add(8, 1, 3))) {
            var pen = new net.minecraft.util.math.Box(marketSite.add(0, 1, 3), marketSite.add(13, 5, 11));
            return purchases.stream().anyMatch(purchase -> getWorld() instanceof ServerWorld world && world.getEntity(purchase.mount()) != null
                    && pen.contains(world.getEntity(purchase.mount()).getPos()));
        }
        return destination != null && gate.equals(destination.gate(purchases.get(0).stall()));
    }
    public static List<Integer> freeStalls(ServerWorld world, DrakeStablePiece stable, UUID first) {
        var free = new ArrayList<Integer>();
        for (int i = stable.stallCount() - 1; i >= 0; i--) {
            if (DrakeOutpostOwnership.availableFor(world, stable, i, first)
                    && world.getEntitiesByClass(LivingEntity.class, stable.stall(i), entity -> entity.isAlive() && !entity.isSpectator()
                    && !(entity instanceof PillagerEntity) && !entity.getUuid().equals(first)).isEmpty()) free.add(i);
        }
        return free;
    }
    public List<Purchase> purchases() { return List.copyOf(purchases); }
    public Vec3d directDestination() { return directDestination; }
    public boolean travelling() { return travelling; }

    public boolean trade(MountMerchantEntity merchant) {
        if (traded || merchant.destination() == null || merchant.squaredDistanceTo(this) > 25 || !merchant.isAlive()
                || merchant.sellerId() != null && (!merchant.ready() || merchant.seller() == null)) return false;
        var world = (ServerWorld)getWorld(); var market = MountMarket.get(getServer());
        var sellers = merchant.readySellers(); var stock = merchant.stock();
        if (sellers.isEmpty() && stock.isEmpty()) return false;
        UUID first = !sellers.isEmpty() ? sellers.get(0).getUuid() : stock.get(0).getUuid();
        var slots = freeStalls(world, destination, first);
        if (slots.isEmpty()) return false;
        for (var seller : sellers) {
            if (slots.isEmpty()) break;
            int slot = slots.remove(0);
            purchases.add(new Purchase(seller.getUuid(), slot, true, merchant.quote(seller)));
            market.offer(seller, getUuid(), marketSite, true);
            MountMerchantEntity.say(seller, "sold");
        }
        for (var drake : stock) {
            if (slots.size() <= 1) break;
            int slot = slots.remove(0);
            purchases.add(new Purchase(drake.getUuid(), slot, false, MountMerchants.PRICE)); merchant.removeStock(drake);
            drake.setMerchant(null, null); drake.setCustomer(null);
            drake.setTarget(null);
        }
        if (purchases.isEmpty()) return false;
        traded = true; elapsed = 0; merchant.sold(this);
        market.buyer(merchant.getUuid(), getUuid());
        ((DrakeFaction.EquipmentDisplay)(Object)this).sscExtras$showEquipment(new ItemStack(Items.EMERALD));
        ((DrakeStableNavigation)getNavigation()).open(merchant.gate());
        return true;
    }
    private void dialogue(String line, Object... arguments) {
        var text = net.minecraft.text.Text.translatable("message.ssc-extras.merchant.buyer." + line, arguments);
        for (var player : ((ServerWorld)getWorld()).getPlayers(player -> squaredDistanceTo(player) < 48 * 48))
            player.sendMessage(text, false);
    }
    private void visitCounter(MountMerchantEntity merchant) {
        var to = merchant.customerPosition();
        if (squaredDistanceTo(to) > 1) {
            setSprinting(true);
            if (age % 10 == 0 || getNavigation().isIdle())
                getNavigation().startMovingAlong(getNavigation().findPathTo(BlockPos.ofFloored(to), 0, 128), 1.2);
            return;
        }
        setSprinting(false); getNavigation().stop();
        getLookControl().lookAt(merchant, 30, 30);
        if (!merchant.atCounter()) return;
        merchant.getLookControl().lookAt(this, 30, 30);
        conversation++;
        if (conversation == 1) dialogue("greeting");
        if (conversation == 41) {
            var sellers = merchant.readySellers();
            if (!sellers.isEmpty()) for (var player : sellers) dialogue("recommend", MountMerchantForms.name(player));
            else if (!merchant.stock().isEmpty()) dialogue("recommend", merchant.stock().get(0).getName());
        }
        if (conversation == 91) dialogue("consider");
        if (conversation >= 141) {
            if (!trade(merchant)) { depart(); return; }
            dialogue("purchase"); dialogue("collect");
        }
    }
    @Override protected void mobTick() {
        super.mobTick();
        directDestination = null; travelling = false;
        if (!(getWorld() instanceof ServerWorld world) || merchantId == null || destination == null) return;
        elapsed++;
        if (!traded) {
            if (!(world.getEntity(merchantId) instanceof MountMerchantEntity merchant) || !merchant.isAlive() || elapsed > 3600) { depart(); return; }
            visitCounter(merchant); return;
        }
        if (purchases.isEmpty()) { depart(); return; }
        if (age % 40 == 0) ((DrakeFaction.EquipmentDisplay)(Object)this).sscExtras$showEquipment(ItemStack.EMPTY);
        if (run == null) run = new NpcEvent() {
            @Override protected void release() { DrakeOutpostOwnership.get(getServer()).assignments().release(this); }
        };
        var participants = new ArrayList<UUID>(); participants.add(getUuid()); purchases.forEach(p -> participants.add(p.mount()));
        if (!DrakeOutpostOwnership.get(getServer()).assignments().acquire(run, participants)) { getNavigation().stop(); return; }
        run.resume();
        if (elapsed > 24000) { depart(); return; }
        for (var member : List.copyOf(purchases)) {
            var entity = world.getEntity(member.mount());
            if (entity == null) { getNavigation().stop(); return; }
            if (!entity.isAlive()) { abandon(member); finishPurchase(member); return; }
            if (entity instanceof ServerPlayerEntity player) {
                var entry = MountMarket.get(getServer()).seller(player.getUuid());
                if (entry == null || !entry.keeper().equals(getUuid()) || !DrakeLeashing.eligible(player)) { depart(); return; }
                if (purchases.indexOf(member) < collected && getVehicle() != player && DrakeLeashing.holder(player) != this
                        && squaredDistanceTo(player) > 24 * 24) {
                    abandon(member); finishPurchase(member); return;
                }
            }
        }
        if (collected < purchases.size()) { collect(world); return; }
        var purchase = purchases.get(0);
        var target = (LivingEntity)world.getEntity(purchase.mount());
        var navigation = (DrakeStableNavigation)getNavigation();
        var gate = destination.gate(purchase.stall());
        if (target.squaredDistanceTo(Vec3d.ofCenter(gate)) < 64) {
            var slots = freeStalls(world, destination, purchase.mount());
            if (!slots.contains(purchase.stall())) {
                if (slots.isEmpty()) { navigation.stop(); return; }
                purchase = new Purchase(purchase.mount(), slots.get(0), purchase.player(), purchase.price());
                purchases.set(0, purchase); atDestinationGate = false; gate = destination.gate(purchase.stall());
            }
        }
        if (destination.stall(purchase.stall()).contains(target.getPos())
                && DrakeStableLayout.insideGate(destination.getBoundingBox(), gate, target.getBoundingBox(), 0)) {
            stopRiding(); navigation.stop();
            if (target instanceof ServerPlayerEntity player) {
                if (!DrakeLeashing.attachPillager(player, LeashKnotEntity.getOrCreate(world, destination.tie(purchase.stall())))) return;
                MountMarket.get(getServer()).release(player.getUuid());
                DrakeOutpostOwnership.capture(player, destination, purchase.stall());
                if (!DrakeOutpostOwnership.owns(player, destination)) { depart(); return; }
                MountMarket.get(getServer()).vacate(world, destination, purchase.stall(), player.getUuid());
                MountMerchantEntity.say(player, "arrived");
            } else if (target instanceof StableDrakeEntity drake) {
                drake.attachLeash(LeashKnotEntity.getOrCreate(world, destination.tie(purchase.stall())), true);
                if (!MountMarket.get(getServer()).reserve(world, destination, purchase.stall(), drake.getUuid(), drake.getName().getString())) {
                    drake.detachLeash(true, false); return;
                }
                drake.setStableHome(destination, purchase.stall());
                DrakeOutpostOwnership.restoreSign(world, destination, destination.sign(purchase.stall()));
            }
            navigation.close(gate); finishPurchase(purchase); return;
        }
        if (getVehicle() != target) {
            if (target.hasPassengers() || target.hasVehicle()) { getNavigation().stop(); return; }
            if (!DrakeRiding.inInteractionReach(this, target)) { move(target.getPos(), false); return; }
            if (target instanceof ServerPlayerEntity player) DrakeLeashing.detach(player, false);
            else if (target instanceof StableDrakeEntity drake) drake.detachLeash(true, false);
            if (!startRiding(target)) {
                if (target instanceof ServerPlayerEntity player) DrakeLeashing.attachPillager(player, this);
                else if (target instanceof StableDrakeEntity drake) drake.attachLeash(this, true);
                getNavigation().stop(); return;
            }
            getNavigation().stop();
        }
        for (var member : purchases) {
            var other = world.getEntity(member.mount());
            if (other == target) continue;
            if (target.squaredDistanceTo(other) > 24 * 24) { depart(); return; }
            if (other instanceof ServerPlayerEntity player) {
                if (DrakeLeashing.holder(player) != this && !DrakeLeashing.attachPillager(player, this)) { depart(); return; }
                var recovery = DrakeLeashing.recoveryDestination(player, this);
                if (recovery != null) { move(recovery, false); return; }
            } else if (other instanceof StableDrakeEntity drake) {
                if (drake.getHoldingEntity() != this) drake.attachLeash(this, true);
                if (age % 10 == 0 && target.squaredDistanceTo(drake) > 9) drake.getNavigation().startMovingTo(target, 1.3);
            }
            if (target.squaredDistanceTo(other) > 36) { getNavigation().stop(); return; }
        }
        if (marketSite != null && purchases.stream().anyMatch(member -> {
            var entity = world.getEntity(member.mount());
            return entity != null && entity.getBoundingBox().maxZ > marketSite.getZ() + 2.5
                    && new net.minecraft.util.math.Box(marketSite.add(-1, 0, 1), marketSite.add(14, 6, 12)).intersects(entity.getBoundingBox());
        })) {
            navigation.open(marketSite.add(8, 1, 3));
            var approach = Vec3d.ofBottomCenter(marketSite.add(8, 1, 5)).add(.5, 0, 0);
            boolean aligned = Math.abs(target.getX() - approach.x) < .2;
            var exit = Vec3d.ofBottomCenter(marketSite.add(8, 1, -2)).add(.5, 0, 0);
            move(aligned || target.getZ() < marketSite.getZ() + 4.5 ? exit : approach, true); return;
        }
        if (target.squaredDistanceTo(Vec3d.ofCenter(gate)) < 64) navigation.open(gate);
        var approach = DrakeStableLayout.gatePoint(destination.getBoundingBox(), gate, -1.5);
        if (Math.abs(target.getX() - approach.x) < .2 && Math.abs(target.getZ() - approach.z) < .45) atDestinationGate = true;
        var inside = Vec3d.ofBottomCenter(destination.bed(purchase.stall())).add(.5, 0, 0);
        var point = waypoint(world, atDestinationGate ? inside : approach);
        if (point == null) { getNavigation().stop(); return; }
        move(point, atDestinationGate || target.squaredDistanceTo(approach) < 2.25);
    }
    private void collect(ServerWorld world) {
        var navigation = (DrakeStableNavigation)getNavigation(); navigation.open(marketSite.add(8, 1, 3));
        var member = purchases.get(collected);
        var target = world.getEntity(member.mount());
        if (getZ() < marketSite.getZ() + 4) {
            var entrance = Vec3d.ofBottomCenter(marketSite.add(8, 1, 5)).add(.5, 0, 0);
            move(entrance, false); return;
        }
        if (!DrakeRiding.inInteractionReach(this, target)) { move(target.getPos(), false); return; }
        if (target instanceof ServerPlayerEntity player) {
            if (!DrakeLeashing.attachPillager(player, this)) { depart(); return; }
        } else if (target instanceof StableDrakeEntity drake) drake.attachLeash(this, true);
        collected++; getNavigation().stop();
    }
    private void move(Vec3d destination, boolean direct) {
        travelling = hasVehicle(); directDestination = travelling && direct ? destination : null;
        if (direct) {
            getNavigation().stop();
            if (!travelling) getMoveControl().moveTo(destination.x, destination.y, destination.z, .9);
        } else if (getNavigation().isIdle() || age % 10 == 0)
            getNavigation().startMovingAlong(getNavigation().findPathTo(destination.x, destination.y, destination.z, 0), travelling ? 1.2 : .9);
        if (travelling) DrakeRiding.tickPillager(this);
    }
    private Vec3d waypoint(ServerWorld world, Vec3d target) {
        var delta = target.subtract(getPos());
        if (delta.horizontalLengthSquared() <= 24 * 24) return target;
        var point = getPos().add(delta.x, 0, delta.z).subtract(getPos()).normalize().multiply(16).add(getPos());
        var column = BlockPos.ofFloored(point);
        if (!world.isChunkLoaded(column)) return null;
        return Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column));
    }
    private void finishPurchase(Purchase purchase) {
        purchases.remove(purchase); collected = Math.min(collected, purchases.size()); atDestinationGate = false;
        if (run != null) DrakeOutpostOwnership.get(getServer()).assignments().release(run, purchase.mount());
    }
    private void abandon(Purchase purchase) {
        var world = (ServerWorld)getWorld();
        var entity = world.getEntity(purchase.mount());
        var market = MountMarket.get(getServer());
        market.vacate(world, destination, purchase.stall(), purchase.mount());
        if (purchase.player()) market.release(purchase.mount());
        if (entity instanceof ServerPlayerEntity player) {
            if (DrakeLeashing.holder(player) == this) DrakeLeashing.detach(player, false);
            if (DrakeOutpostOwnership.owns(player, destination)) DrakeOutpostOwnership.release(player);
            if (world.getEntity(merchantId) instanceof MountMerchantEntity merchant) merchant.recoverSeller(player);
        }
        if (entity instanceof StableDrakeEntity drake) {
            if (drake.getHoldingEntity() == this) drake.detachLeash(true, false);
            drake.clearStableHome();
            if (drake.isAlive() && world.getEntity(merchantId) instanceof MountMerchantEntity merchant && merchant.pen().contains(drake.getPos()))
                merchant.addStock(drake);
        }
    }
    private void depart() {
        var world = (ServerWorld)getWorld();
        stopRiding();
        for (var purchase : purchases) abandon(purchase);
        purchases.clear(); interrupt();
        MountMarket.get(getServer()).finishBuyer(merchantId, getUuid());
        if (world.getEntity(merchantId) instanceof MountMerchantEntity merchant) merchant.buyerFinished(getUuid());
        discard();
    }
    public void interrupt() {
        directDestination = null; travelling = false; setSprinting(false); getNavigation().stop();
        if (run != null) { run.finish(); run = null; }
    }
    @Override public void onDeath(DamageSource source) { if (!getWorld().isClient && merchantId != null) depart(); super.onDeath(source); }
    @Override public boolean canTarget(LivingEntity target) { return false; }
    @Override public boolean canJoinRaid() { return false; }
    @Override public boolean canLead() { return false; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public boolean isDisallowedInPeaceful() { return false; }
    @Override public void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        if (merchantId != null) tag.putUuid("MarketMerchant", merchantId);
        if (marketSite != null) tag.putLong("MarketSite", marketSite.asLong());
        tag.put("MarketDestination", MountMarket.writeStable(destination)); tag.putBoolean("MarketTraded", traded); tag.putInt("MarketElapsed", elapsed);
        var list = new NbtList();
        for (var purchase : purchases) {
            var entry = new NbtCompound(); entry.putUuid("Mount", purchase.mount()); entry.putInt("Stall", purchase.stall()); entry.putBoolean("Player", purchase.player()); entry.putInt("Price", purchase.price()); list.add(entry);
        }
        tag.put("MarketPurchases", list); tag.putInt("MarketConversation", conversation); tag.putInt("MarketCollected", collected);
    }
    @Override public void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        merchantId = tag.containsUuid("MarketMerchant") ? tag.getUuid("MarketMerchant") : null;
        marketSite = tag.contains("MarketSite") ? BlockPos.fromLong(tag.getLong("MarketSite")) : null;
        destination = MountMarket.readStable(tag.getCompound("MarketDestination"));
        if (destination != null) ((DrakeStableNavigation)getNavigation()).home(destination);
        traded = tag.getBoolean("MarketTraded"); elapsed = tag.getInt("MarketElapsed");
        conversation = tag.getInt("MarketConversation"); collected = tag.getInt("MarketCollected");
        purchases.clear(); for (var element : tag.getList("MarketPurchases", NbtElement.COMPOUND_TYPE)) {
            var entry = (NbtCompound)element;
            if (entry.containsUuid("Mount") && destination != null && entry.getInt("Stall") >= 0 && entry.getInt("Stall") < destination.stallCount())
                purchases.add(new Purchase(entry.getUuid("Mount"), entry.getInt("Stall"), entry.getBoolean("Player"), entry.contains("Price") ? entry.getInt("Price") : MountMerchants.PRICE));
        }
    }
}
