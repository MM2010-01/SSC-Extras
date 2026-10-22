package sscextras.client;

import java.util.IdentityHashMap;
import mod.azure.azurelib.cache.object.*;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.*;
import org.joml.Vector3f;
import sscextras.trainer.QuadrupedalTrainer;

/** Molds on anatomy already owned by SSC use its existing animated paw bones. */
public final class TrainerFormLayer extends GeoRenderLayer<FormAnimatable> {
    private final TrainerMoldModel mold = new TrainerMoldModel();
    private record Fit(float x,float y,float z,float width,float depth) { }
    private final IdentityHashMap<GeoBone,Fit> fits = new IdentityHashMap<>();
    private boolean hands, feet;
    public TrainerFormLayer(FormRenderer renderer) { super(renderer); }
    @Override public void preRender(MatrixStack m, FormAnimatable a, BakedGeoModel model, RenderLayer layer,
            VertexConsumerProvider b, VertexConsumer v, float delta, int light, int overlay) {
        hands=feet=false;
        if(a.e==null||a.e.isInvisible()||a.e.isSpectator()||light==0x7ffffffe)return;
        hands=QuadrupedalTrainer.nativeDigitigrade(a.e)&&!QuadrupedalTrainer.equipped(a.e,QuadrupedalTrainer.HANDS,true).isEmpty();
        feet=QuadrupedalTrainer.nativeDigitigrade(a.e)&&!QuadrupedalTrainer.equipped(a.e,QuadrupedalTrainer.FEET,true).isEmpty();
    }
    @Override public void renderForBone(MatrixStack m, FormAnimatable a, GeoBone bone, RenderLayer layer,
            VertexConsumerProvider b, VertexConsumer v, float delta,int light,int overlay) {
        String name=bone.getName();
        boolean arm=name.equals("bipedLeftArm")||name.equals("bipedRightArm");
        boolean leg=name.equals("bipedLeftLeg")||name.equals("bipedRightLeg");
        boolean front=name.endsWith("_front_paw"),hind=name.endsWith("_hind_paw");
        boolean segmented=sscextras.drake.EarthenDrake.stage(a.e)==3;
        if(bone.isHidden()||!(hands&&(segmented?front:arm)||feet&&(segmented?hind:leg)))return;
        var fit=fits.computeIfAbsent(bone,TrainerFormLayer::fit);
        if(fit==null)return;
        m.push();m.translate(fit.x,fit.y+2.05/16,fit.z);m.scale(fit.width,1,fit.depth);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(180));
        mold.foot(m,b,light);m.translate(0,-.1,0);mold.band(m,b,light);m.pop();b.getBuffer(layer);
    }
    private static Fit fit(GeoBone bone) {
        var points=new java.util.ArrayList<Vector3f>();
        for(var cube:bone.getCubes())for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()) {
            var p=new Vector3f(vertex.position());var c=cube.pivot();var r=cube.rotation();
            p.sub((float)c.x/16,(float)c.y/16,(float)c.z/16).rotateX((float)r.x).rotateY((float)r.y).rotateZ((float)r.z)
                .add((float)c.x/16,(float)c.y/16,(float)c.z/16);points.add(p);
        }
        if(points.isEmpty())return null;
        float bottom=Float.POSITIVE_INFINITY,top=Float.NEGATIVE_INFINITY;
        for(var p:points){bottom=Math.min(bottom,p.y);top=Math.max(top,p.y);}
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var p:points)if(p.y<=bottom+Math.max(.08f,(top-bottom)*.22f)){min.min(p);max.max(p);}
        return new Fit((min.x+max.x)/2,bottom,(min.z+max.z)/2,Math.max(.4f,(max.x-min.x)*16/4.8f),Math.max(.4f,(max.z-min.z)*16/6));
    }
}
