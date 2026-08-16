package com.heartstead.core;

import com.heartstead.advancement.HsTriggers;
import com.heartstead.blockentity.CoreHousingBlockEntity;
import com.heartstead.item.CoreItem;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsMenuTypes;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §4.2 — the housing screen handler. "Socketing is controlled by placing the locked core
 * within the Housing's GUI screen", so the core slot is a real slot and everything a click can move
 * is slot-backed, the same rule {@code VaultMenu} and {@code CodexMenu} follow.
 *
 * <h2>How §4.3's refusal works, and why it is not a bounce</h2>
 *
 * §4.3: "the socket is <b>refused at the point of insertion</b>, with the reason shown to the
 * player." Both halves are handled here, and neither of them moves an item:
 *
 * <ul>
 *   <li>{@link CoreSlot#mayPlace} returns false for a duplicate, so the core never enters the slot —
 *       on the server against live {@link ActiveCores} state, on the client against the snapshot in
 *       {@link CoreHousingOpenData} so it does not even predict the placement.</li>
 *   <li>{@link #clicked} explains why, before delegating, so a refusal is never silent.</li>
 * </ul>
 *
 * <p>The earlier shape of this — let the core land, notice the claim failed, hand it back — was
 * wrong twice over. It mutated a container from inside {@code Slot#setChanged()}, which runs in the
 * middle of vanilla's click handling; and under {@code QUICK_MOVE}, which retries while it is making
 * progress, handing the core back is progress, so the two sides could trade the same core forever.
 * Refusing before anything moves removes the whole class of problem rather than the one symptom.
 *
 * <p>{@link #STATUS} and {@link #XP} are a two-int {@link ContainerData} rather than a payload: they
 * are small numbers the screen prints as text, and vanilla already syncs container data every tick.
 * Collecting the XP, unlike reading it, is a real action and goes over
 * {@link com.heartstead.network.CoreHousingCollectXpPayload} instead, the same way the Vault's
 * activate button is a payload while its state is a sync payload.
 */
public final class CoreHousingMenu extends AbstractContainerMenu {

    /** The status int the screen renders. */
    public static final int STATUS = 0;

    /** The floored, not-yet-collected experience the screen renders and the Collect button spends. */
    public static final int XP = 1;

    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_REFUSED = 2;

    private final Container container;
    private final CoreHousingBlockEntity housing;
    private final CoreHousingOpenData openData;
    private final ContainerData status;

    /** Whoever opened this menu. Only DESIGN.md §7.3's criteria read it; nothing branches on it. */
    private final Player owner;


    /** Client side: no block entity, and a stand-in container the server's slot updates write into. */
    public CoreHousingMenu(int containerId, Inventory playerInventory, CoreHousingOpenData openData) {
        this(containerId, playerInventory, new SimpleContainer(1 + openData.bufferSlots()), null, openData,
                new SimpleContainerData(2));
    }

    /** Server side, backed by the real housing. */
    public CoreHousingMenu(int containerId, Inventory playerInventory, CoreHousingBlockEntity housing,
                           CoreHousingOpenData openData) {
        this(containerId, playerInventory, housing, housing, openData, statusOf(housing));
    }

    private CoreHousingMenu(int containerId, Inventory playerInventory, Container container,
                            CoreHousingBlockEntity housing, CoreHousingOpenData openData, ContainerData status) {
        super(HsMenuTypes.CORE_HOUSING, containerId);
        this.container = container;
        this.housing = housing;
        this.openData = openData;
        this.status = status;
        this.owner = playerInventory.player;

        addSlot(new CoreSlot());
        for (int index = 0; index < openData.bufferSlots(); index++) {
            addSlot(new BufferSlot(CoreHousingBlockEntity.FIRST_BUFFER_SLOT + index,
                    CoreHousingLayout.bufferSlotX(index), CoreHousingLayout.bufferSlotY(index)));
        }
        addStandardInventorySlots(playerInventory, CoreHousingLayout.PLAYER_INV_X,
                CoreHousingLayout.playerInvY(openData.bufferSlots()));
        addDataSlots(status);
    }

    private static ContainerData statusOf(CoreHousingBlockEntity housing) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == XP) {
                    return (int) Math.floor(housing.storedExperience());
                }
                if (housing.refused()) {
                    return STATUS_REFUSED;
                }
                return housing.active() ? STATUS_RUNNING : STATUS_EMPTY;
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative: the client never writes status or XP back (CONVENTIONS.md §5).
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public CoreFamily family() {
        return openData.family();
    }

    /** How many §4.2 buffer slots this housing has — the screen lays itself out around it. */
    public int bufferSlots() {
        return openData.bufferSlots();
    }

    /** What the screen prints: empty, running, or §4.3-refused. */
    public int status() {
        return status.get(STATUS);
    }

    /** The floored, not-yet-collected experience the screen's counter and Collect button read. */
    public int xp() {
        return status.get(XP);
    }

    /** The housing screen's Collect button. A no-op for the client-side stand-in menu. */
    public void handleCollectXp(ServerPlayer player) {
        if (housing != null) {
            housing.collectExperience(player);
        }
    }

    private int firstPlayerSlot() {
        return 1 + openData.bufferSlots();
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < firstPlayerSlot()) {
            if (!moveItemStackTo(original, firstPlayerSlot(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the core slot only — the buffer is output, and shift-clicking into it would let a
            // player use a housing as a chest.
            if (!moveItemStackTo(original, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        slot.setChanged();
        return copy;
    }

    /**
     * §4.2 — takes a finished, target-locked core of this housing's family, and only if §4.3 allows
     * it. Family and lock are readable from the stack itself; the duplicate check needs world state,
     * which the server reads live and the client reads from its opening-data snapshot.
     */
    private final class CoreSlot extends Slot {
        CoreSlot() {
            super(CoreHousingMenu.this.container, CoreHousingBlockEntity.CORE_SLOT,
                    CoreHousingLayout.CORE_SLOT_X, CoreHousingLayout.CORE_SLOT_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isSocketableHere(stack) && !isDuplicate(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        /**
         * DESIGN.md §7.3 — the socket criterion. {@link #mayPlace} has already applied every §4.2 and
         * §4.3 rule by the time this runs, so reaching here <em>is</em> "the housing accepted it";
         * there is no second acceptance check to keep in sync.
         */
        @Override
        public void setByPlayer(ItemStack stack, ItemStack old) {
            super.setByPlayer(stack, old);
            if (old.isEmpty()
                    && stack.getItem() instanceof CoreItem core
                    && owner instanceof ServerPlayer serverPlayer) {
                CoreAttunement attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
                if (attunement != null) {
                    HsTriggers.CORE_SOCKETED.trigger(serverPlayer, core.family(), attunement.tier());
                }
            }
        }
    }

    /** A finished, target-locked core of this housing's family — everything but the §4.3 check. */
    private boolean isSocketableHere(ItemStack stack) {
        return stack.getItem() instanceof CoreItem core
                && core.family() == openData.family()
                && Optional.ofNullable(stack.get(HsComponents.CORE_ATTUNEMENT))
                        .map(attunement -> attunement.target().isPresent())
                        .orElse(false);
    }

    /**
     * §4.3 — is this exact family + target already running somewhere else?
     *
     * <p>The server answers from {@link ActiveCores} directly, because it is the authority and its
     * answer can have changed since the screen opened. The client answers from the snapshot it was
     * sent, which is enough to stop it predicting a placement that will be refused.
     */
    private boolean isDuplicate(ItemStack stack) {
        CoreAttunement attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
        if (attunement == null) {
            return false;
        }
        Optional<CoreKey> key = attunement.key();
        if (key.isEmpty()) {
            return false;
        }
        if (housing != null && housing.getLevel() instanceof ServerLevel serverLevel) {
            return ActiveCores.claimedElsewhere(serverLevel, key.get(), housing.getBlockPos());
        }
        return openData.claimedTargets().contains(key.get().target());
    }

    /**
     * §4.3's other half — "with the reason shown to the player". {@link CoreSlot#mayPlace} has
     * already made the click a no-op by this point; this is only what makes the no-op explicable
     * rather than a slot that mysteriously will not accept a core.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (player instanceof ServerPlayer serverPlayer && housing != null) {
            ItemStack offered = offeredCore(slotId, input);
            if (!offered.isEmpty() && isSocketableHere(offered) && isDuplicate(offered)) {
                explainRefusal(serverPlayer, offered);
            }
        }
        super.clicked(slotId, button, input, player);
    }

    /** The core this click is trying to socket, whether carried onto the slot or shift-clicked at it. */
    private ItemStack offeredCore(int slotId, ContainerInput input) {
        if (input == ContainerInput.QUICK_MOVE) {
            return slotId >= firstPlayerSlot() && slotId < slots.size()
                    ? slots.get(slotId).getItem()
                    : ItemStack.EMPTY;
        }
        return slotId == CoreHousingBlockEntity.CORE_SLOT ? getCarried() : ItemStack.EMPTY;
    }

    private void explainRefusal(ServerPlayer player, ItemStack core) {
        CoreKey key = core.get(HsComponents.CORE_ATTUNEMENT).key().orElseThrow();
        Component where = ActiveCores.claimPos((ServerLevel) housing.getLevel(), key)
                .map(pos -> Component.translatable("block.heartstead.core_housing.refused.at",
                        pos.getX(), pos.getY(), pos.getZ()))
                .orElseGet(() -> Component.translatable("block.heartstead.core_housing.refused.elsewhere"));
        player.sendSystemMessage(
                Component.translatable("block.heartstead.core_housing.refused", core.getHoverName(), where), true);
    }

    /** Output only. §4.2's buffer is somewhere yield lands, not somewhere a player stores things. */
    private final class BufferSlot extends Slot {
        BufferSlot(int slot, int x, int y) {
            super(CoreHousingMenu.this.container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        /** DESIGN.md §7.3 — a core stops being a promise the first time yield leaves the buffer. */
        @Override
        public void onTake(Player taker, ItemStack stack) {
            super.onTake(taker, stack);
            if (!stack.isEmpty() && taker instanceof ServerPlayer serverPlayer) {
                HsTriggers.CORE_YIELD_COLLECTED.trigger(serverPlayer);
            }
        }
    }

}
