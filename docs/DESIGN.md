# Heartstead — Design Wiki

> **Status:** reconstructed spec, v0.3 · **Target:** Minecraft Java 26.2 · **Fabric mod**, Java 25 · see §8
>
> This document is the **ground truth** for the pack. Implementation work should reference sections
> by number (e.g. "implement §5 Golem Core") rather than re-describing features. If reality and
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
4. **Never punish a struggling player twice.** Floors, not spirals. See §6.
5. **Leave technical play intact.** Cores match a *small hand-built farm*, not a mega-farm. Players
   who enjoy building the real thing still get more out of it — they just aren't forced into it.
6. **Not an overhaul mod.** This doesn't redefine how you play Minecraft, it reshapes the tedium
   and rewards the above pillars.

Pillars are cited by number throughout — "pillar 5" means the fifth entry above.

---

## 1. The progression spine

The most important decision in the pack. One item family feeds every system.

| Item | Source | Notes |
|---|---|---|
| **Heart Shard** | Dungeon / mineshaft / temple / shipwreck chests (30%, 1–2), trial vaults, fishing treasure, 0.5% from any hostile mob, 10% from Evoker / Elder Guardian | The base currency |
| **Vital Heart** | 4 Shards + Golden Apple | The universal core |
| **Vault Sigil** | Ancient City, End City, Bastion, ominous vaults — never craftable | Storage capacity, and one gate item (§2.2) |

```
 S        S = Heart Shard
SAS       A = Golden Apple
 S        → Vital Heart
```

**Target acquisition rate:** ~1 Vital Heart per 30–40 min of active exploring at T1, ~1 per 15 min
at T2. Tune the shard drop tables to hit that — it is the single biggest dial in the pack (§10).

### 1.1 The central tension

Vital Hearts feed **both** farm cores and extra lives. Spending three hearts on an iron core means
three fewer hearts of health buffer. That choice is the best mechanic in this design.

**Guardrail:** a death must never cost you your first core. Both children of the Vital Heart cost one
heart plus a cheap surcharge, and Blank Core (§4) is deliberately the *cheaper* surcharge —
4 iron + 4 redstone against Life Heart's (§6) 4 gold — so a new player builds their first farm core
before their first extra heart. The pack helps you before it charges you.

> The Vault Sigil carries the same tension in miniature: one spent on your own **Vault Pouch** (§2.2)
> is one not spent on the world's **capacity** (§2.3). On a server that is personal reach against
> shared storage — deliberately the sharpest version of the choice in the pack.

### 1.2 Tier definitions (used throughout)

These define the rough stage of the game, and are not unlockables or steps.

| Tier | Gate | Player state |
|---|---|---|
| **T1** | Pre-Nether → early Nether | Iron, copper, first villager, first dungeon |
| **T2** | Nether established | Diamonds, fortress/bastion, trial chambers, monument, first raid, **ancient city** |
| **T3** | End access | End city, netherite, elytra |

**Ancient city sits at T2, not T3** — the deep dark needs no End access, and two T2 items (the Linked
Funnel and the Codex T2 upgrade) depend on Echo Shards. Getting there is still the hardest thing a T2
player does; that's the point.

---

## 2. Storage — The Vault

A **world-level** virtual inventory, persisted via codec-backed `SavedData` (CONVENTIONS.md §4).
Storage is not backed by real containers, so there is no dupe risk from container NBT juggling.

**One Vault per world, shared by everyone on it.** Anyone can deposit, anyone can withdraw, and a
Sigil spent by one player raises capacity for all. This matches §4.3 — cores are world state too —
so the whole "what does this look like on a server" question has one answer: the base is shared, the
*access* is personal. What each player owns is their Satchel or Pouch (§2.2) and how far they can
reach with it.

The trade-off is real and accepted: on a public server, a shared Vault is grief-able in the same way
a shared chest room is. That is a server-administration problem, not a design one, and the alternative
— a private Vault per player — makes the flagship system invisible to the people you play with.

