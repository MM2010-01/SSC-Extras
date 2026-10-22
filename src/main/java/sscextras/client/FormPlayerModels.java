package sscextras.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;

/** Client-only factories. Each native PlayerEntityRenderer owns its model instances. */
public final class FormPlayerModels {
    private static final Map<Identifier, Supplier<? extends AuthoredPlayerModel>> FACTORIES = new HashMap<>();
    private static int generation;
    private FormPlayerModels() { }
    public static void register(Identifier form, Supplier<? extends AuthoredPlayerModel> factory) {
        if (FACTORIES.putIfAbsent(form, factory) != null) throw new IllegalArgumentException("Duplicate player rig: " + form);
    }
    public static AuthoredPlayerModel resolve(PlayerEntity player, Map<Identifier, AuthoredPlayerModel> instances) {
        var id = FormAbilityManager.getForm(player).FormID; var factory = FACTORIES.get(id);
        return factory == null ? null : instances.computeIfAbsent(id, key -> factory.get());
    }
    public static int generation() { return generation; }
    public static void reload() { generation++; }
}
