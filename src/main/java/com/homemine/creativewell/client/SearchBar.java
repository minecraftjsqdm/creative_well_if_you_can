package com.homemine.creativewell.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/**
 * The search field that sits in the panel's top strip.
 *
 * <p>Vanilla only shows a search box on tabs that opted into one, and only builds a search tree for those tabs
 * ({@code CreativeModeTabSearchRegistry} returns {@code null} keys otherwise). This field is shown on every tab and
 * hides that split: where vanilla has a tree the query is answered by it, so tooltip text matches just like in the
 * vanilla search tab, and plain category tabs fall back to a local match over the display name, the registry id and,
 * for {@code #}-prefixed queries, the item's tags.</p>
 */
public final class SearchBar {
    private final EditBox box;

    public SearchBar(Font font, Component hint) {
        this.box = new EditBox(font, 0, 0, 0, 9, hint);
        this.box.setMaxLength(50);
        this.box.setBordered(false);
        this.box.setTextColor(0xFFFFFF);
        this.box.setCanLoseFocus(true);
        this.box.setHint(hint);
    }

    /** The widget to hand to {@code addRenderableWidget}. */
    public EditBox widget() {
        return this.box;
    }

    /** Places the field inside the top strip of a freshly computed layout. */
    public void position(CreativeGridLayout layout, int leftPos, int topPos) {
        this.box.setX(leftPos + layout.searchX() + 3);
        this.box.setY(topPos + layout.searchY() + 3);
        this.box.setWidth(Math.max(20, layout.searchWidth() - 6));
    }

    public String query() {
        return this.box.getValue();
    }

    public void setQuery(String query) {
        this.box.setValue(query);
    }

    /** Called with the new text whenever it changes, including from {@link #setQuery}. */
    public void setResponder(Consumer<String> responder) {
        this.box.setResponder(responder);
    }

    public boolean isFocused() {
        return this.box.isFocused();
    }

    public void unfocus() {
        this.box.setFocused(false);
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        return this.box.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        return this.box.charTyped(codePoint, modifiers);
    }

    /**
     * The stacks of {@code source} that match {@code query}. An empty query is not a filter, it keeps the whole tab
     * visible, matching vanilla. Query handling is case-insensitive and every whitespace-separated word has to match.
     */
    public static List<ItemStack> resolve(
            @Nullable Minecraft minecraft, CreativeModeTab tab, Collection<ItemStack> source, String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return List.copyOf(source);
        }

        boolean byTag = trimmed.charAt(0) == '#';
        String term = (byTag ? trimmed.substring(1) : trimmed).trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            return List.copyOf(source);
        }

        if (minecraft != null) {
            ClientPacketListener connection = minecraft.getConnection();
            SessionSearchTrees.Key key = byTag
                    ? CreativeModeTabSearchRegistry.getTagSearchKey(tab)
                    : CreativeModeTabSearchRegistry.getNameSearchKey(tab);
            if (connection != null && key != null) {
                SessionSearchTrees trees = connection.searchTrees();
                SearchTree<ItemStack> tree = byTag ? trees.creativeTagSearch(key) : trees.creativeNameSearch(key);
                return List.copyOf(tree.search(term));
            }
        }

        return filterLocally(source, term, byTag);
    }

    private static List<ItemStack> filterLocally(Collection<ItemStack> source, String term, boolean byTag) {
        String[] words = term.split("\\s+");
        List<ItemStack> matches = new ArrayList<>();
        for (ItemStack stack : source) {
            if (!stack.isEmpty() && matches(stack, words, byTag)) {
                matches.add(stack);
            }
        }

        return matches;
    }

    private static boolean matches(ItemStack stack, String[] words, boolean byTag) {
        List<String> candidates = byTag ? tagNames(stack) : nameAndId(stack);
        for (String word : words) {
            boolean found = false;
            for (String candidate : candidates) {
                if (candidate.contains(word)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private static List<String> nameAndId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return List.of(stack.getHoverName().getString().toLowerCase(Locale.ROOT), id.toString());
    }

    private static List<String> tagNames(ItemStack stack) {
        return stack.getTags().map(tag -> tag.location().toString()).toList();
    }
}
