package com.heartstead.gametest;

import com.heartstead.registry.HsAttachments;
import com.heartstead.villager.TaughtTradeInjector;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.lang.reflect.Method;

/**
 * DESIGN.md §7.5 proof of concept — a taught trade injected onto a librarian must survive vanilla
 * {@code Offers} regeneration on level-up and restock, plus an entity save/load round trip standing
 * in for chunk unload/reload.
 *
 * <p>True world reload (a full server restart) cannot be exercised inside a single GameTest run — see
 * the manual checklist in the phase report.
 */
public class VillagerTradePersistenceGameTests {

    @GameTest
    public void taughtTradeSurvivesLevelUp(GameTestHelper helper) {
        Villager villager = spawnTaughtLibrarian(helper);
        invokeUpdateTrades(villager, helper.getLevel());

        helper.succeedIf(() -> {
            if (!offerPresent(villager)) {
                throw new AssertionError("taught trade did not survive updateTrades (level-up regeneration)");
            }
        });
    }

    @GameTest
    public void taughtTradeSurvivesRestock(GameTestHelper helper) {
        Villager villager = spawnTaughtLibrarian(helper);
        villager.restock();

        helper.succeedIf(() -> {
            if (!offerPresent(villager)) {
                throw new AssertionError("taught trade did not survive restock()");
            }
        });
    }

    @GameTest
    public void taughtTradeSurvivesChunkReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager original = spawnTaughtLibrarian(helper);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        original.save(output);
        CompoundTag savedTag = output.buildResult();
        original.discard();

        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedTag);
        Entity reloaded = EntityType.create(EntityTypes.VILLAGER, input, level, EntitySpawnReason.LOAD)
                .orElseThrow(() -> new AssertionError("villager failed to deserialize from its own saved NBT"));
        level.addFreshEntity(reloaded);
        Villager reloadedVillager = (Villager) reloaded;

        boolean attachmentSurvivedReload =
                Boolean.TRUE.equals(((AttachmentTarget) reloadedVillager).getAttached(HsAttachments.TAUGHT_TRADE));

        // Simulate vanilla regenerating Offers after the chunk comes back (the exact failure mode
        // DESIGN.md §7.5 calls out for 26.2's Offers persistence fix) and confirm the attachment,
        // which did survive the NBT round trip, is enough to reapply the trade.
        reloadedVillager.setOffers(new MerchantOffers());
        invokeUpdateTrades(reloadedVillager, level);
        boolean offerReapplied = offerPresent(reloadedVillager);

        helper.succeedIf(() -> {
            if (!attachmentSurvivedReload) {
                throw new AssertionError("taught_trade attachment did not survive entity save/load round trip");
            }
            if (!offerReapplied) {
                throw new AssertionError("taught trade was not reapplied after simulated post-reload regeneration");
            }
        });
    }

    private static Villager spawnTaughtLibrarian(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, 1, 2, 1);
        villager.setVillagerData(
                villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));
        TaughtTradeInjector.teach(villager);
        return villager;
    }

    private static boolean offerPresent(Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getResult().is(Items.DIAMOND)) {
                return true;
            }
        }
        return false;
    }

    /** {@code Villager#updateTrades} is protected — reflection reaches the exact method vanilla calls on level-up. */
    private static void invokeUpdateTrades(Villager villager, ServerLevel level) {
        try {
            Method updateTrades = Villager.class.getDeclaredMethod("updateTrades", ServerLevel.class);
            updateTrades.setAccessible(true);
            updateTrades.invoke(villager, level);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke Villager#updateTrades via reflection", e);
        }
    }
}
