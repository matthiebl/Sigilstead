# Heartstead — Open questions

**[OPEN]** = not yet decided. Resolve one by writing the spec into [DESIGN.md](DESIGN.md), then
delete the entry here.

A design pass on 2026-08-01 closed most of this file. What's left is genuinely open, not merely
unwritten.

---

## Blocking — must be resolved before the phase that needs them

*(none)*

---

## Non-blocking — decide during the phase

### [OPEN] Heart Shard drop tuning is unvalidated
The ~1 Vital Heart / 30–40 min at T1 target is a design intent, not a measured number. The listed
rates (30% in chests, 0.5% mobs, 10% Evoker/Elder Guardian) have never been played. **This one cannot
be closed at a desk** — it needs playtime. Expect Phase 0 to end in retuning, and instrument it: log
shard acquisitions with timestamps in the dev world.

The same caveat applies to every rate in [DESIGN.md](DESIGN.md) §5 and the attunement thresholds in
§4.1. They are internally consistent and reasoned; none of them has been played.

### [OPEN] Config surface
[CONVENTIONS.md](CONVENTIONS.md) §5 puts config in a codec-backed JSON file loaded on server start.
Undecided: whether operators get an in-game config screen, a command, or just the file. **The file
alone is the v1 answer** unless playtesting makes editing it painful — recorded here rather than
resolved because it costs nothing to defer.

### [OPEN] Bundle slot count
[DESIGN.md](DESIGN.md) §2.2 overrides the vanilla Bundle to a 9-slot UI inventory — ~9× vanilla
capacity at T1 for vanilla cost, and the only place the mod rewrites a vanilla item (pillar 6).
**Decided 2026-08-01: keep 9 and ship it as a config field** (`bundle_slots`). Flagged here because
it is the largest unvalidated buff in the pack and the first thing to lower if the early game feels
weightless.

---

## Resolved

Kept briefly so that older notes and prompts make sense. Delete once nothing references them.

### ~~[RESOLVED 2026-08-01] Where do Sigil fragments come from?~~
There are no fragments, and no advancement pays one either. Vault Sigils come only from Ancient City,
End City, Bastion and ominous vaults (§1) — you travel for them. Ominous vaults are repeatable, so
supply isn't hard-capped.

### ~~[RESOLVED 2026-08-01] Bounty advancements~~
**Cut**, along with the reward crates and the capstone Sigils. Reasoning is preserved in
[DESIGN.md](DESIGN.md) §7.3: bounties were a second reward channel paying for the same activities §1
already pays for, which makes the core economy impossible to tune honestly. Advancements return later
as **recognition, not currency**, designed once the systems they describe have stopped moving. If
exploring/fighting/building feels under-rewarded, that is a §1 drop-rate problem first.

### ~~[RESOLVED 2026-08-01] §5 classic farm cores, entries 3–11~~
All eleven entries now have tier, housing, prime recipe, imprint condition and base rate in
[DESIGN.md](DESIGN.md) §5, plus a table comparing tier I and tier III against real technical farms.
They reuse the four §4.2 housings — no new blocks.

### ~~[RESOLVED 2026-08-01] §7.1 Artisan's Table — full spec~~
Recipes written into §7.1. The filtering problem dissolved: extending the vanilla crafting menu
inherits the whole recipe book (search, tabs, craftable-only, click-to-fill) off the live
`RecipeManager`. Verified class shapes are in [REFERENCES.md](REFERENCES.md). Only the two Vault
hooks are new work.

### ~~[RESOLVED 2026-08-01] The Foundry~~
**Cut.** Smelting stays vanilla. Removed from §7, the build order and PROMPTS.md.

### ~~[RESOLVED 2026-08-01] Multiplayer scope~~
The Vault (§2) and the active-core registry (§4.3) are **world** state — one Anchor per world, shared
contents, pooled Sigils, one active core per target for everyone. The Codex archive (§3.3) stays
**per player**. What each player owns individually is their access item's reach (§2.2).

### ~~[RESOLVED 2026-08-01] Per-player core cap~~
Replaced by **one active core per target, per world**, refused at the point of socketing (§4.3).
Tiering is the scaling axis, not core count.

### ~~[RESOLVED 2026-08-01] Existing-world migration~~
**No catch-up path.** The mod works in an existing world, but the economy starts at zero — you
explore for shards like anyone else. Nothing to build and nothing to balance; say so in the README.

### ~~[RESOLVED 2026-08-01] How far to take the UI~~
Settled per feature in DESIGN.md rather than as a global ceiling: the Vault is a slot grid with
search and sort and explicitly **not** a logistics network (§2.4); the Artisan reuses vanilla's
recipe book rather than inventing a browser (§7.1); core attunement is a tooltip and nothing else
(§4.1). The pitch is casual convenience, not an ME terminal.

### ~~[RESOLVED 2026-08-01] Realms support~~
**No Realms.** Realms takes data packs, not mods, and the block registration that forced the mod
decision isn't reversible. Accepted cost, not an open trade-off.

### ~~[RESOLVED 2026-08-01] Pack name~~
**Heartstead**, committed. `heartstead` namespace, `com.heartstead` package. Availability against
existing Minecraft projects was never formally checked — worth a look before publishing, but not a
blocker on anything being built.

### ~~[RESOLVED 2026-08-01] Excavation~~
**Cut** when the enchantment was trimmed out of §3. Chain-break/vein-mine is not in the pack. (It was
§3.3 under the old numbering; §3.3 is now the Codex.)

### ~~[RESOLVED 2026-07-31] Recipe ingredients can't check custom_data~~
Resolved by becoming a Fabric mod. Items are real registered ids, so an ingredient of
`heartstead:heart_shard` matches only a Heart Shard. **Do not reintroduce a base-item table.**
