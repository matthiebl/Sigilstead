package com.sigilstead.vault;

import com.sigilstead.registry.HsItems;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2.3's two ladders as the four slots tab 1 offers, one per thing you can buy.
 *
 * <p>Giving reach a slot <em>per dimension</em> rather than one shared slot is what lets the screen
 * say the whole ladder at a glance: an empty socket with a ghosted Sigil is something you have not
 * bought, and a filled, disabled one is something you have. That is the §2.3 table drawn instead of
 * described, and it means a player never has to guess which Sigil a generic slot wants.
 *
 * <p>Capacity has no dimension and no ceiling (§12.3), so its slot never fills in — it keeps taking
 * Vault Sigils for as long as you keep finding them.
 */
public enum VaultUpgradeKind implements StringRepresentable {

    /** §2.3 capacity. Unbounded, so this slot is never "done". */
    CAPACITY(null),

    OVERWORLD_REACH(Level.OVERWORLD),
    NETHER_REACH(Level.NETHER),
    END_REACH(Level.END);

    /** Only used by DESIGN.md §7.3's {@code sigilstead:vault_upgraded} criterion, which names a slot. */
    public static final Codec<VaultUpgradeKind> CODEC = StringRepresentable.fromEnum(VaultUpgradeKind::values);

    private final ResourceKey<Level> dimension;

    VaultUpgradeKind(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    /** The dimension this slot buys reach into, or null for {@link #CAPACITY}. */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** The §12.2 Sigil this slot accepts — also the item drawn ghosted in the empty socket. */
    public Item sigil() {
        if (dimension == null) {
            return HsItems.VAULT_SIGIL;
        }
        if (Level.OVERWORLD.equals(dimension)) {
            return HsItems.OVERWORLD_VAULT_SIGIL;
        }
        if (Level.NETHER.equals(dimension)) {
            return HsItems.NETHER_VAULT_SIGIL;
        }
        return HsItems.END_VAULT_SIGIL;
    }

    /** A reach tier already bought takes nothing more; capacity always does. */
    public boolean isSatisfied(VaultData vault) {
        return dimension != null && vault.reachTiers().contains(dimension);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String translationKey() {
        return "gui.sigilstead.vault.upgrade." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
