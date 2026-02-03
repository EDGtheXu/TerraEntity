package org.confluence.terraentity.integration.sodiumextras;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.confluence.terraentity.TerraEntity;
import org.confluence.terraentity.init.entity.TEBossEntities;
import toni.sodiumextras.EmbyConfig;

import java.util.ArrayList;
import java.util.List;

public class SodiumExtrasEvents {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SodiumExtrasEvents::addEntitiesToWhitelist);
    }

    private static void addEntitiesToWhitelist() {
        try {
            List<String> list = new ArrayList<>(EmbyConfig.entityWhitelist.get());
            list.add(TEBossEntities.WALL_OF_FLESH.getRegisteredName());
            EmbyConfig.entityWhitelist.set(list);
        } catch (Exception e) {
            TerraEntity.LOGGER.error("Failed to add entities to Sodium Extras whitelist: {}", e.getMessage());
        }
    }
}
