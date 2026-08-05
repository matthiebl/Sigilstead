# Heartstead — Claude Code prompts

Ready-to-paste prompts, in build order. Rules that apply to all of them:

- **Run Claude Code from the repo root.**
- **Reference wiki sections, don't re-describe features.** "Implement §4.2 Soul Cage per the wiki"
  keeps drift down; re-explaining invites a second, subtly different design.
- **One phase per session.** Never ask for the whole mod.
- **Read the real API before writing against it.** 26.x ships unobfuscated, so the jar has true
  names — `javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar <class>`, or
  `./gradlew genSources` for full bodies. Every tutorial predating 26.1 is wrong about names
  (`ResourceLocation` is now `Identifier`) and usually about shapes too. See REFERENCES.md.
- **Claude Code can verify its own work now** (`build`, `test`, `runGametest`). Expect it to. What it
  still can't judge is feel — balance and UI pleasantness need `runClient` and you.

Phase numbers match [DESIGN.md](DESIGN.md) §9.

## Status

| Phase | Status |
|---|---|
| 0.1 — Registration foundation | ✅ Done — redone against v0.4 by 2.5a |
| 0.2 — Loot tables | ✅ Done — redone against v0.4 by 2.5b |
| 0.5a — Villager trade persistence | ✅ Done |
| 1 — Lives | ✅ Done — item renamed to Heart Sigil by 2.5a |
| 2 — The Vault (storage) | ✅ Done — **reworked in v0.4** |
| 2 — The Vault (screen) | ✅ Done — **reworked in v0.4** |
| 2.5a — Rename the spine | ✅ Done |
| 2.5b — Redo the drop tables | ✅ Done |
| 2.5c — Rework the Vault | ✅ Done |
| 3 — Codex | ⬜ Not started |
| 3 — Artisan's Table | ⬜ Not started |
| 4 — Cores | ⬜ Not started |
| 4 — Classic farm replacements | ⬜ Not started |

**Up next: Phase 3 — Codex and Artisan's Table.**

DESIGN.md moved to v0.4 after Phases 0–2 shipped. The spine went from two items (Heart Shard →
Vital Heart) to one found Sigil with three children, and the Vault gained activation, reach tiers and
container rules. All three tasks have landed: the §1 spine, the §12.1 drop table, the §12.2 recipes
and now §2 in full — activation, the deposit/withdraw split, reach as world state, the container
rules and the two-tab screen. Phase 3 can build on the transfer path without writing it twice.

**One thing 2.5c did not build: the §2.2 Bundle override** (replacing the vanilla Bundle with a
`bundle_slots`-wide UI inventory). It is a separate item feature, not one of 2.5c's specced
behaviour changes, and only the *nesting half* of §2.5 was in scope — Satchels and Pouches refuse to
fit inside container items, which vanilla already enforces for bundles and shulker boxes. The
override is unclaimed by any phase; it needs one.

Not yet built: the Codex, Tome, Artisan's Table and Kit (Phase 3), and every §12.4 / §12.5 core prime
and housing recipe (Phase 4). Their result items do not exist yet.

---

## Phase 0 — Items and the heart economy

### 0.1 — Registration foundation ✅

```
Read docs/DESIGN.md §1 and docs/CONVENTIONS.md §2.

Show me the real 26.2 Item.Properties construction signature before writing any
registration code — read it out of the jar with javap (26.x is unobfuscated) or via
./gradlew genSources. I want the API shape confirmed, not inferred from a tutorial.

Then implement HsComponents and HsItems with the three §1 items: heart_shard,
vital_heart, vault_sigil. Real registered items, creative tab entry, lang keys, item
models, and the two placeholder textures already in
src/main/resources/assets/heartstead/textures/item/.

Also add the Vital Heart crafting recipe as JSON in data/heartstead/recipe/.

Run ./gradlew build when done and report the actual result.
```

> **Superseded by v0.4.** The spine is no longer heart_shard → vital_heart. It is one found `sigil`
> plus `core_sigil`, `heart_sigil`, `vault_sigil` and the three dimensional variants (§1, §12.2).
> Phase 2.5 does the rename; this prompt is kept only so the git history reads.

