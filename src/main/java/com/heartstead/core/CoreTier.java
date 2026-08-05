package com.heartstead.core;

import com.heartstead.config.CoreConfig;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * DESIGN.md §4.3 / §12.4 — the three rate tiers a locked core can be upgraded through. Tiering is the
 * only way to scale a core; building a second one of the same target is refused outright
 * ({@link ActiveCores}).
 *
 * <p>The multipliers are config, not constants on the enum (CONVENTIONS.md §5) — only the upgrade
 * <em>cost</em> lives here, because "4 Blaze Powder" is a recipe ingredient rather than a tuning dial,
 * and a config field naming an arbitrary item would have to validate against the item registry.
 *
 * <p>A tier's cost is a <b>list</b> of ingredients, not one item and a count: §12.4 prices tier II at
 * two different materials, and a single-ingredient model would silently make it uncraftable.
 */
public enum CoreTier {

    /** §12.4 tier I — base rate. Roughly a small hand-built farm. Not bought, so it has no cost. */
    ONE(1, List.of()),

    /** §12.4 tier II — 4 Blaze Powder + 4 Diamonds, 2.5× rate. */
    TWO(2, List.of(new Cost(Items.BLAZE_POWDER, 4), new Cost(Items.DIAMOND, 4))),

    /** §12.4 tier III — 4 Netherite Scrap, 6× rate. */
    THREE(3, List.of(new Cost(Items.NETHERITE_SCRAP, 4)));

    /** One ingredient of an upgrade's price. */
    public record Cost(Item item, int count) {
    }

    private final int level;
    private final List<Cost> costs;

    CoreTier(int level, List<Cost> costs) {
        this.level = level;
        this.costs = costs;
    }

    public int level() {
        return level;
    }

    /** What buys this tier from the one below it. Empty for tier I, which is not bought. */
    public List<Cost> costs() {
        return costs;
    }

    /** The tier above this one, or null at tier III — §12.4's ladder stops there. */
    public CoreTier next() {
        return switch (this) {
            case ONE -> TWO;
            case TWO -> THREE;
            case THREE -> null;
        };
    }

    /** §12.4's rate multiplier for this tier: 1× / 2.5× / 6×. */
    public double rateMultiplier(CoreConfig config) {
        return switch (this) {
            case ONE -> 1.0;
            case TWO -> config.tier2Multiplier();
            case THREE -> config.tier3Multiplier();
        };
    }

    /** The price as one line of tooltip — "4 × Blaze Powder, 4 × Diamond". */
    public Component costDescription() {
        Component joined = null;
        for (Cost cost : costs) {
            Component part = Component.translatable("item.heartstead.core.tier_cost",
                    cost.count(), Component.translatable(cost.item().getDescriptionId()));
            joined = joined == null ? part : Component.translatable("item.heartstead.core.tier_cost_join", joined, part);
        }
        return joined == null ? Component.empty() : joined;
    }

    /** Tolerant of an out-of-range persisted level: an unknown tier reads as tier I rather than crashing a load. */
    public static CoreTier ofLevel(int level) {
        return switch (level) {
            case 2 -> TWO;
            case 3 -> THREE;
            default -> ONE;
        };
    }
}
