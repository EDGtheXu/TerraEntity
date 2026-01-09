package org.confluence.terraentity.client.entity.renderer.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;
import org.confluence.terraentity.client.entity.renderer.GeoNormalRenderer;
import org.confluence.terraentity.entity.monster.slime.SpikedSlime;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class GeoSlimeRenderer<T extends SpikedSlime> extends GeoNormalRenderer<T> {

    public GeoSlimeRenderer(EntityRendererProvider.Context renderManager, ResourceLocation path) {
        super(renderManager, path);
    }


    @Override
    protected void adjustPose(PoseStack poseStack, T animatable, BakedGeoModel model, float partialTick) {
        super.adjustPose(poseStack, animatable, model, partialTick);
        this.scale(animatable, poseStack, partialTick);
    }

    protected void scale(Slime livingEntity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float f1 = (float)livingEntity.getSize();
        float f2 = Mth.lerp(partialTickTime, livingEntity.oSquish, livingEntity.squish) / (f1 * 0.5F + 1.0F);
        float f3 = 1.0F / (f2 + 1.0F);
        poseStack.scale(f3 * f1, 1.0F / f3 * f1, f3 * f1);
    }
}
