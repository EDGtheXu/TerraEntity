package org.confluence.terraentity.npc.trade.drawer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.api.npc.trade.ITradeLock;
import org.confluence.terraentity.api.npc.trade.TradeLockRecipeDrawer;
import org.confluence.terraentity.registries.npc_trade_lock.variant.BiomeLock;
import org.jetbrains.annotations.NotNull;

public class BiomeLockRecipeDrawer extends TradeLockRecipeDrawer {
    @Override
    public void drawRecipe(@NotNull ITradeLock lock, GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        if (!(lock instanceof BiomeLock biomeLock)) {
            return;
        }
        var size = getRecipeSize();
        for (var biomeResource : biomeLock.values()) {
            guiGraphics.blitSprite(getBiomeSprite(biomeResource), x, y, size, size);
            drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY, "Biome: " + biomeResource.location());
            x += size;
        }

        for (var biomeResource : biomeLock.tags()) {
            guiGraphics.blitSprite(getBiomeSpriteByTag(biomeResource), x, y, size, size);
            drawTooltip(guiGraphics, x, y, size, size, mouseX, mouseY, "Biome Tag: " + biomeResource.location());
            x += size;
        }
    }

    private ResourceLocation getBiomeSpriteByTag(TagKey<Biome> biomeResource) {
        return ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_biome_tag");
    }

    private ResourceLocation getBiomeSprite(ResourceKey<Biome> biomeResource) {
        return ResourceLocation.fromNamespaceAndPath(TerraEntity.MODID, "shop_lock_biome");
    }
}
