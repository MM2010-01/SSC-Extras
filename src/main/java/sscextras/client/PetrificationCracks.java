package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import sscextras.petrification.Petrification;
import sscextras.player.StoneBodies;

/** Uses Minecraft's block-entity destruction pass on the body's existing posed vertices. */
public final class PetrificationCracks {
    private PetrificationCracks() { }
    public static VertexConsumerProvider buffers(Entity entity, MatrixStack matrices, VertexConsumerProvider original) {
        if (!(entity instanceof LivingEntity living) || !StoneBodies.mineable(living)) return original;
        int stage = Petrification.state(living).crackStage();
        if (stage < 0 || stage > 9) return original;
        var entry = matrices.peek();
        var damage = new OverlayVertexConsumer(MinecraftClient.getInstance().getBufferBuilders().getEffectVertexConsumers()
                .getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry.getPositionMatrix(), entry.getNormalMatrix(), 1);
        return layer -> {
            var base = original.getBuffer(layer);
            return layer.hasCrumbling() ? VertexConsumers.union(damage, base) : base;
        };
    }
}
