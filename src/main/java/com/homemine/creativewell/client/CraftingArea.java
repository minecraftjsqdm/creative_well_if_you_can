package com.homemine.creativewell.client;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Client-side 3x3 crafting block of the creative panel.
 *
 * <p>It holds its own input and result containers, so the contents survive a layout change (a window
 * resize rebuilds the slots but not this object). The result is a preview: it is produced by the real
 * client-side {@code RecipeManager}, and taking it never consumes the grid.</p>
 */
public final class CraftingArea {
    /** Width and height of the crafting grid, in slots. */
    public static final int SIZE = 3;

    private final TransientCraftingContainer input;
    private final SimpleContainer result = new SimpleContainer(1);

    CraftingArea(AbstractContainerMenu menu) {
        this.input = new TransientCraftingContainer(menu, SIZE, SIZE);
    }

    public TransientCraftingContainer input() {
        return this.input;
    }

    public Container resultContainer() {
        return this.result;
    }

    public ItemStack result() {
        return this.result.getItem(0);
    }

    public boolean isInputSlot(Container container) {
        return container == this.input;
    }

    public boolean isResultSlot(Container container) {
        return container == this.result;
    }

    /** Re-runs recipe matching for the current grid contents. A malformed or missing recipe leaves it empty. */
    public void refresh(@Nullable Level level) {
        ItemStack crafted = ItemStack.EMPTY;
        if (level != null) {
            CraftingInput craftingInput = this.input.asCraftInput();
            if (!craftingInput.isEmpty()) {
                Optional<RecipeHolder<CraftingRecipe>> recipe =
                        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
                if (recipe.isPresent()) {
                    crafted = recipe.get().value().assemble(craftingInput, level.registryAccess());
                }
            }
        }
        this.result.setItem(0, crafted);
    }
}
