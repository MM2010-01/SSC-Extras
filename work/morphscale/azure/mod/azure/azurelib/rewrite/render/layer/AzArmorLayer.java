/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
 *  net.minecraft.class_1297
 *  net.minecraft.class_1304
 *  net.minecraft.class_1304$class_1305
 *  net.minecraft.class_1309
 *  net.minecraft.class_1738
 *  net.minecraft.class_1747
 *  net.minecraft.class_1792
 *  net.minecraft.class_1799
 *  net.minecraft.class_1921
 *  net.minecraft.class_2190
 *  net.minecraft.class_2484$class_2485
 *  net.minecraft.class_2487
 *  net.minecraft.class_2512
 *  net.minecraft.class_2519
 *  net.minecraft.class_2520
 *  net.minecraft.class_2631
 *  net.minecraft.class_2960
 *  net.minecraft.class_310
 *  net.minecraft.class_4587
 *  net.minecraft.class_4588
 *  net.minecraft.class_4597
 *  net.minecraft.class_4608
 *  net.minecraft.class_5598
 *  net.minecraft.class_5599
 *  net.minecraft.class_5602
 *  net.minecraft.class_572
 *  net.minecraft.class_630
 *  net.minecraft.class_630$class_628
 *  net.minecraft.class_836
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.layer;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import mod.azure.azurelib.cache.object.GeoCube;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.AzRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.AzArmorModel;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRenderer;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipeline;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererRegistry;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import mod.azure.azurelib.rewrite.render.layer.AzRenderLayer;
import mod.azure.azurelib.util.RenderUtils;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1738;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2190;
import net.minecraft.class_2484;
import net.minecraft.class_2487;
import net.minecraft.class_2512;
import net.minecraft.class_2519;
import net.minecraft.class_2520;
import net.minecraft.class_2631;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_4608;
import net.minecraft.class_5598;
import net.minecraft.class_5599;
import net.minecraft.class_5602;
import net.minecraft.class_572;
import net.minecraft.class_630;
import net.minecraft.class_836;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AzArmorLayer<T extends class_1309>
implements AzRenderLayer<T> {
    protected static final Map<String, class_2960> ARMOR_PATH_CACHE = new Object2ObjectOpenHashMap();
    protected static final class_572<class_1309> INNER_ARMOR_MODEL = new class_572(class_310.method_1551().method_31974().method_32072(class_5602.field_27579));
    protected static final class_572<class_1309> OUTER_ARMOR_MODEL = new class_572(class_310.method_1551().method_31974().method_32072(class_5602.field_27580));
    @Nullable
    protected class_1799 mainHandStack;
    @Nullable
    protected class_1799 offhandStack;
    @Nullable
    protected class_1799 helmetStack;
    @Nullable
    protected class_1799 chestplateStack;
    @Nullable
    protected class_1799 leggingsStack;
    @Nullable
    protected class_1799 bootsStack;

    @Override
    public void preRender(AzRendererPipelineContext<T> context) {
        this.mainHandStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6173);
        this.offhandStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6171);
        this.helmetStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6169);
        this.chestplateStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6174);
        this.leggingsStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6172);
        this.bootsStack = ((class_1309)context.animatable()).method_6118(class_1304.field_6166);
    }

    @Override
    public void render(AzRendererPipelineContext<T> context) {
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<T> context, AzBone bone) {
        class_1747 blockItem;
        class_1799 armorStack = this.getArmorItemForBone(context, bone);
        if (armorStack == null) {
            return;
        }
        context.poseStack().method_22903();
        class_1792 class_17922 = armorStack.method_7909();
        if (class_17922 instanceof class_1747 && (class_17922 = (blockItem = (class_1747)class_17922).method_7711()) instanceof class_2190) {
            class_2190 skullBlock = (class_2190)class_17922;
            this.renderSkullAsArmor(context, bone, armorStack, skullBlock);
        } else {
            this.renderArmor(context, bone, armorStack);
        }
        context.setVertexConsumer(context.multiBufferSource().getBuffer(context.renderType()));
        context.poseStack().method_22909();
    }

    public void renderArmor(AzRendererPipelineContext<T> context, AzBone bone, class_1799 armorStack) {
        class_1304 slot = this.getEquipmentSlotForBone(context, bone, armorStack);
        AzArmorRenderer renderer = AzArmorRendererRegistry.getOrNull(armorStack.method_7909());
        class_572<T> model = this.getModelForItem(armorStack, slot);
        class_630 modelPart = this.getModelPartForBone(context, bone, model);
        if (!modelPart.field_3663.isEmpty()) {
            context.poseStack().method_22903();
            context.poseStack().method_22905(-1.0f, -1.0f, 1.0f);
            if (renderer != null) {
                this.prepModelPartForRender(context, bone, modelPart);
                this.renderAzArmorPiece(context, slot, armorStack, renderer, (class_1309)context.animatable(), model, modelPart);
            } else if (armorStack.method_7909() instanceof class_1738) {
                this.prepModelPartForRender(context, bone, modelPart);
                this.renderVanillaArmorPiece(context, bone, slot, armorStack, modelPart);
            }
            context.poseStack().method_22909();
        }
    }

    @NotNull
    protected class_1304 getEquipmentSlotForBone(AzRendererPipelineContext<T> context, AzBone bone, class_1799 stack) {
        class_1309 animatable = (class_1309)context.animatable();
        for (class_1304 slot : class_1304.values()) {
            boolean isHumanoidArmorSlotType;
            boolean bl = isHumanoidArmorSlotType = slot.method_5925() == class_1304.class_1305.field_6178;
            if (!isHumanoidArmorSlotType || stack != animatable.method_6118(slot)) continue;
            return slot;
        }
        return class_1304.field_6174;
    }

    @NotNull
    protected class_630 getModelPartForBone(AzRendererPipelineContext<T> context, AzBone bone, class_572<?> baseModel) {
        return baseModel.field_3391;
    }

    @Nullable
    protected class_1799 getArmorItemForBone(AzRendererPipelineContext<T> context, AzBone bone) {
        return null;
    }

    protected void renderAzArmorPiece(AzRendererPipelineContext<T> context, class_1304 slot, class_1799 armorStack, AzArmorRenderer renderer, class_1309 entity, class_572<T> model, class_630 modelPart) {
        AzArmorRendererPipeline renderPipelines = renderer.rendererPipeline();
        AzArmorBoneContext boneContext = renderPipelines.context().boneContext();
        AzArmorModel<?> armorModel = renderPipelines.armorModel();
        renderer.prepForRender((class_1297)entity, armorStack, slot, model);
        boneContext.applyBoneVisibilityByPart(slot, modelPart, model);
        armorModel.method_2828(context.poseStack(), null, context.packedLight(), class_4608.field_21444, context.red(), context.green(), context.blue(), context.alpha());
    }

    protected <I extends class_1792> void renderVanillaArmorPiece(AzRendererPipelineContext<T> context, AzBone bone, class_1304 slot, class_1799 armorStack, class_630 modelPart) {
        class_4588 buffer = this.getVanillaArmorBuffer(context, armorStack, slot, bone, false);
        modelPart.method_22698(context.poseStack(), buffer, context.packedLight(), context.packedOverlay());
        if (armorStack.method_7958()) {
            modelPart.method_22699(context.poseStack(), this.getVanillaArmorBuffer(context, armorStack, slot, bone, true), context.packedLight(), context.packedOverlay(), context.red(), context.green(), context.blue(), context.alpha());
        }
    }

    protected class_4588 getVanillaArmorBuffer(AzRendererPipelineContext<T> context, class_1799 stack, class_1304 slot, AzBone bone, boolean forGlint) {
        if (forGlint) {
            return context.multiBufferSource().getBuffer(class_1921.method_27949());
        }
        return context.multiBufferSource().getBuffer(class_1921.method_25448((class_2960)this.getVanillaArmorResource((class_1297)context.animatable(), stack, slot, bone.getName())));
    }

    @Nullable
    protected AzArmorRenderer getRendererForItem(class_1799 stack) {
        class_1792 item = stack.method_7909();
        return AzArmorRendererRegistry.getOrNull(item);
    }

    protected class_572<T> getModelForItem(class_1799 stack, class_1304 slot) {
        AzArmorRenderer renderer = this.getRendererForItem(stack);
        if (renderer == null) {
            return slot == class_1304.field_6172 ? INNER_ARMOR_MODEL : OUTER_ARMOR_MODEL;
        }
        return renderer.rendererPipeline().armorModel();
    }

    protected void renderSkullAsArmor(AzRendererPipelineContext<T> context, AzBone bone, class_1799 stack, class_2190 skullBlock) {
        GameProfile skullProfile = null;
        class_2487 stackTag = stack.method_7969();
        if (stackTag != null) {
            class_2519 tag;
            String skullOwner;
            class_2520 skullTag = stackTag.method_10580("SkullOwner");
            if (skullTag instanceof class_2487) {
                class_2487 compoundTag = (class_2487)skullTag;
                skullProfile = class_2512.method_10683((class_2487)compoundTag);
            } else if (skullTag instanceof class_2519 && !(skullOwner = (tag = (class_2519)skullTag).method_10714()).isBlank()) {
                class_2487 profileTag = new class_2487();
                class_2631.method_11335((GameProfile)new GameProfile(null, skullOwner), name -> stackTag.method_10566("SkullOwner", (class_2520)class_2512.method_10684((class_2487)profileTag, (GameProfile)name)));
                skullProfile = class_2512.method_10683((class_2487)profileTag);
            }
        }
        class_2484.class_2485 type = skullBlock.method_9327();
        class_5598 model = (class_5598)class_836.method_32160((class_5599)class_310.method_1551().method_31974()).get(type);
        class_1921 renderType = class_836.method_3578((class_2484.class_2485)type, (GameProfile)skullProfile);
        context.poseStack().method_22903();
        RenderUtils.translateAndRotateMatrixForBone(context.poseStack(), bone);
        context.poseStack().method_22905(1.1875f, 1.1875f, 1.1875f);
        context.poseStack().method_46416(-0.5f, 0.0f, -0.5f);
        class_836.method_32161(null, (float)0.0f, (float)0.0f, (class_4587)context.poseStack(), (class_4597)context.multiBufferSource(), (int)context.packedLight(), (class_5598)model, (class_1921)renderType);
        context.poseStack().method_22909();
    }

    protected void prepModelPartForRender(AzRendererPipelineContext<T> context, AzBone bone, class_630 sourcePart) {
        GeoCube firstCube = bone.getCubes().get(0);
        class_630.class_628 armorCube = (class_630.class_628)sourcePart.field_3663.get(0);
        double armorBoneSizeX = firstCube.size().method_10216();
        double armorBoneSizeY = firstCube.size().method_10214();
        double armorBoneSizeZ = firstCube.size().method_10215();
        float actualArmorSizeX = Math.abs(armorCube.field_3648 - armorCube.field_3645);
        float actualArmorSizeY = Math.abs(armorCube.field_3647 - armorCube.field_3644);
        float actualArmorSizeZ = Math.abs(armorCube.field_3646 - armorCube.field_3643);
        float scaleX = (float)(armorBoneSizeX / (double)actualArmorSizeX);
        float scaleY = (float)(armorBoneSizeY / (double)actualArmorSizeY);
        float scaleZ = (float)(armorBoneSizeZ / (double)actualArmorSizeZ);
        sourcePart.method_2851(-(bone.getPivotX() - (bone.getPivotX() * scaleX - bone.getPivotX()) / scaleX), -(bone.getPivotY() - (bone.getPivotY() * scaleY - bone.getPivotY()) / scaleY), bone.getPivotZ() - (bone.getPivotZ() * scaleZ - bone.getPivotZ()) / scaleZ);
        sourcePart.field_3654 = -bone.getRotX();
        sourcePart.field_3675 = -bone.getRotY();
        sourcePart.field_3674 = bone.getRotZ();
        context.poseStack().method_22905(scaleX, scaleY, scaleZ);
    }

    public class_2960 getVanillaArmorResource(class_1297 entity, class_1799 stack, class_1304 slot, String type) {
        String domain = "minecraft";
        String path = ((class_1738)stack.method_7909()).method_7686().method_7694();
        String[] materialNameSplit = path.split(":", 2);
        if (materialNameSplit.length > 1) {
            domain = materialNameSplit[0];
            path = materialNameSplit[1];
        }
        String texture = String.format("%s:textures/models/armor/%s_layer_%d.png", domain, path, slot == class_1304.field_6172 ? 2 : 1);
        return ARMOR_PATH_CACHE.computeIfAbsent(texture, class_2960::new);
    }
}

