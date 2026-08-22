package com.sigilstead.client.screen;

import com.sigilstead.core.CoreHousingLayout;
import com.sigilstead.core.CoreHousingMenu;
import com.sigilstead.network.CoreHousingCollectXpPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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

    private static final int TEXT_COLOR = HsGuiPainting.TEXT_COLOR;
    private static final int REFUSED_COLOR = 0xFFAA2020;
    private static final int RUNNING_COLOR = 0xFF207020;

    private Button collectButton;

    public CoreHousingScreen(CoreHousingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CoreHousingLayout.PANEL_WIDTH,
                CoreHousingLayout.panelHeight(menu.bufferSlots()));
        this.inventoryLabelX = CoreHousingLayout.PLAYER_INV_X;
        this.inventoryLabelY = CoreHousingLayout.inventoryLabelY(menu.bufferSlots());
    }

    @Override
    protected void init() {
        super.init();
        collectButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.sigilstead.core_housing.collect"),
                        b -> ClientPlayNetworking.send(new CoreHousingCollectXpPayload()))
                .bounds(leftPos + CoreHousingLayout.COLLECT_BUTTON_X, topPos + CoreHousingLayout.COLLECT_BUTTON_Y,
                        CoreHousingLayout.COLLECT_BUTTON_WIDTH, CoreHousingLayout.COLLECT_BUTTON_HEIGHT)
                .build());
        updateCollectButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateCollectButton();
    }

    private void updateCollectButton() {
        collectButton.active = menu.xp() >= 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int buffers = menu.bufferSlots();
        HsGuiPainting.panel(graphics, leftPos, topPos, CoreHousingLayout.PANEL_WIDTH, CoreHousingLayout.panelHeight(buffers));

        HsGuiPainting.socket(graphics, leftPos + CoreHousingLayout.CORE_SLOT_X, topPos + CoreHousingLayout.CORE_SLOT_Y);

        // One socket per real buffer slot — the count is the server's (CoreHousingOpenData), not a
        // client guess at the config, so a resized buffer draws correctly instead of leaving holes.
        for (int index = 0; index < buffers; index++) {
            HsGuiPainting.socket(graphics, leftPos + CoreHousingLayout.bufferSlotX(index),
                    topPos + CoreHousingLayout.bufferSlotY(index));
        }

        int invY = CoreHousingLayout.playerInvY(buffers);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                HsGuiPainting.socket(graphics, leftPos + CoreHousingLayout.PLAYER_INV_X + col * CoreHousingLayout.SLOT_SIZE,
                        topPos + invY + row * CoreHousingLayout.SLOT_SIZE);
            }
        }
        for (int col = 0; col < 9; col++) {
            HsGuiPainting.socket(graphics, leftPos + CoreHousingLayout.PLAYER_INV_X + col * CoreHousingLayout.SLOT_SIZE,
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
                status = Component.translatable("gui.sigilstead.core_housing.running");
                colour = RUNNING_COLOR;
            }
            case CoreHousingMenu.STATUS_REFUSED -> {
                status = Component.translatable("gui.sigilstead.core_housing.refused");
                colour = REFUSED_COLOR;
            }
            default -> {
                status = Component.translatable("gui.sigilstead.core_housing.empty." + menu.family().id());
                colour = TEXT_COLOR;
            }
        }
        graphics.text(this.font, status, CoreHousingLayout.STATUS_X, CoreHousingLayout.STATUS_Y, colour, false);

        graphics.text(this.font, Component.translatable("gui.sigilstead.core_housing.experience", menu.xp()),
                CoreHousingLayout.XP_TEXT_X, CoreHousingLayout.XP_Y, TEXT_COLOR, false);
    }

}