### 2.1 Input side (blocks)

```
 E        E = Ender Pearl        │  Linked Barrel (T1)
CBC       C = Copper Ingot       │  Barrel + 2 Pearl + 2 Copper
 E        B = Barrel             │  Vacuums contents into your Vault
```

| Block | Tier | Recipe | Role |
|---|---|---|---|
| **Vault Anchor** | T1 | Linked Barrel + Amethyst Shard | Creates the Vault; where Sigils are consumed. **One per world** — a second one cannot be placed |
| **Linked Barrel** | T1 | Barrel + 2 Ender Pearl + 2 Copper Ingot | Vacuums its contents into your Vault |
| **Linked Chest** | T2 | Chest + Amethyst Block + 2 Copper Block | Higher throughput; accepts from Cages / Quarry Nodes |
| **Linked Funnel** | T2 | Hopper + Amethyst Shard + Echo Shard | Pipes mining or farm output straight into the Vault, or can be set to output a specific item from the Vault. All at hopper speed |

### 2.2 Access side (items)

| Tier | Item | Recipe | Capability | Range |
|---|---|---|---|---|
| T1 | **Bundle** | Unchanged | Replaces bundle behaviour with a UI-based inventory that holds 9 slots | — |
| T1 | **Satchel** | Bundle + Ender Pearl + 2 Leather | Withdraw only | 5×5 chunks centred on the Vault Anchor |
| T2 | **Vault Pouch** | Satchel + Vault Sigil + Ender Eye | Withdraw + deposit, search field | Same dimension |
| T3 | **Void Satchel** | Vault Pouch + Netherite Ingot + Nether Star | Sort, auto-restock hotbar/armour | Anywhere |

**Range and remote deposit are properties of the access item, not the Vault.** Capacity (§2.3) is a
property of the Vault, and therefore of the world. Keeping those two axes separate is what lets
Sigils be a clean single dial — and it is what makes a shared Vault work, because the thing each
player progresses individually is the reach of their own pouch.

> **Balance flag — the Bundle override.** This is the one place the design overwrites a vanilla item's
> behaviour, which brushes against pillar 6. Nine slots is ~9× vanilla bundle capacity, granted at T1
> for a vanilla-cost item, and the Bundle is also the Satchel's crafting base. Slot count is a config
> field; expect to lower it before it competes with the Vault itself. See OPEN-QUESTIONS.md.

### 2.3 Capacity — the real balancing lever

Capacity advances by consuming **Vault Sigils** at the Anchor, in three tiers with explicit costs. It
is a property of the world, so Sigils pool: on a server, four players each bringing back two Sigils
gets everyone to T2.

| Vault tier | Sigils (cumulative) | Distinct types | Depth per type |
|---|---|---|---|
| **T1** — Anchor only | 0 | 27 | 10 stacks |
| **T2** | 3 | 108 | 64 stacks |
| **T3** | 8 | 512 | 2048 stacks (effectively unbounded) |

Past T3, each further Sigil buys **+27 distinct types** and nothing else.

Sigils only come from late structures, so storage convenience scales exactly with how much you've
explored. Ominous vaults are repeatable, so the supply is not hard-capped and 8 is reachable without
grinding — but each one is a trip. Early game is already easier than vanilla (no chest sorting) but
you cannot yet dump 40 distinct block types into a bottomless void.

### 2.4 UI shape

A real `ScreenHandler` screen: a scrollable slot grid showing stored stacks with counts, a live
search field, click-to-withdraw and shift-click-to-deposit, and sort. Pinned favourites and a
recents page are still worth having.

Scope discipline: this is a *convenience* store, not a logistics network. No autocrafting from the
Vault screen, no wireless multi-network routing.

### 2.5 Risk

The Vault holds thousands of items in persisted state. Any bug in the transfer path duplicates or
voids them, and players will not forgive either.

