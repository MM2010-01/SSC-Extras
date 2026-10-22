package sscextras.player;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.CreatureInstinct;
import sscextras.SscExtrasConfig;
import sscextras.SscExtrasGameRules;
import sscextras.drake.SentientCatalyst;

/** Server action policy; PlayerMind owns the reserve, persistence and synchronization. */
public final class MentalProgression {
    public static final int CRAFT_COOLDOWN = 200, BOOK_COOLDOWN = 24000;
    public static final String BOOK_READERS = "SscExtrasIntReaders";
    private static final TagKey<net.minecraft.item.Item> BOOKS = TagKey.of(RegistryKeys.ITEM, new Identifier("ssc-extras", "mental/readable_books"));
    private static final TagKey<net.minecraft.item.Item> RAW_MEAT = TagKey.of(RegistryKeys.ITEM, new Identifier("ssc-extras", "mental/raw_meat"));

    static final class Runtime {
        double instinctRemainder, permanentRemainder;
        int permanentTicks;
        final Map<Identifier, Long> recipes = new HashMap<>();
    }
    private MentalProgression() { }
    private static Runtime state(PlayerEntity player) { return PlayerMind.runtime(player).progression; }
    public static boolean eligible(ServerPlayerEntity player) {
        return PlayerMind.enabled(player) && player.isAlive() && !player.isCreative() && !player.isSpectator();
    }
    public static long time(ServerPlayerEntity player) { return player.getServer().getOverworld().getTime(); }

    public static void tierChanged(ServerPlayerEntity player, IntLevel before, IntLevel after) {
        int direction = Integer.compare(after.ordinal(), before.ordinal());
        for (int index = before.ordinal(); index != after.ordinal();) {
            index += direction;
            Text hint = direction > 0 ? SentientCatalyst.hint(index)
                    : Text.translatable("message.ssc-extras.int.falling." + IntLevel.fromOrdinal(index).id())
                        .formatted(Formatting.LIGHT_PURPLE).styled(style -> style.withInsertion("ssc-extras:mental_hint"));
            player.sendMessage(hint, false);
        }
    }

    /** Only the accepted, clamped gain counts; blocked gains and native permanent-form gauge fills do not. */
    public static void instinctGained(PlayerEntity entity, double amount) {
        if (!(entity instanceof ServerPlayerEntity player) || !eligible(player) || !Double.isFinite(amount) || amount <= 0
                || !player.getWorld().getGameRules().getBoolean(SscExtrasGameRules.INSTINCT_GAIN_INT_DRAIN)) return;
        if (FormAbilityManager.getForm(player).getIndex() >= 2 && CreatureInstinct.permanentTarget(player) == null) return;
        var state = state(player);
        double pending = state.instinctRemainder + amount / SscExtrasConfig.instinctPerIntLoss();
        double loss = Math.floor(pending * 100 + 1e-8) / 100;
        state.instinctRemainder = Math.max(0, pending - loss);
        lose(player, loss);
    }

    private static void lose(ServerPlayerEntity player, double amount) {
        // PlayerMind deliberately bounds each action; a configured ratio can exceed that bound.
        while (amount >= 0.009999) {
            double step = Math.min(PlayerMind.MAX_POINTS, amount);
            if (PlayerMind.losePoints(player, step) == 0) break;
            amount -= step;
        }
    }

    /** Called by the existing player lifecycle. Counts eligible online ticks, never offline catch-up. */
    public static void tick(ServerPlayerEntity player) {
        var state = state(player);
        if (!eligible(player) || !player.getWorld().getGameRules().getBoolean(SscExtrasGameRules.PERMANENT_FORM_INT_DRAIN)
                || FormAbilityManager.getForm(player).getIndex() < 3) {
            state.permanentTicks = 0;
            return;
        }
        if (++state.permanentTicks < SscExtrasConfig.permanentFormIntIntervalTicks()) return;
        state.permanentTicks = 0;
        double pending = state.permanentRemainder + SscExtrasConfig.permanentFormIntLoss();
        double loss = Math.floor(pending * 100 + 1e-8) / 100;
        state.permanentRemainder = Math.max(0, pending - loss);
        PlayerMind.losePoints(player, loss);
    }

