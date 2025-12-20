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

    private static final int SYNC_INTERVAL = 20; // 每20tick（1秒）同步一次

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 每SYNC_INTERVAL tick同步一次
        if (serverLevel.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }

        // 收集所有肉墙实体
        List<Integer> wallOfFleshIds = new ArrayList<>();
        for (Entity entity : serverLevel.getEntities().getAll()) {
            if (entity instanceof WallOfFlesh && entity.isAlive()) {
                wallOfFleshIds.add(entity.getId());
            }
        }

        // 如果有肉墙实体，发送给所有玩家
        if (!wallOfFleshIds.isEmpty()) {
            SyncWallOfFleshEntitiesPacket.sendToAllPlayers(wallOfFleshIds);
        }
    }
}

