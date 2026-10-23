package sscextras.trainer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import sscextras.ConfiguredLootChance;
import sscextras.collar.AccessoryEquipment;
import sscextras.collar.Collars;
import sscextras.collar.RootChestCondition;
import static sscextras.trainer.QuadrupedalTrainer.*;

/** A single chest roll supplies the complete set. Existing item stacks own all loot. */
public final class TrainerLoot {
    private TrainerLoot() { }
    public static void addPool(LootTable.Builder table) {
        table.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                .conditionally(() -> RootChestCondition.INSTANCE)
                .conditionally(() -> new ConfiguredLootChance("cursedTrainerLootChance"))
                .with(LootTableEntry.builder(id("cursed_trainer_set"))));
    }
    public static boolean equipCarried(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !Collars.canAutoEquip(player)) return false;
        var sources = new ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().size(); i++) sources.add(player.getInventory().getStack(i));
        boolean fitted = tryEquip(serverPlayer, sources);
        if (fitted) player.getInventory().markDirty();
        return fitted;
    }
    public static boolean openedContainer(ServerPlayerEntity player) {
        var slots = player.currentScreenHandler.slots.stream()
                .filter(slot -> slot.inventory != player.getInventory() && slot.canTakeItems(player)).toList();
        if (!tryEquip(player, slots.stream().map(slot -> slot.getStack()).toList())) return false;
        slots.forEach(slot -> slot.markDirty()); player.currentScreenHandler.sendContentUpdates();
        return true;
    }
    public static boolean tryEquip(ServerPlayerEntity player, List<ItemStack> sources) {
        if (!Collars.canAutoEquip(player) || fullSet(player) || complete(player)
                || sscextras.player.PlayerStance.quadrupedal(player)) return false;
        var locations = new ArrayList<Integer>();
        var selected = new ArrayList<ItemStack>();
        var counts = new java.util.IdentityHashMap<ItemStack, Integer>();
        var state = TrainerState.get(player);
        boolean refitting = equipment(player) != 0 || state.handsTicks > 0 || state.feetTicks > 0;
        for (int location : new int[]{HANDS, FEET, NECK}) {
            if (!equipped(player, location, false).isEmpty()) continue;
            // Automatic loot never displaces the player's currently worn gear.
            var slots = stacks(player, location);
            if (slots.isEmpty() || slots.stream().anyMatch(stack -> !stack.isEmpty())) {
                if (refitting) continue;
                return false;
            }
            ItemStack found = null;
            for (var source : sources) if (source.isOf(location == NECK ? COLLAR : MOLD)
                    && source.getCount() > counts.getOrDefault(source, 0)) { found = source; break; }
            if (found == null) {
                if (refitting) continue;
                return false;
            }
            if (fit(player, location) != Fit.SUITABLE && refitting) continue;
            locations.add(location);
            selected.add(found); counts.merge(found, 1, Integer::sum);
        }
        var body = locations.stream().map(location -> fit(player, location))
                .filter(value -> value != Fit.SUITABLE).findFirst().orElse(Fit.SUITABLE);
        if (locations.isEmpty()) return false;
        if (body != Fit.SUITABLE || complete(player)) {
            var form = net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager.getForm(player).FormID;
            if (!form.equals(state.autoRejectedForm)) {
                reject(player, body == Fit.SUITABLE ? Fit.FERAL : body); state.autoRejectedForm = form;
            }
            return false;
        }
        state.autoRejectedForm = null;
        for (int i = 0; i < locations.size(); i++) {
            if (!equipPiece(player, locations.get(i), selected.get(i))) { refresh(player); return false; }
        }
        refresh(player);
        player.sendMessage(Text.translatable(fullSet(player) ? "message.ssc-extras.trainer.auto_equipped"
                : "message.ssc-extras.trainer.refitted"), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                player.getSoundCategory(), 1, .65f);
        return true;
    }

    /** Explicit item use can fit one pair, including after completion or collar loss. */
    public static boolean equipHeld(ServerPlayerEntity player, ItemStack stack) {
        if (!stack.isOf(MOLD) && !stack.isOf(COLLAR)) return false;
        Fit rejected = null;
        for (int location : stack.isOf(COLLAR) ? new int[]{NECK} : new int[]{HANDS, FEET}) {
            var available = stacks(player, location);
            if (available.isEmpty() || available.stream().anyMatch(s -> !s.isEmpty())) continue;
            var body = fit(player, location);
            if (body != Fit.SUITABLE) { rejected = body; continue; }
            if (!equipPiece(player, location, stack)) return false;
            refresh(player);
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                    player.getSoundCategory(), 1, .65f);
            return true;
        }
        if (rejected != null) reject(player, rejected);
        return false;
    }

    private static boolean equipPiece(ServerPlayerEntity player, int location, ItemStack source) {
        var template = source.copyWithCount(1); binding(template, true);
        var fitted = AccessoryEquipment.forceEquip(player, group(location), slot(location), template);
        if (fitted == null || !fitted.changed()) return false;
        // Consume only a verified write. No duplicate inventory or rollback state.
        source.decrement(1);
        return true;
    }
}
