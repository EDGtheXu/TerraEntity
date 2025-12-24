package org.confluence.terraentity.client.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.attachment.WeaponStorage;
import org.confluence.terraentity.client.buffer.DebugBlocksHelper;
import org.confluence.terraentity.client.buffer.DebugEntityHelper;
import org.confluence.terraentity.client.buffer.NPCChatBubbleBuffer;
import org.confluence.terraentity.client.gui.CustomizeBossHealthBar;
import org.confluence.terraentity.client.post.BossSpawnCameraManager;
import org.confluence.terraentity.client.boss.renderer.WallOfFleshRenderer;
import org.confluence.terraentity.client.post.BrainTranslucent;
import org.confluence.terraentity.client.post.TongueRenderer;
import org.confluence.terraentity.client.post.WallOfFleshTranslucent;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFlesh;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.network.s2c.SyncWallOfFleshEntitiesPacket;
import org.confluence.terraentity.integration.ModChecker;
import org.confluence.terraentity.item.BaseWhipItem;
import org.confluence.terraentity.item.YoyosItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.confluence.terraentity.TerraEntity.MODID;
import static org.confluence.terraentity.config.ClientConfig.bossBarStyle;

@EventBusSubscriber(modid = MODID,value = Dist.CLIENT)
public class RenderEvent {
    @SubscribeEvent
    public static void guiEvent( RenderGuiLayerEvent.Pre event){


    }

