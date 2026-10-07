package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
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
    private BlockPos site;
    private DrakeStablePiece destination;
    private final List<UUID> stock = new ArrayList<>();
    private UUID seller, buyer;
    private final List<UUID> waiting = new ArrayList<>();
    private final java.util.Map<UUID, Integer> escapes = new java.util.HashMap<>();
    private UUID runaway;
    private boolean captured;
    private final java.util.Map<UUID, Integer> quotes = new java.util.HashMap<>();
    private final UUID[] signs = new UUID[3];
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
        if (listed && player != null && player.isAlive() && pen().contains(player.getPos()))
            result.add(new Listing(player.getUuid(), player.getId(), Text.literal(MountMerchantForms.name(player)), quote(player), true));
        return result;
    }
    public boolean purchasable(UUID id, PlayerEntity customer) {
        if (id.equals(customer.getUuid()) || buyer != null) return false;
        if (readySellers().stream().anyMatch(player -> player.getUuid().equals(id))) return true;
        return (seller == null || ready()) && stock().stream().anyMatch(drake -> id.equals(drake.getUuid()));
    }
    public void registerSeller() { listed = true; DrakeOutpostOwnership.release(seller()); updateSigns(); }
    public BlockPos sign(int slot) { return site.add(new int[]{6, 10, 11}[slot], 1, 2); }
    public void updateSigns() {
        if (!(getWorld() instanceof ServerWorld world) || site == null) return;
        var offers = listings();
        for (int i = 0; i < signs.length; i++) {
            UUID id = signs[i];
            if (id != null && offers.stream().noneMatch(offer -> offer.id().equals(id))) signs[i] = null;
        }
        for (var offer : offers) {
            if (java.util.Arrays.asList(signs).contains(offer.id())) continue;
            for (int i = 0; i < signs.length; i++) if (signs[i] == null) { signs[i] = offer.id(); break; }
        }
        for (int i = 0; i < signs.length; i++) {
            var pos = sign(i);
            if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down()))
                world.setBlockState(pos, net.minecraft.block.Blocks.DARK_OAK_SIGN.getDefaultState().with(net.minecraft.block.SignBlock.ROTATION, 8));
            if (!(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.SignBlockEntity sign)) continue;
            UUID id = signs[i]; var offer = offers.stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
            var text = new net.minecraft.block.entity.SignText();
            if (offer != null) text = text.withMessage(1, offer.name()).withMessage(2, Text.translatable("screen.ssc-extras.merchant.cost", offer.price()));
            if (!java.util.Arrays.equals(sign.getFrontText().getMessages(false), text.getMessages(false))) {
                sign.setText(text, true); sign.setText(text, false); sign.markDirty();
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    }
    private RitualProgress conversion = new RitualProgress();
    private boolean pretend, stocked;
    private int nextBuyer = 1200, restockTicks;
    private MountConversionRitual ritual;

    public MountMerchantEntity(EntityType<? extends PillagerEntity> type, World world) { super(type, world); setPersistent(); }
    public void setSite(BlockPos site, DrakeStablePiece destination) { this.site = site.toImmutable(); this.destination = destination; }
    public BlockPos site() { return site; }
    public DrakeStablePiece destination() { return destination; }
    public void refreshDestination() {
        if (!(getWorld() instanceof ServerWorld world) || site == null) return;
        var nearest = DrakeCaptureGoal.findStable(world, site, 384);
        if (nearest != null && (destination == null || nearest.getBoundingBox().getCenter().getSquaredDistance(site)
                < destination.getBoundingBox().getCenter().getSquaredDistance(site))) destination = nearest;
    }
    public boolean holdsOpen(BlockPos gate) {
        return site != null && gate.equals(gate()) && (seller != null && !pen().contains(seller() == null ? Vec3d.ZERO : seller().getPos())
                || (seller == null || ready()) && pen().contains(getPos())
                || getWorld().getPlayers().stream().anyMatch(player -> pen().contains(player.getPos()) && MountMarket.playerOwned(player)
                        && DrakeLeashing.holder(player) instanceof PlayerEntity)
                || !getWorld().getEntitiesByClass(StableDrakeEntity.class, pen(), drake -> drake.isAlive() && drake.merchant() == null
                        && (drake.customer() != null || drake.getHoldingEntity() instanceof MountBuyerEntity)).isEmpty());
    }
    public BlockPos gate() { return site.add(8, 1, 3); }
    public Vec3d counter() { return Vec3d.ofBottomCenter(site.add(3, 1, 1)); }
    public Vec3d customerPosition() { return Vec3d.ofBottomCenter(site.add(3, 1, -1)); }
    public boolean atCounter() { return squaredDistanceTo(counter()) < 1; }
    public BlockPos tie() { return site.add(6, 1, 7); }
    public Vec3d inside() { return Vec3d.ofBottomCenter(site.add(8, 1, 8)).add(.5, 0, 0); }
    public Box pen() { return new Box(site.add(0, 1, 3), site.add(13, 5, 11)).contract(.625, 0, .625); }
    public UUID sellerId() { return seller; }
    public boolean ready() { return seller != null && conversion.finished(); }
    public boolean capturedSeller() { return captured; }
    public boolean hasBuyer() { return buyer != null; }
    public ServerPlayerEntity seller() {
        return seller != null && getWorld() instanceof ServerWorld world && world.getEntity(seller) instanceof ServerPlayerEntity player ? player : null;
    }
    public List<ServerPlayerEntity> readySellers() {
        var ids = new ArrayList<>(waiting);
        if (ready()) ids.add(seller);
        var world = (ServerWorld)getWorld();
        return ids.stream().map(world::getEntity).filter(entity -> entity instanceof ServerPlayerEntity)
                .map(entity -> (ServerPlayerEntity)entity).filter(player -> player.isAlive() && EarthenDrake.stage(player) >= 2
                        && pen().contains(player.getPos())).toList();
    }
    private void beginOffer(ServerPlayerEntity player, boolean pretend) {
        if (ready()) { waiting.add(seller); interrupt(); }
        getNavigation().stop();
        seller = player.getUuid(); this.pretend = pretend; listed = false; conversion = new RitualProgress();
        captured = false;
    }
    public List<StableDrakeEntity> stock() {
        if (!(getWorld() instanceof ServerWorld world) || site == null) return List.of();
        return stock.stream().map(world::getEntity).filter(entity -> entity instanceof StableDrakeEntity)
                .map(entity -> (StableDrakeEntity)entity).filter(entity -> entity.isAlive() && getUuid().equals(entity.merchant())
                        && pen().expand(2).contains(entity.getPos()) && !entity.hasPassengers()).toList();
    }
    public void addStock(StableDrakeEntity drake) {
        if (!stock.contains(drake.getUuid())) stock.add(drake.getUuid());
        drake.clearStableHome(); drake.setMerchant(getUuid(), tie());
        if (getWorld().getBlockState(tie()).isIn(net.minecraft.registry.tag.BlockTags.FENCES))
            drake.attachLeash(LeashKnotEntity.getOrCreate(getWorld(), tie()), true);
        updateSigns();
    }
    public void removeStock(StableDrakeEntity drake) { stock.remove(drake.getUuid()); updateSigns(); }
    public boolean canOffer(PlayerEntity player) {
        return site != null && player.isAlive() && !player.isSpectator() && MountMerchantForms.dialogue(player) != 4;
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
        beginOffer(player, pretend);
        if (EarthenDrake.stage(player) < 2 && stock().isEmpty()) restock();
        MountMarket.get(getServer()).offer(player, getUuid(), site, false);
        say(player, pretend ? "pretend" : "accepted");
        return true;
    }
    public boolean buy(ServerPlayerEntity player) { return !stock().isEmpty() && buy(player, stock().get(0).getUuid()); }
    public boolean buy(ServerPlayerEntity player, UUID id) {
        if (site == null || squaredDistanceTo(player) > 64 || !getVisibilityCache().canSee(player) || !purchasable(id, player)) return false;
        var listing = listings().stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
        if (listing == null || emeralds(player) < listing.price()) return false;
        if (listing.player()) {
            var mount = ((ServerWorld)getWorld()).getEntity(id) instanceof ServerPlayerEntity target ? target : null;
            if (mount == null || !DrakeLeashing.attach(mount, player)) return false;
            MountMarket.get(getServer()).sellTo(id, player.getUuid());
            say(mount, "sold"); removeSeller(id);
        } else {
            var drake = (StableDrakeEntity)((ServerWorld)getWorld()).getEntity(id);
            drake.setMerchant(null, null); drake.setCustomer(player.getUuid()); drake.setTarget(null);
            drake.attachLeash(player, true); removeStock(drake);
        }
        int cost = listing.price();
        for (int slot = 0; slot < player.getInventory().size() && cost > 0; slot++) {
            var stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.EMERALD)) continue;
            int taken = Math.min(cost, stack.getCount()); stack.decrement(taken); cost -= taken;
        }
        ((DrakeStableNavigation)getNavigation()).open(gate()); updateSigns();
        player.getInventory().markDirty();
        player.sendMessage(Text.translatable("message.ssc-extras.merchant.purchase", listing.name(), listing.price()), false); return true;
    }
    private void recoverStock() {
        var world = (ServerWorld)getWorld();
        for (var drake : world.getEntitiesByClass(StableDrakeEntity.class, pen(), entity -> entity.isAlive()
                && entity.customer() == null && !entity.hasPassengers())) {
            var holder = drake.getHoldingEntity();
            if (holder instanceof MountBuyerEntity || holder instanceof PlayerEntity) continue;
            if (drake.merchant() != null && !getUuid().equals(drake.merchant())) continue;
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
            if (line == null) continue;
            beginOffer(player, false);
            captured = true;
            MountMarket.get(getServer()).offer(player, getUuid(), site, false);
            if (EarthenDrake.stage(player) < 2 && stock().isEmpty()) restock();
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
    public void buyerFinished(UUID id) { if (id.equals(buyer)) { buyer = null; nextBuyer = 1200; } }
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
        seller = null; runaway = null; setSprinting(false); listed = false; conversion = new RitualProgress(); updateSigns();
    }
    private boolean chaseRunaway() {
        var world = (ServerWorld)getWorld(); var market = MountMarket.get(getServer());
        if (runaway == null) {
            var candidates = new ArrayList<UUID>();
            if (seller != null && listed) candidates.add(seller);
            if (seller == null || ready()) candidates.addAll(waiting);
            for (var id : candidates) {
                if (!(world.getEntity(id) instanceof ServerPlayerEntity player) || !player.isAlive()
                        || player.hasPassengers() || player.hasVehicle() || DrakeLeashing.attached(player) || !market.busy(player)) continue;
                if (player.squaredDistanceTo(Vec3d.ofCenter(tie())) < 4) {
                    if (id.equals(seller)) return true;
                    continue;
                }
                runaway = id; interrupt(); say(player, "escape_chase"); break;
            }
        }
        if (runaway == null) return false;
        var id = runaway;
        if (!(world.getEntity(id) instanceof ServerPlayerEntity player) || !market.busy(player)
                || !MountMerchantForms.convertible(player) || player.hasPassengers() || player.hasVehicle()) {
            runaway = null; setSprinting(false);
            if (id.equals(seller)) cancelOffer(); else { waiting.remove(id); market.release(id); }
            return false;
        }
        if (DrakeLeashing.attached(player)) { runaway = null; setSprinting(false); return false; }
        ((DrakeStableNavigation)getNavigation()).open(gate());
        getLookControl().lookAt(player, 30, 30); setSprinting(true);
        if (squaredDistanceTo(player) > 4 || !getVisibilityCache().canSee(player)) {
            if (getNavigation().isIdle() || age % 10 == 0) getNavigation().startMovingTo(player, 1.2);
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
        conversion = new RitualProgress(); runaway = null; setSprinting(false); updateSigns(); return true;
    }
    public void interrupt() { if (ritual != null) { ritual.finish(); ritual = null; } }
    @Override protected void initGoals() {
        super.initGoals(); goalSelector.clear(goal -> true); targetSelector.clear(goal -> true);
        goalSelector.add(0, new SwimGoal(this)); goalSelector.add(1, new MeleeAttackGoal(this, 1, false));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8)); goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(0, new RevengeGoal(this));
    }
    @Override public boolean canTarget(LivingEntity target) { return target == getAttacker() && super.canTarget(target); }
    @Override protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world) || site == null || destination == null) return;
        if (!stocked && world.isChunkLoaded(site.add(12, 0, 10))) { recoverStock(); restock(); stocked = true; }
        if (age % 40 == 0) { recoverStock(); updateSigns(); }
        if (age % 20 == 0) recruit();
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
        if ((seller == null || ready()) && getTarget() == null) {
            if (!atCounter()) {
                ((DrakeStableNavigation)getNavigation()).open(gate());
                double gateX = site.getX() + 9;
                if (pen().contains(getPos()) || Math.abs(getX() - gateX) < 1 && getZ() > site.getZ() + 2) {
                    var point = new Vec3d(gateX, site.getY() + 1,
                            Math.abs(getX() - gateX) < .2 ? site.getZ() + 1.5 : site.getZ() + 5.5);
                    getNavigation().stop(); getMoveControl().moveTo(point.x, point.y, point.z, .8);
                } else if (age % 10 == 0 || getNavigation().isIdle()) getNavigation().startMovingAlong(getNavigation().findPathTo(counter().x, counter().y, counter().z, 0), .8);
            } else {
                getNavigation().stop();
                if (buyer == null) getLookControl().lookAt(customerPosition().x, customerPosition().y + 1, customerPosition().z);
            }
        }
        if (buyer != null) {
            if (!MountMarket.get(getServer()).buyerActive(getUuid(), buyer)) buyerFinished(buyer);
            return;
        }
        if (--nextBuyer <= 0) {
            nextBuyer = 1200;
            if (random.nextInt(4) == 0 && (seller == null || ready())) {
                var spawned = MountBuyerEntity.spawn(this);
                if (spawned != null) buyer = spawned.getUuid();
            }
        }
        if (seller == null && stock().isEmpty() && ++restockTicks >= 12000) restock();
    }
    private void restock() {
        var world = (ServerWorld)getWorld();
        if (!stock().isEmpty() || !world.isChunkLoaded(site.add(12, 0, 10))) return;
        // Missing UUIDs may be unloaded, so only retire stock known to be dead or sold.
        stock.removeIf(id -> world.getEntity(id) instanceof StableDrakeEntity drake && (!drake.isAlive() || !getUuid().equals(drake.merchant())));
        if (!stock.isEmpty()) return;
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            var drake = DrakeStable.DRAKE.create(world);
            drake.refreshPositionAndAngles(site.getX() + 3 + i * 6, site.getY() + 1, site.getZ() + 7, 180, 0);
            drake.setCustomName(Text.literal(DrakeMountNames.choose(random)));
            if (world.spawnEntity(drake)) addStock(drake);
        }
        restockTicks = 0;
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
        for (int i = 0; i < signs.length; i++) if (signs[i] != null) tag.putUuid("MarketSign" + i, signs[i]);
        tag.put("MarketConversion", conversion.write()); tag.putBoolean("MarketPretend", pretend);
        tag.putBoolean("MarketStocked", stocked); tag.putInt("MarketRestockTicks", restockTicks); tag.putInt("MarketNextBuyer", nextBuyer);
        var ids = new NbtList(); stock.forEach(id -> ids.add(NbtHelper.fromUuid(id))); tag.put("MarketStock", ids);
    }
    @Override public void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
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
        for (int i = 0; i < signs.length; i++) signs[i] = tag.containsUuid("MarketSign" + i) ? tag.getUuid("MarketSign" + i) : null;
        stocked = tag.getBoolean("MarketStocked"); restockTicks = tag.getInt("MarketRestockTicks");
        nextBuyer = tag.contains("MarketNextBuyer") ? Math.max(1, Math.min(1200, tag.getInt("MarketNextBuyer"))) : 1200;
        stock.clear(); for (var id : tag.getList("MarketStock", NbtElement.INT_ARRAY_TYPE)) stock.add(NbtHelper.toUuid(id));
    }
}
