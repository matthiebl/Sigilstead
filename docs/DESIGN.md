# Heartstead — Design Wiki

> **Status:** reconstructed spec, v0.2 · **Target:** Minecraft Java 26.2 · **Fabric mod**, Java 25 · see §9
>
> This document is the **ground truth** for the pack. Implementation work should reference sections
> by number (e.g. "implement §6.2 Golem Core") rather than re-describing features. If reality and
> this document disagree, fix this document in the same change.

---

## 0. Design pillars

The pack exists to delete *technical Minecraft as a prerequisite* without deleting Minecraft.

1. **Reward the fun loop.** Exploring, fighting and building should pay. Grinding, AFKing and
   rectangle-farm-building should not be *required* to play efficiently.
2. **One currency, three sinks.** Everything hangs off a single found item family (§1). Storage,
   farms and extra lives all draw on the same exploration reward, so exploring is never wasted.
3. **Convenience is earned, not free.** Early game is already easier than vanilla; the *scale* of
   convenience unlocks through structures you have to travel to.
4. **Never punish a struggling player twice.** Floors, not spirals. See §5.
5. **Leave technical play intact.** Cores match a *small hand-built farm*, not a mega-farm. Players
   who enjoy building the real thing still get more out of it — they just aren't forced into it.

### 0.1 The hard wall — **LIFTED** (2026-07-31)

This section used to describe the data pack UI ceiling: `/dialog` menus have no draggable item slots,
no way to force-open a container GUI, and no way to detect an inventory click — so the Vault and the
Artisan's Table had to be searchable lists with buttons rather than grids.

**The project is now a Fabric mod (§8), and none of that applies.** Real `ScreenHandler` screens with
real slots, drag-and-drop, scroll, and live search are all available.

Several decisions in this document were **concessions to that ceiling** and are now reversible. Each
is flagged inline, but the three that matter:

| Was constrained to | Can now be |
|---|---|
| §2.4 Vault as a scrollable text list with withdraw 1/16/64 buttons | A real slot grid with search and drag-out |
| §7.1 Artisan's Table as a searchable recipe list, "no datapack can give you a grid" | A real 3×3 grid backed by inventory **and** Vault |
| §7.1 a shipped, pre-generated ~280-recipe index, because functions can't read the recipe registry at runtime | Read the live recipe manager directly — **the index and its generator are unnecessary** |

That last one deletes a whole build-time dependency and the "curate 280 recipes" problem with it.

**The real constraint now is scope, not capability.** Don't rebuild these as maximal UIs just because
you can; the pack's pitch is casual convenience, not an ME terminal.

---

## 1. The progression spine

The most important decision in the pack. One item family feeds every system.

| Item | Source | Notes |
|---|---|---|
| **Heart Shard** | Dungeon / mineshaft / temple / shipwreck chests (30%, 1–2), trial vaults, fishing treasure, 0.5% from any hostile mob, 10% from Evoker / Elder Guardian | The base currency |
| **Vital Heart** | 4 Shards + Golden Apple | The universal core |
| **Vault Sigil** | Ancient City, End City, Bastion, ominous vaults, or a capstone bounty advancement (§7.3) — never craftable | Storage capacity only |

```
 S        S = Heart Shard
SAS       A = Golden Apple
 S        → Vital Heart
```

**Target acquisition rate:** ~1 Vital Heart per 30–40 min of active exploring at T1, ~1 per 15 min
at T2. Tune the shard drop tables to hit that — it is the single biggest dial in the pack (§14).

### 1.1 The central tension

Vital Hearts feed **both** farm cores and extra lives. Spending three hearts on an iron core means
three fewer hearts of health buffer. That choice is the best mechanic in this design.

**Guardrail:** a death must never cost you your first core. Blank Core (§4) is deliberately *cheaper*
than Life Heart (§5) — 4 iron + 8 gold vs 17 gold — so a new player builds their first farm core
before their first extra heart. The pack helps you before it charges you.

### 1.2 Tier definitions (used throughout)

