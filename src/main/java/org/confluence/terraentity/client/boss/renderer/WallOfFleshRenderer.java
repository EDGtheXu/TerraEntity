package org.confluence.terraentity.client.boss.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.client.boss.model.GeoBossModel;
import org.confluence.terraentity.client.entity.renderer.GeoNormalRenderer;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFlesh;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshEye;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshMouth;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshPart;
import net.minecraft.util.Tuple;
import javax.annotation.Nonnull;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class WallOfFleshRenderer extends GeoNormalRenderer<WallOfFlesh> {

    private static final String[] VARIANT_BONES = {"bone0", "bone1", "bone2", "bone3", "bone4"};
    private static final int[] MODEL_WEIGHTS = {1, 1, 1, 1, 5};

    private static final float CELL_SIZE = 240f;
    private static final float CELL_HALF = CELL_SIZE / 2.0f;
    private static final Pattern GRID_BONE = Pattern.compile("bone-?\\d+_-?\\d+");
    private final Map<String, Integer> cellVariantCache = new HashMap<>();

    private boolean modelMerged = false;

    public WallOfFleshRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoBossModel<>(TEBossEntities.WALL_OF_FLESH), false, 1.0f, 0.5f);
    }

    @Override
    public void preRender(PoseStack poseStack, WallOfFlesh animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        this.entityRenderTranslations = new Matrix4f(poseStack.last().pose());
        GeoModel<WallOfFlesh> geoModel = super.getGeoModel();
        getOrBuildMergedRoot(animatable, geoModel);
        GeoBone mergedRoot = geoModel.getBone("All").orElse(null);
        if (mergedRoot != null && !model.topLevelBones().contains(mergedRoot)) {
            model.topLevelBones().clear();
            model.topLevelBones().add(mergedRoot);
        }
        scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    @Override
    public void render(WallOfFlesh wall, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.pushPose();
        super.render(wall, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
        if (Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()
                && !wall.isInvisible()
                && !Minecraft.getInstance().showOnlyReducedInfo()
                && !wall.isRemoved()) {
            AABB aabb = wall.getOutsideCollisionBox().move(-wall.getX(), -wall.getY(), -wall.getZ());
            LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), aabb, 1, 0, 0, 1.0F);
            AABB bbaa = wall.getInsideBox().move(-wall.getX(), -wall.getY(), -wall.getZ());
            LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), bbaa, 0, 0, 1, 1.0F);
        }

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WallOfFlesh animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (bone.getName() != null) {
            Vec3 camPos = camera.getPosition();
            Vector3d pos3d = bone.getWorldPosition();
            Vec3 pos = new Vec3(pos3d.x, pos3d.y, pos3d.z);
            double distSq = camPos.distanceToSqr(pos);

            if (bone.getName().endsWith("_b") && distSq > 100*100) {
                return;
            }else if (bone.getName().endsWith("_c") && distSq > 200*200) {
                return;
            }else if (bone.getName().endsWith("_d") && distSq > 400*400) {
                return;
            }else if (distSq > 1200*1200) {
                return;
            }
        }

        Frustum frustum = Minecraft.getInstance().levelRenderer.getFrustum();

        AABB worldAabb = null;
        // 只对网格骨骼进行网格索引解析和视锥体剔除
        if (isGridBone(bone)) {
            int[] idx = parseGridIndex(bone.getName());
            if (idx == null) {
                return;
            }
            float c = CELL_HALF / 16f;
            // 相对坐标（Gecko 单位 1/16 方块），保持当前 poseStack 变换，这样 AABB 随实体移动
            double cx = (idx[0] * CELL_SIZE + CELL_HALF) / 16.0;
            double cy = (idx[1] * CELL_SIZE + CELL_HALF) / 16.0;
            double cz = 0;
            AABB cellAabb = new AABB(cx, cy, cz, cx, cy, cz).inflate(c).inflate(8);
            worldAabb = cellAabb.move(animatable.getX(), animatable.getY(), animatable.getZ());
            //if (renderHitBoxes && !animatable.isInvisible() && !Minecraft.getInstance().showOnlyReducedInfo() && !animatable.isRemoved()) {
            //    LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), cellAabb, 1, 0, 0, 1.0F);
            //}
        }

        if (worldAabb != null && cubeInFrustum(frustum, worldAabb.minX, worldAabb.minY, worldAabb.minZ, worldAabb.maxX, worldAabb.maxY, worldAabb.maxZ)) {
            //return;
        }

        if ("Head_eye".equals(bone.getName())) {
            GeoBone parent = bone.getParent();
            if (parent != null) {
                for (WallOfFleshPart part : animatable.subEntities) {
                    String partName = part.name;
                    if (part instanceof WallOfFleshEye eyePart && parent.getName().equals(partName)) {
                        adjustEyePose(bone, eyePart, animatable, partialTick);
                    }
                }
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private boolean cubeInFrustum(Frustum frustum,double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float f = (float)(minX - frustum.camX);
        float f1 = (float)(minY - frustum.camY);
        float f2 = (float)(minZ - frustum.camZ);
        float f3 = (float)(maxX - frustum.camX);
        float f4 = (float)(maxY - frustum.camY);
        float f5 = (float)(maxZ - frustum.camZ);

        // 使用 intersectAab 方法获取更详细的相交结果
        int intersectResult = frustum.intersection.intersectAab(f, f1, f2, f3, f4, f5);

        // INTERSECT: AABB与视锥体相交（包括部分相交）
        // INSIDE: AABB完全在视锥体内
        // 这两种情况都表示AABB是可见的
        return intersectResult != FrustumIntersection.INTERSECT && intersectResult != FrustumIntersection.INSIDE;
    }

    private int[] parseGridIndex(String name) {
        // 解析 boneX_Y 或 bone-3_5
        int split = name.indexOf('_');
        if (!name.startsWith("bone") || split < 0) return null;
        String xs = name.substring(4, split);
        String ys = name.substring(split + 1);
        if (xs.isEmpty() || ys.isEmpty()) return null;
        int x = Integer.parseInt(xs);
        int y = Integer.parseInt(ys);
        return new int[]{x, y};
    }

    private void adjustEyePose(GeoBone bone, WallOfFleshEye eyePart, WallOfFlesh parentMob, float partialTick) {

        if (parentMob == null || !parentMob.isAlive() || parentMob.deathTime > 0) {
            return;
        }

        LivingEntity target = eyePart.target;
        if (target != null && target.isAlive() && !target.isRemoved()) {
            Vec3 targetEyePos = new Vec3(target.xo, target.yo, target.zo)
                    .lerp(target.position(), partialTick)
                    .add(0.0, target.getEyeHeight(), 0.0);
            Vec3 eyePos = new Vec3(eyePart.xo, eyePart.yo, eyePart.zo)
                    .lerp(eyePart.position(), partialTick)
                    .add(0.0, eyePart.getBbHeight() * 0.5, 0.0);
            Vec3 dist = targetEyePos.subtract(eyePos);

            Vec3 forward = parentMob.getForward().normalize();
            Vec3 toTargetHorizontal = new Vec3(dist.x, 0, dist.z);
            double hLenSqr = toTargetHorizontal.lengthSqr();

            if (hLenSqr <= 1.0E-6 && forward.dot(toTargetHorizontal.normalize()) >= -0.02) {
                float yaw = (float) (Math.atan2(dist.z, dist.x));
                float pitch = (float) (Math.atan2(dist.y, Math.sqrt(dist.x * dist.x + dist.z * dist.z)));
                eyePart.stareYaw = (float) (Math.PI / 2 - yaw);
                eyePart.starePitch = pitch;
            }
        }

        float lerpYaw = eyePart.lerpYaw(partialTick);
        float lerpPitch = eyePart.lerpPitch(partialTick);

        float clampedYaw = Mth.clamp(lerpYaw, (float) Math.toRadians(-60), (float) Math.toRadians(60));
        float clampedPitch = Mth.clamp(lerpPitch, (float) Math.toRadians(-45), (float) Math.toRadians(45));

        bone.setRotY(clampedYaw);
        bone.setRotX(clampedPitch);
    }

    private int pickVariantIndex(WallOfFlesh wall, int x, int y) {
        String key = wall.getId() + ":" + x + ":" + y;
        Integer cached = cellVariantCache.get(key);
        if (cached != null) {
            return cached;
        }
        int totalWeight = 0;
        for (int weight : MODEL_WEIGHTS) {
            totalWeight += weight;
        }
        int randomWeight = wall.getRandom().nextInt(Math.max(totalWeight, 1));
        int selectedIndex = 0;
        int cumulativeWeight = MODEL_WEIGHTS[0];

        while (randomWeight >= cumulativeWeight && selectedIndex < MODEL_WEIGHTS.length - 1) {
            selectedIndex++;
            cumulativeWeight += MODEL_WEIGHTS[selectedIndex];
        }
        cellVariantCache.put(key, selectedIndex);
        return selectedIndex;
    }

    private void getOrBuildMergedRoot(WallOfFlesh animatable, GeoModel<WallOfFlesh> model) {
        int collisionWidth = Mth.floor(150 * 2 / animatable.gridSpacing);
        int collisionHeight = Mth.floor(150 * 2 / animatable.gridSpacing);
        final int gridX = animatable.getGridSizeX() + collisionWidth;
        final int gridY = animatable.getGridSizeY() + collisionHeight;

        // 检查缓存是否有效
        if (modelMerged) {
            return;
        }

        GeoBone baseRoot = model.getBone("All").orElse(null);
        if (baseRoot == null) {
            return;
        }

        GeoBone[] variants = new GeoBone[VARIANT_BONES.length];
        for (int i = 0; i < VARIANT_BONES.length; i++) {
            variants[i] = model.getBone(VARIANT_BONES[i]).orElse(null);
        }
        GeoBone eyeBone = model.getBone("bone_eye").orElse(null);
        GeoBone mouthBone = model.getBone("bone_mouth").orElse(null);

        baseRoot.getChildBones().clear();

        int halfGridX = gridX / 2;
        int halfGridY = gridY / 2;
        float size = CELL_SIZE;
        float halfSize = CELL_HALF;

        // 预计算常量以减少循环内的计算
        for (int x = -halfGridX; x < gridX - halfGridX; x++) {
            for (int y = -halfGridY; y < gridY - halfGridY; y++) {
                int variantIndex = pickVariantIndex(animatable, x, y);
                GeoBone variant = variantIndex >= 0 && variantIndex < variants.length ? variants[variantIndex] : null;

                if (variant != null) {
                    Vec3 offset = new Vec3(x * size + halfSize, y * size + halfSize, 0);
                    String boneName = "bone" + x + "_" + y;
                    GeoBone clone = copyBone(variant, offset, boneName, baseRoot);
                    baseRoot.getChildBones().add(clone);
                }
            }
        }

        List<Tuple<Integer, Vec3>> localOffsets = animatable.getLocalOffsets();
        WallOfFleshPart[] parts = animatable.getParts();
        for (WallOfFleshPart modelPart : parts) {
            if (modelPart == null || !modelPart.isAlive()) {
                continue;
            }

            int partIndex = -1;
            for (int i = 0; i < animatable.subEntities.size(); i++) {
                if (animatable.subEntities.get(i) == modelPart) {
                    partIndex = i;
                    break;
                }
            }
            if (partIndex < 0 || partIndex >= localOffsets.size()) {
                continue;
            }

            Vec3 localOffset = localOffsets.get(partIndex).getB();
            localOffset = animatable.rotateLocalOffset(localOffset).scale(16.0F);

            if (modelPart instanceof WallOfFleshEye && eyeBone != null) {
                GeoBone eyeClone = copyBone(eyeBone, localOffset, modelPart.name, baseRoot);
                baseRoot.getChildBones().add(eyeClone);
            } else if (modelPart instanceof WallOfFleshMouth && mouthBone != null) {
                GeoBone mouthClone = copyBone(mouthBone, localOffset, modelPart.name, baseRoot);
                baseRoot.getChildBones().add(mouthClone);
            }
        }

        modelMerged = true;
    }

    /**
     * geckolib 1.21.1 的构造器手动拷贝骨骼（递归拷贝子骨骼），使用指定的新名称。
     */
    private GeoBone copyBone(GeoBone template, Vec3 offset, String newName, GeoBone parent) {
        GeoBone copy = new GeoBone(parent,
                newName,
                template.getMirror(),
                template.getInflate(),
                template.shouldNeverRender(),
                template.getReset());

        float scaleX = template.getScaleX();
        float scaleY = template.getScaleY();
        float scaleZ = template.getScaleZ();
        float offsetX = (float) (offset.x * scaleX);
        float offsetY = (float) (offset.y * scaleY);

        copy.setPivotX(template.getPivotX() + offsetX);
        copy.setPivotY(template.getPivotY() + offsetY);
        copy.setPivotZ(template.getPivotZ());

        copy.setPosX(template.getPosX() + offsetX);
        copy.setPosY(template.getPosY() + offsetY);
        copy.setPosZ(template.getPosZ());

        copy.setRotX(template.getRotX());
        copy.setRotY(template.getRotY());
        copy.setRotZ(template.getRotZ());

        Double inflate = template.getInflate();
        float inflateScale = inflate == null ? 1.0f : (1.0f + (float)(inflate / 16.0));
        copy.updateScale(scaleX * inflateScale, scaleY * inflateScale, scaleZ * inflateScale);

        copy.getCubes().addAll(template.getCubes());

        if (!template.getChildBones().isEmpty()) {
            for (GeoBone child : template.getChildBones()) {
                GeoBone childCopy = copyBone(child, Vec3.ZERO, child.getName(), copy);
                copy.getChildBones().add(childCopy);
            }
        }

        return copy;
    }

    private boolean isGridBone(GeoBone bone) {
        String name = bone.getName();
        return name != null && GRID_BONE.matcher(name).matches();
    }

    @Override
    public GeoModel<WallOfFlesh> getGeoModel() {
        return super.getGeoModel();
    }

    @Override
    public boolean shouldRender(@Nonnull WallOfFlesh wall, @Nonnull Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    protected void applyRotations(WallOfFlesh animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        if (isShaking(animatable))
            rotationYaw += (float)(Math.cos(animatable.tickCount * 3.25d) * Math.PI * 0.4d);

        if (!animatable.hasPose(Pose.SLEEPING))
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - rotationYaw));

        if (animatable.deathTime <= 0 && animatable.isAutoSpinAttack()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f - animatable.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees((animatable.tickCount + partialTick) * -75f));
        }
    }

    @Override
    protected int getSkyLightLevel(@Nonnull WallOfFlesh entity, @Nonnull BlockPos pos) {
        Vec3 potionPos = new Vec3(pos.getX(), entity.level().getMaxBuildHeight()+1, pos.getZ());
        return super.getSkyLightLevel(entity, BlockPos.containing(potionPos));
    }

    @Override
    protected int getBlockLightLevel(@Nonnull WallOfFlesh entity, @Nonnull BlockPos pos) {
        Vec3 potionPos = new Vec3(pos.getX(), entity.level().getMaxBuildHeight()+1, pos.getZ());
        return super.getBlockLightLevel(entity, BlockPos.containing(potionPos));
    }

}
