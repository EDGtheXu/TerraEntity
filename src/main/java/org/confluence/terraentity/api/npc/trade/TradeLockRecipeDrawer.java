package org.confluence.terraentity.api.npc.trade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class TradeLockRecipeDrawer {
    public static final TradeLockRecipeDrawer EMPTY = new TradeLockRecipeDrawer() {
        @Override
        public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {}
    };

    protected void drawRecipeLocks(List<ITradeLock> locks, GuiGraphics guiGraphics, int x, int y, String text, int mouseX, int mouseY, String tooltipText) {
        Font font = Minecraft.getInstance().font;
        int size = getRecipeSize();
        for (ITradeLock tradeLock : locks) {
            var drawer = tradeLock.getCodec().drawer();
            drawer.drawRecipe(tradeLock, guiGraphics, x + size, y, mouseX, mouseY);
            guiGraphics.drawString(font, text, x, y, 0xFFFFFF);
            drawTooltip(guiGraphics, x, y, font.width(text), size, mouseX, mouseY, tooltipText);
            y += size;
        }
    }

    protected void drawTooltip(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, String text) {
        if (mouseX > x && mouseX <= x + width && mouseY > y && mouseY <= y + height) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal(text), x, y);
        }
    }

    public abstract void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY);

    protected int getRecipeSize() {
        return 8;
    }
}
