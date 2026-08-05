package com.heartstead.core;

import com.heartstead.Heartstead;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * DESIGN.md §4.1 step 2 — imprinting. The whole of §4's "rewards the activity it replaces" lives
 * here: every qualifying event a player performs is offered to the cores they are carrying, and the
 * ones that care advance.
 *
 * <h2>The hand rules, exactly as §4.1 states them</h2>
 *
 * <ul>
 *   <li><b>A target only locks from the hand.</b> An un-attuned Primed Core takes a target only from
 *       the main hand or offhand — "you should never discover that a core in your backpack quietly
 *       became a chicken core". Main hand wins when both hold an un-attuned core of the family.</li>
 *   <li><b>Once a core has a target, it counts from anywhere in the inventory</b>, and cores on
 *       <em>different</em> targets advance in parallel: "nothing is queued and nothing waits its
 *       turn". A zombie Soul Core, a wheat Verdant Core and a deepslate Lithic Core are all fed by
 *       one afternoon of ordinary play.</li>
 *   <li><b>One event advances one core.</b> Two cores locked to the same target do not both tick off
 *       a single kill — that would halve the cost of the second core for no reason, and §4.3 refuses
 *       to run two of them anyway. The first matching core takes the credit and the walk stops
 *       there.</li>
 *   <li><b>Wrong-target events are inert.</b> No penalty, no reset — pillar 4. A core whose target
 *       does not match simply is not touched.</li>
 * </ul>
 *
 * <p>At the threshold the stack converts in place into the finished {@link com.heartstead.item.CoreItem},
 * keeping its target and its slot, so the player's next glance at their inventory shows
 * <i>Verdant Core (Wheat)</i> where the Primed Core was.
 */
public final class CoreImprint {

    /**
     * §4.1's four imprint lists — <b>what each family is allowed to lock onto</b> — as tags rather
     * than hardcoded predicates (CONVENTIONS.md §6). These are the balance surface of §4.1: they
     * decide which mob a Soul Core may become, and so which loot table §4.2 will farm forever.
     *
     * <ul>
     *   <li>{@code heartstead:soul_imprintable} — entity types a Soul Core may target</li>
     *   <li>{@code heartstead:pastoral_imprintable} — species a Pastoral Core may target</li>
     *   <li>{@code heartstead:verdant_imprintable} — crops a Verdant Core may target (maturity is
     *       still checked in code; a tag cannot express "at max age")</li>
     *   <li>{@code heartstead:lithic_imprintable} — blocks a Lithic Core may target</li>
     * </ul>
     *
     * <p>They are explicit allow-lists, not category rules. "Any hostile mob" would have quietly
     * included the Ender Dragon, the Wither and every §12.1 mini-boss — and an Evoker Soul Core farms
     * Totems of Undying at roughly a hundred times the rate §12.5 prices the dedicated raid core at.
     * A rule with exceptions that severe is better written down than inferred, and being data means
     * retuning it is a JSON edit rather than a recompile.
     */
    public static final TagKey<EntityType<?>> SOUL_IMPRINTABLE =
            TagKey.create(Registries.ENTITY_TYPE, Heartstead.id("soul_imprintable"));

    public static final TagKey<EntityType<?>> PASTORAL_IMPRINTABLE =
            TagKey.create(Registries.ENTITY_TYPE, Heartstead.id("pastoral_imprintable"));

    public static final TagKey<Block> VERDANT_IMPRINTABLE =
            TagKey.create(Registries.BLOCK, Heartstead.id("verdant_imprintable"));

    public static final TagKey<Block> LITHIC_IMPRINTABLE =
            TagKey.create(Registries.BLOCK, Heartstead.id("lithic_imprintable"));

    private CoreImprint() {
    }

