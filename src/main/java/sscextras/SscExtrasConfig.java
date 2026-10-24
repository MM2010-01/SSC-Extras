package sscextras;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

public final class SscExtrasConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ssc-extras");
    private static final Map<String, Float> LOOT_DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Float> lootChances = new LinkedHashMap<>();
    private static float instinctPerHit = 10.0f;
    private static float instinctPerPotion = 200.0f / 3.0f;
    private static float petrificationIIIMovementBreakChance = .01f;
    private static double instinctPerIntLoss = 100, permanentFormIntLoss = 0.05;
    private static Map<Identifier, Double> instinctPerIntLossOverrides = Map.of(
            new Identifier("ssc-extras", "earthen_drake"), 5.0);
    private static int permanentFormIntIntervalTicks = 200;
    private static boolean intHudAutoHide = true, quadHudAutoHide = true;
    private static Set<Identifier> permanentFormProgressionExceptions = Set.of();

    static {
        LOOT_DEFAULTS.put("cursedCollarLootChance", 1.0f / 15.0f);
        LOOT_DEFAULTS.put("cursedTrainerLootChance", 1.0f / 30.0f);
        LOOT_DEFAULTS.put("drakeCursePotionLootChance", 0.25f);
        LOOT_DEFAULTS.put("stableReinsLootChance", 1.0f);
        LOOT_DEFAULTS.put("stableSaddleLootChance", 1.0f);
        LOOT_DEFAULTS.put("stableBeastizationCatalystLootChance", 0.25f);
        LOOT_DEFAULTS.put("stableDrakeReinsDropChance", 0.085f);
        LOOT_DEFAULTS.put("stableDrakeSaddleDropChance", 0.085f);
        lootChances.putAll(LOOT_DEFAULTS);
    }

    private SscExtrasConfig() { }

    public static void load() {
        load(FabricLoader.getInstance().getConfigDir().resolve("ssc-extras.properties"));
    }

    static void load(Path file) {
        Properties config = new Properties();
        boolean readable = true;
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    config.load(reader);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("Could not load SSC Extras config; using defaults", exception);
            config.clear();
            readable = false;
        }
        boolean changed = config.putIfAbsent("instinctPerHit", "10.0") == null;
        changed |= config.putIfAbsent("instinctPerPotion", Float.toString(200.0f / 3.0f)) == null;
        changed |= config.putIfAbsent("PetrificationIIIMovementBreakChance", "0.01") == null;
        petrificationIIIMovementBreakChance = readValue(config, "PetrificationIIIMovementBreakChance", .01f, true);
        changed |= config.putIfAbsent("permanentFormProgressionExceptions", "") == null;
        changed |= config.putIfAbsent("instinctPerIntLoss", "100") == null;
        changed |= config.putIfAbsent("instinctPerIntLossOverrides", "ssc-extras:earthen_drake=5") == null;
        changed |= config.putIfAbsent("permanentFormIntLoss", "0.05") == null;
        changed |= config.putIfAbsent("permanentFormIntIntervalSeconds", "10") == null;
        changed |= config.putIfAbsent("intHudAutoHide", "true") == null;
        changed |= config.putIfAbsent("quadHudAutoHide", "true") == null;
        intHudAutoHide = readBoolean(config, "intHudAutoHide", true);
        quadHudAutoHide = readBoolean(config, "quadHudAutoHide", true);
        instinctPerIntLoss = readRange(config, "instinctPerIntLoss", 100, 0.01, 1000000);
        var ratios = new LinkedHashMap<Identifier, Double>();
        for (String entry : config.getProperty("instinctPerIntLossOverrides").split(",")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split("=", -1);
            String key = parts[0].trim();
            Identifier id = Identifier.tryParse(key);
            if (parts.length != 2 || id == null || !key.contains(":")) {
                LOGGER.warn("Invalid instinctPerIntLossOverrides entry: {}; expected namespace:form_or_group_id=ratio", entry);
                continue;
            }
            ratios.put(id, readRange("instinctPerIntLossOverrides " + key, parts[1].trim(),
                    instinctPerIntLoss, 0.01, 1000000));
        }
        instinctPerIntLossOverrides = Map.copyOf(ratios);
        permanentFormIntLoss = readRange(config, "permanentFormIntLoss", 0.05, 0, 400);
        permanentFormIntIntervalTicks = (int)Math.round(20 * readRange(config, "permanentFormIntIntervalSeconds", 10, 0.05, 86400));
        for (var entry : LOOT_DEFAULTS.entrySet()) {
            changed |= config.putIfAbsent(entry.getKey(), Float.toString(entry.getValue())) == null;
            lootChances.put(entry.getKey(), readValue(config, entry.getKey(), entry.getValue(), true));
        }
        instinctPerHit = readValue(config, "instinctPerHit", 10.0f, false);
        instinctPerPotion = readValue(config, "instinctPerPotion", 200.0f / 3.0f, false);
        var exceptions = new HashSet<Identifier>();
        for (String entry : config.getProperty("permanentFormProgressionExceptions").split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) continue;
            Identifier id = Identifier.tryParse(value);
            if (id != null && value.contains(":")) exceptions.add(id);
            else LOGGER.warn("Invalid permanentFormProgressionExceptions entry: {}; expected namespace:stage_2_form_id", value);
        }
        permanentFormProgressionExceptions = Set.copyOf(exceptions);
        if (readable && changed) {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    config.store(writer, "Restart to apply. Instinct amounts: greater than 0, at most 100.\n"
                            + "intHudAutoHide / quadHudAutoHide: client HUD hides after 60 seconds without changes (true/false; default true).\n"
                            + "instinctPerIntLoss: actual instinct gained per 1 INT lost (0.01 to 1000000).\n"
                            + "instinctPerIntLossOverrides: comma-separated namespace:form_or_group_id=ratio; same range.\n"
                            + "Exact form overrides group, then instinctPerIntLoss. Default: ssc-extras:earthen_drake=5. Empty disables overrides.\n"
                            + "permanentFormIntLoss: INT lost each interval (0 to 400); permanentFormIntIntervalSeconds: 0.05 to 86400.\n"
                            + "Both INT drains also require their gamerule and EnableINTSystem.\n"
                            + "Loot/drop chances: 0 to 1 (0 disables, 1 guarantees; 0.25 = 25%).\n"
                            + "PetrificationIIIMovementBreakChance: chance per fresh movement input to escape III into I (0 to 1; 0.01 = 1%).\n"
                            + "Collars: eligible dungeon chests. Drake Curse potions: pillager outpost chests.\n"
                            + "stable*LootChance: stable chests. stableDrake*DropChance: naturally equipped drake gear.\n"
                            + "Natural gear retains +0.01 per Looting level unless disabled. Player-supplied gear always drops.\n"
                            + "permanentFormProgressionExceptions: comma-separated stage 2 form IDs allowed without Echo Catalyst\n"
                            + "when UnlockedPermanentFormProgressionForOtherForms is false (example: shape-shifter-curse:bat_2). Empty by default.");
                }
            } catch (IOException exception) {
                LOGGER.warn("Could not save SSC Extras config defaults", exception);
            }
        }
    }

    private static float readValue(Properties config, String key, float fallback, boolean chance) {
        try {
            float value = Float.parseFloat(config.getProperty(key));
            if (Float.isFinite(value) && (chance ? value >= 0 && value <= 1 : value > 0 && value <= 100)) return value;
        } catch (NumberFormatException ignored) { }
        LOGGER.warn("Invalid {}: expected {}; using {}", key,
                chance ? "a number from 0 to 1" : "a number greater than 0 and at most 100", fallback);
        return fallback;
    }
    private static double readRange(Properties config, String key, double fallback, double min, double max) {
        return readRange(key, config.getProperty(key), fallback, min, max);
    }

    private static double readRange(String key, String raw, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(raw);
            if (Double.isFinite(value) && value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        LOGGER.warn("Invalid {}: expected {} to {}; using {}", key, min, max, fallback);
        return fallback;
    }

    private static boolean readBoolean(Properties config, String key, boolean fallback) {
        String value = config.getProperty(key).trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        LOGGER.warn("Invalid {}: expected true or false; using {}", key, fallback);
        return fallback;
    }

    public static float lootChance(String key) {
        Float chance = lootChances.get(key);
        if (chance == null) throw new IllegalArgumentException("Unknown SSC Extras loot chance: " + key);
        return chance;
    }

    public static float instinctPerHit() { return instinctPerHit; }
    public static float instinctPerPotion() { return instinctPerPotion; }
    public static float petrificationIIIMovementBreakChance() { return petrificationIIIMovementBreakChance; }
    public static double instinctPerIntLoss() { return instinctPerIntLoss; }
    /** Exact stage override, then native form group override, then the global ratio. */
    public static double instinctPerIntLoss(PlayerFormBase form) {
        if (form == null) return instinctPerIntLoss;
        Double exact = instinctPerIntLossOverrides.get(form.FormID);
        if (exact != null) return exact;
        return form.getGroup() == null ? instinctPerIntLoss
                : instinctPerIntLossOverrides.getOrDefault(form.getGroup().GroupID, instinctPerIntLoss);
    }
    public static double permanentFormIntLoss() { return permanentFormIntLoss; }
    public static int permanentFormIntIntervalTicks() { return permanentFormIntIntervalTicks; }
    public static boolean intHudAutoHide() { return intHudAutoHide; }
    public static boolean quadHudAutoHide() { return quadHudAutoHide; }
    public static Set<Identifier> permanentFormProgressionExceptions() { return permanentFormProgressionExceptions; }
}
