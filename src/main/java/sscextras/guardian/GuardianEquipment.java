package sscextras.guardian;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import sscextras.collar.CuriosCompat;
import sscextras.collar.AccessoryEquipment;
import sscextras.drake.DrakeEquipment;
import sscextras.drake.SoulboundEquipment;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.status_effects.attachment.EffectManager;
import sscextras.CreatureInstinct;
import sscextras.InstinctTarget;
import java.util.List;

public final class GuardianEquipment {
    public static final Piece CUFFS = new Piece(false), COLLAR = new Piece(true);
    public static final int RANGE = 128;
    public static final List<String> LOCATIONS = List.of("wrist", "ankle", "necklace");
    public static final TagKey<EntityType<?>> VILLAGE_FACTION = TagKey.of(RegistryKeys.ENTITY_TYPE, RuneboundGuardian.id("village_faction"));
    private GuardianEquipment() { }
    public static void register() {
        Registry.register(Registries.ITEM, RuneboundGuardian.id("guardian_cuffs"), CUFFS);
        Registry.register(Registries.ITEM, RuneboundGuardian.id("guardian_collar"), COLLAR);
        CuriosCompat.register(CUFFS);
        CuriosCompat.register(COLLAR);
    }
    public static ItemStack equipped(PlayerEntity player, String location) {
        var io = DrakeEquipment.slots();
        if (io == null) return ItemStack.EMPTY;
        String group = group(location), name = slot(location);
        var stacks = io.getEntitySlot(player, group, name);
        if (stacks != null) for (var stack : stacks)
            if (stack.isOf(location.equals("necklace") ? COLLAR : CUFFS)) return stack;
        return ItemStack.EMPTY;
    }
    public static String group(String location) {
        return CuriosCompat.instance != null ? "" : location.equals("wrist") ? "hand" : location.equals("ankle") ? "feet" : "chest";
    }
    public static String slot(String location) {
        return CuriosCompat.instance == null ? location : location.equals("wrist") ? "bracelet" : location.equals("ankle") ? "anklet" : "necklace";
    }
    public static boolean complete(PlayerEntity player) {
        return !equipped(player, "wrist").isEmpty() && !equipped(player, "ankle").isEmpty() && !equipped(player, "necklace").isEmpty();
    }
    public static int visuals(PlayerEntity player) {
        return (equipped(player,"wrist").isEmpty()?0:1) | (equipped(player,"ankle").isEmpty()?0:2) | (equipped(player,"necklace").isEmpty()?0:4);
    }
    public static boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && FormAbilityManager.getForm(player) == RegPlayerForms.ORIGINAL_SHIFTER
                && !TransformManager.getPlayerTransformData(player).isTransforming && visuals(player) == 0;
    }
    public static boolean hasSlots(PlayerEntity player) {
        var io = DrakeEquipment.slots();
        if (io == null) return false;
        for (var location : LOCATIONS) {
            var stacks = io.getEntitySlot(player, group(location), slot(location));
            if (stacks == null || stacks.isEmpty()) return false;
        }
        return true;
    }
    public static boolean fit(ServerPlayerEntity player, BlockPos pedestal) {
        return fit(player, pedestal, false);
    }
    /** Capture admits other living forms too; fitting and native Morphscale exceptions stay shared. */
    static boolean fitCaptured(ServerPlayerEntity player, BlockPos pedestal) {
        return fit(player, pedestal, true);
    }
    private static boolean fit(ServerPlayerEntity player, BlockPos pedestal, boolean captured) {
        if (!(captured ? player.isAlive() && !player.isCreative() && !player.isSpectator() : eligible(player))
                || !hasSlots(player) || !GuardianPedestal.intact(player.getWorld(), pedestal)) return false;
        var data = GuardianWorldState.get(player.getServer());
        if (data.villages.values().stream().anyMatch(v -> v.world.equals(player.getWorld().getRegistryKey()) && v.pedestal.equals(pedestal))) return false;
        if (player.getWorld().getBlockEntity(pedestal) instanceof GuardianOffering offering && offering.hasResident()) return false;
        var assignments = sscextras.drake.DrakeOutpostOwnership.get(player.getServer()).assignments();
        var reserved = assignments.owner(GuardianPedestal.reservation(player.getServerWorld(), pedestal));
        if (reserved != null && (!captured || reserved != assignments.owner(player.getUuid()))) return false;
        var io = DrakeEquipment.slots();
        // Clear all competing slots before onEquip can start any curse or transformation.
        for (var location : LOCATIONS) {
            var stacks = io.getEntitySlot(player, group(location), slot(location));
            for (int i = 0; i < stacks.size(); i++) {
                if (!AccessoryEquipment.clear(player, io, group(location), slot(location), i,
                        stack -> player.dropItem(stack, true, false))) return false;
            }
        }
        for (var slot : EquipmentSlot.values()) if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            var stack = player.getEquippedStack(slot);
            if (net.onixary.shapeShifterCurseFabric.additional_power.IsMorphScaleItemCondition.MSI_condition(null, stack)) continue;
            player.equipStack(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) player.dropItem(stack, true, false);
        }
        for (var location : LOCATIONS) {
            var stack = (location.equals("necklace") ? COLLAR : CUFFS).getDefaultStack();
            SoulboundEquipment.enchant(stack);
            if (AccessoryEquipment.forceEquip(player, group(location), slot(location), stack,
                    worn -> worn.isOf(stack.getItem()), displaced -> player.dropItem(displaced, true, false)) == null) return false;
        }
        var village = new GuardianWorldState.Village();
        village.world = player.getWorld().getRegistryKey(); village.pedestal = pedestal.toImmutable();
        data.villages.put(player.getUuid(), village); data.markDirty();
        applyCurse(player);
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.set_bound"), false);
        return true;
    }
    /** Portable body accessory records use the active backend's actual slot names. */
    static net.minecraft.nbt.NbtList bodySet() {
        var result = new net.minecraft.nbt.NbtList();
        for (var location : LOCATIONS) {
            var entry = new net.minecraft.nbt.NbtCompound();
            entry.putString("Group", group(location)); entry.putString("Slot", slot(location)); entry.putInt("Index", 0);
            entry.put("Stack", (location.equals("necklace") ? COLLAR : CUFFS).getDefaultStack().writeNbt(new net.minecraft.nbt.NbtCompound()));
            result.add(entry);
        }
        return result;
    }
    static int bodyVisuals(net.minecraft.nbt.NbtList accessories) {
        int result = 0;
        for (int i = 0; i < accessories.size(); i++) {
            var entry = accessories.getCompound(i);
            var stack = ItemStack.fromNbt(entry.getCompound("Stack"));
            String slot = entry.getString("Slot");
            if (stack.isOf(COLLAR)) result |= 4;
            else if (stack.isOf(CUFFS)) result |= slot.equals("ankle") || slot.equals("anklet") ? 2 : 1;
        }
        return result;
    }
    private static void applyCurse(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || TransformManager.getPlayerTransformData(player).isTransforming
                || FormAbilityManager.getForm(player) != RegPlayerForms.ORIGINAL_SHIFTER) return;
        var target = CreatureInstinct.getTarget(player);
        if (target != null && target.getGroup() == RuneboundGuardian.GROUP && EffectManager.hasTransformativeEffect(player)) return;
        var instinct = RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
        if (target != null && target.getGroup() != RuneboundGuardian.GROUP) instinct.instinctValue = 0;
        ((InstinctTarget)instinct).sscExtras$setTarget(RuneboundGuardian.FORMS[0].FormID);
        EffectManager.overrideEffect(player, RuneboundGuardian.CURSE);
        RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
    }
    public static float instinctRate(PlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || InstinctTicker.isPausing
                || TransformManager.getPlayerTransformData(player).isTransforming) return 0;
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0 && (FormAbilityManager.getForm(player) != RegPlayerForms.ORIGINAL_SHIFTER
                || CreatureInstinct.getTarget(player) != RuneboundGuardian.FORMS[0]) || stage == 3) return 0;
        return Integer.bitCount(visuals(player)) * .05f;
    }
    public static boolean protects(PlayerEntity player, Entity target) {
        return !equipped(player, "necklace").isEmpty() && (target.getType().isIn(VILLAGE_FACTION)
                || target instanceof PlayerEntity other && RuneboundGuardian.stage(other) >= 0);
    }
    public static boolean confined(PlayerEntity player) {
        return !player.isCreative() && !player.isSpectator() && player.isAlive()
                && (!equipped(player, "wrist").isEmpty() || !equipped(player, "ankle").isEmpty());
    }
    public static boolean outside(ServerPlayerEntity player, net.minecraft.registry.RegistryKey<World> world, Vec3d pos) {
        if (!confined(player)) return false;
        var village = GuardianVillages.claim(player);
        return village != null && (!village.world.equals(world) || pos.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) > RANGE * RANGE);
    }
    public static void tick(ServerPlayerEntity player) {
        if (!confined(player)) { RuneboundGuardian.state(player).cuffPosition = null; return; }
        var village = GuardianVillages.claim(player);
        if (village == null) return;
        var state = RuneboundGuardian.state(player);
        if (!outside(player, player.getWorld().getRegistryKey(), player.getPos())) { state.cuffPosition = player.getPos(); return; }
        var world = player.getServer().getWorld(village.world);
        if (world == null) return;
        var destination = state.cuffPosition;
        if (destination == null || destination.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) > RANGE * RANGE)
            destination = GuardianPedestal.position(village.pedestal);
        world.getChunk(BlockPos.ofFloored(destination));
        player.stopRiding(); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        player.teleport(world, destination.x, destination.y, destination.z, player.getYaw(), player.getPitch());
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.cuffs_boundary", RANGE), true);
    }
    public static void rememberDeath(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity server) GuardianVillages.stopRepair(server);
        var state = RuneboundGuardian.state(player);
        state.soulboundStage = complete(player) || RuneboundGuardian.stage(player) >= 0 ? -1 : -2;
        if (complete(player)) enchantSet(player);
    }
    public static void respawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, boolean alive) {
        var oldState = RuneboundGuardian.state(oldPlayer);
        if (alive || oldState.soulboundStage < -1 && !oldState.coreDeath) return;
        if (oldState.coreDeath) {
            GuardianCores.revert(player);
            var data = GuardianWorldState.get(player.getServer());
            data.villages.remove(player.getUuid()); data.markDirty();
        }
        FormAbilityManager.applyForm(player, RegPlayerForms.ORIGINAL_SHIFTER);
        RuneboundGuardian.state(player).coreDeath = false;
        RuneboundGuardian.state(player).soulboundStage = -2;
        applyCurse(player);
        // The next player tick enforces the boundary after the connection points at the new player.
        RuneboundGuardian.state(player).sync();
    }
    private static void enchantSet(PlayerEntity player) {
        for (var location : LOCATIONS) {
            var stack = equipped(player, location);
            if (!EnchantmentHelper.hasBindingCurse(stack)) stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
            SoulboundEquipment.enchant(stack);
        }
    }
    public static final class Piece extends AccessoryItem {
        private final boolean collar;
        Piece(boolean collar) { super(new Settings().maxCount(1)); this.collar = collar; }
        @Override public ItemStack getDefaultStack() {
            var stack = super.getDefaultStack(); stack.addEnchantment(Enchantments.BINDING_CURSE, 1); return stack;
        }
        @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
            if (!(entity instanceof PlayerEntity) || !CollarSlots.isActive(slot)) return false;
            var name = slot.slot().getPath();
            if (slot.slot().getNamespace().equals("curios"))
                return collar ? name.equals("necklace") : name.equals("bracelet") || name.equals("wrist") || name.equals("anklet");
            return collar ? name.equals("chest/necklace") : name.equals("hand/wrist") || name.equals("feet/ankle");
        }
        @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
            return entity instanceof PlayerEntity player && player.isCreative() || !EnchantmentHelper.hasBindingCurse(stack);
        }
        @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData slot) { accessoryTick(stack, entity, slot); }
        @Override public void accessoryTick(ItemStack stack, LivingEntity entity, SlotData slot) {
            if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player) || !CollarSlots.isActive(slot)) return;
            if (!EnchantmentHelper.hasBindingCurse(stack)) stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
            stack.getOrCreateNbt().putString(Collars.INFUSION, Registries.STATUS_EFFECT.getId(RuneboundGuardian.CURSE).toString());
            if (FormAbilityManager.getForm(player) == RegPlayerForms.ORIGINAL_SHIFTER) applyCurse(player);
            else Collars.applyCurse(player, stack);
            if (complete(player)) enchantSet(player);
        }
        @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
            lines.add(Text.translatable("tooltip.ssc-extras.guardian_equipment"));
            lines.add(Text.translatable(collar ? "tooltip.ssc-extras.guardian_collar" : "tooltip.ssc-extras.guardian_cuffs", RANGE));
        }
    }
}
