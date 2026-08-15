package com.heartstead.client.screen;

import com.heartstead.client.codex.CodexClientCache;
import com.heartstead.codex.CodexLayout;
import com.heartstead.codex.CodexMenu;
import com.heartstead.network.CodexArchivePayload;
import com.heartstead.network.CodexBuyCapacityPayload;
import com.heartstead.network.CodexSealPayload;
import com.heartstead.network.CodexSyncPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * DESIGN.md §3.3 — the Codex screen. Archive and buy-capacity are both a slot plus a confirm button,
 * the same two-step shape {@code VaultScreen}'s upgrade slots use; the empower list is drawn
 * stonecutter-style, one clickable row per archived enchantment the Tome in the slot could be sealed
 * against.
 *
 * <p>Geometry comes from {@link CodexLayout}; painting rather than blitting a chest texture, for the
 * same reason {@code VaultScreen} does.
 */
public class CodexScreen extends AbstractContainerScreen<CodexMenu> {

    private static final int TEXT_COLOR = HsGuiPainting.TEXT_COLOR;
    private static final int ROW_HOVER = 0x40FFFFFF;

    private Button archiveButton;
    private Button capacityButton;
    private int scrollRows;

    public CodexScreen(CodexMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CodexLayout.PANEL_WIDTH, CodexLayout.PANEL_HEIGHT);
        this.inventoryLabelX = CodexLayout.INVENTORY_LABEL_X;
        this.inventoryLabelY = CodexLayout.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        archiveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.heartstead.codex.archive"),
                        b -> ClientPlayNetworking.send(new CodexArchivePayload()))
                .bounds(leftPos + CodexLayout.ARCHIVE_BUTTON_X, topPos + CodexLayout.ARCHIVE_BUTTON_Y,
                        CodexLayout.ARCHIVE_BUTTON_WIDTH, CodexLayout.ARCHIVE_BUTTON_HEIGHT)
                .build());
        capacityButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.heartstead.codex.buy_capacity"),
                        b -> ClientPlayNetworking.send(new CodexBuyCapacityPayload()))
                .bounds(leftPos + CodexLayout.CAPACITY_BUTTON_X, topPos + CodexLayout.CAPACITY_BUTTON_Y,
                        CodexLayout.CAPACITY_BUTTON_WIDTH, CodexLayout.CAPACITY_BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        archiveButton.active = !menu.slots.get(0).getItem().isEmpty();
        capacityButton.active = !menu.slots.get(1).getItem().isEmpty();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        HsGuiPainting.panel(graphics, leftPos, topPos, CodexLayout.PANEL_WIDTH, CodexLayout.PANEL_HEIGHT);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                HsGuiPainting.socket(graphics, leftPos + CodexLayout.PLAYER_INV_X + col * CodexLayout.CELL,
                        topPos + CodexLayout.PLAYER_INV_Y + row * CodexLayout.CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            HsGuiPainting.socket(graphics, leftPos + CodexLayout.PLAYER_INV_X + col * CodexLayout.CELL,
                    topPos + CodexLayout.HOTBAR_Y);
        }

        HsGuiPainting.socket(graphics, leftPos + CodexLayout.ARCHIVE_SLOT_X, topPos + CodexLayout.ARCHIVE_SLOT_Y);
        HsGuiPainting.socket(graphics, leftPos + CodexLayout.CAPACITY_SLOT_X, topPos + CodexLayout.CAPACITY_SLOT_Y);
        HsGuiPainting.socket(graphics, leftPos + CodexLayout.TOME_SLOT_X, topPos + CodexLayout.TOME_SLOT_Y);
        HsGuiPainting.socket(graphics, leftPos + CodexLayout.SEALED_TOME_SLOT_X, topPos + CodexLayout.SEALED_TOME_SLOT_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        CodexSyncPayload state = CodexClientCache.state();
        graphics.text(this.font, Component.translatable("gui.heartstead.codex.capacity_summary",
                        state.archived().size(), state.capacityCap()),
                CodexLayout.CAPACITY_SLOT_X - 60, CodexLayout.CAPACITY_SLOT_Y - 12, TEXT_COLOR, false);

        List<CodexSyncPayload.Entry> visible = visibleRows(state);
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        for (int i = 0; i < visible.size(); i++) {
            int y = CodexLayout.LIST_Y + i * CodexLayout.LIST_ROW_HEIGHT;
            if (inRow(relX, relY, y)) {
                graphics.fill(CodexLayout.LIST_X, y, CodexLayout.LIST_X + CodexLayout.LIST_WIDTH, y + CodexLayout.LIST_ROW_HEIGHT, ROW_HOVER);
            }
            graphics.text(this.font, rowLabel(visible.get(i)), CodexLayout.LIST_X + 2, y + 2, TEXT_COLOR, false);
        }
    }

    private Component rowLabel(CodexSyncPayload.Entry entry) {
        Holder<Enchantment> holder = registries().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .get(entry.enchantment()).orElse(null);
        if (holder == null) {
            return Component.literal(entry.enchantment().identifier().toString());
        }
        return Enchantment.getFullname(holder, entry.level());
    }

    private net.minecraft.core.HolderLookup.Provider registries() {
        return minecraft.level.registryAccess();
    }

    private List<CodexSyncPayload.Entry> visibleRows(CodexSyncPayload state) {
        List<CodexSyncPayload.Entry> all = state.archived();
        int max = Math.max(0, all.size() - CodexLayout.LIST_VISIBLE_ROWS);
        scrollRows = Math.max(0, Math.min(max, scrollRows));
        int from = Math.min(scrollRows, all.size());
        int to = Math.min(from + CodexLayout.LIST_VISIBLE_ROWS, all.size());
        return all.subList(from, to);
    }

    private static boolean inRow(double relX, double relY, int rowY) {
        return relX >= CodexLayout.LIST_X && relX < CodexLayout.LIST_X + CodexLayout.LIST_WIDTH
                && relY >= rowY && relY < rowY + CodexLayout.LIST_ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double relX = event.x() - leftPos;
        double relY = event.y() - topPos;
        List<CodexSyncPayload.Entry> visible = visibleRows(CodexClientCache.state());
        for (int i = 0; i < visible.size(); i++) {
            int y = CodexLayout.LIST_Y + i * CodexLayout.LIST_ROW_HEIGHT;
            if (inRow(relX, relY, y)) {
                ClientPlayNetworking.send(new CodexSealPayload(visible.get(i).enchantment()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollRows -= (int) Math.signum(scrollY);
        return true;
    }
}