**Write the GameTest suite before the Vault itself** (CONVENTIONS.md §8): deposit N / withdraw N and
assert exact conservation, across a full inventory on withdraw, a capacity boundary, a mid-transfer
server shutdown, and concurrent access. All state goes through a versioned Codec (CONVENTIONS.md §4).

**Concurrent access is now a first-class case, not an edge case** — a shared Vault means two players
can withdraw the same stack in the same tick. All mutation is server-side and serialised through the
`SavedData`; the screen handler sends intents, never results.

This is the single most testable part of the project, so there is no excuse for shipping it untested.

---

## 3. Enchantments

### 3.1 Abundance I–III *(treasure)*

Multiplies **non-ore bulk drops only**: stone, deepslate, sand, gravel, clay, netherrack,
terracotta, logs. +1 drop per level at 60% chance each → ×1.6 / ×2.2 / ×2.8 average.

- Deliberately **excludes ores** — that's Fortune's job, and stacking both is how the economy breaks.
- Treasure-only, from mineshaft and trial chamber loot (T2). Not table-obtainable until archived (§3.3).
- Implementation: `enchantment_level` conditions on block loot tables checking the tool. No new
  effect component needed.

### 3.2 Kiln Touch *(treasure)*

Autosmelt: raw ore → ingot, sand → glass, cobble → stone, clay → brick, log → charcoal. Grants the
furnace's smelting XP on break.

- **Stacks with Fortune** by applying `apply_bonus: ore_drops` to the ingot entry, so Fortune III on
  iron behaves exactly as it would on raw iron. This is the version worth building — most mods lose
  Fortune value on autosmelt.
- Exclusive set with Silk Touch.
- Source: Nether fortress and bastion loot (T2). Thematically right and geographically gated.

### 3.3 The Codex — the knowledge system

```
BBB       B = Bookshelf
BLB       L = Lectern
 A        A = Amethyst Shard    → Codex
```

| Step | Interaction | Effect |
|---|---|---|
| **Archive** | Place an enchanted book or enchanted item in the archive UI slot | Enchantment + level recorded permanently. The item is consumed |
| **Capacity** | — | T1: 8 enchantments · T2 (+Echo Shard): 16 · T3 (+Nether Star): unlimited |
| **Tome** | Place an empty Tome in the empower slot | List of archived enchantments → **Sealed Tome** for one chosen enchantment. Tome recipe: Book + Lapis + Iron Ingot |
| **Teach** | Enter the Sealed Tome in a librarian trade | Consumes the Tome + 1 emerald. That librarian now permanently sells that book |

Two costs, don't confuse them: **teaching** costs a Sealed Tome plus one emerald, once per librarian.
**Buying** the taught book afterwards costs the fixed price below, every time.

**The archive belongs to the player, not to the block** — a codec-backed player attachment
(CONVENTIONS.md §4). Any Codex you walk up to shows *your* archive and *your* capacity, so the block
is a workbench rather than a container, and there is nothing to lose if someone breaks it. This is
the one system that stays personal while the Vault (§2) and the core registry (§4.3) are world state,
and deliberately so: what you have found and archived is a record of your own play, and two players
on a server should each have to earn their own Mending.

The Echo Shard and Nether Star capacity upgrades are therefore consumed **per player**, at any Codex.

**Fixed prices by enchantment value, no rerolling:** Mending 32 emeralds, Efficiency V 28,
Feather Falling IV 10, Silk Touch 24. Discounts and Hero of the Village still apply.

**Why this balances well:** you permanently lose the Mending book you found in order to make Mending
permanently purchasable. One lucky exploration payoff converts into lasting access — which is exactly
the vanilla trade-hall reroll grind deleted and replaced with exploring.

Implementation traps live in §7.2.

---

## 4. Farm replacement — Cores

Craft a **Blank Core**, attune it by *playing*, then socket it into housing.

```
IRI       I = Iron Ingot
RHR       R = Redstone
IRI       H = Vital Heart      → Blank Core
```

