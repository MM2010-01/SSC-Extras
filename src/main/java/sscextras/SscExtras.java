package sscextras;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SscExtras implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ssc-extras");
    private static float instinctPerHit = 10.0f;
    private static float instinctPerPotion = 200.0f / 3.0f;

    @Override
    public void onInitialize() {
        sscextras.drake.EarthenDrake.register();
        sscextras.effigy.FeralEffigy.register();
        sscextras.collar.Collars.register();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("ssc-extras.properties");
        Properties config = new Properties();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    config.load(reader);
                }
            } else {
                config.setProperty("instinctPerHit", "10.0");
                config.setProperty("instinctPerPotion", Float.toString(instinctPerPotion));
                try (Writer writer = Files.newBufferedWriter(file)) {
                    config.store(writer, "Instinct per hit or curse potion application: greater than 0, at most 100. Restart to apply.");
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("Could not load SSC Extras config; using default instinct amounts", exception);
        }
        instinctPerHit = readAmount(config, "instinctPerHit", 10.0f);
        instinctPerPotion = readAmount(config, "instinctPerPotion", 200.0f / 3.0f);
    }

    private static float readAmount(Properties config, String key, float fallback) {
        try {
            float value = Float.parseFloat(config.getProperty(key, Float.toString(fallback)));
            if (Float.isFinite(value) && value > 0 && value <= 100) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        LOGGER.warn("Invalid {}: expected a number greater than 0 and at most 100; using {}", key, fallback);
        return fallback;
    }

    public static float instinctPerHit() {
        return instinctPerHit;
    }

    public static float instinctPerPotion() {
        return instinctPerPotion;
    }
}
