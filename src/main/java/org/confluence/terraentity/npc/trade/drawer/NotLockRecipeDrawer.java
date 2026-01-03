package org.confluence.terraentity.npc.trade.drawer;

import net.minecraft.client.gui.GuiGraphics;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockRecipeDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.variant.NotLock;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NotLockRecipeDrawer extends TradeLockRecipeDrawer {
    @Override
    public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!(lock instanceof NotLock notLock)) {
            return;
        }
        drawRecipeLocks(List.of(notLock.lock()), guiGraphics, x, y, "!", mouseX, mouseY, "Any can be satisfied");
    }
}