### 4.1 Attunement — rewards the activity it replaces

Two steps, the same shape for every core.

**Step 1 — Prime (crafting).** A shaped recipe combines the Blank Core with family reagents and
yields a **Primed Core**. The *family* is now fixed; the *target* is not. The stack carries a
`heartstead:attunement` component — `{ family, target: null, progress: 0 }`.

**Step 2 — Imprint (playing).** Hold the Primed Core and do the thing the core will replace. The
first qualifying event writes the target; every later one of the same target increments progress. At
the threshold the stack converts into the finished Core, named for what it learned —
*Verdant Core (Wheat)*.

| Family | Prime recipe | Imprint action | Threshold |
|---|---|---|---|
| **Soul Core** | Blank Core + 4 Rotten Flesh + 4 Bone | Kill a hostile mob | 16 kills of the first type killed |
| **Verdant Core** | Blank Core + 4 Bone Meal + 4 Moss Block | Harvest a mature crop | 32 of the first crop harvested |
| **Pastoral Core** | Blank Core + 4 Hay Bale + 4 Leather | Breed two animals | 8 breeds of the first species bred |
| **Lithic Core** | Blank Core + 4 Stone + 4 Flint | Mine a stone or earth block | 64 of the first block type mined |

The numbers are one session of the activity, not a grind: 16 kills is a few minutes in a dark room,
32 crops is one small field, 8 breeds is the pen you were going to build anyway, 64 blocks is a
stack of mining. All four are config fields (§10, CONVENTIONS.md §5).

**How it behaves in the hand:**

- **A target only locks from the hand.** An un-attuned Primed Core has to be in your **main hand or
  offhand** to take a target — sitting in the inventory it is inert. Deliberate: you should never
  discover that a core in your backpack quietly became a chicken core. If un-attuned cores of the
  same family are in both hands, the main hand takes the lock.
- **The lock happens on the first qualifying event, not at craft time.** Kill a zombie holding a
  Primed Soul Core and it is a zombie core from that moment. The tooltip updates on that first
  event — `Attuning: Zombie 1/16` — so the lock is never a surprise you discover at 16/16.
- **Once a core has a target, everything you carry progresses at once.** Attuned Primed Cores count
  from anywhere in the inventory, and any number of them advance in parallel — carry a zombie Soul
  Core, a wheat Verdant Core and a deepslate Lithic Core and one afternoon of ordinary play feeds all
  three. Nothing is queued and nothing waits its turn.
- **Progress lives on the stack.** Drop it, chest it, die with it — the counter is a data component,
  so it survives everything a stack survives. There is no player-side counter to desync.
- **Wrong-target events are inert.** Mining dirt with a stone-locked Lithic Core does nothing at all.
  No penalty, no reset — pillar 4.
- **The tooltip is the entire UI.** No screen, no block, no ritual. That is the point: attunement is
  something you finish without noticing you were doing it.
- **Priming is reversible until the target locks.** A Primed Core at any progress reverts to a Blank
  Core in the crafting grid; the reagents are not refunded. Once a target is locked it is locked, and
  that irreversibility is what gives the choice weight.

### 4.2 Housing

Socket the finished Core into its matching housing block. Cores can be pulled back out and
re-socketed elsewhere; they never degrade.

| Block | Recipe | Base rate |
|---|---|---|
| **Soul Cage** | 8 Iron Bars + Soul Core | 1 loot roll / 20s |
| **Verdant Planter** | Composter + 4 Dirt + Verdant Core | 1 harvest / 30s |
| **Paddock** | 4 Oak Fence + 4 Hay + Pastoral Core | 1 yield / 45s |
| **Quarry Node** | 8 Deepslate Bricks + Lithic Core | 8 blocks / 20s |

Housings are deliberately cheap — the Core is the cost, and it already ate a Vital Heart.

**The housing supplies the family; the core supplies the rate and the loot table.** The rates above
are what a §4.1 core yields; a §5 core overrides them with its own (§5). That is why eleven classic
farm replacements need no new blocks.

