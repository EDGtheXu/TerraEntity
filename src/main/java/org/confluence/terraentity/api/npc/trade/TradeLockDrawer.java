package org.confluence.terraentity.api.npc.trade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface TradeLockDrawer {
    default void drawRecipeLocks(List<ITradeLock> locks, GuiGraphics guiGraphics, int x, int y, String text, int mouseX, int mouseY, String tooltipText) {
        Font font = Minecraft.getInstance().font;
        int size = getRecipeSize();
        for (ITradeLock tradeLock : locks) {
            if (!(tradeLock instanceof TradeLockDrawer drawer)) {
                continue;
            }
            drawer.drawRecipe(guiGraphics, x + size, y, mouseX, mouseY);
            guiGraphics.drawString(font, text, x, y, 0xFFFFFF);
            drawer.drawTooltip(guiGraphics, x, y, font.width(text), size, mouseX, mouseY, tooltipText);
            y += size;
        }
    }

    default void drawTooltip(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, String text) {
        if (mouseX > x && mouseX <= x + width && mouseY > y && mouseY <= y + height) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal(text), x, y);
        }
    }

    void drawRecipe(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY);

    default int getRecipeSize() {
        return 8;
    }
}
