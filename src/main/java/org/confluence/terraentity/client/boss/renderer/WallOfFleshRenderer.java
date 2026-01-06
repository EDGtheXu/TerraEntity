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
    private int cachedPartCount = -1; //记录缓存 part 数量
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
        // 检查缓存是否有效
        List<Tuple<Integer, Vec3>> localOffsets = animatable.getLocalOffsets();

        // 数据未同步则跳过
        if (localOffsets.isEmpty()) return;

        // 数量未变化则不重构
        if (modelMerged && cachedPartCount == localOffsets.size()) {
            return;
        }

        GeoBone baseRoot = model.getBone("All").orElse(null);
        if (baseRoot == null) {
            return;
        }

        // 清理旧骨骼
        baseRoot.getChildBones().clear();

        GeoBone[] variants = new GeoBone[VARIANT_BONES.length];
        for (int i = 0; i < VARIANT_BONES.length; i++) {
            variants[i] = model.getBone(VARIANT_BONES[i]).orElse(null);
        }
        GeoBone eyeBone = model.getBone("bone_eye").orElse(null);
        GeoBone mouthBone = model.getBone("bone_mouth").orElse(null);

        float geckoScale = 16.0f;
        int gridX = animatable.getGridSizeX();
        int gridY = animatable.getGridSizeY();
        float spacing = animatable.gridSpacing;

        // 绘制背景墙网格
        for (int ix = 0; ix < gridX; ix++) {
            for (int iy = 0; iy < gridY; iy++) {
                int vIdx = pickVariantIndex(animatable, ix, iy);
                GeoBone variant = (vIdx >= 0 && vIdx < variants.length) ? variants[vIdx] : null;

                if (variant != null) {
                    // 对齐中心点
                    double lx = (ix - gridX / 2.0) * spacing * geckoScale;
                    double ly = (iy - gridY / 2.0) * spacing * geckoScale;

                    // X 轴取反以对齐渲染器的局部坐标系
                    Vec3 renderPos = new Vec3(-lx, ly, 0);
                    String boneName = "grid_" + ix + "_" + iy;
                    baseRoot.getChildBones().add(copyBone(variant, renderPos, boneName, baseRoot));
                }
            }
        }

        // 直接遍历 localOffsets，确保位置与部件对应
        for (Tuple<Integer, Vec3> entry : localOffsets) {
            int partIndex = entry.getA();
            Vec3 rawOffset = entry.getB();

            if (partIndex >= 0 && partIndex < animatable.subEntities.size()) {
                WallOfFleshPart part = animatable.subEntities.get(partIndex);
                if (part == null || !part.isAlive()) continue;

                // X轴取反 和 缩放
                Vec3 renderOffset = new Vec3(-rawOffset.x * geckoScale, rawOffset.y * geckoScale, rawOffset.z * geckoScale);

                if (part instanceof WallOfFleshEye && eyeBone != null) {
                    baseRoot.getChildBones().add(copyBone(eyeBone, renderOffset, part.name, baseRoot));
                } else if (part instanceof WallOfFleshMouth && mouthBone != null) {
                    baseRoot.getChildBones().add(copyBone(mouthBone, renderOffset, part.name, baseRoot));
                }
            }
        }

        this.cachedPartCount = localOffsets.size();
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

        // 只有传入了有效 offset 的顶层骨骼（嘴巴根部）才使用 offset
        // 递归产生的子骨骼（牙齿等）必须保留 template 原始的 Pos 和 Pivot
        if (offset != Vec3.ZERO) {
            copy.setPosX((float) offset.x);
            copy.setPosY((float) offset.y);
            copy.setPosZ((float) offset.z);
            copy.setPivotX((float) offset.x);
            copy.setPivotY((float) offset.y);
            copy.setPivotZ((float) offset.z);
        } else {
            // 保留子骨骼在 Blockbench 里定义的相对位置
            copy.setPosX(template.getPosX());
            copy.setPosY(template.getPosY());
            copy.setPosZ(template.getPosZ());
            copy.setPivotX(template.getPivotX());
            copy.setPivotY(template.getPivotY());
            copy.setPivotZ(template.getPivotZ());
        }

        copy.setRotX(template.getRotX());
        copy.setRotY(template.getRotY());
        copy.setRotZ(template.getRotZ());
        copy.updateScale(template.getScaleX(), template.getScaleY(), template.getScaleZ());

        copy.getCubes().addAll(template.getCubes());

        if (!template.getChildBones().isEmpty()) {
            for (GeoBone child : template.getChildBones()) {
                // 递归时保持 Vec3.ZERO，这样子骨骼就会走上面的 else 分支，保留原始坐标
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