Output goes to small internal storage — **or straight into your Vault if a Linked Funnel is below.**
That is the integration payoff, and the moment the two flagship systems click together.

**Cores accrue while unloaded, and must never require a chunkloader.** A housing block stores the
world-time stamp of its last settled yield. On chunk load — and on any interaction — it settles the
whole elapsed delta in one calculation rather than ticking. Nothing about a core rewards keeping a
chunk alive, and walking away costs you nothing.

The two bugs this design invites, and both are GameTestable (CONVENTIONS.md §8):

- **Double-counting** — settling on chunk load *and* ticking while loaded. Settle from the timestamp
  and update the timestamp in the same operation; never accrue from two clocks.
- **Unbounded catch-up** — a chunk untouched for forty in-game days dumps its whole backlog at once.
  Cap the settled delta at a configurable ceiling (default: 24 in-game hours of yield) so internal
  storage limits are meaningful and the first chunk load after a long absence isn't a jackpot.

### 4.3 Rate tiering

Upgrade via upgrade slots within the housing, which consume the resource.

| Tier | Cost | Effect |
|---|---|---|
| I | base | Roughly a small hand-built farm |
| II | 4 Blaze Powder | 2.5× rate, +Looting II equivalent on Cages |
| III | 2 Netherite Scrap | 6× rate, Looting III, Cages start yielding rare drops (heads, totems at low rate) |

**Tiering is the only way to scale. Building more of the same core is not.**

**One active core per target, per world.** A zombie Soul Core cannot be socketed while another zombie
Soul Core is active anywhere in the world — the socket is **refused at the point of insertion**, with
the reason shown to the player. Nothing is ever placed and then silently switched off, so there is no
dormancy, no wake-up ordering, and no "which of my four does the game pick" question to answer.

Breadth is free and encouraged: zombie *and* skeleton, wheat *and* carrot, cobble *and* deepslate are
all fine. It is only the duplicate that is refused. Depth costs Blaze Powder and Netherite Scrap.

This is the anti-trivialisation valve, and it does the job a per-player core cap would have done —
better, because it points players at the interesting axis instead of a number. It also bounds tick
cost: live housings are bounded by the number of distinct things anyone bothered to attune.

The registry of active cores is **world-level `SavedData`** (CONVENTIONS.md §4), not player state:
the constraint is on the world, so on a server twenty players share one set of cores. That is the
same shape as the Nether portal or the world spawn — a place the server has, not a thing each player
owns — and it removes any question about what happens when a player leaves.

---

## 5. Classic farm replacements

Each entry replaces a specific vanilla technical build. Target rates are deliberately *small farm*,
not *mega farm* (pillar 5).

They use the same **prime → imprint** shape as §4.1 and the same **one active core per target, per
world** rule as §4.3 — they share one registry with the §4 cores, so a Golem Core and a zombie Soul
Core are two different targets and both may run.

**No new housing blocks.** Every core here sockets into one of the four §4.2 housings. The housing
supplies the family and the shape; the **core** supplies the rate and the loot table, overriding the
§4.2 base rate. That keeps §5 pure data — eleven recipes and eleven loot tables, no new block
entities, no new models.

**Two imprint shapes**, chosen per core by whether anything already gates it:

- **Milestone** where a structure, dimension or boss is the gate. Doing the thing once *is* the
  attunement, because getting there was the cost.
- **Counted** where the activity can be done anywhere from T1. These use the §4.1 rule — the first
  qualifying event locks nothing (the target is implied by the core), it just counts.

Prime recipes **consume a trophy from the thing they automate** wherever one exists — a Wither
Skeleton Skull, a Shulker Shell, a Nautilus Shell. You pay one of the drop you are about to make
passive, which is the fairest possible gate and needs no extra code.

