package com.sigilstead.vault;

import com.sigilstead.Sigilstead;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level Vault state (DESIGN.md §2): shared item contents, the count of Vault Sigils spent at
 * the Anchor ({@link VaultCapacityTier} turns that into a capacity), the §2.1 activation flags, and
 * the §2.3 reach tiers. One instance per world (CONVENTIONS.md §4) — {@link Vault} always resolves
 * it off the overworld's save data, so a Vault accessed from any dimension is the same shared store
 * rather than one per dimension.
 *
 * <p><b>Why activation is two flags, not one.</b> {@code activated} is whether an Anchor is standing
 * and live right now; {@code everActivated} is whether this world has <em>ever</em> had one. §2.1
 * prices the first activation at zero and every one after it at a Vault Sigil, so the second flag is
 * what stops a player breaking and re-placing the Anchor to keep paying nothing. It is never cleared.
 *
 * <p>Capacity ({@code sigilsSpent}) and {@code reachTiers} are deliberately untouched by
 * deactivation — §2.1's "capacity and reach are never lost", which is what stops one player on a
 * server deleting everyone else's progress by breaking a block.
 *
 * <p>Carries a schema version so a future shape change has somewhere to migrate from instead of
 * guessing at an unversioned blob (CONVENTIONS.md §4). Everything v2 added is an optional field with
 * a v1-safe default, so a v1 blob loads as a dormant Vault that has never been activated — which is
 * exactly what a pre-activation world was. v3 re-keyed {@code contents} from a bare {@link Item} to
 * a {@link VaultKey}, so stacks keep their components; the field decodes either shape and a v1/v2
 * blob's items load as component-free keys, which is exactly what they were.
 *
 * <p><b>Two indexes over one map.</b> {@code contents} counts per {@link VaultKey} — that is what a
 * grid cell and a withdrawal address. {@code itemTotals} is a derived per-{@link Item} rollup, and
 * it is what §2.3's capacity check reads: the distinct-type cap counts item types, and the per-type
 * depth cap pools every variant of one item. Both are maintained together in {@link #add} and
 * {@link #remove}, never recomputed by callers.
 *
 * <p>Raw map mutation lives here; capacity enforcement lives in {@link Vault}, which is the
 * intended entry point for everything but this class's own package.
 */
public final class VaultData extends SavedData {

    public static final int CURRENT_VERSION = 3;

    // Declared ahead of TYPE on purpose: TYPE's initializer calls codec(), and a static field
    // read before its own declaration is null rather than a compile error.
    /**
     * v3's shape: an ordered list of {variant, count}, because a map keyed on a full
     * {@link ItemStack} has no sane string key to hash on. The list also keeps insertion order,
     * which is the order the §2.4 grid falls back to when the player hasn't picked a sort.
     */
    private static final Codec<List<Map.Entry<VaultKey, Integer>>> V3_CONTENTS_CODEC =
            RecordCodecBuilder.<Map.Entry<VaultKey, Integer>>create(instance -> instance
                    .group(
                            VaultKey.CODEC.fieldOf("item").forGetter(Map.Entry::getKey),
                            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(Map.Entry::getValue))
                    .apply(instance, Map::entry))
                    .listOf();

    /** v1/v2's shape: counts per bare item id. Read-only — nothing writes it any more. */
    private static final Codec<Map<Item, Integer>> LEGACY_CONTENTS_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.intRange(1, Integer.MAX_VALUE));

    /**
     * Decodes either shape and always encodes v3, so an existing world migrates the first time it
     * saves. A pre-v3 blob's items become component-free keys, which is what they already were —
     * nothing in a v2 Vault could carry components, since the old contents map had nowhere to put
     * them.
     */
    private static final Codec<Map<VaultKey, Integer>> CONTENTS_CODEC =
            Codec.either(V3_CONTENTS_CODEC, LEGACY_CONTENTS_CODEC)
                    .xmap(
                            either -> either.map(VaultData::fromEntries, VaultData::fromLegacy),
                            contents -> Either.left(List.copyOf(contents.entrySet())));

    private static Map<VaultKey, Integer> fromEntries(List<Map.Entry<VaultKey, Integer>> entries) {
        Map<VaultKey, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<VaultKey, Integer> entry : entries) {
            map.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return map;
    }

    private static Map<VaultKey, Integer> fromLegacy(Map<Item, Integer> legacy) {
        Map<VaultKey, Integer> map = new LinkedHashMap<>();
        legacy.forEach((item, count) -> map.merge(VaultKey.of(item), count, Integer::sum));
        return map;
    }

    public static final SavedDataType<VaultData> TYPE =
            new SavedDataType<>(Sigilstead.id("vault"), VaultData::createNew, codec(), DataFixTypes.LEVEL);

    private final int version;
    private final Map<VaultKey, Integer> contents;

    /** Per-{@link Item} rollup of {@link #contents}, for §2.3's two capacity caps. Never persisted. */
    private final Map<Item, Integer> itemTotals = new LinkedHashMap<>();
    private int sigilsSpent;
    private Optional<BlockPos> anchorPos;
    private Optional<ResourceKey<Level>> anchorDimension;
    private boolean activated;
    private boolean everActivated;
    private final Set<ResourceKey<Level>> reachTiers;

    /**
     * Bumped on every mutation, never persisted. {@link com.sigilstead.vault.VaultMenu} (and Phase
     * 3's Artisan, per REFERENCES.md) poll this each tick to know whether their synced client
     * snapshot is stale, rather than the Vault pushing updates itself.
     */
    private int revision;

    private VaultData(
            int version,
            Map<VaultKey, Integer> contents,
            int sigilsSpent,
            Optional<BlockPos> anchorPos,
            Optional<ResourceKey<Level>> anchorDimension,
            boolean activated,
            boolean everActivated,
            List<ResourceKey<Level>> reachTiers) {
        this.version = version;
        this.contents = new LinkedHashMap<>(contents);
        this.contents.forEach((key, count) -> itemTotals.merge(key.item(), count, Integer::sum));
        this.sigilsSpent = sigilsSpent;
        this.anchorPos = anchorPos;
        this.anchorDimension = anchorDimension;
        this.activated = activated;
        this.everActivated = everActivated;
        this.reachTiers = new LinkedHashSet<>(reachTiers);
    }

    private static VaultData createNew() {
        return new VaultData(CURRENT_VERSION, Map.of(), 0, Optional.empty(), Optional.empty(), false, false, List.of());
    }

    private static Codec<VaultData> codec() {
        return RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.INT.fieldOf("version").forGetter(v -> v.version),
                        CONTENTS_CODEC.fieldOf("contents").forGetter(v -> v.contents),
                        Codec.intRange(0, Integer.MAX_VALUE)
                                .fieldOf("sigils_spent")
                                .forGetter(v -> v.sigilsSpent),
                        BlockPos.CODEC.optionalFieldOf("anchor_pos").forGetter(v -> v.anchorPos),
                        Level.RESOURCE_KEY_CODEC.optionalFieldOf("anchor_dimension").forGetter(v -> v.anchorDimension),
                        Codec.BOOL.optionalFieldOf("activated", false).forGetter(v -> v.activated),
                        Codec.BOOL.optionalFieldOf("ever_activated", false).forGetter(v -> v.everActivated),
                        Level.RESOURCE_KEY_CODEC.listOf()
                                .optionalFieldOf("reach_tiers", List.of())
                                .forGetter(v -> List.copyOf(v.reachTiers)))
                .apply(instance, VaultData::new));
    }

    /** Stored count for one variant — item and components together; zero if the Vault holds none. */
    public int count(VaultKey key) {
        return contents.getOrDefault(key, 0);
    }

    /**
     * Stored count for an item across <em>every</em> variant of it, which is what §2.3's per-type
     * depth cap limits. Ten enchanted swords and one plain one are eleven here.
     */
    public int count(Item item) {
        return itemTotals.getOrDefault(item, 0);
    }

    /**
     * Number of distinct item types currently stored, for the §2.3 distinct-type capacity check.
     * Counted per {@link Item}, not per variant — see {@link VaultKey} for why enchanting a sword
     * must not cost a Vault slot.
     */
    public int distinctTypeCount() {
        return itemTotals.size();
    }

    /** The stored variants of {@code item}, in insertion order. Empty if the Vault holds none. */
    public List<VaultKey> variantsOf(Item item) {
        List<VaultKey> keys = new ArrayList<>();
        for (VaultKey key : contents.keySet()) {
            if (key.item() == item) {
                keys.add(key);
            }
        }
        return keys;
    }

    public int sigilsSpent() {
        return sigilsSpent;
    }

    /**
     * Immutable snapshot of every stored variant, in insertion order, for {@link VaultMenu}'s client
     * sync. Ordered rather than {@code Map.copyOf} so the grid doesn't reshuffle itself every time
     * the Vault is saved and reloaded.
     */
    public Map<VaultKey, Integer> contents() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(contents));
    }

    /** Bumped on every mutation; callers diff this against a last-seen value to know when to resync. */
    public int revision() {
        return revision;
    }

    public Optional<BlockPos> anchorPos() {
        return anchorPos;
    }

    /** The dimension the standing Anchor is in — §2.3's local reach only applies inside it. */
    public Optional<ResourceKey<Level>> anchorDimension() {
        return anchorDimension;
    }

    /** §2.1 — whether an Anchor is standing and live right now. A dormant Vault's storage tab is locked. */
    public boolean activated() {
        return activated;
    }

    /** §2.1 — whether this world has ever had an activated Anchor. Only the first one is free. */
    public boolean everActivated() {
        return everActivated;
    }

    /** §2.3 — the dimensions the world has bought withdrawal reach into. Independent, not cumulative. */
    public Set<ResourceKey<Level>> reachTiers() {
        return Set.copyOf(reachTiers);
    }

    void setAnchor(BlockPos pos, ResourceKey<Level> dimension) {
        anchorPos = Optional.of(pos);
        anchorDimension = Optional.of(dimension);
        revision++;
        setDirty();
    }

    void setActivated(boolean value) {
        activated = value;
        if (value) {
            everActivated = true;
        }
        revision++;
        setDirty();
    }

    void markEverActivated() {
        everActivated = true;
        revision++;
        setDirty();
    }

    void grantReach(ResourceKey<Level> dimension) {
        if (reachTiers.add(dimension)) {
            revision++;
            setDirty();
        }
    }

    void revokeReach(ResourceKey<Level> dimension) {
        if (reachTiers.remove(dimension)) {
            revision++;
            setDirty();
        }
    }

    void add(VaultKey key, int amount) {
        if (amount <= 0) {
            return;
        }
        contents.merge(key, amount, Integer::sum);
        itemTotals.merge(key.item(), amount, Integer::sum);
        revision++;
        setDirty();
    }

    void remove(VaultKey key, int amount) {
        if (amount <= 0) {
            return;
        }
        int stored = count(key);
        int taken = Math.min(stored, amount);
        if (taken <= 0) {
            return;
        }
        if (stored == taken) {
            contents.remove(key);
        } else {
            contents.put(key, stored - taken);
        }
        int itemRemaining = itemTotals.getOrDefault(key.item(), 0) - taken;
        if (itemRemaining <= 0) {
            itemTotals.remove(key.item());
        } else {
            itemTotals.put(key.item(), itemRemaining);
        }
        revision++;
        setDirty();
    }

    void spendSigil() {
        sigilsSpent++;
        revision++;
        setDirty();
    }
}
