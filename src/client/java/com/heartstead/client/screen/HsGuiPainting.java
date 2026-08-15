package com.heartstead.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The vanilla-styled painted panel every hand-drawn screen in the pack uses — {@code VaultScreen},
 * {@code CodexScreen} and {@code CoreHousingScreen} each used to carry an identical private copy of
 * this (same colours, same bevel), which is exactly the kind of duplication CLAUDE.md asks not to
 * repeat. One copy here instead.
 *
 * <p><b>Corners are chamfered, not blitted from a chest texture</b> — each screen already explains why
 * painting beats blitting (arbitrary width). The one pixel at each corner of {@link #panel} is left
 * undrawn rather than filled with the hard black edge, the same "corner cut" vanilla's own buttons and
 * sliders use to read as soft rather than a stark rectangle; {@code GuiGraphics.fill} draws flat
 * rectangles only, so a true anti-aliased curve is not an option that would still look like Minecraft.
 */
public final class HsGuiPainting {

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    public static final int PANEL_BODY = 0xFFC6C6C6;
    public static final int PANEL_LIGHT = 0xFFFFFFFF;
    public static final int PANEL_DARK = 0xFF555555;
    public static final int PANEL_EDGE = 0xFF000000;
    public static final int TEXT_COLOR = 0xFF404040;

    private HsGuiPainting() {
    }

    /** A vanilla-style raised panel: black outline, body fill, light top-left, dark bottom-right — chamfered corners. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;

        // The border, as four strips rather than one rectangle — each stops one pixel short of both
        // its corners, which is what leaves the corner pixel itself undrawn.
        graphics.fill(x + 1, y, right - 1, y + 1, PANEL_EDGE);
        graphics.fill(x + 1, bottom - 1, right - 1, bottom, PANEL_EDGE);
        graphics.fill(x, y + 1, x + 1, bottom - 1, PANEL_EDGE);
        graphics.fill(right - 1, y + 1, right, bottom - 1, PANEL_EDGE);

        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL_BODY);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, bottom - 1, PANEL_LIGHT);
        graphics.fill(right - 2, y + 1, right - 1, bottom - 1, PANEL_DARK);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, PANEL_DARK);
    }

    /** One 18×18 slot depression, drawn at the socket's top-left (one pixel out from the item). */
    public static void socket(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, itemX - 1, itemY - 1, 18, 18);
    }
}
