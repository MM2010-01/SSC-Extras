package sscextras.drake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayList;
import java.util.List;

public final class MountMerchantScreenHandler extends ScreenHandler {
    public static final int OFFER = 1, YES = 2, NO = 3, PRETEND = 4, CONTINUE = 5, BUY = 10;
    private final MountMerchantEntity merchant;
    private final PlayerEntity customer;
    private final PropertyDelegate state;
    private final List<MountMerchantEntity.Listing> listings;
    public MountMerchantScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, inventory, null, readListings(buf));
    }
    public MountMerchantScreenHandler(int syncId, PlayerInventory inventory, MountMerchantEntity merchant) {
        this(syncId, inventory, merchant, merchant.listings());
    }
    public MountMerchantScreenHandler(int syncId, PlayerInventory inventory, MountMerchantEntity merchant, List<MountMerchantEntity.Listing> listings) {
        super(MountMerchants.SCREEN, syncId); this.merchant = merchant; customer = inventory.player; this.listings = List.copyOf(listings);
        state = new ArrayPropertyDelegate(4 + listings.size() * 2); addProperties(state); refresh();
    }
    public static void writeListings(PacketByteBuf buf, List<MountMerchantEntity.Listing> listings) {
        buf.writeVarInt(listings.size());
        for (var row : listings) { buf.writeUuid(row.id()); buf.writeVarInt(row.entityId()); buf.writeText(row.name()); buf.writeVarInt(row.price()); buf.writeBoolean(row.player()); }
    }
    private static List<MountMerchantEntity.Listing> readListings(PacketByteBuf buf) {
        int count = buf.readVarInt(); if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid market size");
        var result = new ArrayList<MountMerchantEntity.Listing>();
        for (int i = 0; i < count; i++) result.add(new MountMerchantEntity.Listing(buf.readUuid(), buf.readVarInt(), buf.readText(), buf.readVarInt(), buf.readBoolean()));
        return result;
    }
    public List<MountMerchantEntity.Listing> listings() { return listings; }
    public int emeralds() { return state.get(0); }
    public boolean canOffer() { return state.get(1) != 0; }
    public int phase() { return state.get(2); }
    public boolean confirming() { return phase() == 2; }
    public int dialogue() { return state.get(3); }
    public boolean present(int row) { return state.get(4 + row) != 0; }
    public int price(int row) { return state.get(4 + listings.size() + row); }
    public boolean canBuy(int row) { return state.get(4 + row) == 2 && emeralds() >= price(row); }
    private void refresh() {
        if (merchant == null) return;
        state.set(0, MountMerchantEntity.emeralds(customer)); state.set(1, merchant.canOffer(customer) ? 1 : 0);
        state.set(3, MountMerchantForms.dialogue(customer));
        var available = merchant.listings();
        for (int i = 0; i < listings.size(); i++) {
            var id = listings.get(i).id();
            state.set(4 + i, available.stream().noneMatch(row -> row.id().equals(id)) ? 0 : merchant.purchasable(id, customer) ? 2 : 1);
            state.set(4 + listings.size() + i, available.stream().filter(row -> row.id().equals(id)).findFirst().map(MountMerchantEntity.Listing::price).orElse(listings.get(i).price()));
        }
    }
    @Override public void sendContentUpdates() { refresh(); super.sendContentUpdates(); }
    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (player != customer || !(player instanceof ServerPlayerEntity serverPlayer) || !canUse(player) || merchant == null) return false;
        if (id >= BUY && id < BUY + listings.size() && phase() == 0) {
            boolean bought = merchant.buy(serverPlayer, listings.get(id - BUY).id()); refresh(); return bought;
        }
        if (id == OFFER && merchant.canOffer(player)) {
            state.set(2, !MountMerchantForms.convertible(player) ? 3 : merchant.hasDemand(player) ? 1 : 4);
            return true;
        }
        if (id == CONTINUE && phase() == 1) { state.set(2, 2); return true; }
        if (id == NO && phase() != 0) { state.set(2, 0); return true; }
        if ((id == YES || id == PRETEND) && confirming()) {
            if (!merchant.hasDemand(player)) { state.set(2, 4); return true; }
            if (!merchant.offer(serverPlayer, id == PRETEND)) { MountMerchantEntity.say(player, "unavailable"); state.set(2, 0); refresh(); return false; }
            serverPlayer.closeHandledScreen(); return true;
        }
        return false;
    }
    @Override public boolean canUse(PlayerEntity player) {
        return merchant == null || merchant.isAlive() && player.isAlive() && !player.isSpectator() && MountMerchantForms.dialogue(player) != 4
                && player.getWorld() == merchant.getWorld() && merchant.squaredDistanceTo(player) <= 64 && merchant.getTarget() == null;
    }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