| Tier | Gate | Player state |
|---|---|---|
| **T1** | Pre-Nether → early Nether | Iron, copper, first villager, first dungeon |
| **T2** | Nether established | Diamonds, fortress/bastion, trial chambers, monument, first raid |
| **T3** | End access | Ancient city, End city, netherite, elytra |

---

## 2. Storage — The Vault

A player-bound virtual inventory, persisted via a codec-backed attachment (CONVENTIONS.md §4).
Storage is not backed by real containers, so there is no dupe risk from container NBT juggling.

### 2.1 Input side (blocks)

```
 E        E = Ender Pearl        │  Linked Barrel (T1)
CBC       C = Copper Ingot       │  Barrel + 2 Pearl + 2 Copper
 E        B = Barrel             │  Vacuums contents into your Vault
```

| Block | Tier | Recipe | Role |
|---|---|---|---|
| **Linked Barrel** | T1 | Barrel + 2 Ender Pearl + 2 Copper Ingot | Vacuums its contents into your Vault |
| **Vault Anchor** | T1 | Linked Barrel + Amethyst Shard | Creates your Vault; where Sigils are consumed. One per player |
| **Linked Chest** | T2 | Chest + Amethyst Block + 2 Copper Block | Higher throughput; accepts from Cages / Quarry Nodes |
| **Linked Funnel** | T2 | Hopper + Amethyst Shard + Echo Shard | Pipes mining or farm output straight in |

### 2.2 Access side (items)

| Tier | Item | Recipe | Capability |
|---|---|---|---|
| T1 | **Satchel** | Bundle + Ender Pearl + 2 Leather | Withdraw only; must be within 16 blocks of a Linked block |
| T2 | **Vault Pouch** | Shulker Box + Ender Eye + Echo Shard | Withdraw + deposit, same dimension, search field |
| T3 | **Void Satchel** | Vault Pouch + Netherite Ingot + Nether Star | Any dimension, sort, auto-restock hotbar/armour |

### 2.3 Capacity — the real balancing lever

| | Distinct types | Depth per type | Range | Remote deposit |
|---|---|---|---|---|
| T1 | 27 | 10 stacks | 16 blocks | ✗ |
| T2 | 108 | 64 stacks | Same dimension | ✓ |
| T3 | 512 | 2048 stacks | Anywhere | ✓ |

Tiers advance by consuming **Vault Sigils** at the Anchor — **1 Sigil per +27 distinct types**.
Sigils only come from late structures, so storage convenience scales exactly with how much you've
explored. Early game is already easier than vanilla (no chest sorting) but you cannot yet dump 40
distinct block types into a bottomless void.

### 2.4 UI shape

A real `ScreenHandler` screen (§0.1): a scrollable slot grid showing stored stacks with counts, a
live search field, click-to-withdraw and shift-click-to-deposit, and sort. Pinned favourites and a
recents page are still worth having — they were good ideas independent of the old constraint.

Cap distinct types per network for performance regardless of tier.

Scope discipline: this is a *convenience* store, not a logistics network. No autocrafting from the
Vault screen, no wireless multi-network routing.

### 2.5 Risk

The Vault holds thousands of items in persisted state. Any bug in the transfer path duplicates or
voids them, and players will not forgive either.

**Write the GameTest suite before the Vault itself** (CONVENTIONS.md §8): deposit N / withdraw N and
assert exact conservation, across a full inventory on withdraw, a capacity boundary, a mid-transfer
server shutdown, and concurrent access. All state goes through a versioned Codec (CONVENTIONS.md §4).

This risk used to be unavoidable and untestable — the data pack version could only be checked by
hand. It is now the single most testable part of the project, so there is no excuse for shipping it
untested.

---

## 3. Enchantments

### 3.1 Abundance I–III *(treasure)*

Multiplies **non-ore bulk drops only**: stone, deepslate, sand, gravel, clay, netherrack,
terracotta, logs. +1 drop per level at 60% chance each → ×1.6 / ×2.2 / ×2.8 average.

