package sscextras.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import sscextras.drake.DrakeLeashing;

public final class DrakeLeashRenderer {
    private DrakeLeashRenderer() { }
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.consumers() == null) return;
            for (var player : context.world().getPlayers()) {
                var holder = DrakeLeashing.holder(player);
                if (holder == null) continue;
                float tick = context.tickDelta();
                var start = player.getLerpedPos(tick).add(0, player.getHeight() * .75, 0);
                var delta = holder.getLeashPos(tick).subtract(start);
                var matrices = context.matrixStack();
                matrices.push();
                var relative = start.subtract(context.camera().getPos());
                matrices.translate(relative.x, relative.y, relative.z);
                var buffer = context.consumers().getBuffer(RenderLayer.getLeash());
                float x = (float)delta.x, y = (float)delta.y, z = (float)delta.z;
                float width = .0125f / Math.max(.001f, MathHelper.sqrt(x * x + z * z));
                var from = BlockPos.ofFloored(start);
                var to = BlockPos.ofFloored(holder.getLeashPos(tick));
                int blockFrom = context.world().getLightLevel(LightType.BLOCK, from);
                int blockTo = context.world().getLightLevel(LightType.BLOCK, to);
                int skyFrom = context.world().getLightLevel(LightType.SKY, from);
                int skyTo = context.world().getLightLevel(LightType.SKY, to);
                for (int i = 0; i <= 24; i++) segment(buffer, matrices.peek().getPositionMatrix(), x, y, z, z * width, x * width,
                        i, false, blockFrom, blockTo, skyFrom, skyTo);
                for (int i = 24; i >= 0; i--) segment(buffer, matrices.peek().getPositionMatrix(), x, y, z, z * width, x * width,
                        i, true, blockFrom, blockTo, skyFrom, skyTo);
                matrices.pop();
            }
        });
    }

    private static void segment(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float sideX, float sideZ,
            int i, boolean reverse, int blockFrom, int blockTo, int skyFrom, int skyTo) {
        float t = i / 24f;
        int light = LightmapTextureManager.pack((int)MathHelper.lerp(t, blockFrom, blockTo), (int)MathHelper.lerp(t, skyFrom, skyTo));
        float shade = i % 2 == (reverse ? 1 : 0) ? .7f : 1;
        float height = y > 0 ? y * t * t : y - y * (1 - t) * (1 - t);
        float offset = reverse ? 0 : .025f;
        buffer.vertex(matrix, x * t - sideX, height + offset, z * t + sideZ).color(.5f * shade, .4f * shade, .3f * shade, 1).light(light).next();
        buffer.vertex(matrix, x * t + sideX, height + .025f - offset, z * t - sideZ).color(.5f * shade, .4f * shade, .3f * shade, 1).light(light).next();
    }
}
