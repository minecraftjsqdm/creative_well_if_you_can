package com.homemine.creativewell.config;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for the creative picker, written to {@code config/creativewell-client.toml}.
 *
 * <p>The in-game gear button edits exactly these values, so every accessor has a matching setter and the panel is
 * nothing more than a different view of the file.</p>
 */
public final class CreativeGridConfig {
    /** Where the picker grid gets its column cap from. */
    public enum MaxColumnsMode {
        /** Use {@link #maxColumns()}. */
        BOUNDED,
        /** No cap at all: more window means more columns. */
        UNLIMITED,
        /** No cap, and JEI's reserved space is ignored (JEI cannot stay on screen then). */
        IGNORE_JEI
    }

    /** What {@code Shift+left click} on a grid stack does. */
    public enum ShiftClickAction {
        /** Grab a full stack onto the cursor, like vanilla's shift click. */
        GRAB_FULL_STACK,
        /** Push a full stack straight into the survival inventory. */
        STOCK_INVENTORY
    }

    /** Whether the search text is remembered per tab or shared by every tab. */
    public enum SearchShareMode {
        /** Every tab keeps its own search text. */
        PER_TAB,
        /** One search text for every tab. */
        GLOBAL
    }

    /** How this screen's search text follows JEI's own search field. */
    public enum JeiLinkMode {
        /** No synchronisation. */
        OFF,
        /** JEI's text is copied into this screen. */
        ONE_WAY,
        /** Both directions are kept equal. */
        TWO_WAY
    }

    /** Slider lower bound; the picker grid never goes below one vanilla row either. */
    public static final int MIN_COLUMNS = 9;
    /** Slider upper bound. */
    public static final int MAX_COLUMNS = 24;
    /** Slider default. */
    public static final int DEFAULT_MAX_COLUMNS = 16;

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.EnumValue<MaxColumnsMode> MAX_COLUMNS_MODE;
    private static final ModConfigSpec.IntValue MAX_COLUMNS_VALUE;
    private static final ModConfigSpec.EnumValue<ShiftClickAction> SHIFT_CLICK_ACTION;
    private static final ModConfigSpec.EnumValue<SearchShareMode> SEARCH_SHARE_MODE;
    private static final ModConfigSpec.EnumValue<JeiLinkMode> JEI_LINK_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Creative well if you can. The in-game gear button writes this same file.");
        builder.push("grid");
        MAX_COLUMNS_MODE = builder
                .comment("BOUNDED uses maxColumns.",
                        "UNLIMITED drops the column cap.",
                        "IGNORE_JEI drops the cap and also ignores the space JEI reserves, so JEI cannot stay visible.")
                .defineEnum("maxColumnsMode", MaxColumnsMode.BOUNDED);
        MAX_COLUMNS_VALUE = builder
                .comment("Column cap of the picker grid; only read in BOUNDED mode.")
                .defineInRange("maxColumns", DEFAULT_MAX_COLUMNS, MIN_COLUMNS, MAX_COLUMNS);
        SHIFT_CLICK_ACTION = builder
                .comment("GRAB_FULL_STACK puts a full stack on the cursor.",
                        "STOCK_INVENTORY pushes a full stack into the survival inventory instead.")
                .defineEnum("shiftClickAction", ShiftClickAction.GRAB_FULL_STACK);
        SEARCH_SHARE_MODE = builder
                .comment("PER_TAB remembers the search text of every tab separately.",
                        "GLOBAL keeps one search text for every tab.")
                .defineEnum("searchShareMode", SearchShareMode.PER_TAB);
        JEI_LINK_MODE = builder
                .comment("OFF keeps the two search fields apart.",
                        "ONE_WAY copies JEI's text into this screen.",
                        "TWO_WAY keeps both in sync.")
                .defineEnum("jeiLinkMode", JeiLinkMode.OFF);
        builder.pop();
        SPEC = builder.build();
    }

    private CreativeGridConfig() {
    }

    public static MaxColumnsMode maxColumnsMode() {
        return MAX_COLUMNS_MODE.get();
    }

    public static void setMaxColumnsMode(MaxColumnsMode mode) {
        MAX_COLUMNS_MODE.set(mode);
    }

    public static int maxColumns() {
        return MAX_COLUMNS_VALUE.get();
    }

    public static void setMaxColumns(int columns) {
        MAX_COLUMNS_VALUE.set(Mth.clamp(columns, MIN_COLUMNS, MAX_COLUMNS));
    }

    public static ShiftClickAction shiftClickAction() {
        return SHIFT_CLICK_ACTION.get();
    }

    public static void setShiftClickAction(ShiftClickAction action) {
        SHIFT_CLICK_ACTION.set(action);
    }

    public static SearchShareMode searchShareMode() {
        return SEARCH_SHARE_MODE.get();
    }

    public static void setSearchShareMode(SearchShareMode mode) {
        SEARCH_SHARE_MODE.set(mode);
    }

    public static JeiLinkMode jeiLinkMode() {
        return JEI_LINK_MODE.get();
    }

    public static void setJeiLinkMode(JeiLinkMode mode) {
        JEI_LINK_MODE.set(mode);
    }

    /** The cap to hand to the layout: unbounded modes report {@link Integer#MAX_VALUE}. */
    public static int resolvedMaxColumns() {
        return maxColumnsMode() == MaxColumnsMode.BOUNDED ? maxColumns() : Integer.MAX_VALUE;
    }

    /** True when the user accepted crowding JEI out, which also drops the reserved space to zero. */
    public static boolean ignoresJei() {
        return maxColumnsMode() == MaxColumnsMode.IGNORE_JEI;
    }

    /**
     * Flushes the values to disk. Called when a change is finished rather than on every slider pixel, so dragging
     * the slider does not rewrite the file dozens of times.
     */
    public static void save() {
        SPEC.save();
    }
}