### 0.2 — Loot tables ✅

```
Add Heart Shard to loot per docs/DESIGN.md §1: dungeon, mineshaft, temple and shipwreck
chests at 30% for 1-2; trial vaults; fishing treasure; 0.5% from any hostile mob; 10%
from Evoker and Elder Guardian.

26.2's entity predicate format is a component-style map that REJECTS unknown
sub-predicates, so 1.21.x examples are wrong. Show me one predicate and confirm it
before generating the rest.

Consider whether a Fabric loot-table-modification callback is a better fit than
overriding vanilla loot table JSON outright — overriding conflicts with every other mod
that touches the same table. Recommend one and say why.
```

> **Superseded by v0.4.** The whole drop table changed and moved to §12.1 — different item, roughly
> quarter rates on chests and mobs, new boss and mini-boss entries north of 50%, and two hard
> exclusions (nothing killed by a core, and never the Wither). Phase 2.5 redoes it.

Phase 0 ends there. **There is no advancement or bounty work in this phase** — read DESIGN.md §7.3
before proposing any. Advancements are deferred until the §1 economy has actually been played.

---

## Phase 0.5 — Risk spike

Still the riskiest mechanic. It ships with a GameTest instead of a manual checklist.

### 0.5a — Villager trade persistence ✅

```
Build a proof of concept for docs/DESIGN.md §7.2 ONLY.

Inject a fixed trade onto a librarian and make it survive the villager regenerating
Offers on level-up and restock. Then write GameTests asserting it survives: level-up,
restock, chunk unload/reload, and world reload.

Run ./gradlew runGametest and report real output. If the tests can't cover world reload,
say so and give me a manual checklist for that case only.
```

---

## Phase 1 — Lives ✅

```
Implement docs/DESIGN.md §6: Life Heart item and recipe, max_health attribute modifier
on consume, the 20-heart cap, -1 heart on death with a floor of 5, and the harder-mode
config toggles.

Player state uses attachments per CONVENTIONS.md §2.2, with a versioned codec. Every
number in §6 is a config field per §5 of CONVENTIONS.md, not a literal.

GameTest the floor: die repeatedly, assert health never goes below it.

Note DESIGN.md §1.1 — Blank Core must stay cheaper than Life Heart. Don't change either
recipe without flagging it.
```

> **Partly superseded by v0.4.** The mechanic is unchanged and still shipped; the item is now the
> **Heart Sigil** and its numbers moved to §12.6. Phase 2.5a does the rename. The §1.1 guardrail
> survives verbatim, with Core Sigil in Blank Core's place.

---

## Phase 2 — The Vault ✅

The biggest lift, and the first real blocks and screen handlers in the project.

```
Write the GameTest suite for docs/DESIGN.md §2.5 FIRST, before any Vault implementation:
deposit N / withdraw N exact conservation, full inventory on withdraw, capacity boundary,
mid-transfer shutdown, and concurrent access from two players. They should fail for the
right reason (no implementation) when you run them.

Then implement the storage layer per §2.1-2.3 with a versioned codec, and make the tests
pass. No UI in this task.

The Vault is WORLD state, not player state — codec-backed SavedData per CONVENTIONS.md
§4, one Anchor per world, shared contents and pooled Sigil capacity. Concurrent access is
a first-class case here, not an edge case. Access-item range (§2.2) is the only per-player
part.
```

Then, separately:

```
Now the Vault screen per §2.4 — a real ScreenHandler with a scrollable slot grid, live
search, click-to-withdraw, shift-click-to-deposit and sort.

Screen classes go in src/client per CONVENTIONS.md §3; the screen handler itself is
common. Watch the client/server split — a leak here crashes dedicated servers.

The client needs a synced snapshot of Vault contents. Phase 3's Artisan needs the same
thing (REFERENCES.md) — build ONE sync path and design it with that second consumer in
mind.

All mutation is server-side; the screen sends intents, never results.

This is the first block + block entity + screen handler in the project. Establish those
patterns carefully — the Codex and the cores will copy them.
```

