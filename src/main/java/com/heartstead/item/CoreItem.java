package com.heartstead.item;

import com.heartstead.core.CoreAttunement;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreTargets;
import com.heartstead.core.CoreTier;
import com.heartstead.registry.HsComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * DESIGN.md §4.1 step 2's output and §4.2's input — a finished, locked Core. It is a separate
 * registered item from {@link PrimedCoreItem} rather than a flag on one item, so that the §4.2
 * housing slot, the §4.1 revert recipe and the §4.3 upgrade recipe can each say exactly which of the
 * two they mean without inspecting a component (CONVENTIONS.md §2: "recipes gate correctly").
 *
 * <p>Stacks to one. Two identical cores can never both be useful — §4.3 refuses the second socket —
 * so letting them stack would only invite a player to craft a stack of something the world will not
 * accept.
 */
public class CoreItem extends Item {

    private final CoreFamily family;

    public CoreItem(Properties properties, CoreFamily family) {
        super(properties);
        this.family = family;
    }

    public CoreFamily family() {
        return family;
    }

    /** §4.1 — "the finished Core, named for what it learned — <i>Verdant Core (Wheat)</i>". */
    @Override
    public Component getName(ItemStack stack) {
        CoreAttunement attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
        if (attunement == null || attunement.target().isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable("item.heartstead.core.named", super.getName(stack),
                CoreTargets.displayName(family, attunement.target().get()));
    }

    /** §4.3 — the rate tier, and what buys the next one. Tier III says so rather than showing a blank. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        CoreAttunement attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
        if (attunement == null) {
            return;
        }
        CoreTier tier = attunement.tier();
        lines.accept(Component.translatable("item.heartstead.core.tier", tier.level())
                .withStyle(ChatFormatting.GOLD));

        CoreTier next = tier.next();
        if (next == null) {
            lines.accept(Component.translatable("item.heartstead.core.tier_max")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.accept(Component.translatable("item.heartstead.core.tier_next", next.costDescription())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        attunement.target().ifPresent(target -> lines.accept(Component.translatable("item.heartstead.core.yield",
                CoreTargets.yieldDescription(family, target)).withStyle(ChatFormatting.GRAY)));
    }
}
