package com.homemine.creativewell.client;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

/**
 * The vertical tab strip down the left edge of {@link CreativeGridScreen}.
 *
 * <p>Vanilla shows at most ten tabs in two horizontal rows above and below the panel, which runs out of room as soon
 * as a few mods add their own tab. Here every tab lives in a single column that scrolls with the mouse wheel or by
 * dragging its own scrollbar, so the count is unbounded.</p>
 *
 * <p>The panel owns no {@link net.minecraft.client.gui.components.AbstractWidget}s: it is plain geometry plus direct
 * drawing, and the screen forwards mouse events to it.</p>
 */
public final class TabListPanel {
    private static final int BUTTON_LEFT_INSET = 2;
    private static final int BUTTON_WIDTH = 20;
    private static final int BUTTON_HEIGHT = 26;
    private static final int ICON_INSET_X = 2;
    private static final int ICON_INSET_Y = 5;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int THUMB_MIN_HEIGHT = 12;

    private static final int BUTTON_COLOR = 0xFF3A3A3A;
    private static final int BUTTON_HOVER_COLOR = 0xFF4E4E4E;
    private static final int BUTTON_SELECTED_COLOR = 0xFF6E6E6E;
    private static final int BUTTON_BORDER_COLOR = 0xFF232323;
    private static final int ACCENT_COLOR = 0xFFFFD24A;
    private static final int SCROLL_TRACK_COLOR = 0xFF1A1A1A;
    private static final int SCROLL_THUMB_COLOR = 0xFFB0B0B0;

    private final List<CreativeModeTab> tabs = new ArrayList<>();

    private int leftPos;
    private int listTop;
    private int listBottom;
    private int scroll;

    private boolean dragging;

    /**
     * Rebuilds the displayed tab list. Catches mods that register their tab after the first screen open, and drops
     * category tabs that ended up empty because their feature flags are disabled.
     */
    public void refreshTabs() {
        this.tabs.clear();
        CreativeModeTabRegistry.getSortedCreativeModeTabs().stream()
                .filter(CreativeModeTab::shouldDisplay)
                .forEach(this.tabs::add);

        CreativeModeTab hotbar = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.HOTBAR);
        if (hotbar != null) {
            this.tabs.add(hotbar);
        }

