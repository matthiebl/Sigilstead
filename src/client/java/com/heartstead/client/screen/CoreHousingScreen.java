package com.heartstead.client.screen;

import com.heartstead.core.CoreHousingLayout;
import com.heartstead.core.CoreHousingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * DESIGN.md §4.2 — the housing screen: a core slot, the buffer, and one line of status. Painted
 * rather than blitted from a chest texture, matching {@code CodexScreen} and {@code VaultScreen}.
 *
 * <p>Deliberately the smallest screen in the mod. §4 is a tooltip system (§4.1: "the tooltip is the
 * entire UI"); this exists only because §4.2 sockets by placing a core in a slot, and CLAUDE.md is
 * explicit that being able to build a real UI everywhere is not a reason to.
 *
 * <p>The status line is the §4.3 refusal's second half — "with the reason shown to the player". The
 * action-bar message fires at the moment of refusal; this says so persistently for as long as the
 * screen is open.
 */
public class CoreHousingScreen extends AbstractContainerScreen<CoreHousingMenu> {

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final int PANEL_BODY = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int REFUSED_COLOR = 0xFFAA2020;
    private static final int RUNNING_COLOR = 0xFF207020;

    public CoreHousingScreen(CoreHousingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CoreHousingLayout.PANEL_WIDTH,
                CoreHousingLayout.panelHeight(menu.bufferSlots()));
        this.inventoryLabelX = CoreHousingLayout.PLAYER_INV_X;
        this.inventoryLabelY = CoreHousingLayout.inventoryLabelY(menu.bufferSlots());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int buffers = menu.bufferSlots();
        panel(graphics, leftPos, topPos, CoreHousingLayout.PANEL_WIDTH, CoreHousingLayout.panelHeight(buffers));

        socket(graphics, leftPos + CoreHousingLayout.CORE_SLOT_X, topPos + CoreHousingLayout.CORE_SLOT_Y);

        // One socket per real buffer slot — the count is the server's (CoreHousingOpenData), not a
        // client guess at the config, so a resized buffer draws correctly instead of leaving holes.
        for (int index = 0; index < buffers; index++) {
            socket(graphics, leftPos + CoreHousingLayout.bufferSlotX(index),
                    topPos + CoreHousingLayout.bufferSlotY(index));
        }

        int invY = CoreHousingLayout.playerInvY(buffers);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                socket(graphics, leftPos + CoreHousingLayout.PLAYER_INV_X + col * CoreHousingLayout.SLOT_SIZE,
                        topPos + invY + row * CoreHousingLayout.SLOT_SIZE);
            }
        }
        for (int col = 0; col < 9; col++) {
            socket(graphics, leftPos + CoreHousingLayout.PLAYER_INV_X + col * CoreHousingLayout.SLOT_SIZE,
                    topPos + CoreHousingLayout.hotbarY(buffers));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        Component status;
        int colour;
        switch (menu.status()) {
            case CoreHousingMenu.STATUS_RUNNING -> {
                status = Component.translatable("gui.heartstead.core_housing.running");
                colour = RUNNING_COLOR;
            }
            case CoreHousingMenu.STATUS_REFUSED -> {
                status = Component.translatable("gui.heartstead.core_housing.refused");
                colour = REFUSED_COLOR;
            }
            default -> {
                status = Component.translatable("gui.heartstead.core_housing.empty." + menu.family().id());
                colour = TEXT_COLOR;
            }
        }
        graphics.text(this.font, status, CoreHousingLayout.STATUS_X, CoreHousingLayout.STATUS_Y, colour, false);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_BODY);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, PANEL_LIGHT);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, PANEL_DARK);
    }

    private static void socket(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, itemX - 1, itemY - 1, 18, 18);
    }
}
