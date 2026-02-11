package org.confluence.terraentity.integration;

import com.nyfaria.nyfsspiders.client.ClientEventHandlers;
import net.minecraft.world.entity.monster.Spider;
import net.neoforged.bus.api.SubscribeEvent;
import software.bernie.geckolib.event.GeoRenderEvent;

public class NyfsSpidersEvents {
    @SubscribeEvent
    public static void pre(GeoRenderEvent.Entity.Pre event) {
        if (event.getEntity() instanceof Spider bloodCrawler) {
            ClientEventHandlers.onPreRenderLiving(bloodCrawler, event.getPartialTick(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void post(GeoRenderEvent.Entity.Post event) {
        if (event.getEntity() instanceof Spider bloodCrawler) {
            ClientEventHandlers.onPostRenderLiving(bloodCrawler, event.getPartialTick(), event.getPoseStack(), event.getBufferSource());
        }
    }
}
