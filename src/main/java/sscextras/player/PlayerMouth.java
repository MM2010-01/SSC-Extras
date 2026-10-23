package sscextras.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.drake.*;
import sscextras.trainer.QuadrupedalTrainer;

/** Physical item handling, independent of intelligence and natural form abilities. */
public final class PlayerMouth {
    public static final TagKey<Item> RANGED_WEAPONS = TagKey.of(RegistryKeys.ITEM, new Identifier("ssc-extras", "mouth_ranged_weapons"));
    private PlayerMouth() { }

    public static boolean active(PlayerEntity player) {
        return withoutTrainer(player) || !player.isSpectator() && QuadrupedalTrainer.mouth(player);
    }

    /** Existing form/feral/shoe mouth handling, before the trainer adds its own attachment. */
    public static boolean withoutTrainer(PlayerEntity player) {
        return !player.isSpectator() && (FormAbilityManager.getForm(player).getBodyType() == PlayerFormBodyType.FERAL
                || DrakeFeralization.permanent(player)
                || DrakeFeralization.temporary(player) && EarthenDrake.stage(player) >= 0
                || DrakeShoes.hands(player));
    }

    public static boolean ranged(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof RangedWeaponItem || item instanceof TridentItem || stack.isIn(RANGED_WEAPONS)
                || stack.getUseAction() == UseAction.BOW || stack.getUseAction() == UseAction.CROSSBOW
                || stack.getUseAction() == UseAction.SPEAR;
    }

    public static boolean preventsUse(PlayerEntity player, ItemStack stack) {
        return !stack.isEmpty() && active(player) && ranged(stack);
    }
}
