package com.homemine.creativewell.compat;

import com.homemine.creativewell.CreativeWellMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

/**
 * InventoryProfilesNext paints its sorting buttons itself instead of adding them to {@code Screen#children},
 * so {@link com.homemine.creativewell.client.CreativeGridScreen} cannot drop them. IPN does offer a supported
 * opt-out: a JSON file under {@code config/inventoryprofilesnext/integrationHints/} that maps a screen class
 * to {@code "ignore": true}. IPN reads that folder when a world is joined, so writing it during client setup
 * is early enough, and it needs no compile-time dependency on IPN itself.
 */
@EventBusSubscriber(modid = CreativeWellMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreativeWellIpnCompat {
    private static final String SCREEN_CLASS = "com.homemine.creativewell.client.CreativeGridScreen";

    private CreativeWellIpnCompat() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("inventoryprofilesnext")) {
            return;
        }
        Path file = FMLPaths.CONFIGDIR.get()
                .resolve("inventoryprofilesnext")
                .resolve("integrationHints")
                .resolve("creativewell.json");
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\n  \"" + SCREEN_CLASS + "\": {\n    \"ignore\": true\n  }\n}\n",
                    StandardCharsets.UTF_8);
            CreativeWellMod.LOGGER.info("[creativewell] asked InventoryProfilesNext to leave {} alone", SCREEN_CLASS);
        } catch (IOException e) {
            CreativeWellMod.LOGGER.warn("[creativewell] could not write the InventoryProfilesNext hint file {}", file, e);
        }
    }
}
