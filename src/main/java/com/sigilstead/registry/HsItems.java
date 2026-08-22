package com.sigilstead.registry;

import com.sigilstead.Sigilstead;
import com.sigilstead.core.CoreAttunement;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.item.CoreItem;
import com.sigilstead.item.HeartSigilItem;
import com.sigilstead.item.PrimedCoreItem;
import com.sigilstead.item.SealedTomeItem;
import com.sigilstead.item.VaultAccessItem;
import com.sigilstead.vault.VaultAccess;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Item registration (DESIGN.md §1, §6, CONVENTIONS.md §2).
 *
 * <p>Real registered items with their own ids — no {@code custom_data} identity markers and no
 * base-item table.
 *
 * <p>This is the whole §1 progression spine: one found {@link #SIGIL}, its three children, and the
 * three dimensional Vault Sigils §2.3 buys withdrawal reach with. Nothing else in the pack is
 * currency, and only the Sigil itself is uncraftable.
 */
public final class HsItems {

    private HsItems() {
    }

    /** §1 — the only currency. Found, never craftable; see {@link com.sigilstead.economy.SigilLoot}. */
    public static final Item SIGIL = register("sigil", new Item.Properties());

    /** §1 / §4.1 — primed into a farm core. Cheapest of the three surcharges, per the §1.1 guardrail. */
    public static final Item CORE_SIGIL = register("core_sigil", new Item.Properties());

    /** §1 / §6 — consumed for +1 max heart. */
    public static final Item HEART_SIGIL =
            register("heart_sigil", new Item.Properties(), HeartSigilItem::new);

    /** §1 / §2.3 — Vault capacity, the Vault Pouch, and re-anchoring. */
    public static final Item VAULT_SIGIL = register("vault_sigil", new Item.Properties());

    /** §2.3 — overworld-wide withdrawal. Echo Shard is the proof item (§12.2). */
    public static final Item OVERWORLD_VAULT_SIGIL =
            register("overworld_vault_sigil", new Item.Properties());

    /** §2.3 — Nether-wide withdrawal. Ghast Tear + Blaze Powder are the proof items (§12.2). */
    public static final Item NETHER_VAULT_SIGIL =
            register("nether_vault_sigil", new Item.Properties());

    /** §2.3 — End-wide withdrawal. Dragon's Breath is the proof item (§12.2). */
    public static final Item END_VAULT_SIGIL = register("end_vault_sigil", new Item.Properties());

    /**
     * §2.2 T1 — deposit from anywhere, any dimension, and no withdrawal. §2.0 calls this "the safe
     * verb": the worst it can do is spare you a walk home. Stacks to one, because a second Satchel
     * does nothing a first one doesn't.
     */
    public static final Item SATCHEL = register(
            "satchel",
            new Item.Properties().stacksTo(1),
            properties -> new VaultAccessItem(properties, VaultAccess.SATCHEL));

    /** §2.2 T2 — adds the withdrawal verb, still subject to the §2.3 reach tier for where you stand. */
    public static final Item VAULT_POUCH = register(
            "vault_pouch",
            new Item.Properties().stacksTo(1),
            properties -> new VaultAccessItem(properties, VaultAccess.POUCH));

    /** §3.3 Tome — placed empty into the Codex's empower slot to become a Sealed Tome. */
    public static final Item TOME = register("tome", new Item.Properties());

    /** §3.3 Sealed Tome — carries {@link com.sigilstead.codex.SealedTomeData}; taught to a librarian, then consumed. */
    public static final Item SEALED_TOME =
            register("sealed_tome", new Item.Properties().stacksTo(1), SealedTomeItem::new);

    // ---------------------------------------------------------------- §4 cores
    //
    // Two registered items per family, not one item with a "finished" flag. §4.1 has three recipes
    // that must tell them apart — prime (Core Sigil -> Primed), revert (Primed -> Core Sigil) and
    // §4.3's upgrade (Core -> Core) — and CONVENTIONS.md §2 makes that a matter of item identity
    // rather than of a component a recipe would have to inspect.
    //
    // Primed cores stack to one as well: two Primed Cores of a family in the same slot could not
    // hold two different targets, and §4.1's parallel-progress rule is explicitly per stack.

    /** §4.1 — primed for Soul (kill a hostile mob). Attunes into {@link #SOUL_CORE}. */
    public static final Item PRIMED_SOUL_CORE = registerPrimedCore(CoreFamily.SOUL);

    /** §4.1 — primed for Verdant (harvest a mature crop). Attunes into {@link #VERDANT_CORE}. */
    public static final Item PRIMED_VERDANT_CORE = registerPrimedCore(CoreFamily.VERDANT);

    /** §4.1 — primed for Pastoral (breed two animals). Attunes into {@link #PASTORAL_CORE}. */
    public static final Item PRIMED_PASTORAL_CORE = registerPrimedCore(CoreFamily.PASTORAL);

    /** §4.1 — primed for Lithic (mine a stone or earth block). Attunes into {@link #LITHIC_CORE}. */
    public static final Item PRIMED_LITHIC_CORE = registerPrimedCore(CoreFamily.LITHIC);

    /** §4.2 — the finished Soul Core. Sockets into a Soul Cage. */
    public static final Item SOUL_CORE = registerCore(CoreFamily.SOUL);

    /** §4.2 — the finished Verdant Core. Sockets into a Verdant Planter. */
    public static final Item VERDANT_CORE = registerCore(CoreFamily.VERDANT);

    /** §4.2 — the finished Pastoral Core. Sockets into a Paddock. */
    public static final Item PASTORAL_CORE = registerCore(CoreFamily.PASTORAL);

    /** §4.2 — the finished Lithic Core. Sockets into a Quarry Node. */
    public static final Item LITHIC_CORE = registerCore(CoreFamily.LITHIC);

    /**
     * Family lookups, built after the fields above so class initialisation order stays honest. The
     * items know their family; these give the other direction, which the prime recipes, the §4.1
     * conversion at threshold and the §4.2 housing slots all need.
     */
    private static final Map<CoreFamily, Item> PRIMED_CORES = new EnumMap<>(Map.of(
            CoreFamily.SOUL, PRIMED_SOUL_CORE,
            CoreFamily.VERDANT, PRIMED_VERDANT_CORE,
            CoreFamily.PASTORAL, PRIMED_PASTORAL_CORE,
            CoreFamily.LITHIC, PRIMED_LITHIC_CORE));

    private static final Map<CoreFamily, Item> CORES = new EnumMap<>(Map.of(
            CoreFamily.SOUL, SOUL_CORE,
            CoreFamily.VERDANT, VERDANT_CORE,
            CoreFamily.PASTORAL, PASTORAL_CORE,
            CoreFamily.LITHIC, LITHIC_CORE));

    /** §4.1 — the Primed Core item of {@code family}. */
    public static Item primedCore(CoreFamily family) {
        return PRIMED_CORES.get(family);
    }

    /** §4.1 / §4.2 — the finished Core item of {@code family}. */
    public static Item core(CoreFamily family) {
        return CORES.get(family);
    }

    /**
     * §4.1 — the fresh attunement is a <b>default component</b> on the item, not something the prime
     * recipe stamps on. That way every route to a Primed Core produces the same thing: the recipe,
     * {@code /give}, the creative tab, a datapack. A recipe-supplied component would leave the other
     * three handing out an inert core that never counts anything.
     */
    private static Item registerPrimedCore(CoreFamily family) {
        return register(
                "primed_" + family.id() + "_core",
                new Item.Properties()
                        .stacksTo(1)
                        .component(HsComponents.CORE_ATTUNEMENT, CoreAttunement.primed(family)),
                properties -> new PrimedCoreItem(properties, family));
    }

    private static Item registerCore(CoreFamily family) {
        return register(family.id() + "_core", new Item.Properties().stacksTo(1),
                properties -> new CoreItem(properties, family));
    }

    private static Item register(String path, Item.Properties properties) {
        return register(path, properties, Item::new);
    }

    private static Item register(String path, Item.Properties properties, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Sigilstead.id(path));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
