package com.homemine.creativewell.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
 *
 * <p><b>A search never blocks the render thread.</b> Those vanilla trees are built asynchronously, and building one
 * walks every stack of the tab and turns its <i>whole tooltip</i> into suffix-array entries: seconds of work on a
 * vanilla-sized tab, and minutes on a pack with hundreds of mods. {@code SessionSearchTrees#creativeNameSearch} and
 * {@code creativeTagSearch} hide that behind a {@link CompletableFuture#join()}, so asking them for a tree that is not
 * finished yet freezes the client for as long as the build runs - which is exactly what used to happen on the first
 * keystroke after opening the panel, because the trees are started by the vanilla screen's constructor and are
 * therefore never ready at that moment. The futures are read directly instead: a finished one is used, an unfinished
 * one makes this keystroke answer from the local match and asks {@link CreativeGridScreen} to run the search again
 * once the tree lands.</p>
 */
public final class SearchBar {
    /**
     * Lowercased hover name and registry id per item, remembered so a keystroke does not rebuild them for every
     * stack of the tab. Only the render thread touches this, and only through {@link #resolve}.
     */
    private static final Map<Item, List<String>> NAME_CACHE = new IdentityHashMap<>();
    /** Tag locations per item, remembered for the same reason. */
    private static final Map<Item, List<String>> TAG_CACHE = new IdentityHashMap<>();

    private final EditBox box;

    public SearchBar(Font font, Component hint) {
        this.box = new EditBox(font, 0, 0, 0, 9, hint);
        this.box.setMaxLength(50);
        this.box.setBordered(false);
        this.box.setTextColor(0xFFFFFF);
        this.box.setCanLoseFocus(true);
        this.box.setHint(hint);
    }

    /**
     * The outcome of one keystroke: the stacks to show, plus whether a search tree for the query was still being
     * built. {@code awaitingTree} is what tells the screen to run the search again when that tree becomes usable.
     */
    public record Result(List<ItemStack> items, boolean awaitingTree) {
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
     *
     * <p>Never blocks: an unfinished search tree falls back to {@link #filterLocally} and reports that through
     * {@link Result#awaitingTree()} instead of waiting for it.</p>
     */
    public static Result resolve(
            @Nullable Minecraft minecraft, CreativeModeTab tab, Collection<ItemStack> source, String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return new Result(List.copyOf(source), false);
        }

        boolean byTag = trimmed.charAt(0) == '#';
        String term = (byTag ? trimmed.substring(1) : trimmed).trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            return new Result(List.copyOf(source), false);
        }

        CompletableFuture<SearchTree<ItemStack>> future = searchTree(minecraft, tab, trimmed, byTag);
        if (future == null) {
            return new Result(filterLocally(source, term, byTag), false);
        }

        // A tree that is done is used, whatever state it ended in: an exceptional or cancelled one would have thrown
        // out of join() and taken the client down with it, and an empty one is simply no better than no tree at all.
        if (!isUsable(future)) {
            return new Result(filterLocally(source, term, byTag), true);
        }

        return new Result(List.copyOf(future.getNow(SearchTree.empty()).search(term)), false);
    }

    /**
     * True once the tree {@code query} needs is finished, so a grid that is showing the local match can switch over.
     * False for a query that needs no tree at all, which is also what keeps the screen from retrying forever.
     */
    public static boolean treeReady(@Nullable Minecraft minecraft, CreativeModeTab tab, String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        boolean byTag = trimmed.charAt(0) == '#';
        if ((byTag ? trimmed.substring(1) : trimmed).trim().isEmpty()) {
            return false;
        }

        CompletableFuture<SearchTree<ItemStack>> future = searchTree(minecraft, tab, trimmed, byTag);
        return future != null && isUsable(future);
    }

    /**
     * Drops the per-item caches. Called when the creative tabs are rebuilt and when the screen opens, because tag
     * data can have been replaced in between and the caches cannot tell.
     */
    public static void invalidateCaches() {
        NAME_CACHE.clear();
        TAG_CACHE.clear();
    }

    /**
     * The future behind the tree {@code query} would use, or {@code null} when this query is not answered by a tree:
     * no client connection yet, an empty query, or a tab that never opted into a search bar.
     */
    @Nullable
    private static CompletableFuture<SearchTree<ItemStack>> searchTree(
            @Nullable Minecraft minecraft, CreativeModeTab tab, String trimmed, boolean byTag) {
        if (minecraft == null || minecraft.getConnection() == null) {
            return null;
        }

        SessionSearchTrees.Key key = byTag
                ? CreativeModeTabSearchRegistry.getTagSearchKey(tab)
                : CreativeModeTabSearchRegistry.getNameSearchKey(tab);
        if (key == null) {
            return null;
        }

        return byTag
                ? CreativeModeTabSearchRegistry.getTagSearchTree(key)
                : CreativeModeTabSearchRegistry.getNameSearchTree(key);
    }

    private static boolean isUsable(CompletableFuture<?> future) {
        return future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
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
        Item item = stack.getItem();
        // A stack carrying a name of its own cannot use the per-item cache, because that name belongs to the stack
        // rather than to the item. Everything else is remembered.
        if (stack.has(DataComponents.ITEM_NAME) || stack.has(DataComponents.CUSTOM_NAME)) {
            return List.of(hoverName(stack), idOf(item));
        }

        return NAME_CACHE.computeIfAbsent(item, key -> List.of(hoverName(stack), idOf(item)));
    }

    private static String hoverName(ItemStack stack) {
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static String idOf(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }

    private static List<String> tagNames(ItemStack stack) {
        return TAG_CACHE.computeIfAbsent(
                stack.getItem(), key -> stack.getTags().map(tag -> tag.location().toString()).toList());
    }
}