---

## Phase 2.5 — Economy retrofit ⬜

Phases 0–2 shipped against spec v0.3. DESIGN.md v0.4 unified the currency and reworked the Vault.
This phase closes the gap. **Do it before Phase 3** — the Codex and the Artisan both build on the
Vault's transfer path and on §1's item ids, and doing them first means doing them twice.

Three tasks, in order. Don't merge them; the first is a rename and the second changes behaviour, and
mixing those makes the diff unreviewable.

### 2.5a — Rename the spine

```
Read docs/DESIGN.md §1 and §12.2, then retire the v0.3 spine.

heart_shard and vital_heart are GONE, replaced by one found item `sigil`. life_heart
becomes `heart_sigil`; the not-yet-built Blank Core is now `core_sigil`. Add `vault_sigil`
plus `overworld_vault_sigil`, `nether_vault_sigil` and `end_vault_sigil`.

Rename the Java too — HsItems fields, LifeHeartItem, HeartShardLoot, and the lives
package's references. Registry ids, lang keys, models and textures all move with them.
This is a pre-release mod with no players, so there is NO migration path and no legacy id
aliasing: delete the old ids outright.

Then write the §12.2 recipes as JSON. The §1.1 guardrail is a hard constraint — Core Sigil
must stay the cheapest of the three surcharges. Say so if any recipe you write breaks it.

./gradlew build and report the real result.
```

### 2.5b — Redo the drop tables

```
Replace the v0.3 Heart Shard loot with the §12.1 Sigil table. Every chest, mob and boss
entry, including the new ones (Ravager, Warden, Ender Dragon first-kill vs respawn).

TWO EXCLUSIONS, and they are the reason the economy doesn't bootstrap — read §4.2 and
§12.1 before writing either:

1. No mob killed by a core ever drops a Sigil. Cores don't exist until Phase 4, so build
   the hook now and leave it obviously named, or Phase 4 will not remember to add it.
2. The Wither never drops a Sigil, even on a player kill. The §5 Wither Skull Core makes
   skulls passively, so skulls -> Wither -> Sigils is the loop in disguise.

Keep the Fabric loot-table-modification callback approach from 0.2 rather than overriding
vanilla JSON.

GameTest exclusion 2 at minimum: kill a Wither, assert zero Sigils.
```

### 2.5c — Rework the Vault

```
Read docs/DESIGN.md §2 in full, then bring the Phase 2 Vault up to v0.4.

Behaviour changes, all specced:

- §2.1 activation. First Anchor a world ever has activates free; every activation after
  costs a Vault Sigil; breaking an activated Anchor loses the activation but NEVER the
  capacity or reach already bought. The anti-softlock fallback (a Vault Sigil sitting in
  the Vault can pay) is part of this, and a held Sigil is always preferred.
- §2.1 block hardening: PushReaction.BLOCK, explosion resistance, and the dragon_immune
  and wither_immune tags.
- §2.0 the deposit/withdraw split. Deposit is unrestricted from anywhere. Withdrawal needs
  the §12.3 reach tier covering where the player is standing. This is ONE rule and it
  applies to the Satchel, the Pouch and the Linked Funnel's output mode alike.
- §2.3 reach tiers as world state alongside capacity, bought with the dimensional Sigils.
- §2.5 the container rules, both directions: the Vault rejects any item with a non-empty
  container or bundle_contents component, and the Bundle override refuses to nest.
- §2.4 the two-tab Anchor screen. Tab 1 (activation and upgrades) is always available
  including when dormant; tab 2 (storage) is locked until activated.

Every number comes from §12.3 and §12.7 as config, not literals.

The §2.6 conservation suite still has to pass unchanged — if a test needs editing to go
green, that is a finding to report, not a test to edit. Add cases for: activation across a
world reload, a withdrawal attempted from a dimension the world hasn't bought, and deposit
while the Anchor's chunk is unloaded.

./gradlew build && ./gradlew runGametest, and report real output.
```

