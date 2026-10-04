package sscextras.collar;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional Forge API bridge: SSC's Connector jar does not enable its Curios mixins. */
public final class CuriosCompat implements AccessoryUtils.AccessoryIO {
    public static CuriosCompat instance;
    private final Class<?> itemInterface, contextClass;
    private final Method register, inventory, resolve, getHandler, getStacks, getCount, getStack, setStack, renders;
    private final Method entity, identifier, index;

    private CuriosCompat() throws ReflectiveOperationException {
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        itemInterface = Class.forName("top.theillusivec4.curios.api.type.capability.ICurioItem");
        contextClass = Class.forName("top.theillusivec4.curios.api.SlotContext");
        Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
        Class<?> stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
        Class<?> dynamic = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
        register = api.getMethod("registerCurio", Item.class, itemInterface);
        inventory = api.getMethod("getCuriosInventory", LivingEntity.class);
        resolve = inventory.getReturnType().getMethod("resolve");
        getHandler = handler.getMethod("getStacksHandler", String.class);
        getStacks = stacks.getMethod("getStacks");
        renders = stacks.getMethod("getRenders");
        getCount = dynamic.getMethod("getSlots");
        getStack = dynamic.getMethod("getStackInSlot", int.class);
        setStack = dynamic.getMethod("setStackInSlot", int.class, ItemStack.class);
        entity = contextClass.getMethod("entity");
        identifier = contextClass.getMethod("identifier");
        index = contextClass.getMethod("index");
    }

    public static void register(AccessoryItem... items) {
        if (!FabricLoader.getInstance().isModLoaded("curios")) return;
        try {
            CuriosCompat bridge = instance == null ? new CuriosCompat() : instance;
            for (AccessoryItem item : items) {
                Object adapter = Proxy.newProxyInstance(bridge.itemInterface.getClassLoader(), new Class<?>[]{bridge.itemInterface},
                        (proxy, method, args) -> bridge.dispatch(item, proxy, method, args));
                bridge.register.invoke(null, item, adapter);
            }
            instance = bridge;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register SSC Extras collars with Curios", exception);
        }
        LoggerFactory.getLogger("ssc-extras").info("Registered native Curios collar callbacks and necklace access");
    }

    private Object dispatch(AccessoryItem item, Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> "SSC Extras Curios collar";
            };
        }
        if (args != null && args.length > 0 && contextClass.isInstance(args[0])) {
            LivingEntity wearer = (LivingEntity) call(entity, args[0]);
            var slot = new AccessoryItem.SlotData(new Identifier("curios", (String) call(identifier, args[0])), (int) call(index, args[0]));
            switch (method.getName()) {
                case "curioTick": item.accessoryTick((ItemStack) args[1], wearer, slot); return null;
                case "onEquip": item.onEquip((ItemStack) args[2], wearer, slot); return null;
                case "onUnequip": item.onUnequip((ItemStack) args[2], wearer, slot); return null;
                case "canEquip": return item.canEquip((ItemStack) args[1], wearer, slot);
                case "canUnequip": return item.canUnequip((ItemStack) args[1], wearer, slot);
            }
        }
        return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Curios collar access failed: " + method.getName(), exception);
        }
    }

    private Object handler(LivingEntity entity, String slot) {
        Optional<?> inventory = (Optional<?>) call(resolve, call(this.inventory, null, entity));
        return inventory.isEmpty() ? null : ((Optional<?>) call(getHandler, inventory.get(), slot)).orElse(null);
    }

    public boolean visible(LivingEntity entity, String slot, int index) {
        Object handler = handler(entity, slot);
        if (handler == null) return false;
        var values = (List<?>) call(renders, handler);
        return index < values.size() && Boolean.TRUE.equals(values.get(index));
    }

    @Override public Map<Pair<String, String>, List<ItemStack>> getEntitySlots(LivingEntity entity) {
        return Map.of(new Pair<>("", "necklace"), getEntitySlot(entity, "", "necklace"));
    }

    @Override public List<ItemStack> getEntitySlot(LivingEntity entity, String group, String name) {
        Object handler = handler(entity, name);
        if (handler == null) return List.of();
        Object stacks = call(getStacks, handler);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < (int) call(getCount, stacks); i++) items.add((ItemStack) call(getStack, stacks, i));
        return items;
    }

    @Override public ItemStack getEntitySlot(LivingEntity entity, String group, String name, int index) {
        Object handler = handler(entity, name);
        return handler == null ? ItemStack.EMPTY : (ItemStack) call(getStack, call(getStacks, handler), index);
    }

    @Override public void setEntitySlot(LivingEntity entity, String group, String name, int index, ItemStack stack) {
        Object handler = handler(entity, name);
        if (handler != null) call(setStack, call(getStacks, handler), index, stack);
    }
}
