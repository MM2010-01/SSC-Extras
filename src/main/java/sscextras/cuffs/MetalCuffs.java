package sscextras.cuffs;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import sscextras.CreatureInstinct;
import sscextras.collar.CuriosCompat;

public final class MetalCuffs {
    public static final MetalCuffsItem ITEM = new MetalCuffsItem();
    private static final String WEAR = "SscExtrasCuffWear";
    private MetalCuffs() { }

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("ssc-extras", "metal_cuffs"), ITEM);
        CuriosCompat.register(ITEM);
    }

    public static ItemStack equipped(PlayerEntity player, boolean ankles, boolean visibleOnly) {
        var curios = CuriosCompat.instance;
        var io = curios != null ? curios : AccessoryUtils.nowAccessoryMod;
        if (io == null) return ItemStack.EMPTY;
        String name = curios != null ? ankles ? "anklet" : "bracelet" : ankles ? "ankle" : "wrist";
        var stacks = io.getEntitySlot(player, curios != null ? "" : ankles ? "feet" : "hand", name);
        if (stacks != null) for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isOf(ITEM) && stack.getDamage() < stack.getMaxDamage()
                    && (!visibleOnly || curios == null || curios.visible(player, name, i))) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Called before any gain is capped, so overflow is charged in full. */
    public static float apply(PlayerEntity player, float current, float gain) {
        float value = current + gain;
        if (gain <= 0 || value <= 99 || !(player instanceof ServerPlayerEntity serverPlayer)
                || !player.isAlive() || player.isSpectator()
                || TransformManager.getPlayerTransformData(player).isTransforming) return MathHelper.clamp(value, 0, 100);
        var form = FormAbilityManager.getForm(player);
        if (form.getIndex() < 0 || form.getIndex() > 2
                || form.getIndex() == 2 && CreatureInstinct.permanentTarget(form) == null) return MathHelper.clamp(value, 0, 100);
        ItemStack wrists = equipped(player, false, false), ankles = equipped(player, true, false);
        double remaining = (double) current + gain - 99;
        while (remaining > 0.0000001 && (!wrists.isEmpty() || !ankles.isEmpty())) {
            double share = !wrists.isEmpty() && !ankles.isEmpty() ? 0.5 : 1;
            double step = remaining;
            if (!wrists.isEmpty()) step = Math.min(step, (1 - wear(wrists)) / share);
            if (!ankles.isEmpty()) step = Math.min(step, (1 - wear(ankles)) / share);
            if (!wrists.isEmpty()) damage(serverPlayer, wrists, step * share, false);
            if (!ankles.isEmpty()) damage(serverPlayer, ankles, step * share, true);
            remaining -= step;
        }
        return MathHelper.clamp(99 + (float) remaining, 0, 100);
    }

    private static double wear(ItemStack stack) {
        return stack.hasNbt() ? MathHelper.clamp(stack.getNbt().getDouble(WEAR), 0, 0.9999999) : 0;
    }

    private static void damage(ServerPlayerEntity player, ItemStack stack, double amount, boolean ankles) {
        double pending = wear(stack) + amount;
        if (pending < 0.9999999) {
            stack.getOrCreateNbt().putDouble(WEAR, pending);
            return;
        }
        stack.getOrCreateNbt().remove(WEAR);
        int previous = stack.getMaxDamage() - stack.getDamage();
        stack.damage(1, player, wearer -> wearer.getWorld().playSound(null,
                wearer.getX(), wearer.getY(), wearer.getZ(), SoundEvents.ENTITY_ITEM_BREAK, wearer.getSoundCategory(), 1, 1));
        if (stack.isEmpty()) return;
        int remaining = stack.getMaxDamage() - stack.getDamage();
        Text location = Text.translatable("message.ssc-extras.metal_cuffs." + (ankles ? "ankles" : "wrists"));
        if (previous >= 240 && remaining < 240) {
            player.sendMessage(Text.translatable("message.ssc-extras.metal_cuffs.critical", location).formatted(Formatting.RED), false);
        } else if (previous >= 600 && remaining < 600) {
            player.sendMessage(Text.translatable("message.ssc-extras.metal_cuffs.weak", location).formatted(Formatting.YELLOW), false);
        }
    }
}
