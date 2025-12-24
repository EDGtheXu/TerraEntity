package org.confluence.terraentity.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFlesh;
import org.confluence.terraentity.network.s2c.SyncWallOfFleshEntitiesPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端tick事件处理器
 * 定期将服务端的肉墙实体同步到客户端
 */
@EventBusSubscriber(modid = TerraEntity.MODID)
public class ServerTickEvent {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        serverLevel.getGameTime();

        List<SyncWallOfFleshEntitiesPacket.BossInfo> infos = new ArrayList<>();

        // 收集发送肉墙实体数据
        for (Entity entity : serverLevel.getEntities().getAll()) {
            if (entity instanceof WallOfFlesh && entity.isAlive()) {
                infos.add(new SyncWallOfFleshEntitiesPacket.BossInfo(
                        entity.getId(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        entity.getYRot(),
                        entity.level().dimension()));
            }
        }

        if (!infos.isEmpty()) {
            SyncWallOfFleshEntitiesPacket.sendToAllPlayers(infos);
        }
    }
}