---

## Phase 3 — Codex and Artisan's Table ⬜

```
Implement docs/DESIGN.md §3.3 — the Codex block and block entity, Archive / Tome / Teach
flows, tier capacities, and the fixed librarian prices.

Reuse the Phase 0.5a persistence approach for the librarian side; read that code first
rather than reinventing it.

The archive is PER PLAYER (a codec-backed attachment), not per block — any Codex shows
you your own archive. The capacity upgrades are consumed per player too.
```

Then, separately:

```
Implement docs/DESIGN.md §7.1 — Artisan's Table and Kit.

Read REFERENCES.md "the recipe book is reusable" first. Do NOT build a recipe browser:
extend the vanilla crafting menu and return RecipeBookType.CRAFTING, which inherits
search, category tabs, craftable-only and click-to-fill off the live RecipeManager.

The only new work is the two Vault hooks in that REFERENCES section. Route every
withdrawal through the Phase 2 transfer code — a second item-moving path is how the
Vault gets a dupe bug, and it would invalidate the §2.5 suite.
```

---

## Phase 4 — Cores ✅ (§4.1–4.3; the §5 eleven are still open)

```
Implement docs/DESIGN.md §4: the prime -> imprint attunement in §4.1, the four housing
blocks in §4.2, and rate tiering in §4.3. The Core Sigil itself already exists from
Phase 2.5a — don't re-add it. Every recipe, threshold and rate comes from §12.4.

§4.2's no-Sigils-from-cores rule is absolute and is the reason the economy doesn't
bootstrap. Phase 2.5b left a hook for it; wire every core loot table through that hook and
GameTest it — socket a core, run it, assert zero Sigils ever appear in its output.

Attunement state is a data component on the stack per §4.1. Get the hand rules exactly
right: an un-attuned core only takes a target from the main hand or offhand, but once it
HAS a target every carried core progresses in parallel from anywhere in the inventory.

Offline accrual per §4.2: settle the whole elapsed delta from ONE stored timestamp on
chunk load, capped at core_accrual_cap_hours. Never require a chunkloader. GameTest it —
advance the world clock, unload and reload the chunk, assert the yield matches the
formula and doesn't double-count. That's the bug this design invites.

The one-active-core-per-target rule in §4.3 is world-level SavedData and is enforced by
REFUSING the socket, not by placing something dormant. GameTest the duplicate-socket path
including across a world reload.

Vault integration from §4.2 is the payoff — build it in this phase.

core_rate_multiplier ships at 1.0 but is a config field; I expect to lower it.
```

Then, separately:

```
Implement the eleven classic farm replacements in docs/DESIGN.md §5, with every recipe,
imprint and rate taken from §12.5.

These need NO new blocks — every one sockets into a §4.2 housing. The housing supplies
the family, the core supplies the rate and the loot table. It should come out as eleven
recipes and eleven loot tables plus the imprint conditions, and almost no new Java.

Note the two imprint shapes in §5 (milestone vs counted) and which cores use which.

Before implementing, check my tier-I and tier-III numbers against the comparison table at
the end of §12.5 and tell me if any are wrong — those rates have never been played.
```

---

## Utility prompts

### Update the wiki after a decision

```
I changed <X> during implementation. Update docs/DESIGN.md to match, and remove the
matching entry from docs/OPEN-QUESTIONS.md if it resolved one. Don't touch anything that
didn't actually change.
```

### Version bump

```
26.3 released. Check meta.fabricmc.net and piston-meta for the new Loader / Fabric API /
Loom / Java versions, update gradle.properties and docs/REFERENCES.md, then run
./gradlew build and report what actually broke.

Don't trust mod-listing sites for version numbers — one of them was wrong about Fabric
Loader during the initial setup. Query the meta APIs directly.
```

### Before publishing

```
Audit the repo for anything still using a placeholder: TODO markers, placeholder
textures, hardcoded values that should be config, and any file referencing a name other
than heartstead. List them; don't fix them yet.
```
