package sscextras.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import sscextras.guardian.*;
import java.util.*;

public final class GuardianClient {
    private record Threat(UUID villager, UUID attacker, long until) { }
    private static final List<Threat> THREATS = new ArrayList<>();
    private static Entity owner;
    private static boolean detached, freeLook;
    private static Perspective previous;
    public static float yaw, pitch;
    private GuardianClient() { }
    public static void register() {
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(GuardianOffering.TYPE, GuardianOfferingRenderer::new);
        for (var item : new net.minecraft.item.Item[]{GuardianEquipment.CUFFS, GuardianEquipment.COLLAR})
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item, (stack, mode, matrices, buffers, light, overlay) -> {
                matrices.push(); matrices.translate(.5, .35, .5);
                matrices.scale(1.7f, 1.7f, 1.7f);
                if (stack.isOf(GuardianEquipment.COLLAR)) GuardianAdornmentMesh.render("display/collar", matrices, buffers, light);
                else {
                    matrices.translate(-.19,0,-.14); GuardianAdornmentMesh.render("display/wrist", matrices, buffers, light);
                    matrices.translate(.38,0,.28); GuardianAdornmentMesh.render("display/wrist", matrices, buffers, light);
                }
                matrices.pop();
            });
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack,tint)->0x35e8ee,GuardianCores.CORE);
        if(!GuardianForgeHud.register()) HudRenderCallback.EVENT.register((context,delta)->{
            var client=MinecraftClient.getInstance();
            if(client.player!=null&&!client.options.hudHidden&&!client.player.isCreative()&&!client.player.isSpectator())
                energyBar(context,client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight());
        });
        EntityRendererRegistry.register(GuardianCores.SHELL, GuardianShellRenderer::new);
        EntityRendererRegistry.register(GuardianCores.ANCHOR, context -> new EntityRenderer<GuardianAnchorEntity>(context) {
            @Override public Identifier getTexture(GuardianAnchorEntity entity) { return GuardianMesh.permanentTexture(false, true, false); }
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return RuneboundGuardian.id("guardian_meshes"); }
            @Override public void reload(ResourceManager manager) { GuardianMesh.reload(); GuardianAdornmentMesh.reload(); }
        });
        ClientPlayNetworking.registerGlobalReceiver(GuardianVillages.VISION, (client, handler, buffer, sender) -> {
            int count = buffer.readVarInt(); var entries = new ArrayList<Threat>();
            for (int i = 0; i < count; i++) entries.add(new Threat(buffer.readUuid(), buffer.readUuid(), buffer.readVarInt()));
            client.execute(() -> {
                THREATS.clear(); if (client.world == null) return;
                for (var t : entries) THREATS.add(new Threat(t.villager, t.attacker, client.world.getTime() + t.until));
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            updateCamera(client);
            var player = client.player;
            if (player == null) return;
            var state = RuneboundGuardian.state(player);
            boolean giveUp = player.isAlive() && state.detached && state.canGiveUp && state.ritualTicks == 0;
            if (giveUp && client.currentScreen == null) client.setScreen(new GuardianCoreScreen());
            else if (!giveUp && client.currentScreen instanceof GuardianCoreScreen) client.setScreen(null);
            if (state.deactivated) {
                player.setYaw(state.frozenYaw); player.setHeadYaw(state.frozenYaw); player.setPitch(state.frozenPitch);
                player.bodyYaw = state.frozenYaw;
            }
            if (detached) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            restore(client); THREATS.clear(); owner = null; FormTailPhysics.clear();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity player && RuneboundGuardian.stage(player) >= 0)
                FormTailPhysics.reset(player);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.consumers() == null) return;
            for (var player : context.world().getPlayers()) {
                var state = RuneboundGuardian.state(player); int ticks = state.ritualTicks;
                if (ticks < 2 || ticks > GuardianRitual.SCULPTED || player.squaredDistanceTo(context.camera().getPos()) > 4096) continue;
                var matrices = context.matrixStack(); var position = player.getLerpedPos(context.tickDelta()).subtract(context.camera().getPos());
                if (!state.detached) {
                    matrices.push(); matrices.translate(position.x, position.y, position.z);
                    var lines = context.consumers().getBuffer(RenderLayer.getLines());
                    for (int side : new int[]{-1, 1}) {
                        var matrix = matrices.peek().getPositionMatrix(); var normal = matrices.peek().getNormalMatrix();
                        lines.vertex(matrix, side * .5f, 1.25f, 0).color(.3f,.32f,.34f,1).normal(normal,side, -1,0).next();
                        lines.vertex(matrix, side * .9f, -.05f, 0).color(.3f,.32f,.34f,1).normal(normal,side, -1,0).next();
                    }
                    matrices.pop();
                }
                if (ticks >= GuardianRitual.SOUL_START && ticks < GuardianRitual.SOUL_SEALED) {
                    float t = (ticks + context.tickDelta() - GuardianRitual.SOUL_START) / (GuardianRitual.SOUL_SEALED - GuardianRitual.SOUL_START);
                    float scale = 1 - .9f*t;
                    matrices.push(); matrices.translate(position.x, position.y + 1.2*t, position.z - 2*t);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-player.bodyYaw));
                    matrices.scale(-.9375f*scale,-.9375f*scale,.9375f*scale); matrices.translate(0,-1.501,0);
                    GuardianMesh.get(2).render(matrices,context.consumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,null,true,false,.55f,null);
                    matrices.pop();
                }
            }
        });
    }
    private static void restore(MinecraftClient client) {
        if (previous != null) client.options.setPerspective(previous);
        previous = null; detached = freeLook = false;
    }
    private static void updateCamera(MinecraftClient client) {
        var player = client.player;
        if (owner != player) { restore(client); owner = player; }
        if (player == null) return;
        var state = RuneboundGuardian.state(player);
        boolean nextDetached = state.detached && state.ritualTicks <= 1;
        boolean nextFreeLook = state.ritualTicks <= 1 && (nextDetached || state.deactivated);
        if (nextFreeLook && !freeLook) { yaw = player.getYaw(); pitch = player.getPitch(); }
        if (nextDetached && !detached) previous = client.options.getPerspective();
        if (!nextDetached && detached) {
            if (previous != null) client.options.setPerspective(previous);
            previous = null;
        }
        detached = nextDetached; freeLook = nextFreeLook;
    }
    public static boolean camera() {
        updateCamera(MinecraftClient.getInstance());
        return freeLook;
    }
    public static boolean detachedCamera() { return detached; }
    public static boolean look(Entity entity, double dx, double dy) {
        if (entity != MinecraftClient.getInstance().player) return false;
        if (!camera()) return false;
        yaw += dx*.15f; pitch = MathHelper.clamp(pitch+(float)dy*.15f,-90,90); return true;
    }
    public static int outline(Entity entity) {
        var client = MinecraftClient.getInstance(); var player = client.player;
        if (player == null || entity.getWorld() != client.world || RuneboundGuardian.stage(player) < 2 || !RuneboundGuardian.state(player).vision) return -1;
        long now = client.world.getTime();
        for (var threat : THREATS) if (threat.until >= now) {
            if (threat.villager.equals(entity.getUuid())) return 0xffd940;
            if (RuneboundGuardian.stage(player) == 3 && threat.attacker.equals(entity.getUuid())) return 0xff3434;
        }
        if (RuneboundGuardian.stage(player) == 3 && (entity.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.RAIDERS)
                || entity instanceof net.minecraft.entity.mob.IllagerEntity)) return 0xff3434;
        return entity instanceof VillagerEntity ? 0x35edee : -1;
    }
    public static boolean energy() {
        var player = MinecraftClient.getInstance().player;
        return player != null && RuneboundGuardian.stage(player) >= 2 && !RuneboundGuardian.state(player).detached;
    }
    public static void energyBar(DrawContext context, int width, int height) {
        if (!energy()) return;
        var state = RuneboundGuardian.state(MinecraftClient.getInstance().player);
        int x=width/2+10, y=height-38, fill=(int)Math.round(78*state.fraction());
        context.fill(x-1,y-1,x+80,y+8,0xff374852); context.fill(x,y,x+79,y+7,0xff081b22);
        if (fill>0) { context.fill(x+1,y+1,x+1+fill,y+6,state.overdrive?0xff72ffff:0xff10bfcc); context.fill(x+1,y+1,x+1+fill,y+2,0xffbcffff); }
        for (int i=1;i<10;i++) context.fill(x+i*8,y+1,x+i*8+1,y+6,0x80334950);
    }
}