    public static void register() {
        // Soul (§4.1) — kill a mob on the soul_imprintable list.
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
            if (killer instanceof ServerPlayer player && isImprintable(killed.getType(), SOUL_IMPRINTABLE)) {
                offer(player, CoreFamily.SOUL, BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()));
            }
        });

        // Verdant (§4.1) — harvest a MATURE crop. An immature break is not a harvest and must not
        // lock a target; a player clearing a field they just planted would otherwise imprint on it.
        // Lithic (§4.1) — mine a stone or earth block.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (state.is(VERDANT_IMPRINTABLE) && isMature(state)) {
                offer(serverPlayer, CoreFamily.VERDANT, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            }
            if (state.is(LITHIC_IMPRINTABLE)) {
                offer(serverPlayer, CoreFamily.LITHIC, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            }
        });
    }

    /** Whether {@code type} is on {@code tag} — {@link EntityType} has no {@code is(TagKey)} of its own. */
    public static boolean isImprintable(EntityType<?> type, TagKey<EntityType<?>> tag) {
        return type.builtInRegistryHolder().is(tag);
    }

    /**
     * §4.1 Pastoral — one successful breed. Called from {@code AnimalBreedMixin} because Fabric API
     * has no breeding event in 26.2; the hook is on the parent that produced the child, so a breed
     * counts once rather than twice.
     */
    public static void onBred(ServerPlayer player, Animal parent) {
        if (isImprintable(parent.getType(), PASTORAL_IMPRINTABLE)) {
            offer(player, CoreFamily.PASTORAL, BuiltInRegistries.ENTITY_TYPE.getKey(parent.getType()));
        }
    }

    /**
     * §4.1 — "harvest a <b>mature</b> crop". Read off whatever {@code age} property the block has
     * rather than off {@code CropBlock}, so nether wart (max 3), torchflower (2) and pitcher crop (4)
     * work alongside vanilla's four (7) — and so does anything a datapack adds to
     * {@link #VERDANT_IMPRINTABLE}. A block with no age property is treated as always mature, which
     * is the only sensible reading of "harvest" for something that does not grow.
     */
    private static boolean isMature(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && age.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.getValue(age) >= max;
            }
        }
        return true;
    }

    /**
     * Offers one qualifying event to the cores a player is carrying. <b>One event advances at most
     * one core.</b>
     *
     * <p>The first core already locked to this target takes the credit and the walk stops. If none is
     * carrying this target, the event instead locks one un-attuned core from a hand — so a single
     * kill either advances a core or aims one, never both and never several.
     *
     * <p>Search order is main hand, then offhand, then the rest of the inventory, so the core you are
     * actually holding is the one that benefits. That is the only ordering a player can predict
     * without opening their inventory, which matters because §4.1 promises attunement is "something
     * you finish without noticing you were doing it" — a rule you cannot see must at least be a rule
     * you would have guessed.
     */
    public static void offer(ServerPlayer player, CoreFamily family, Identifier target) {
        if (target == null) {
            return;
        }
        int threshold = HsConfigManager.get().attunementThresholds().forFamily(family);

        for (CoreSlot slot : carriedSlots(player)) {
            CoreAttunement attunement = primedAttunement(slot.get(), family);
            if (attunement != null && attunement.targets(target)) {
                slot.set(applied(slot.get(), attunement.advanced(), family, threshold));
                player.getInventory().setChanged();
                return;
            }
        }

        lockFromHand(player, family, target, threshold);
        player.getInventory().setChanged();
    }

    /**
     * §4.1 — "If un-attuned cores of the same family are in both hands, the main hand takes the lock."
     * Exactly one core locks per event, and only from a hand: a core in the backpack stays inert, so
     * you never discover that something in storage quietly became a chicken core.
     */
    private static void lockFromHand(ServerPlayer player, CoreFamily family, Identifier target, int threshold) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ItemStack stack = player.getItemBySlot(slot);
            CoreAttunement attunement = primedAttunement(stack, family);
            if (attunement != null && attunement.unattuned()) {
                player.setItemSlot(slot, applied(stack, attunement.lockTo(target), family, threshold));
                return;
            }
        }
    }

    /**
     * The stack this slot should hold after the event: the same Primed Core with a new attunement, or
     * — at the threshold — the finished Core carrying it. An {@link ItemStack}'s item is final, so
     * §4.1's "the stack converts into the finished Core" is a slot replacement, which is why every
     * caller writes the result back through the slot it came from.
     */
    private static ItemStack applied(ItemStack stack, CoreAttunement attunement, CoreFamily family, int threshold) {
        if (!attunement.complete(threshold)) {
            stack.set(HsComponents.CORE_ATTUNEMENT, attunement);
            return stack;
        }
        ItemStack finished = new ItemStack(HsItems.core(family));
        finished.set(HsComponents.CORE_ATTUNEMENT, attunement);
        return finished;
    }

    /** A carried stack, readable and replaceable — §4.1's conversion needs to write the slot, not the stack. */
    private interface CoreSlot {
        ItemStack get();

        void set(ItemStack stack);
    }

    /**
     * Every stack a player is carrying — §4.1's "anywhere in the inventory" — <b>in credit order</b>:
     * main hand, offhand, then the rest of the inventory. Since one event advances exactly one core,
     * this order is the rule, not an implementation detail: the core in your hand wins.
     *
     * <p>The offhand is listed explicitly because it is equipment rather than an inventory slot, and
     * the main-hand index is skipped on the second pass so it cannot be visited twice.
     */
    private static List<CoreSlot> carriedSlots(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<CoreSlot> slots = new ArrayList<>();
        int mainHand = inventory.getSelectedSlot();

        slots.add(inventorySlot(inventory, mainHand));
        slots.add(new CoreSlot() {
            @Override
            public ItemStack get() {
                return player.getItemBySlot(EquipmentSlot.OFFHAND);
            }

            @Override
            public void set(ItemStack stack) {
                player.setItemSlot(EquipmentSlot.OFFHAND, stack);
            }
        });

        for (int index = 0; index < inventory.getNonEquipmentItems().size(); index++) {
            if (index != mainHand) {
                slots.add(inventorySlot(inventory, index));
            }
        }

        return slots;
    }

    private static CoreSlot inventorySlot(Inventory inventory, int slot) {
        return new CoreSlot() {
            @Override
            public ItemStack get() {
                return inventory.getItem(slot);
            }

            @Override
            public void set(ItemStack stack) {
                inventory.setItem(slot, stack);
            }
        };
    }

    /** The attunement on {@code stack} if it is a Primed Core of {@code family}, else null. */
    private static CoreAttunement primedAttunement(ItemStack stack, CoreFamily family) {
        if (stack.isEmpty() || !(stack.getItem() instanceof com.heartstead.item.PrimedCoreItem primed)
                || primed.family() != family) {
            return null;
        }
        return stack.get(HsComponents.CORE_ATTUNEMENT);
    }
}
