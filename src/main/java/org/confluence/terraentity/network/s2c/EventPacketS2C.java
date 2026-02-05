package org.confluence.terraentity.network.s2c;

import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.confluence.lib.network.IPacketS2C;
import org.confluence.lib.util.LibStreamCodecUtils;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.init.TEAttachments;
import org.confluence.terraentity.utils.AdapterUtils;

import java.util.EnumMap;
import java.util.function.Consumer;

public record EventPacketS2C(TypeEnum typeEnum) implements IPacketS2C {
    public static final Type<EventPacketS2C> TYPE = new Type<>(TerraEntity.space("event_s2c"));
    public static final StreamCodec<FriendlyByteBuf, EventPacketS2C> STREAM_CODEC = LibStreamCodecUtils.fromEnum(TypeEnum.class)
            .map(EventPacketS2C::new, EventPacketS2C::typeEnum);
    private static final EnumMap<TypeEnum, Consumer<Player>> handlers = Util.make(new EnumMap<>(TypeEnum.class), map -> {
        map.put(TypeEnum.RESET_CRIMSON_STORM, (player) -> {
            if (player.isLocalPlayer()) {
                player.getData(TEAttachments.UNSYNC).triggerInvulnerableStorm(player);
            }
        });
    });

    @Override
    public void work(Player player) {
        work(typeEnum, player);
    }

    @Override
    public Type<EventPacketS2C> type() {
        return TYPE;
    }

    private static void work(TypeEnum typeEnum, Player player) {
        Consumer<Player> consumer = handlers.get(typeEnum);
        if (consumer == null) {
            TerraEntity.LOGGER.warn("Unknown client-bound event packet type: {}", typeEnum);
        } else {
            consumer.accept(player);
        }
    }

    public static void resetCrimsonStorm(ServerPlayer player) {
        AdapterUtils.sendToPlayer(player, new EventPacketS2C(TypeEnum.RESET_CRIMSON_STORM));
        work(TypeEnum.RESET_CRIMSON_STORM, player);
    }

    public enum TypeEnum {
        RESET_CRIMSON_STORM
    }
}
