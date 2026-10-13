package sscextras.player;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import sscextras.drake.BeastizationCatalyst;

public final class PlayerMind {
    public interface State {
        Runtime sscExtras$mind();
        int sscExtras$baseInt();
        void sscExtras$baseInt(int value);
        int sscExtras$effectiveInt();
        void sscExtras$effectiveInt(int value);
    }

    public static final class Runtime {
        private final Map<Identifier, IntLevel> limits = new LinkedHashMap<>();
        private IntLevel beforeFeral = IntLevel.FUTURISTIC;
        private boolean loaded;
        long nextHint;
    }

    private PlayerMind() { }
    private static State state(PlayerEntity player) { return (State)player; }
    public static boolean enabled(PlayerEntity player) { return sscextras.SscExtrasGameRules.intSystemEnabled(player); }
    public static IntLevel baseInt(PlayerEntity player) { return IntLevel.fromOrdinal(state(player).sscExtras$baseInt()); }
    public static IntLevel effectiveInt(PlayerEntity player) {
        if (player.hasStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED)) return IntLevel.FERAL;
        if (player.getWorld().isClient) return IntLevel.fromOrdinal(state(player).sscExtras$effectiveInt());
        IntLevel result = baseInt(player);
        for (IntLevel limit : state(player).sscExtras$mind().limits.values()) result = IntLevel.lower(result, limit);
        return result;
    }
    public static boolean feral(PlayerEntity player) {
        return enabled(player) ? effectiveInt(player) == IntLevel.FERAL
                : sscextras.drake.DrakeFeralization.permanent(player) || sscextras.drake.DrakeFeralization.temporary(player);
    }
    public static boolean permits(PlayerEntity player, IntLevel required) {
        return !enabled(player) || player.isCreative() || player.isSpectator() || effectiveInt(player).permits(required);
    }
    public record Decision(boolean allowed, IntLevel required, net.minecraft.text.Text reason) { }
    public static Decision check(PlayerEntity player, MentalContext context) {
        IntLevel required = MentalRequirements.required(context);
        return new Decision(permits(player, required), required, net.minecraft.text.Text.translatable(
                "message.ssc-extras.int.required", required.text()).styled(style -> style.withInsertion("ssc-extras:mental_hint")));
    }
    public static boolean allow(PlayerEntity player, MentalContext context) {
        var result = check(player, context);
        if (!result.allowed && player instanceof ServerPlayerEntity serverPlayer) {
            var runtime = state(player).sscExtras$mind();
            if (player.getWorld().getTime() >= runtime.nextHint) {
                runtime.nextHint = player.getWorld().getTime() + 40;
                serverPlayer.sendMessage(result.reason, true);
            }
        }
        return result.allowed;
    }
    public static boolean allowEquipmentClick(PlayerEntity player, int slot, int button, net.minecraft.screen.slot.SlotActionType action) {
        if (player.currentScreenHandler != player.playerScreenHandler) return true;
        var handler = player.playerScreenHandler;
        net.minecraft.item.ItemStack incoming = net.minecraft.item.ItemStack.EMPTY;
        if (slot >= 5 && slot <= 8) {
            if (action == net.minecraft.screen.slot.SlotActionType.SWAP && (button >= 0 && button <= 8 || button == 40))
                incoming = player.getInventory().getStack(button);
            else if (action == net.minecraft.screen.slot.SlotActionType.PICKUP || action == net.minecraft.screen.slot.SlotActionType.QUICK_CRAFT)
                incoming = handler.getCursorStack();
        } else if (slot >= 9 && slot < handler.slots.size() && action == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE) {
            var stack = handler.getSlot(slot).getStack();
            if (stack.getItem() instanceof net.minecraft.item.ArmorItem armor && player.getEquippedStack(armor.getSlotType()).isEmpty()) incoming = stack;
        }
        return incoming.isEmpty() || allow(player, MentalContext.item(MentalContext.Action.EQUIP, incoming));
    }
    public static boolean allowHeldTool(PlayerEntity player) {
        var inventory = player.getInventory();
        return allow(player, MentalContext.item(MentalContext.Action.USE, inventory.main.get(inventory.selectedSlot)));
    }
    public static void setBaseInt(ServerPlayerEntity player, IntLevel level) {
        state(player).sscExtras$baseInt(level.ordinal());
        state(player).sscExtras$mind().loaded = true;
        refresh(player);
    }
    public static void limitInt(ServerPlayerEntity player, Identifier source, IntLevel level) {
        state(player).sscExtras$mind().limits.put(source, level);
        refresh(player);
    }
    public static void removeLimit(ServerPlayerEntity player, Identifier source) {
        state(player).sscExtras$mind().limits.remove(source);
        refresh(player);
    }
    public static void feralize(ServerPlayerEntity player) {
        var runtime = state(player).sscExtras$mind();
        if (!((sscextras.drake.DrakeFeralization.State)player).sscExtras$feral()) runtime.beforeFeral = baseInt(player);
        setBaseInt(player, IntLevel.FERAL);
    }
    public static void recover(ServerPlayerEntity player, int dose) {
        IntLevel restored = state(player).sscExtras$mind().beforeFeral;
        setBaseInt(player, dose >= 4 ? restored : IntLevel.lower(restored,
                switch (dose) { case 0 -> IntLevel.FERAL; case 1 -> IntLevel.DUMB; case 2 -> IntLevel.SIMPLE; default -> IntLevel.REGULAR; }));
    }
    public static void restore(ServerPlayerEntity player) { recover(player, 4); }

    public static void refresh(ServerPlayerEntity player) {
        var value = state(player);
        int next = effectiveInt(player).ordinal();
        int previous = value.sscExtras$effectiveInt();
        if (next == previous) return;
        value.sscExtras$effectiveInt(next);
        if (enabled(player) && next < previous) {
            player.clearActiveItem();
            if (player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();
        }
        player.playerScreenHandler.onContentChanged(player.getInventory());
    }

    public static void write(PlayerEntity player, NbtCompound nbt) {
        nbt.putString("SscExtrasInt", baseInt(player).id());
        nbt.putString("SscExtrasIntBeforeFeral", state(player).sscExtras$mind().beforeFeral.id());
    }
    public static void read(PlayerEntity player, NbtCompound nbt) {
        var value = state(player); var runtime = value.sscExtras$mind();
        runtime.limits.clear();
        IntLevel level = IntLevel.FUTURISTIC;
        if (nbt.contains("SscExtrasInt")) level = saved(nbt.getString("SscExtrasInt"));
        else if (nbt.getBoolean("SscExtrasFeralMind")) level = legacy(nbt.getInt("SscExtrasSentience"));
        runtime.beforeFeral = nbt.contains("SscExtrasIntBeforeFeral") ? saved(nbt.getString("SscExtrasIntBeforeFeral")) : IntLevel.FUTURISTIC;
        runtime.loaded = nbt.contains("SscExtrasInt") || nbt.getBoolean("SscExtrasFeralMind");
        value.sscExtras$baseInt(level.ordinal()); value.sscExtras$effectiveInt(level.ordinal());
    }
    private static IntLevel saved(String name) {
        try { return IntLevel.fromId(name); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown saved SSC Extras INT: " + name, error); }
    }
    private static IntLevel legacy(int dose) {
        return switch (dose) { case 1 -> IntLevel.DUMB; case 2 -> IntLevel.SIMPLE; case 3 -> IntLevel.REGULAR; default -> IntLevel.FERAL; };
    }
    public static void importLegacy(PlayerEntity player, int dose) {
        var value = state(player);
        if (value.sscExtras$mind().loaded) return;
        value.sscExtras$mind().loaded = true;
        value.sscExtras$baseInt(legacy(dose).ordinal());
        value.sscExtras$effectiveInt(effectiveInt(player).ordinal());
    }
    public static void copy(PlayerEntity oldPlayer, PlayerEntity newPlayer) {
        var from = state(oldPlayer); var to = state(newPlayer);
        to.sscExtras$baseInt(from.sscExtras$baseInt());
        to.sscExtras$effectiveInt(from.sscExtras$baseInt());
        to.sscExtras$mind().beforeFeral = from.sscExtras$mind().beforeFeral;
        to.sscExtras$mind().loaded = from.sscExtras$mind().loaded;
    }
}
