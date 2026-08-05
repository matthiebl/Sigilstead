package com.heartstead.item;

import com.heartstead.config.HsConfigManager;
import com.heartstead.core.CoreAttunement;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreTargets;
import com.heartstead.registry.HsComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * DESIGN.md §4.1 step 1 — a Primed Core: family fixed by the prime recipe, target not yet locked.
 * Carried while you play until it reaches its threshold, at which point {@link com.heartstead.core.CoreImprint}
 * converts the stack into the matching {@link CoreItem}.
 *
 * <p>§4.1: "The tooltip is the entire UI. No screen, no block, no ritual." That is all this class
 * does beyond carrying a family — the attunement state itself lives in the
 * {@link HsComponents#CORE_ATTUNEMENT} component so it survives everything a stack survives.
 *
 * <p>{@code appendHoverText} is common-side API (vanilla items implement it in common too), so this
 * does not belong in {@code src/client} — nothing here draws, it only supplies text.
 */
public class PrimedCoreItem extends Item {

    private final CoreFamily family;

    public PrimedCoreItem(Properties properties, CoreFamily family) {
        super(properties);
        this.family = family;
    }

    public CoreFamily family() {
        return family;
    }

    /**
     * §4.1 — "The tooltip updates on that first event — {@code Attuning: Zombie 1/16} — so the lock
     * is never a surprise you discover at 16/16." Before the lock it says what the core is waiting
     * for and, deliberately, that it has to be held for that to count.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        CoreAttunement attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
        if (attunement == null) {
            return;
        }
        int threshold = HsConfigManager.get().attunementThresholds().forFamily(family);

        if (attunement.unattuned()) {
            lines.accept(Component.translatable("item.heartstead.core.unattuned." + family.id())
                    .withStyle(ChatFormatting.GRAY));
            lines.accept(Component.translatable("item.heartstead.core.hand_only")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Component target = CoreTargets.displayName(family, attunement.target().orElseThrow());
        lines.accept(Component.translatable("item.heartstead.core.attuning", target,
                attunement.progress(), threshold).withStyle(ChatFormatting.AQUA));
    }
}
