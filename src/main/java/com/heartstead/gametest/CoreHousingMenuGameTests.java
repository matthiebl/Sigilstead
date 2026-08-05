package com.heartstead.gametest;

import com.heartstead.blockentity.CoreHousingBlockEntity;
import com.heartstead.core.ActiveCores;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreHousingMenu;
import com.heartstead.core.CoreKey;
import com.heartstead.registry.HsBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §4.2 / §4.3 through the screen a player actually uses. {@code CoreSocketGameTests} drives
 * the block entity directly, which proves the rule but not the path: socketing happens by clicking a
 * core into a slot, and the §4.3 refusal has to survive being run from inside a menu click.
 */
public class CoreHousingMenuGameTests {

    /**
     * §4.3 — clicking a duplicate core into a second housing's slot. It must be refused and handed
     * back, the housing must stay empty, and above all it must not take the server down.
     */
    @GameTest
    public void clickingADuplicateCoreIntoASecondHousingIsRefusedCleanly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:vindicator";
        CoreKey key = new CoreKey(CoreFamily.SOUL, Identifier.parse(target));
        ActiveCores.claimPos(level, key).ifPresent(held -> ActiveCores.release(level, key, held));

        CoreHousingBlockEntity first = housing(helper, new BlockPos(1, 1, 1));
        first.setItem(CoreHousingBlockEntity.CORE_SLOT, CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));

        CoreHousingBlockEntity second = housing(helper, new BlockPos(3, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CoreHousingMenu menu = new CoreHousingMenu(1, player.getInventory(), second,
                second.getScreenOpeningData(player));

        // Carry a duplicate core and click it into the housing's core slot, exactly as a player does.
        menu.setCarried(CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));
        menu.clicked(0, 0, ContainerInput.PICKUP, player);

        boolean housingEmpty = second.getItem(CoreHousingBlockEntity.CORE_SLOT).isEmpty();
        boolean stillRunning = first.active();
        boolean secondInactive = !second.active();
        int coresHeld = countCores(player, menu);

        helper.succeedIf(() -> {
            if (!housingEmpty) {
                throw new AssertionError("the refused core stayed in the second housing's slot");
            }
            if (!secondInactive) {
                throw new AssertionError("the second housing is running a duplicate target");
            }
            if (!stillRunning) {
                throw new AssertionError("refusing the duplicate knocked out the housing that legitimately held it");
            }
            if (coresHeld != 1) {
                throw new AssertionError("the player ended up with " + coresHeld + " cores, expected exactly 1 — "
                        + "a refusal must neither destroy nor duplicate the core");
            }
        });
    }

    /**
     * The same refusal, reached by shift-clicking rather than by carrying. This is a separate test
     * because it is a separate code path: {@code QUICK_MOVE} runs {@link CoreHousingMenu#quickMoveStack}
     * in a loop until it stops making progress, so a refusal that hands the core straight back would
     * be handed the same core again on the next pass and never terminate.
     */
    @GameTest
    public void shiftClickingADuplicateCoreIsRefusedWithoutLooping(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:pillager";
        CoreKey key = new CoreKey(CoreFamily.SOUL, Identifier.parse(target));
        ActiveCores.claimPos(level, key).ifPresent(held -> ActiveCores.release(level, key, held));

        CoreHousingBlockEntity first = housing(helper, new BlockPos(1, 1, 1));
        first.setItem(CoreHousingBlockEntity.CORE_SLOT, CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));

        CoreHousingBlockEntity second = housing(helper, new BlockPos(3, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CoreHousingMenu menu = new CoreHousingMenu(1, player.getInventory(), second,
                second.getScreenOpeningData(player));

        // Put the duplicate in the player's inventory and shift-click it at the housing.
        int playerSlot = menu.slots.size() - 1;
        menu.slots.get(playerSlot).set(CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));
        menu.clicked(playerSlot, 0, ContainerInput.QUICK_MOVE, player);

        boolean housingEmpty = second.getItem(CoreHousingBlockEntity.CORE_SLOT).isEmpty();
        int coresHeld = countCores(player, menu);

        helper.succeedIf(() -> {
            if (!housingEmpty) {
                throw new AssertionError("a shift-clicked duplicate core ended up socketed");
            }
            if (coresHeld != 1) {
                throw new AssertionError("the player has " + coresHeld + " cores after a refused shift-click, "
                        + "expected exactly 1");
            }
        });
    }

    /** The accepting case through the same path: a free target sockets and runs. */
    @GameTest
    public void clickingAFreeCoreIntoAHousingSockets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:evoker";
        CoreKey key = new CoreKey(CoreFamily.SOUL, Identifier.parse(target));
        ActiveCores.claimPos(level, key).ifPresent(held -> ActiveCores.release(level, key, held));

        CoreHousingBlockEntity housing = housing(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CoreHousingMenu menu = new CoreHousingMenu(1, player.getInventory(), housing,
                housing.getScreenOpeningData(player));

        menu.setCarried(CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));
        menu.clicked(0, 0, ContainerInput.PICKUP, player);

        boolean socketed = !housing.getItem(CoreHousingBlockEntity.CORE_SLOT).isEmpty();
        boolean running = housing.active();

        helper.succeedIf(() -> {
            if (!socketed || !running) {
                throw new AssertionError("a core with a free target did not socket through the menu "
                        + "(socketed=" + socketed + ", running=" + running + ")");
            }
        });
    }

    /**
     * The §4.3 refusal message has to survive being sent to a real client. A GameTest's mock player
     * has no connection, so nothing else in this suite ever serialises it — and a component carrying
     * an argument the network codec rejects throws on the server's network thread, which is a crash
     * no amount of server-side logic testing would catch.
     */
    @GameTest
    public void theRefusalMessageSerialisesToAClient(GameTestHelper helper) {
        Component message = Component.translatable("block.heartstead.core_housing.refused",
                CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:zombie").getHoverName(),
                Component.translatable("block.heartstead.core_housing.refused.at", 1, 2, 3));

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        Exception failure = null;
        try {
            ComponentSerialization.STREAM_CODEC.encode(buf, message);
            ComponentSerialization.STREAM_CODEC.decode(buf);
        } catch (Exception e) {
            failure = e;
        }

        Exception thrown = failure;
        helper.succeedIf(() -> {
            if (thrown != null) {
                throw new AssertionError("the §4.3 refusal message cannot be sent to a client: " + thrown);
            }
        });
    }

    private static CoreHousingBlockEntity housing(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, HsBlocks.SOUL_CAGE);
        return helper.getBlockEntity(relative, CoreHousingBlockEntity.class);
    }

    private static int countCores(ServerPlayer player, CoreHousingMenu menu) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof com.heartstead.item.CoreItem) {
                found += stack.getCount();
            }
        }
        if (menu.getCarried().getItem() instanceof com.heartstead.item.CoreItem) {
            found += menu.getCarried().getCount();
        }
        return found;
    }
}
