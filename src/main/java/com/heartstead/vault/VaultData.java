package com.heartstead.vault;

import com.heartstead.Heartstead;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * exactly what a pre-activation world was.
 *
 * <p>Raw map mutation lives here; capacity enforcement lives in {@link Vault}, which is the
 * intended entry point for everything but this class's own package.
 */
public final class VaultData extends SavedData {

    public static final int CURRENT_VERSION = 2;

    public static final SavedDataType<VaultData> TYPE =
            new SavedDataType<>(Heartstead.id("vault"), VaultData::createNew, codec(), DataFixTypes.LEVEL);

    private final int version;
    private final Map<Item, Integer> contents;
    private int sigilsSpent;
    private Optional<BlockPos> anchorPos;
    private Optional<ResourceKey<Level>> anchorDimension;
    private boolean activated;
    private boolean everActivated;
    private final Set<ResourceKey<Level>> reachTiers;

    /**
     * Bumped on every mutation, never persisted. {@link com.heartstead.vault.VaultMenu} (and Phase
     * 3's Artisan, per REFERENCES.md) poll this each tick to know whether their synced client
     * snapshot is stale, rather than the Vault pushing updates itself.
     */
    private int revision;

    private VaultData(
            int version,
            Map<Item, Integer> contents,
            int sigilsSpent,
            Optional<BlockPos> anchorPos,
            Optional<ResourceKey<Level>> anchorDimension,
            boolean activated,
            boolean everActivated,
            List<ResourceKey<Level>> reachTiers) {
        this.version = version;
        this.contents = new LinkedHashMap<>(contents);
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
                        Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.intRange(1, Integer.MAX_VALUE))
                                .fieldOf("contents")
                                .forGetter(v -> Map.copyOf(v.contents)),
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

    /** Stored count for one item type; zero if the Vault holds none. */
    public int count(Item item) {
        return contents.getOrDefault(item, 0);
    }

    /** Number of distinct item types currently stored, for the §2.3 distinct-type capacity check. */
    public int distinctTypeCount() {
        return contents.size();
    }

    public int sigilsSpent() {
        return sigilsSpent;
    }

    /** Immutable snapshot of every distinct type currently stored, for {@link VaultMenu}'s client sync. */
    public Map<Item, Integer> contents() {
        return Map.copyOf(contents);
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

    void add(Item item, int amount) {
        if (amount <= 0) {
            return;
        }
        contents.merge(item, amount, Integer::sum);
        revision++;
        setDirty();
    }

    void remove(Item item, int amount) {
        if (amount <= 0) {
            return;
        }
        int remaining = Math.max(0, count(item) - amount);
        if (remaining == 0) {
            contents.remove(item);
        } else {
            contents.put(item, remaining);
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