    @SubscribeEvent
    public static void drawBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
//        String name = ((TranslatableContents)event.getBossEvent().getName().getContents()).getKey().split("\\.",2)[1];
        if(bossBarStyle != 0){
            try{
                CustomizeBossHealthBar bar = CustomizeBossHealthBar.getBossHealthBars((event.getBossEvent().getName().getString()));
                if(bar!= null)
                    bar.render(event);
            }catch (Exception e){
                TerraEntity.LOGGER.warn(e.getLocalizedMessage());
            }

        }
    }

    public static boolean isIrisShader = false;
    public static boolean isAfterSky = false;

    // 静态的虚拟实体，用于渲染远处的肉山
    private static WallOfFlesh dummyBoss;

    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            BrainTranslucent.render(event);
            WallOfFleshTranslucent.render(event);
            DebugBlocksHelper.Singleton().render(event);
            NPCChatBubbleBuffer.getInstance().render(event);
            isAfterSky = false;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            TongueRenderer.renderFirstPerson(event);
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.getEntityRenderDispatcher() != null && mc.player != null) {
                net.minecraft.client.multiplayer.ClientLevel level = mc.level;
                if (level == null) {
                    return;
                }
                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
                float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);

                poseStack.pushPose();
                Vec3 cameraPos = event.getCamera().getPosition();
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                // 获取包含坐标信息的列表
                List<SyncWallOfFleshEntitiesPacket.BossInfo> infos = SyncWallOfFleshEntitiesPacket.getClientBossInfos();
                Set<Integer> renderedIds = new HashSet<>();

                if (!infos.isEmpty()) {

                    for (SyncWallOfFleshEntitiesPacket.BossInfo info : infos) {

                        if (info.dimension() != level.dimension()) {
                            continue;
                        }

                        Entity realEntity = level.getEntity(info.id());
                        WallOfFlesh entityToRender = null;

                        // 1. 如果客户端能找到真实的实体，直接用真实的
                        if (realEntity instanceof WallOfFlesh wall) {
                            entityToRender = wall;
                        }
                        // 2. 如果找不到（太远了），使用虚拟实体
                        else {
                            if (dummyBoss == null || dummyBoss.level() != level) {
                                dummyBoss = new WallOfFlesh(TEBossEntities.WALL_OF_FLESH.get(), level);
                            }
                            // 将虚拟实体移动到数据包指定的位置
                            dummyBoss.setPos(info.x(), info.y(), info.z());

                            float rot = info.yRot();
                            // 设置当前旋转
                            dummyBoss.setYRot(rot);
                            dummyBoss.yBodyRot = rot;
                            dummyBoss.yHeadRot = rot;
                            // 强制设置"上一帧"的旋转等于当前旋转-避免在视距边缘由于频繁切换实体渲染和虚拟渲染导致的旋转问题
                            dummyBoss.yRotO = rot;
                            dummyBoss.yBodyRotO = rot;
                            dummyBoss.yHeadRotO = rot;

                            dummyBoss.setId(info.id());
                            entityToRender = dummyBoss;
                        }

                        if (entityToRender != null) {
                            var renderer = mc.getEntityRenderDispatcher().getRenderer(entityToRender);
                            if (renderer instanceof WallOfFleshRenderer wallRenderer) {
                                poseStack.pushPose();
                                // 移动到目标位置 (info.x/y/z 是最新的服务端位置)
                                poseStack.translate(info.x(), info.y(), info.z());

                                // 渲染。注意：最后一个参数是光照。对于远处的 Boss，建议给满亮度 (15728880)，否则可能因为区块没加载而全黑
                                wallRenderer.renderToTarget(
                                        entityToRender,
                                        info.yRot(),
                                        partialTick,
                                        poseStack,
                                        bufferSource,
                                        15728880);
                                poseStack.popPose();
                                renderedIds.add(info.id());
                            }
                        }
                    }
                }

                // 3. 渲染原本就在附近的实体（防止闪烁或遗漏，但跳过已经渲染过的 ID）
                for (Entity entity : level.getEntities().getAll()) {
                    if (entity instanceof WallOfFlesh wall && !renderedIds.contains(wall.getId())) {
                        var renderer = mc.getEntityRenderDispatcher().getRenderer(wall);
                        if (renderer instanceof WallOfFleshRenderer wallRenderer) {
                            poseStack.pushPose();
                            poseStack.translate(wall.getX(), wall.getY(), wall.getZ());
                            float entityYaw = wall.getYRot();
                            int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(wall, partialTick);
                            wallRenderer.renderToTarget(wall, entityYaw, partialTick, poseStack, bufferSource,
                                    packedLight);
                            poseStack.popPose();
                        }
                    }
                }
                bufferSource.endBatch();
                poseStack.popPose();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            isAfterSky = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            isIrisShader = ModChecker.iris.isLoaded() && RenderSystem.getShader() instanceof ExtendedShader;
            DebugEntityHelper.INSTANCE.render(event);
        }
    }

    @SubscribeEvent
    public static void RenderLiving(RenderLivingEvent.Pre<Player, EntityModel<Player>> event) {
        TongueRenderer.render(event);
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if(BossSpawnCameraManager.INSTANCE.isAnimating()){
            event.setCanceled(true);
        }

        ItemStack stack = event.getItemStack();
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null){
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND && stack.getItem() instanceof BaseWhipItem item) {
            // 右手使用鞭子时取消渲染
            if (player.getCooldowns().isOnCooldown(item)) {
//                ci.cancel();
                float progress = (player.tickCount - BaseWhipItem.clickTime + event.getPartialTick());
                int cooldown = BaseWhipItem.cooldownTime;
                progress = Math.min(progress, cooldown) / cooldown;

                progress = progress > 0.5? 2 - progress * 2 : progress * 2;
                event.getPoseStack().translate(0, -progress  ,0);
            }
        }
//        Minecraft.getInstance().getBlockRenderer().renderBatched();
        if (event.getHand() == InteractionHand.MAIN_HAND) {

            Item item = player.getMainHandItem().getItem();
            // 使用有悠悠球时渲染手臂
            if (item instanceof YoyosItem && WeaponStorage.of(player).yoyosEntity != null) {

                PlayerRenderer playerrenderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
                PoseStack poseStack = event.getPoseStack();
                poseStack.pushPose();
                poseStack.translate(0.3, 0.1, -0.5);


//            poseStack.translate(0.5,-0.2,-1.2);
                var buffer = event.getMultiBufferSource();
                int packedLight = event.getPackedLight();
                float f = 1.0F;
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(f * -60.0F));
                poseStack.translate(f * 0.3F, -1.1F, 0.45F);
                poseStack.translate(0.8, 1.0, 0.3);

                playerrenderer.renderRightHand(poseStack, buffer, packedLight, player);

                poseStack.popPose();
            }
        }


    }


    @SubscribeEvent
    public static void calculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        BossSpawnCameraManager.INSTANCE.update(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));


    }
}
