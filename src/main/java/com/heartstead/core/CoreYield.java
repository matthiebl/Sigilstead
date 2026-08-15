package com.heartstead.core;

import com.heartstead.Heartstead;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN.md §4.2 — the single place a socketed core turns time into items. Every core loot table in
 * §4 and §5 goes through {@link #produce}; there is no second path, deliberately, because §4.2's
 * no-Sigils rule has to hold for tables this phase has never seen.
 *
 * <h2>The no-Sigils-from-cores rule, twice over</h2>
 *
 * §4.2: "No core ever yields a Sigil. Not at any housing, any tier, or through any loot table it
 * inherits… it is the rule that keeps the economy from bootstrapping." It is enforced here at two
 * independent layers, because §4.2 also says it is "exactly the sort of invariant a loot-table
 * refactor reintroduces silently":
 *
 * <ol>
 *   <li><b>Structurally</b>, via the hook Phase 2.5b left. {@link com.heartstead.economy.SigilLoot}
 *       guards every Sigil pool it injects with {@code killedByPlayer}, so a roll made without a
 *       {@code LAST_DAMAGE_PLAYER} parameter cannot pass it. {@link #entityParams} never supplies
 *       one — that is the contract {@code SigilLoot}'s javadoc asks Phase 4 to keep.</li>
 *   <li><b>By filter</b>, via {@link #NEVER_FROM_CORES}. The structural guard only covers pools
 *       <em>this mod</em> injected; a datapack or a future §5 table could put a Sigil in a mob's own
 *       loot, and the rule is absolute rather than best-effort. The tag is data
 *       ({@code data/heartstead/tags/item/never_from_cores.json}) so the exclusion list stays
 *       editable without a recompile, and it covers the Sigil's three children too: a core producing
 *       Vault Sigils bootstraps just as hard as one producing Sigils.</li>
 * </ol>
 *
 * The GameTest that matters here is {@code CoreYieldGameTests} — socket a core, produce, assert zero
 * Sigils ever appear.
 */
public final class CoreYield {

    /** §4.2 — the absolute exclusion list. Anything in this tag is dropped from a core's output. */
    public static final TagKey<Item> NEVER_FROM_CORES =
            TagKey.create(Registries.ITEM, Heartstead.id("never_from_cores"));

    private CoreYield() {
    }

    /**
     * Rolls {@code core}'s loot table {@code rolls} times and returns what it produced, already
     * filtered by {@link #NEVER_FROM_CORES}. Returns empty when the core has no target, when its
     * target no longer exists, or when that target has no loot table — a housing holding such a core
     * quietly produces nothing rather than failing.
     */
    public static List<ItemStack> produce(ServerLevel level, CoreAttunement core, BlockPos pos, int rolls) {
        Optional<net.minecraft.resources.Identifier> target = core.target();
        if (target.isEmpty() || rolls <= 0) {
            return List.of();
        }

        Optional<ResourceKey<LootTable>> tableKey = CoreTargets.lootTable(core.family(), target.get());
        if (tableKey.isEmpty()) {
            return List.of();
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey.get());

        Optional<LootParams> params = params(level, core.family(), target.get(), pos);
        if (params.isEmpty()) {
            return List.of();
        }

        List<ItemStack> produced = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            table.getRandomItems(params.get(), stack -> {
                if (!stack.isEmpty() && !stack.is(NEVER_FROM_CORES)) {
                    produced.add(stack);
                }
            });
        }
        return produced;
    }

    private static Optional<LootParams> params(ServerLevel level, CoreFamily family,
                                               net.minecraft.resources.Identifier target, BlockPos pos) {
        // §5's classic cores roll a bespoke heartstead loot table that needs no context at all — six
        // of them do not even name a real entity or block (ClassicCore's javadoc explains why), so
        // building an ENTITY or BLOCK LootParams for them is both unnecessary and, for those six,
        // impossible.
        if (ClassicCore.forTarget(target).isPresent()) {
            return Optional.of(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
        }
        return switch (family.imprint()) {
            case ENTITY -> entityParams(level, target, pos);
            case BLOCK -> blockParams(level, target, pos);
        };
    }

    /**
     * The {@code ENTITY} parameter set requires a {@code THIS_ENTITY}, so one is instantiated and
     * never added to the world — it exists only long enough for the loot table to read its type and
     * is discarded here. It is never ticked, never sees a chunk and never renders.
     *
     * <p><b>{@code LAST_DAMAGE_PLAYER} is deliberately absent</b>, and must stay absent. That is the
     * §4.2 hook: it is what makes "no Sigils from cores" a structural property of the loot context
     * rather than a rule a future edit has to remember. It also means player-kill-only vanilla drops
     * (a wither skeleton's skull is not one, but a zombie's rare drops are) behave as if a non-player
     * killed the mob, which is the right reading of a farm that runs while you are asleep.
     */
    private static Optional<LootParams> entityParams(ServerLevel level,
                                                     net.minecraft.resources.Identifier target, BlockPos pos) {
        Optional<EntityType<?>> type = CoreTargets.entityType(target);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Entity entity = type.get().create(level, EntitySpawnReason.LOAD);
        if (entity == null) {
            return Optional.empty();
        }
        Vec3 origin = Vec3.atCenterOf(pos);
        entity.snapTo(origin.x, origin.y, origin.z, 0.0f, 0.0f);

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        entity.discard();
        return Optional.of(params);
    }

    /**
     * The {@code BLOCK} parameter set's {@code TOOL} is supplied empty on purpose. An enchanted tool
     * would let a core inherit Fortune or Silk Touch, which §4.2 never grants it — the core's rate
     * and its tier are the only things that scale its output.
     */
    private static Optional<LootParams> blockParams(ServerLevel level,
                                                    net.minecraft.resources.Identifier target, BlockPos pos) {
        Optional<Block> block = CoreTargets.block(target);
        if (block.isEmpty()) {
            return Optional.empty();
        }
        BlockState state = harvestState(block.get());

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);
        return Optional.of(params);
    }

    /**
     * §4.1 says a Verdant Core imprints on "a mature crop", so its yield must be the mature state —
     * a default-state wheat block drops one seed and nothing else, which would silently make every
     * Verdant Core worthless. Non-crop blocks have no such distinction and use their default state.
     */
    private static BlockState harvestState(Block block) {
        BlockState state = block.defaultBlockState();
        if (block instanceof net.minecraft.world.level.block.CropBlock crop) {
            return crop.getStateForAge(crop.getMaxAge());
        }
        return state;
    }
}
