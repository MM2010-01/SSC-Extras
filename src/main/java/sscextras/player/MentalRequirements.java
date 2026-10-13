package sscextras.player;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import sscextras.player.MentalContext.Action;

public final class MentalRequirements {
    private record Rules(Map<String, IntLevel> mods, Map<String, IntLevel> overrides, Map<Identifier, IntLevel> recipes,
                         Map<Identifier, IntLevel> screens) { }
    private static Rules rules = defaults();
    private static final Map<Identifier, Function<MentalContext, IntLevel>> ADAPTERS = new LinkedHashMap<>();
    private MentalRequirements() { }

    public static void registerAdapter(Identifier id, Function<MentalContext, IntLevel> adapter) {
        if (ADAPTERS.putIfAbsent(id, adapter) != null) throw new IllegalArgumentException("Duplicate mental adapter: " + id);
    }
    public static IntLevel modLevel(String namespace) { return rules.mods.getOrDefault(namespace, IntLevel.REGULAR); }
    public static IntLevel screen(Identifier id) { return rules.screens.getOrDefault(id, modLevel(id.getNamespace())); }
    public static IntLevel screen(net.minecraft.screen.ScreenHandler handler) {
        var type = ((sscextras.mixin.MentalScreenHandlerAccessor)handler).sscExtras$nullableType();
        var id = type == null ? null : Registries.SCREEN_HANDLER.getId(type);
        return id == null ? IntLevel.FERAL : screen(id);
    }
    public static boolean morphscaleArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem && net.onixary.shapeShifterCurseFabric.additional_power.IsMorphScaleItemCondition.MSI_condition(null, stack);
    }
    public static IntLevel required(MentalContext context) {
        ItemStack stack = context.stack();
        if (morphscaleArmor(stack) && context.action() != Action.CRAFT) return IntLevel.FERAL;
        String kind = context.recipe() != null ? "recipes" : context.block() != null ? "blocks" : context.entity() != null ? "entities" : "items";
        IntLevel exact = rules.overrides.get(kind + "/" + context.action().name().toLowerCase(Locale.ROOT) + "/" + context.id());
        if (exact != null) return exact;
        if (context.recipe() != null && rules.recipes.containsKey(context.id())) return rules.recipes.get(context.id());
        IntLevel taggedOverride = tagged(context, "overrides");
        if (taggedOverride != null) return taggedOverride;
        IntLevel integration = null;
        for (var adapter : ADAPTERS.values()) {
            IntLevel value = adapter.apply(context);
            if (value != null) integration = integration == null ? value : IntLevel.higher(integration, value);
        }
        if (integration != null) return integration;
        IntLevel tagged = tagged(context, "requirements");
        if (tagged != null) return tagged;
        if (context.recipe() != null) return recipe(context);
        if (context.block() != null) return block(context.block());
        if (context.entity() != null) {
            return context.entity() instanceof AnimalEntity || context.entity() instanceof BoatEntity
                    || context.entity() instanceof ItemFrameEntity || context.entity() instanceof ArmorStandEntity
                    ? IntLevel.DUMB : modLevel(context.id().getNamespace());
        }
        if (stack.isEmpty()) return IntLevel.FERAL;
        Item item = stack.getItem();
        if (stack.isFood() && context.action() != Action.CRAFT) return IntLevel.FERAL;
        IntLevel mod = modLevel(context.id().getNamespace());
        if (item instanceof AliasedBlockItem && ((AliasedBlockItem)item).getBlock() instanceof CropBlock) return IntLevel.DUMB;
        if (context.action() != Action.CRAFT && item instanceof BlockItem block) return block(block.getBlock().getDefaultState());
        if (context.action() != Action.CRAFT && (item.getClass() == PickaxeItem.class || item.getClass() == AxeItem.class
                || item.getClass() == ShovelItem.class || item.getClass() == HoeItem.class || item.getClass() == SwordItem.class))
            return IntLevel.DUMB;
        if (mod.ordinal() > IntLevel.REGULAR.ordinal()) return mod;
        if (context.action() == Action.CRAFT) return craftItem(stack);
        if (item instanceof ToolItem || item instanceof SwordItem || item instanceof ShieldItem || item instanceof ShearsItem
                || item instanceof FishingRodItem || item instanceof BucketItem || item instanceof LeadItem
                || item instanceof BoneMealItem || item instanceof FlintAndSteelItem || item instanceof ArmorItem)
            return IntLevel.DUMB;
        return mod;
    }

    private static IntLevel tagged(MentalContext context, String group) {
        String action = context.action().name().toLowerCase(Locale.ROOT);
        for (int index = IntLevel.values().length - 1; index >= 0; index--) {
            var level = IntLevel.values()[index];
            var id = new Identifier("ssc-extras", "mental/" + group + "/" + action + "/" + level.id());
            if (!context.stack().isEmpty() && context.stack().isIn(TagKey.of(RegistryKeys.ITEM, id))) return level;
            if (context.block() != null && context.block().isIn(TagKey.of(RegistryKeys.BLOCK, id))) return level;
        }
        return null;
    }
    private static IntLevel block(BlockState state) {
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        IntLevel mod = modLevel(id.getNamespace());
        if (state.isIn(TagKey.of(RegistryKeys.BLOCK, new Identifier("ssc-extras", "mental/simple_building")))) return IntLevel.DUMB;
        if (mod.ordinal() > IntLevel.REGULAR.ordinal()) return mod;
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof BedBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof CampfireBlock
                || block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock
                || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock || block instanceof CaveVines)
            return IntLevel.DUMB;
        if (block instanceof CraftingTableBlock || block instanceof ComposterBlock || block instanceof FurnaceBlock || block instanceof SmokerBlock)
            return IntLevel.SIMPLE;
        return mod;
    }
    private static IntLevel craftItem(ItemStack stack) {
        if (stack.isFood()) return IntLevel.SIMPLE;
        Item item = stack.getItem();
        if (item instanceof ToolItem tool && (tool.getMaterial() == ToolMaterials.WOOD || tool.getMaterial() == ToolMaterials.STONE)) return IntLevel.SIMPLE;
        if (stack.isIn(TagKey.of(RegistryKeys.ITEM, new Identifier("ssc-extras", "mental/simple_crafts")))) return IntLevel.SIMPLE;
        return modLevel(Registries.ITEM.getId(item).getNamespace());
    }
    private static IntLevel recipe(MentalContext context) {
        var recipe = context.recipe();
        Identifier type = Registries.RECIPE_TYPE.getId(recipe.getType());
        IntLevel output = craftItem(context.stack());
        IntLevel machine = recipe instanceof AbstractCookingRecipe
                ? recipe.getType() == RecipeType.CAMPFIRE_COOKING ? IntLevel.DUMB : context.stack().isFood() ? IntLevel.SIMPLE : IntLevel.REGULAR
                : type.getNamespace().equals("minecraft") ? IntLevel.SIMPLE : modLevel(type.getNamespace());
        IntLevel origin = modLevel(context.id().getNamespace());
        if (origin.ordinal() > IntLevel.REGULAR.ordinal()) machine = IntLevel.higher(machine, origin);
        if (recipe.getType() == RecipeType.CAMPFIRE_COOKING && context.stack().isFood()) return IntLevel.DUMB;
        return IntLevel.higher(machine, output);
    }

    private static Rules defaults() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MentalRequirements.class.getResourceAsStream(
                "/data/ssc-extras/mental/defaults.json")), StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject(), empty());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static Rules empty() { return new Rules(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>()); }
    private static Rules parse(JsonObject json, Rules target) {
        read(json, "mods", target.mods, Function.identity());
        read(json, "overrides", target.overrides, Function.identity());
        read(json, "recipes", target.recipes, Identifier::new);
        read(json, "screens", target.screens, Identifier::new);
        return target;
    }
    private static <K> void read(JsonObject json, String key, Map<K, IntLevel> target, Function<String, K> convert) {
        if (!json.has(key)) return;
        for (var entry : json.getAsJsonObject(key).entrySet()) target.put(convert.apply(entry.getKey()), IntLevel.fromId(entry.getValue().getAsString()));
    }
    public static String encode() {
        var json = new JsonObject();
        write(json, "mods", rules.mods); write(json, "overrides", rules.overrides); write(json, "recipes", rules.recipes);
        write(json, "screens", rules.screens);
        return json.toString();
    }
    private static void write(JsonObject json, String key, Map<?, IntLevel> values) {
        var map = new JsonObject(); values.forEach((id, value) -> map.addProperty(id.toString(), value.id())); json.add(key, map);
    }
    public static void receive(String json) { rules = parse(JsonParser.parseString(json).getAsJsonObject(), empty()); }
    public static void resetClient() { rules = defaults(); }
    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("ssc-extras", "mental_requirements"); }
            @Override public void reload(ResourceManager manager) {
                Rules next = defaults();
                var resources = new TreeMap<>(manager.findResources("mental", id -> id.getPath().endsWith(".json")));
                var defaultsId = new Identifier("ssc-extras", "mental/defaults.json");
                var base = resources.remove(defaultsId);
                if (base != null) {
                    try (var reader = base.getReader()) { parse(JsonParser.parseReader(reader).getAsJsonObject(), next); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                }
                for (var entry : resources.entrySet()) {
                    try (var reader = entry.getValue().getReader()) { parse(JsonParser.parseReader(reader).getAsJsonObject(), next); }
                    catch (java.io.IOException | RuntimeException error) { throw new IllegalArgumentException("Invalid mental requirements: " + entry.getKey(), error); }
                }
                rules = next;
            }
        });
    }
}
