package com.heartstead.vault;

/**
 * The Vault screen's geometry, in one place because it is shared across the source-set split:
 * {@link VaultMenu} (common) registers real slots at these coordinates and {@code VaultScreen}
 * (client) draws the panel at them. When those two disagree, clicking a visible slot lands outside
 * every slot and vanilla drops the carried stack on the floor. That is not hypothetical — a
 * 19-pixel mismatch here did exactly that.
 *
 * <p><b>The panel is drawn, not blitted from {@code generic_54.png}.</b> A chest texture is 176 wide,
 * which leaves a 6-pixel margin beside a nine-column grid — not enough for vanilla's 12-pixel
 * scroller without it sitting on top of the last column. Rather than drop a column or overlap one,
 * the screen paints its own panel in vanilla's greys at whatever width the contents actually need.
 */
public final class VaultLayout {

    public static final int COLS = 9;
    public static final int ROWS = 5;
    public static final int CELL = 18;

    /** Left margin, grid, gutter, scroller, right margin. */
    public static final int PANEL_WIDTH = 8 + COLS * CELL + 4 + 12 + 8;

    public static final int GRID_X = 8;
    public static final int GRID_Y = 26;

    public static final int SEARCH_X = 8;
    public static final int SEARCH_Y = 6;
    public static final int SEARCH_WIDTH = 118;
    public static final int SEARCH_HEIGHT = 16;

    public static final int SORT_X = SEARCH_X + SEARCH_WIDTH + 4;
    public static final int SORT_Y = SEARCH_Y;
    public static final int SORT_WIDTH = PANEL_WIDTH - SORT_X - 8;
    public static final int SORT_HEIGHT = SEARCH_HEIGHT;

    public static final int SCROLLER_X = GRID_X + COLS * CELL + 4;
    public static final int SCROLLER_WIDTH = 12;
    public static final int SCROLLER_HEIGHT = 15;

    /** Player inventory, centred under a panel wider than the vanilla 176. */
    public static final int PLAYER_INV_X = (PANEL_WIDTH - COLS * CELL) / 2;
    public static final int PLAYER_INV_Y = GRID_Y + ROWS * CELL + 17;

    /** {@code addStandardInventorySlots} puts the hotbar 58 below the first main row. */
    public static final int HOTBAR_Y = PLAYER_INV_Y + 58;

    public static final int PANEL_HEIGHT = HOTBAR_Y + CELL + 8;

    public static final int INVENTORY_LABEL_X = PLAYER_INV_X;
    public static final int INVENTORY_LABEL_Y = PLAYER_INV_Y - 11;

    /** §2.3's four upgrade slots (see {@link VaultUpgradeKind}), evenly spaced across tab 1. */
    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int UPGRADE_SLOT_SPACING = 40;
    public static final int UPGRADE_SLOT_Y = 62;
    public static final int UPGRADE_SLOT_X =
            (PANEL_WIDTH - ((UPGRADE_SLOT_COUNT - 1) * UPGRADE_SLOT_SPACING + CELL)) / 2;

    /**
     * §2.4 — each upgrade slot arms on drop and buys nothing until this fires, the same two-step
     * shape as an enchanting table's confirm click. One button per slot, centred under its socket.
     */
    public static final int CONFIRM_BUTTON_WIDTH = 32;
    public static final int CONFIRM_BUTTON_HEIGHT = 16;
    public static final int CONFIRM_BUTTON_Y = UPGRADE_SLOT_Y + CELL + 4;

    public static int confirmButtonX(int index) {
        return upgradeSlotX(index) + (CELL - CONFIRM_BUTTON_WIDTH) / 2;
    }

    public static final int ACTIVATE_WIDTH = 104;
    public static final int ACTIVATE_HEIGHT = 16;
    public static final int ACTIVATE_X = (PANEL_WIDTH - ACTIVATE_WIDTH) / 2;

    /** Below the confirm-button row, and clear of the inventory label — which it used to sit on top of. */
    public static final int ACTIVATE_Y = CONFIRM_BUTTON_Y + CONFIRM_BUTTON_HEIGHT + 4;

    public static final int TAB_WIDTH = 28;
    public static final int TAB_HEIGHT = 20;

    /** Left edge of the upgrade slot for {@code index}, panel-relative. */
    public static int upgradeSlotX(int index) {
        return UPGRADE_SLOT_X + index * UPGRADE_SLOT_SPACING;
    }

    private VaultLayout() {
    }
}
