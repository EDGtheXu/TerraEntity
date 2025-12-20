package org.confluence.terraentity.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.utils.AdapterUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 同步服务端的肉墙实体ID列表到客户端
 */
public class SyncWallOfFleshEntitiesPacket implements CustomPacketPayload {
    public static final Type<SyncWallOfFleshEntitiesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "sync_wall_of_flesh_entities")
    );
    
    // 客户端存储的肉墙实体ID集合（线程安全）
    private static final Set<Integer> CLIENT_WALL_OF_FLESH_IDS = new CopyOnWriteArraySet<>();
    
    private final List<Integer> wallOfFleshIds;  // 肉墙实体ID列表
    
    /**
     * 获取客户端已知的肉墙实体ID集合
     * @return 实体ID集合的不可变视图
     */
    public static Set<Integer> getClientWallOfFleshIds() {
        return Collections.unmodifiableSet(CLIENT_WALL_OF_FLESH_IDS);
    }

    public SyncWallOfFleshEntitiesPacket(List<Integer> wallOfFleshIds) {
        this.wallOfFleshIds = new ArrayList<>(wallOfFleshIds);
    }

    public SyncWallOfFleshEntitiesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.wallOfFleshIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.wallOfFleshIds.add(buf.readInt());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(wallOfFleshIds.size());
        for (Integer id : wallOfFleshIds) {
            buf.writeInt(id);
        }
    }

    public static final StreamCodec<FriendlyByteBuf, SyncWallOfFleshEntitiesPacket> STREAM_CODEC = 
        StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            SyncWallOfFleshEntitiesPacket::new
        );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncWallOfFleshEntitiesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 客户端处理：直接更新静态集合
            if (context.player().level().isClientSide()) {
                CLIENT_WALL_OF_FLESH_IDS.clear();
                CLIENT_WALL_OF_FLESH_IDS.addAll(packet.wallOfFleshIds);
            }
        });
    }

    /**
     * 发送给指定玩家
     */
    public static void sendToPlayer(ServerPlayer player, List<Integer> wallOfFleshIds) {
        AdapterUtils.sendToPlayer(player, new SyncWallOfFleshEntitiesPacket(wallOfFleshIds));
    }

    /**
     * 发送给所有玩家
     */
    public static void sendToAllPlayers(List<Integer> wallOfFleshIds) {
        AdapterUtils.sendToAllPlayers(new SyncWallOfFleshEntitiesPacket(wallOfFleshIds));
    }
}

