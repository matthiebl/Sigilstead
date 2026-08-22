package com.sigilstead.mixin;

import com.sigilstead.core.ClassicCore;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreImprint;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN.md §5 Ominous Core — "Milestone: complete a level-5 ominous raid". Fabric API has no raid
 * event, so this hooks {@code Raid#tick} the same way {@code AnimalBreedMixin} hooks breeding: at
 * the one point vanilla itself knows the raid just won, right where it awards
 * {@code CriteriaTriggers.RAID_WIN} to each Hero of the Village.
 *
 * <p>Only Raid Omen level 5 counts — §12.5 prices the milestone at the maximum, the raid a level-5
 * Ominous Bottle actually buys, not any raid a player happens to survive.
 *
 * <p>{@code raidEvent}'s tracked players are a best-effort roster: it is only refreshed on a
 * 20-tick cadence during the post-victory celebration, so a player who left the fight moments before
 * victory may be missed. That is an acceptable approximation for a milestone gate, not a conservation
 * path — nothing here moves items, only who is offered the chance to lock a target.
 */
@Mixin(Raid.class)
abstract class RaidVictoryMixin {

    @Shadow
    private ServerBossEvent raidEvent;

    @Unique
    private boolean sigilstead$notifiedOminous;

    @Inject(method = "tick", at = @At("TAIL"))
    private void sigilstead$imprintOminous(ServerLevel level, CallbackInfo ci) {
        Raid self = (Raid) (Object) this;
        if (sigilstead$notifiedOminous || !self.isVictory() || self.getRaidOmenLevel() < self.getMaxRaidOmenLevel()) {
            return;
        }
        sigilstead$notifiedOminous = true;

        for (ServerPlayer player : raidEvent.getPlayers()) {
            CoreImprint.offer(player, CoreFamily.SOUL, ClassicCore.OMINOUS.target());
        }
    }
}
