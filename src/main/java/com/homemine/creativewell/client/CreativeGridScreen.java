package com.homemine.creativewell.client;

import com.homemine.creativewell.CreativeWellMod;
import com.homemine.creativewell.config.CreativeGridConfig;
import com.homemine.creativewell.config.CreativeGridConfig.JeiLinkMode;
import com.homemine.creativewell.config.CreativeGridConfig.SearchShareMode;
import com.homemine.creativewell.config.CreativeGridConfig.ShiftClickAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Unit;

/**
 * Replaces the vanilla creative inventory. The picker is a dynamic grid: the number of columns and rows follows the
 * window size ({@link CreativeGridLayout}), the grid scrolls when a tab holds more stacks than fit, and clicking a
 * slot picks the item up exactly like vanilla (left click grabs a stack, right click splits, shift left-click fills
 * the carried stack, middle click clones, {@code Q} drops, number keys swap into the hotbar).
 *
 * <p>{@link TabListPanel} draws the tabs down the left edge and {@link SearchBar} filters whatever tab is selected
 * from the strip along the top. Under the grid sits a client-only 3x3 crafting block with its result slot, and under
 * that the {@link RelicButton} that switches over to Curios. To the right of the crafting block is the player's own
 * backpack, hotbar, armor and offhand.</p>
 */
public class CreativeGridScreen extends AbstractContainerScreen<CreativeItemPickerMenu> {
    private static final int PANEL_COLOR = 0xF0101010;
    private static final int BORDER_COLOR = 0xFF909090;
    private static final int SLOT_COLOR = 0xFF202020;
    private static final int SLOT_BORDER_COLOR = 0xFF383838;
    private static final int SEARCH_FIELD_COLOR = 0xFF151515;
    private static final int SEARCH_FIELD_FOCUS_COLOR = 0xFF606060;
    private static final int TAB_BAR_COLOR = 0xFF2C2C2C;
    private static final int SEPARATOR_COLOR = 0x60FFFFFF;
    private static final int SCROLL_TRACK_COLOR = 0xFF151515;
    private static final int SCROLL_THUMB_COLOR = 0xFF9A9A9A;
    private static final int SCROLL_THUMB_MIN_HEIGHT = 15;
    private static final int ARROW_COLOR = 0xFFBFBFBF;
    /** The cross of the destroy slot, lifted pixel by pixel out of vanilla's {@code tab_inventory.png}. */
    private static final ResourceLocation DESTROY_ICON =
            ResourceLocation.fromNamespaceAndPath(CreativeWellMod.MOD_ID, "textures/gui/destroy_x.png");

    /** Selected tab. Static so the choice survives closing and reopening the screen, matching vanilla. */
    private static CreativeModeTab selectedTab = CreativeModeTabs.getDefaultTab();

    /** Search text of every tab, read when {@link SearchShareMode#PER_TAB} is active. Static for the same reason. */
    private static final Map<CreativeModeTab, String> TAB_QUERIES = new HashMap<>();
    /** The one search text every tab shares when {@link SearchShareMode#GLOBAL} is active. */
    private static String sharedQuery = "";

    private final boolean displayOperatorCreativeTab;
    private final TabListPanel tabList = new TabListPanel();
    private final RelicButton relicButton = new RelicButton();
    private final SettingsPanel settingsPanel = new SettingsPanel(this::onSettingsChanged);

    private CreativeGridLayout layout =
            CreativeGridLayout.compute(320, 240, CreativeGridConfig.DEFAULT_MAX_COLUMNS, 0, 0);
    /** Mirrors the search text so it survives a resize, which rebuilds the widgets through {@link #init()}. */
    private String query = "";
    /** Set when a setting changed; the rebuild is deferred to {@link #containerTick()} so nothing moves mid-render. */
    private boolean settingsDirty;
    /** Suppresses the search responder while the field is written to from code, so a restore is not an edit. */
    private boolean updatingSearchText;
    /** Last text seen in JEI's search field, so only real changes are copied across. */
    @Nullable
    private String lastJeiText;
    @Nullable
    private SearchBar searchBar;
    @Nullable
    private CreativeModeTab hoveredTab;
    @Nullable
    private CreativeInventoryListener listener;
    private boolean hasClickedOutside;
    /** Foreign class names already reported, so the per-frame purge logs each of them exactly once. */
    private final java.util.Set<String> reportedForeignWidgets = new java.util.HashSet<>();
    /**
     * The widgets {@link #init()} built for this screen itself, compared by identity. Anything else found in
     * {@link #children()} was hung there by another mod and is dropped, whichever mod that is.
     */
    private final java.util.Set<GuiEventListener> ownWidgets =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /**
     * The renderables {@link #init()} built for this screen itself, compared by identity. A mod that keeps drawing
     * its own button hangs it on the screen with {@code addRenderableOnly} rather than {@code addRenderableWidget},
     * which puts it in {@link #renderables} alone and so leaves it invisible to {@link #children()}.
     */
    private final java.util.Set<Renderable> ownRenderables =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public CreativeGridScreen(LocalPlayer player) {
        super(new CreativeItemPickerMenu(player), player.getInventory(), Component.translatable("creativewell.screen.title"));
        // Vanilla does this in its own constructor so the carried stack is shared with the inventory menu.
        player.containerMenu = this.menu;
        this.displayOperatorCreativeTab = net.minecraft.client.Minecraft.getInstance().options.operatorItemsTab().get();
        // The screen's own Minecraft reference is only assigned in init(), so the tab build has to be told the player.
        this.rebuildTabContents(player);
    }