    public static double crafted(ServerPlayerEntity player, Identifier recipe, double reward) {
        if (!eligible(player) || reward <= 0 || PlayerMind.points(player) >= PlayerMind.MAX_POINTS) return 0;
        long now = time(player);
        var recipes = state(player).recipes;
        recipes.entrySet().removeIf(entry -> now < entry.getValue() || now - entry.getValue() >= CRAFT_COOLDOWN);
        if (recipes.containsKey(recipe)) return 0;
        double gain = PlayerMind.gainPoints(player, reward);
        if (gain > 0) recipes.put(recipe, now);
        return gain;
    }

    public static boolean isBook(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)
                || stack.getItem() instanceof net.minecraft.item.WritableBookItem
                || stack.getItem() instanceof net.minecraft.item.WrittenBookItem || stack.isIn(BOOKS));
    }
    public static boolean allowBook(PlayerEntity player, ItemStack stack) {
        return PlayerMind.allow(player, MentalContext.item(MentalContext.Action.READ, stack));
    }
    public static double readBook(ServerPlayerEntity player, ItemStack book) {
        if (!isBook(book) || !eligible(player) || !PlayerMind.permits(player, IntLevel.SIMPLE)
                || PlayerMind.points(player) >= PlayerMind.MAX_POINTS) return 0;
        long now = time(player);
        var readers = book.getOrCreateSubNbt(BOOK_READERS);
        String reader = player.getUuidAsString();
        if (readers.contains(reader, NbtElement.NUMBER_TYPE) && now >= readers.getLong(reader)
                && now - readers.getLong(reader) < BOOK_COOLDOWN) return 0;
        boolean simple = PlayerMind.effectiveInt(player) == IntLevel.SIMPLE;
        double gain = PlayerMind.gainPoints(player, simple ? 5 : 25);
        if (gain > 0) {
            readers.putLong(reader, now);
            player.getInventory().markDirty();
            player.sendMessage(Text.translatable("message.ssc-extras.int.book." + (simple ? "simple" : "clear"))
                    .formatted(Formatting.AQUA).styled(style -> style.withInsertion("ssc-extras:mental_hint")), false);
        }
        return gain;
    }
    public static void ate(ServerPlayerEntity player, ItemStack food) {
        if (food.isFood() && food.isIn(RAW_MEAT)) PlayerMind.losePoints(player, 5);
    }

    static void write(PlayerEntity player, NbtCompound nbt) {
        var state = state(player); var tag = new NbtCompound();
        tag.putDouble("InstinctRemainder", state.instinctRemainder);
        tag.putDouble("PermanentRemainder", state.permanentRemainder);
        tag.putInt("PermanentTicks", state.permanentTicks);
        var recipes = new NbtCompound();
        state.recipes.forEach((id, last) -> recipes.putLong(id.toString(), last));
        tag.put("Recipes", recipes); nbt.put("SscExtrasIntProgression", tag);
    }
    static void read(PlayerEntity player, NbtCompound nbt) {
        var state = state(player); var tag = nbt.getCompound("SscExtrasIntProgression");
        state.instinctRemainder = remainder(tag.getDouble("InstinctRemainder"));
        state.permanentRemainder = remainder(tag.getDouble("PermanentRemainder"));
        state.permanentTicks = Math.max(0, Math.min(tag.getInt("PermanentTicks"), SscExtrasConfig.permanentFormIntIntervalTicks() - 1));
        state.recipes.clear();
        var recipes = tag.getCompound("Recipes");
        for (String key : recipes.getKeys()) {
            var id = Identifier.tryParse(key);
            if (id != null && recipes.contains(key, NbtElement.NUMBER_TYPE)) state.recipes.put(id, recipes.getLong(key));
        }
    }
    private static double remainder(double value) { return Double.isFinite(value) && value >= 0 && value < 0.01 ? value : 0; }
    static void copy(PlayerEntity oldPlayer, PlayerEntity newPlayer) {
        var from = state(oldPlayer); var to = state(newPlayer);
        to.instinctRemainder = from.instinctRemainder; to.permanentRemainder = from.permanentRemainder;
        to.permanentTicks = from.permanentTicks; to.recipes.clear(); to.recipes.putAll(from.recipes);
    }
}