- Deliberately **excludes ores** — that's Fortune's job, and stacking both is how the economy breaks.
- Treasure-only, from mineshaft and trial chamber loot (T2). Not table-obtainable until archived (§3.4).
- Implementation: `enchantment_level` conditions on block loot tables checking the tool. No new
  effect component needed.

### 3.2 Kiln Touch I *(treasure)*

Autosmelt: raw ore → ingot, sand → glass, cobble → stone, clay → brick, log → charcoal. Grants the
furnace's smelting XP on break.

- **Stacks with Fortune** by applying `apply_bonus: ore_drops` to the ingot entry, so Fortune III on
  iron behaves exactly as it would on raw iron. This is the version worth building — most mods lose
  Fortune value on autosmelt.
- Exclusive set with Silk Touch.
- Source: Nether fortress and bastion loot (T2). Thematically right and geographically gated.

### 3.3 Excavation *(treasure)* — **UNDERSPECIFIED, see [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md)**

Chain-break / vein-mine. Flagged in the source conversation as one of the **two riskiest mechanics in
the pack** (alongside §7.5 villager persistence) and explicitly scheduled to be built and tested
**first, in isolation**, before anything depends on it.

Needs a concrete spec before implementation: block budget per level, tool-durability cost model,
what block sets it applies to, and recursion/entity-count safety limits.

### 3.4 The Codex — the knowledge system

```
BBB       B = Bookshelf
BLB       L = Lectern
 A        A = Amethyst Shard    → Codex
```

| Step | Interaction | Effect |
|---|---|---|
| **Archive** | Right-click Codex holding an enchanted book or enchanted item | Dialog: *"Archive Efficiency IV? The item will be consumed."* Enchantment + level recorded permanently |
| **Capacity** | — | T1: 3 enchantments · T2 (+Echo Shard): 9 · T3 (+Nether Star): unlimited |
| **Seal** | Right-click Codex empty-handed | List of archived enchantments → **Seal Tome** for one chosen enchantment. Costs 1 Book + 1 Ink Sac + 3 XP levels |
| **Teach** | Right-click a librarian holding the Sealed Tome | Confirm dialog → that librarian **permanently** sells that one book. One enchantment per librarian, forever. Consumes the Tome + a **Binding Contract** (Paper + Ink Sac + Emerald) |

**Fixed prices by enchantment value, no rerolling:** Mending 32 emeralds, Efficiency V 28,
Feather Falling IV 10, Silk Touch 24. Discounts and Hero of the Village still apply.

**Why this balances well:** you permanently lose the Mending book you found in order to make Mending
permanently purchasable. One lucky exploration payoff converts into lasting access — which is exactly
the vanilla trade-hall reroll grind deleted and replaced with exploring.

Implementation traps live in §7.5.

---

## 4. Farm replacement — Cores

Craft a **Blank Core**, attune it by *playing*, then socket it into housing.

```
IRI       I = Iron Bars
RHR       R = Redstone
IRI       H = Vital Heart      → Blank Core
```

### 4.1 Attunement — rewards the activity it replaces

| Core | Attunement |
|---|---|
| **Soul Core** | Hold Blank Core in offhand, kill 16 of one mob type → rewards fighting |
| **Verdant Core** | Blank Core + 4 Bone Meal + 4 Moss Block, then feed it one of the crop |
| **Pastoral Core** | Blank Core + 4 Hay Bale + 4 Leather, then right-click the animal |
| **Lithic Core** | Blank Core + 4 Stone + 4 Flint, then feed it one block sample (cobble / stone / deepslate / sand / gravel) |

### 4.2 Housing

| Block | Recipe | Base rate |
|---|---|---|
| **Soul Cage** | 8 Iron Bars + Soul Core | 1 loot roll / 20s |
| **Verdant Planter** | Composter + 4 Dirt + Verdant Core | 1 harvest / 30s |
| **Paddock** | 4 Oak Fence + 4 Hay + Pastoral Core | 1 yield / 45s |
| **Quarry Node** | 8 Deepslate Bricks + Lithic Core | 8 blocks / 20s |

