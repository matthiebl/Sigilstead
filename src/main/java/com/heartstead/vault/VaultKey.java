package com.heartstead.vault;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2.3 — what the Vault actually counts against: an item <em>together with its
 * components</em>, not a bare {@link Item}.
 *
 * <p>The Vault used to key its contents on {@code Item} alone, which meant a half-used Silk Touch
 * pickaxe and a fresh one were the same row, and whatever came back out was built from
 * {@code new ItemStack(item)} — a brand-new, unenchanted tool. That is item destruction, and §2.6
 * says the transfer path must not lose anything. A key is a count-1 copy of the deposited stack, so
 * enchantments, damage, custom names and every other component survive a round trip untouched.
 *
 * <p><b>Variants are free; item types are not.</b> Equality here is item + components, so ten
 * differently-enchanted diamond swords are ten keys and ten cells in the grid. §2.3's
 * <em>distinct type</em> cap, however, is counted per {@link Item} ({@link VaultData}), so those ten
 * swords still spend one of the tier's 27 types and share one per-type depth allowance. The capacity
 * ladder prices what the player thinks of as "kinds of thing"; it would be a strange tax if
 * enchanting a sword cost a Vault slot.
 *
 * <p>Immutable by construction: the prototype is copied in and copied out, so nothing outside can
 * mutate a live map key and corrupt the hash.
 */
public final class VaultKey {

    public static final Codec<VaultKey> CODEC = ItemStack.CODEC.xmap(VaultKey::of, VaultKey::stack);

    /** S2C. The server is the authority on what is in the Vault, so the trusted codec is right here. */
    public static final StreamCodec<RegistryFriendlyByteBuf, VaultKey> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(VaultKey::of, VaultKey::stack);

    /**
     * C2S. A withdraw intent names a key the client read off its own snapshot, so the bytes are
     * player-controlled and go through vanilla's untrusted codec. The server looks the result up in
     * the Vault and does nothing at all if it isn't there — a forged key can only name something
     * that isn't stored.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VaultKey> UNTRUSTED_STREAM_CODEC =
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.map(VaultKey::of, VaultKey::stack);

    private final ItemStack prototype;
    private final int hash;

    private VaultKey(ItemStack prototype) {
        this.prototype = prototype;
        this.hash = ItemStack.hashItemAndComponents(prototype);
    }

    /** The key {@code stack} stores under. Count is discarded — that's what the map value is for. */
    public static VaultKey of(ItemStack stack) {
        return new VaultKey(stack.copyWithCount(1));
    }

    public static VaultKey of(Item item) {
        return new VaultKey(new ItemStack(item));
    }

    /** A fresh count-1 stack of this variant. Safe to mutate; the key keeps its own copy. */
    public ItemStack stack() {
        return prototype.copy();
    }

    /** A fresh stack of this variant with {@code count} in it, for handing back on withdrawal. */
    public ItemStack stack(int count) {
        return prototype.copyWithCount(count);
    }

    public Item item() {
        return prototype.getItem();
    }

    /** This variant's own max stack size — components (a stack-size override, say) can change it. */
    public int maxStackSize() {
        return prototype.getMaxStackSize();
    }

    /** True when {@code stack} would go into this key's row. */
    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(prototype, stack);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof VaultKey key
                        && hash == key.hash
                        && ItemStack.isSameItemSameComponents(prototype, key.prototype));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "VaultKey[" + prototype + "]";
    }
}
