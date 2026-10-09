package sscextras.guardian;

import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import sscextras.CreatureInstinct;

public final class GuardianCombat {
    public static final TagKey<DamageType> MAGIC = TagKey.of(RegistryKeys.DAMAGE_TYPE, RuneboundGuardian.id("guardian_magic"));
    private static final TagKey<Item> NORMAL_TOOLS = TagKey.of(RegistryKeys.ITEM, RuneboundGuardian.id("guardian_normal_tools"));
    private GuardianCombat() { }

    public static boolean tool(ItemStack stack) {
        return stack.isIn(NORMAL_TOOLS) || stack.getItem() instanceof ToolItem || stack.getItem() instanceof RangedWeaponItem
                || stack.getItem() instanceof TridentItem || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof ShearsItem
                || stack.getItem() instanceof FlintAndSteelItem;
    }
    public static boolean blockedTool(PlayerEntity player, ItemStack stack) {
        return RuneboundGuardian.stage(player) >= 2 && tool(stack) && !stack.isIn(RuneboundGuardian.TOOLS);
    }
    public static double toolEfficiency(PlayerEntity player, ItemStack stack) {
        int stage = RuneboundGuardian.stage(player);
        if (stack.isIn(RuneboundGuardian.TOOLS)) return stage >= 1 ? 1 : .5;
        return stage == 1 && tool(stack) ? .5 : 1;
    }
    public static boolean locked(PlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        return state.detached || state.ritualTicks > 0;
    }
    public static boolean canAttack(PlayerEntity player, Entity target) {
        return !locked(player) && !blockedTool(player, player.getMainHandStack())
                && !GuardianEquipment.protects(player, target)
                && !(target instanceof VillagerEntity && RuneboundGuardian.stage(player) >= 1);
    }
    public static boolean canUse(PlayerEntity player, ItemStack stack) {
        return !locked(player) && !blockedTool(player, stack) && !(RuneboundGuardian.construct(player) && stack.isFood());
    }
    public static boolean stone(PlayerEntity player, ItemStack stack) {
        return RuneboundGuardian.construct(player) && stack.isIn(RuneboundGuardian.STONES);
    }

