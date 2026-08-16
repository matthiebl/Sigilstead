package com.heartstead.advancement;

import com.heartstead.Heartstead;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §7.3 — the criteria the discovery path needs and vanilla cannot observe.
 *
 * <p><b>Most of these are plain {@link PlayerTrigger} instances.</b> A milestone like "the Vault was
 * activated" carries no parameters, and {@code PlayerTrigger} is exactly the shape vanilla already
 * uses for its own parameterless events ({@code minecraft:slept_in_bed}, {@code minecraft:raid_win}):
 * a codec taking only an optional player predicate, and a public {@code trigger(ServerPlayer)}.
 * Writing a bespoke class per milestone would be nine copies of that file with the name changed.
 *
 * <p>The three below it take parameters, so they get real classes — which reach tier was bought,
 * which core tier was socketed, how many hearts you are on. Each keeps its parameter optional so a
 * JSON criterion can ignore it and mean "any".
 *
 * <p><b>These triggers hold no state.</b> They fire from the server-side call site that already
 * exists for the thing they describe, and if the advancement JSON were deleted tomorrow every one of
 * them would become a no-op. Nothing in §§2–6 may branch on whether a trigger fired.
 */
public final class HsTriggers {

    private HsTriggers() {
    }

    /** §2.1 — a dormant Anchor became an activated one, free or paid. */
    public static final PlayerTrigger VAULT_ACTIVATED = new PlayerTrigger();

    /** §2.0's free verb: a player put something into the Vault. Funnel deposits do not count — no player. */
    public static final PlayerTrigger VAULT_DEPOSITED = new PlayerTrigger();

    /** §2.0's ranged verb: a withdrawal that passed the reach check and actually moved items. */
    public static final PlayerTrigger VAULT_WITHDREW = new PlayerTrigger();

    /** §4.1 — a Primed Core hit its threshold and converted into the finished Core. */
    public static final PlayerTrigger CORE_ATTUNED = new PlayerTrigger();

    /** §4.2 — a player took yield out of a housing's buffer. The moment a core stops being a promise. */
    public static final PlayerTrigger CORE_YIELD_COLLECTED = new PlayerTrigger();

    /** §3.3 Archive — an enchantment entered the player's archive. */
    public static final PlayerTrigger CODEX_ARCHIVED = new PlayerTrigger();

    /** §3.3 Teach — a Sealed Tome successfully taught a librarian. */
    public static final PlayerTrigger VILLAGER_TAUGHT = new PlayerTrigger();

    /** §2.3 — a capacity or reach tier was bought at the Anchor. */
    public static final VaultUpgradedTrigger VAULT_UPGRADED = new VaultUpgradedTrigger();

    /** §4.2/§4.3 — an attuned core was accepted by a housing. */
    public static final CoreSocketedTrigger CORE_SOCKETED = new CoreSocketedTrigger();

    /** §6 — the player's heart count changed to this. */
    public static final HeartLevelTrigger HEART_LEVEL = new HeartLevelTrigger();

    public static void register() {
        register("vault_activated", VAULT_ACTIVATED);
        register("vault_deposited", VAULT_DEPOSITED);
        register("vault_withdrew", VAULT_WITHDREW);
        register("vault_upgraded", VAULT_UPGRADED);
        register("core_attuned", CORE_ATTUNED);
        register("core_socketed", CORE_SOCKETED);
        register("core_yield_collected", CORE_YIELD_COLLECTED);
        register("codex_archived", CODEX_ARCHIVED);
        register("villager_taught", VILLAGER_TAUGHT);
        register("heart_level", HEART_LEVEL);
    }

    private static void register(String path, CriterionTrigger<?> trigger) {
        Identifier id = Heartstead.id(path);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, id, trigger);
    }
}
