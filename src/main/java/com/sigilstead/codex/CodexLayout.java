package com.sigilstead.codex;

/**
 * The Codex screen's geometry, shared across the source-set split the same way
 * {@link com.sigilstead.vault.VaultLayout} is: {@link CodexMenu} (common) registers real slots at
 * these coordinates and {@code CodexScreen} (client) paints the panel at them.
 */
public final class CodexLayout {

    public static final int CELL = 18;
    public static final int PANEL_WIDTH = 176;

    public static final int ARCHIVE_SLOT_X = 26;
    public static final int ARCHIVE_SLOT_Y = 20;
    public static final int ARCHIVE_BUTTON_WIDTH = 60;
    public static final int ARCHIVE_BUTTON_HEIGHT = 16;
    public static final int ARCHIVE_BUTTON_X = ARCHIVE_SLOT_X + CELL + 8;
    public static final int ARCHIVE_BUTTON_Y = ARCHIVE_SLOT_Y + 1;

    public static final int CAPACITY_SLOT_X = 132;
    public static final int CAPACITY_SLOT_Y = 20;
    public static final int CAPACITY_BUTTON_WIDTH = 34;
    public static final int CAPACITY_BUTTON_HEIGHT = 16;
    public static final int CAPACITY_BUTTON_X = CAPACITY_SLOT_X - CAPACITY_BUTTON_WIDTH - 6;
    public static final int CAPACITY_BUTTON_Y = CAPACITY_SLOT_Y + 1;

    public static final int TOME_SLOT_X = 26;
    public static final int TOME_SLOT_Y = 52;
    public static final int SEALED_TOME_SLOT_X = 26;
    public static final int SEALED_TOME_SLOT_Y = 76;

    /** The scrollable list of archived enchantments, stonecutter-screen style. */
    public static final int LIST_X = 70;
    public static final int LIST_Y = 46;
    public static final int LIST_WIDTH = 90;
    public static final int LIST_ROW_HEIGHT = 12;
    public static final int LIST_VISIBLE_ROWS = 4;
    public static final int LIST_HEIGHT = LIST_VISIBLE_ROWS * LIST_ROW_HEIGHT;

    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 104;
    public static final int HOTBAR_Y = PLAYER_INV_Y + 58;
    public static final int PANEL_HEIGHT = HOTBAR_Y + CELL + 8;

    public static final int INVENTORY_LABEL_X = PLAYER_INV_X;
    public static final int INVENTORY_LABEL_Y = PLAYER_INV_Y - 11;

    private CodexLayout() {
    }
}
