package sscextras;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import sscextras.drake.EarthenDrake;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;

/** Opt-in, isolated render probe; never included in the distributable mod. */
public final class DrakeClientSmokeServer implements ModInitializer {
    @Override public void onInitialize() {
        if (!Boolean.getBoolean("ssc-extras.drake-smoke-server")) return;
        if (Boolean.getBoolean("ssc-extras.sentient-preview")) { SentientPreviewServer.register(); return; }
        ServerPlayNetworking.registerGlobalReceiver(EarthenDrake.id("preview_scene"), (server, player, handler, buf, sender) -> {
            int scene = buf.readInt();
            server.execute(() -> {
                boolean references = Boolean.getBoolean("ssc-extras.drake-references");
                int stage = switch (scene) { case 0 -> 0; case 1 -> 1; case 2,3,9,14,15,17,18,19,20,21,22,23,24 -> 2; default -> 3; };
                player.getInventory().clear();
                MetalCuffsChecks.equip(player, false, ItemStack.EMPTY);
                MetalCuffsChecks.equip(player, true, ItemStack.EMPTY);
                player.getHungerManager().setFoodLevel(!references && (scene == 3 || scene == 15) ? 6 : 20);
                player.getHungerManager().setSaturationLevel(0);
                var world = player.getServerWorld();
                for (int x=-5; x<=5; x++) for (int z=-5; z<=5; z++) world.setBlockState(new BlockPos(x,89,z), Blocks.STONE.getDefaultState());
                world.setTimeOfDay(6000);
                player.teleport(world, .5, 90, .5, 0, 0);
                var form = references ? switch (scene) {
                    case 0 -> RegPlayerForms.ANUBIS_WOLF_0;
                    case 1 -> RegPlayerForms.ANUBIS_WOLF_1;
                    case 2 -> RegPlayerForms.ANUBIS_WOLF_2;
                    case 3 -> RegPlayerForms.BAT_2;
                    case 4 -> RegPlayerForms.AXOLOTL_2;
                    default -> RegPlayerForms.AXOLOTL_3;
                } : EarthenDrake.FORMS[stage];
                FormAbilityManager.applyForm(player, form);
                var skin = net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
                skin.setEnableFormColor(scene == 15 || scene == 16);
                skin.setFormColor("416FAD", "EBC47F", "713F84", "44EEAA", "EE5588", false, false, false);
                net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent.SKIN_SETTINGS.sync(player);
                player.getInventory().clear();
                if (!references && (scene == 5 || scene == 9)) player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.APPLE));
                for (var slot : CollarSlots.get(player)) slot.set(player, scene == 10 ? new ItemStack(Collars.FERALIZING) : ItemStack.EMPTY);
            });
        });
    }
}