| Core | Replaces | Tier | Housing | Prime recipe (+ Blank Core) | Imprint | Base rate, tier I |
|---|---|---|---|---|---|---|
| **Golem** | Iron farm | T1 | Soul Cage | 4 Iron Block + Poppy | *Counted:* build 4 iron golems while carrying it | 1 ingot / 40s — **90/hr** |
| **Ominous** | Raid farm | T2 | Soul Cage | 4 Emerald Block + Ominous Bottle | *Milestone:* complete a level-5 ominous raid | 1 roll / 90s, totem 4% — **1.6 totems/hr** |
| **Barter** | Piglin barter farm | T2 | Soul Cage | 4 Gold Block + Crying Obsidian | *Milestone:* barter with a piglin | 1 barter / 30s — **120/hr** |
| **Guardian** | Guardian farm | T2 | Soul Cage | 4 Prismarine Brick + Sponge | *Milestone:* kill an Elder Guardian | 1 roll / 25s — **144/hr** |
| **Tidal** | Drowned / trident farm | T2 | Soul Cage | 4 Prismarine + Nautilus Shell | *Counted:* kill 16 drowned | 1 roll / 30s, trident 0.5% — **0.6 tridents/hr** |
| **Wither Skull** | Skull farm | T2 | Soul Cage | 4 Nether Brick + Wither Skeleton Skull | *Milestone:* kill a wither skeleton in a fortress | 1 roll / 45s, skull 2% — **1.6 skulls/hr** |
| **Ender** | Enderman XP farm | T3 | Soul Cage | 4 Obsidian + 4 Ender Pearl | *Milestone:* kill the Ender Dragon | 1 roll / 20s + XP — **180 pearls/hr** |
| **Shulker** | Shulker shell farm | T3 | Soul Cage | 4 Purpur Block + Shulker Shell | *Milestone:* kill a shulker in an End city | **10 shells/hr** |
| **Slime** | Slime farm | T1 | Soul Cage | 4 Slime Block + Moss Block | *Counted:* kill 16 slimes | 1 slimeball / 30s — **120/hr** |
| **Apiary** | Honey farm | T1 | Paddock | 4 Honeycomb + 4 Flowers | *Counted:* breed 8 bees | 1 comb or bottle / 60s — **60/hr** |
| **Geode** | Amethyst farm | T1 | Quarry Node | 4 Amethyst Block + Calcite | *Counted:* mine 32 amethyst clusters | 4 shards / 60s — **240/hr** |

**How far below a real farm these sit**, which is the number that matters for pillar 5 — tier I
against a competent technical build:

| Core | Tier I | Tier III (6×) | Real technical farm |
|---|---|---|---|
| Golem | 90 ingots/hr | 540 | 600+ |
| Ominous | 1.6 totems/hr | 9.6 | 30–60 |
| Barter | 120/hr | 720 | ~1000 |
| Guardian | 144/hr | 864 | 300+ |
| Tidal | 0.6 tridents/hr | 3.6 | 20+ |
| Wither Skull | 1.6 skulls/hr | 9.6 | 10–20 |
| Ender | 180 pearls/hr | 1080 | 2000+ |
| Shulker | 10 shells/hr | 60 | 100+ |
| Slime | 120/hr | 720 | 1000+ |
| Apiary | 60/hr | 360 | ~200 |
| Geode | 240 shards/hr | 1440 | ~1000 |

Tier I lands at roughly a tenth to a third of a real farm, and **fully upgraded lands near parity**
— which is the intended shape. A player who has explored enough to buy tier III has earned a real
farm's output; a player who builds the actual farm still gets there sooner and cheaper. Two entries
overshoot at tier III (Apiary, Geode, both above their vanilla equivalent) because their real farms
are cheap to build — lower those two first if tier III proves too strong.

All rates are config fields and all are multiplied by `core_rate_multiplier` (§10).

---

## 6. Lives and death

`keepInventory true`, XP kept. **The entire cost of dying moves to health.**

