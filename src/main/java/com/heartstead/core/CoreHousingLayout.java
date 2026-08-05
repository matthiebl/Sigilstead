package com.heartstead.core;

/**
 * Screen geometry for the §4.2 housings, in common code for the same reason {@code CodexLayout} is:
 * {@link CoreHousingMenu} (common) registers real slots at these coordinates and
 * {@code CoreHousingScreen} (client) paints the panel at them, so a client-only constant referenced
 * from a common {@code Slot} would crash a dedicated server (CONVENTIONS.md §3).
 *
 * <p>Deliberately small. §4 has no screen at all for attunement (§4.1: "the tooltip is the entire
 * UI"); the housing needs one only because §4.2 sockets by placing a core in a slot, so this is a
 * core slot, a grid of buffer slots and a status line — not a machine GUI.
 *
 * <p>Everything below the buffer is a function of how many buffer slots there are, because
 * {@code housing_slots} is config (CONVENTIONS.md §5) and a fixed layout would overlap the player's
 * inventory the moment an operator raised it.
 */
public final class CoreHousingLayout {

    private CoreHousingLayout() {
    }

    public static final int PANEL_WIDTH = 176;
    public static final int SLOT_SIZE = 18;

    public static final int CORE_SLOT_X = 80;
    public static final int CORE_SLOT_Y = 18;

    public static final int STATUS_X = 8;
    public static final int STATUS_Y = 40;

    public static final int BUFFER_X = 8;
    public static final int BUFFER_Y = 52;
    public static final int BUFFER_COLUMNS = 9;

    public static final int PLAYER_INV_X = 8;

    /** Vanilla's gap between the three inventory rows and the hotbar. */
    private static final int HOTBAR_GAP = 4;

    public static int bufferSlotX(int index) {
        return BUFFER_X + (index % BUFFER_COLUMNS) * SLOT_SIZE;
    }

    public static int bufferSlotY(int index) {
        return BUFFER_Y + (index / BUFFER_COLUMNS) * SLOT_SIZE;
    }

    public static int bufferRows(int bufferSlots) {
        return Math.max(1, (bufferSlots + BUFFER_COLUMNS - 1) / BUFFER_COLUMNS);
    }

    public static int playerInvY(int bufferSlots) {
        return BUFFER_Y + bufferRows(bufferSlots) * SLOT_SIZE + 14;
    }

    public static int hotbarY(int bufferSlots) {
        return playerInvY(bufferSlots) + 3 * SLOT_SIZE + HOTBAR_GAP;
    }

    public static int panelHeight(int bufferSlots) {
        return hotbarY(bufferSlots) + SLOT_SIZE + 8;
    }

    public static int inventoryLabelY(int bufferSlots) {
        return playerInvY(bufferSlots) - 11;
    }
}
