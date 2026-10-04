package sscextras.effigy;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class FeralEffigy {
    public static final Identifier ID = new Identifier("ssc-extras", "feral_effigy");
    public static final EffigyBlock BLOCK = new EffigyBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).strength(3.5f).sounds(BlockSoundGroup.STONE).nonOpaque().luminance(state -> 5));
    public static final BlockItem ITEM = new BlockItem(BLOCK, new Item.Settings());
    public static final ScreenHandlerType<EffigyScreenHandler> SCREEN = new ScreenHandlerType<>(EffigyScreenHandler::new, FeatureFlags.VANILLA_FEATURES);

    private FeralEffigy() { }

    public static void register() {
        Registry.register(Registries.BLOCK, ID, BLOCK);
        Registry.register(Registries.ITEM, ID, ITEM);
        Registry.register(Registries.SCREEN_HANDLER, ID, SCREEN);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> Infusions.useTool(player, hand, hit));
    }
}
