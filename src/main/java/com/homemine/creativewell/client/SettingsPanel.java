package com.homemine.creativewell.client;

import com.homemine.creativewell.config.CreativeGridConfig;
import com.homemine.creativewell.config.CreativeGridConfig.JeiLinkMode;
import com.homemine.creativewell.config.CreativeGridConfig.MaxColumnsMode;
import com.homemine.creativewell.config.CreativeGridConfig.SearchShareMode;
import com.homemine.creativewell.config.CreativeGridConfig.ShiftClickAction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The gear button in the search strip and the floating panel it opens.
 *
 * <p>Like {@link TabListPanel} and {@link RelicButton} this is drawn by hand instead of being built from widgets, so
 * it can float above the item slots without joining the screen's widget list. Every option writes straight into
 * {@link CreativeGridConfig} and then tells the screen that something changed, which is what makes the new setting
 * visible the next tick.</p>
 *
 * <p>The column slider is the one option that is not a plain either/or: its track runs over every column count and
 * ends in the two uncapped modes, so a single drag reaches both "unlimited" and "cram JEI too" instead of them
 * needing buttons of their own. Dragging only moves {@link #pendingStop}: the panel is positioned from the layout,
 * and the layout follows the column count, so applying the value mid-drag would drag the slider out from under the
 * cursor.</p>
 */
public final class SettingsPanel {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 134;
    private static final int INSET = 6;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - INSET * 2;
    private static final int BUTTON_HEIGHT = 14;
    private static final int PAIR_WIDTH = (CONTENT_WIDTH - 4) / 2;
    private static final int TRIPLE_WIDTH = (CONTENT_WIDTH - 12) / 3;

    private static final int TITLE_Y = 6;
    private static final int COLUMNS_LABEL_Y = 19;
    private static final int SLIDER_Y = 31;
    private static final int SLIDER_HEIGHT = 10;
    private static final int SLIDER_THUMB_WIDTH = 10;
    private static final int SHIFT_LABEL_Y = 45;
    private static final int SHIFT_ROW_Y = 56;
    private static final int SEARCH_LABEL_Y = 74;
    private static final int SEARCH_ROW_Y = 85;
    private static final int JEI_LABEL_Y = 103;
    private static final int JEI_ROW_Y = 114;

    /** One slider stop per column count, then the two uncapped modes. */
    private static final int BOUNDED_STOPS = CreativeGridConfig.MAX_COLUMNS - CreativeGridConfig.MIN_COLUMNS + 1;
    private static final int UNLIMITED_STOP = BOUNDED_STOPS;
    private static final int IGNORE_JEI_STOP = BOUNDED_STOPS + 1;
    private static final int TOTAL_STOPS = BOUNDED_STOPS + 2;

    private static final int PANEL_COLOR = 0xF0101010;
    private static final int BORDER_COLOR = 0xFF909090;
    private static final int SEPARATOR_COLOR = 0x40FFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int MUTED_TEXT_COLOR = 0xFF9A9A9A;
    private static final int BUTTON_COLOR = 0xFF3A3A3A;
    private static final int BUTTON_HOVER_COLOR = 0xFF4E4E4E;
    private static final int BUTTON_BORDER_COLOR = 0xFF232323;
    private static final int BUTTON_TEXT_COLOR = 0xFFD0D0D0;
    private static final int TRACK_COLOR = 0xFF151515;
    private static final int TICK_COLOR = 0xFF3C3C3C;
    private static final int THUMB_COLOR = 0xFF9A9A9A;
    private static final int ACCENT_COLOR = 0xFFFFD24A;
    private static final int GEAR_COLOR = 0xFFBFBFBF;
    private static final int GEAR_HOVER_COLOR = ACCENT_COLOR;

    private static final Component TITLE = Component.translatable("creativewell.settings.title");
    private static final Component COLUMNS_LABEL = Component.translatable("creativewell.settings.columns.label");
    private static final Component COLUMNS_UNLIMITED = Component.translatable("creativewell.settings.columns.unlimited");
    private static final Component COLUMNS_IGNORE_JEI = Component.translatable("creativewell.settings.columns.ignoreJei");
    private static final Component SHIFT_LABEL = Component.translatable("creativewell.settings.shift.label");
    private static final Component SHIFT_GRAB = Component.translatable("creativewell.settings.shift.grab");
    private static final Component SHIFT_STOCK = Component.translatable("creativewell.settings.shift.stock");
    private static final Component SEARCH_LABEL = Component.translatable("creativewell.settings.search.label");
    private static final Component SEARCH_PER_TAB = Component.translatable("creativewell.settings.search.perTab");
    private static final Component SEARCH_GLOBAL = Component.translatable("creativewell.settings.search.global");
    private static final Component JEI_LABEL = Component.translatable("creativewell.settings.jei.label");
    private static final Component JEI_OFF = Component.translatable("creativewell.settings.jei.off");
    private static final Component JEI_ONE_WAY = Component.translatable("creativewell.settings.jei.oneWay");
    private static final Component JEI_TWO_WAY = Component.translatable("creativewell.settings.jei.twoWay");

    private final Runnable onChanged;

    private int gearX;
    private int gearY;
    private int panelX;
    private int panelY;
    private boolean open;
    private boolean draggingSlider;
    private int pendingStop;

    public SettingsPanel(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /**
     * Anchors the gear to the search strip and hangs the panel underneath it, right edges aligned.
     *
     * <p>The panel is then pulled back inside the free space between the screen edges and the JEI strips, so it can
     * never sit on top of the item list of JEI or of the vanilla creative screen.</p>
     */
    public void position(CreativeGridLayout layout, int leftPos, int topPos,
            int screenWidth, int screenHeight, int jeiLeft, int jeiRight) {
        this.gearX = leftPos + layout.gearX();
        this.gearY = topPos + layout.gearY();

        int minX = CreativeGridLayout.MARGIN + jeiLeft;
        int maxX = Math.max(minX, screenWidth - CreativeGridLayout.MARGIN - jeiRight - PANEL_WIDTH);
        this.panelX = Mth.clamp(this.gearX + CreativeGridLayout.GEAR_SIZE - PANEL_WIDTH, minX, maxX);

        int minY = CreativeGridLayout.MARGIN;
        int maxY = Math.max(minY, screenHeight - CreativeGridLayout.MARGIN - PANEL_HEIGHT);
        this.panelY = Mth.clamp(this.gearY + CreativeGridLayout.GEAR_SIZE + 2, minY, maxY);
    }

    public boolean isOpen() {
        return this.open;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void close() {
        this.open = false;
        this.draggingSlider = false;
    }

    public boolean isOverPanel(double mouseX, double mouseY) {
        return mouseX >= this.panelX && mouseX < this.panelX + PANEL_WIDTH
                && mouseY >= this.panelY && mouseY < this.panelY + PANEL_HEIGHT;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /**
     * @return true when the click belongs to the gear or to the open panel, so the screen stops there and leaves
     *     the slots underneath alone. A click outside an open panel closes it and is passed on.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.gearX <= mouseX && mouseX < this.gearX + CreativeGridLayout.GEAR_SIZE
                && this.gearY <= mouseY && mouseY < this.gearY + CreativeGridLayout.GEAR_SIZE) {
            if (button == 0) {
                toggle();
            }

            return true;
        }

        if (!this.open) {
            return false;
        }

        if (!this.isOverPanel(mouseX, mouseY)) {
            close();
            return false;
        }

        if (button != 0) {
            return true;
        }

        if (this.isOverSlider(mouseX, mouseY)) {
            this.draggingSlider = true;
            this.pendingStop = stopAt(mouseX);
            return true;
        }

        if (this.isOverRow(mouseX, mouseY, SHIFT_ROW_Y, 0, PAIR_WIDTH)) {
            applyShift(ShiftClickAction.GRAB_FULL_STACK);
        } else if (this.isOverRow(mouseX, mouseY, SHIFT_ROW_Y, 1, PAIR_WIDTH)) {
            applyShift(ShiftClickAction.STOCK_INVENTORY);
        } else if (this.isOverRow(mouseX, mouseY, SEARCH_ROW_Y, 0, PAIR_WIDTH)) {
            applySearchShare(SearchShareMode.PER_TAB);
        } else if (this.isOverRow(mouseX, mouseY, SEARCH_ROW_Y, 1, PAIR_WIDTH)) {
            applySearchShare(SearchShareMode.GLOBAL);
        } else if (this.isOverTriple(mouseX, mouseY, JEI_ROW_Y, 0)) {
            applyJeiLink(JeiLinkMode.OFF);
        } else if (this.isOverTriple(mouseX, mouseY, JEI_ROW_Y, 1)) {
            applyJeiLink(JeiLinkMode.ONE_WAY);
        } else if (this.isOverTriple(mouseX, mouseY, JEI_ROW_Y, 2)) {
            applyJeiLink(JeiLinkMode.TWO_WAY);
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!this.draggingSlider) {
            return false;
        }

        this.pendingStop = stopAt(mouseX);
        return true;
    }

    /** Commits a slider drag; the value only reaches the config and the disk once the drag is over. */
    public boolean mouseReleased() {
        if (!this.draggingSlider) {
            return false;
        }

        this.draggingSlider = false;
        applyStop(this.pendingStop);
        return true;
    }

    /** Turns a slider stop into a setting: a column count, or one of the two uncapped modes at the far end. */
    private void applyStop(int stop) {
        if (stop >= IGNORE_JEI_STOP) {
            applyMode(MaxColumnsMode.IGNORE_JEI);
        } else if (stop >= UNLIMITED_STOP) {
            applyMode(MaxColumnsMode.UNLIMITED);
        } else {
            CreativeGridConfig.setMaxColumns(CreativeGridConfig.MIN_COLUMNS + stop);
            applyMode(MaxColumnsMode.BOUNDED);
        }
    }

    private void applyMode(MaxColumnsMode mode) {
        CreativeGridConfig.setMaxColumnsMode(mode);
        commit();
    }

    private void applyShift(ShiftClickAction action) {
        CreativeGridConfig.setShiftClickAction(action);
        commit();
    }

    private void applySearchShare(SearchShareMode mode) {
        CreativeGridConfig.setSearchShareMode(mode);
        commit();
    }

    private void applyJeiLink(JeiLinkMode mode) {
        CreativeGridConfig.setJeiLinkMode(mode);
        commit();
    }

    private void commit() {
        CreativeGridConfig.save();
        this.onChanged.run();
    }

    private boolean isOverRow(double mouseX, double mouseY, int rowY, int index, int width) {
        int x = this.panelX + INSET + index * (width + 4);
        return mouseX >= x && mouseX < x + width
                && mouseY >= this.panelY + rowY && mouseY < this.panelY + rowY + BUTTON_HEIGHT;
    }

    private boolean isOverTriple(double mouseX, double mouseY, int rowY, int index) {
        int x = this.panelX + INSET + index * (TRIPLE_WIDTH + 6);
        return mouseX >= x && mouseX < x + TRIPLE_WIDTH
                && mouseY >= this.panelY + rowY && mouseY < this.panelY + rowY + BUTTON_HEIGHT;
    }

    private boolean isOverSlider(double mouseX, double mouseY) {
        int x = this.panelX + INSET;
        return mouseX >= x && mouseX < x + CONTENT_WIDTH
                && mouseY >= this.panelY + SLIDER_Y && mouseY < this.panelY + SLIDER_Y + SLIDER_HEIGHT;
    }

    /** The slider stop the cursor points at, clamped to the track. */
    private int stopAt(double mouseX) {
        int trackX = this.panelX + INSET;
        int travel = CONTENT_WIDTH - SLIDER_THUMB_WIDTH;
        double fraction = (mouseX - trackX - SLIDER_THUMB_WIDTH / 2.0) / travel;
        return (int) Math.round(Mth.clamp(fraction, 0.0, 1.0) * (TOTAL_STOPS - 1));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Draws the gear always and the panel on top of everything else while it is open. */
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        this.renderGear(guiGraphics, mouseX, mouseY);
        if (!this.open) {
            return;
        }

        int x0 = this.panelX;
        int y0 = this.panelY;
        int x1 = x0 + PANEL_WIDTH;
        int y1 = y0 + PANEL_HEIGHT;
        guiGraphics.fill(x0, y0, x1, y1, PANEL_COLOR);
        guiGraphics.fill(x0, y0, x1, y0 + 1, BORDER_COLOR);
        guiGraphics.fill(x0, y1 - 1, x1, y1, BORDER_COLOR);
        guiGraphics.fill(x0, y0, x0 + 1, y1, BORDER_COLOR);
        guiGraphics.fill(x1 - 1, y0, x1, y1, BORDER_COLOR);

        guiGraphics.drawString(font, TITLE, x0 + INSET, y0 + TITLE_Y, ACCENT_COLOR, false);
        guiGraphics.fill(x0 + INSET, y0 + TITLE_Y + 11, x1 - INSET, y0 + TITLE_Y + 12, SEPARATOR_COLOR);

        guiGraphics.drawString(font, columnsLabel(), x0 + INSET, y0 + COLUMNS_LABEL_Y, TEXT_COLOR, false);
        this.renderSlider(guiGraphics, mouseX, mouseY);

        guiGraphics.drawString(font, SHIFT_LABEL, x0 + INSET, y0 + SHIFT_LABEL_Y, TEXT_COLOR, false);
        ShiftClickAction shift = CreativeGridConfig.shiftClickAction();
        this.renderButton(guiGraphics, font, INSET, SHIFT_ROW_Y, PAIR_WIDTH,
                shift == ShiftClickAction.GRAB_FULL_STACK, mouseX, mouseY, SHIFT_GRAB);
        this.renderButton(guiGraphics, font, INSET + PAIR_WIDTH + 4, SHIFT_ROW_Y, PAIR_WIDTH,
                shift == ShiftClickAction.STOCK_INVENTORY, mouseX, mouseY, SHIFT_STOCK);

        guiGraphics.drawString(font, SEARCH_LABEL, x0 + INSET, y0 + SEARCH_LABEL_Y, TEXT_COLOR, false);
        SearchShareMode share = CreativeGridConfig.searchShareMode();
        this.renderButton(guiGraphics, font, INSET, SEARCH_ROW_Y, PAIR_WIDTH,
                share == SearchShareMode.PER_TAB, mouseX, mouseY, SEARCH_PER_TAB);
        this.renderButton(guiGraphics, font, INSET + PAIR_WIDTH + 4, SEARCH_ROW_Y, PAIR_WIDTH,
                share == SearchShareMode.GLOBAL, mouseX, mouseY, SEARCH_GLOBAL);

        guiGraphics.drawString(font, JEI_LABEL, x0 + INSET, y0 + JEI_LABEL_Y, TEXT_COLOR, false);
        JeiLinkMode link = CreativeGridConfig.jeiLinkMode();
        this.renderTriple(guiGraphics, font, JEI_ROW_Y, 0, link == JeiLinkMode.OFF, mouseX, mouseY, JEI_OFF);
        this.renderTriple(guiGraphics, font, JEI_ROW_Y, 1, link == JeiLinkMode.ONE_WAY, mouseX, mouseY, JEI_ONE_WAY);
        this.renderTriple(guiGraphics, font, JEI_ROW_Y, 2, link == JeiLinkMode.TWO_WAY, mouseX, mouseY, JEI_TWO_WAY);
    }

    /** The setting the slider currently stands for: the live value while dragging, the stored value otherwise. */
    private Component columnsLabel() {
        return describeStop(this.draggingSlider ? this.pendingStop : currentStop());
    }

    private static Component describeStop(int stop) {
        if (stop >= IGNORE_JEI_STOP) {
            return COLUMNS_IGNORE_JEI;
        }
        if (stop >= UNLIMITED_STOP) {
            return COLUMNS_UNLIMITED;
        }
        return Component.translatable("creativewell.settings.columns.value", CreativeGridConfig.MIN_COLUMNS + stop);
    }

    /** Where the thumb rests when nothing is being dragged. */
    private static int currentStop() {
        return switch (CreativeGridConfig.maxColumnsMode()) {
            case UNLIMITED -> UNLIMITED_STOP;
            case IGNORE_JEI -> IGNORE_JEI_STOP;
            case BOUNDED -> Mth.clamp(CreativeGridConfig.maxColumns(),
                    CreativeGridConfig.MIN_COLUMNS, CreativeGridConfig.MAX_COLUMNS) - CreativeGridConfig.MIN_COLUMNS;
        };
    }

    private void renderGear(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean hovered = mouseX >= this.gearX && mouseX < this.gearX + CreativeGridLayout.GEAR_SIZE
                && mouseY >= this.gearY && mouseY < this.gearY + CreativeGridLayout.GEAR_SIZE;
        int color = hovered ? GEAR_HOVER_COLOR : GEAR_COLOR;
        int centerX = this.gearX + CreativeGridLayout.GEAR_SIZE / 2;
        int centerY = this.gearY + CreativeGridLayout.GEAR_SIZE / 2;

        // A ring with four teeth and a hole, all rectangles so no glyph has to be trusted to exist.
        guiGraphics.fill(centerX - 5, centerY - 5, centerX + 5, centerY + 5, color);
        guiGraphics.fill(centerX - 2, centerY - 7, centerX + 2, centerY - 5, color);
        guiGraphics.fill(centerX - 2, centerY + 5, centerX + 2, centerY + 7, color);
        guiGraphics.fill(centerX - 7, centerY - 2, centerX - 5, centerY + 2, color);
        guiGraphics.fill(centerX + 5, centerY - 2, centerX + 7, centerY + 2, color);
        guiGraphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, PANEL_COLOR);
    }

    private void renderSlider(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int trackX = this.panelX + INSET;
        int trackY = this.panelY + SLIDER_Y;
        int trackRight = trackX + CONTENT_WIDTH;
        guiGraphics.fill(trackX, trackY, trackRight, trackY + SLIDER_HEIGHT, TRACK_COLOR);

        // A tick per stop makes it visible that the last two are the uncapped modes and not just more column counts.
        for (int stop = 0; stop < TOTAL_STOPS; stop++) {
            int tickX = trackX + stopOffset(stop) + SLIDER_THUMB_WIDTH / 2;
            boolean uncapped = stop >= UNLIMITED_STOP;
            guiGraphics.fill(tickX, trackY + 2, tickX + 1, trackY + SLIDER_HEIGHT - 2,
                    uncapped ? ACCENT_COLOR : TICK_COLOR);
        }

        int thumbX = trackX + stopOffset(this.draggingSlider ? this.pendingStop : currentStop());
        boolean hovered = this.isOverSlider(mouseX, mouseY);
        guiGraphics.fill(thumbX, trackY, thumbX + SLIDER_THUMB_WIDTH, trackY + SLIDER_HEIGHT,
                hovered || this.draggingSlider ? ACCENT_COLOR : THUMB_COLOR);
    }

    /** Pixel offset from the start of the track for a slider stop. */
    private static int stopOffset(int stop) {
        int travel = CONTENT_WIDTH - SLIDER_THUMB_WIDTH;
        return (int) Math.round((double) stop / (TOTAL_STOPS - 1) * travel);
    }

    private void renderButton(
            GuiGraphics guiGraphics, Font font, int x, int rowY, int width,
            boolean active, int mouseX, int mouseY, Component label) {
        int left = this.panelX + x;
        int top = this.panelY + rowY;
        int right = left + width;
        int bottom = top + BUTTON_HEIGHT;
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;

        guiGraphics.fill(left, top, right, bottom, hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        if (active) {
            guiGraphics.fill(left, top, right, top + 1, ACCENT_COLOR);
            guiGraphics.fill(left, bottom - 1, right, bottom, ACCENT_COLOR);
            guiGraphics.fill(left, top, left + 1, bottom, ACCENT_COLOR);
            guiGraphics.fill(right - 1, top, right, bottom, ACCENT_COLOR);
        } else {
            guiGraphics.fill(left, top, right, top + 1, BUTTON_BORDER_COLOR);
            guiGraphics.fill(left, bottom - 1, right, bottom, BUTTON_BORDER_COLOR);
            guiGraphics.fill(left, top, left + 1, bottom, BUTTON_BORDER_COLOR);
            guiGraphics.fill(right - 1, top, right, bottom, BUTTON_BORDER_COLOR);
        }

        int textColor = active ? ACCENT_COLOR : BUTTON_TEXT_COLOR;
        guiGraphics.drawString(font, trimmed(font, label, width - 4), (left + right) / 2 - trimmedWidth(font, label, width - 4) / 2,
                top + (BUTTON_HEIGHT - 8) / 2, textColor, false);
    }

    private void renderTriple(
            GuiGraphics guiGraphics, Font font, int rowY, int index,
            boolean active, int mouseX, int mouseY, Component label) {
        this.renderButton(guiGraphics, font, INSET + index * (TRIPLE_WIDTH + 6), rowY, TRIPLE_WIDTH,
                active, mouseX, mouseY, label);
    }

    private static String trimmed(Font font, Component label, int maxWidth) {
        String text = label.getString();
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth);
    }

    private static int trimmedWidth(Font font, Component label, int maxWidth) {
        return font.width(trimmed(font, label, maxWidth));
    }
}
