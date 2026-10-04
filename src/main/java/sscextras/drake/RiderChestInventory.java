package sscextras.drake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public final class RiderChestInventory extends SimpleInventory {
    private final LivingEntity owner;
    private final ItemStack item;
    private boolean loading = true;

    public RiderChestInventory(LivingEntity owner, ItemStack item) {
        super(54);
        this.owner = owner;
        this.item = item;
        var contents = DefaultedList.ofSize(54, ItemStack.EMPTY);
        if (item.hasNbt()) Inventories.readNbt(item.getNbt().getCompound("RiderChest"), contents);
        for (int i = 0; i < 54; i++) setStack(i, contents.get(i));
        loading = false;
    }

    public boolean belongsTo(ItemStack stack) { return stack == item; }

    @Override public void markDirty() {
        super.markDirty();
        if (loading) return;
        var contents = DefaultedList.ofSize(54, ItemStack.EMPTY);
        for (int i = 0; i < 54; i++) contents.set(i, getStack(i));
        item.getOrCreateNbt().put("RiderChest", Inventories.writeNbt(new net.minecraft.nbt.NbtCompound(), contents));
    }

    @Override public boolean isValid(int slot, ItemStack stack) { return !stack.isOf(DrakeEquipment.RIDERS_CHEST); }

    @Override public boolean canPlayerUse(PlayerEntity player) {
        return owner.isAlive() && player.isAlive() && owner.getWorld() == player.getWorld()
                && player.squaredDistanceTo(owner) <= 64 && equipped(owner) == item;
    }

    public static ItemStack equipped(Entity owner) {
        return owner instanceof PlayerEntity player ? DrakeEquipment.equipped(player, DrakeEquipment.RIDERS_CHEST)
                : owner instanceof StableDrakeEntity drake ? drake.chest() : ItemStack.EMPTY;
    }

    public static void open(ServerPlayerEntity viewer, LivingEntity owner) {
        var stack = equipped(owner);
        if (stack.isEmpty() || !owner.isAlive() || viewer.isSpectator() || viewer.getWorld() != owner.getWorld()
                || viewer.squaredDistanceTo(owner) > 64) return;
        var state = (DrakeRiding.State) owner;
        RiderChestInventory inventory = state.sscExtras$getChest();
        if (inventory == null || !inventory.belongsTo(stack)) {
            inventory = new RiderChestInventory(owner, stack);
            state.sscExtras$setChest(inventory);
        }
        final var shared = inventory;
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, playerInventory, player) ->
                new GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X6, id, playerInventory, shared, 6) {
                    { for (int i = 0; i < 54; i++) {
                        final int index = i;
                        slots.set(i, new net.minecraft.screen.slot.Slot(shared, i, slots.get(i).x, slots.get(i).y) {
                            @Override public boolean canInsert(ItemStack value) { return shared.isValid(index, value); }
                        });
                        slots.get(i).id = i;
                    } }
                }, Text.translatable("container.ssc-extras.riders_chest", owner.getDisplayName())));
    }
}
