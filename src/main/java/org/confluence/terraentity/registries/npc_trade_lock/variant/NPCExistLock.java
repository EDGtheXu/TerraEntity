package org.confluence.terraentity.registries.npc_trade_lock.variant;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.npc.trade.ITradeHolder;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockDrawer;
import org.confluence.terraentity.entity.npc.AbstractTerraNPC;
import org.confluence.terraentity.init.TEAi;
import org.confluence.terraentity.registries.npc_trade_lock.TradeLockProvider;
import org.confluence.terraentity.registries.npc_trade_lock.TradeLockProviderTypes;

import java.util.List;

public record NPCExistLock(EntityType<?> entityType) implements ITradeLock, TradeLockDrawer {

    public static MapCodec<NPCExistLock> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().xmap(NPCExistLock::new, NPCExistLock::entityType).fieldOf("entity_type");

    @Override
    public boolean canTrade(Player player, ITradeHolder npc, int index) {
        if(npc instanceof AbstractTerraNPC mob){
            List<AbstractTerraNPC> nearbyNPCs = mob.getBrain().getMemory(TEAi.MemoryModules.NEARBY_NPC.get()).orElse(List.of());
            return nearbyNPCs.stream().anyMatch(e->e.getType() == entityType);
        }
        return false;
    }

    @Override
    public TradeLockProvider getCodec() {
        return TradeLockProviderTypes.NPC_EXIST_LOCK.get();
    }

    @Override
    public void drawRecipe(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        var size = getRecipeSize();
        var item = DeferredSpawnEggItem.deferredOnlyById(entityType());
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_npc_exist"), x, y, size, size);
        drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY,  "NPC Exist: " + entityType().getDescription());
    }
}