Output goes to any adjacent container — **or straight into your Vault if a Linked block is adjacent.**
That is the integration payoff, and the moment the two flagship systems click together.

### 4.3 Rate tiering (upgrade in place)

| Tier | Cost | Effect |
|---|---|---|
| I | base | Roughly a small hand-built farm |
| II | Blaze Powder + Copper Block | 2.5× rate, +Looting II equivalent on Cages |
| III | Echo Shard + Netherite Scrap | 6× rate, Looting III, Cages start yielding rare drops (heads, totems at low rate) |

### 4.4 Guardrails

- **Active core cap per player:** 3 at T1, 6 at T2, 12 at T3. This is the anti-trivialisation valve
  and it protects TPS.
- **XP at 25% of normal** from Cages. Enchanting should still mean playing.
- **No spawn eggs. No Wither / Dragon / Warden cores.** Blaze, Ghast and Guardian cores gated to T2
  attunement (you have to go there anyway).
- **Fertility bonus** — scan surroundings, grant up to **+50%**: a Planter ringed by real farmland and
  water, a Cage inside an enclosed dark 5×5×3 room, a Quarry Node below y=0 in a hollowed chamber.
  This is the single best way to make *construction* pay off, and it's cheap with block-count predicates.
- **Offline accrual:** store a world-time stamp per core and settle on chunk load. **Never require
  chunkloaders.**

---

## 5. Lives and death

`keepInventory true`, XP kept. **The entire cost of dying moves to health.**

```
Life Heart = Vital Heart + 4 Gold Ingot
Blank Core = Vital Heart + 4 Iron Bars + 4 Redstone
```

Same parent, cheap divergent children — the choice is real, but a death never destroys a farm.

- Consume a Life Heart → +1 heart via a `max_health` attribute modifier.
- **Cap 15 hearts (30 HP).** Escalating cost: hearts 11–12 cost 1 Life Heart each, 13–14 cost 2,
  heart 15 costs 3.
- **On death: −1 heart. Floor at 5 hearts (10 HP), never below.**
- Recovery: consume more Life Hearts. The loop is *go explore, find shards, come back stronger.*
- At the floor, a **cosmetic "Frail" indicator only** — no stacked debuff (pillar §0.4).
- Config toggles for a harder mode: floor at 3 hearts, or −2 hearts for deaths in the End.

Implementation: a `max_health` attribute modifier applied from a player attachment holding the
current heart count, with the death hook on `ServerPlayerEvents.AFTER_RESPAWN` (or equivalent —
confirm the 26.2 event name). The attachment must survive respawn and dimension change, which is
exactly what attachments are for and what a scoreboard would have got subtly wrong.

---

## 6. Classic farm replacements

Each entry replaces a specific vanilla technical build. Target rates are deliberately *small farm*,
not *mega farm* (pillar §0.5).

> **Detail level:** the source design doc had per-entry recipes and rates for all of these. Only the
> two headline entries survived in full. The rest are named and scoped here and need their rates and
> attunement conditions written before implementation — see [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).

| Core | Replaces | Attunement | Notes |
|---|---|---|---|
| **Golem Core** | Iron farm | Craft an iron golem | The single most-built technical farm in vanilla |
| **Ominous Core** | Raid farm | Complete a level-5 ominous raid | **Capped at 1 per player.** ~1.6 totems/hr vs 30–60 for a real raid farm |
| Barter Core | Gold / piglin barter farm | TBD | T2 |
| Guardian Core | Guardian farm | TBD | T2, monument-gated |
| Tidal Core | Drowned / trident farm | TBD | Trident rate must stay low |
| Wither Skull Core | Wither skeleton skull farm | TBD | T2, fortress-gated |
| Ender Core | Enderman XP farm | TBD | T3, End-gated |
| Shulker Core | Shulker shell farm | TBD | T3. Interacts with §8 wandering-trader shells |
| Slime Core | Slime farm | TBD | T1/T2 |
| Apiary Core | Honey farm | TBD | T1 |
| Geode Core | Amethyst farm | TBD | T1/T2 |

