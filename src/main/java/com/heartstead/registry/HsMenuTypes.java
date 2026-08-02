package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.codex.CodexMenu;
import com.heartstead.vault.VaultAccess;
import com.heartstead.vault.VaultMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;

/**
 * Screen handler registration (DESIGN.md §2.4, CONVENTIONS.md §2). Vanilla's {@link MenuType}
 * constructor is private (registration is meant to happen through {@code MenuType.register}, which
 * is also private and reserved for vanilla's own types), so — like every other Fabric mod — this
 * goes through Fabric API's {@link ExtendedMenuType}, which exposes a public constructor. The Codex and core screens (a later phase) copy this same shape.
 */
public final class HsMenuTypes {

    private HsMenuTypes() {
    }

    /**
     * The opening data is the §2.2 {@link VaultAccess} the menu was opened with — the Anchor's two
     * tabs, a Satchel's deposit-only storage tab, or a Pouch's full one. The client needs it to know
     * what to draw; the server keeps its own copy on the menu and re-checks against that, never
     * against anything the client sends back.
     */
    public static final MenuType<VaultMenu> VAULT = Registry.register(
            BuiltInRegistries.MENU,
            ResourceKey.create(Registries.MENU, Heartstead.id("vault")),
            new ExtendedMenuType<>(VaultMenu::new, VaultAccess.STREAM_CODEC));

    /** DESIGN.md §3.3 — the Codex. No opening data: every Codex shows the opening player's own archive. */
    public static final MenuType<CodexMenu> CODEX = Registry.register(
            BuiltInRegistries.MENU,
            ResourceKey.create(Registries.MENU, Heartstead.id("codex")),
            new ExtendedMenuType<>(
                    (containerId, inventory, unit) -> new CodexMenu(containerId, inventory),
                    net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)));

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
