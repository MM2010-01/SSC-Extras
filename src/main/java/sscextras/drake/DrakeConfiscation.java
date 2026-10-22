package sscextras.drake;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.IsMorphScaleItemCondition;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import sscextras.collar.AccessoryEquipment;
import sscextras.collar.Collars;
import sscextras.collar.CuriosCompat;
import java.util.EnumSet;
import java.util.function.Predicate;

/** Capture/search policy. The existing claim owns personal progress; the stable owns seized cargo. */
public final class DrakeConfiscation {
    public static final TagKey<Item> WHITELIST = TagKey.of(RegistryKeys.ITEM, EarthenDrake.id("confiscation_whitelist"));

    public static final class State {
        boolean initialDone, initialTaken, feedingCatalysts, catalystHint, checkDue, misbehaved;
        int chance = 10;
        long lastDay = Long.MIN_VALUE;

        void adjust(int points) { chance = Math.max(0, Math.min(100, chance + points)); }

        NbtCompound write() {
            var tag = new NbtCompound();
            tag.putBoolean("InitialDone", initialDone); tag.putBoolean("InitialTaken", initialTaken);
            tag.putBoolean("FeedingCatalysts", feedingCatalysts); tag.putBoolean("CatalystHint", catalystHint);
            tag.putBoolean("CheckDue", checkDue); tag.putInt("Chance", chance); tag.putLong("LastDay", lastDay);
            tag.putBoolean("Misbehaved", misbehaved);
            return tag;
        }

        static State read(NbtCompound tag) {
            var state = new State();
            state.initialDone = tag.getBoolean("InitialDone"); state.initialTaken = tag.getBoolean("InitialTaken");
            state.feedingCatalysts = tag.getBoolean("FeedingCatalysts"); state.catalystHint = tag.getBoolean("CatalystHint");
            state.checkDue = tag.getBoolean("CheckDue");
            state.misbehaved = tag.getBoolean("Misbehaved");
            state.chance = Math.max(0, Math.min(100, tag.getInt("Chance")));
            state.lastDay = tag.getLong("LastDay");
            return state;
        }
    }

    enum Contraband {
        KEY("contraband_key"), INHIBITOR("contraband_inhibitor"), LAVA("contraband_lava");
        final String line;
        Contraband(String line) { this.line = line; }
    }

    private DrakeConfiscation() { }

    static Contraband contraband(ItemStack stack) {
        if (stack.isOf(Collars.CLEANSING_KEY)) return Contraband.KEY;
        if (stack.isOf(RegCustomItem.INHIBITOR) || stack.isOf(RegCustomItem.POWERFUL_INHIBITOR)
                || stack.isOf(RegCustomItem.CREATIVE_INHIBITOR)) return Contraband.INHIBITOR;
        return stack.isOf(Items.LAVA_BUCKET) ? Contraband.LAVA : null;
    }

    static boolean allowed(ItemStack stack) {
        return stack.isEmpty() || contraband(stack) == null && (stack.isIn(WHITELIST)
                || IsMorphScaleItemCondition.MSI_condition(null, stack));
    }

    static void tick(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var state = claim.confiscation;
        long day = Math.floorDiv(player.getWorld().getTimeOfDay(), 24000);
        if (state.lastDay == Long.MIN_VALUE) { state.lastDay = day; dirty(player); return; }
        // One roll on a newly observed day; no catch-up queue or rerolls after time moves backward.
        if (day <= state.lastDay) return;
        state.lastDay = day;
        if (state.initialDone && !claim.soulbound && player.isAlive() && !player.isCreative()
                && !player.isSpectator() && !claim.awaitingRespawn && !state.checkDue)
            state.checkDue = player.getRandom().nextInt(100) < state.chance;
        dirty(player);
    }

    static void goodService(DrakeOutpostOwnership.Claim claim, int before, int after) {
        var state = claim.confiscation;
        state.misbehaved |= claim.tryingToEscape;
        int days = after / 24000 - before / 24000;
        if (days <= 0) return;
        if (!claim.tryingToEscape) state.adjust(-Math.max(0, days - (state.misbehaved ? 1 : 0)));
        state.misbehaved = claim.tryingToEscape;
    }

    static void dirty(ServerPlayerEntity player) { DrakeOutpostOwnership.get(player.getServer()).markDirty(); }