The Ominous Core cap of 1 is load-bearing: totems are the pack's main "you can afford to die" item
and lives are already the death currency (§5).

---

## 7. Crafting, smelting and the workbench layer

### 7.1 Artisan's Table (T1 block) / Artisan's Kit (T2 portable)

Craft from your inventory **and your Vault**, in one place.

Both constraints that shaped this feature are **gone** (§0.1):

1. It can be a **real 3×3 crafting grid** plus a searchable recipe list — the "no datapack can give
   you a grid" limitation no longer applies.
2. There is **no shipped recipe index and no generator.** A mod reads the live `RecipeManager` at
   runtime, so the Artisan sees every recipe in the game including those added by other mods and data
   packs — strictly better than the ~280 hand-curated entries, and it never goes stale.
   `scripts/generate_recipe_index.py` was deleted with the data pack scaffold.

What remains genuinely undesigned is the *filtering* — "every recipe in the game" is not a useful
list. Craftable-now-first, favourites, and a search field are the minimum.

### 7.2 The Foundry — smelting

Core-powered and deliberately **fuel-free**. Bulk queue drawn from the Vault, gear recycling at 50%
return, and a Dye Vat.

Explicitly avoids building on vanilla furnace internals — fuel mechanics have moved around across
recent versions and the fragility isn't worth it.

### 7.3 Supporting quality-of-life changes

| Change | Tier | Why |
|---|---|---|
| Remove the anvil **"Too Expensive"** cap | T1 | Pure tedium, no skill expression |
| **Waystones** — Lodestone + Echo Shard, teleport between discovered ones | T2 | Rewards exploring, deletes walking |
| **Shulker shells from wandering traders** (2 for 12 emeralds) | T2 | Storage shouldn't be gated behind an End city grind |
| **XP Bank** — Bottle o' Enchanting recipe from stored levels | T2 | Banks exploration into enchanting |
| **Recall Stone** — one-use return to death location | T1 | Cheap safety net now that inventory is kept |
| Structure chests seeded with 1–2 Heart Shards | all | Makes every ruin worth opening |
| **Bounty advancements** paying shards / emeralds / XP / a Vault Sigil | all | Explicit reward for exploring, fighting, building |

#### 7.3.1 Bounty advancement triggers

Three independent chains — explore, fight, build — each rooted under `heartstead:bounty/root`
(earned by picking up your first Heart Shard). Each chain is T1 → T2 → capstone, linear parentage.

| Chain | Tier | Trigger shape | Reward |
|---|---|---|---|
| Explore | T1 | `location` — enter any of mineshaft / desert pyramid / jungle pyramid / igloo / swamp hut / shipwreck (OR) | Crate T1 |
| Explore | T2 | `location` — enter any of ancient city / trial chambers / monument / fortress / bastion / stronghold (OR) | Crate T2 |
| Explore | Capstone | `location` — enter end city **and** ancient city (AND) | Crate + **1 Vault Sigil** |
| Fight | T1 | `player_killed_entity` — kill any of zombie / skeleton / spider / creeper (OR) | Crate T1 |
| Fight | T2 | `player_killed_entity` — kill any of blaze / wither skeleton / evoker / elder guardian / ravager / piglin brute (OR) | Crate T2 |
| Fight | Capstone | `player_killed_entity` — kill warden **and** the ender dragon (AND) | Crate + **1 Vault Sigil** |
| Build | T1 | `placed_block` — place a bed **and** a chest/barrel **and** a light source (AND) | Crate T1 |
| Build | T2 | `construct_beacon` (level ≥ 1) **and** `placed_block` conduit **and** anvil (AND) | Crate T2 |
| Build | Capstone | `construct_beacon` (level 4, full beacon) **and** `placed_block` netherite block (AND) | Crate + **1 Vault Sigil** |