```
Life Heart = Vital Heart + 4 Gold Ingot
Blank Core = Vital Heart + 4 Iron Ingot + 4 Redstone
```

Same parent, cheap divergent children — the choice is real, but a death never destroys a farm.

- Consume a Life Heart → +1 heart via a `max_health` attribute modifier.
- **Cap 20 hearts (40 HP).**
- **On death: −1 heart. Floor at 5 hearts (10 HP), never below.**
- Recovery: consume more Life Hearts. The loop is *go explore, find shards, come back stronger.*
- Config toggles for a harder mode: floor at 3 hearts, or −2 hearts for deaths.

Implementation: a `max_health` attribute modifier applied from a player attachment holding the
current heart count, with the death hook on `ServerPlayerEvents.AFTER_RESPAWN` (or equivalent —
confirm the 26.2 event name). The attachment must survive respawn and dimension change, which is
exactly what attachments are for and what a scoreboard would have got subtly wrong.

---

## 7. Other

### 7.1 Artisan's Table (T1 block) / Artisan's Kit (T2 portable)

A real 3×3 crafting grid that draws from your inventory **and the Vault**, with the ordinary vanilla
recipe book on the side. Recipes count as craftable when the ingredients are in the Vault, and
clicking one pulls what's missing out of it.

| Item | Tier | Recipe |
|---|---|---|
| **Artisan's Table** | T1 | Crafting Table + 4 Copper Ingot + Amethyst Shard |
| **Artisan's Kit** | T2 | Artisan's Table + 2 Leather + Ender Pearl — the same menu, opened from the hand |

The recipe book is **vanilla's, reused as-is** — search, category tabs, craftable-only toggle and
click-to-fill all come with it, live off the `RecipeManager`, including other mods' recipes. There is
no filtering problem to solve here and no browser to design; matching vanilla behaviour beats
inventing a second convention players have to learn. Implementation shapes are in
[REFERENCES.md](REFERENCES.md).

### 7.2 Villager trade persistence — **build and test this first**

Villagers **regenerate `Offers` on level-up and on restock**, which wipes any injected trade.

- Store the taught enchantment in an **attachment on the villager entity** and **re-apply the offer
  whenever a reset is detected.** An attachment is typed, codec-backed, and holds the enchantment
  *and* level directly.
- 26.2 specifically fixed a bug where an empty `Offers` tag failed to persist through a relog or data
  merge. Trade manipulation is **version-sensitive** — pin the format range tightly and re-test the
  villager path on every release.

### 7.3 Advancements — **deliberately deferred**

The pack will have an advancement tree. It is **not designed yet, and that is on purpose.**

An earlier draft specified a "bounty" system: three advancement chains — explore, fight, build —
paying reward crates of emeralds and Heart Shards, with a Vault Sigil at each capstone. **Cut
2026-08-01.** The reasoning is worth keeping, because it is the test any future version has to pass:

Bounties were a *second* reward channel bolted alongside the first. §1 already pays you for exploring,
fighting and building — that is the entire point of the Heart Shard. Paying twice for the same
activity doesn't reinforce the loop, it obscures how well the loop works, and it makes the pack's
core economy impossible to tune honestly. Sigils dropping out of an advancement tree also quietly
undercut §2.3, where storage capacity is supposed to be the thing you travel for.

So: **ship the economy first, play it, then decide.** If normal progression turns out to under-reward
exploring, fighting or building, that is a §1 drop-rate problem before it is an advancement problem —
and §10 ranks shard rates as the highest-impact dial for exactly this reason.

What advancements should be when they arrive: **recognition, not currency.** Vanilla advancements
mark that you did a thing; they don't pay you for it. A Heartstead tree that names milestones —
your first Vault Sigil, a fully attuned core, a T3 Vault — costs nothing to balance and can be
designed at the end, once the systems it describes have stopped moving.

Design them last, and only after playtesting. Nothing in the build order (§9) depends on them.

---

## 8. Version targeting

