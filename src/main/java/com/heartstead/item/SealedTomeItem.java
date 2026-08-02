package com.heartstead.item;

import com.heartstead.codex.Codex;
import com.heartstead.codex.SealedTomeData;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsItems;
import com.heartstead.villager.TaughtTradeInjector;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * DESIGN.md §3.3 Teach — right-clicking a librarian while holding a Sealed Tome consumes it plus one
 * emerald and permanently teaches that villager the archived book, via
 * {@link TaughtTradeInjector}'s villager-attachment persistence (§7.2).
 *
 * <p><b>Not implemented as {@code Item#interactLivingEntity}.</b> That override is unreachable for a
 * Villager: {@code Player.interactOn} calls {@code entity.interact(...)} — which for any adult
 * villager with offers resolves to {@code Villager#mobInteract} opening the trade screen and
 * returning a <em>consuming</em> result — before it ever tries the held item's own interaction.
 * Vanilla has the same problem and solves it the same way: {@code Mob#checkAndHandleImportantInteractions}
 * special-cases Name Tags to run before {@code mobInteract}, but only for that one hardcoded item.
 * {@link com.heartstead.mixin.SealedTomeTeachMixin} injects at the head of {@code mobInteract} instead,
 * so {@link #tryTeach} runs before the trade screen has a chance to open.
 */
public class SealedTomeItem extends Item {

    public SealedTomeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(HsItems.SEALED_TOME);
        stack.set(HsComponents.SEALED_TOME_DATA, new SealedTomeData(enchantment, level));
        return stack;
    }

    /**
     * The actual Teach step. Returns empty when the interaction isn't a teach attempt at all (not
     * holding a Sealed Tome) or when it should fail soft, letting the villager's own
     * {@code mobInteract} run as normal (wrong profession) — in both cases
     * {@link com.heartstead.mixin.SealedTomeTeachMixin} leaves {@code mobInteract} uncancelled.
     * A present result means the Tome interaction is fully handled and {@code mobInteract} must not
     * run this tick — the trade screen must not open on top of a successful (or refused) Teach.
     */
    public static Optional<InteractionResult> tryTeach(ServerPlayer player, Villager villager, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(HsItems.SEALED_TOME)) {
            return Optional.empty();
        }
        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            player.sendSystemMessage(Component.translatable("gui.heartstead.codex.teach_not_librarian"));
            return Optional.empty();
        }
        SealedTomeData data = stack.get(HsComponents.SEALED_TOME_DATA);
        if (data == null) {
            return Optional.empty();
        }

        ServerLevel level = (ServerLevel) villager.level();
        Holder<Enchantment> holder = Codex.holderOf(level.registryAccess(), data.enchantment());
        int price = Codex.priceFor(holder, data.enchantment(), data.level());

        int emeraldCost = com.heartstead.config.HsConfigManager.get().codex().teachEmeraldCost();
        if (!hasEmeralds(player, emeraldCost)) {
            player.sendSystemMessage(Component.translatable("gui.heartstead.codex.teach_need_emerald", emeraldCost));
            return Optional.of(InteractionResult.CONSUME);
        }

        takeEmeralds(player, emeraldCost);
        stack.shrink(1);
        TaughtTradeInjector.teach(villager, data.enchantment(), holder, data.level(), price);
        player.sendSystemMessage(Component.translatable("gui.heartstead.codex.taught"));
        return Optional.of(InteractionResult.SUCCESS);
    }

    private static boolean hasEmeralds(ServerPlayer player, int count) {
        return countEmeralds(player) >= count;
    }

    private static int countEmeralds(ServerPlayer player) {
        int total = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(Items.EMERALD)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void takeEmeralds(ServerPlayer player, int count) {
        int remaining = count;
        var slots = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack s = slots.get(i);
            if (!s.is(Items.EMERALD)) {
                continue;
            }
            int taken = Math.min(s.getCount(), remaining);
            s.shrink(taken);
            remaining -= taken;
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SealedTomeData data = stack.get(HsComponents.SEALED_TOME_DATA);
        if (data == null) {
            return;
        }
        Holder<Enchantment> holder = Codex.holderOf(context.registries(), data.enchantment());
        tooltip.accept(Enchantment.getFullname(holder, data.level()));
    }
}
