package org.confluence.terraentity.client.boss.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.client.boss.model.GeoBossModel;
import org.confluence.terraentity.client.entity.renderer.GeoNormalRenderer;
import org.confluence.terraentity.entity.boss.primeenderdragon.PrimeEnderDragon;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.utils.TEUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

public class PrimeEnderDragonRenderer extends GeoNormalRenderer<PrimeEnderDragon> {

    float yaw;
    static ResourceLocation laserTexture = TerraEntity.defaultPath("textures/entity/beacon_beam.png");

    public PrimeEnderDragonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoBossModel<>(TEBossEntities.PRIME_ENDER_DRAGON), false, 1.0f, 0);
    }

    @Override
    public void render(PrimeEnderDragon entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.yaw = entityYaw;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);


        this.renderLaser(entity, partialTick, poseStack, bufferSource);

    }

    protected void adjustPose(PoseStack poseStack, PrimeEnderDragon animatable, BakedGeoModel model, float partialTick){
        this.rotateFromX(poseStack, animatable, partialTick);
    }

    private void rotateFromX(PoseStack poseStack, PrimeEnderDragon animatable, float partialTick) {
        var t = animatable.getLatencyPos(1, partialTick);
        var to = animatable.getLatencyPos(4, partialTick);
        Vec3 dir = new Vec3(t[1] - to[1], t[2] - to[2], t[3] - to[3]);
        float[] rots = TEUtils.dirToRot(dir);

        double rad = Mth.lerp(partialTick, animatable.yRotO, animatable.getYRot()) * Math.PI / 180;
        poseStack.mulPose(Axis.of(new Vector3f((float) Math.cos(rad), 0, (float) Math.sin(rad))).rotationDegrees(rots[1]));
    }

    private void renderLaser(PrimeEnderDragon entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        this.model.getBone("bone5").ifPresent(bone->{
            float end = entity.getLaserRange(partialTick);
            if(end <= 0) {
                return;
            }

            poseStack.pushPose();
            this.rotateFromX(poseStack, entity, partialTick);

            poseStack.translate(bone.getLocalPosition().x, bone.getLocalPosition().y - 0.5F, bone.getLocalPosition().z);
            Vec3 dir = Vec3.directionFromRotation(new Vec2(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()), Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
            poseStack.mulPose(new Quaternionf().rotateTo(new Vector3f(0,1,0), dir.toVector3f()));
            poseStack.translate(0, 1, 0);
            float time = partialTick + entity.tickCount;
            poseStack.mulPose(Axis.YN.rotation(time * 0.2f ));

            float w = 0.5f;
            float start = 1;

            float uvOffset = time % 10 /  10;
            int color = 0x66440044;
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.beaconBeam(laserTexture, false));
            VertexConsumer glowConsumer = bufferSource.getBuffer(RenderType.beaconBeam(laserTexture, true));

            // 四棱锥
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, w,  w, 0.9f, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, -w,  w, 0.9f, 0);
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0);

            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, w,  -w, 0.9f, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, w,  w, 0.9f, 0);
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0);

            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, -w,  w, 0.9f, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, -w,  -w, 0.9f, 0);
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0);
//
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, -w,  -w, 0.9f, 0.9f);
            addVertex(poseStack.last(), consumer, color, start, w,  -w, 0.9f, 0);
            addVertex(poseStack.last(), consumer, color, 0, 0,  0, 0, 0);

            // 顶
            addVertex(poseStack.last(), consumer, color, end, w,  w, 0, 1);
            addVertex(poseStack.last(), consumer, color, end, w,  -w, 1, 1);
            addVertex(poseStack.last(), consumer, color, end, -w,  -w, 1, 0);
            addVertex(poseStack.last(), consumer, color, end, -w,  w, 0, 0);

