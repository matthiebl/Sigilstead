package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.vault.VaultMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.inventory.MenuType;

/**
 * Screen handler registration (DESIGN.md §2.4, CONVENTIONS.md §2). Vanilla's {@link MenuType}
 * constructor is private (registration is meant to happen through {@code MenuType.register}, which
 * is also private and reserved for vanilla's own types), so — like every other Fabric mod — this
 * goes through Fabric API's {@link ExtendedMenuType}, which exposes a public constructor. The Vault
 * needs no extra opening data, so {@code D} is {@link Unit}; the Codex and core screens (a later
 * phase) copy this same shape.
 */
public final class HsMenuTypes {

    private HsMenuTypes() {
    }

    public static final MenuType<VaultMenu> VAULT = Registry.register(
            BuiltInRegistries.MENU,
            ResourceKey.create(Registries.MENU, Heartstead.id("vault")),
            new ExtendedMenuType<>((containerId, inventory, data) -> new VaultMenu(containerId, inventory), StreamCodec.unit(Unit.INSTANCE)));

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
