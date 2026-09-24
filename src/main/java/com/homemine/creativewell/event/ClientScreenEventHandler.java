package com.homemine.creativewell.event;

import com.homemine.creativewell.CreativeWellMod;
import com.homemine.creativewell.client.CreativeGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = CreativeWellMod.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenEventHandler {
    private ClientScreenEventHandler() {}

    /**
     * Swaps the vanilla creative inventory for {@link CreativeGridScreen}. The event is not cancelled:
     * we only replace the screen instance, so {@code Minecraft#setScreen} keeps its normal
     * closing/removing/init flow (which also guarantees {@code popGuiLayer} can close us again).
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        event.setNewScreen(new CreativeGridScreen(player));
    }
}
