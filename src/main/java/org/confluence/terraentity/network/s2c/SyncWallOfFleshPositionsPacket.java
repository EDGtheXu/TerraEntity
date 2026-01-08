package org.confluence.terraentity.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFlesh;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshEye;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshMouth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SyncWallOfFleshPositionsPacket implements CustomPacketPayload {
    public static final Type<SyncWallOfFleshPositionsPacket> TYPE = new Type<>(TerraEntity.fromSpaceAndPath(TerraEntity.MODID, "sync_wall_of_flesh_positions"));

    private final int wallOfFleshId;
    private final List<Vec3> eyePositions;
    private final List<Vec3> mouthPositions;

    public SyncWallOfFleshPositionsPacket(int wallOfFleshId, List<Vec3> eyePositions, List<Vec3> mouthPositions) {
        this.wallOfFleshId = wallOfFleshId;
        this.eyePositions = new ArrayList<>(eyePositions);
        this.mouthPositions = new ArrayList<>(mouthPositions);
    }

    public SyncWallOfFleshPositionsPacket(FriendlyByteBuf buf) {
        this.wallOfFleshId = buf.readInt();

        int eyeCount = buf.readInt();
        this.eyePositions = new ArrayList<>(eyeCount);
        for (int i = 0; i < eyeCount; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            this.eyePositions.add(new Vec3(x, y, z));
        }

        int mouthCount = buf.readInt();
        this.mouthPositions = new ArrayList<>(mouthCount);
        for (int i = 0; i < mouthCount; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            this.mouthPositions.add(new Vec3(x, y, z));
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(wallOfFleshId);

        buf.writeInt(eyePositions.size());
        for (Vec3 pos : eyePositions) {
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
        }

        buf.writeInt(mouthPositions.size());
        for (Vec3 pos : mouthPositions) {
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncWallOfFleshPositionsPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            SyncWallOfFleshPositionsPacket::new
        );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncWallOfFleshPositionsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();

            Entity wallEntity = level.getEntity(packet.wallOfFleshId);
            if (wallEntity instanceof WallOfFlesh wall && wall.subEntities.isEmpty()) {
                for (int i = 0; i < packet.eyePositions.size(); i++) {
                    Vec3 pos = packet.eyePositions.get(i);
                    WallOfFleshEye eye = new WallOfFleshEye(wall, "WallOfFleshEye" + (i + 1), 4.0f, 4.0f);
                    wall.addChild(eye, pos);
                }

                for (int i = 0; i < packet.mouthPositions.size(); i++) {
                    Vec3 pos = packet.mouthPositions.get(i);
                    WallOfFleshMouth mouth = new WallOfFleshMouth(wall, "WallOfFleshMouth" + i, 3.0f, 4.0f);
                    wall.addChild(mouth, pos);
                }
            }
        });
    }
}
