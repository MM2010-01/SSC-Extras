/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1792
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRenderer;
import net.minecraft.class_1792;
import org.jetbrains.annotations.Nullable;

public class AzArmorRendererRegistry {
    private static final Map<class_1792, AzArmorRenderer> ITEM_TO_RENDERER = new HashMap<class_1792, AzArmorRenderer>();
    private static final Map<class_1792, Supplier<AzArmorRenderer>> ITEM_TO_RENDERER_SUPPLIER = new HashMap<class_1792, Supplier<AzArmorRenderer>>();

    public static void register(class_1792 item, Supplier<AzArmorRenderer> armorRendererSupplier) {
        ITEM_TO_RENDERER_SUPPLIER.put(item, armorRendererSupplier);
    }

    public static void register(Supplier<AzArmorRenderer> armorRendererSupplier, class_1792 item, class_1792 ... items) {
        AzArmorRendererRegistry.register(item, armorRendererSupplier);
        for (class_1792 otherItem : items) {
            AzArmorRendererRegistry.register(otherItem, armorRendererSupplier);
        }
    }

    @Nullable
    public static AzArmorRenderer getOrNull(class_1792 item) {
        return ITEM_TO_RENDERER.computeIfAbsent(item, $ -> {
            Supplier<AzArmorRenderer> rendererSupplier = ITEM_TO_RENDERER_SUPPLIER.get(item);
            return rendererSupplier == null ? null : rendererSupplier.get();
        });
    }
}

