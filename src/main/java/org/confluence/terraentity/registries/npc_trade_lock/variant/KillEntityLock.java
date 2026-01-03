package org.confluence.terraentity.registries.npc_trade_lock.variant;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.npc.trade.ITradeHolder;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.TradeLockProvider;
import org.confluence.terraentity.registries.npc_trade_lock.TradeLockProviderTypes;

public record KillEntityLock(EntityType<?> entityType) implements ITradeLock, TradeLockDrawer {

    public static final MapCodec<KillEntityLock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(KillEntityLock::entityType)
    ).apply(instance, KillEntityLock::new));

    public static KillEntityLock create(EntityType<?> entityType){
        return new KillEntityLock(entityType);
    }

    @Override
    public boolean canTrade(Player player, ITradeHolder npc, int index) {
        if(player.isLocalPlayer()){
            return ((LocalPlayer)player).getStats().getValue(Stats.ENTITY_KILLED.get(entityType)) > 0;
        }
        return ((ServerPlayer)player).getStats().getValue(Stats.ENTITY_KILLED.get(entityType)) > 0;
    }

    @Override
    public TradeLockProvider getCodec() {
        return TradeLockProviderTypes.KILL_ENTITY_LOCK.get();
    }

    @Override
    public void drawRecipe(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        var size = getRecipeSize();
        var item = DeferredSpawnEggItem.deferredOnlyById(entityType());
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_kill_entity"), x, y, size, size);
        drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY,  "Kill: " + entityType().getDescription());
    }
}
