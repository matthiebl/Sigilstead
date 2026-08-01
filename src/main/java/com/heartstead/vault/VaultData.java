package com.heartstead.vault;

import com.heartstead.Heartstead;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level Vault state (DESIGN.md §2): shared item contents plus the count of Vault Sigils spent
 * at the Anchor, which {@link VaultCapacityTier} turns into a capacity. One instance per world
 * (CONVENTIONS.md §4) — {@link Vault} always resolves it off the overworld's save data, so a Vault
 * accessed from any dimension is the same shared store rather than one per dimension.
 *
 * <p>Carries a schema version so a future shape change has somewhere to migrate from instead of
 * guessing at an unversioned blob (CONVENTIONS.md §4).
 *
 * <p>Raw map mutation lives here; capacity enforcement lives in {@link Vault}, which is the
 * intended entry point for everything but this class's own package.
 */
public final class VaultData extends SavedData {

    public static final int CURRENT_VERSION = 1;

    public static final SavedDataType<VaultData> TYPE =
            new SavedDataType<>(Heartstead.id("vault"), VaultData::createNew, codec(), DataFixTypes.LEVEL);

    private final int version;
    private final Map<Item, Integer> contents;
    private int sigilsSpent;
    private Optional<BlockPos> anchorPos;

    /**
     * Bumped on every mutation, never persisted. {@link com.heartstead.vault.VaultMenu} (and Phase
     * 3's Artisan, per REFERENCES.md) poll this each tick to know whether their synced client
     * snapshot is stale, rather than the Vault pushing updates itself.
     */
    private int revision;

    private VaultData(int version, Map<Item, Integer> contents, int sigilsSpent, Optional<BlockPos> anchorPos) {
        this.version = version;
        this.contents = new LinkedHashMap<>(contents);
        this.sigilsSpent = sigilsSpent;
        this.anchorPos = anchorPos;
    }

    private static VaultData createNew() {
        return new VaultData(CURRENT_VERSION, Map.of(), 0, Optional.empty());
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
                        BlockPos.CODEC.optionalFieldOf("anchor_pos").forGetter(v -> v.anchorPos))
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

    void setAnchorPos(BlockPos pos) {
        anchorPos = Optional.of(pos);
        setDirty();
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
