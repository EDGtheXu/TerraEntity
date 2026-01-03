package org.confluence.terraentity.npc.trade.drawer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockRecipeDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.variant.TimeLock;
import org.jetbrains.annotations.NotNull;

public class TimeLockRecipeDrawer extends TradeLockRecipeDrawer {
    @Override
    public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!(lock instanceof TimeLock timeLock)) {
            return;
        }
        var size = getRecipeSize();
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_time"), x, y, size, size);
        drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY,
                "Time: " + (timeLock.reverse() ? "Except " : "") + timeLock.from() + " to " + timeLock.to());
    }
}
