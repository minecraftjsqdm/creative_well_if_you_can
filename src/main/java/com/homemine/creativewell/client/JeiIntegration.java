package com.homemine.creativewell.client;

import com.homemine.creativewell.compat.CreativeWellJeiPlugin;
import com.homemine.creativewell.config.CreativeGridConfig;
import javax.annotation.Nullable;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.config.IJeiConfigCategory;
import mezz.jei.api.runtime.config.IJeiConfigFile;
import mezz.jei.api.runtime.config.IJeiConfigManager;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import net.neoforged.fml.ModList;

/**
 * Everything {@link CreativeGridScreen} needs to know about JEI, kept in one place.
 *
 * <p>JEI is a soft dependency: the mod must start without it, so no JEI type may be reachable from this class. All
 * real work lives in the private {@link Bridge}, which is a separate class file that the JVM only loads once a
 * method here is actually called, and every call is behind {@link #isLoaded()}.</p>
 *
 * <p>There is one more thing JEI does not expose through its public API: a way to hide its own overlay. The
 * "crowd JEI out" setting therefore only drops the reserved space to zero and lets the two overlap, which is the
 * documented fallback.</p>
 */
public final class JeiIntegration {
    private static final String JEI_MOD_ID = "jei";
    /** JEI has to keep at least six ingredient slots per row; below that the list is useless. */
    private static final int MIN_LIST_COLUMNS = 6;
    /** Side length of one of JEI's ingredient slots, in GUI pixels. */
    private static final int SLOT = 18;
    /** JEI's border and margin around a list. */
    private static final int LIST_OVERHEAD = 8;
    /** The category names JEI writes into {@code jei-client.ini}. */
    private static final String INGREDIENT_LIST = "ingredientList";
    private static final String MAX_COLUMNS = "maxColumns";

    private JeiIntegration() {
    }

    /** Space reserved at the two sides of the panel, in GUI pixels. */
    public record Reserves(int left, int right) {
        public static final Reserves NONE = new Reserves(0, 0);
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(JEI_MOD_ID);
    }

    /**
     * How much room to leave for JEI on either side of the panel. Zero whenever JEI is missing, its runtime is not
     * up yet, the ingredient list is hidden, or the user picked the "crowd JEI out" mode.
     */
    public static Reserves reserve() {
        if (!isLoaded() || CreativeGridConfig.ignoresJei()) {
            return Reserves.NONE;
        }

        return Bridge.reserve();
    }

    /** JEI's current search text, or {@code null} when there is nothing to read. */
    @Nullable
    public static String searchText() {
        return isLoaded() ? Bridge.searchText() : null;
    }

    /** Pushes {@code text} into JEI's search field. */
    public static void setSearchText(String text) {
        if (isLoaded()) {
            Bridge.setSearchText(text);
        }
    }

    /** A one-line summary of the JEI state, for the layout log. */
    public static String describe() {
        return isLoaded() ? Bridge.describe() : "jei=absent";
    }

    /** Only ever loaded on a JEI install, which is why the JEI types may appear here. */
    private static final class Bridge {
        static Reserves reserve() {
            IJeiRuntime runtime = CreativeWellJeiPlugin.runtime();
            if (runtime == null || !runtime.getIngredientListOverlay().isListDisplayed()) {
                return Reserves.NONE;
            }

            // JEI draws its ingredient list against the right edge and its public API exposes no bounds for it, so
            // the width comes from JEI's own config. Nothing is reserved on the left: the exclusion areas JEI
            // reports are the places JEI has to avoid, not places JEI occupies, so they say nothing about its size.
            return new Reserves(0, estimateListWidth(runtime));
        }

        /** The room JEI's ingredient list needs, from JEI's own config so it holds before JEI reports anything. */
        private static int estimateListWidth(IJeiRuntime runtime) {
            int columns = readInt(runtime, INGREDIENT_LIST, MAX_COLUMNS, MIN_LIST_COLUMNS);
            return Math.max(MIN_LIST_COLUMNS * SLOT, columns * SLOT + LIST_OVERHEAD);
        }

        private static int readInt(IJeiRuntime runtime, String categoryName, String valueName, int fallback) {
            IJeiConfigManager manager = runtime.getConfigManager();
            if (manager == null) {
                return fallback;
            }

            for (IJeiConfigFile file : manager.getConfigFiles()) {
                for (IJeiConfigCategory category : file.getCategories()) {
                    if (!categoryName.equals(category.getName())) {
                        continue;
                    }

                    for (IJeiConfigValue<?> value : category.getConfigValues()) {
                        if (valueName.equals(value.getName()) && value.getValue() instanceof Number number) {
                            return number.intValue();
                        }
                    }
                }
            }

            return fallback;
        }

        @Nullable
        static String searchText() {
            IJeiRuntime runtime = CreativeWellJeiPlugin.runtime();
            return runtime == null ? null : runtime.getIngredientFilter().getFilterText();
        }

        static void setSearchText(String text) {
            IJeiRuntime runtime = CreativeWellJeiPlugin.runtime();
            if (runtime != null) {
                runtime.getIngredientFilter().setFilterText(text);
            }
        }

        static String describe() {
            IJeiRuntime runtime = CreativeWellJeiPlugin.runtime();
            if (runtime == null) {
                return "jei=loaded runtime=pending";
            }

            boolean displayed = runtime.getIngredientListOverlay().isListDisplayed();
            return "jei=loaded runtime=ready listDisplayed=" + displayed
                    + " listWidth=" + (displayed ? estimateListWidth(runtime) : 0);
        }
    }
}