//            addVertex(poseStack.last(), consumer, color, 0, w,  w, 0, 1);
//            addVertex(poseStack.last(), consumer, color, 0, -w,  w, 1, 1);
//            addVertex(poseStack.last(), consumer, color, 0, -w,  -w, 1, 0);
//            addVertex(poseStack.last(), consumer, color, 0, w,  -w, 0, 0);
            // 主体
            renderPart(poseStack, consumer, color, start, end,
                    w, -w,
                    -w, -w,
                    w, w,
                    -w, w,
                    0.0F, 1.0F, uvOffset, uvOffset + 1);

            float glowWidth = w + 0.2f;
            start -= 0.1f;
            end += 0.1f;
            renderPart(poseStack, glowConsumer, color, start, end,
                    glowWidth, -glowWidth,
                    -glowWidth, -glowWidth,
                    glowWidth, glowWidth,
                    -glowWidth, glowWidth,
                    0.0F, 1.0F, uvOffset + 0.5f, uvOffset + 1.5f);

            addVertex(poseStack.last(), glowConsumer, color, start, glowWidth,  glowWidth, 0, 1);
            addVertex(poseStack.last(), glowConsumer, color, start, -glowWidth,  glowWidth, 1, 1);
            addVertex(poseStack.last(), glowConsumer, color, start, -glowWidth,  -glowWidth, 1, 0);
            addVertex(poseStack.last(), glowConsumer, color, start, glowWidth,  -glowWidth, 0, 0);

            addVertex(poseStack.last(), glowConsumer, color, end, glowWidth,  glowWidth, 0, 1);
            addVertex(poseStack.last(), glowConsumer, color, end, glowWidth,  -glowWidth, 1, 1);
            addVertex(poseStack.last(), glowConsumer, color, end, -glowWidth,  -glowWidth, 1, 0);
            addVertex(poseStack.last(), glowConsumer, color, end, -glowWidth,  glowWidth, 0, 0);

            start += 0.1f;
            end -= 0.1f;

            poseStack.mulPose(Axis.YN.rotation(-time * 0.4f));

            float interval = 10;
            float thickFrom = time * 0.3f % interval -  interval;

            float len = 2;
            w = 0.6f;
            glowWidth = w + 0.2f;
            while (thickFrom < end) {
                float s = Math.max(start, thickFrom);
                float to = Math.min(end, thickFrom + len);
                if(to <= start) {
                    thickFrom += interval;
                    continue;
                }
                renderPart(poseStack, consumer, color, s, to,
                    w, -w,
                    -w, -w,
                    w, w,
                    -w, w,
                    0F, 1F, uvOffset, uvOffset + 1);

                addVertex(poseStack.last(), consumer, color, s, w,  w, 0, 1);
                addVertex(poseStack.last(), consumer, color, s, -w,  w, 1, 1);
                addVertex(poseStack.last(), consumer, color, s, -w,  -w, 1, 0);
                addVertex(poseStack.last(), consumer, color, s, w,  -w, 0, 0);

                addVertex(poseStack.last(), consumer, color, to, w,  w, 0, 1);
                addVertex(poseStack.last(), consumer, color, to, w,  -w, 1, 1);
                addVertex(poseStack.last(), consumer, color, to, -w,  -w, 1, 0);
                addVertex(poseStack.last(), consumer, color, to, -w,  w, 0, 0);

                s -= 0.1f;
                to += 0.1f;
                renderPart(poseStack, glowConsumer, color, s, to,
                        glowWidth, -glowWidth,
                        -glowWidth, -glowWidth,
                        glowWidth, glowWidth,
                        -glowWidth, glowWidth,
                        0F, 1F, uvOffset, uvOffset + 1);

                addVertex(poseStack.last(), glowConsumer, color, s, glowWidth,  glowWidth, 0, 1);
                addVertex(poseStack.last(), glowConsumer, color, s, -glowWidth,  glowWidth, 1, 1);
                addVertex(poseStack.last(), glowConsumer, color, s, -glowWidth,  -glowWidth, 1, 0);
                addVertex(poseStack.last(), glowConsumer, color, s, glowWidth,  -glowWidth, 0, 0);

                addVertex(poseStack.last(), glowConsumer, color, to, glowWidth,  glowWidth, 0, 1);
                addVertex(poseStack.last(), glowConsumer, color, to, glowWidth,  -glowWidth, 1, 1);
                addVertex(poseStack.last(), glowConsumer, color, to, -glowWidth,  -glowWidth, 1, 0);
                addVertex(poseStack.last(), glowConsumer, color, to, -glowWidth,  glowWidth, 0, 0);

                thickFrom += interval;
            }

            poseStack.popPose();
        });
    }


    @Override
    public void renderRecursively(PoseStack poseStack, PrimeEnderDragon animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if(name.startsWith("bone")) {
            String indexStr = name.substring(4);
            ToFloatFunction<Float> toFloatFunction = ToFloatFunction.createUnlimited((angle)-> 0.4f * tanhLike(angle, 10f));
            if(!indexStr.isEmpty()) {
                int index = Integer.parseInt(indexStr);
                if (index >= 6 && index <= 16) {
                    // 尾巴
                    index -= 1;
                    var t = animatable.getLatencyPos(index, partialTick);
                    bone.setRotY(Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F * 0.15F);
                }else if(index == 2) {
                    // 后腿
                    var t = animatable.getLatencyPos(4, partialTick);
                    float angle = Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F;
                    bone.setRotY(angle);
                    bone.setRotZ(toFloatFunction.apply(angle * -4));
                }else if(index == 19) {
                    // 前腿和翅膀
                    var t = animatable.getLatencyPos(3, partialTick);
                    float angle = Mth.wrapDegrees(yaw - (float) t[0]) * 0.017453292F;

                    bone.setRotY(angle);
                    bone.setRotZ(toFloatFunction.apply(angle * -2F));
                }else if(index >= 3 && index <= 5) {

                    // 头和颈部
                    index = 5 - index;
                    var t = animatable.getLatencyPos(index, partialTick);
                    float angle = Mth.wrapDegrees(-yaw + (float) t[0]) * 0.017453292F * 0.2F;
                    bone.setRotY(angle);
                    bone.setRotZ(toFloatFunction.apply(angle * 1F));


                }

            }

        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static float tanhLike(float x, float k) {
        return (float) Math.tanh(k * x);
    }


    // 改自信标
    private static void renderPart(PoseStack poseStack, VertexConsumer consumer, int color, float minY, float maxY, float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4, float minU, float maxU, float minV, float maxV) {
        PoseStack.Pose posestack$pose = poseStack.last();
        renderQuad(posestack$pose, consumer, color, minY, maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(posestack$pose, consumer, color, minY, maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(posestack$pose, consumer, color, minY, maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(posestack$pose, consumer, color, minY, maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);

    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer consumer, int color, float minY, float maxY, float minX, float minZ, float maxX, float maxZ, float minU, float maxU, float minV, float maxV) {
        addVertex(pose, consumer, color, maxY, minX, minZ, maxU, minV);
        addVertex(pose, consumer, color, minY, minX, minZ, maxU, maxV);
        addVertex(pose, consumer, color, minY, maxX, maxZ, minU, maxV);
        addVertex(pose, consumer, color, maxY, maxX, maxZ, minU, minV);
    }


    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, float y, float x, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
