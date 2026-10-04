package sscextras;

import net.fabricmc.loader.api.FabricLoader;
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

public final class SscExtrasConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ssc-extras");
    private static final Map<String, Float> LOOT_DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Float> lootChances = new LinkedHashMap<>();
    private static float instinctPerHit = 10.0f;
    private static float instinctPerPotion = 200.0f / 3.0f;

    static {
        LOOT_DEFAULTS.put("cursedCollarLootChance", 1.0f / 15.0f);
        LOOT_DEFAULTS.put("drakeCursePotionLootChance", 0.25f);
        LOOT_DEFAULTS.put("stableReinsLootChance", 1.0f);
        LOOT_DEFAULTS.put("stableSaddleLootChance", 1.0f);
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
        for (var entry : LOOT_DEFAULTS.entrySet()) {
            changed |= config.putIfAbsent(entry.getKey(), Float.toString(entry.getValue())) == null;
            lootChances.put(entry.getKey(), readValue(config, entry.getKey(), entry.getValue(), true));
        }
        instinctPerHit = readValue(config, "instinctPerHit", 10.0f, false);
        instinctPerPotion = readValue(config, "instinctPerPotion", 200.0f / 3.0f, false);
        if (readable && changed) {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    config.store(writer, "Restart to apply. Instinct amounts: greater than 0, at most 100.\n"
                            + "Loot/drop chances: 0 to 1 (0 disables, 1 guarantees; 0.25 = 25%).\n"
                            + "Collars: eligible dungeon chests. Drake Curse potions: pillager outpost chests.\n"
                            + "stable*LootChance: stable chests. stableDrake*DropChance: naturally equipped drake gear.\n"
                            + "Natural gear retains +0.01 per Looting level unless disabled. Player-supplied gear always drops.");
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

    public static float lootChance(String key) {
        Float chance = lootChances.get(key);
        if (chance == null) throw new IllegalArgumentException("Unknown SSC Extras loot chance: " + key);
        return chance;
    }

    public static float instinctPerHit() { return instinctPerHit; }
    public static float instinctPerPotion() { return instinctPerPotion; }
}
