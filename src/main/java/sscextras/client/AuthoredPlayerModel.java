package sscextras.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Actual player-model lifecycle; never an additional body feature or invisibility layer. */
public abstract class AuthoredPlayerModel extends PlayerEntityModel<AbstractClientPlayerEntity> {
    public static final java.util.List<String> PLAYER_JOINTS = java.util.List.of("bipedBody", "bipedHead", "bipedLeftArm", "bipedRightArm", "bipedLeftLeg", "bipedRightLeg");
    protected AuthoredPlayerModel(ModelPart root) { super(root, false); }
    public abstract void prepare(AbstractClientPlayerEntity player, VertexConsumerProvider buffers);
    public void animate(float delta) { }
    public abstract void clear();
    public abstract void renderHand(MatrixStack matrices, int light, boolean left);
    public float renderScale() { return 1; }
}
