package sscextras.drake;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetPotionLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.*;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatus;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatusPotion;
import sscextras.effigy.Infusions;
import sscextras.ConfiguredLootChance;

public final class EarthenDrake {
    public static final PlayerFormBase[] FORMS = new PlayerFormBase[4];
    public static final PlayerFormGroup GROUP;
    public static final BaseTransformativeStatusEffect CURSE;
    public static final TransformativeStatusPotion POTION_EFFECT;
    public static final Potion POTION;

    static {
        var group = new PlayerFormGroup(id("earthen_drake"));
        for (int stage = 0; stage < FORMS.length; stage++) {
            FORMS[stage] = RegPlayerForms.registerPlayerForm(new EarthenDrakeForm(stage));
            group.addForm(FORMS[stage], stage);
        }
        GROUP = RegPlayerForms.registerPlayerFormGroup(group);
        CURSE = RegTStatusEffect.register(id("to_earthen_drake_0_effect"), new TransformativeStatus(FORMS[0]));
        POTION_EFFECT = Registry.register(Registries.STATUS_EFFECT, id("to_earthen_drake_0_potion"),
                new TransformativeStatusPotion(CURSE));
        POTION = Registry.register(Registries.POTION, id("earthen_drake"),
                new Potion("ssc_extras_earthen_drake", new StatusEffectInstance(POTION_EFFECT)));
    }

    private EarthenDrake() { }
    public static Identifier id(String path) { return new Identifier("ssc-extras", path); }

    public static void register() {
        DrakeInstinct.register();
        var body = DrakeBodyPower.factory();
        Registry.register(ApoliRegistries.POWER_FACTORY, body.getSerializerId(), body);
        var bodySlam = DrakeBodySlamPower.factory();
        Registry.register(ApoliRegistries.POWER_FACTORY, bodySlam.getSerializerId(), bodySlam);
        var bareClaws = new ConditionFactory<Entity>(id("bare_claws"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player
                        && player.getInventory().main.get(player.getInventory().selectedSlot).isEmpty()
                        && Infusions.weapon(player).isEmpty());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, bareClaws.getSerializerId(), bareClaws);
        var clawTips = new ConditionFactory<Entity>(id("netherite_claws"), new SerializableData(),
                (data, entity) -> entity instanceof PlayerEntity player
                        && !DrakeEquipment.equipped(player, DrakeEquipment.CLAW_TIPS).isEmpty());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, clawTips.getSerializerId(), clawTips);
        LootTableEvents.MODIFY.register((resources, manager, id, builder, source) -> {
            if (id.equals(new Identifier("minecraft", "chests/pillager_outpost"))) {
                builder.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(() -> new ConfiguredLootChance("drakeCursePotionLootChance"))
                        .with(ItemEntry.builder(Items.POTION).apply(SetPotionLootFunction.builder(POTION))));
            }
        });
    }

    public static ItemStack potion(Item item) { return PotionUtil.setPotion(new ItemStack(item), POTION); }

    public static int stage(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return form.getGroup() == GROUP ? form.getIndex() : -1;
    }

    public static boolean onAllFours(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, DrakeBodyPower.class).stream().anyMatch(DrakeBodyPower::onAllFours);
    }

    public static int eatingDuration(PlayerEntity player, ItemStack stack, int original) {
        if (!stack.isFood()) return original;
        int stage = stage(player);
        return stage < 1 ? original : Math.max(1, (int)Math.ceil(original / (stage == 1 ? 1.5 : 2.0)));
    }

    /** A temporary native tool lets normal mining speed, harvest levels and loot rules apply. */
    public static ItemStack clawTool(PlayerEntity player, BlockState block) {
        int stage = stage(player);
        if (stage < 0 || player.isSpectator()
                || !player.getInventory().main.get(player.getInventory().selectedSlot).isEmpty()) return ItemStack.EMPTY;
        Item tool;
        if (block.isIn(BlockTags.PICKAXE_MINEABLE)) {
            tool = stage == 0 ? Items.WOODEN_PICKAXE : stage == 1 ? Items.STONE_PICKAXE : stage == 2 ? Items.IRON_PICKAXE : Items.DIAMOND_PICKAXE;
        } else if (block.isIn(BlockTags.SHOVEL_MINEABLE)) {
            tool = stage == 0 ? Items.WOODEN_SHOVEL : stage == 1 ? Items.STONE_SHOVEL : stage == 2 ? Items.IRON_SHOVEL : Items.DIAMOND_SHOVEL;
        } else if (block.isIn(BlockTags.AXE_MINEABLE)) {
            tool = stage == 0 ? Items.WOODEN_AXE : stage == 1 ? Items.STONE_AXE : stage == 2 ? Items.IRON_AXE : Items.DIAMOND_AXE;
        } else if (block.isIn(BlockTags.HOE_MINEABLE)) {
            tool = stage == 0 ? Items.WOODEN_HOE : stage == 1 ? Items.STONE_HOE : stage == 2 ? Items.IRON_HOE : Items.DIAMOND_HOE;
        } else if (block.isOf(Blocks.COBWEB) && stage >= 2) {
            tool = stage == 2 ? Items.IRON_SWORD : Items.DIAMOND_SWORD;
        } else return ItemStack.EMPTY;
        if (stage == 3 && !DrakeEquipment.equipped(player, DrakeEquipment.CLAW_TIPS).isEmpty()) {
            Item upgraded = tool == Items.DIAMOND_PICKAXE ? Items.NETHERITE_PICKAXE
                    : tool == Items.DIAMOND_SHOVEL ? Items.NETHERITE_SHOVEL
                    : tool == Items.DIAMOND_AXE ? Items.NETHERITE_AXE
                    : tool == Items.DIAMOND_HOE ? Items.NETHERITE_HOE : Items.NETHERITE_SWORD;
            return DrakeEquipment.claws(player, upgraded);
        }
        var stack = new ItemStack(tool);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        return stack;
    }
}
