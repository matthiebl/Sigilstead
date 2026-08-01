package com.heartstead.lives;

import com.heartstead.Heartstead;
import com.heartstead.config.HsConfig;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * DESIGN.md §6: consuming a Life Heart raises max health by one heart, capped at 20; dying costs
 * one heart, floored at 5. Both numbers are config fields (CONVENTIONS.md §5).
 *
 * <p>The player attachment {@link HsAttachments#HEART_LEVEL} is the source of truth. The
 * {@code max_health} attribute modifier is a derived value recomputed from it — on join (in case
 * the entity's own attribute state and the attachment ever disagree) and after every respawn,
 * since respawn hands the world a brand-new {@link ServerPlayer} instance with a fresh
 * {@link net.minecraft.world.entity.ai.attributes.AttributeMap} that needs the modifier reapplied.
 */
public final class LivesSystem {

    private static final Identifier MODIFIER_ID = Heartstead.id("heart_level");

    private LivesSystem() {
    }

    public static void register() {
        ServerPlayerEvents.JOIN.register(LivesSystem::onJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(LivesSystem::onAfterRespawn);
    }

    private static void onJoin(ServerPlayer player) {
        HeartLevel level = player.getAttachedOrCreate(HsAttachments.HEART_LEVEL, HeartLevel::initial);
        applyModifier(player, level.hearts());
    }

    private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        HeartLevel oldLevel = oldPlayer.getAttachedOrCreate(HsAttachments.HEART_LEVEL, HeartLevel::initial);
        int hearts = alive ? oldLevel.hearts() : afterDeath(oldLevel.hearts());

        HeartLevel newLevel = oldLevel.withHearts(hearts);
        newPlayer.setAttached(HsAttachments.HEART_LEVEL, newLevel);
        applyModifier(newPlayer, hearts);
    }

    private static int afterDeath(int hearts) {
        HsConfig config = HsConfigManager.get();
        return heartsAfterDeath(hearts, config.heartFloor(), config.heartLossOnDeath());
    }

    /** Pure clamp math, split out so it's testable without a running server (CONVENTIONS.md §8). */
    static int heartsAfterDeath(int hearts, int floor, int lossOnDeath) {
        return Math.max(floor, hearts - lossOnDeath);
    }

    /**
     * Consumes a Life Heart's worth of healing: +1 heart, capped. Returns {@code false} (and
     * changes nothing) if the player is already at the configured cap, so the item isn't wasted
     * on a no-op — the caller decides whether that refusal consumes the stack.
     */
    public static boolean consumeLifeHeart(ServerPlayer player) {
        HsConfig config = HsConfigManager.get();
        HeartLevel level = player.getAttachedOrCreate(HsAttachments.HEART_LEVEL, HeartLevel::initial);
        if (level.hearts() >= config.heartCap()) {
            return false;
        }

        int hearts = heartsAfterConsume(level.hearts(), config.heartCap());
        player.setAttached(HsAttachments.HEART_LEVEL, level.withHearts(hearts));
        applyModifier(player, hearts);
        player.heal(2.0f);
        return true;
    }

    /** Pure clamp math, split out so it's testable without a running server (CONVENTIONS.md §8). */
    static int heartsAfterConsume(int hearts, int cap) {
        return Math.min(cap, hearts + 1);
    }

    private static void applyModifier(ServerPlayer player, int hearts) {
        double extraHealth = (hearts - HeartLevel.STARTING_HEARTS) * 2.0;
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        if (extraHealth == 0.0) {
            attribute.removeModifier(MODIFIER_ID);
            return;
        }
        attribute.addOrReplacePermanentModifier(
                new AttributeModifier(MODIFIER_ID, extraHealth, AttributeModifier.Operation.ADD_VALUE));
    }
}
