package sscextras.client;

import java.util.IdentityHashMap;
import mod.azure.azurelib.cache.object.*;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import sscextras.trainer.QuadrupedalTrainer;

/** Molds on anatomy already owned by SSC use its existing animated paw bones. */
public final class TrainerFormLayer extends GeoRenderLayer<FormAnimatable> {
    private final TrainerMoldModel mold = new TrainerMoldModel();
    private record Band(Vector3f center,Quaternionf rotation,float width,float depth) {
        Vector3f corner(int x,int z) {
            return new Vector3f(x*(width/2+.3f),0,z*(depth/2+.3f)).rotate(rotation).add(center);
        }
    }
    private record Fit(float x,float y,float z,float width,float depth,java.util.List<Band> bands) { }
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
        boolean drakeTwo=sscextras.drake.EarthenDrake.stage(a.e)==2;
        var fit=fits.computeIfAbsent(bone,key->fit(key,drakeTwo));
        if(fit==null)return;
        m.push();m.translate(fit.x,fit.y+2.5/16,fit.z);m.scale(fit.width,1,fit.depth);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(180));
        mold.foot(m,b,light);
        if(fit.bands.isEmpty()){m.translate(0,-.1,0);mold.band(m,b,light);}
        m.pop();
        for(var band:fit.bands){
            m.push();m.translate(band.center.x/16,band.center.y/16,band.center.z/16);
            m.multiply(band.rotation);m.scale(band.width/4.35f,1,band.depth/4.35f);
            mold.band(m,b,light);m.pop();
        }
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){
            Vector3f previous=null;
            for(var band:fit.bands){var point=band.corner(x,z);if(previous!=null)mold.rod(m,b,light,previous,point);previous=point;}
            if(previous!=null)mold.rod(m,b,light,previous,new Vector3f(fit.x*16+x*2.45f*fit.width,fit.y*16+2.5f,fit.z*16+z*2.2f*fit.depth));
        }
        b.getBuffer(layer);
    }
    private static Vector3f transformed(GeoCube cube,Vector3f point) {
        var c=cube.pivot();var r=cube.rotation();
        return point.sub((float)c.x/16,(float)c.y/16,(float)c.z/16)
                .rotateX((float)r.x).rotateY((float)r.y).rotateZ((float)r.z)
                .add((float)c.x/16,(float)c.y/16,(float)c.z/16);
    }
    private static Band band(GeoCube cube,float along) {
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()){min.min(vertex.position());max.max(vertex.position());}
        var center=transformed(cube,new Vector3f((min.x+max.x)/2,min.y+(max.y-min.y)*along,(min.z+max.z)/2)).mul(16);
        var r=cube.rotation();
        return new Band(center,new Quaternionf().rotationZYX((float)r.z,(float)r.y,(float)r.x),(max.x-min.x)*16+.3f,(max.z-min.z)*16+.3f);
    }
    private static Fit fit(GeoBone bone,boolean drakeTwo) {
        var points=new java.util.ArrayList<Vector3f>();
        boolean arm=bone.getName().endsWith("Arm");
        var cubes=bone.getCubes();
        // Drake two stores its angled segments and paw in one native limb bone.
        // Fit its two distal cuffs to the authored cubes, never the paw's AABB.
        var bands=drakeTwo?java.util.List.of(band(cubes.get(arm?0:1),arm?.75f:.6f),band(cubes.get(arm?0:2),arm?.25f:.5f)):java.util.List.<Band>of();
        for(int i=drakeTwo?(arm?1:5):0;i<cubes.size();i++){
            var cube=cubes.get(i);
            for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices())points.add(transformed(cube,new Vector3f(vertex.position())));
        }
        if(points.isEmpty())return null;
        float bottom=Float.POSITIVE_INFINITY,top=Float.NEGATIVE_INFINITY;
        for(var p:points){bottom=Math.min(bottom,p.y);top=Math.max(top,p.y);}
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var p:points)if(drakeTwo||p.y<=bottom+Math.max(.08f,(top-bottom)*.22f)){min.min(p);max.max(p);}
        float width=Math.max(.4f,(max.x-min.x)*16/4.8f),depth=Math.max(.4f,(max.z-min.z)*16/6);
        return new Fit((min.x+max.x)/2,bottom,(min.z+max.z)/2-(drakeTwo?.75f/16*depth:0),width,depth,bands);
    }
}
