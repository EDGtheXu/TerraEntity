package org.confluence.terraentity.client.boss.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.confluence.terraentity.client.boss.model.GeoBossModel;
import org.confluence.terraentity.client.entity.renderer.GeoNormalRenderer;
import org.confluence.terraentity.entity.boss.PrimeEnderDragon;
import org.confluence.terraentity.init.entity.TEBossEntities;
import software.bernie.geckolib.cache.object.GeoBone;

public class PrimeEnderDragonRenderer extends GeoNormalRenderer<PrimeEnderDragon> {

    float yaw;
    public PrimeEnderDragonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoBossModel<>(TEBossEntities.PRIME_ENDER_DRAGON), false, 1.0f, 0);
    }

    @Override
    public void render(PrimeEnderDragon entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.yaw = entityYaw;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, PrimeEnderDragon animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if(name.startsWith("bone")) {
//            bone.setPosX(80);
            String indexStr = name.substring(4);
            if(!indexStr.isEmpty()) {
                int index = Integer.parseInt(indexStr);
                if (index >= 6 && index <= 16) {
                    index -= 1;
                    var t = animatable.getLatencyPos(index, partialTick);
                    bone.setRotY(Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F * 0.15F);
                }else if(index == 2) {
                    var t = animatable.getLatencyPos(4, partialTick);
                    bone.setRotY(Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F );
                }else if(index == 19) {
                    var t = animatable.getLatencyPos(3, partialTick);
                    bone.setRotY(Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F );
                }else if(index >= 3 && index <= 5) {
                    index = 5 - index;
                    var t = animatable.getLatencyPos(index, partialTick);
                    bone.setRotY(Mth.wrapDegrees(-yaw + (float) t[0]) * 0.017453292F * 0.2F);
                }

            }

        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }


}
