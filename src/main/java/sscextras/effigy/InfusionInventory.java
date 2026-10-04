package sscextras.effigy;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InfusionInventory extends SimpleInventory implements AutoSyncedComponent, ServerTickingComponent {
    private final PlayerEntity owner;
    private final Map<UUID, Applied> applied = new HashMap<>();
    private final ItemStack[] synced = new ItemStack[InfusionSlot.values().length];
    private boolean loading, dirty, snapshotHasItems;
    private record Applied(EntityAttribute attribute, double value, EntityAttributeModifier.Operation operation) { }

    public InfusionInventory(PlayerEntity owner) {
        super(InfusionSlot.values().length);
        this.owner = owner;
        java.util.Arrays.fill(synced, ItemStack.EMPTY);
    }

    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean canPlayerUse(PlayerEntity player) { return player == owner && owner.isAlive(); }
    @Override public boolean shouldSyncWith(ServerPlayerEntity player) { return player == owner; }

    @Override public void markDirty() {
        super.markDirty();
        if (!loading) dirty = true;
    }

    @Override public void serverTick() {
        if (isEmpty() && applied.isEmpty() && !dirty && !snapshotHasItems) return;
        if (!owner.isAlive()) return;
        for (int i = 0; i < size(); i++) if (!ItemStack.areEqual(synced[i], getStack(i))) dirty = true;
        refreshAttributes();
        flush();
    }

    public void flush() {
        if (!dirty || owner.getWorld().isClient) return;
        dirty = false;
        InfusionComponents.KEY.sync(owner);
        snapshot();
    }

    private void snapshot() {
        snapshotHasItems = false;
        for (int i = 0; i < size(); i++) {
            synced[i] = getStack(i).copy();
            snapshotHasItems |= !synced[i].isEmpty();
        }
    }

    public void refreshAttributes() {
        if (owner.getWorld().isClient) return;
        Map<UUID, Applied> wanted = new HashMap<>();
        for (InfusionSlot slot : InfusionSlot.values()) {
            if (!slot.armor() && slot != InfusionSlot.WEAPON) continue;
            ItemStack stack = Infusions.active(owner, slot);
            if (stack.isEmpty()) continue;
            stack.getAttributeModifiers(slot.equipment).forEach((attribute, modifier) -> {
                UUID id = UUID.nameUUIDFromBytes(("ssc-extras:infusion/" + slot.name() + "/"
                        + Registries.ATTRIBUTE.getId(attribute) + "/" + modifier.getId())
                        .getBytes(StandardCharsets.UTF_8));
                wanted.put(id, new Applied(attribute, modifier.getValue(), modifier.getOperation()));
            });
        }
        applied.forEach((id, old) -> {
            if (!old.equals(wanted.get(id)) && owner.getAttributeInstance(old.attribute()) != null) {
                owner.getAttributeInstance(old.attribute()).removeModifier(id);
            }
        });
        wanted.forEach((id, value) -> {
            var instance = owner.getAttributeInstance(value.attribute());
            if (instance != null && (!value.equals(applied.get(id)) || !instance.hasModifier(new EntityAttributeModifier(
                    id, "Feral infusion", value.value(), value.operation())))) {
                instance.removeModifier(id);
                instance.addTemporaryModifier(new EntityAttributeModifier(id, "Feral infusion", value.value(), value.operation()));
            }
        });
        applied.clear();
        applied.putAll(wanted);
    }

    @Override public void readFromNbt(NbtCompound tag) {
        loading = true;
        clear();
        NbtList list = tag.getList("Items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompound(i);
            int index = item.getByte("Slot") & 255;
            if (index < size()) setStack(index, ItemStack.fromNbt(item));
        }
        loading = false;
        dirty = false;
        snapshot();
    }

    @Override public void writeToNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (int i = 0; i < size(); i++) {
            ItemStack stack = getStack(i);
            if (stack.isEmpty()) continue;
            NbtCompound item = stack.writeNbt(new NbtCompound());
            item.putByte("Slot", (byte)i);
            list.add(item);
        }
        tag.put("Items", list);
    }
}
