package sscextras.effigy;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public enum InfusionSlot {
    HEAD(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 22, 32),
    CHEST(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 22, 50),
    LEGS(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 22, 68),
    FEET(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 22, 86),
    WEAPON(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 91, 32),
    PICKAXE(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 73, 59),
    AXE(EquipmentSlot.MAINHAND, Items.IRON_AXE, 109, 59),
    SHOVEL(EquipmentSlot.MAINHAND, Items.IRON_SHOVEL, 73, 86),
    HOE(EquipmentSlot.MAINHAND, Items.IRON_HOE, 109, 86);

    public final EquipmentSlot equipment;
    public final Item example;
    public final int x, y;

    InfusionSlot(EquipmentSlot equipment, Item example, int x, int y) {
        this.equipment = equipment;
        this.example = example;
        this.x = x;
        this.y = y;
    }

    public boolean armor() { return equipment.getType() == EquipmentSlot.Type.ARMOR; }
    public String translation() { return "slot.ssc-extras." + name().toLowerCase(java.util.Locale.ROOT); }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (armor()) return item instanceof ArmorItem armor && armor.getSlotType() == equipment;
        if (item instanceof RangedWeaponItem || item instanceof TridentItem) return false;
        return switch (this) {
            case WEAPON -> item instanceof SwordItem || item instanceof AxeItem || tagged(stack, "origins", "melee_weapons");
            case PICKAXE -> item instanceof MiningToolItem && !(item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem)
                    || tagged(stack, "minecraft", "pickaxes") || tagged(stack, "forge", "tools/pickaxes");
            case AXE -> item instanceof AxeItem || tagged(stack, "minecraft", "axes") || tagged(stack, "forge", "tools/axes");
            case SHOVEL -> item instanceof ShovelItem || tagged(stack, "minecraft", "shovels") || tagged(stack, "forge", "tools/shovels");
            case HOE -> item instanceof HoeItem || tagged(stack, "minecraft", "hoes") || tagged(stack, "forge", "tools/hoes");
            default -> false;
        };
    }

    private static boolean tagged(ItemStack stack, String namespace, String path) {
        return stack.isIn(TagKey.of(RegistryKeys.ITEM, new Identifier(namespace, path)));
    }
}
