package sscextras.client;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/** Fit the mold to the distal surface of an authored paw, in that paw's own frame. */
public final class TrainerMeshMolds {
    private static final TrainerMoldModel MOLD=new TrainerMoldModel();
    private record FootFit(float x,float y,float z,float sx,float sz) { }
    private TrainerMeshMolds() { }
    public static void rails(List<RiggedMesh.Part> upper,List<RiggedMesh.Part> lower,List<RiggedMesh.Part> paw,
            Vector3f[] joints,MatrixStack m,VertexConsumerProvider b,int light,UnaryOperator<Vector3f> pose) {
        var first=RiggedMesh.bounds(upper);var second=RiggedMesh.bounds(lower);var foot=footFit(paw,pose);
        if(first==null||second==null||foot==null)return;
        var centers=new Vector3f[]{new Vector3f(first[0]).add(first[1]).mul(.5f),new Vector3f(second[0]).add(second[1]).mul(.5f)};
        var path=new java.util.ArrayList<Vector3f>();path.add(centers[0]);path.add(centers[1]);
        for(var joint:joints)if(joint.y>centers[0].y)path.add(joint);
        path.sort(java.util.Comparator.comparingDouble(p->p.y));
        float left=Float.POSITIVE_INFINITY,right=Float.NEGATIVE_INFINITY;
        for(var parts:List.of(upper,lower))for(var part:parts)for(int i=0;i<part.vertices().length;i+=8){
            var v=part.vertices();float x=pose.apply(new Vector3f(v[i],v[i+1],v[i+2])).x;
            left=Math.min(left,x);right=Math.max(right,x);
        }
        // Two lateral rails follow the same bend planes as the actual Guardian
        // surface. Corner-to-corner braces crossed the knees and kept the old frame.
        for(int side:new int[]{-1,1}){
            float railX=(side<0?left-.65f/16:right+.65f/16)*16;
            Vector3f previous=null;
            for(var center:path){
                var point=pose.apply(new Vector3f(center)).mul(16);point.x=railX;
                if(point.y>=foot.y*16)continue;
                if(previous!=null)MOLD.rod(m,b,light,previous,point);previous=point;
            }
            var ankle=new Vector3f(railX,foot.y*16,foot.z*16);
            if(previous!=null)MOLD.rod(m,b,light,previous,ankle);
            MOLD.rod(m,b,light,ankle,new Vector3f(foot.x*16+side*2.42f*foot.sx,foot.y*16,foot.z*16));
            for(int i=0;i<centers.length;i++){
                var box=i==0?first:second;var pin=pose.apply(new Vector3f(centers[i]));
                float half=(box[1].x-box[0].x)*8+.15f;
                pin.mul(16);pin.x+=side*half;
                MOLD.rod(m,b,light,pin,new Vector3f(railX,pin.y,pin.z));
            }
        }
    }
    public static void band(List<RiggedMesh.Part> parts,MatrixStack m,VertexConsumerProvider b,int light,UnaryOperator<Vector3f> pose){
        var box=RiggedMesh.bounds(parts);if(box==null)return;var min=box[0];var max=box[1];
        var center=new Vector3f(min).add(max).mul(.5f);
        var top=pose.apply(new Vector3f(center.x,min.y,center.z));var bottom=pose.apply(new Vector3f(center.x,max.y,center.z));
        var fitted=pose.apply(center);
        float width=(max.x-min.x)*16+.3f,depth=(max.z-min.z)*16+.3f;
        m.push();m.translate(fitted.x,fitted.y,fitted.z);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotation((float)Math.atan2(bottom.z-top.z,bottom.y-top.y)));
        MOLD.fittedBand(m,b,light,width,depth,1);m.pop();
    }
    public static void render(List<RiggedMesh.Part> parts,MatrixStack m,VertexConsumerProvider b,int light,UnaryOperator<Vector3f> pose,boolean ankleBand){
        var fit=footFit(parts,pose);if(fit==null)return;
        m.push();m.translate(fit.x,fit.y,fit.z);m.scale(fit.sx,1,fit.sz);
        MOLD.foot(m,b,light);
        if(ankleBand){m.translate(0,-.1,0);MOLD.band(m,b,light);}
        m.pop();
    }
    private static FootFit footFit(List<RiggedMesh.Part> parts,UnaryOperator<Vector3f> pose){
        var points=new java.util.ArrayList<Vector3f>();float top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(var part:parts)for(int i=0;i<part.vertices().length;i+=8){var v=part.vertices();var p=pose.apply(new Vector3f(v[i],v[i+1],v[i+2]));points.add(p);top=Math.min(top,p.y);bottom=Math.max(bottom,p.y);}
        if(points.isEmpty())return null;
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var p:points)if(p.y>=bottom-Math.max(.08f,(bottom-top)*.45f)){min.min(p);max.max(p);}
        float sx=Math.max(.35f,(max.x-min.x)*16/4.8f),sz=Math.max(.35f,(max.z-min.z)*16/6);
        return new FootFit((min.x+max.x)/2,bottom-2.5f/16,(min.z+max.z)/2+.75f/16*sz,sx,sz);
    }
}
