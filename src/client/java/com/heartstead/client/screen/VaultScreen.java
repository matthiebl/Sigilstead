package com.heartstead.client.screen;

import com.heartstead.client.vault.VaultClientCache;
import com.heartstead.network.VaultSyncPayload;
import com.heartstead.network.VaultWithdrawPayload;
import com.heartstead.vault.VaultMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2.4 — the Vault screen: a scrollable grid, live search, click-to-withdraw (left click
 * = a stack, right click = one) and shift-click-to-deposit (handled server-side by
 * {@link VaultMenu#quickMoveStack}). Sort cycles between by-count and by-name.
 *
 * <p>The grid is drawn from {@link VaultClientCache}, not from real menu slots — see
 * {@link VaultMenu}'s javadoc for why. There is deliberately no texture asset here yet; the panel is
 * flat-filled placeholder chrome, not final art.
 */
public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 34;

    private EditBox searchBox;
    private Button sortButton;
    private SortMode sortMode = SortMode.COUNT_DESC;
    private int scrollRows;
    private List<VaultSyncPayload.Entry> visible = List.of();

    public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 224);
        this.inventoryLabelY = VaultMenu.PLAYER_INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, leftPos + 8, topPos + 18, 118, 14, Component.translatable("gui.heartstead.vault.search"));
        searchBox.setHint(Component.translatable("gui.heartstead.vault.search"));
        searchBox.setResponder(s -> refresh());
        addRenderableWidget(searchBox);

        sortButton = Button.builder(sortMode.label(), b -> {
            sortMode = sortMode.next();
            b.setMessage(sortMode.label());
            refresh();
        }).bounds(leftPos + 130, topPos + 18, 38, 14).build();
        addRenderableWidget(sortButton);

        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(java.util.Locale.ROOT);
        List<VaultSyncPayload.Entry> filtered = new ArrayList<>();
        for (VaultSyncPayload.Entry entry : VaultClientCache.entries()) {
            if (query.isEmpty() || entry.item().getDefaultInstance().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        filtered.sort(sortMode.comparator());
        this.visible = filtered;
        int maxScroll = Math.max(0, ceilDiv(visible.size(), COLS) - ROWS);
        scrollRows = Math.min(scrollRows, maxScroll);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        graphics.fill(GRID_X - 2, GRID_Y - 2, GRID_X + COLS * CELL + 2, GRID_Y + ROWS * CELL + 2, 0x66000000);

        int firstIndex = scrollRows * COLS;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = firstIndex + row * COLS + col;
                if (index >= visible.size()) {
                    continue;
                }
                VaultSyncPayload.Entry entry = visible.get(index);
                int x = GRID_X + col * CELL + 1;
                int y = GRID_Y + row * CELL + 1;
                ItemStack display = new ItemStack(entry.item(), 1);
                graphics.item(display, x, y);
                graphics.itemDecorations(this.font, display, x, y, countLabel(entry.count()));
            }
        }
    }

    private static String countLabel(int count) {
        return count >= 1000 ? (count / 1000) + "k" : Integer.toString(count);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int col = (int) ((event.x() - leftPos - GRID_X) / CELL);
        int row = (int) ((event.y() - topPos - GRID_Y) / CELL);
        if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
            int index = (scrollRows + row) * COLS + col;
            if (index < visible.size()) {
                VaultSyncPayload.Entry entry = visible.get(index);
                Item item = entry.item();
                int count = event.button() == 1 ? 1 : new ItemStack(item).getMaxStackSize();
                ClientPlayNetworking.send(new VaultWithdrawPayload(item, count));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        int maxScroll = Math.max(0, ceilDiv(visible.size(), COLS) - ROWS);
        int next = scrollRows - (int) Math.signum(scrollY);
        scrollRows = Math.max(0, Math.min(maxScroll, next));
        return true;
    }

    private enum SortMode {
        COUNT_DESC(Component.translatable("gui.heartstead.vault.sort.count"), Comparator.<VaultSyncPayload.Entry>comparingInt(VaultSyncPayload.Entry::count).reversed()),
        NAME_ASC(Component.translatable("gui.heartstead.vault.sort.name"), Comparator.comparing(e -> e.item().getDefaultInstance().getHoverName().getString()));

        private final Component label;
        private final Comparator<VaultSyncPayload.Entry> comparator;

        SortMode(Component label, Comparator<VaultSyncPayload.Entry> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        Component label() {
            return label;
        }

        Comparator<VaultSyncPayload.Entry> comparator() {
            return comparator;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
