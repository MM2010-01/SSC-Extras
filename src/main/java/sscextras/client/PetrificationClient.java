package sscextras.client;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import sscextras.petrification.Petrification;

public final class PetrificationClient {
    public static final Identifier STONE = Petrification.id("textures/misc/petrified.png");
    public static final Identifier TRANSPARENT = Petrification.id("textures/misc/transparent.png");
    private static final java.util.Map<Identifier, Identifier> TEXTURES = new java.util.HashMap<>();
    private PetrificationClient() { }
    public static void register() {
        PetrificationMiningClient.register();
        var stone = new Identifier("minecraft", "block/stone");
        FluidRenderHandlerRegistry.INSTANCE.register(Petrification.STILL, Petrification.FLOWING, new SimpleFluidRenderHandler(stone, stone));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        // Connector can deliver CCA's initial tracking packet before the entity exists client-side.
        // Request that same component once from the actual entity-load event; no polling or second state owner.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof net.minecraft.entity.LivingEntity)
                    || !net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(sscextras.petrification.PetrificationComponents.SYNC_REQUEST)) return;
            var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); buffer.writeVarInt(entity.getId());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(sscextras.petrification.PetrificationComponents.SYNC_REQUEST, buffer);
        });
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() { return Petrification.id("petrified_textures"); }
                    @Override public void reload(net.minecraft.resource.ResourceManager manager) {
                        clear();
                    }
                });
    }
    private static void clear() {
        var textures = MinecraftClient.getInstance().getTextureManager();
        TEXTURES.values().stream().filter(id -> !id.equals(STONE)).distinct().forEach(textures::destroyTexture);
        TEXTURES.clear();
    }
    public static Identifier texture(Identifier original) { return TEXTURES.computeIfAbsent(original, PetrificationClient::tileStone); }
    private static Identifier tileStone(Identifier original) {
        var client = MinecraftClient.getInstance();
        if (client.getTextureManager().getOrDefault(original, null) instanceof NativeImageBackedTexture dynamic
                && dynamic.getImage() != null) {
            try { return tileStone(dynamic.getImage()); }
            catch (java.io.IOException ignored) { return STONE; }
        }
        var resource = client.getResourceManager().getResource(original);
        if (resource.isEmpty()) return STONE;
        try (var input = resource.get().getInputStream();
             var mask = NativeImage.read(input)) {
            return tileStone(mask);
        } catch (java.io.IOException ignored) { return STONE; }
    }
    private static Identifier tileStone(NativeImage mask) throws java.io.IOException {
        var client = MinecraftClient.getInstance();
        try (var stoneInput = client.getResourceManager().getResourceOrThrow(new Identifier("minecraft", "textures/block/stone.png")).getInputStream();
             var stone = NativeImage.read(stoneInput)) {
            var image = new NativeImage(mask.getWidth(), mask.getHeight(), false);
            for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++)
                image.setColor(x,y,(mask.getColor(x,y)&0xff000000) | (stone.getColor(x%stone.getWidth(),y%stone.getHeight())&0xffffff));
            return client.getTextureManager().registerDynamicTexture("ssc_extras_petrification", new NativeImageBackedTexture(image));
        }
    }
}
