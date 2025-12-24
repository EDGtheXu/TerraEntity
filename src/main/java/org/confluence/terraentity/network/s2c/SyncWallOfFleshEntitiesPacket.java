package org.confluence.terraentity.network.s2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.utils.AdapterUtils;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SyncWallOfFleshEntitiesPacket implements CustomPacketPayload {
    public static final Type<SyncWallOfFleshEntitiesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "sync_wall_of_flesh_entities"));

    // 数据结构：包含ID、位置、朝向
    public record BossInfo(
        int id,
        double x, double y, double z, float yRot,
        ResourceKey<Level> dimension) {
    }

    // 客户端存储的肉山信息列表
    private static final List<BossInfo> CLIENT_BOSS_INFOS = new CopyOnWriteArrayList<>();

    private final List<BossInfo> bossInfos;

    public static List<BossInfo> getClientBossInfos() {
        return Collections.unmodifiableList(CLIENT_BOSS_INFOS);
    }

    public SyncWallOfFleshEntitiesPacket(List<BossInfo> bossInfos) {
        this.bossInfos = bossInfos;
    }

    // 定义 BossInfo 的编解码器
    private static final StreamCodec<ByteBuf, BossInfo> BOSS_INFO_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossInfo::id,
            ByteBufCodecs.DOUBLE, BossInfo::x,
            ByteBufCodecs.DOUBLE, BossInfo::y,
            ByteBufCodecs.DOUBLE, BossInfo::z,
            ByteBufCodecs.FLOAT, BossInfo::yRot,
            ResourceKey.streamCodec(Registries.DIMENSION), BossInfo::dimension,
            BossInfo::new);

    public static final StreamCodec<FriendlyByteBuf, SyncWallOfFleshEntitiesPacket> STREAM_CODEC = StreamCodec
            .composite(
                    BOSS_INFO_CODEC.apply(ByteBufCodecs.list()),
                    SyncWallOfFleshEntitiesPacket::bossInfos,
                    SyncWallOfFleshEntitiesPacket::new);

    public static void clear() {
        CLIENT_BOSS_INFOS.clear();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public List<BossInfo> bossInfos() {
        return bossInfos;
    }

    public static void handle(SyncWallOfFleshEntitiesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                CLIENT_BOSS_INFOS.clear();
                CLIENT_BOSS_INFOS.addAll(packet.bossInfos);
            }
        });
    }

    public static void sendToAllPlayers(List<BossInfo> bossInfos) {
        AdapterUtils.sendToAllPlayers(new SyncWallOfFleshEntitiesPacket(bossInfos));
    }
}
