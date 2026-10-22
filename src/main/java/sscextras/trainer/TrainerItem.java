package sscextras.trainer;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CollarSlots;

public final class TrainerItem extends AccessoryItem {
    private final boolean collar;
    public TrainerItem(boolean collar) { super(new Settings().maxCount(1)); this.collar = collar; }
    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (!(entity instanceof PlayerEntity player) || !CollarSlots.isActive(slot)) return false;
        String name = slot.slot().getPath();
        boolean matches = slot.slot().getNamespace().equals("curios")
                ? collar ? name.equals("necklace") : name.equals("hands") || name.equals("feet")
                : collar ? name.equals("chest/necklace") : name.equals("hand/glove") || name.equals("feet/shoes");
        if (!matches) return false;
        var fit = QuadrupedalTrainer.fit(player);
        if (fit == QuadrupedalTrainer.Fit.SUITABLE && !QuadrupedalTrainer.complete(player)) return true;
        QuadrupedalTrainer.reject(player, fit == QuadrupedalTrainer.Fit.SUITABLE ? QuadrupedalTrainer.Fit.FERAL : fit);
        return false;
    }
    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) {
        return entity instanceof PlayerEntity player && (player.isCreative() || QuadrupedalTrainer.complete(player));
    }
    @Override public ItemStack getDefaultStack() {
        var stack = super.getDefaultStack(); QuadrupedalTrainer.binding(stack, true); return stack;
    }
    @Override public void onEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        if (entity instanceof ServerPlayerEntity player && CollarSlots.isActive(slot)) QuadrupedalTrainer.refresh(player);
    }
    @Override public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity player) || slot < 0
                || slot >= player.getInventory().size() || player.getInventory().getStack(slot) != stack) return;
        // One set scan per inventory tick, owned by its necklace rather than both molds.
        if (collar) TrainerLoot.equipCarried(player);
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable(collar ? "tooltip.ssc-extras.trainer.neck" : "tooltip.ssc-extras.trainer.mold").formatted(Formatting.GRAY));
        if (!collar) lines.add(Text.translatable("tooltip.ssc-extras.trainer.mouth").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.trainer.set").formatted(Formatting.DARK_PURPLE));
        lines.add(Text.translatable("tooltip.ssc-extras.trainer.rates").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.trainer.binding").formatted(Formatting.DARK_PURPLE));
        lines.add(Text.translatable("tooltip.ssc-extras.trainer.cure").formatted(Formatting.GRAY));
    }
}