**Why this shape:** vanilla has no "player constructed a structure" trigger, only single-block
`placed_block` events and the existing `construct_beacon` criterion. Build bounties therefore reward
*milestone construction* (meaningful blocks placed, once each) rather than volume — matching how
"explore" rewards reaching a place rather than counting steps, and avoiding the counter/attachment
infrastructure that "place N blocks" or "kill N of a type" would need. That infrastructure doesn't
exist yet (attachments are Phase 1, §10) — Phase 0 bounties are deliberately built from stock advancement
triggers only.

The capstone-tier Vault Sigil is naturally capped at one per player: advancement completion is
itself the guard, the same pattern §6's Ominous Core cap relies on. No extra code needed.

### 7.4 Loot and reward overhaul

- Advancement-triggered bounties (explore, fight, build) paying emeralds / XP / resource crates
- Wandering trader sells bulk goods rather than novelties

**Decided against (2026-07-31):** broadening ordinary mob drops (gunpowder/string/blaze rods/ender
pearls from any hostile kill) and seeding structure chests with farm-substitute items. Both were
implemented and reverted — leave mob and chest loot tables alone. Bounties and the wandering trader
are the reward surface for this pillar, not blanket loot-table edits.

### 7.5 Villager trade persistence — **build and test this first**

Villagers **regenerate `Offers` on level-up and on restock**, which wipes any injected trade.

- Store the taught enchantment in an **attachment on the villager entity** and **re-apply the offer
  whenever a reset is detected.** (The data pack plan used scoreboard `Tags` because it had nothing
  better; an attachment is typed, codec-backed and holds the enchantment *and* level directly.)
- 26.2 specifically fixed a bug where an empty `Offers` tag failed to persist through a relog or data
  merge. Trade manipulation is **version-sensitive** — pin the format range tightly and re-test the
  villager path on every release.

---

## 8. Distribution — **Fabric mod** (decided 2026-07-31)

**Decision: a single Fabric mod. Not a data pack, and not a datapack + optional-mod hybrid.**

### 8.1 Why

The data pack path was abandoned two items into Phase 0.1. Ranked by weight:

