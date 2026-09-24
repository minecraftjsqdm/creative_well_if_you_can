package com.homemine.creativewell.compat;

import com.homemine.creativewell.CreativeWellMod;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * The only class in this mod that is compiled against JEI. JEI discovers it through the {@code @JeiPlugin}
 * annotation, so it is loaded only on a JEI install, and everything else reaches JEI through
 * {@code client.JeiIntegration} instead of touching these types.
 *
 * <p>Being a plugin does not mean taking JEI over: the screen only asks where JEI is drawing so it can stay out of
 * the way, and optionally mirrors the search text.</p>
 */
@JeiPlugin
public final class CreativeWellJeiPlugin implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CreativeWellMod.MOD_ID, CreativeWellMod.MOD_ID);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        CreativeWellMod.LOGGER.info("[creativewell] JEI runtime is available");
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        CreativeWellMod.LOGGER.info("[creativewell] JEI runtime went away");
    }

    /** The live JEI runtime, or {@code null} while JEI has not finished starting. */
    @Nullable
    public static IJeiRuntime runtime() {
        return runtime;
    }
}
