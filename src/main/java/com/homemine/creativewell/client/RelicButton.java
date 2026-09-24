package com.homemine.creativewell.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The small button under the crafting block that hands the player over to Curios' own screen.
 *
 * <p>Curios keeps its slots in a server menu, and a player can only ever have one of those, so its grid can not be
 * laid over the creative picker: the picker is a client-only menu that the server knows nothing about. The way out
 * is to switch screens, exactly as the button Curios itself puts into the vanilla inventory does. The button is
 * hidden rather than greyed out when Curios is not installed, so nothing suggests a screen that does not exist.</p>
 *
 * <p>Like {@link TabListPanel} this is plain geometry plus direct drawing, not a widget: the screen forwards its
 * mouse events here.</p>
 */
public final class RelicButton {
    private static final int WIDTH = 66;
    private static final int HEIGHT = 16;
    /** Gap between the crafting grid and the top of the button. */
    private static final int GAP_ABOVE = 4;

    private static final int COLOR = 0xFF3A3A3A;
    private static final int HOVER_COLOR = 0xFF4E4E4E;
    private static final int BORDER_COLOR = 0xFF232323;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private static final String CURIOS_MOD_ID = "curios";

    private int x;
    private int y;

    /** Whether anything is listening for the open request. */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    /** Anchors the button under the crafting grid. Must be called after {@code leftPos}/{@code topPos} are known. */
    public void position(CreativeGridLayout layout, int leftPos, int topPos) {
        this.x = leftPos + layout.craftingX();
        this.y = topPos + layout.craftingY()
                + CreativeGridLayout.CRAFT_GRID_SIZE * CreativeGridLayout.CELL + GAP_ABOVE;
    }

    /**
     * Handles the press.
     *
     * @return {@code true} when the press landed on the button and the screen change was asked for.
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isAvailable() || !this.isOver(mouseX, mouseY)) {
            return false;
        }

        CuriosBridge.open();
        return true;
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!isAvailable()) {
            return;
        }

        boolean hovered = this.isOver(mouseX, mouseY);
        guiGraphics.fill(this.x, this.y, this.x + WIDTH, this.y + HEIGHT, BORDER_COLOR);
        guiGraphics.fill(this.x + 1, this.y + 1, this.x + WIDTH - 1, this.y + HEIGHT - 1,
                hovered ? HOVER_COLOR : COLOR);

        Component label = Component.translatable("creativewell.relic.open");
        int textWidth = font.width(label);
        guiGraphics.drawString(font, label,
                this.x + (WIDTH - textWidth) / 2, this.y + (HEIGHT - font.lineHeight) / 2 + 1,
                TEXT_COLOR, false);
    }

    private boolean isOver(double mouseX, double mouseY) {
        return mouseX >= (double) this.x && mouseX < (double) (this.x + WIDTH)
                && mouseY >= (double) this.y && mouseY < (double) (this.y + HEIGHT);
    }

    /**
     * Keeps every Curios reference inside its own class file. A nested class is only loaded when it is first used, so
     * on a client without Curios nothing here ever resolves {@code CPacketOpenCurios} and no
     * {@code NoClassDefFoundError} can reach the picker.
     */
    private static final class CuriosBridge {
        private static void open() {
            // The carried stack is deliberately empty. This screen's container menu is the client-only picker, whose
            // carried stack is the local creative cursor; reporting it would ask the server for items it never
            // authorised. Closing the screen is left to the server too: opening the Curios menu comes back as a
            // vanilla open-screen packet, and the client swaps the screen when it arrives.
            PacketDistributor.sendToServer(
                    new top.theillusivec4.curios.common.network.client.CPacketOpenCurios(ItemStack.EMPTY));
        }
    }
}