    @Override
    protected void init() {
        if (this.minecraft.gameMode == null || !this.minecraft.gameMode.hasInfiniteItems()) {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            return;
        }

        JeiIntegration.Reserves reserves = JeiIntegration.reserve();
        this.layout = CreativeGridLayout.compute(
                this.width, this.height, CreativeGridConfig.resolvedMaxColumns(), reserves.left(), reserves.right());
        this.imageWidth = this.layout.panelWidth();
        this.imageHeight = this.layout.panelHeight();
        super.init();

        // Slots are placed panel-relative and the panel is centred inside what JEI left over, so the layout has the
        // final word on where the panel sits. Moving it after super.init() carries every slot along.
        this.leftPos = this.layout.x();
        this.topPos = this.layout.y();

        this.menu.applyLayout(this.layout);
        CreativeWellMod.LOGGER.info("[creativewell] panel {}x{} at {},{} grid {}x{} cap={} reserves={}/{}",
                this.imageWidth, this.imageHeight, this.leftPos, this.topPos,
                this.layout.columns(), this.layout.rows(), CreativeGridConfig.resolvedMaxColumns(),
                reserves.left(), reserves.right());
        CreativeWellMod.LOGGER.info("[creativewell] {}", JeiIntegration.describe());

        // The search field is rebuilt with the screen, so the text has to be carried over by hand.
        this.searchBar = new SearchBar(this.font, Component.translatable("creativewell.search.hint"));
        this.searchBar.position(this.layout, this.leftPos, this.topPos);
        this.searchBar.setQuery(this.query);
        this.searchBar.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBar.widget());

        this.tabList.updatePosition(this.leftPos, this.topPos, this.imageHeight);
        this.relicButton.position(this.layout, this.leftPos, this.topPos);
        this.settingsPanel.position(this.layout, this.leftPos, this.topPos,
                this.width, this.height, reserves.left(), reserves.right());
        this.tabList.refreshTabs();
        if (this.tabList.indexOf(selectedTab) < 0) {
            selectedTab = this.fallbackTab();
        }

        this.tabList.ensureVisible(selectedTab);
        this.applyTab(selectedTab);

        this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
        this.listener = new CreativeInventoryListener(this.minecraft);
        this.minecraft.player.inventoryMenu.addSlotListener(this.listener);