1. **Blocks.** The design has 12+ custom blocks (Vault Anchor, Linked Chest/Barrel/Funnel, Soul Cage,
   Verdant Planter, Paddock, Quarry Node, Codex, Artisan's Table, Foundry, Dye Vat). A data pack
   cannot register a block; each would be a vanilla block shadowed by a marker entity that desyncs on
   piston/explosion/chunk-edge, can't have its own texture without repainting *all* barrels, and
   loses identity on break. There is no workaround, obscure-base-block or otherwise.
2. **Three of five flagship systems are inventory UIs.** Vault, Artisan, Foundry. The old plan
   already conceded these needed a mod — which meant building each *twice*, dialog and screen, with
   the state layer stuck in `/data storage` regardless. The hybrid was more work than either pure path.
3. **Item identity.** Data pack recipes match ingredients by item id, not `custom_data`, so any
   currency item can be faked with a plain copy of its base item. The mitigation — pick a base item
   with no other survival source — does not scale to ~25 custom items. It was already strained at two.
4. **Verification.** This is the underrated one. The data pack could only be tested by hand. The mod
   has compile checking, JUnit and GameTest, which turns §2.5 from an accepted risk into a tested one.

Confirmed 2026-07-31: 26.2 still has **no** data-pack item or block registration. It is vanilla-item-
plus-components, same as 1.21.x. This is not a limitation that is about to lift.

### 8.2 What it costs

| Cost | Note |
|---|---|
| **No Realms** | Realms takes data packs, not mods. Currently **undecided** whether this matters — see OPEN-QUESTIONS.md. Every mod-only dependency should be flagged until it's settled |
| **Both sides install** | Server *and* client for multiplayer |
| **Release lag** | Mods trail new Minecraft versions; data packs mostly need a format bump |
| **Zero-install lost** | Partly theoretical — custom item textures already required a resource pack, and singleplayer can't auto-push one |

### 8.3 What did *not* change

**Content stays data-driven.** Recipes, loot tables, advancements, tags and enchantments are still
JSON, now shipped inside the mod jar at `src/main/resources/data/heartstead/`. Vanilla loot overrides
still go in `data/minecraft/`. The Phase 0 economy work carries over essentially unchanged.

Being a mod is a licence to write Java where Java helps — not an instruction to write Java everywhere.

---

## 9. Version targeting

| | |
|---|---|
| Target release | **Minecraft 26.2** (released 2026-06-16) |
| Java | **25** (`java-runtime-epsilon`) — Gradle itself must run on JDK 25, not just the toolchain |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.156.0+26.2 |
| Loom | 1.17.17 |
| Mappings | **None.** 26.1 was the first unobfuscated release — nothing to map, nothing remapped |
| Data pack format (bundled JSON) | 107.1 |

26.2 changed the **entity predicate format** to a component-style map and now **rejects unknown
sub-predicates**. Any predicate example found from a 1.21.x tutorial will need rewriting.

26.3 snapshots are already live (data pack formats 108→113), so expect a version bump soon. Mods
break on version bumps via mappings and API changes; run `./gradlew genSources` and read the real
signatures rather than trusting a tutorial. Item construction in particular changed several times
across 1.21.x.

---

## 10. Build order

Ship in this order. Each phase is independently playable.

| Phase | Content | Why here |
|---|---|---|
| **0** | Item registration + heart economy + loot tables + bounty advancements | Mostly JSON as before, plus real item registration. **Ship this alone and it's already a good mod.** |
| **0.5** | Spike: §3.3 Excavation chain-break + §7.5 villager persistence | Still the two riskiest mechanics — but now each ships with a GameTest instead of a manual checklist |
| **1** | Lives system | Small, self-contained, high impact. First use of player attachments |
| **2** | Codex + librarian teaching | The most novel feature, and the one most likely to attract users |
| **3** | The Vault | Biggest lift. **Write the conservation GameTests first** (§2.5) |
| **4** | Cores (incl. §6 classic farm replacements) | Needs the Vault for its best version. First real block entities with offline accrual |
| **5** | Artisan's Table + Foundry | Needs the Vault. No longer needs a recipe index generator (§7.1) |

The old Phase 6 ("optional client mod") is gone — it *is* the mod now.

Phase 0 is slightly larger than in the data pack plan, because items and blocks now need registering
before any JSON references them. Everything after Phase 0 is smaller.

---

## 11. Tuning dials, ranked by impact

1. **Heart Shard drop rates** — controls the pace of literally everything.
2. **Active core cap per tier** — the difference between *"no farms needed"* and *"resources meaningless"*.
3. **Vault type capacity per Sigil** — controls how long storage stays a puzzle.
4. **Core base rates** — aim at *small hand-built farm*, not mega-farm.
5. **Fixed librarian prices** — too cheap and enchanting collapses.

**Ship `core_rate_multiplier` at 1.0, but expect to lower it after playtesting.** Everyone
underestimates compounding passive income.

---

## 12. Known risks

| Risk | Mitigation |
|---|---|
| **Item loss / duplication** in the Vault | GameTest conservation suite written *before* the Vault (§2.5). Versioned Codecs for all persisted state |
| **Version churn.** Mappings and API move every release | Pin versions in `gradle.properties`; `./gradlew genSources` before writing against an unfamiliar class; never trust a pre-1.21.5 tutorial |
| **Client/server split leaks.** A client-only class in common code crashes dedicated servers | Split source sets (CONVENTIONS.md §3); run `runGametest` (server-side) in CI, not just `runClient` |
| **Compounding passive income** | §11 — ship at 1.0, plan to lower |
| **Scope creep now that the UI ceiling is gone** | §0.1 — the pitch is casual convenience, not an ME terminal. New capability is not a mandate |
| **Villager `Offers` regeneration** (§7.5) | Still a real risk, but now testable — GameTest it across level-up, restock and reload |
