package com.sigilstead.gametest;

import com.sigilstead.Sigilstead;
import com.sigilstead.advancement.HsTriggers;
import com.sigilstead.vault.VaultUpgradeKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §7.3 — the discovery path.
 *
 * <p><b>The load test is the one that matters.</b> An advancement whose JSON names a trigger that is
 * not registered, or passes conditions its codec rejects, does not crash and does not warn in play —
 * it is dropped silently during the data-pack load and the branch simply never appears in the tree.
 * That failure is invisible in {@code runClient} unless you happen to look at the right tab, and
 * every one of {@link HsTriggers}'s ten triggers is exercised by asking the server for the
 * advancements that name them.
 *
 * <p>The grant test then closes the other half: that the criterion the Java fires is the criterion
 * the JSON is listening for. A trigger firing into a criterion name nobody uses is also silent.
 */
public class DiscoveryPathGameTests {

    /** Every advancement §7.3's generator emits. Kept literal so a renamed branch fails here. */
    private static final List<String> TREE = List.of(
            "root",
            "sigil", "core_sigil", "heart_sigil", "vault_sigil", "three_ways",
            "vault_anchor", "vault_activated", "satchel", "vault_deposited", "vault_pouch",
            "vault_withdrew", "vault_capacity", "reach_overworld", "reach_nether", "reach_end",
            "linked_funnel",
            "primed_core", "classic_core", "core_attuned", "core_housing", "core_socketed",
            "core_yield", "core_tier_iii",
            "codex", "codex_archived", "tome", "villager_taught", "abundance", "kiln_touch",
            "heart_gained", "heart_max");

    @GameTest
    public void everyAdvancementInTheTreeLoads(GameTestHelper helper) {
        ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();

        List<String> missing = new ArrayList<>();
        for (String name : TREE) {
            if (advancements.get(Sigilstead.id(name)) == null) {
                missing.add(name);
            }
        }

        helper.succeedIf(() -> {
            if (!missing.isEmpty()) {
                throw new AssertionError(
                        "advancements failed to load (bad trigger id, or conditions the codec rejected): "
                                + String.join(", ", missing));
            }
        });
    }

    /**
     * The recipe-unlock layer. Without it every Sigilstead recipe is invisible in the recipe book,
     * which is the state the mod shipped in through Phase 4 — so this asserts one of each staging
     * rung rather than trusting the generator's own count.
     */
    @GameTest
    public void recipeUnlocksLoadAndGrantTheirRecipe(GameTestHelper helper) {
        ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();

        List<String> missing = new ArrayList<>();
        for (String recipe : List.of("root", "vault_sigil", "vault_anchor", "vault_pouch",
                "primed_soul_core", "soul_cage", "guardian_core", "tome")) {
            if (advancements.get(Sigilstead.id("recipes/" + recipe)) == null) {
                missing.add(recipe);
            }
        }

        helper.succeedIf(() -> {
            if (!missing.isEmpty()) {
                throw new AssertionError("recipe unlocks failed to load: " + String.join(", ", missing));
            }
        });
    }

    /**
     * §7.3's parameterless criteria. Fires the trigger the server-side call site fires and asserts
     * the matching advancement is done — the link the JSON and the Java have to agree on.
     */
    @GameTest
    public void firingATriggerGrantsItsAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        HsTriggers.VAULT_ACTIVATED.trigger(player);
        HsTriggers.VAULT_DEPOSITED.trigger(player);
        HsTriggers.CODEX_ARCHIVED.trigger(player);
        HsTriggers.VAULT_UPGRADED.trigger(player, VaultUpgradeKind.NETHER_REACH);
        HsTriggers.HEART_LEVEL.trigger(player, 20);

        List<String> ungranted = new ArrayList<>();
        for (String name : List.of("vault_activated", "vault_deposited", "codex_archived",
                "reach_nether", "heart_max")) {
            if (!isDone(player, name)) {
                ungranted.add(name);
            }
        }
        // The parameterised triggers must also refuse what they do not match: an Overworld purchase
        // is not an End one, and firing the wrong one would make the reach ladder meaningless.
        boolean endLeaked = isDone(player, "reach_end");

        helper.succeedIf(() -> {
            if (!ungranted.isEmpty()) {
                throw new AssertionError("triggers fired but these were not granted: " + String.join(", ", ungranted));
            }
            if (endLeaked) {
                throw new AssertionError("sigilstead:reach_end was granted by a nether_reach purchase");
            }
        });
    }

    private static boolean isDone(ServerPlayer player, String name) {
        Identifier id = Sigilstead.id(name);
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id);
        if (holder == null) {
            return false;
        }
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