| | |
|---|---|
| Target release | **Minecraft 26.2** (released 2026-06-16) |
| Java | **25** (`java-runtime-epsilon`) — Gradle itself must run on JDK 25, not just the toolchain |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.156.0+26.2 |
| Loom | 1.17.17 |
| Mappings | **None.** 26.1 was the first unobfuscated release — nothing to map, nothing remapped |
| Data format (bundled JSON: recipes, loot tables, advancements, tags) | 107.1 |

26.2 changed the **entity predicate format** to a component-style map and now **rejects unknown
sub-predicates**. Any predicate example found from a 1.21.x tutorial will need rewriting.

26.3 snapshots are already live (data formats 108→113), so expect a version bump soon. Mods break on
version bumps via mappings and API changes; run `./gradlew genSources` and read the real signatures
rather than trusting a tutorial. Item construction in particular changed several times across 1.21.x.

---

## 9. Build order

Ship in this order. Each phase is independently playable.

| Phase | Content | Why here |
|---|---|---|
| **0** | Item registration + heart economy + loot tables | Mostly JSON, plus real item registration. Ship this and play it before adding reward systems on top (§7.3) |
| **0.5** | Spike: §7.2 villager persistence | Still the riskiest remaining mechanic — but now ships with a GameTest instead of a manual checklist |
| **1** | Lives system (§6) | Small, self-contained, high impact. First use of player attachments |
| **2** | The Vault (§2) | Biggest lift. **Write the conservation GameTests first** (§2.5). First real blocks and screen handlers — establish those patterns here |
| **3** | Codex (§3.3) + Artisan's Table (§7.1) + librarian teaching | The most novel feature, and the one most likely to attract users. Reuses Phase 2's block and screen patterns |
| **4** | Cores (§4, incl. §5 classic farm replacements) | Needs the Vault for its best version |

---

## 10. Tuning dials, ranked by impact

1. **Heart Shard drop rates** — controls the pace of literally everything.
2. **Core base rates and `core_rate_multiplier`** — aim at *small hand-built farm*, not mega-farm.
   With one core per target (§4.3), tier III at 6× is the whole scaling curve.
3. **Vault Sigil cost per capacity tier** — controls how long storage stays a puzzle.
4. **Attunement thresholds** (§4.1) — the difference between "earned while playing" and "a grind".
5. **Fixed librarian prices** — too cheap and enchanting collapses.
6. **Bundle slot count** (§2.2) — currently 9; the largest unvalidated T1 buff in the pack.

**Ship `core_rate_multiplier` at 1.0, but expect to lower it after playtesting.** Everyone
underestimates compounding passive income.

---

## 11. Known risks

| Risk | Mitigation |
|---|---|
| **Item loss / duplication** in the Vault | GameTest conservation suite written *before* the Vault (§2.5). Versioned Codecs for all persisted state |
| **Compounding passive income** | §4.3 caps cores at one per target per world, so breadth is bounded by what's been attuned and depth by tier cost — §10, ship `core_rate_multiplier` at 1.0 and plan to lower |
| **Offline accrual double-counting or backlog dumps** (§4.2) | Settle from a single stored timestamp, never from two clocks; cap the settled delta. GameTest it: advance the clock, unload and reload, assert the yield matches the formula exactly |
| **Socket refusal desync** (§4.3) | The active-core registry is world `SavedData` and the refusal is server-authoritative; the client never decides. GameTest the duplicate-socket path including across a world reload |
| **Version churn.** Mappings and API move every release | Pin versions in `gradle.properties`; `./gradlew genSources` before writing against an unfamiliar class; never trust a pre-1.21.5 tutorial |
| **Client/server split leaks.** A client-only class in common code crashes dedicated servers | Split source sets (CONVENTIONS.md §3); run `runGametest` (server-side) in CI, not just `runClient` |
| **Villager `Offers` regeneration** (§7.2) | Still a real risk, but now testable — GameTest it across level-up, restock and reload |