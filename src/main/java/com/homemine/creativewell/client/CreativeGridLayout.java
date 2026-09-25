package com.homemine.creativewell.client;

import net.minecraft.util.Mth;

/**
 * Pure geometry for {@link CreativeGridScreen}, recomputed from the GUI-scaled screen size.
 *
 * <p>The panel is never a fixed 195x136 box: the number of columns and rows is derived from the room
 * that is actually available, so the item grid grows with the window. All coordinates are relative to
 * the panel's top-left corner ({@code leftPos}/{@code topPos}), matching what {@link net.minecraft.world.inventory.Slot}
 * expects.</p>
 */
public final class CreativeGridLayout {
    /** Side length of a single slot cell. */
    public static final int CELL = 18;
    /** Width of the vertical tab strip along the left edge of the panel. */
    public static final int TAB_BAR_WIDTH = TabListPanel.TAB_BAR_WIDTH;
    /** Width of the scroll track along the right edge of the grid. */
    public static final int SCROLLBAR_WIDTH = 12;
    /** Height reserved at the top of the panel for the search bar. */
    public static final int SEARCH_BAR_HEIGHT = 14;
    /** Height reserved at the bottom of the panel for the player inventory + hotbar. */
    public static final int PLAYER_AREA_HEIGHT = 100;
    /** Gap between the panel's inner blocks. */
    public static final int PADDING = 4;
    /** Gap between the panel and the screen edge. */
    public static final int MARGIN = 4;
    /** The grid never drops below a single vanilla row. */
    public static final int MIN_COLUMNS = 9;
    /** Width and height of the crafting grid, in slots. */
    public static final int CRAFT_GRID_SIZE = 3;
    /** Gap between the crafting grid and its result slot; the arrow is drawn in here. */
    public static final int CRAFT_RESULT_GAP = 16;
    /** Gap between the crafting block and the backpack block of the player area. */
    public static final int CRAFT_PLAYER_GAP = 8;
    /** Gap between the backpack block and the armor block of the player area. */
    public static final int PLAYER_ARMOR_GAP = 6;
    /** Side length of the gear button that opens the settings panel. */
    public static final int GEAR_SIZE = 14;
    /** Gap between the search field and the gear button. */
    public static final int GEAR_GAP = 2;
    /** Width of the player model box in the player area. */
    public static final int AVATAR_WIDTH = 46;
    /** Height of the player model box in the player area. */
    public static final int AVATAR_HEIGHT = 62;
    /** Gap between the player model box and the crafting grid. */
    public static final int AVATAR_GAP = 4;
    /** Width of the crafting + backpack + armor blocks, without the player model box. */
    public static final int PLAYER_BLOCK_WIDTH = CRAFT_GRID_SIZE * CELL + CRAFT_RESULT_GAP + CELL
            + CRAFT_PLAYER_GAP + 9 * CELL + PLAYER_ARMOR_GAP + 2 * CELL;
    /** Width the player area needs no matter how few columns the grid has. */
    public static final int PLAYER_BAND_WIDTH = AVATAR_WIDTH + AVATAR_GAP + PLAYER_BLOCK_WIDTH;

    private final int panelX;
    private final int panelY;
    private final int panelWidth;
    private final int panelHeight;
    private final int columns;
    private final int rows;
    private final int gridX;
    private final int gridY;
    private final int searchX;
    private final int searchY;
    private final int searchWidth;
    private final int scrollbarX;
    private final int scrollbarY;
    private final int scrollbarHeight;
    private final int inventoryX;
    private final int inventoryY;
    private final int hotbarX;
    private final int hotbarY;
    private final int armorX;
    private final int armorY;
    private final int offhandX;
    private final int offhandY;
    private final int destroyX;
    private final int destroyY;
    private final int craftingX;
    private final int craftingY;
    private final int resultX;
    private final int resultY;
    private final int gearX;
    private final int gearY;
    private final int avatarX;
    private final int avatarY;

