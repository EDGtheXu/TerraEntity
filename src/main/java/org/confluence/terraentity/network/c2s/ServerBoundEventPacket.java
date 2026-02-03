package org.confluence.terraentity.network.c2s;

import com.google.common.collect.ImmutableMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.confluence.lib.util.ScheduledForMove;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.item.ILeftClickStateItem;
import org.confluence.terraentity.attachment.WeaponStorage;
import org.confluence.terraentity.entity.npc.AbstractTerraNPC;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TENpcEntities;
import org.confluence.terraentity.integration.ModChecker;
import org.confluence.terraentity.integration.curios.CuriosHelper;
import org.confluence.terraentity.mixed.IPlayer;
import org.confluence.terraentity.utils.AdapterUtils;
import org.confluence.terraentity.utils.TEUtils;

import java.util.EnumMap;
import java.util.function.Consumer;

@ScheduledForMove(since = "1.2.0", inVersion = "2.0.0")
public class ServerBoundEventPacket implements CustomPacketPayload {
    private enum TypeEnum {
        SUMMON_SKELETRON,
        MOUSE_LEFT_CLICK,
        MOUSE_RELEASE,
        WHEEL_UP,
        WHEEL_DOWN,
        RIDE_OR_LEAVE
    }

    static final EnumMap<TypeEnum, Consumer<Player>> handlers = new EnumMap<>(ImmutableMap.<TypeEnum, Consumer<Player>>builder()
            .put(TypeEnum.SUMMON_SKELETRON, (player) -> {
                if (player.level().isClientSide) return;
                Vec3 pos = player.position();
                if (IPlayer.of(player).terra_entity$getTradeHolder() instanceof AbstractTerraNPC npc && npc.getType() == TENpcEntities.OLD_MAN.get()) {
                    confluenceHook(npc);
                    TEUtils.spawnEntity(TEBossEntities.SKELETRON.get(),
                            (ServerLevel) player.level(),
                            pos.add(TEUtils.sphere(10, (float) Math.random() * 3.14F, (float) Math.random() * 3.14F))
                    );
                }
            })
            .put(TypeEnum.MOUSE_LEFT_CLICK, (player) -> {
                WeaponStorage.of(player).leftClicking = true;
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ILeftClickStateItem item) {
                    item.onLeftClick(player, stack);
                }
            })
            .put(TypeEnum.MOUSE_RELEASE, (player) -> {
                WeaponStorage.of(player).leftClicking = false;
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ILeftClickStateItem item) {
                    item.onLeftRelease(player, stack);
                }
            })
            .put(TypeEnum.WHEEL_UP, (player) -> {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ILeftClickStateItem item) {
                    item.onWhellScroll(player, stack, 1);
                }
            })
            .put(TypeEnum.WHEEL_DOWN, (player) -> {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ILeftClickStateItem item) {
                    item.onWhellScroll(player, stack, -1);
                }
            })
            .put(TypeEnum.RIDE_OR_LEAVE, (player) -> {
                if (ModChecker.curios.isLoaded()) {
                    CuriosHelper.rideOrLeave(player);
                }
            })

            .build());

    private final TypeEnum _type;

    public static final Type<ServerBoundEventPacket> TYPE = new Type<>(TerraEntity.fromSpaceAndPath(TerraEntity.MODID, "server_bound_event_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerBoundEventPacket> STREAM_CODEC = CustomPacketPayload.codec(ServerBoundEventPacket::write, ServerBoundEventPacket::new);

    ServerBoundEventPacket(TypeEnum type) {
        this._type = type;
    }

    ServerBoundEventPacket(FriendlyByteBuf buf) {
        this._type = buf.readEnum(TypeEnum.class);
    }


    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(_type);
    }

    public static void handle(ServerBoundEventPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> work(packet._type, context.player()));
    }

    private static void work(TypeEnum type, Player player) {
        Consumer<Player> consumer = handlers.get(type);
        if (consumer == null) {
            TerraEntity.LOGGER.warn("Unknown server-bound event packet type: {}", type);
        } else {
            consumer.accept(player);
        }
    }

    private static void confluenceHook(AbstractTerraNPC npc) {
        npc.discard(); // 这样不会肢解，但是不会触发死亡事件所以需要mixin
    }

    @Override
    public Type<ServerBoundEventPacket> type() {
        return TYPE;
    }

    public static void summonSkeletron(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.SUMMON_SKELETRON));
        work(TypeEnum.SUMMON_SKELETRON, player);
    }

    public static void mouseLeftClick(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.MOUSE_LEFT_CLICK));
        work(TypeEnum.MOUSE_LEFT_CLICK, player);
    }

    public static void mouseRelease(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.MOUSE_RELEASE));
        work(TypeEnum.MOUSE_RELEASE, player);
    }

    public static void wheelUp(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.WHEEL_UP));
        work(TypeEnum.WHEEL_UP, player);
    }

    public static void wheelDown(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.WHEEL_DOWN));
        work(TypeEnum.WHEEL_DOWN, player);
    }

    public static void rideOrLeave(Player player) {
        AdapterUtils.sendToServer(new ServerBoundEventPacket(TypeEnum.RIDE_OR_LEAVE));
        work(TypeEnum.RIDE_OR_LEAVE, player);
    }
}
