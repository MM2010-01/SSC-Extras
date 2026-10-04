package sscextras.drake;

import com.google.gson.Gson;
import net.minecraft.util.math.random.Random;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class DrakeMountNames {
    public static final List<String> NAMES = load();
    private DrakeMountNames() { }

    private static List<String> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(DrakeMountNames.class.getResourceAsStream(
                "/data/ssc-extras/drake_mount_names.json")), StandardCharsets.UTF_8)) {
            return List.of(new Gson().fromJson(reader, String[].class));
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    public static String choose(Random random, String... excluded) {
        var choices = NAMES.stream().filter(name -> !List.of(excluded).contains(name)).toList();
        return choices.get(random.nextInt(choices.size()));
    }
}