    public static ItemStack clawTool(PlayerEntity player, BlockState block) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 1 || player.isSpectator() || !player.getInventory().getMainHandStack().isEmpty()) return ItemStack.EMPTY;
        Item tool;
        if (block.isIn(BlockTags.PICKAXE_MINEABLE)) tool = stage == 1 ? Items.STONE_PICKAXE : stage == 2 ? Items.IRON_PICKAXE : Items.DIAMOND_PICKAXE;
        else if (block.isIn(BlockTags.AXE_MINEABLE)) tool = stage == 1 ? Items.STONE_AXE : stage == 2 ? Items.IRON_AXE : Items.DIAMOND_AXE;
        else if (block.isIn(BlockTags.SHOVEL_MINEABLE)) tool = stage == 1 ? Items.STONE_SHOVEL : stage == 2 ? Items.IRON_SHOVEL : Items.DIAMOND_SHOVEL;
        else if (block.isIn(BlockTags.HOE_MINEABLE)) tool = stage == 1 ? Items.STONE_HOE : stage == 2 ? Items.IRON_HOE : Items.DIAMOND_HOE;
        else if (block.isOf(Blocks.COBWEB)) tool = stage == 1 ? Items.STONE_SWORD : stage == 2 ? Items.IRON_SWORD : Items.DIAMOND_SWORD;
        else return ItemStack.EMPTY;
        ItemStack result = new ItemStack(tool); result.getOrCreateNbt().putBoolean("Unbreakable", true); return result;
    }

    public static boolean immune(PlayerEntity player, DamageSource source) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0 || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (RuneboundGuardian.state(player).detached) return true;
        if (stage >= 1 && (source.isOf(DamageTypes.HOT_FLOOR) || source.isOf(DamageTypes.IN_FIRE)
                && (player.getWorld().getBlockState(player.getBlockPos()).getBlock() instanceof CampfireBlock
                || player.getWorld().getBlockState(player.getBlockPos().down()).getBlock() instanceof CampfireBlock))) return true;
        if (stage >= 2 && source.isOf(DamageTypes.LAVA) && !player.isSubmergedIn(FluidTags.LAVA)) {
            player.extinguish(); return true;
        }
        return stage == 3 && source.isIn(DamageTypeTags.IS_FIRE);
    }

    public static float incoming(PlayerEntity player, DamageSource source, float amount) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0 || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        if (source.isIn(MAGIC)) {
            float absorbed = amount * new float[]{.1f, .6f, .8f, 1}[stage];
            amount -= absorbed;
            if (stage >= 1 && !player.getWorld().isClient) {
                player.heal(absorbed);
                if (stage == 1) {
                    var state = RuneboundGuardian.state(player);
                    state.foodRemainder += absorbed;
                    int food = (int)state.foodRemainder; state.foodRemainder -= food;
                    player.getHungerManager().add(food, .25f);
                } else RuneboundGuardian.state(player).charge(absorbed * 2);
                magicInstinct(player);
            }
        }
        if (stage >= 2 && source.isIn(DamageTypeTags.IS_PROJECTILE)) amount *= stage == 2 ? .2f : .05f;
        return amount * new float[]{.9f, .75f, .4f, .2f}[stage];
    }
    public static void magicInstinct(PlayerEntity player) {
        if (player.getWorld().isClient || InstinctTicker.isPausing || RuneboundGuardian.stage(player) < 1
                || RuneboundGuardian.stage(player) > 2 || player.isCreative() || player.isSpectator()) return;
        var state = RuneboundGuardian.state(player);
        long now = player.getWorld().getTime();
        if (state.magicInstinctAt != Long.MIN_VALUE && now - state.magicInstinctAt < 20) return;
        state.magicInstinctAt = now;
        InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_GUARDIAN_MAGIC", 1);
    }
    public static void potion(PlayerEntity player, ItemStack stack) {
        int stage = RuneboundGuardian.stage(player);
        if (player.getWorld().isClient || stage < 1 || PotionUtil.getPotionEffects(stack).isEmpty()) return;
        player.heal(stage == 3 ? 4 : 2);
        if (stage == 1) player.getHungerManager().add(3, .5f);
        else RuneboundGuardian.state(player).charge(stage == 3 ? 20 : 10);
        magicInstinct(player);
    }
    public static StatusEffectInstance shorten(PlayerEntity player, StatusEffectInstance effect) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 1 || !effect.getEffectType().isBeneficial() && effect.getEffectType() instanceof
                net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect) return effect;
        if (effect.getEffectType().isBeneficial() || effect.getEffectType().isInstant()) return effect;
        // Infinite hostile effects become finite so that shortened debuffs cannot remain permanent.
        int original = effect.isInfinite() ? 1200 : effect.getDuration();
        return new StatusEffectInstance(effect.getEffectType(), Math.max(1, (int)Math.ceil(original * (stage == 1 ? .5 : .2))),
                effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
    }
    public static boolean absorbEffect(PlayerEntity player, StatusEffectInstance effect) {
        if (RuneboundGuardian.stage(player) != 3 || effect.getEffectType().isBeneficial()
                || effect.getEffectType() instanceof net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect) return false;
        if (!player.getWorld().isClient) {
            long now = player.getWorld().getTime();
            var state = RuneboundGuardian.state(player);
            if (state.magicInstinctAt == Long.MIN_VALUE || now - state.magicInstinctAt >= 20) {
                state.charge(2); player.heal(1); state.magicInstinctAt = now;
            }
        }
        return true;
    }
    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> canAttack(player, target) ? ActionResult.PASS : ActionResult.FAIL);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> locked(player) || blockedTool(player, player.getStackInHand(hand)) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> canUse(player, player.getStackInHand(hand))
                ? TypedActionResult.pass(player.getStackInHand(hand)) : TypedActionResult.fail(player.getStackInHand(hand)));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> canUse(player, player.getStackInHand(hand)) ? ActionResult.PASS : ActionResult.FAIL);
        UseEntityCallback.EVENT.register((player, world, hand, target, hit) -> canUse(player, player.getStackInHand(hand)) ? ActionResult.PASS : ActionResult.FAIL);
    }
}
