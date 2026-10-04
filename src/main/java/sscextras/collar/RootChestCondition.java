package sscextras.collar;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.util.JsonSerializer;
import sscextras.mixin.LootContextAccessor;

public final class RootChestCondition implements LootCondition {
    public static final RootChestCondition INSTANCE = new RootChestCondition();
    public static final LootConditionType TYPE = new LootConditionType(new JsonSerializer<RootChestCondition>() {
        @Override public void toJson(JsonObject json, RootChestCondition condition, JsonSerializationContext context) { }
        @Override public RootChestCondition fromJson(JsonObject json, JsonDeserializationContext context) { return INSTANCE; }
    });

    private RootChestCondition() { }

    @Override public LootConditionType getType() { return TYPE; }

    @Override public boolean test(LootContext context) {
        int tables = 0;
        for (var entry : ((LootContextAccessor) context).sscExtras$activeEntries()) {
            if (entry.type() == LootDataType.LOOT_TABLES && ++tables > 1) return false;
        }
        return true;
    }
}
