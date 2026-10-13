package sscextras.player;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record MentalContext(Action action, ItemStack stack, BlockState block, BlockEntity blockEntity,
                            Recipe<?> recipe, Entity entity, Identifier id) {
    public enum Action { USE, PLACE, INTERACT, CRAFT, READ, EQUIP }
    public static MentalContext item(Action action, ItemStack stack) {
        return new MentalContext(action, stack, null, null, null, null, Registries.ITEM.getId(stack.getItem()));
    }
    public static MentalContext block(Action action, World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return new MentalContext(action, ItemStack.EMPTY, state, world.getBlockEntity(pos), null, null, Registries.BLOCK.getId(state.getBlock()));
    }
    public static MentalContext recipe(Recipe<?> recipe, DynamicRegistryManager registries) {
        return new MentalContext(Action.CRAFT, recipe.getOutput(registries), null, null, recipe, null, recipe.getId());
    }
    public static MentalContext entity(Entity entity) {
        return new MentalContext(Action.INTERACT, ItemStack.EMPTY, null, null, null, entity, Registries.ENTITY_TYPE.getId(entity.getType()));
    }
}
