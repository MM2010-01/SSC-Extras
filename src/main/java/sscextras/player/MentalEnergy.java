package sscextras.player;

import java.lang.reflect.Method;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public final class MentalEnergy {
    private MentalEnergy() { }
    public static void register() {
        try {
            Class<?> provider = Class.forName("net.minecraftforge.common.capabilities.ICapabilityProvider");
            Class<?> capability = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Object energy = Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities").getField("ENERGY").get(null);
            Method get = provider.getMethod("getCapability", capability, net.minecraft.util.math.Direction.class);
            Method present = Class.forName("net.minecraftforge.common.util.LazyOptional").getMethod("isPresent");
            MentalRequirements.registerAdapter(new Identifier("ssc-extras", "forge_energy"), context -> {
                if (has(provider, get, present, energy, context.stack()) || has(provider, get, present, energy, context.blockEntity()))
                    return IntLevel.FUTURISTIC;
                return null;
            });
        } catch (ClassNotFoundException absent) {
            // Forge is optional on the native Fabric runtime.
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Forge Energy API", error);
        }
    }
    private static boolean has(Class<?> provider, Method get, Method present, Object energy, Object owner) {
        if (owner == null || owner instanceof ItemStack stack && stack.isEmpty() || !provider.isInstance(owner)) return false;
        try {
            if ((boolean)present.invoke(get.invoke(owner, energy, null))) return true;
            if (!(owner instanceof ItemStack)) for (var direction : net.minecraft.util.math.Direction.values())
                if ((boolean)present.invoke(get.invoke(owner, energy, direction))) return true;
            return false;
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot inspect Forge Energy capability", error); }
    }
}
