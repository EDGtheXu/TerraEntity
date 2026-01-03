package org.confluence.terraentity.npc.trade.drawer;

import net.minecraft.client.gui.GuiGraphics;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockRecipeDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.variant.OrLock;
import org.jetbrains.annotations.NotNull;

public class OrLockRecipeDrawer extends TradeLockRecipeDrawer {
    @Override
    public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!(lock instanceof OrLock orLock)) {
            return;
        }
        drawRecipeLocks(orLock.locks(), guiGraphics, x, y, "|", mouseX, mouseY, "None should be satisfied");
    }
}