        // Snapshot taken here on purpose: everything above is ours, everything hung on the screen from now on (the
        // ScreenEvent.Init.Post handlers of other mods) is not, and purgeForeignWidgets() drops exactly that set.
        this.ownWidgets.clear();
        this.ownWidgets.addAll(this.children());
        this.ownRenderables.clear();
        this.ownRenderables.addAll(this.renderables);
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft.player != null && this.listener != null) {
            this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
            this.listener = null;
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.minecraft.player == null) {
            return;
        }

        if (this.settingsDirty) {
            this.settingsDirty = false;
            // A setting can change the layout itself, so the widgets are rebuilt rather than nudged.
            this.rebuildWidgets();
            return;
        }

        this.refreshInvalidatedTabs();
        this.syncJeiSearch();

        if (this.minecraft.gameMode == null || !this.minecraft.gameMode.hasInfiniteItems()) {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
        }
    }

    /** A setting was written to the config: the screen rebuilds itself on the next tick. */
    private void onSettingsChanged() {
        this.settingsDirty = true;
    }

    /**
     * Copies text between JEI's search field and this screen's own, as far as {@link JeiLinkMode} asks for. Nothing
     * is done while the two already agree, which is also what keeps a two-way link from echoing forever.
     */
    private void syncJeiSearch() {
        if (CreativeGridConfig.jeiLinkMode() == JeiLinkMode.OFF || !JeiIntegration.isLoaded()) {
            return;
        }

        String jeiText = JeiIntegration.searchText();
        if (jeiText == null || jeiText.equals(this.lastJeiText)) {
            return;
        }

        this.lastJeiText = jeiText;
        if (jeiText.equals(this.query)) {
            return;
        }

        this.query = jeiText;
        rememberQuery(selectedTab, jeiText);
        if (this.searchBar != null) {
            this.updatingSearchText = true;
            this.searchBar.setQuery(jeiText);
            this.updatingSearchText = false;
        }

        this.loadItems(false);
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    /** Makes {@code tab} the selected one and loads its stacks into the grid. */
    private void applyTab(CreativeModeTab tab) {
        selectedTab = tab;
        this.clearDraggingState();
        this.restoreQuery(tab);
        this.loadItems(true);
    }

    /** Remembers {@code value} in whichever place {@link CreativeGridConfig#searchShareMode()} picked. */
    private static void rememberQuery(CreativeModeTab tab, String value) {
        if (CreativeGridConfig.searchShareMode() == SearchShareMode.PER_TAB) {
            TAB_QUERIES.put(tab, value);
        } else {
            sharedQuery = value;
        }
    }

    /** Brings the search field back to the text the new tab, or the shared bucket, remembers. */
    private void restoreQuery(CreativeModeTab tab) {
        String remembered = CreativeGridConfig.searchShareMode() == SearchShareMode.PER_TAB
                ? TAB_QUERIES.getOrDefault(tab, "")
                : sharedQuery;
        this.query = remembered;
        if (this.searchBar == null) {
            return;
        }

        // Writing the field fires the responder; that must not be mistaken for the player typing.
        this.updatingSearchText = true;
        this.searchBar.setQuery(remembered);
        this.updatingSearchText = false;
    }

    /**
     * Fills the grid from the selected tab. The search query always applies, so every tab filters the same way, and
     * an empty query simply shows the whole tab.
     */
    private void loadItems(boolean resetScroll) {
        Collection<ItemStack> source = selectedTab.getType() == CreativeModeTab.Type.HOTBAR
                ? this.buildHotbarPage()
                : selectedTab.getDisplayItems();
        Collection<ItemStack> visible = SearchBar.resolve(this.minecraft, selectedTab, source, this.query);

        if (resetScroll) {
            this.menu.items.clear();
            this.menu.items.addAll(visible);
            this.menu.resetScroll();
        } else {
            this.refreshTabItems(visible);
        }
    }

    /** The search text changed: keep a copy for the next {@link #init()} and re-filter the grid in place. */
    private void onSearchChanged(String value) {
        this.query = value;
        if (this.updatingSearchText) {
            return;
        }

        rememberQuery(selectedTab, value);
        if (CreativeGridConfig.jeiLinkMode() == JeiLinkMode.TWO_WAY && JeiIntegration.isLoaded()) {
            // Note the text as ours before pushing it, so the next tick does not read it back as JEI's.
            this.lastJeiText = value;
            JeiIntegration.setSearchText(value);
        }

        this.loadItems(false);
    }

    /**
     * The nine saved hotbars, one row each. An empty slot shows the paper placeholder vanilla uses to tell the player
     * which keys store a hotbar, and those placeholders refuse to be picked up.
     */
    private List<ItemStack> buildHotbarPage() {
        List<ItemStack> stacks = new ArrayList<>();
        HotbarManager manager = this.minecraft.getHotbarManager();
        for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            Hotbar hotbar = manager.get(hotbarIndex);
            if (!hotbar.isEmpty()) {
                stacks.addAll(hotbar.load(this.minecraft.level.registryAccess()));
                continue;
            }

            for (int slot = 0; slot < 9; slot++) {
                if (slot != hotbarIndex) {
                    stacks.add(ItemStack.EMPTY);
                    continue;
                }

                ItemStack placeholder = new ItemStack(Items.PAPER);
                placeholder.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
                Component hotbarKey = this.minecraft.options.keyHotbarSlots[hotbarIndex].getTranslatedKeyMessage();
                Component saveKey = this.minecraft.options.keySaveHotbarActivator.getTranslatedKeyMessage();
                placeholder.set(DataComponents.ITEM_NAME, Component.translatable("inventory.hotbarInfo", saveKey, hotbarKey));
                stacks.add(placeholder);
            }
        }

        return stacks;
    }

    /** Replaces the visible stacks while keeping the scrollbar where the user left it. */
    private void refreshTabItems(Collection<ItemStack> stacks) {
        int rowIndex = this.menu.getRowIndexForScroll(this.menu.scrollOffs());
        this.menu.items.clear();
        this.menu.items.addAll(stacks);
        this.menu.scrollTo(this.menu.getScrollForRowIndex(rowIndex));
    }

    private void rebuildTabContents(LocalPlayer player) {
        CreativeModeTabs.tryRebuildTabContents(
                player.connection.enabledFeatures(),
                player.canUseGameMasterBlocks() && this.displayOperatorCreativeTab,
                player.level().registryAccess());
    }

    /** Rebuilds the tab contents when the server's feature flags or registries changed. */
    private void refreshInvalidatedTabs() {
        LocalPlayer player = this.minecraft.player;
        if (!CreativeModeTabs.tryRebuildTabContents(
                player.connection.enabledFeatures(),
                player.canUseGameMasterBlocks() && this.displayOperatorCreativeTab,
                player.level().registryAccess())) {
            return;
        }

        this.tabList.refreshTabs();
        if (this.tabList.indexOf(selectedTab) < 0) {
            this.applyTab(this.fallbackTab());
            return;
        }

        if (selectedTab.getType() == CreativeModeTab.Type.CATEGORY) {
            if (selectedTab.getDisplayItems().isEmpty()) {
                this.applyTab(this.fallbackTab());
            } else {
                this.loadItems(false);
            }
        }
    }

    /** The tab to fall back to when the selected one stopped existing: the first listed one. */
    private CreativeModeTab fallbackTab() {
        CreativeModeTab first = this.tabList.firstTab();
        return first != null ? first : CreativeModeTabs.getDefaultTab();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /**
     * Hands typing to the search field first. Without this the screen's own key handling would swallow the keys
     * mid-word, {@code E} would close the screen and {@code 1-9} would swap hotbar slots while the player types.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBar != null && this.searchBar.isFocused()) {
            if (this.searchBar.handleKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            // Escape is left to the screen so it still closes the inventory.
            if (keyCode != 256) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBar != null && this.searchBar.handleCharTyped(codePoint, modifiers)) {
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.tabList.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        if (!this.menu.canScroll()) {
            return false;
        }
        this.menu.scrollBy(scrollY);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The field keeps the keyboard while it is focused, so clicking anywhere else has to release it again.
        if (this.searchBar != null && this.searchBar.isFocused() && !this.isOverSearchField(mouseX, mouseY)) {
            this.searchBar.unfocus();
        }

        // The gear and its panel are drawn over the slots, so they get the click before anything underneath does.
        if (this.settingsPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            if (this.relicButton.mouseClicked(mouseX, mouseY)) {
                return true;
            }

            CreativeModeTab tab = this.tabList.mouseClicked(mouseX, mouseY);
            if (tab != null) {
                this.applyTab(tab);
                this.tabList.ensureVisible(tab);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverSearchField(double mouseX, double mouseY) {
        int left = this.leftPos + this.layout.searchX();
        int top = this.topPos + this.layout.searchY();
        return mouseX >= left && mouseX < left + this.layout.searchWidth()
                && mouseY >= top && mouseY < top + CreativeGridLayout.SEARCH_BAR_HEIGHT;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.settingsPanel.mouseReleased()) {
            return true;
        }

        if (button == 0) {
            this.tabList.mouseReleased();
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.settingsPanel.mouseDragged(mouseX, mouseY)) {
            return true;
        }

        if (this.tabList.mouseDragged(mouseY)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean outside = mouseX < (double) guiLeft
                || mouseY < (double) guiTop
                || mouseX >= (double) (guiLeft + this.imageWidth)
                || mouseY >= (double) (guiTop + this.imageHeight);
        this.hasClickedOutside = outside;
        return outside;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        boolean quickMove = type == ClickType.QUICK_MOVE;
        if (slotId == -999 && type == ClickType.PICKUP) {
            type = ClickType.THROW;
        }

        if (slot == null && type != ClickType.QUICK_CRAFT) {
            this.dropCarriedOutside(mouseButton);
            return;
        }

        // The destroy slot reports mayPickup false, so it has to be claimed before that guard turns it away.
        if (slot == this.menu.destroySlot()) {
            this.handleDestroySlotClick(mouseButton, type);
            return;
        }

        if (slot != null && !slot.mayPickup(this.minecraft.player)) {
            return;
        }

        // The recipe preview is taken by copying it onto the cursor, so the grid is never consumed.
        if (slot != null && this.menu.craftingArea().isResultSlot(slot.container)) {
            this.handleCraftingResultClick(type);
            return;
        }

        boolean craftingSlot = slot != null && this.menu.craftingArea().isInputSlot(slot.container);

        if (slot != null && slot.container == this.menu.container()) {
            this.handleCreativeSlotClick(slot, mouseButton, type, quickMove);
        } else if (this.menu != null) {
            // Player slots sit at the same menu index as their InventoryMenu counterpart, so no index translation
            // is needed: a slot's own index already is the InventoryMenu position the server expects.
            ItemStack before = slot == null ? ItemStack.EMPTY : slot.getItem();
            this.menu.clicked(slot == null ? slotId : slot.index, mouseButton, type, this.minecraft.player);
            if (AbstractContainerMenu.getQuickcraftHeader(mouseButton) == 2) {
                for (int index = 0; index < 9; index++) {
                    int hotbarSlot = CreativeItemPickerMenu.HOTBAR_SLOT_INDEX + index;
                    this.minecraft.gameMode.handleCreativeModeItemAdd(
                            this.menu.getSlot(hotbarSlot).getItem(), hotbarSlot);
                }
            } else if (slot != null) {
                // Crafting slots have no server counterpart: the server only accepts slot numbers 0..45, so
                // reporting them would do nothing but log a rejected slot.
                if (!craftingSlot) {
                    ItemStack after = this.menu.getSlot(slot.index).getItem();
                    this.minecraft.gameMode.handleCreativeModeItemAdd(after, slot.index);
                }
                if (type == ClickType.SWAP) {
                    // F swaps with the offhand, which the inventory stores at index 40 rather than a hotbar index.
                    int swappedSlot = mouseButton == 40
                            ? CreativeItemPickerMenu.OFFHAND_SLOT_INDEX
                            : CreativeItemPickerMenu.HOTBAR_SLOT_INDEX + mouseButton;
                    this.minecraft.gameMode.handleCreativeModeItemAdd(before, swappedSlot);
                } else if (type == ClickType.THROW && !before.isEmpty()) {
                    ItemStack dropped = before.copyWithCount(mouseButton == 0 ? 1 : before.getMaxStackSize());
                    this.minecraft.player.drop(dropped, true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(dropped);
                }

                this.minecraft.player.inventoryMenu.broadcastChanges();
            }
        }
    }

    /**
     * The destroy slot holds nothing, so the menu cannot act on it and the screen has to. Every branch mirrors
     * vanilla's own handling for the tab types where this slot exists: a shift click empties the whole inventory, a
     * left click drops the carried stack off the cursor, and a right click takes a single item off it.
     */
    private void handleDestroySlotClick(int mouseButton, ClickType type) {
        if (type == ClickType.QUICK_MOVE) {
            for (int index = 0; index < this.minecraft.player.inventoryMenu.getItems().size(); index++) {
                this.minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, index);
            }

            // The 3x3 grid stands in for vanilla's 2x2, which the loop above does clear; this one is client-only and
            // its container never notifies the menu, so the preview needs an explicit nudge.
            this.menu.craftingArea().input().clearContent();
            this.menu.craftingArea().refresh(this.minecraft.level);
            this.minecraft.player.inventoryMenu.broadcastChanges();
        } else if (type == ClickType.PICKUP) {
            if (mouseButton == 0) {
                this.menu.setCarried(ItemStack.EMPTY);
            } else if (!this.menu.getCarried().isEmpty()) {
                this.menu.getCarried().shrink(1);
            }
        }
    }

    /** Throws the carried stack (or a single item with the right button) into the world. */
    private void dropCarriedOutside(int mouseButton) {
        if (this.menu.getCarried().isEmpty() || !this.hasClickedOutside) {
            return;
        }

        if (mouseButton == 0) {
            this.minecraft.player.drop(this.menu.getCarried(), true);
            this.minecraft.gameMode.handleCreativeModeItemDrop(this.menu.getCarried());
            this.menu.setCarried(ItemStack.EMPTY);
        } else if (mouseButton == 1) {
            ItemStack dropped = this.menu.getCarried().split(1);
            this.minecraft.player.drop(dropped, true);
            this.minecraft.gameMode.handleCreativeModeItemDrop(dropped);
        }
    }

    /**
     * The infinite-source behaviour of a creative picker slot: the grid never loses its stack, the carried stack is
     * grown or shrunk instead.
     */
    private void handleCreativeSlotClick(Slot slot, int mouseButton, ClickType type, boolean quickMove) {
        ItemStack carried = this.menu.getCarried();
        ItemStack slotStack = slot.getItem();

        if (quickMove && CreativeGridConfig.shiftClickAction() == ShiftClickAction.STOCK_INVENTORY) {
            this.stockIntoInventory(slotStack.isEmpty() ? carried : slotStack);
            return;
        }

        if (type == ClickType.SWAP) {
            if (!slotStack.isEmpty()) {
                this.minecraft.player.getInventory().setItem(mouseButton, slotStack.copyWithCount(slotStack.getMaxStackSize()));
                this.minecraft.player.inventoryMenu.broadcastChanges();
            }
            return;
        }

        if (type == ClickType.CLONE) {
            if (this.menu.getCarried().isEmpty() && slot.hasItem()) {
                this.menu.setCarried(slotStack.copyWithCount(slotStack.getMaxStackSize()));
            }
            return;
        }

        if (type == ClickType.THROW) {
            if (!slotStack.isEmpty()) {
                ItemStack dropped = slotStack.copyWithCount(mouseButton == 0 ? 1 : slotStack.getMaxStackSize());
                this.minecraft.player.drop(dropped, true);
                this.minecraft.gameMode.handleCreativeModeItemDrop(dropped);
            }
            return;
        }

        if (!carried.isEmpty() && !slotStack.isEmpty() && ItemStack.isSameItemSameComponents(carried, slotStack)) {
            if (mouseButton == 0) {
                if (quickMove) {
                    carried.setCount(carried.getMaxStackSize());
                } else if (carried.getCount() < carried.getMaxStackSize()) {
                    carried.grow(1);
                }
            } else {
                carried.shrink(1);
            }
        } else if (!slotStack.isEmpty() && carried.isEmpty()) {
            this.menu.setCarried(slotStack.copyWithCount(quickMove ? slotStack.getMaxStackSize() : slotStack.getCount()));
        } else if (mouseButton == 0) {
            this.menu.setCarried(ItemStack.EMPTY);
        } else if (!this.menu.getCarried().isEmpty()) {
            this.menu.getCarried().shrink(1);
        }
    }

    /**
     * The stocking Shift-click: fills matching stacks in the backpack and hotbar, then the first empty slot, up to
     * a full stack, instead of putting the stack on the cursor.
     */
    private void stockIntoInventory(ItemStack source) {
        if (source.isEmpty() || this.minecraft.player == null || this.minecraft.gameMode == null) {
            return;
        }

        ItemStack offering = source.copyWithCount(source.getMaxStackSize());
        int empty = -1;
        for (int index = 9; index < CreativeItemPickerMenu.OFFHAND_SLOT_INDEX && !offering.isEmpty(); index++) {
            Slot slot = this.menu.getSlot(index);
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                if (empty < 0) {
                    empty = index;
                }
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(existing, offering)) {
                continue;
            }
            int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), offering.getCount());
            if (moved <= 0) {
                continue;
            }
            // Growing the slot's own stack keeps its reference valid; the packet below mirrors it on the server.
            existing.grow(moved);
            offering.shrink(moved);
            this.minecraft.gameMode.handleCreativeModeItemAdd(existing, index);
        }

        if (!offering.isEmpty() && empty >= 0) {
            this.menu.getSlot(empty).setByPlayer(offering, ItemStack.EMPTY);
            this.minecraft.gameMode.handleCreativeModeItemAdd(offering, empty);
        }

        this.minecraft.player.inventoryMenu.broadcastChanges();
    }

    /**
     * Taking the crafting result is a creative copy: the preview lands on the cursor and the grid keeps everything.
     * Dropping and hotbar-swapping it are no-ops, because there is nothing durable behind the preview.
     */
    private void handleCraftingResultClick(ClickType type) {
        ItemStack result = this.menu.craftingArea().result();
        if (result.isEmpty() || type == ClickType.THROW || type == ClickType.SWAP) {
            return;
        }

        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            this.menu.setCarried(result.copy());
        } else if (ItemStack.isSameItemSameComponents(carried, result)
                && carried.getCount() + result.getCount() <= carried.getMaxStackSize()) {
            carried.grow(result.getCount());
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x0 = this.leftPos;
        int y0 = this.topPos;
        int x1 = x0 + this.imageWidth;
        int y1 = y0 + this.imageHeight;

        guiGraphics.fill(x0, y0, x1, y1, PANEL_COLOR);
        guiGraphics.fill(x0, y0, x1, y0 + 1, BORDER_COLOR);
        guiGraphics.fill(x0, y1 - 1, x1, y1, BORDER_COLOR);
        guiGraphics.fill(x0, y0, x0 + 1, y1, BORDER_COLOR);
        guiGraphics.fill(x1 - 1, y0, x1, y1, BORDER_COLOR);

        // Left tab strip: the panel paints the recess, TabListPanel draws the buttons on top.
        guiGraphics.fill(x0 + 1, y0 + 1, x0 + CreativeGridLayout.TAB_BAR_WIDTH, y1 - 1, TAB_BAR_COLOR);
        guiGraphics.fill(x0 + CreativeGridLayout.TAB_BAR_WIDTH, y0 + 1, x0 + CreativeGridLayout.TAB_BAR_WIDTH + 1, y1 - 1, SEPARATOR_COLOR);
        this.hoveredTab = this.tabList.render(guiGraphics, this.font, mouseX, mouseY, selectedTab);

        // Search field recess; SearchBar draws its text on top of it.
        int searchLeft = x0 + this.layout.searchX();
        int searchTop = y0 + this.layout.searchY();
        int searchRight = searchLeft + this.layout.searchWidth();
        int searchFieldBottom = searchTop + CreativeGridLayout.SEARCH_BAR_HEIGHT;
        guiGraphics.fill(searchLeft, searchTop, searchRight, searchFieldBottom, SEARCH_FIELD_COLOR);
        if (this.searchBar != null && this.searchBar.isFocused()) {
            guiGraphics.fill(searchLeft, searchTop, searchRight, searchTop + 1, SEARCH_FIELD_FOCUS_COLOR);
            guiGraphics.fill(searchLeft, searchFieldBottom - 1, searchRight, searchFieldBottom, SEARCH_FIELD_FOCUS_COLOR);
            guiGraphics.fill(searchLeft, searchTop, searchLeft + 1, searchFieldBottom, SEARCH_FIELD_FOCUS_COLOR);
            guiGraphics.fill(searchRight - 1, searchTop, searchRight, searchFieldBottom, SEARCH_FIELD_FOCUS_COLOR);
        }

        // Search bar strip separator.
        int searchBottom = y0 + CreativeGridLayout.SEARCH_BAR_HEIGHT + CreativeGridLayout.PADDING - 1;
        guiGraphics.fill(x0 + CreativeGridLayout.TAB_BAR_WIDTH + 1, searchBottom, x1 - 1, searchBottom + 1, SEPARATOR_COLOR);

        this.renderSlotFrames(guiGraphics, this.layout.gridX(), this.layout.gridY(), this.layout.columns(), this.layout.rows());
        this.renderSlotFrames(guiGraphics, this.layout.craftingX(), this.layout.craftingY(),
                CreativeGridLayout.CRAFT_GRID_SIZE, CreativeGridLayout.CRAFT_GRID_SIZE);
        this.renderSlotFrames(guiGraphics, this.layout.resultX(), this.layout.resultY(), 1, 1);
        this.renderCraftingArrow(guiGraphics);
        this.relicButton.render(guiGraphics, this.font, mouseX, mouseY);
        this.renderSlotFrames(guiGraphics, this.layout.inventoryX(), this.layout.inventoryY(), 9, 3);
        this.renderSlotFrames(guiGraphics, this.layout.armorX(), this.layout.armorY(), 2, 2);
        this.renderSlotFrames(guiGraphics, this.layout.offhandX(), this.layout.offhandY(), 1, 1);
        this.renderSlotFrames(guiGraphics, this.layout.hotbarX(), this.layout.hotbarY(), 9, 1);

        // The destroy slot wears the panel's own slot frame so it blends in, with vanilla's cross blitted inside it.
        int destroyX = this.leftPos + this.layout.destroyX();
        int destroyY = this.topPos + this.layout.destroyY();
        this.renderSlotFrames(guiGraphics, this.layout.destroyX(), this.layout.destroyY(), 1, 1);
        guiGraphics.blit(DESTROY_ICON, destroyX + 1, destroyY + 1, 0, 0.0F, 0.0F, 16, 16, 16, 16);

        // Player area separator.
        int playerTop = y0 + this.layout.inventoryY() - CreativeGridLayout.PADDING;
        guiGraphics.fill(x0 + CreativeGridLayout.TAB_BAR_WIDTH + 1, playerTop, x1 - 1, playerTop + 1, SEPARATOR_COLOR);

        this.renderPlayerModel(guiGraphics, mouseX, mouseY);
        this.renderScrollbar(guiGraphics);
    }

    /** The player model in the player area, turning with the mouse exactly like the vanilla inventory does. */
    private void renderPlayerModel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft.player == null) {
            return;
        }

        int x = this.leftPos + this.layout.avatarX();
        int y = this.topPos + this.layout.avatarY();
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, x, y, x + CreativeGridLayout.AVATAR_WIDTH, y + CreativeGridLayout.AVATAR_HEIGHT,
                30, 0.0625F, mouseX, mouseY, this.minecraft.player);
    }

    private void renderSlotFrames(GuiGraphics guiGraphics, int originX, int originY, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x = this.leftPos + originX + column * CreativeGridLayout.CELL;
                int y = this.topPos + originY + row * CreativeGridLayout.CELL;
                guiGraphics.fill(x, y, x + 17, y + 17, SLOT_BORDER_COLOR);
                guiGraphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_COLOR);
            }
        }
    }

    /** A small stepped arrow in the gap between the crafting grid and the result slot. */
    private void renderCraftingArrow(GuiGraphics guiGraphics) {
        int gapLeft = this.leftPos + this.layout.craftingX()
                + CreativeGridLayout.CRAFT_GRID_SIZE * CreativeGridLayout.CELL;
        int centerY = this.topPos + this.layout.resultY() + 8;

        guiGraphics.fill(gapLeft + 2, centerY - 1, gapLeft + 9, centerY + 1, ARROW_COLOR);
        guiGraphics.fill(gapLeft + 7, centerY - 3, gapLeft + 9, centerY + 3, ARROW_COLOR);
        guiGraphics.fill(gapLeft + 9, centerY - 2, gapLeft + 11, centerY + 2, ARROW_COLOR);
        guiGraphics.fill(gapLeft + 11, centerY - 1, gapLeft + 13, centerY + 1, ARROW_COLOR);
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int trackX = this.leftPos + this.layout.scrollbarX();
        int trackY = this.topPos + this.layout.scrollbarY();
        int trackWidth = CreativeGridLayout.SCROLLBAR_WIDTH;
        int trackHeight = this.layout.scrollbarHeight();
        guiGraphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, SCROLL_TRACK_COLOR);

        if (!this.menu.canScroll()) {
            return;
        }

        int visibleRows = this.layout.rows();
        int totalRows = this.menu.calculateRowCount() + visibleRows;
        int thumbHeight = Math.max(SCROLL_THUMB_MIN_HEIGHT, trackHeight * visibleRows / totalRows);
        int thumbY = trackY + (int) (this.menu.scrollOffs() * (float) (trackHeight - thumbHeight));
        guiGraphics.fill(trackX + 1, thumbY, trackX + trackWidth - 1, thumbY + thumbHeight, SCROLL_THUMB_COLOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.purgeForeignWidgets();
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Vanilla asks for the hovered slot's tooltip from its own render() override, which this screen does not
        // inherit; without this call the item information never appears. The call guards itself: no tooltip while
        // something is carried, and none unless a slot with a stack is hovered.
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Drawn last so the panel covers the slots, the carried stack and the item tooltip underneath it.
        //
        // Being last is not enough on its own: vanilla draws every item stack at z=150 (GuiGraphics.renderItem)
        // and its count badge at z=200, while RenderType.gui() tests depth with LEQUAL and writes depth. Geometry
        // submitted later at z=0 therefore fails the depth test wherever an item already wrote a nearer depth and
        // silently disappears, which is exactly how the panel used to end up underneath the grid. Lifting the panel
        // to the tooltip layer (z=400, what GuiGraphics uses for its own tooltips) puts it above the slots.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);
        this.settingsPanel.render(guiGraphics, this.font, mouseX, mouseY);
        guiGraphics.pose().popPose();

        if (this.hoveredTab != null && !this.settingsPanel.isOpen()) {
            guiGraphics.renderTooltip(this.font, this.hoveredTab.getDisplayName(), mouseX, mouseY);
        }

        // Vanilla's own label for the destroy slot; the translation key is shared, so no lang entry is needed here.
        if (this.hoveredSlot == this.menu.destroySlot() && !this.settingsPanel.isOpen()) {
            guiGraphics.renderTooltip(this.font, Component.translatable("inventory.binSlot"), mouseX, mouseY);
        }
    }

    /**
     * Drops whatever another mod hung on this screen, Quark's inventory sort button and FTB's sidebar above all: they
     * are laid out for the vanilla inventory and would sit on top of this panel. Some of them are put back after
     * {@code ScreenEvent.Init.Post}, so this runs on every frame rather than once; the screen's own widgets are known
     * by identity from the {@link #ownWidgets} snapshot {@link #init()} took, which is why nothing here matches on a
     * mod id or a widget class name.
     */
    private void purgeForeignWidgets() {
        for (GuiEventListener listener : List.copyOf(this.children())) {
            if (this.ownWidgets.contains(listener)) {
                continue;
            }

            if (this.reportedForeignWidgets.add(listener.getClass().getName())) {
                CreativeWellMod.LOGGER.info("[creativewell] dropping foreign widget {}", listener.getClass().getName());
            }
            this.removeWidget(listener);
        }

        // The second pass is for the ones that never made it into children(): a mod that draws its own button asks
        // the screen for a render-only entry instead, and those are drawn by the super call later in this method.
        for (Renderable renderable : List.copyOf(this.renderables)) {
            if (this.ownRenderables.contains(renderable) || isJeiOverlay(renderable)) {
                continue;
            }

            if (this.reportedForeignWidgets.add(renderable.getClass().getName())) {
                CreativeWellMod.LOGGER.info("[creativewell] dropping foreign renderable {}", renderable.getClass().getName());
            }
            this.renderables.remove(renderable);
        }
    }

    /**
     * JEI hands its ingredient list to the screen the same render-only way, and that list is squeezed next to the
     * panel rather than hidden, so it is the one thing this purge leaves alone.
     */
    private static boolean isJeiOverlay(Renderable renderable) {
        return renderable.getClass().getName().startsWith("mezz.jei.");
    }

    /**
     * The panel rectangle in absolute screen coordinates, handed to JEI as an exclusion area so its ingredient list
     * moves and shrinks instead of being covered by the panel. {@link com.homemine.creativewell.config.CreativeGridConfig.MaxColumnsMode#IGNORE_JEI}
     * asks for the opposite, so nothing is reported then.
     */
    public List<Rect2i> jeiExclusionAreas() {
        if (CreativeGridConfig.ignoresJei()) {
            return List.of();
        }

        return List.of(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
    }

    /** The search bar (later stage) owns the top strip, so the vanilla title is not drawn in the panel. */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
