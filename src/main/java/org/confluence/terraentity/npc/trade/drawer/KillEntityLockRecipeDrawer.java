package org.confluence.terraentity.npc.trade.drawer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockRecipeDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.variant.KillEntityLock;
import org.jetbrains.annotations.NotNull;

public class KillEntityLockRecipeDrawer extends TradeLockRecipeDrawer {
    @Override
    public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!(lock instanceof KillEntityLock killEntityLock)) {
            return;
        }
        var size = getRecipeSize();
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_kill_entity"), x, y, size, size);
        drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY, "Kill: " + killEntityLock.entityType().getDescription());
    }
}