        this.clampScroll();
    }

    /** Anchors the strip to the panel. Must be called after {@code leftPos}/{@code topPos} are known. */
    public void updatePosition(int leftPos, int topPos, int panelHeight) {
        this.leftPos = leftPos;
        this.listTop = topPos + 1;
        this.listBottom = topPos + panelHeight - 1;
        this.clampScroll();
    }

    public List<CreativeModeTab> tabs() {
        return this.tabs;
    }

    public int indexOf(CreativeModeTab tab) {
        return this.tabs.indexOf(tab);
    }

    @Nullable
    public CreativeModeTab firstTab() {
        return this.tabs.isEmpty() ? null : this.tabs.get(0);
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int contentHeight() {
        return this.tabs.size() * BUTTON_HEIGHT;
    }

    private int viewHeight() {
        return this.listBottom - this.listTop;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - this.viewHeight());
    }

    public boolean canScroll() {
        return this.maxScroll() > 0;
    }

    private void clampScroll() {
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
    }

    private int scrollbarX() {
        return this.leftPos + CreativeGridLayout.TAB_BAR_WIDTH - SCROLLBAR_WIDTH;
    }

    private boolean insideScrollbar(double mouseX, double mouseY) {
        return this.canScroll()
                && mouseX >= (double) this.scrollbarX() && mouseX < (double) (this.scrollbarX() + SCROLLBAR_WIDTH)
                && mouseY >= (double) this.listTop && mouseY < (double) this.listBottom;
    }

    private int thumbHeight() {
        int track = this.viewHeight();
        return Math.max(THUMB_MIN_HEIGHT, track * track / Math.max(1, this.contentHeight()));
    }

    private int thumbY() {
        int travel = this.viewHeight() - this.thumbHeight();
        return this.listTop + (travel <= 0 ? 0 : this.scroll * travel / Math.max(1, this.maxScroll()));
    }

    /** Scrolls the least amount that brings {@code tab} fully into view. */
    public void ensureVisible(CreativeModeTab tab) {
        int index = this.tabs.indexOf(tab);
        if (index < 0) {
            return;
        }

        int top = index * BUTTON_HEIGHT;
        if (top < this.scroll) {
            this.scroll = top;
        } else if (top + BUTTON_HEIGHT > this.scroll + this.viewHeight()) {
            this.scroll = top + BUTTON_HEIGHT - this.viewHeight();
        }

        this.clampScroll();
    }

    @Nullable
    public CreativeModeTab tabAt(double mouseX, double mouseY) {
        if (mouseX < (double) this.leftPos || mouseX >= (double) (this.leftPos + CreativeGridLayout.TAB_BAR_WIDTH)
                || mouseY < (double) this.listTop || mouseY >= (double) this.listBottom) {
            return null;
        }

        int index = (int) ((mouseY - (double) this.listTop + (double) this.scroll) / BUTTON_HEIGHT);
        return index >= 0 && index < this.tabs.size() ? this.tabs.get(index) : null;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /**
     * Handles the press. Tabs switch on the press, exactly like the vanilla tabs do, so no matching release is
     * needed to commit the choice.
     *
     * @return the tab to make current, or {@code null} when the press grabbed the scrollbar or missed every tab.
     */
    @Nullable
    public CreativeModeTab mouseClicked(double mouseX, double mouseY) {
        if (this.insideScrollbar(mouseX, mouseY)) {
            this.dragging = true;
            this.dragTo(mouseY);
            return null;
        }

        return this.tabAt(mouseX, mouseY);
    }

    /** Releases the strip: stops a scrollbar drag and forgets the press. */
    public void mouseReleased() {
        this.dragging = false;
    }

    public boolean mouseDragged(double mouseY) {
        if (!this.dragging) {
            return false;
        }

        this.dragTo(mouseY);
        return true;
    }

    private void dragTo(double mouseY) {
        int travel = this.viewHeight() - this.thumbHeight();
        if (travel <= 0) {
            return;
        }

        float ratio = (float) (mouseY - this.listTop - this.thumbHeight() / 2.0) / (float) travel;
        this.scroll = Math.round(Mth.clamp(ratio, 0.0F, 1.0F) * this.maxScroll());
        this.clampScroll();
    }

    /** @return {@code true} when the cursor is over the strip and the wheel was applied to it. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.canScroll()
                || mouseX < (double) this.leftPos || mouseX >= (double) (this.leftPos + CreativeGridLayout.TAB_BAR_WIDTH)
                || mouseY < (double) this.listTop || mouseY >= (double) this.listBottom) {
            return false;
        }

        this.scroll = Mth.clamp(this.scroll - (int) (scrollY * BUTTON_HEIGHT), 0, this.maxScroll());
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Draws every tab button and the scrollbar.
     *
     * @return the tab under the cursor, so the caller can render its tooltip once the item pass is done.
     */
    @Nullable
    public CreativeModeTab render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, CreativeModeTab selectedTab) {
        CreativeModeTab hovered = this.tabAt(mouseX, mouseY);

        for (int index = 0; index < this.tabs.size(); index++) {
            int buttonTop = this.listTop - this.scroll + index * BUTTON_HEIGHT;
            int buttonBottom = buttonTop + BUTTON_HEIGHT;
            if (buttonBottom <= this.listTop || buttonTop >= this.listBottom) {
                continue;
            }

            CreativeModeTab tab = this.tabs.get(index);
            boolean selected = tab == selectedTab;
            int color = selected ? BUTTON_SELECTED_COLOR : tab == hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR;

            int x0 = this.leftPos + BUTTON_LEFT_INSET;
            int x1 = x0 + BUTTON_WIDTH;
            int y0 = Math.max(buttonTop, this.listTop);
            int y1 = Math.min(buttonBottom, this.listBottom);

            guiGraphics.fill(x0, y0, x1, y1, BUTTON_BORDER_COLOR);
            guiGraphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
            if (selected) {
                guiGraphics.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 1, ACCENT_COLOR);
            }

            // A partly scrolled button is clipped, and its icon would spill into its neighbour.
            if (buttonTop >= this.listTop && buttonBottom <= this.listBottom) {
                ItemStack icon = tab.getIconItem();
                guiGraphics.renderItem(icon, x0 + ICON_INSET_X, buttonTop + ICON_INSET_Y);
                guiGraphics.renderItemDecorations(font, icon, x0 + ICON_INSET_X, buttonTop + ICON_INSET_Y);
            }
        }

        this.renderScrollbar(guiGraphics);
        return hovered;
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (!this.canScroll()) {
            return;
        }

        int x = this.scrollbarX();
        guiGraphics.fill(x, this.listTop, x + SCROLLBAR_WIDTH, this.listBottom, SCROLL_TRACK_COLOR);
        int thumbY = this.thumbY();
        guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + this.thumbHeight(), SCROLL_THUMB_COLOR);
    }
}
