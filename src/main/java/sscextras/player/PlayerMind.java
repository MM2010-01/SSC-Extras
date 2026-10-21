package sscextras.player;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import sscextras.drake.BeastizationCatalyst;

public final class PlayerMind {
    public static final int POINTS_PER_LEVEL = 100, MAX_POINTS = 400;
    private static final int UNITS_PER_POINT = 100, MAX_UNITS = MAX_POINTS * UNITS_PER_POINT;
    public static final double MIN_POINTS = Integer.MIN_VALUE / (double)UNITS_PER_POINT;
    public interface State {
        Runtime sscExtras$mind();
        int sscExtras$intUnits();
        void sscExtras$intUnits(int value);
        int sscExtras$effectiveInt();
        void sscExtras$effectiveInt(int value);
    }

    public static final class Runtime {
        private final Map<Identifier, IntLevel> limits = new LinkedHashMap<>();
        private int beforeFeral = MAX_UNITS;
        private boolean loaded;
        long nextHint;
    }

    private PlayerMind() { }
    private static State state(PlayerEntity player) { return (State)player; }
    public static boolean enabled(PlayerEntity player) { return sscextras.SscExtrasGameRules.intSystemEnabled(player); }
    public static double points(PlayerEntity player) { return state(player).sscExtras$intUnits() / (double)UNITS_PER_POINT; }
    public static double effectivePoints(PlayerEntity player) {
        return Math.max(0, Math.min(points(player), effectiveInt(player).ordinal() * POINTS_PER_LEVEL));
    }
    public static IntLevel baseInt(PlayerEntity player) { return level(state(player).sscExtras$intUnits()); }
    private static IntLevel level(int units) {
        if (units <= 0) return IntLevel.FERAL;
        int step = POINTS_PER_LEVEL * UNITS_PER_POINT;
        return IntLevel.fromOrdinal((units + step - 1) / step);
    }
    private static int units(double points) {
        if (!Double.isFinite(points)) throw new IllegalArgumentException("INT points must be finite");
        return (int)Math.round(Math.max(MIN_POINTS, Math.min(MAX_POINTS, points)) * UNITS_PER_POINT);
    }
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
        setPoints(player, level.ordinal() * POINTS_PER_LEVEL);
    }
    public static void setPoints(ServerPlayerEntity player, double points) {
        state(player).sscExtras$intUnits(units(points));
        state(player).sscExtras$mind().loaded = true;
        refresh(player);
    }
    /** Commit a completed server action. Returns the actual loss; never changes instinct or body state. */
    public static double losePoints(ServerPlayerEntity player, double amount) {
        int loss = units(Math.max(0, amount));
        if (loss == 0 || !enabled(player) || !player.isAlive() || player.isCreative() || player.isSpectator()) return 0;
        int previous = state(player).sscExtras$intUnits();
        loss = (int)Math.min(loss, (long)previous - Integer.MIN_VALUE);
        if (loss == 0) return 0;
        var before = effectiveInt(player);
        setPoints(player, (previous - loss) / (double)UNITS_PER_POINT);
        if (effectiveInt(player) != before) player.sendMessage(net.minecraft.text.Text.translatable(
                "message.ssc-extras.int.decreased", effectiveInt(player).text())
                .styled(style -> style.withInsertion("ssc-extras:mental_hint")), true);
        return loss / (double)UNITS_PER_POINT;
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
        if (!((sscextras.drake.DrakeFeralization.State)player).sscExtras$feral()) runtime.beforeFeral = state(player).sscExtras$intUnits();
        setBaseInt(player, IntLevel.FERAL);
    }
    public static void recover(ServerPlayerEntity player, int dose) {
        int restored = state(player).sscExtras$mind().beforeFeral;
        int recovered = dose >= 4 ? restored : Math.min(restored, Math.max(0, dose) * POINTS_PER_LEVEL * UNITS_PER_POINT);
        setPoints(player, recovered / (double)UNITS_PER_POINT);
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
        nbt.putInt("SscExtrasIntVersion", 2);
        nbt.putString("SscExtrasInt", baseInt(player).id());
        nbt.putDouble("SscExtrasIntPoints", points(player));
        nbt.putString("SscExtrasIntBeforeFeral", level(state(player).sscExtras$mind().beforeFeral).id());
        nbt.putDouble("SscExtrasIntPointsBeforeFeral", state(player).sscExtras$mind().beforeFeral / (double)UNITS_PER_POINT);
    }
    public static void read(PlayerEntity player, NbtCompound nbt) {
        var value = state(player); var runtime = value.sscExtras$mind();
        runtime.limits.clear();
        IntLevel level = IntLevel.REGULAR;
        if (nbt.contains("SscExtrasInt")) level = saved(nbt.getString("SscExtrasInt"));
        else if (nbt.getBoolean("SscExtrasFeralMind")) level = legacy(nbt.getInt("SscExtrasSentience"));
        IntLevel previous = nbt.contains("SscExtrasIntBeforeFeral") ? saved(nbt.getString("SscExtrasIntBeforeFeral")) : IntLevel.REGULAR;
        runtime.beforeFeral = savedUnits(nbt, "SscExtrasIntPointsBeforeFeral", previous);
        runtime.loaded = nbt.contains("SscExtrasIntPoints") || nbt.contains("SscExtrasInt") || nbt.getBoolean("SscExtrasFeralMind");
        value.sscExtras$intUnits(savedUnits(nbt, "SscExtrasIntPoints", level));
        value.sscExtras$effectiveInt(baseInt(player).ordinal());
    }
    private static int savedUnits(NbtCompound nbt, String key, IntLevel fallback) {
        if (!nbt.contains(key, net.minecraft.nbt.NbtElement.NUMBER_TYPE) || !Double.isFinite(nbt.getDouble(key)))
            return fallback.ordinal() * POINTS_PER_LEVEL * UNITS_PER_POINT;
        double points = nbt.getDouble(key);
        if (nbt.getInt("SscExtrasIntVersion") < 2 && points > 200) {
            // Merge all three old upper tiers without dropping access or their partial bar.
            long oldUnits = Math.round(Math.min(500, points) * UNITS_PER_POINT);
            return 300 * UNITS_PER_POINT + (int)((oldUnits - 1) % (POINTS_PER_LEVEL * UNITS_PER_POINT) + 1);
        }
        return units(points);
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
        value.sscExtras$intUnits(legacy(dose).ordinal() * POINTS_PER_LEVEL * UNITS_PER_POINT);
        value.sscExtras$effectiveInt(effectiveInt(player).ordinal());
    }
    public static void copy(PlayerEntity oldPlayer, PlayerEntity newPlayer) {
        var from = state(oldPlayer); var to = state(newPlayer);
        to.sscExtras$intUnits(from.sscExtras$intUnits());
        to.sscExtras$effectiveInt(baseInt(oldPlayer).ordinal());
        to.sscExtras$mind().beforeFeral = from.sscExtras$mind().beforeFeral;
        to.sscExtras$mind().loaded = from.sscExtras$mind().loaded;
    }
}
