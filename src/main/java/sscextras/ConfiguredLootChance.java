package sscextras;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.JsonSerializer;

public record ConfiguredLootChance(String configKey) implements LootCondition {
    private static final LootConditionType TYPE = new LootConditionType(new JsonSerializer<ConfiguredLootChance>() {
        @Override public void toJson(JsonObject json, ConfiguredLootChance condition, JsonSerializationContext context) {
            json.addProperty("config", condition.configKey());
        }

        @Override public ConfiguredLootChance fromJson(JsonObject json, JsonDeserializationContext context) {
            return new ConfiguredLootChance(JsonHelper.getString(json, "config"));
        }
    });

    public ConfiguredLootChance {
        SscExtrasConfig.lootChance(configKey);
    }

    public static void register() {
        Registry.register(Registries.LOOT_CONDITION_TYPE, new Identifier("ssc-extras", "configured_chance"), TYPE);
    }

    @Override public LootConditionType getType() { return TYPE; }

    @Override public boolean test(LootContext context) {
        return context.getRandom().nextFloat() < SscExtrasConfig.lootChance(configKey);
    }
}
