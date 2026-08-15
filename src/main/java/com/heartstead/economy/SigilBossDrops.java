package com.heartstead.economy;

import com.heartstead.config.HsConfigManager;
import com.heartstead.config.SigilDrop;
import com.heartstead.registry.HsItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

/**
 * DESIGN.md §12.1 — the Ender Dragon's Sigils: 5 on the world's first kill, 1 on every respawn.
 *
 * <p>Separate from {@link SigilLoot} for two reasons. The dragon ships an <em>empty</em> loot table
 * (it drops XP and the egg through bespoke code), so a pool added there would never roll; and the
 * first-kill / respawn split is world state, which no loot condition can read.
 *
 * <p>The split is read from {@link EnderDragonFight#hasPreviouslyKilledDragon()}, which the fight
 * only sets once the dragon's death animation completes — well after {@code AFTER_DEATH} — so the
 * first kill still reads {@code false} here.
 *
 * <p>§4.2's no-Sigils-from-cores rule holds trivially: this pays on a real dragon entity dying, and
 * no core spawns one. The §5 Ender Core does not touch this hook either way — per §4.2, "the §5
 * cores whose whole point is such a drop … must ship their own loot table rather than inherit the
 * mob's", so the Ender Core rolls its own bespoke {@code heartstead:classic_cores/ender} table
 * ({@link com.heartstead.core.ClassicCore}) rather than a real enderman's. This dragon death only
 * decides whether the Ender Core's target is allowed to lock at all — see
 * {@code CoreImprint#registerClassicMilestones}.
 */
public final class SigilBossDrops {

    private SigilBossDrops() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof EnderDragon dragon) || !(entity.level() instanceof ServerLevel level)) {
                return;
            }

            EnderDragonFight fight = level.getDragonFight();
            boolean firstKill = fight == null || !fight.hasPreviouslyKilledDragon();
            SigilDrop drop = firstKill
                    ? HsConfigManager.get().sigil().enderDragonFirstKill()
                    : HsConfigManager.get().sigil().enderDragonRespawn();

            if (drop.isDisabled()) {
                return;
            }

            int amount = drop.min() >= drop.max()
                    ? drop.min()
                    : drop.min() + level.getRandom().nextInt(drop.max() - drop.min() + 1);
            if (amount <= 0 || level.getRandom().nextDouble() >= drop.chance()) {
                return;
            }

            dragon.spawnAtLocation(level, new ItemStack(HsItems.SIGIL, amount));
        });
    }
}