    private CreativeGridLayout(
            int panelX, int panelY, int panelWidth, int panelHeight, int columns, int rows,
            int gridX, int gridY, int searchX, int searchY, int searchWidth,
            int scrollbarX, int scrollbarY, int scrollbarHeight,
            int inventoryX, int inventoryY, int hotbarX, int hotbarY,
            int armorX, int armorY, int offhandX, int offhandY,
            int destroyX, int destroyY,
            int craftingX, int craftingY, int resultX, int resultY,
            int gearX, int gearY, int avatarX, int avatarY) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.columns = columns;
        this.rows = rows;
        this.gridX = gridX;
        this.gridY = gridY;
        this.searchX = searchX;
        this.searchY = searchY;
        this.searchWidth = searchWidth;
        this.scrollbarX = scrollbarX;
        this.scrollbarY = scrollbarY;
        this.scrollbarHeight = scrollbarHeight;
        this.inventoryX = inventoryX;
        this.inventoryY = inventoryY;
        this.hotbarX = hotbarX;
        this.hotbarY = hotbarY;
        this.armorX = armorX;
        this.armorY = armorY;
        this.offhandX = offhandX;
        this.offhandY = offhandY;
        this.destroyX = destroyX;
        this.destroyY = destroyY;
        this.craftingX = craftingX;
        this.craftingY = craftingY;
        this.resultX = resultX;
        this.resultY = resultY;
        this.gearX = gearX;
        this.gearY = gearY;
        this.avatarX = avatarX;
        this.avatarY = avatarY;
    }

    /**
     * Fits the panel into the room that is left between the two strips JEI reserves, {@code jeiLeft} and
     * {@code jeiRight}.
     *
     * <p>The player area needs {@link #PLAYER_BAND_WIDTH} pixels whatever the column count is, so the panel is
     * never narrower than that band: with a small window the panel keeps the player area intact and JEI is the
     * one that gets squeezed (the screen hands JEI the panel rectangle as an exclusion area). The band is also
     * anchored to the middle of the screen, so the player area stays centred for every column count.</p>
     */
    public static CreativeGridLayout compute(
            int screenWidth, int screenHeight, int maxColumns, int jeiLeft, int jeiRight) {
        int availableHeight = Math.max(0, screenHeight - MARGIN * 2);

        int gridX = TAB_BAR_WIDTH + PADDING;
        int gridY = SEARCH_BAR_HEIGHT + PADDING;

        // The player band starts at gridX, so its centre sits at gridX + PLAYER_BAND_WIDTH / 2. Moving the panel
        // so that centre lands on the middle of the screen centres the player area for every column count.
        int bandCentre = gridX + PLAYER_BAND_WIDTH / 2;
        int panelX = Math.max(MARGIN + jeiLeft, screenWidth / 2 - bandCentre);

        int roomRight = screenWidth - MARGIN - jeiRight;
        int roomForGrid = roomRight - panelX - gridX - PADDING - SCROLLBAR_WIDTH;
        int wanted = Math.max(roomForGrid, PLAYER_BAND_WIDTH);
        int columns = Mth.clamp(wanted / CELL, MIN_COLUMNS, Math.max(MIN_COLUMNS, maxColumns));

        // The content block spans the grid plus its scroll track, but never less than the player band.
        int contentWidth = Math.max(columns * CELL + PADDING + SCROLLBAR_WIDTH, PLAYER_BAND_WIDTH);
        int panelWidth = gridX + contentWidth + PADDING;

        int gridAreaHeight = availableHeight - SEARCH_BAR_HEIGHT - PLAYER_AREA_HEIGHT - PADDING * 2;
        int rows = Math.max(1, gridAreaHeight / CELL);
        int panelHeight = gridY + rows * CELL + PADDING + PLAYER_AREA_HEIGHT;
        int panelY = MARGIN + Math.max(0, (availableHeight - panelHeight) / 2);

        // The player area is one row band: the player model, 3x3 crafting grid -> arrow -> result, then the
        // backpack, then the armor block with the offhand slot underneath it.
        int inventoryY = gridY + rows * CELL + PADDING + 6;
        int craftingX = gridX + AVATAR_WIDTH + AVATAR_GAP;
        int craftingY = inventoryY;
        int resultX = craftingX + CRAFT_GRID_SIZE * CELL + CRAFT_RESULT_GAP;
        int resultY = craftingY;
        int inventoryX = resultX + CELL + CRAFT_PLAYER_GAP;
        int hotbarX = inventoryX;
        int hotbarY = inventoryY + 3 * CELL + 8;

        int armorX = inventoryX + 9 * CELL + PLAYER_ARMOR_GAP;
        int armorY = inventoryY;
        int offhandX = armorX;
        int offhandY = armorY + 2 * CELL + 4;

        // Vanilla puts its destroy slot beside the inventory; here it goes straight under the offhand slot,
        // which is the only free cell in the armor column.
        int destroyX = armorX;
        int destroyY = offhandY + CELL + PADDING;

        int avatarX = gridX;
        int avatarY = inventoryY;

        // The gear shares the search strip with the field and sits against the content block's right edge.
        int searchWidth = contentWidth - GEAR_SIZE - GEAR_GAP;
        int gearX = gridX + contentWidth - GEAR_SIZE;
        int gearY = PADDING - 1;

        return new CreativeGridLayout(
                panelX, panelY, panelWidth, panelHeight, columns, rows,
                gridX, gridY, gridX, PADDING - 1, searchWidth,
                gridX + columns * CELL + PADDING, gridY, rows * CELL,
                inventoryX, inventoryY, hotbarX, hotbarY,
                armorX, armorY, offhandX, offhandY,
                destroyX, destroyY,
                craftingX, craftingY, resultX, resultY,
                gearX, gearY, avatarX, avatarY);
    }

    /** Left edge of the panel, in screen pixels; {@code AbstractContainerScreen} uses it as {@code leftPos}. */
    public int x() {
        return this.panelX;
    }

    /** Top edge of the panel, in screen pixels; {@code AbstractContainerScreen} uses it as {@code topPos}. */
    public int y() {
        return this.panelY;
    }

    /** Left edge of the gear button, panel-relative. */
    public int gearX() {
        return this.gearX;
    }

    /** Top edge of the gear button, panel-relative. */
    public int gearY() {
        return this.gearY;
    }

    public int panelWidth() {
        return this.panelWidth;
    }

    public int panelHeight() {
        return this.panelHeight;
    }

    public int columns() {
        return this.columns;
    }

    public int rows() {
        return this.rows;
    }

    /** Number of item slots visible at once; also the size of the backing container. */
    public int visibleSlotCount() {
        return this.columns * this.rows;
    }

    public int gridX() {
        return this.gridX;
    }

    public int gridY() {
        return this.gridY;
    }

    public int searchX() {
        return this.searchX;
    }

    public int searchY() {
        return this.searchY;
    }

    public int searchWidth() {
        return this.searchWidth;
    }

    public int scrollbarX() {
        return this.scrollbarX;
    }

    public int scrollbarY() {
        return this.scrollbarY;
    }

    public int scrollbarHeight() {
        return this.scrollbarHeight;
    }

    public int inventoryX() {
        return this.inventoryX;
    }

    public int inventoryY() {
        return this.inventoryY;
    }

    public int hotbarX() {
        return this.hotbarX;
    }

    public int hotbarY() {
        return this.hotbarY;
    }

    public int armorX() {
        return this.armorX;
    }

    public int armorY() {
        return this.armorY;
    }

    public int offhandX() {
        return this.offhandX;
    }

    public int offhandY() {
        return this.offhandY;
    }

    public int destroyX() {
        return this.destroyX;
    }

    public int destroyY() {
        return this.destroyY;
    }

    public int craftingX() {
        return this.craftingX;
    }

    public int craftingY() {
        return this.craftingY;
    }

    public int resultX() {
        return this.resultX;
    }

    public int resultY() {
        return this.resultY;
    }

    /** Left edge of the player model box, panel-relative. */
    public int avatarX() {
        return this.avatarX;
    }

    /** Top edge of the player model box, panel-relative. */
    public int avatarY() {
        return this.avatarY;
    }
}
