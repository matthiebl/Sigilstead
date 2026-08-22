package com.sigilstead.core;

import com.sigilstead.Sigilstead;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * DESIGN.md §4.3 — the registry of active cores, as world-level {@link SavedData} (CONVENTIONS.md §4).
 *
 * <p>§4.3 is explicit that this is <b>world</b> state and not player state: "the constraint is on the
 * world, so on a server twenty players share one set of cores… it removes any question about what
 * happens when a player leaves." A per-player registry would let two players run the same zombie core
 * and double the world's output, which is the exact trivialisation §4.3 exists to prevent.
 *
 * <p>Stores a claim per {@link CoreKey}, remembering <em>where</em> the claim is so a refusal can name
 * the block that already holds it and so {@link ActiveCores} can spot a claim whose housing no longer
 * exists. Raw map mutation lives here; the liveness rules live in {@link ActiveCores}.
 */
public final class ActiveCoreData extends SavedData {

    public static final int CURRENT_VERSION = 1;

    public static final SavedDataType<ActiveCoreData> TYPE =
            new SavedDataType<>(Sigilstead.id("active_cores"), ActiveCoreData::createNew, codec(), DataFixTypes.LEVEL);

    /** One socketed core: which key it occupies, and the housing holding it. */
    public record Claim(CoreKey key, BlockPos pos, ResourceKey<Level> dimension) {

        public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        CoreKey.CODEC.fieldOf("key").forGetter(Claim::key),
                        BlockPos.CODEC.fieldOf("pos").forGetter(Claim::pos),
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Claim::dimension))
                .apply(instance, Claim::new));

        /** True when this claim is held by the housing at {@code otherPos} in {@code otherDimension}. */
        public boolean isAt(BlockPos otherPos, ResourceKey<Level> otherDimension) {
            return pos.equals(otherPos) && dimension.equals(otherDimension);
        }
    }

    private final int version;
    private final Map<CoreKey, Claim> claims;

    private ActiveCoreData(int version, List<Claim> claims) {
        this.version = version;
        this.claims = new LinkedHashMap<>();
        for (Claim claim : claims) {
            this.claims.put(claim.key(), claim);
        }
    }

    private static ActiveCoreData createNew() {
        return new ActiveCoreData(CURRENT_VERSION, List.of());
    }

    private static Codec<ActiveCoreData> codec() {
        return RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.INT.fieldOf("version").forGetter(v -> v.version),
                        Claim.CODEC.listOf().fieldOf("claims").forGetter(v -> List.copyOf(v.claims.values())))
                .apply(instance, ActiveCoreData::new));
    }

    public Optional<Claim> claim(CoreKey key) {
        return Optional.ofNullable(claims.get(key));
    }

    /** Every claim currently recorded — used by the GameTests and by nothing else. */
    public List<Claim> claims() {
        return List.copyOf(claims.values());
    }

    void put(Claim claim) {
        claims.put(claim.key(), claim);
        setDirty();
    }

    void remove(CoreKey key) {
        if (claims.remove(key) != null) {
            setDirty();
        }
    }
}