    /** A single inspection traverses physical inventory, cursor, active accessories and Rider's Chest cargo. */
    static EnumSet<Contraband> take(ServerPlayerEntity player, DrakeConfiscationStorage storage, boolean initial,
                                    boolean keepCatalysts) {
        var found = EnumSet.noneOf(Contraband.class);
        Predicate<ItemStack> take = stack -> !stack.isEmpty() && (initial ? !allowed(stack)
                && !(keepCatalysts && stack.isOf(RegCustomItem.CATALYST)) : contraband(stack) != null);
        var seenChests = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack, Boolean>());
        java.util.function.Consumer<ItemStack> collect = stack -> {
            var kind = contraband(stack); if (kind != null) found.add(kind);
            storage.cargo.add(stack);
        };
        java.util.function.Consumer<ItemStack> inspectChest = stack -> {
            if (stack.isOf(DrakeEquipment.RIDERS_CHEST) && seenChests.add(stack)) {
                var chest = stack == RiderChestInventory.equipped(player) ? RiderChestInventory.get(player)
                        : new RiderChestInventory(player, stack);
                takeInventory(chest, take, collect);
            }
        };
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) inspectChest.accept(inventory.getStack(i));
        takeInventory(inventory, take, collect);
        var cursor = player.currentScreenHandler.getCursorStack();
        inspectChest.accept(cursor);
        if (take.test(cursor)) { player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); collect.accept(cursor); }
        var io = AccessoryEquipment.slots();
        if (io != null) {
            var slots = CuriosCompat.instance == null ? io.getEntitySlots(player) : CuriosCompat.instance.allSlots(player);
            if (slots != null) for (var entry : slots.entrySet()) for (int i = 0; i < entry.getValue().size(); i++) {
                var stack = io.getEntitySlot(player, entry.getKey().getLeft(), entry.getKey().getRight(), i);
                if (stack == null) continue;
                inspectChest.accept(stack);
                if (take.test(stack)) AccessoryEquipment.clear(player, io, entry.getKey().getLeft(),
                        entry.getKey().getRight(), i, collect);
            }
        }
        inventory.markDirty(); player.currentScreenHandler.sendContentUpdates(); dirty(player);
        return found;
    }

    private static void takeInventory(Inventory inventory, Predicate<ItemStack> take,
                                      java.util.function.Consumer<ItemStack> collect) {
        for (int i = 0; i < inventory.size(); i++) if (take.test(inventory.getStack(i))) collect.accept(inventory.removeStack(i));
        inventory.markDirty();
    }

    private record Dose(ItemStack stack, java.util.function.Consumer<ItemStack> replace) { }

    private static Dose catalystIn(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(RegCustomItem.CATALYST)) {
            final int slot = i;
            return new Dose(inventory.getStack(i), stack -> { inventory.setStack(slot, stack); inventory.markDirty(); });
        }
        return null;
    }

    private static Dose catalystInChest(ServerPlayerEntity player, ItemStack stack) {
        return !stack.isOf(DrakeEquipment.RIDERS_CHEST) ? null : catalystIn(stack == RiderChestInventory.equipped(player)
                ? RiderChestInventory.get(player) : new RiderChestInventory(player, stack));
    }

    private static Dose catalyst(ServerPlayerEntity player) {
        var dose = catalystIn(player.getInventory());
        if (dose != null) return dose;
        var cursor = player.currentScreenHandler.getCursorStack();
        if (cursor.isOf(RegCustomItem.CATALYST)) return new Dose(cursor, player.currentScreenHandler::setCursorStack);
        dose = catalystInChest(player, cursor); if (dose != null) return dose;
        for (int i = 0; i < player.getInventory().size(); i++) {
            dose = catalystInChest(player, player.getInventory().getStack(i)); if (dose != null) return dose;
        }
        var io = AccessoryEquipment.slots();
        if (io == null) return null;
        var slots = CuriosCompat.instance == null ? io.getEntitySlots(player) : CuriosCompat.instance.allSlots(player);
        if (slots != null) for (var entry : slots.entrySet()) for (int i = 0; i < entry.getValue().size(); i++) {
            var stack = io.getEntitySlot(player, entry.getKey().getLeft(), entry.getKey().getRight(), i);
            if (stack == null) continue;
            if (stack.isOf(RegCustomItem.CATALYST)) {
                final int slot = i;
                return new Dose(stack, remaining -> io.setEntitySlot(player, entry.getKey().getLeft(), entry.getKey().getRight(), slot, remaining));
            }
            dose = catalystInChest(player, stack); if (dose != null) return dose;
        }
        return null;
    }

    static boolean hasCatalyst(ServerPlayerEntity player) { return catalyst(player) != null; }

    static void feedCatalyst(ServerPlayerEntity player, DrakeConfiscationStorage storage) {
        var dose = catalyst(player);
        if (dose == null) return;
        var food = dose.stack().split(1); dose.replace().accept(dose.stack());
        // Native finishUsing owns food, instinct/progression and the returned bowl; no invented transformation.
        var result = food.finishUsing(player.getWorld(), player);
        if (!result.isEmpty()) storage.cargo.add(result);
        dirty(player);
        player.currentScreenHandler.sendContentUpdates();
    }
}
