package sscextras.inventory;

import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Optional;

/** Optional Forge 1.20.1 IItemHandler bridge; never loads Forge classes on Fabric. */
final class ForgeItemHandlers {
    private record Api(Class<?> provider, Object capability, Method get, Method resolve,
                       Method size, Method stack, Method extract, Method insert, Class<?> modifiable, Method set) { }
    private static final Api API = load();

    private static Api load() {
        try {
            var provider = Class.forName("net.minecraftforge.common.capabilities.ICapabilityProvider");
            var capability = Class.forName("net.minecraftforge.common.capabilities.Capability");
            var handler = Class.forName("net.minecraftforge.items.IItemHandler");
            var modifiable = Class.forName("net.minecraftforge.items.IItemHandlerModifiable");
            return new Api(provider,
                    Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities").getField("ITEM_HANDLER").get(null),
                    provider.getMethod("getCapability", capability),
                    Class.forName("net.minecraftforge.common.util.LazyOptional").getMethod("resolve"),
                    handler.getMethod("getSlots"), handler.getMethod("getStackInSlot", int.class),
                    handler.getMethod("extractItem", int.class, int.class, boolean.class),
                    handler.getMethod("insertItem", int.class, ItemStack.class, boolean.class), modifiable,
                    modifiable.getMethod("setStackInSlot", int.class, ItemStack.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Forge item-handler API", error);
        }
    }

    static Object get(Object owner) {
        if (API == null || !API.provider.isInstance(owner)) return null;
        return ((Optional<?>)invoke(API.resolve, invoke(API.get, owner, API.capability))).orElse(null);
    }
    static int size(Object handler) { return (int)invoke(API.size, handler); }
    static ItemStack stack(Object handler, int slot) { return (ItemStack)invoke(API.stack, handler, slot); }
    static ItemStack extract(Object handler, int slot, int amount) {
        var extracted = (ItemStack)invoke(API.extract, handler, slot, amount, false);
        if (!extracted.isEmpty() || !API.modifiable.isInstance(handler)) return extracted;
        var previous = stack(handler, slot).copy();
        invoke(API.set, handler, slot, ItemStack.EMPTY);
        return stack(handler, slot).isEmpty() ? previous : ItemStack.EMPTY;
    }
    static ItemStack insert(Object handler, int slot, ItemStack stack) {
        var remainder = (ItemStack)invoke(API.insert, handler, slot, stack, false);
        if (!remainder.isEmpty() && API.modifiable.isInstance(handler) && stack(handler, slot).isEmpty()) {
            invoke(API.set, handler, slot, remainder);
            if (ItemStack.areEqual(stack(handler, slot), remainder)) return ItemStack.EMPTY;
        }
        return remainder;
    }
    private static Object invoke(Method method, Object owner, Object... arguments) {
        try { return method.invoke(owner, arguments); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot access Forge item inventory", error); }
    }
}
