package com.heartstead.client.screen;

import com.heartstead.client.vault.VaultClientCache;
import com.heartstead.network.VaultActivatePayload;
import com.heartstead.network.VaultDepositCarriedPayload;
import com.heartstead.network.VaultStatePayload;
import com.heartstead.network.VaultSyncPayload;
import com.heartstead.network.VaultTabPayload;
import com.heartstead.network.VaultWithdrawPayload;
import com.heartstead.registry.HsBlocks;
import com.heartstead.registry.HsItems;
import com.heartstead.vault.VaultLayout;
import com.heartstead.vault.VaultMenu;
import com.heartstead.vault.VaultUpgradeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2.4 — the Vault screen. Two bookmark tabs, a scrollable grid, live search,
 * click-to-withdraw, and deposit either by shift-clicking your inventory or by dropping a carried
 * stack anywhere in the grid.
 *
 * <p><b>Geometry comes from {@link VaultLayout}, never from constants declared here.</b> The menu
 * registers real slots at those coordinates and this class paints the panel at them; if the two ever
 * disagree, clicking a visible slot lands outside every slot and vanilla drops the carried stack on
 * the floor. That is a real bug this screen has already had.
 *
 * <p><b>The panel is painted, not blitted from a chest texture.</b> {@code generic_54.png} is 176
 * wide, which leaves six pixels beside a nine-column grid — not enough for vanilla's twelve-pixel
 * scroller without it sitting on the last column. Painting means the width can be whatever the
 * contents need, at the cost of drawing every slot socket by hand.
 *
 * <p><b>Coordinate spaces, the recurring trap.</b> {@link #extractBackground} runs in absolute screen
 * coordinates and computes {@code leftPos}/{@code topPos} itself. {@link #extractLabels} runs inside
 * a pose already translated by {@code (leftPos, topPos)}. Mouse events arrive absolute.
 */
public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier DISABLED_SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/crafter/disabled_slot");
    private static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    /** Vanilla's GUI palette, so a painted panel is indistinguishable from a blitted one. */
    private static final int PANEL_BODY = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int TAB_UNSELECTED_BODY = 0xFF8B8B8B;

    /** Vanilla's slot hover tint, straight out of {@code AbstractContainerScreen}. */
    private static final int SLOT_HOVER = 0x80FFFFFF;

    /** Ghost icons are the real item faded toward the panel body, so no new art is needed. */
    private static final int GHOST_FADE = 0xB0C6C6C6;
    private static final int SATISFIED_FADE = 0x60C6C6C6;

    private static final int TEXT_COLOR = 0xFF404040;

    private EditBox searchBox;
    private Button sortButton;
    private Button activateButton;

    private SortMode sortMode = SortMode.COUNT_DESC;
    private Tab tab = Tab.STORAGE;
    private int scrollRows;
    private List<VaultSyncPayload.Entry> visible = List.of();

    public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, VaultLayout.PANEL_WIDTH, VaultLayout.PANEL_HEIGHT);
        this.inventoryLabelX = VaultLayout.INVENTORY_LABEL_X;
        this.inventoryLabelY = VaultLayout.INVENTORY_LABEL_Y;
        // The search box occupies the title strip, so the title itself would collide with it.
        this.titleLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        searchBox = addRenderableWidget(new EditBox(this.font,
                leftPos + VaultLayout.SEARCH_X, topPos + VaultLayout.SEARCH_Y,
                VaultLayout.SEARCH_WIDTH, VaultLayout.SEARCH_HEIGHT,
                Component.translatable("gui.heartstead.vault.search")));
        searchBox.setHint(Component.translatable("gui.heartstead.vault.search"));
        searchBox.setResponder(s -> refresh());

        sortButton = addRenderableWidget(Button.builder(sortMode.label(), b -> {
                    sortMode = sortMode.next();
                    b.setMessage(sortMode.label());
                    refresh();
                })
                .bounds(leftPos + VaultLayout.SORT_X, topPos + VaultLayout.SORT_Y,
                        VaultLayout.SORT_WIDTH, VaultLayout.SORT_HEIGHT)
                .build());

        activateButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.heartstead.vault.activate"),
                        b -> ClientPlayNetworking.send(new VaultActivatePayload()))
                .bounds(leftPos + VaultLayout.ACTIVATE_X, topPos + VaultLayout.ACTIVATE_Y,
                        VaultLayout.ACTIVATE_WIDTH, VaultLayout.ACTIVATE_HEIGHT)
                .build());

        selectTab(tab);
        refresh();
    }

    private void selectTab(Tab next) {
        this.tab = next;
        boolean storage = next == Tab.STORAGE;

        searchBox.visible = storage;
        sortButton.visible = storage;
        activateButton.visible = !storage;

        // Both sides must agree: Slot#isActive() is evaluated on the client too, so telling only the
        // server would leave the upgrade slots drawn but unclickable.
        menu.handleSetTab(!storage);
        ClientPlayNetworking.send(new VaultTabPayload(!storage));
        updateButtonStates();
    }

    /**
     * §2.4 — tab 2 is "locked until activated". Greying is a rendering courtesy; the server refuses
     * the same things independently, so a client ignoring this gains nothing.
     */
    private void updateButtonStates() {
        VaultStatePayload state = VaultClientCache.state();
        if (tab == Tab.STORAGE && !state.activated() && menu.access().hasAnchorTab()) {
            selectTab(Tab.ANCHOR);
            return;
        }
        activateButton.active = !state.activated();
        activateButton.setMessage(activationLabel(state));
        activateButton.setTooltip(Tooltip.create(activationTooltip(state)));
    }

    /**
     * §2.1 — the button says what it will actually cost, and names the anti-softlock fallback only
     * when it applies, which is §2.4's wording verbatim.
     */
    private static Component activationLabel(VaultStatePayload state) {
        if (state.activated()) {
            return Component.translatable("gui.heartstead.vault.activated");
        }
        if (!state.everActivated()) {
            return Component.translatable("gui.heartstead.vault.activate_free");
        }
        return Component.translatable("gui.heartstead.vault.activate_cost", state.reactivationCost());
    }

    /**
     * §2.4 — "shows the 'use a Vault Sigil from the Vault' fallback only when it applies". A tooltip
     * rather than a longer label: the fallback is an explanation, not a second verb, and the button
     * sends the same intent either way (the server picks held-Sigil over stored-Sigil, §2.1).
     */
    private static Component activationTooltip(VaultStatePayload state) {
        if (state.activated()) {
            return Component.translatable("gui.heartstead.vault.activated.tip");
        }
        if (!state.everActivated()) {
            return Component.translatable("gui.heartstead.vault.activate_free.tip");
        }
        if (state.canActivateFromVaultStock()) {
            return Component.translatable("gui.heartstead.vault.activate_from_vault.tip", state.reactivationCost());
        }
        return Component.translatable("gui.heartstead.vault.activate_cost.tip", state.reactivationCost());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonStates();
        refresh();
    }

    private void refresh() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        List<VaultSyncPayload.Entry> filtered = new ArrayList<>();
        for (VaultSyncPayload.Entry entry : VaultClientCache.entries()) {
            if (query.isEmpty() || displayName(entry.item()).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        filtered.sort(sortMode.comparator());
        this.visible = filtered;
        scrollRows = Math.min(scrollRows, maxScroll());
    }

    private static String displayName(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }

    private int maxScroll() {
        return Math.max(0, ceilDiv(visible.size(), VaultLayout.COLS) - VaultLayout.ROWS);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    // ------------------------------------------------------------------ background (absolute space)

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        extractTabs(graphics);
        panel(graphics, leftPos, topPos, VaultLayout.PANEL_WIDTH, VaultLayout.PANEL_HEIGHT);

        // Player inventory sockets. The slots themselves are real and drawn by the base class; these
        // are the depressions they sit in, which a painted panel has to supply itself.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < VaultLayout.COLS; col++) {
                socket(graphics, leftPos + VaultLayout.PLAYER_INV_X + col * VaultLayout.CELL,
                        topPos + VaultLayout.PLAYER_INV_Y + row * VaultLayout.CELL);
            }
        }
        for (int col = 0; col < VaultLayout.COLS; col++) {
            socket(graphics, leftPos + VaultLayout.PLAYER_INV_X + col * VaultLayout.CELL,
                    topPos + VaultLayout.HOTBAR_Y);
        }

        if (tab == Tab.STORAGE) {
            for (int row = 0; row < VaultLayout.ROWS; row++) {
                for (int col = 0; col < VaultLayout.COLS; col++) {
                    socket(graphics, leftPos + VaultLayout.GRID_X + col * VaultLayout.CELL,
                            topPos + VaultLayout.GRID_Y + row * VaultLayout.CELL);
                }
            }
        } else {
            for (int i = 0; i < VaultLayout.UPGRADE_SLOT_COUNT; i++) {
                socket(graphics, leftPos + VaultLayout.upgradeSlotX(i), topPos + VaultLayout.UPGRADE_SLOT_Y);
            }
        }
    }

    /** A vanilla-style raised panel: black outline, body fill, light top-left, dark bottom-right. */
    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_BODY);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, PANEL_LIGHT);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, PANEL_DARK);
    }

    /** One 18×18 slot depression, drawn at the socket's top-left (one pixel out from the item). */
    private static void socket(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, itemX - 1, itemY - 1, 18, 18);
    }

    /**
     * Bookmark tabs above the panel, painted rather than blitted so they can be short — vanilla's
     * creative sprite is 32 tall, which towers over a panel this size. The selected tab shares the
     * panel's body colour and omits its bottom edge so it reads as joined to it.
     */
    private void extractTabs(GuiGraphicsExtractor graphics) {
        if (!menu.access().hasAnchorTab()) {
            return;
        }
        for (Tab value : Tab.values()) {
            boolean selected = value == tab;
            int x = tabX(value);
            int y = tabY();
            int w = VaultLayout.TAB_WIDTH;
            int h = VaultLayout.TAB_HEIGHT;

            graphics.fill(x, y, x + w, y + h + 2, PANEL_EDGE);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h + 2, selected ? PANEL_BODY : TAB_UNSELECTED_BODY);
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_LIGHT);
            graphics.fill(x + 1, y + 1, x + 2, y + h + 1, PANEL_LIGHT);
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + h + 1, PANEL_DARK);

            graphics.item(value.icon(), x + (w - 16) / 2, y + (h - 16) / 2);
        }
    }

    private int tabX(Tab value) {
        return leftPos + 6 + value.ordinal() * (VaultLayout.TAB_WIDTH + 2);
    }

    /** The tab's last two pixels tuck under the panel's top edge, which is what makes it a bookmark. */
    private int tabY() {
        return topPos - VaultLayout.TAB_HEIGHT;
    }

    private boolean overTab(Tab value, double mouseX, double mouseY) {
        int x = tabX(value);
        int y = tabY();
        return mouseX >= x && mouseX < x + VaultLayout.TAB_WIDTH
                && mouseY >= y && mouseY < y + VaultLayout.TAB_HEIGHT;
    }

    // ------------------------------------------------------------------ foreground (panel space)

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (tab == Tab.STORAGE) {
            extractStorageTab(graphics, mouseX, mouseY);
        } else {
            extractAnchorTab(graphics);
        }
    }

    private void extractStorageTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean withdrawable = menu.access().canWithdraw() && VaultClientCache.state().canWithdrawHere();
        int firstIndex = scrollRows * VaultLayout.COLS;
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        for (int row = 0; row < VaultLayout.ROWS; row++) {
            for (int col = 0; col < VaultLayout.COLS; col++) {
                int x = VaultLayout.GRID_X + col * VaultLayout.CELL;
                int y = VaultLayout.GRID_Y + row * VaultLayout.CELL;

                // §2.2 — a Satchel can't take anything out, and neither can a Pouch out of reach.
                // Disabled sockets say so without a caption: it is the same visual vanilla's Crafter
                // uses for a slot you may not use.
                if (!withdrawable) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT_SPRITE, x - 1, y - 1, 18, 18);
                }

                int index = firstIndex + row * VaultLayout.COLS + col;
                if (index < visible.size()) {
                    VaultSyncPayload.Entry entry = visible.get(index);
                    ItemStack display = new ItemStack(entry.item(), 1);
                    graphics.item(display, x, y);
                    graphics.itemDecorations(this.font, display, x, y, countLabel(entry.count()));
                }

                // Every cell highlights on hover, filled or not — an empty cell is still a place you
                // can drop a carried stack, so it has to feel live the way an empty chest slot does.
                if (inCell(relX, relY, x, y)) {
                    graphics.fill(x, y, x + 16, y + 16, SLOT_HOVER);
                }
            }
        }
        extractScroller(graphics);
    }

    /** Vanilla's creative-inventory scroller, in the gutter the painted panel makes room for. */
    private void extractScroller(GuiGraphicsExtractor graphics) {
        int max = maxScroll();
        int trackTop = VaultLayout.GRID_Y;
        int trackHeight = VaultLayout.ROWS * VaultLayout.CELL;
        int y = max <= 0 ? trackTop : trackTop + (trackHeight - VaultLayout.SCROLLER_HEIGHT) * scrollRows / max;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                max <= 0 ? SCROLLER_DISABLED_SPRITE : SCROLLER_SPRITE,
                VaultLayout.SCROLLER_X, y, VaultLayout.SCROLLER_WIDTH, VaultLayout.SCROLLER_HEIGHT);
    }

    /**
     * §2.4 tab 1: the §2.3 ladders as four sockets. An empty one shows a ghosted Sigil so the player
     * can see what it wants without reading anything; a reach tier already bought shows its Sigil
     * dimmed and takes no more, which is the §12.3 table drawn rather than described.
     */
    private void extractAnchorTab(GuiGraphicsExtractor graphics) {
        VaultStatePayload state = VaultClientCache.state();

        graphics.text(this.font, Component.translatable("gui.heartstead.vault.tab.anchor"),
                VaultLayout.GRID_X, 8, TEXT_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.heartstead.vault.capacity_summary",
                        state.distinctTypesUsed(), state.distinctTypeCap(), state.stackDepthCap()),
                VaultLayout.GRID_X, 24, TEXT_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.heartstead.vault.sigils_spent", state.sigilsSpent()),
                VaultLayout.GRID_X, 36, TEXT_COLOR, false);

        VaultUpgradeKind[] kinds = VaultUpgradeKind.values();
        for (int i = 0; i < kinds.length; i++) {
            int x = VaultLayout.upgradeSlotX(i);
            int y = VaultLayout.UPGRADE_SLOT_Y;
            boolean satisfied = isSatisfied(kinds[i], state);

            // The real Slot renders anything the player has put in; this is the hint underneath.
            if (menu.slots.get(i).getItem().isEmpty()) {
                graphics.fakeItem(new ItemStack(kinds[i].sigil()), x, y);
                graphics.fill(x, y, x + 16, y + 16, satisfied ? SATISFIED_FADE : GHOST_FADE);
            }
            if (satisfied) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT_SPRITE, x - 1, y - 1, 18, 18);
            }
        }
    }

    private static boolean isSatisfied(VaultUpgradeKind kind, VaultStatePayload state) {
        return kind.dimension() != null && state.reachTiers().contains(kind.dimension());
    }

    private static String countLabel(int count) {
        return count >= 1000 ? (count / 1000) + "k" : Integer.toString(count);
    }

    private static boolean inCell(double relX, double relY, int cellX, int cellY) {
        return relX >= cellX && relX < cellX + 16 && relY >= cellY && relY < cellY + 16;
    }

    // ------------------------------------------------------------------ tooltips and input

    /**
     * Tooltips for the Vault grid and the upgrade sockets. The base class only knows about real
     * slots, and the grid has none — so without this, hovering a stored stack showed nothing at all.
     * Runs in absolute coordinates, after the panel translate has been popped.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        if (tab == Tab.STORAGE) {
            VaultSyncPayload.Entry hovered = entryAt(relX, relY);
            if (hovered != null) {
                // A real count, not the "1" the grid draws — the icon is a type, not a stack.
                ItemStack tooltipStack = new ItemStack(hovered.item(), 1);
                graphics.setTooltipForNextFrame(this.font, tooltipStack, mouseX, mouseY);
            }
            return;
        }

        VaultUpgradeKind[] kinds = VaultUpgradeKind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (!inCell(relX, relY, VaultLayout.upgradeSlotX(i), VaultLayout.UPGRADE_SLOT_Y)) {
                continue;
            }
            if (!menu.slots.get(i).getItem().isEmpty()) {
                return;
            }
            boolean satisfied = isSatisfied(kinds[i], VaultClientCache.state());
            graphics.setTooltipForNextFrame(this.font, Component.translatable(
                    kinds[i].translationKey() + (satisfied ? ".owned" : "")), mouseX, mouseY);
            return;
        }
    }

    /** The grid entry under a panel-relative point, or null. */
    private VaultSyncPayload.Entry entryAt(double relX, double relY) {
        int firstIndex = scrollRows * VaultLayout.COLS;
        for (int row = 0; row < VaultLayout.ROWS; row++) {
            for (int col = 0; col < VaultLayout.COLS; col++) {
                int index = firstIndex + row * VaultLayout.COLS + col;
                if (index >= visible.size()) {
                    continue;
                }
                int x = VaultLayout.GRID_X + col * VaultLayout.CELL;
                int y = VaultLayout.GRID_Y + row * VaultLayout.CELL;
                if (inCell(relX, relY, x, y)) {
                    return visible.get(index);
                }
            }
        }
        return null;
    }

    private boolean inGrid(double relX, double relY) {
        return relX >= VaultLayout.GRID_X && relX < VaultLayout.GRID_X + VaultLayout.COLS * VaultLayout.CELL
                && relY >= VaultLayout.GRID_Y && relY < VaultLayout.GRID_Y + VaultLayout.ROWS * VaultLayout.CELL;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.access().hasAnchorTab()) {
            for (Tab value : Tab.values()) {
                if (!overTab(value, event.x(), event.y())) {
                    continue;
                }
                if (value != tab && (value == Tab.ANCHOR || VaultClientCache.state().activated())) {
                    selectTab(value);
                }
                return true;
            }
        }
        if (tab != Tab.STORAGE) {
            return super.mouseClicked(event, doubleClick);
        }

        double relX = event.x() - leftPos;
        double relY = event.y() - topPos;
        if (!inGrid(relX, relY)) {
            return super.mouseClicked(event, doubleClick);
        }

        // Carrying something? Then the grid behaves like a container slot: click to put it in.
        // This is what makes the Vault feel like a chest rather than a read-only list.
        if (!menu.getCarried().isEmpty()) {
            ClientPlayNetworking.send(new VaultDepositCarriedPayload(event.button() == 1));
            return true;
        }

        if (!menu.access().canWithdraw()) {
            return super.mouseClicked(event, doubleClick);
        }
        VaultSyncPayload.Entry hovered = entryAt(relX, relY);
        if (hovered == null) {
            return super.mouseClicked(event, doubleClick);
        }
        int count = event.button() == 1 ? 1 : new ItemStack(hovered.item()).getMaxStackSize();
        ClientPlayNetworking.send(new VaultWithdrawPayload(hovered.item(), count));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.STORAGE) {
            scrollRows = Math.max(0, Math.min(maxScroll(), scrollRows - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** §2.4's two tabs. Order is draw order, left to right. */
    private enum Tab {
        STORAGE,
        ANCHOR;

        ItemStack icon() {
            return this == STORAGE ? new ItemStack(HsItems.VAULT_SIGIL) : new ItemStack(HsBlocks.VAULT_ANCHOR);
        }
    }

    private enum SortMode {
        COUNT_DESC(Component.translatable("gui.heartstead.vault.sort.count"),
                Comparator.<VaultSyncPayload.Entry>comparingInt(VaultSyncPayload.Entry::count).reversed()),
        NAME_ASC(Component.translatable("gui.heartstead.vault.sort.name"),
                Comparator.comparing(e -> displayName(e.item())));

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
