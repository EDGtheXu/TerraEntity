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
import org.confluence.terraentity.network.s2c.SyncWallOfFleshEntitiesPacket;
import org.confluence.terraentity.integration.ModChecker;
import org.confluence.terraentity.item.BaseWhipItem;
import org.confluence.terraentity.item.YoyosItem;

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
    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            BrainTranslucent.render(event);
            WallOfFleshTranslucent.render(event);
            DebugBlocksHelper.Singleton().render(event);
            //            NPCRenderer.target.blitToScreen(100,100);
            NPCChatBubbleBuffer.getInstance().render(event);
//            if (!VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
//                if (VeilRenderSystem.drawLights(Minecraft.getInstance().level.getProfiler(), VeilRenderSystem.getCullingFrustum())) {
//                    VeilRenderSystem.compositeLights(Minecraft.getInstance().level.getProfiler());
//                } else {
//                    AdvancedFbo.unbind();
//                }
//            }

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

                // 使用从服务端同步的肉墙实体ID列表
                Set<Integer> syncedIds = SyncWallOfFleshEntitiesPacket.getClientWallOfFleshIds();
                Set<Integer> renderedIds = new java.util.HashSet<>(); // 记录已渲染的实体ID，避免重复渲染
                
                // 先尝试使用同步的实体ID列表进行渲染
                if (!syncedIds.isEmpty()) {
                    for (Integer id : syncedIds) {
                        Entity entity = level.getEntity(id);
                        if (entity instanceof WallOfFlesh wall) {
                            var renderer = mc.getEntityRenderDispatcher().getRenderer(wall);
                            if (renderer instanceof WallOfFleshRenderer wallRenderer) {
                                poseStack.pushPose();
                                poseStack.translate(wall.getX(), wall.getY(), wall.getZ());
                                float entityYaw = wall.getYRot();
                                int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(wall, partialTick);
                                wallRenderer.renderToTarget(wall, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                                poseStack.popPose();
                                renderedIds.add(wall.getId());
                            }
                        }
                    }
                }
                
                // 遍历所有实体，渲染那些已加载但可能不在同步列表中的实体（或同步列表为空时）
                for (Entity entity : level.getEntities().getAll()) {
                    if (entity instanceof WallOfFlesh wall && !renderedIds.contains(wall.getId())) {
                        var renderer = mc.getEntityRenderDispatcher().getRenderer(wall);
                        if (renderer instanceof WallOfFleshRenderer wallRenderer) {
                            poseStack.pushPose();
                            poseStack.translate(wall.getX(), wall.getY(), wall.getZ());
                            float entityYaw = wall.getYRot();
                            int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(wall, partialTick);
                            wallRenderer.renderToTarget(wall, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                            poseStack.popPose();
                        }
                    }
                }

                bufferSource.endBatch();
                poseStack.popPose();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
//            NPCChatBubbleBuffer.getInstance().refresh();

            isAfterSky = true;
        } else if(event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES){
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
