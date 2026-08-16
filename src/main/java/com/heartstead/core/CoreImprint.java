package com.heartstead.core;

import com.heartstead.Heartstead;
import com.heartstead.advancement.HsTriggers;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

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

    /** §5 Golem — how far from a just-built Iron Golem to look for the player who built it. */
    private static final double GOLEM_BUILD_CREDIT_BLOCKS = 16.0;

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

        registerClassicMilestones();
    }

    /**
     * DESIGN.md §5's four milestone cores that lock on a mob kill rather than a structure/dimension
     * gate with no matching mob: Guardian, Wither Skull, Ender and Shulker. Ominous (raid victory) and
     * Barter (piglin bartering) have no kill to hook and live in their own mixins instead.
     *
     * <p>Each targets a {@link ClassicCore#isSynthetic() synthetic} id rather than the real mob, so
     * this deliberately does <b>not</b> go through the {@link #SOUL_IMPRINTABLE} tag check the generic
     * path uses — three of these four mobs are excluded from that tag precisely so a generic Soul Core
     * can never farm them at the tag-gated rate (§4.1's exclusion table). Bypassing the tag here is
     * safe only because the target these four lock is one the generic path can never reach anyway.
     *
     * <p>Uses {@link ServerLivingEntityEvents#AFTER_DEATH} rather than the combat "killed other
     * entity" event, matching {@link com.heartstead.economy.SigilBossDrops}'s proven pattern for the
     * Ender Dragon rather than assuming the combat event covers boss deaths the same way it covers an
     * ordinary mob.
     */
    private static void registerClassicMilestones() {
        // Golem (§5, Counted) — "build 4 iron golems while carrying it". IronGolem#isPlayerCreated is
        // vanilla's own marker for exactly this (CarvedPumpkinBlock sets it the moment the golem-
        // building pattern completes, and vanilla uses the same flag to gate the golem attacking its
        // builder), so no mixin is needed here at all — unlike a natural village spawn, a player-built
        // golem is unambiguous.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem golem && golem.isPlayerCreated()) {
                if (level.getNearestPlayer(golem, GOLEM_BUILD_CREDIT_BLOCKS) instanceof ServerPlayer builder) {
                    offer(builder, CoreFamily.SOUL, ClassicCore.GOLEM.target());
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity.level() instanceof ServerLevel level) || !(killingPlayer(damageSource) instanceof ServerPlayer player)) {
                return;
            }

            if (entity.getType() == EntityTypes.ELDER_GUARDIAN) {
                offer(player, CoreFamily.SOUL, ClassicCore.GUARDIAN.target());
            } else if (entity.getType() == EntityTypes.WITHER_SKELETON && inStructure(level, entity.blockPosition(), BuiltinStructures.FORTRESS)) {
                offer(player, CoreFamily.SOUL, ClassicCore.WITHER_SKULL.target());
            } else if (entity.getType() == EntityTypes.SHULKER && inStructure(level, entity.blockPosition(), BuiltinStructures.END_CITY)) {
                offer(player, CoreFamily.SOUL, ClassicCore.SHULKER.target());
            } else if (entity.getType() == EntityTypes.ENDER_DRAGON) {
                offer(player, CoreFamily.SOUL, ClassicCore.ENDER.target());
            }
        });
    }

    private static Entity killingPlayer(DamageSource damageSource) {
        return damageSource.getEntity();
    }

    /** Whether {@code pos} falls inside a generated instance of {@code structure} — §5's location gates. */
    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> structure) {
        Structure resolved = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(structure);
        return resolved != null && level.structureManager().getStructureWithPieceAt(pos, resolved).isValid();
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
        int threshold = ClassicCore.forTarget(target)
                .map(classic -> HsConfigManager.get().classicCores().forCore(classic).threshold())
                .orElseGet(() -> HsConfigManager.get().attunementThresholds().forFamily(family));

        for (CoreSlot slot : carriedSlots(player)) {
            CoreAttunement attunement = primedAttunement(slot.get(), family);
            if (attunement != null && attunement.targets(target)) {
                slot.set(applied(player, slot.get(), attunement.advanced(), family, threshold));
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
                player.setItemSlot(slot, applied(player, stack, attunement.lockTo(target), family, threshold));
                return;
            }
        }
    }

    /**
     * The stack this slot should hold after the event: the same Primed Core with a new attunement, or
     * — at the threshold — the finished Core carrying it. An {@link ItemStack}'s item is final, so
     * §4.1's "the stack converts into the finished Core" is a slot replacement, which is why every
     * caller writes the result back through the slot it came from.
     *
     * <p>The conversion is also the only moment §4.1 ever completes, so it is where DESIGN.md §7.3's
     * attunement criterion fires. Purely observational — the return value does not depend on it.
     */
    private static ItemStack applied(
            ServerPlayer player, ItemStack stack, CoreAttunement attunement, CoreFamily family, int threshold) {
        if (!attunement.complete(threshold)) {
            stack.set(HsComponents.CORE_ATTUNEMENT, attunement);
            return stack;
        }
        ItemStack finished = new ItemStack(HsItems.core(family));
        finished.set(HsComponents.CORE_ATTUNEMENT, attunement);
        HsTriggers.CORE_ATTUNED.trigger(player);
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
