package com.sigilstead.codex;

import com.sigilstead.config.CodexConfig;
import com.sigilstead.config.CodexPrice;
import com.sigilstead.config.HsConfigManager;
import com.sigilstead.item.SealedTomeItem;
import com.sigilstead.registry.HsAttachments;
import com.sigilstead.registry.HsItems;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN.md §3.3 — the Archive / Tome / Teach verbs over the per-player {@link CodexArchive}
 * attachment. All mutation happens here, the same shape as {@link com.sigilstead.vault.Vault}: the
 * Codex block and its menu are callers, not owners, of this logic.
 */
public final class Codex {

    private Codex() {
    }

    public static CodexArchive get(ServerPlayer player) {
        return player.getAttachedOrCreate(HsAttachments.CODEX_ARCHIVE, CodexArchive::initial);
    }

    public static CodexArchiveTier tier(ServerPlayer player) {
        return CodexArchiveTier.forTier(get(player).capacityTier(), HsConfigManager.get().codex());
    }

    /**
     * §3.3 Archive — records every enchantment on {@code stack} (a book has one; a found enchanted
     * item may carry several) and returns whether at least one was newly archived. The item is only
     * ever consumed by the caller when this returns {@code true} — nothing here mutates the stack.
     *
     * <p>Refuses outright, archiving nothing, if the archive has no room for <em>any</em> of the
     * incoming enchantments — the alternative (archiving what fits, silently dropping the rest) would
     * consume a found item and quietly waste part of it.
     */
    public static boolean tryArchive(ServerPlayer player, ItemStack stack) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return false;
        }

        CodexArchive archive = get(player);
        CodexArchiveTier capTier = CodexArchiveTier.forTier(archive.capacityTier(), HsConfigManager.get().codex());

        int distinctCount = archive.archived().size();
        boolean anyFits = false;
        for (var entry : enchantments.entrySet()) {
            ResourceKey<Enchantment> key = entry.getKey().unwrapKey().orElse(null);
            if (key == null) {
                continue;
            }
            boolean alreadyArchived = archive.levelOf(key) > 0;
            if (alreadyArchived || capTier.hasRoomFor(distinctCount)) {
                anyFits = true;
                break;
            }
        }
        if (!anyFits) {
            return false;
        }

        for (var entry : enchantments.entrySet()) {
            ResourceKey<Enchantment> key = entry.getKey().unwrapKey().orElse(null);
            if (key == null) {
                continue;
            }
            int level = entry.getIntValue();
            boolean alreadyArchived = archive.levelOf(key) > 0;
            int currentDistinct = archive.archived().size();
            CodexArchiveTier currentTier = CodexArchiveTier.forTier(archive.capacityTier(), HsConfigManager.get().codex());
            if (!alreadyArchived && !currentTier.hasRoomFor(currentDistinct)) {
                continue;
            }
            archive = archive.withArchived(key, level);
        }
        player.setAttached(HsAttachments.CODEX_ARCHIVE, archive);
        return true;
    }

    /** §3.3/§12.6 capacity upgrade item for the tier after the player's current one, or empty if maxed. */
    public static Optional<Item> nextCapacityUpgradeItem(ServerPlayer player) {
        return switch (get(player).capacityTier()) {
            case 0 -> Optional.of(Items.ECHO_SHARD);
            case 1 -> Optional.of(Items.NETHER_STAR);
            default -> Optional.empty();
        };
    }

    /** §3.3/§12.6 — consumes the matching upgrade item from {@code stack} (a stack of exactly it) and advances one tier. */
    public static boolean tryBuyCapacity(ServerPlayer player, ItemStack stack) {
        Optional<Item> required = nextCapacityUpgradeItem(player);
        if (required.isEmpty() || stack.isEmpty() || !stack.is(required.get())) {
            return false;
        }
        CodexArchive archive = get(player);
        player.setAttached(HsAttachments.CODEX_ARCHIVE, archive.withCapacityTier(archive.capacityTier() + 1));
        stack.shrink(1);
        return true;
    }

    /**
     * §3.3 Tome — consumes one empty Tome from {@code tomeStack} and returns a Sealed Tome for
     * {@code enchantment}, or empty if that enchantment isn't archived or the stack isn't a Tome.
     */
    public static Optional<ItemStack> trySeal(ServerPlayer player, ItemStack tomeStack, ResourceKey<Enchantment> enchantment) {
        if (tomeStack.isEmpty() || !tomeStack.is(HsItems.TOME)) {
            return Optional.empty();
        }
        int level = get(player).levelOf(enchantment);
        if (level <= 0) {
            return Optional.empty();
        }
        tomeStack.shrink(1);
        return Optional.of(SealedTomeItem.create(enchantment, level));
    }

    /**
     * §3.3 Teach — the price for {@code enchantment} at {@code level}: §12.6's four hand-tuned entries
     * if it's one of them, otherwise a rarity-scaled formula so <em>anything</em> archived can be
     * taught, not just those four. Never empty — every enchantment has a vanilla anvil-rarity
     * multiplier (1/2/4/8) to fall back on.
     */
    public static int priceFor(Holder<Enchantment> holder, ResourceKey<Enchantment> enchantment, int level) {
        CodexConfig config = HsConfigManager.get().codex();
        for (CodexPrice price : config.prices()) {
            if (price.level() == level && price.enchantment().equals(enchantment.identifier())) {
                return price.emeralds();
            }
        }
        int rarityMultiplier = holder.value().getAnvilCost();
        int raw = config.formulaEmeraldsPerRarityLevel() * level * rarityMultiplier;
        return Math.max(config.formulaMinEmeralds(), Math.min(config.formulaMaxEmeralds(), raw));
    }

    public static Holder<Enchantment> holderOf(HolderLookup.Provider registries, ResourceKey<Enchantment> key) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
