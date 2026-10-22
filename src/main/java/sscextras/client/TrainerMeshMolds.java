package sscextras.client;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/** Fit the mold to the distal surface of an authored paw, in that paw's own frame. */
public final class TrainerMeshMolds {
    private static final TrainerMoldModel MOLD=new TrainerMoldModel();
    private TrainerMeshMolds() { }
    public static void render(List<RiggedMesh.Part> parts,MatrixStack m,VertexConsumerProvider b,int light,UnaryOperator<Vector3f> pose){
        var points=new java.util.ArrayList<Vector3f>();float top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(var part:parts)for(int i=0;i<part.vertices().length;i+=8){var v=part.vertices();var p=pose.apply(new Vector3f(v[i],v[i+1],v[i+2]));points.add(p);top=Math.min(top,p.y);bottom=Math.max(bottom,p.y);}
        if(points.isEmpty())return;
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var p:points)if(p.y>=bottom-Math.max(.08f,(bottom-top)*.45f)){min.min(p);max.max(p);}
        float sx=Math.max(.35f,(max.x-min.x)*16/4.8f),sz=Math.max(.35f,(max.z-min.z)*16/6);
        m.push();m.translate((min.x+max.x)/2,bottom-2.05/16,(min.z+max.z)/2+.75f/16*sz);m.scale(sx,1,sz);
        MOLD.foot(m,b,light);m.translate(0,-.1,0);MOLD.band(m,b,light);m.pop();
    }
}
