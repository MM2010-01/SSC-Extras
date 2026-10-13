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
    private static final InfusionSlot[] SLOTS = InfusionSlot.values();
    private final PlayerEntity owner;
    private final Map<ModifierKey, Applied> applied = new HashMap<>();
    private final ItemStack[] synced = new ItemStack[SLOTS.length];
    private boolean loading, dirty, snapshotHasItems;
    private record ModifierKey(InfusionSlot slot, EntityAttribute attribute, UUID sourceId) {
        UUID infusionId() {
            return UUID.nameUUIDFromBytes(("ssc-extras:infusion/" + slot.name() + "/"
                    + Registries.ATTRIBUTE.getId(attribute) + "/" + sourceId).getBytes(StandardCharsets.UTF_8));
        }
    }
    private static final class Applied {
        EntityAttributeModifier modifier;
        boolean wanted, changed = true;
        Applied(UUID id, EntityAttributeModifier source) {
            modifier = new EntityAttributeModifier(id, "Feral infusion", source.getValue(), source.getOperation());
        }
    }

    public InfusionInventory(PlayerEntity owner) {
        super(SLOTS.length);
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
        applied.values().forEach(value -> value.wanted = false);
        // Conditions and item-provided modifiers stay live; only their derived identities are cached.
        for (InfusionSlot slot : SLOTS) {
            if (!slot.armor() && slot != InfusionSlot.WEAPON) continue;
            ItemStack stack = Infusions.active(owner, slot);
            if (stack.isEmpty()) continue;
            stack.getAttributeModifiers(slot.equipment).forEach((attribute, modifier) -> {
                var key = new ModifierKey(slot, attribute, modifier.getId());
                var value = applied.computeIfAbsent(key, entry -> new Applied(entry.infusionId(), modifier));
                if (Double.compare(value.modifier.getValue(), modifier.getValue()) != 0
                        || value.modifier.getOperation() != modifier.getOperation()) {
                    value.modifier = new EntityAttributeModifier(value.modifier.getId(), "Feral infusion",
                            modifier.getValue(), modifier.getOperation());
                    value.changed = true;
                }
                value.wanted = true;
            });
        }
        var iterator = applied.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var value = entry.getValue();
            if (!value.wanted || value.changed) {
                var instance = owner.getAttributeInstance(entry.getKey().attribute());
                if (instance != null) instance.removeModifier(value.modifier.getId());
            }
            if (!value.wanted) iterator.remove();
        }
        applied.forEach((key, value) -> {
            var instance = owner.getAttributeInstance(key.attribute());
            if (instance != null && !instance.hasModifier(value.modifier)) {
                instance.addTemporaryModifier(value.modifier);
            }
            value.changed = false;
        });
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
