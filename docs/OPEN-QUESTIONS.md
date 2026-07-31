# Heartstead — Open questions and reconstruction gaps

Two kinds of entry: **[GAP]** = detail existed in the original design wiki but wasn't recoverable
from the shared conversation. **[OPEN]** = never decided.

Resolve a **[GAP]** by writing the missing spec into [DESIGN.md](DESIGN.md), then delete the entry here.

---

## Blocking — must be resolved before the phase that needs them

### [OPEN] Pack name
Directory is `heartstead`, so this scaffold uses `heartstead:` everywhere. The source conversation
ended mid-naming with an unpicked shortlist: **Wanderhoard** (Claude's pick), **Cairnkeep**,
**Tallyheart**, **Farstead** — all checked as unused by existing Minecraft projects. `Heartstead`
itself was **not** availability-checked. Confirm before anything is published, because the namespace
is baked into every file name and every `custom_data` key.

### [GAP] §3.3 Excavation
Named in the conversation as one of the two riskiest mechanics and scheduled for the week-one spike,
but its spec was in the wiki file only. Needs:
- Block budget per level, and levels available
- Durability cost model (per block? flat?)
- Which block sets it chains across, and whether it respects tool material
- Recursion depth / entity-count safety cap
- Interaction with §3.1 Abundance and §3.2 Kiln Touch (all three on one pickaxe is a real case)

### [GAP] §6 classic farm cores, entries 3–11
Golem Core and Ominous Core survived with attunement and rates. **Barter, Guardian, Tidal, Wither
Skull, Ender, Shulker, Slime, Apiary and Geode cores have names and scope only** — no recipes,
attunement conditions, base rates, or per-player caps. Each needs the same four fields the headline
entries have.

### [GAP] §7.1 Artisan's Table — full spec
Known: T1 block + T2 portable Kit, draws from inventory + Vault, searchable list with ×1/×8/×64,
~280 curated recipes, must be generated not hand-written. **Not known:** the recipes for the Table
and Kit themselves, how the ~280 were curated, the index JSON schema, and the search/paging UX.

### [GAP] §7.2 The Foundry — full spec
Known: core-powered, fuel-free, bulk queue from Vault, 50% gear recycling, a Dye Vat, avoids vanilla
furnace internals. **Not known:** recipe, which core powers it, smelt rate, queue depth, and whether
recycling is 50% of *inputs* or 50% by *material value*.

---

## Non-blocking — decide during the phase

### [OPEN] Heart Shard drop tuning is unvalidated
The ~1 Vital Heart / 30–40 min at T1 target is a design intent, not a measured number. The listed
rates (30% in chests, 0.5% mobs, 10% Evoker/Elder Guardian) have never been played. Expect Phase 0
to end in retuning, and instrument it — log shard acquisitions with timestamps in the dev world.

### [OPEN] Multiplayer scope
Vault is player-bound and Anchor is "one per player" — but nothing specifies shared/team vaults,
whether cores are owned by a player or a world, or how the per-player core cap behaves on a server
with 20 players. This changes the storage schema, so decide before Phase 3.

### [OPEN] Config surface
[CONVENTIONS.md](CONVENTIONS.md) §6 puts config in scoreboards. The design wiki mentions "config
toggles" for harder-mode lives. No decision on whether there's a config *dialog* for operators or
just raw `/scoreboard`.

### [OPEN] Existing-world migration
Anyone adding this to a live world starts with chests full of items and no shards. Is there a
migration/catch-up path, or is it new-worlds-only? Affects marketing more than code, but the answer
shapes Phase 0's loot weighting.

### [OPEN] Where do Sigil *fragments* come from?
Design wiki §7.3 has bounty advancements paying "Sigil fragments", but §1 says Vault Sigils are
"never craftable" and structure-only. Either fragments combine into Sigils (contradicting §1) or they
are a separate currency. Pick one.

### [GAP] Original section numbering
The source conversation referenced the wiki as §8 (classic farms), §10.5 (villager persistence),
§13.1 (heart shard loot), §14 (mod split), §15 (roadmap), §16.2 (core_rate_multiplier). The
reconstruction renumbers these. **Old section references from that conversation will not match
[DESIGN.md](DESIGN.md).** Use the new numbers.
