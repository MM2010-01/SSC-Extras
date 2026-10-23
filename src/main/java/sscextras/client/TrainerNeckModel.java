package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Hollow chin support: a tall front, a shallow back and continuous sloping sides. */
public final class TrainerNeckModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final RiggedMesh.Part support = support();
    private static RiggedMesh.Part support() {
        var vertices=new java.util.ArrayList<Float>();
        for(float[] bounds:new float[][]{{-3,3,-3,-2.35f},{-3,3,2.35f,3},{-3,-2.35f,-2.35f,2.35f},{2.35f,3,-2.35f,2.35f}}) {
            var points=new org.joml.Vector3f[8];
            for(int i=0;i<8;i++) {
                float z=bounds[(i&4)==0?2:3];
                points[i]=new org.joml.Vector3f(bounds[(i&1)==0?0:1],(i&2)==0?-3+(z+3)*.375f:1.5f,z).div(16);
            }
            for(int[] face:new int[][]{{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}}) {
                var normal=new org.joml.Vector3f(points[face[1]]).sub(points[face[0]])
                        .cross(new org.joml.Vector3f(points[face[2]]).sub(points[face[0]])).normalize();
                for(int i=0;i<4;i++) {
                    var p=points[face[i]];
                    for(float value:new float[]{p.x,p.y,p.z,i==1||i==2?.1875f:0,i>=2?.125f:0,normal.x,normal.y,normal.z})vertices.add(value);
                }
            }
        }
        float[] data=new float[vertices.size()];for(int i=0;i<data.length;i++)data[i]=vertices.get(i);
        return new RiggedMesh.Part(0,false,data,null);
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push(); matrices.translate(0,.25/16,2.5/16);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45));
        RiggedMesh.emit(support,matrices,null,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,.72f,.76f,.82f,1,OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
