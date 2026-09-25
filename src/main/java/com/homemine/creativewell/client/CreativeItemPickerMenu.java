package com.homemine.creativewell.client;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Client-only menu backing {@link CreativeGridScreen}.
 *
 * <p>It is the dynamic counterpart of vanilla's {@code CreativeModeInventoryScreen.ItemPickerMenu}: instead of a
 * hard-coded 5x9 page it holds {@code columns * rows} grid slots whose size is decided by {@link CreativeGridLayout}.
 * The container backing those slots is owned by this class (vanilla's static one is package-private) and is rebuilt
 * whenever the layout changes, so a resized window simply renders more or fewer slots.</p>
 *
 * <p>The menu never exists on the server (container id is a throwaway value) and never receives a
 * {@code ContainerSynchronizer}, so {@code broadcastChanges()} only walks the local slots and performs no network
 * writes; creative item transfer is driven explicitly by {@code MultiPlayerGameMode#handleCreativeModeItemAdd}.</p>
 */
public class CreativeItemPickerMenu extends AbstractContainerMenu {
    /**
     * Menu indices {@code 0..45} mirror {@code InventoryMenu} in its own slot order. That identity is what makes
     * the vanilla sync paths line up: the client applies {@code ClientboundContainerSetSlotPacket} for container id
     * 0 through {@code getSlot(i)}, and the server applies {@code ServerboundSetCreativeModeSlotPacket} for
     * {@code slotNum 1..45} through {@code player.inventoryMenu.getSlot(slotNum)}. Keeping the same order means both
     * sides agree without any index translation.
     */
    private static final int PLAYER_SLOT_COUNT = 46;
    /** Off-screen coordinate vanilla uses for the crafting grid slots it keeps around but never draws. */
    private static final int OFFSCREEN = -2000;
    /** Menu index of the first hotbar slot; identical to its {@code InventoryMenu} position. */
    public static final int HOTBAR_SLOT_INDEX = 36;
    /** Menu index of the offhand slot; identical to its {@code InventoryMenu} position. */
    public static final int OFFHAND_SLOT_INDEX = 45;

    /** All stacks currently displayable in the picker grid. */
    public final NonNullList<ItemStack> items = NonNullList.create();

    /** The player's real inventory menu; the carried stack and every player slot mirror it, exactly like vanilla. */
    private final AbstractContainerMenu inventoryMenu;

    /** Owner of the menu; needed to resolve crafting recipes against the level. */
    private final Player player;

    /** The client-only 3x3 crafting block; its containers outlive a slot rebuild. */
    private final CraftingArea craftingArea;

    /** The destroy slot, rebuilt by every {@link #applyLayout} but always last in {@link #slots}. */
    private Slot destroySlot;

    /** Backing container of the visible grid page; its size equals {@code columns * rows}. */
    private SimpleContainer container = new SimpleContainer(CreativeGridLayout.MIN_COLUMNS * 5);
    private int columns = CreativeGridLayout.MIN_COLUMNS;
    private int rows = 5;
    private float scrollOffs;

    public CreativeItemPickerMenu(Player player) {
        super(null, 0);
        this.player = player;
        this.inventoryMenu = player.inventoryMenu;
        this.craftingArea = new CraftingArea(this);
    }

    /** The container behind the grid slots, used by the screen to tell creative slots from inventory slots. */
    public Container container() {
        return this.container;
    }

    public CraftingArea craftingArea() {
        return this.craftingArea;
    }

    /** Vanilla's "destroy item" slot; the screen draws the cross and handles the clicks itself. */
    @Nullable
    public Slot destroySlot() {
        return this.destroySlot;
    }

    public int columns() {
        return this.columns;
    }

    public int rows() {
        return this.rows;
    }

    public float scrollOffs() {
        return this.scrollOffs;
    }

    /**
     * Rebuilds the player slots and the grid slots for the given layout. Called on every screen (re)init, which
     * includes window resizes, because the number of visible slots may have changed.
     */
    public void applyLayout(CreativeGridLayout layout) {
        this.columns = layout.columns();
        this.rows = layout.rows();
        this.container = new SimpleContainer(this.columns * this.rows);
        this.slots.clear();

        // Player slots come first so that their indices stay identical to InventoryMenu's.
        this.addPlayerSlots(layout);

        int gridX = layout.gridX();
        int gridY = layout.gridY();
        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
                this.addSlot(new CreativeSlot(
                        this.container,
                        column + row * this.columns,
                        gridX + column * CreativeGridLayout.CELL,
                        gridY + row * CreativeGridLayout.CELL));
            }
        }

        // The crafting block sits at the end so that the player slot indices above stay untouched.
        int craftingX = layout.craftingX();
        int craftingY = layout.craftingY();
        for (int index = 0; index < CraftingArea.SIZE * CraftingArea.SIZE; index++) {
            this.addSlot(new Slot(
                    this.craftingArea.input(),
                    index,
                    craftingX + index % CraftingArea.SIZE * CreativeGridLayout.CELL,
                    craftingY + index / CraftingArea.SIZE * CreativeGridLayout.CELL));
        }
        this.addSlot(new ResultSlot(this.craftingArea.resultContainer(), 0, layout.resultX(), layout.resultY()));

        // Appended last, after every player slot, so the 0..45 player indices above are unaffected.
        this.destroySlot = new DestroySlot(layout.destroyX(), layout.destroyY());
        this.addSlot(this.destroySlot);

        this.scrollTo(this.scrollOffs);
    }

    /**
     * Mirrors every slot of {@link InventoryMenu} into this menu at the same index, just placed at the coordinates
     * the layout picked. The crafting grid ({@code 0..4}) is parked off-screen like vanilla does, because the 3x3
     * crafting area is a separate client-only concern.
     */
    private void addPlayerSlots(CreativeGridLayout layout) {
        for (int index = 0; index < PLAYER_SLOT_COUNT; index++) {
            int x;
            int y;
            if (index >= 5 && index < 9) {
                // Armor, laid out as the usual 2x2 block: helmet/chestplate on top, leggings/boots below.
                int offset = index - 5;
                x = layout.armorX() + offset % 2 * CreativeGridLayout.CELL;
                y = layout.armorY() + offset / 2 * CreativeGridLayout.CELL;
            } else if (index >= 9 && index < HOTBAR_SLOT_INDEX) {
                x = layout.inventoryX() + (index - 9) % 9 * CreativeGridLayout.CELL;
                y = layout.inventoryY() + (index - 9) / 9 * CreativeGridLayout.CELL;
            } else if (index >= HOTBAR_SLOT_INDEX && index < OFFHAND_SLOT_INDEX) {
                x = layout.hotbarX() + (index - HOTBAR_SLOT_INDEX) * CreativeGridLayout.CELL;
                y = layout.hotbarY();
            } else if (index == OFFHAND_SLOT_INDEX) {
                x = layout.offhandX();
                y = layout.offhandY();
            } else {
                x = OFFSCREEN;
                y = OFFSCREEN;
            }

            this.addSlot(new PlayerSlot(this.inventoryMenu.getSlot(index), x, y));
        }
    }

    public boolean canScroll() {
        return this.items.size() > this.columns * this.rows;
    }

    /** Number of rows the content overflows by; 0 when everything fits. */
    protected int calculateRowCount() {
        return Math.max(0, Mth.positiveCeilDiv(this.items.size(), this.columns) - this.rows);
    }

    public int getRowIndexForScroll(float scrollOffs) {
        return Math.max((int) (scrollOffs * this.calculateRowCount() + 0.5), 0);
    }

    public float getScrollForRowIndex(int rowIndex) {
        return this.calculateRowCount() == 0 ? 0.0F : Mth.clamp((float) rowIndex / (float) this.calculateRowCount(), 0.0F, 1.0F);
    }

    /** Applies one mouse-wheel step and copies the resulting page into the backing container. */
    public void scrollBy(double input) {
        if (this.calculateRowCount() == 0) {
            return;
        }
        this.scrollOffs = Mth.clamp(this.scrollOffs - (float) (input / (double) this.calculateRowCount()), 0.0F, 1.0F);
        this.scrollTo(this.scrollOffs);
    }

    public void resetScroll() {
        this.scrollOffs = 0.0F;
        this.scrollTo(0.0F);
    }

    /** Copies the {@code rows}-tall window of {@link #items} that starts at the current scroll offset. */
    public void scrollTo(float pos) {
        int firstRow = this.getRowIndexForScroll(pos);

        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
                int source = column + (row + firstRow) * this.columns;
                int target = column + row * this.columns;
                this.container.setItem(
                        target,
                        source >= 0 && source < this.items.size() ? this.items.get(source) : ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Keeps the crafting result in sync with the grid.
     *
     * <p>{@code AbstractContainerMenu} neither registers a {@code ContainerListener} nor dispatches anything from
     * {@code slotsChanged}, so the only hook available is this override. The input container calls it itself on every
     * mutation, which is exactly the signal needed.</p>
     */
    @Override
    public void slotsChanged(Container container) {
        if (this.craftingArea.isInputSlot(container)) {
            this.craftingArea.refresh(this.player.level());
        }

        super.slotsChanged(container);
    }

    /**
     * Shift-clicking the hotbar row clears the matching slot, mirroring vanilla's creative picker. The rest of the
     * player area is left alone: emptying it would delete real items rather than move them.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= HOTBAR_SLOT_INDEX && index < OFFHAND_SLOT_INDEX) {
            Slot slot = this.slots.get(index);
            if (slot.hasItem()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.container;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != this.container;
    }

    @Override
    public ItemStack getCarried() {
        return this.inventoryMenu.getCarried();
    }

    @Override
    public void setCarried(ItemStack stack) {
        this.inventoryMenu.setCarried(stack);
    }

    /**
     * A picker slot that refuses locked stacks, e.g. the paper placeholders vanilla puts in an empty saved hotbar.
     */
    private static class CreativeSlot extends Slot {
        CreativeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = this.getItem();
            return super.mayPickup(player) && !stack.isEmpty()
                    ? stack.isItemEnabled(player.level().enabledFeatures()) && !stack.has(DataComponents.CREATIVE_SLOT_LOCK)
                    : stack.isEmpty();
        }
    }

    /**
     * The recipe preview. It can be taken but never filled, and taking it is handled by the screen so that it never
     * consumes the grid contents.
     */
    private static class ResultSlot extends Slot {
        ResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * Vanilla's "destroy item" slot. It holds nothing: {@link #mayPlace} is false so no drag or drop can ever put a
     * stack on it, and {@link #mayPickup} is false so the menu's own transfer paths skip it. The screen intercepts
     * clicks on it before the {@code mayPickup} guard is reached.
     */
    private static class DestroySlot extends Slot {
        DestroySlot(int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /**
     * A view of one of the player's real {@link InventoryMenu} slots, drawn at a position this screen chose.
     *
     * <p>It reuses the wrapped slot's container and container index, then forwards everything that matters. Wrapping
     * instead of re-creating the slot is required because vanilla replaces a few inventory slots with anonymous
     * subclasses that carry behaviour (armor slots enforce {@code canEquip} and supply the empty-armor icons); a plain
     * {@link Slot} would silently drop that.</p>
     */
    private static class PlayerSlot extends Slot {
        private final Slot target;

        PlayerSlot(Slot target, int x, int y) {
            super(target.container, target.getContainerSlot(), x, y);
            this.target = target;
        }

        @Override
        public ItemStack getItem() {
            return this.target.getItem();
        }

        @Override
        public boolean hasItem() {
            return this.target.hasItem();
        }

        @Override
        public void set(ItemStack stack) {
            this.target.set(stack);
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            this.target.setByPlayer(newStack, oldStack);
        }

        @Override
        public void setChanged() {
            this.target.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return this.target.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.target.getMaxStackSize(stack);
        }

        @Override
        public ItemStack remove(int amount) {
            return this.target.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            this.target.onTake(player, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.target.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.target.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return this.target.isActive();
        }

        @Nullable
        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return this.target.getNoItemIcon();
        }
    }
}
