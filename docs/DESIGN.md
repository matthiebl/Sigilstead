# Heartstead — Design Wiki

> **Status:** reconstructed spec, v0.4 · **Target:** Minecraft Java 26.2 · **Fabric mod**, Java 25 · see §8
>
> This document is the **ground truth** for the pack. Implementation work should reference sections
> by number (e.g. "implement §5 Golem Core") rather than re-describing features. If reality and
> this document disagree, fix this document in the same change.
>
> **Every number and every recipe lives in §12.** The body sections describe *what a thing is and
> why*; §12 is the single place to tune *how much*. If you find a number in the body, it escaped —
> move it.

---

## 0. Design pillars

The pack exists to delete *technical Minecraft as a prerequisite* without deleting Minecraft.

1. **Reward the fun loop.** Exploring, fighting and building should pay. Grinding, AFKing and
   rectangle-farm-building should not be *required* to play efficiently.
2. **One currency, three sinks.** Everything hangs off a single found item — the **Sigil** (§1).
   Storage, farms and extra lives all draw on it, so no drop is ever dead to any player, and there
   is exactly one drop rate to tune.
3. **Convenience is earned, not free.** Early game is already easier than vanilla; the *scale* of
   convenience unlocks through structures and dimensions you have to travel to.
4. **Never punish a struggling player twice.** Floors, not spirals. See §6.
5. **Leave technical play intact.** Cores match a *small hand-built farm*, not a mega-farm. Players
   who enjoy building the real thing still get more out of it — they just aren't forced into it.
6. **Not an overhaul mod.** This doesn't redefine how you play Minecraft, it reshapes the tedium
   and rewards the above pillars.

Pillars are cited by number throughout — "pillar 5" means the fifth entry above.

---

## 1. The progression spine

The most important decision in the pack, and the one everything else is priced against.

**You find exactly one thing: the Sigil.** It is never craftable and has no sub-unit. Every system
in the pack is bought by spending Sigils, and every Sigil can become any of three things.

```
                         ┌─ Core Sigil    → farm cores      (§4, §5)
Sigil  ──── craft ───────┼─ Heart Sigil   → extra max health (§6)
(found only)             └─ Vault Sigil   → storage capacity, reach and access (§2)
```

| Item | Source | Role |
|---|---|---|
| **Sigil** | Structure chests, trial vaults, fishing treasure, any hostile mob at a low rate, and **bosses and mini-bosses at a high rate** — full table in §12.1. Never craftable | The only currency |
| **Core Sigil** | Sigil + iron + redstone (§12.2) | Primed and imprinted into a farm core (§4.1) |
| **Heart Sigil** | Sigil + gold + golden apple (§12.2) | Consumed for +1 max heart (§6) |
| **Vault Sigil** | Sigil + amethyst + ender eye (§12.2) | Vault capacity (§2.3), the Vault Pouch (§2.2), re-anchoring (§2.1) |
| **Overworld / Nether / End Vault Sigil** | Vault Sigil + a proof item from that dimension (§12.2) | Vault withdrawal reach into that dimension (§2.3) |

**Target acquisition rate:** ~1 Sigil per 20–25 min of active exploring at T1, ~1 per 10 min at T2
(§12.1). It is the single biggest dial in the pack (§10), and it is faster than it looks it should
be *because one Sigil now funds three systems instead of one* — see the note in §12.1.

### 1.1 The central tension

Every Sigil you pick up is a three-way choice, and you make it at the moment of crafting rather
than at the moment of finding. Spending one on an iron core is one fewer heart of health buffer and
one less step of storage. **That choice is the best mechanic in this design**, and unifying the
currency is what makes it continuous instead of occasional.

It also removes the failure mode of the old two-currency spine: a storage-focused player no longer
accumulates cores they don't want, and a farm-focused player no longer finds a storage item that is
dead in their hands. Nobody's drop is ever wasted, which is pillar 2 stated as a feeling.

**Guardrail: a death must never cost you your first core.** Core Sigil is deliberately the cheapest
of the three surcharges (§12.2), so a new player builds their first farm core before their first
extra heart. The pack helps you before it charges you. **Do not change the relative ordering of the
three surcharges without re-reading this paragraph** — it is the one constraint §12.2 must satisfy.

### 1.2 Tier definitions (used throughout)

These define the rough stage of the game, and are not unlockables or steps.

| Tier | Gate | Player state |
|---|---|---|
| **T1** | Pre-Nether → early Nether | Iron, copper, first villager, first dungeon |
| **T2** | Nether established | Diamonds, fortress/bastion, trial chambers, monument, first raid, **ancient city** |
| **T3** | End access | End city, netherite, elytra |

**Ancient city sits at T2, not T3** — the deep dark needs no End access, and two T2 unlocks depend
on Echo Shards: the Codex capacity upgrade (§3.3) and the **Overworld Vault Sigil** (§2.3), which is
what buys overworld-wide withdrawal. Getting there is still the hardest thing a T2 player does;
that's the point.

---

## 2. Storage — The Vault

A **world-level** virtual inventory, persisted via codec-backed `SavedData` (CONVENTIONS.md §4).
Storage is not backed by real containers, so there is no dupe risk from container NBT juggling.

**One Vault per world, shared by everyone on it.** Anyone can deposit, anyone can withdraw, and a
Sigil spent by one player advances the Vault for all. This matches §4.3 — cores are world state too
— so the "what does this look like on a server" question has one answer: **the Vault, its capacity
and its reach are all shared; what each player owns is which verbs they have.**

The trade-off is real and accepted: on a public server, a shared Vault is grief-able in the same way
a shared chest room is. That is a server-administration problem, not a design one, and the alternative
— a private Vault per player — makes the flagship system invisible to the people you play with.

### 2.0 The one rule the whole system hangs on

> **Deposit is free from anywhere. Withdrawal is ranged.**

Sending things *to* the Vault has no distance limit, no dimension limit, and costs nothing beyond a
T1 Satchel. Taking things *out* remotely is the capability that progresses, gated by the Anchor's
reach tier (§2.3).

That split is deliberate:

- **Deposit is the safe verb.** It cannot be used to cheese a fight or a build; the worst it does is
  spare you a walk home. It is also the verb that rewards long trips, which is pillar 1 — so it is
  granted at T1 and never taken away.
- **Withdrawal is the powerful verb.** Restocking blocks mid-build or arrows mid-fight from across
  the world is genuine power, so it is what the ladder sells.

One rule, applying identically to items (§2.2) and blocks (§2.1). There is no separate "field"
concept and no per-item range table; a thing either deposits (always allowed) or withdraws (needs
the reach tier covering where you're standing).

### 2.1 The Anchor

| Block | Tier | Role |
|---|---|---|
| **Vault Anchor** | T1 | Creates the Vault, holds its activation, capacity and reach, and opens its screen. **One per world** — a second one cannot be placed |
| **Linked Funnel** | T1 | Hopper-speed automation link. Feeds the Vault from any farm or core output, anywhere; or dispenses one configured item *out* of the Vault, subject to reach |

Recipes in §12.2.

**Activation.** A placed Anchor is dormant until activated, and a dormant Anchor's storage tab is
locked. Activation is where the anti-portability cost lives:

- **The first Anchor a world has ever had activates for free.** The Vault is a T1 system; nothing
  about creating it costs a Sigil. The world's `SavedData` records that the Vault has been created.
- **Every activation after that costs 1 Vault Sigil** (§12.3), consumed at the Anchor. Breaking an
  activated Anchor loses the activation.
- **Capacity and reach are never lost.** Vault Sigils spent on §2.3 stay spent — breaking the Anchor
  does not refund them and does not roll the world back. On a server, one player breaking the Anchor
  must not be able to delete everyone else's progress; it costs one Sigil to put back, and nothing
  more.
- **Anti-softlock:** if the player has no Vault Sigil but one is *in the Vault*, activation may
  consume that instead, and the screen offers it explicitly as a second button. A held Sigil is
  always preferred, so nobody silently drains the shared pool.

This is what stops the Anchor being a pocket Vault. Carrying it and re-placing it as you travel
costs a Vault Sigil every single time, which is strictly worse than the Satchel you already have,
while a genuine base move costs one Sigil once.

**The Anchor is hard to lose by accident.** It resists explosions, is in the `dragon_immune` and
`wither_immune` tags, and `PushReaction.BLOCK` stops pistons relocating it out from under the
world's position claim. Deliberately breaking it is the only way to lose an activation. The Anchor
*item* still drops normally — the punishment is the lost activation, not a lost block.

**Nothing linked works without an activated Anchor.** Funnels go inert, Satchels and Pouches refuse.

The Funnel is configured **in the world, not in a screen**: right-clicking it with an empty hand
cycles input/output (the mode is a block state, so it is visible and needs no sync packet), and
right-clicking with an item sets the output filter to that item's type. Nothing is consumed. Two
fields do not earn a screen handler. Throughput is `vault.funnel_items_per_transfer` (§12.7).

### 2.2 Access side (items)

| Tier | Item | Capability |
|---|---|---|
| T1 | **Bundle** | Vanilla Bundle behaviour replaced with a UI-based inventory of `bundle_slots` slots (§12.7) |
| T1 | **Satchel** | **Deposit, from anywhere, any dimension.** No withdrawal |
| T2 | **Vault Pouch** | Adds withdrawal (subject to §2.3 reach), search and sort |

Recipes in §12.2.

§1.1's tension survives in miniature: a Vault Sigil spent on your own Pouch is one not spent on the
world's capacity or reach — personal capability against shared infrastructure, and on a server that
is the sharpest version of the choice in the pack.

> **Balance flag — the Bundle override.** This is the one place the design overwrites a vanilla
> item's behaviour, which brushes against pillar 6. Nine slots is ~9× vanilla bundle capacity,
> granted at T1 for a vanilla-cost item, and the Bundle is also the Satchel's crafting base. Slot
> count is a config field; expect to lower it before it competes with the Vault itself.

> **Balance flag — free universal deposit.** §2.0 deletes the "walk home when your inventory is
> full" loop outright, at T1. That is the pitch, not a bug — but it is the largest single convenience
> the pack grants and it has never been played. It ships behind `deposit_requires_reach` (§12.7,
> default `false`) so it is a switch rather than a rewrite.

### 2.3 Capacity and reach — the two Vault ladders

Both are properties of the **world**, bought at the Anchor, shared by everyone. Sigils pool: on a
server, four players each bringing back two gets everyone to the next tier.

**Capacity** — how much fits. Costs plain **Vault Sigils**. Table in §12.3.

**Reach** — how far withdrawal works. Costs a **dimensional Vault Sigil**, which is a Vault Sigil
crafted with a proof item that only exists in the dimension it unlocks (§12.2). Table in §12.3.

The proof items are the **structural gate**, and they are the reason a craftable Vault Sigil does not
break §2's economy. Sigils are fungible, but a Ghast Tear is not: you cannot buy reach into a
dimension you have never visited. *Storage convenience scales with how much you have explored* — while still
letting a storage-focused player spend every Sigil they find.

Reach tiers are independent, not cumulative: they name the dimension they unlock, so an Anchor
placed in the Nether still needs the Nether tier for Nether-wide withdrawal. Local reach comes free
with activation and covers a small radius around the Anchor in whatever dimension it stands in.

### 2.4 UI shape

The Anchor's screen has **two tabs**:

1. **Anchor** — activation, capacity upgrades, reach upgrades, and what each costs. Always available,
   including when the Anchor is dormant; it is how a player learns what activation is. Shows the
   "use a Vault Sigil from the Vault" fallback (§2.1) only when it applies.

   **Upgrades are bought by putting the Sigil in a slot and confirming**, not by clicking a bare buy
   button — the same two-step shape as an enchanting table. There is **one socket per thing you can
   buy**: capacity, then one per reach tier, each with its own confirm button beneath it. Dropping a
   Sigil in only *arms* that socket; nothing is spent and nothing is bought until the confirm click,
   so a misdrop or a change of mind costs nothing — closing the screen or pulling the Sigil back out
   returns it untouched. The confirm button is disabled while its socket is empty, holds the wrong
   Sigil, or (for a reach tier) is already satisfied. An empty socket shows its Sigil ghosted so it
   says what it wants without a caption, and a reach tier already bought shows that Sigil dimmed and
   takes no more — §12.3's table drawn rather than described. Capacity never fills in, because §12.3
   gives it no ceiling. Activation stays a button, because its two cases — the world's free first
   activation and the from-Vault fallback — have no item for the player to place.

2. **Storage** — the Vault itself, **locked until activated**.
   A scrollable slot grid showing stored stacks with counts, a live search field, click-to-withdraw,
   shift-click-to-deposit, and sort. **This is the tab the screen opens on** — storage is what a
   player opens the Vault for, and tab 1 is somewhere they go deliberately. Pinned favourites and a
   recents page are still worth having.

Both tabs are drawn as **bookmark tabs** above a panel painted in vanilla's own greys — not blitted
from a chest texture, which is 176 wide and leaves no room beside a nine-column grid for the
scrollbar. Where a verb is unavailable — a Satchel, or a Pouch out of reach — the grid cells are
drawn as **disabled sockets** rather than captioned with an error, because the shape of a slot you
may not use is already a thing Minecraft says.

The grid behaves like a container even though it has no real slots behind it: **hovering shows the
item's tooltip and highlights the cell** (empty cells too — an empty cell is still somewhere you can
drop something), and **clicking with a stack on the cursor deposits it**, right-click for one. That
gesture is what makes the Vault feel like a chest rather than a read-only list.

The Vault Pouch opens tab 2 alone. **So does the Satchel**, with withdrawal refused — one screen, and
what differs between the two rungs is the verb, which is exactly how §2.2 frames the ladder. A
Satchel showing a greyed-out grid teaches the ladder; a second, deposit-only screen would not.

### 2.5 What the Vault refuses to store

**No containers holding contents.** Any item carrying a non-empty `container` or `bundle_contents`
component is rejected at the deposit path — filled shulker boxes, filled bundles, Satchels and
Pouches. Storing them would launder capacity (a shulker is 27 free types inside one type) and gives
the transfer path a nested case it does not need. Empty shulker boxes and empty bundles store
normally.

**No nesting, symmetrically.** The §2.2 Bundle applies the same rule to itself: it cannot contain
bundles, shulker boxes, Satchels or Pouches. One rule, both directions, no exponential container.

The visible cost is that hauling a filled shulker home does not deposit in one click. See
OPEN-QUESTIONS.md — unpacking on deposit is the known fix and is deliberately deferred until the
friction is felt rather than predicted.

### 2.6 Risk

The Vault holds thousands of items in persisted state. Any bug in the transfer path duplicates or
voids them, and players will not forgive either.

**Write the GameTest suite before the Vault itself** (CONVENTIONS.md §8): deposit N / withdraw N and
assert exact conservation, across a full inventory on withdraw, a capacity boundary, a mid-transfer
server shutdown, and concurrent access. All state goes through a versioned Codec (CONVENTIONS.md §4).

**Concurrent access is a first-class case, not an edge case** — a shared Vault means two players can
withdraw the same stack in the same tick. All mutation is server-side and serialised through the
`SavedData`; the screen handler sends intents, never results.

Additional cases this design adds: activation and de-activation across a world reload, a reach check
evaluated for a player in a dimension the world has not bought, and deposit-from-anywhere while the
Anchor's chunk is unloaded.

This is the single most testable part of the project, so there is no excuse for shipping it untested.

---

## 3. Enchantments

### 3.1 Abundance I–III *(treasure)*

Multiplies **non-ore bulk drops only**: stone, deepslate, sand, gravel, clay, netherrack,
terracotta, logs. Multipliers in §12.6.

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

| Step | Interaction | Effect |
|---|---|---|
| **Archive** | Place an enchanted book or enchanted item in the archive UI slot | Enchantment + level recorded permanently. The item is consumed |
| **Capacity** | — | Three tiers, bought with Echo Shard then Nether Star (§12.6) |
| **Tome** | Place an empty Tome in the empower slot | List of archived enchantments → **Sealed Tome** for one chosen enchantment |
| **Teach** | Enter the Sealed Tome in a librarian trade | Consumes the Tome + 1 emerald. That librarian now permanently sells that book |

Recipes in §12.2, capacities and prices in §12.6.

Two costs, don't confuse them: **teaching** costs a Sealed Tome plus one emerald, once per librarian.
**Buying** the taught book afterwards costs the fixed price, every time.

**Any archived enchantment can be sealed and taught, not just the four named in §12.6.** Those four
have hand-picked prices; everything else — any other vanilla enchantment, or a mod's — prices off a
rarity formula (§12.6) so Teach never dead-ends on an enchantment nobody thought to price by hand.

**The archive belongs to the player, not to the block** — a codec-backed player attachment
(CONVENTIONS.md §4). Any Codex you walk up to shows *your* archive and *your* capacity, so the block
is a workbench rather than a container, and there is nothing to lose if someone breaks it. This is
the one system that stays personal while the Vault (§2) and the core registry (§4.3) are world state,
and deliberately so: what you have found and archived is a record of your own play, and two players
on a server should each have to earn their own Mending.

The capacity upgrades are therefore consumed **per player**, at any Codex.

**Why this balances well:** you permanently lose the Mending book you found in order to make Mending
permanently purchasable. One lucky exploration payoff converts into lasting access — which is exactly
the vanilla trade-hall reroll grind deleted and replaced with exploring.

Implementation traps live in §7.2.

---

## 4. Farm replacement — Cores

Craft a **Core Sigil**, attune it by *playing*, then socket it into housing. The Core Sigil is one of
the three children of the Sigil (§1) and is the cheapest of them (§1.1); recipe in §12.2.

### 4.1 Attunement — rewards the activity it replaces

Two steps, the same shape for every core.

**Step 1 — Prime (crafting).** A shaped recipe combines the Core Sigil with family reagents and
yields a **Primed Core**. The *family* is now fixed; the *target* is not. The stack carries a
`heartstead:attunement` component — `{ family, target: null, progress: 0 }`.

**Step 2 — Imprint (playing).** Hold the Primed Core and do the thing the core will replace. The
first qualifying event writes the target; every later one of the same target increments progress. At
the threshold the stack converts into the finished Core, named for what it learned —
*Verdant Core (Wheat)*.

| Family | Imprint action | Target locked by |
|---|---|---|
| **Soul Core** | Kill a hostile mob | First mob type killed |
| **Verdant Core** | Harvest a mature crop | First crop harvested |
| **Pastoral Core** | Breed two animals | First species bred |
| **Lithic Core** | Mine a stone or earth block | First block type mined |

Prime recipes and imprint thresholds are in §12.4. The thresholds are one session of the activity,
not a grind — a few minutes in a dark room, one small field, the pen you were going to build anyway,
a stack of mining.

**What each family may lock onto is an explicit allow-list, and it is data.** Four tags under
`data/heartstead/tags/`, one per family — `soul_imprintable` and `pastoral_imprintable` under
`entity_type/`, `verdant_imprintable` and `lithic_imprintable` under `block/`. Editing a target list
is a JSON edit, not a recompile, and the defaults are in §12.4.

They are allow-lists rather than "any hostile mob" because the category rule is wrong at the edges in
ways that break the economy: it includes the Ender Dragon, the Wither and every §12.1 mini-boss. An
Evoker Soul Core would produce a Totem of Undying every 20 seconds against §12.5's 1.6 totems/hour
for the core actually designed to make them. **The exclusions are the point of the list**, so they
are written down in §12.4 rather than inferred from a mob's category.

**How it behaves in the hand:**

- **A target only locks from the hand.** An un-attuned Primed Core has to be in your **main hand or
  offhand** to take a target — sitting in the inventory it is inert. Deliberate: you should never
  discover that a core in your backpack quietly became a chicken core. If un-attuned cores of the
  same family are in both hands, the main hand takes the lock.
- **The lock happens on the first qualifying event, not at craft time.** Kill a zombie holding a
  Primed Soul Core and it is a zombie core from that moment. The tooltip updates on that first
  event — `Attuning: Zombie 1/16` — so the lock is never a surprise you discover at 16/16.
- **Once a core has a target, it counts from anywhere in the inventory.** Attuned Primed Cores on
  *different* targets advance in parallel — carry a zombie Soul Core, a wheat Verdant Core and a
  deepslate Lithic Core and one afternoon of ordinary play feeds all three. Nothing is queued and
  nothing waits its turn.
- **One qualifying event advances exactly one core.** Two zombie Soul Cores in the same inventory do
  *not* both tick off one kill; the first matching core takes the credit and that is the end of the
  event. Otherwise a second copy of a core would cost half as much attunement as the first, for a
  target §4.3 will only ever let you run one of. Search order is main hand, then offhand, then the
  rest of the inventory — the core you are holding wins, because that is the only order a player can
  predict without opening their inventory. An event that advances a core does not also lock an
  un-attuned one.
- **Progress lives on the stack.** Drop it, chest it, die with it — the counter is a data component,
  so it survives everything a stack survives. There is no player-side counter to desync.
- **Wrong-target events are inert.** Mining dirt with a stone-locked Lithic Core does nothing at all.
  No penalty, no reset — pillar 4.
- **The tooltip is the entire UI.** No screen, no block, no ritual. That is the point: attunement is
  something you finish without noticing you were doing it.
- **Priming is reversible; the target is not.** A Primed Core at any progress reverts to a Core Sigil
  in the crafting grid — the reagents are not refunded, and neither is the progress. What is
  irreversible is the *aim*: a core cannot be re-targeted, so changing your mind about a zombie core
  means scrapping it and starting the imprint again. That is what gives the choice weight. Once the
  core has finished it is no longer a Primed Core and no longer reverts.

### 4.2 Housing

Socket the finished Core into its matching housing block. Cores can be pulled back out and
re-socketed elsewhere; they never degrade. Socketing is controlled by placing the locked core
within the Housing's GUI screen.

| Block | Family |
|---|---|
| **Soul Cage** | Soul Core |
| **Verdant Planter** | Verdant Core |
| **Paddock** | Pastoral Core |
| **Quarry Node** | Lithic Core |

Recipes and base rates in §12.4. Housings are deliberately cheap — the Core is the cost, and it
already ate a Sigil.

**The housing supplies the family; the core supplies the rate and the loot table.** A §5 core
overrides the §4.2 base rate with its own. That is why eleven classic farm replacements need no new
blocks.

Output goes to small internal storage — **or straight into your Vault if a Linked Funnel is below.**
That is the integration payoff, and the moment the two flagship systems click together.

**Cores also accrue experience**, alongside items and on the same clock — every production cycle
grants a fixed amount of XP, sized off what the vanilla action the core replaces would actually award
a player doing it themselves (§12.4, §12.5). It scales with tier exactly the way item yield does:
tier only changes cycles per hour, not what one cycle is worth. XP is not blocked by a full item
buffer — it isn't an item, and has nowhere to overflow. It collects the same way the buffer does:
accrue while away, then take it explicitly — a **Collect** button in the housing screen next to a
running total, not orbs dropped into the world, so an unloaded chunk never has to spawn anything to
pay out what it owes.

**No core ever yields a Sigil.** Not at any housing, any tier, or through any loot table it inherits.
This is not a balance preference, it is the rule that keeps the economy from bootstrapping: cores
are bought with Sigils, so a core that produced Sigils would produce cores, and the curve goes
exponential. It needs a GameTest, because it is exactly the sort of invariant a loot-table refactor
reintroduces silently. §12.1 carries the matching exclusion on the Wither, whose summoning item *is*
core-producible.

**A consequence of how that rule is enforced.** A core rolls its inherited loot table with no killing
player in the context — that is *why* no Sigil pool can pass. It follows that any vanilla drop gated
on `killed_by_player` yields nothing to a core at all: an Enderman Soul Core produces no pearls and a
Blaze one no rods. That is correct behaviour for a farm that runs while you are asleep, and it is not
a bug to work around. It does mean the §5 cores whose whole point is such a drop (Ender, Wither Skull,
Shulker, Tidal) **must ship their own loot table** rather than inherit the mob's — which §4.2 already
allows for: "the core supplies the rate and the loot table."

**Cores accrue while unloaded, and must never require a chunkloader.** A housing block stores the
world-time stamp of its last settled yield. On chunk load — and on any interaction — it settles the
whole elapsed delta in one calculation rather than ticking. Nothing about a core rewards keeping a
chunk alive, and walking away costs you nothing.

The two bugs this design invites, and both are GameTestable (CONVENTIONS.md §8):

- **Double-counting** — settling on chunk load *and* ticking while loaded. Settle from the timestamp
  and update the timestamp in the same operation; never accrue from two clocks.
- **Unbounded catch-up** — a chunk untouched for forty in-game days dumps its whole backlog at once.
  Cap the settled delta at a configurable ceiling (§12.7) so internal storage limits are meaningful
  and the first chunk load after a long absence isn't a jackpot.

### 4.3 Rate tiering

Upgrade a locked core. Costs and multipliers in §12.4.

**Tiering is the only way to scale. Building more of the same core is not.**

**One active core per target, per world.** A zombie Soul Core cannot be socketed while another zombie
Soul Core is active anywhere in the world — the socket is **refused at the point of insertion**, with
the reason shown to the player. Nothing is ever placed and then silently switched off, so there is no
dormancy, no wake-up ordering, and no "which of my four does the game pick" question to answer.

"At the point of insertion" is literal and load-bearing: the housing's core slot **will not accept**
the duplicate, and the message explains why. It is not accepted and then handed back. A refusal that
moves the item runs inside vanilla's click handling, where shift-click retries as long as it is
making progress — and handing the core back counts as progress.

Breadth is free and encouraged: zombie *and* skeleton, wheat *and* carrot, cobble *and* deepslate are
all fine. It is only the duplicate that is refused. Depth costs the tier resources.

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
not *mega farm* (pillar 5). **All eleven, with prime recipes, imprint conditions, rates and the
comparison against real farms, are in §12.5.**

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

The §4.2 no-Sigils rule applies to every core here without exception, including the boss-adjacent
ones. A Guardian Core rolls a guardian's loot table minus Sigils; killing an Elder Guardian yourself
is what pays (§12.1).

---

## 6. Lives and death

`keepInventory true`, XP kept. **The entire cost of dying moves to health.**

- Consume a **Heart Sigil** → +1 max heart via a `max_health` attribute modifier.
- There is a cap, a per-death loss and a **floor below which death never takes you** — pillar 4.
  Numbers and the harder-mode toggles are in §12.6.
- Recovery: consume more Heart Sigils. The loop is *go explore, find Sigils, come back stronger.*

The Heart Sigil is one of the three children of the Sigil (§1) and costs more than the Core Sigil by
design — see the §1.1 guardrail.

Implementation: a `max_health` attribute modifier applied from a player attachment holding the
current heart count, with the death hook on `ServerPlayerEvents.AFTER_RESPAWN` (or equivalent —
confirm the 26.2 event name). The attachment must survive respawn and dimension change, which is
exactly what attachments are for and what a scoreboard would have got subtly wrong.

---

## 7. Other

### 7.1 Artisan's Table (T1 block) / Artisan's Kit (T2 portable)

A real 3×3 crafting grid that draws from your inventory **and the Vault**, with the ordinary vanilla
recipe book on the side. Recipes count as craftable when the ingredients are in the Vault, and
clicking one pulls what's missing out of it. Recipes in §12.2.

Pulling ingredients out of the Vault **is a withdrawal**, so it obeys §2.0: the Table at your base is
always in local reach, but the Artisan's Kit used out in the world needs the reach tier covering
where you are standing. That falls out of the rule for free and needs no separate concept.

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

### 7.3 The discovery path — advancements as the tutorial

**The problem this solves.** Every system in §§2–6 is invisible on the first day. A player who has
never read this wiki gets a Sigil from a chest and has no way to learn that it buys three different
things, that a Vault exists, or that a farm can be replaced by a core. Nothing in the pack tells
them. That is a discovery failure, not a balance one, and it is what this section fixes.

**The tree is the tutorial.** Heartstead ships a single advancement tab that steps a player through
the pack in the order the systems actually unlock, with each advancement's description saying what
the thing *is* rather than congratulating them for reaching it.

#### The bounty cut still stands

An earlier draft specified a "bounty" system: three advancement chains — explore, fight, build —
paying reward crates. **Cut 2026-08-01, and still cut.** The reasoning is the test every version of
this section has to pass:

Bounties were a *second* reward channel bolted alongside the first. §1 already pays you for exploring,
fighting and building — that is the entire point of the Sigil. Paying twice for the same activity
doesn't reinforce the loop, it obscures how well the loop works, and it makes the pack's core economy
impossible to tune honestly. **This objection is strictly stronger under the unified currency**: with
one item feeding three systems, a second source of it moves every dial in the pack at once.

**The discovery path passes that test because it pays nothing.** Every advancement in it grants
recognition and — where it applies — a *recipe book unlock*, which is information the player already
had the right to. Zero Sigils, zero items, zero XP. `rewards.loot` and `rewards.experience` do not
appear anywhere in `data/heartstead/advancement/`, and adding them reopens the argument above.

That is also why this was buildable before playtesting, against the old "design them last" gate: the
gate existed because a *reward* tree cannot be tuned until the economy is played. A tree that pays
nothing has nothing to tune, and the systems it describes have stopped moving — Phases 0–4 have all
shipped. What playtesting may still change is the *wording and ordering*, which is cheap.

#### Two layers

**Layer 1 — the visible tree** (`data/heartstead/advancement/*.json`). One tab, rooted at
`heartstead:root`, which is granted by `minecraft:tick`: **the tab is there from the first second of
a new world**, before the player owns anything. A root gated on an item would hide the entire
tutorial from exactly the player who needs it. The root's description is the pack's one-paragraph
pitch. It sets `show_toast: false` and `announce_to_chat: false`, so appearing costs no noise.

Four chains hang off it, matching the four systems:

| Chain | From | Steps |
|---|---|---|
| **Spine** | `root` | first Sigil → the three surcharges (§1.1), one branch each |
| **Vault** | `vault_sigil` | Anchor → activation → deposit → Satchel → Pouch → withdrawal → capacity → reach ×3 → Funnel |
| **Cores** | `core_sigil` | primed core → attunement → socket → first yield → tier III; the eleven (§5) as a side branch |
| **Codex** | `root` | Codex → archive → Tome → teach a librarian; the two §3 enchantments as a side branch |
| **Lives** | `heart_sigil` | consume one → the 20-heart cap (§6) |

The Codex chain hangs off the root rather than off a Sigil because §3.3's block costs no Sigil — the
tree's shape should say which systems the currency gates and which it does not.

Three advancements are `challenge`-type, and they are the three ends of the longest ladders: End-wide
reach (§2.3), a tier III core (§4.3), and the 20-heart cap (§6). Everything else is `task`, except
the one-of-each-Sigil `goal` that names §1.1's tension explicitly.

**Layer 2 — recipe unlocks** (`data/heartstead/advancement/recipes/*.json`), hidden, parented to a
display-less `heartstead:recipes/root`, exactly as vanilla does it. This is not decoration: a recipe
with no advancement granting it **never appears in the recipe book at all**, which is the state every
Heartstead recipe shipped in through Phase 4. This layer is what makes the §7.1 Artisan's Table
useful for Heartstead's own content.

Unlocks are staged so the book teaches rather than dumps:

| Holding | Unlocks |
|---|---|
| `sigil` | the three surcharges, and the Codex |
| `vault_sigil` | Anchor, Satchel, Funnel, the three dimensional Sigils |
| `satchel` | Vault Pouch |
| `core_sigil` | the four primed cores and their reverts |
| a primed core | that family's housing, and the §5 eleven of that family |
| `codex` | Tome |

#### Constraints

- **Data-driven** (CLAUDE.md rule 5). The tree is JSON, emitted by `tools/gen_advancements.py` — the
  generator is the artifact and the JSON is build output, per CONVENTIONS.md §6. It also emits the
  `advancements.heartstead.*` lang block, so titles and descriptions live in one place.
- **New criteria, not new systems.** Heartstead events that vanilla cannot observe are exposed as
  registered criterion triggers under `heartstead:` (`vault_activated`, `vault_deposited`,
  `vault_withdrew`, `vault_upgraded`, `core_attuned`, `core_socketed`, `core_yield_collected`,
  `codex_archived`, `villager_taught`, `heart_level`). They fire from the existing server-side call
  sites and hold no state of their own.
- **No second book.** A custom guidebook screen was considered and deferred, not rejected — see
  OPEN-QUESTIONS.md. The advancement tooltip already renders a title, an icon and a description in
  vanilla's own frame, which is most of what a guide page is, and it needs no new UI.

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
| **0** | Item registration + Sigil economy + loot tables | Mostly JSON, plus real item registration. Ship this and play it before adding reward systems on top (§7.3) |
| **0.5** | Spike: §7.2 villager persistence | Still the riskiest remaining mechanic — but now ships with a GameTest instead of a manual checklist |
| **1** | Lives system (§6) | Small, self-contained, high impact. First use of player attachments |
| **2** | The Vault (§2) | Biggest lift. **Write the conservation GameTests first** (§2.6). First real blocks and screen handlers — establish those patterns here |
| **3** | Codex (§3.3) + Artisan's Table (§7.1) + librarian teaching | The most novel feature, and the one most likely to attract users. Reuses Phase 2's block and screen patterns |
| **4** | Cores (§4, incl. §5 classic farm replacements) | Needs the Vault for its best version |
| **5** | The discovery path (§7.3) | Last, because it describes the other four. Pays nothing, so it needed no economy data to design |

---

## 10. Tuning dials, ranked by impact

1. **Sigil drop rates** (§12.1) — controls the pace of literally everything, and now does so for
   three systems at once rather than two. A 20% change here is a 20% change to storage, farms and
   survivability simultaneously.
2. **The three surcharges** (§12.2) — the relative price of Core / Heart / Vault Sigil *is* the
   §1.1 tension. Their ordering is constrained (§1.1); their spacing is free and unvalidated.
3. **Core base rates and `core_rate_multiplier`** (§12.5) — aim at *small hand-built farm*, not
   mega-farm. With one core per target (§4.3), tier III is the whole scaling curve.
4. **Vault capacity cost per tier** (§12.3) — controls how long storage stays a puzzle.
5. **Attunement thresholds** (§12.4) — the difference between "earned while playing" and "a grind".
6. **Fixed librarian prices** (§12.6) — too cheap and enchanting collapses.
7. **Bundle slot count** (§12.7) — currently 9; the largest unvalidated T1 buff in the pack.

**Ship `core_rate_multiplier` at 1.0, but expect to lower it after playtesting.** Everyone
underestimates compounding passive income.

---

## 11. Known risks

| Risk | Mitigation |
|---|---|
| **Item loss / duplication** in the Vault | GameTest conservation suite written *before* the Vault (§2.6). Versioned Codecs for all persisted state |
| **Economy bootstrapping.** A Sigil source that a core can reach makes the currency self-producing | §4.2's absolute no-Sigils-from-cores rule, plus §12.1's Wither exclusion. GameTest it — socket a core, run it, assert zero Sigils ever appear in its output |
| **One currency, three sinks means one mistake, three symptoms** | A wrong Sigil rate no longer under-rewards one system, it distorts all three at once and they mask each other. Instrument acquisition *and* each spend path separately in the dev world, or you will not be able to tell which dial is wrong |
| **Compounding passive income** | §4.3 caps cores at one per target per world, so breadth is bounded by what's been attuned and depth by tier cost — ship `core_rate_multiplier` at 1.0 and plan to lower |
| **Offline accrual double-counting or backlog dumps** (§4.2) | Settle from a single stored timestamp, never from two clocks; cap the settled delta. GameTest it: advance the clock, unload and reload, assert the yield matches the formula exactly |
| **Socket refusal desync** (§4.3) | The active-core registry is world `SavedData` and the refusal is server-authoritative; the client never decides. GameTest the duplicate-socket path including across a world reload |
| **Vault softlock.** An activated Anchor broken with no Sigil anywhere | §2.1's three guards: the block resists explosions and pistons, the first activation is free, and a Sigil sitting in the Vault can pay for re-activation. If all three fail the player explores for one Sigil — recoverable, never permanent |
| **Version churn.** Mappings and API move every release | Pin versions in `gradle.properties`; `./gradlew genSources` before writing against an unfamiliar class; never trust a pre-1.21.5 tutorial |
| **Client/server split leaks.** A client-only class in common code crashes dedicated servers | Split source sets (CONVENTIONS.md §3); run `runGametest` (server-side) in CI, not just `runClient` |
| **Villager `Offers` regeneration** (§7.2) | Still a real risk, but now testable — GameTest it across level-up, restock and reload |

---

## 12. Tuning tables

**Every number and every recipe in the pack lives here.** The sections above describe what things are
and why; this section is the only place to change what they cost. All of it is config (CONVENTIONS.md
§5) or data-driven JSON (`src/main/resources/data/heartstead/`) — none of it is a Java literal.

**None of these numbers has been played.** They are internally consistent and reasoned; that is all.
See OPEN-QUESTIONS.md.

### 12.1 Sigil sources

The one drop table that matters. Target: **~1 Sigil per 20–25 min of active exploring at T1, ~1 per
10 min at T2.**

**Chests**

| Source | Chance | Amount |
|---|---|---|
| Dungeon / mineshaft / desert temple / jungle temple / shipwreck treasure | 12% | 1 |
| Stronghold library / altar room | 25% | 1 |
| Trial chamber vault (normal) | 20% | 1 |
| Trial chamber vault (ominous) | 50% | 1 |
| Bastion treasure | 50% | 1–2 |
| Ancient city | 60% | 1–2 |
| End city treasure | 50% | 1–2 |
| Fishing treasure | 5% | 1 |

**Mobs**

| Source | Chance | Amount |
|---|---|---|
| Any hostile mob | 0.15% | 1 |
| Ravager | 25% | 1 |
| Evoker | 60% | 1 |
| Elder Guardian | 60% | 1 |
| Warden | 100% | 2 |
| Ender Dragon — first kill | 100% | 5 |
| Ender Dragon — each respawn | 100% | 1 |
| **Wither** | **never** | — |

**Two exclusions, both load-bearing (§4.2):**

- **No mob killed by a core ever drops a Sigil**, regardless of type. The 0.15% hostile-mob rate and
  every boss rate above apply to player kills only.
- **The Wither is not a Sigil source at all**, even on a player kill. The §5 Wither Skull Core
  produces skulls passively, so skulls → Wither → Sigils would be the bootstrap loop wearing a
  disguise. The Wither already pays a Nether Star; it does not need to pay twice.

Boss and mini-boss rates are deliberately high — north of 50% — because those fights are the purest
expression of pillar 1 and none of them is passively farmable under the two rules above.

**How the two exclusions are actually enforced.** Every mob pool carries vanilla's
`killed_by_player` condition. A core settles its yield by rolling the loot table of the thing it
replaces, with no killing player in the loot context, so exclusion 1 holds structurally rather than
by Phase 4 remembering a rule — *and Phase 4 must never supply a player parameter to a core's roll.*
Exclusion 2 is a hard-coded deny-list, with no config field to switch the Wither back on.

**Which vanilla tables each chest row maps to.** The stronghold row is the library and crossing
(altar room) tables only — corridor chests are excluded so one stronghold does not roll a dozen
times. The shipwreck row is the treasure chest only, not supply or map. The trial chamber rows are
the two top-level vault reward tables. The Ender Dragon ships an *empty* loot table and its
first-kill/respawn split is world state, so its Sigils are paid on the death event instead.

### 12.2 Recipes

**The spine (§1).** The ordering constraint from §1.1 — Core cheapest — is satisfied by these three
and must survive any retuning.

| Result | Ingredients | Notes |
|---|---|---|
| **Core Sigil** | Sigil + 4 Iron Ingot + 4 Redstone | Cheapest of the three, per the §1.1 guardrail |
| **Heart Sigil** | Sigil + Golden Apple | Middle |
| **Vault Sigil** | Sigil + 4 Amethyst Shard + Ender Eye | Dearest; the Ender Eye puts it at late T1 / early T2 |
| **Overworld Vault Sigil** | Vault Sigil + Echo Shard | Proof item: ancient city |
| **Nether Vault Sigil** | Vault Sigil + Ghast Tear + Blaze Powder | Proof item: the Nether. Not producible by any §5 core |
| **End Vault Sigil** | Vault Sigil + Dragon's Breath | Proof item: the End. Not producible by any §5 core |

```
I R I      I = Iron Ingot   │  S A        (shapeless)       │  S A A A A E   (shapeless)
R S R      R = Redstone     │             A = Golden Apple  │                A = Amethyst Shard
I R I      S = Sigil        │             S = Sigil         │                E = Ender Eye
           → Core Sigil     │             → Heart Sigil     │                S = Sigil
                            │                               │                → Vault Sigil
```

Only the Core Sigil is shaped. Heart Sigil, Vault Sigil and the three dimensional Vault Sigils are
all shapeless — Vault Sigil in particular has six ingredients and no arrangement worth memorising,
and the earlier "Ender Eye in any free slot" phrasing was a shapeless recipe already.

The three dimensional Vault Sigils are shapeless: Vault Sigil + proof item(s).

**The Vault (§2).**

| Result | Ingredients |
|---|---|
| **Vault Anchor** | Barrel + 4 Amethyst Shard + 2 Ender Pearl + 2 Copper Ingot |
| **Linked Funnel** | Hopper + Amethyst Shard + 2 Copper Ingot + Ender Pearl |
| **Satchel** | Bundle + Ender Pearl + 2 Leather |
| **Vault Pouch** | Satchel + Vault Sigil + Ender Eye |

```
AEA        A = Amethyst Shard     │   A        A = Amethyst Shard
CBC        E = Ender Pearl        │  CHC       C = Copper Ingot
AEA        C = Copper Ingot       │   E        H = Hopper
           B = Barrel             │            E = Ender Pearl
           → Vault Anchor         │            → Linked Funnel
```

**Everything else.**

| Result | Ingredients |
|---|---|
| **Codex** | 5 Bookshelf + Lectern + Amethyst Shard |
| **Tome** | Book + Lapis Lazuli + Iron Ingot (shapeless) |
| **Artisan's Table** | Crafting Table + 4 Diamond + Vault Sigil + Ender Eye + 2 Shulker Shell |
| **Artisan's Kit** | Artisan's Table + 2 Leather + 2 Vault Sigil |

```
BBB        B = Bookshelf
BLB        L = Lectern
 A         A = Amethyst Shard    → Codex

DVD        D = Diamond        S = Shulker Shell
SCS        V = Vault Sigil    C = Crafting Table
DED        E = Ender Eye      → Artisan's Table

 V         V = Vault Sigil
LAL        L = Leather
 V         A = Artisan's Table   → Artisan's Kit
```

Artisan's Table and Kit are purposelly locked behind end game and Vault Sigils to
restrict ease of access until the end of the game. Crafting from the Vault is supposed
to feel like you have unlocked the final boss of storage management.

Core prime recipes are in §12.4 (families) and §12.5 (the eleven classic replacements); housing
recipes are in §12.4.

### 12.3 Vault capacity, reach and activation

**Activation (§2.1)**

| Event | Cost |
|---|---|
| First activation in a world, ever | free |
| Every activation after that | 1 Vault Sigil |
| Capacity and reach already bought | never lost, never refunded |

**Capacity — distinct item types and depth per type**

| Vault tier | Vault Sigils (cumulative) | Distinct types | Depth per type |
|---|---|---|---|
| **T1** — on activation | 0 | 27 | 10 stacks |
| **T2** | 3 | 108 | 64 stacks |
| **T3** | 8 | 512 | 2048 stacks (effectively unbounded) |

Every Vault Sigil spent on capacity gives **+27 distinct types immediately**, whether or not it
crosses a tier threshold — the table above is the floor each tier guarantees, not a gate that holds
distinct types flat until the next milestone. Depth per type is different: it only moves at a tier
threshold, jumping straight to the table's value (10 → 64 → 2048) rather than climbing gradually.
Past T3, each further Vault Sigil buys **+27 distinct types** and nothing else, continuing the same
per-Sigil rate.

**Reach — where withdrawal works (§2.0, §2.3).** Deposit ignores this table entirely.

| Reach tier | Cost | Grants withdrawal |
|---|---|---|
| **Local** | free, with activation | Within 5×5 chunks of the Anchor, in the Anchor's own dimension |
| **Overworld** | 1 Overworld Vault Sigil | Anywhere in the Overworld |
| **Nether** | 1 Nether Vault Sigil | Anywhere in the Nether |
| **End** | 1 End Vault Sigil | Anywhere in the End |

Tiers are independent, not cumulative — each names the dimension it unlocks. Local reach always
applies, wherever the Anchor stands.

### 12.4 Core families — prime recipes, thresholds, housings and base rates

**Prime recipes and imprint thresholds (§4.1)** — each also takes the Core Sigil.

| Family | Prime reagents | Imprint action | Threshold |
|---|---|---|---|
| **Soul Core** | 4 Rotten Flesh + 4 Bone | Kill a hostile mob | 16 kills of the first type |
| **Verdant Core** | 4 Bone Meal + 4 Moss Block | Harvest a mature crop | 32 of the first crop |
| **Pastoral Core** | 4 Hay Bale + 4 Leather | Breed two animals | 8 breeds of the first species |
| **Lithic Core** | 4 Stone + 4 Flint | Mine a stone or earth block | 64 of the first block type |

**Imprint allow-lists (§4.1)** — four tags under `data/heartstead/tags/`, editable as JSON.

| Family | Tag | Default contents |
|---|---|---|
| **Soul** | `entity_type/soul_imprintable` | Every hostile mob **except** the exclusions below |
| **Pastoral** | `entity_type/pastoral_imprintable` | Every breedable vanilla animal. Not villagers (an unspecced emerald farm), not the undead mounts |
| **Verdant** | `block/verdant_imprintable` | Wheat, carrots, potatoes, beetroots, nether wart, torchflower, pitcher crop. Maturity is checked in code — the tag says *which* crop, the block's `age` says *when* |
| **Lithic** | `block/lithic_imprintable` | Overworld and Nether base stone, dirt, sand, terracotta, gravel, clay, calcite, tuff, end stone |

**Excluded from `soul_imprintable`, and why.** Each drops something unconditionally — no
`killed_by_player` gate — so a core targeting it farms that drop at the housing's full rate:

| Excluded | Drops | Against |
|---|---|---|
| Ender Dragon, Wither | — | The two boss fights; §12.1 already keeps both out of the Sigil table |
| **Evoker** | Totem of Undying | §12.5's Ominous core: 1.6 totems/hr. An Evoker Soul Cage is ~180/hr |
| **Elder Guardian** | Wet sponge, armour trim template | §12.5's Guardian core, which pays neither |
| **Warden** | Sculk catalyst | Nothing else in the pack produces one |
| **Ravager** | Saddle | Nothing else in the pack produces one |

Illusioner and Giant are also absent: neither spawns naturally, so neither is a thing a player could
attune to by playing. Adding any of these back is one line of JSON if playtesting disagrees.

**Housings (§4.2)**

| Block | Recipe | Base rate, tier I | XP/cycle |
|---|---|---|---|
| **Soul Cage** | 9 Iron Bars | 1 loot roll / 20s | 4 |
| **Verdant Planter** | Composter + 4 Dirt around it | 1 harvest / 30s | 0 |
| **Paddock** | 4 Oak Fence + 4 Hay | 1 yield / 45s | 3 |
| **Quarry Node** | 8 Deepslate Bricks + Amethyst Shard | 8 blocks / 20s | 0 |

XP/cycle applies to a generic (non-§5) core socketed in that housing — a zombie Soul Core, a wheat
Verdant Core. It is sized off what the vanilla action itself awards: an average hostile-mob kill (4)
and a breed (3) both grant XP in vanilla; harvesting a crop and mining plain stone, dirt or sand do
not, so those two housings' generic baseline is 0. §5's classic cores override this the same way they
override the base rate and loot table — see §12.5.

**Rate tiering (§4.3)**

| Tier | Cost | Effect |
|---|---|---|
| I | base | Roughly a small hand-built farm |
| II | 4 Blaze Powder + 4 Diamonds | 2.5× rate |
| III | 4 Netherite Scrap | 6× rate |

Offline accrual cap: `core_accrual_cap_hours`, default 24 in-game hours of yield (§12.7).

### 12.5 Classic farm replacements — the eleven

Each also takes a Core Sigil. All rates are multiplied by `core_rate_multiplier` (§12.7).

| Core | Replaces | Tier | Housing | Prime reagents | Imprint | Base rate, tier I | XP/cycle |
|---|---|---|---|---|---|---|---|
| **Golem** | Iron farm | T1 | Soul Cage | 4 Iron Block + Poppy | *Counted:* build 4 iron golems while carrying it | 1 ingot / 40s — **90/hr** | 0 |
| **Ominous** | Raid farm | T2 | Soul Cage | 4 Emerald Block + Ominous Bottle | *Milestone:* complete a level-5 ominous raid | 1 roll / 90s, totem 4% — **1.6 totems/hr** | 8 |
| **Barter** | Piglin barter farm | T2 | Soul Cage | 4 Gold Block + Crying Obsidian | *Milestone:* barter with a piglin | 1 barter / 30s — **120/hr** | 0 |
| **Guardian** | Guardian farm | T2 | Soul Cage | 4 Prismarine Brick + Sponge | *Milestone:* kill an Elder Guardian | 1 roll / 25s — **144/hr** | 10 |
| **Tidal** | Drowned / trident farm | T2 | Soul Cage | 4 Prismarine + Nautilus Shell | *Counted:* kill 16 drowned | 1 roll / 30s, trident 0.5% — **0.6 tridents/hr** | 5 |
| **Wither Skull** | Skull farm | T2 | Soul Cage | 4 Nether Brick + Wither Skeleton Skull | *Milestone:* kill a wither skeleton in a fortress | 1 roll / 45s, skull 2% — **1.6 skulls/hr** | 5 |
| **Ender** | Enderman XP farm | T3 | Soul Cage | 4 Obsidian + 4 Ender Pearl | *Milestone:* kill the Ender Dragon | 1 roll / 20s + XP — **180 pearls/hr** | 5 |
| **Shulker** | Shulker shell farm | T3 | Soul Cage | 4 Purpur Block + Shulker Shell | *Milestone:* kill a shulker in an End city | **10 shells/hr** | 5 |
| **Slime** | Slime farm | T1 | Soul Cage | 4 Slime Block + Moss Block | *Counted:* kill 16 slimes | 1 slimeball / 30s — **120/hr** | 3 |
| **Apiary** | Honey farm | T1 | Paddock | 4 Honeycomb + 4 Flowers | *Counted:* breed 8 bees | 1 comb or bottle / 60s — **60/hr** | 3 |
| **Geode** | Amethyst farm | T1 | Quarry Node | 4 Amethyst Block + Calcite | *Counted:* mine 32 amethyst clusters | 4 shards / 60s — **240/hr** | 0 |

XP/cycle is sized off the vanilla action's own XP award (an Enderman kill, a Guardian kill, a breed),
flagged for playtesting the same way the rates above already are — see §4.2.

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

### 12.6 Enchantments, Codex and lives

**Abundance (§3.1)** — +1 drop per level at 60% chance each: ×1.6 / ×2.2 / ×2.8 average.

**Codex capacity (§3.3)**, consumed per player:

| Tier | Cost | Archived enchantments |
|---|---|---|
| T1 | — | 8 |
| T2 | Echo Shard | 16 |
| T3 | Nether Star | unlimited |

**Fixed librarian prices (§3.3)**, no rerolling. Discounts and Hero of the Village still apply.

| Enchantment | Emeralds |
|---|---|
| Mending | 32 |
| Efficiency V | 28 |
| Silk Touch | 24 |
| Feather Falling IV | 10 |

**Everything else teachable (§3.3)** prices at `3 emeralds × level × rarity multiplier`, clamped to
4–64. Rarity multiplier is vanilla's own anvil-cost rarity (1 common, 2 uncommon, 4 rare, 8 very
rare) — the same number vanilla already assigns every enchantment, so nothing new needs defining
per enchantment. A common level-1 enchantment floors at 4 emeralds; a very-rare one at max level
usually ceilings at 64.

**Lives (§6)**

| Dial | Default | Harder mode |
|---|---|---|
| Max hearts | 20 (40 HP) | — |
| Loss per death | 1 heart | 2 hearts |
| Floor | 5 hearts (10 HP) | 3 hearts (6 HP) |

### 12.7 Config fields

Codec-backed JSON, loaded on server start (CONVENTIONS.md §5).

| Field | Default | Governs |
|---|---|---|
| `sigil_drop_rates.*` | §12.1 | Every entry in the Sigil drop table |
| `vault.tier*_distinct_types`, `vault.tier*_stack_depth`, `vault.tier*_sigils` | §12.3 | Capacity ladder |
| `vault.distinct_types_per_sigil` | 27 | Capacity — every Sigil's immediate distinct-type gain |
| `vault.reactivation_sigils` | 1 | §2.1 re-anchoring cost |
| `vault.local_reach_chunks` | 5 | Local reach square, centred on the Anchor |
| `vault.reach_tier_sigils` | 1 | Dimensional Sigils per reach tier |
| `vault.funnel_items_per_transfer` | 16 | §2.1 Linked Funnel throughput, per hopper cycle. Never played |
| `deposit_requires_reach` | `false` | Turns §2.0's free deposit off, making deposit obey reach too |
| `bundle_slots` | 9 | §2.2 Bundle override. **Not yet implemented** — the field lands with the override |
| `enchantment.abundance_chance_per_level` | 0.6 | §3.1/§12.6 — the per-level Abundance bonus roll |
| `enchantment.abundance_book_chance` | 0.06 | §3.1 mineshaft/trial chamber Abundance book rate |
| `enchantment.kiln_touch_book_chance` | 0.06 | §3.2 nether fortress/bastion Kiln Touch book rate |
| `core_rate_multiplier` | 1.0 | Global multiplier on every §12.4 / §12.5 rate |
| `core_accrual_cap_hours` | 24 | §4.2 offline backlog ceiling |
| `attunement_thresholds.*` | §12.4 | Per-family imprint counts |
| `core.*_period_ticks` | §12.4 | Tier-I period per housing, in ticks (20s = 400) |
| `core.quarry_blocks_per_cycle` | 8 | §12.4 — the Quarry Node is the only housing producing in bulk |
| `core.tier2_multiplier`, `core.tier3_multiplier` | 2.5, 6.0 | §12.4 rate tiers |
| `core.housing_slots` | 9 | §4.2 internal storage. **Never played** — §4.2 says only "small" |
| `core.settle_interval_ticks` | 20 | How often a *loaded* housing re-runs the same settle-from-timestamp calculation. Not a rate: changing it cannot change total yield |
| `core.soul_cage_xp_per_cycle`, `core.paddock_xp_per_cycle`, `core.verdant_planter_xp_per_cycle`, `core.quarry_node_xp_per_cycle` | 4, 3, 0, 0 | §4.2/§12.4 — generic (non-§5) core XP per cycle, per housing |
| `classic_cores.*.xp_per_cycle` | §12.5 | Per-classic-core XP per cycle, overriding the generic baseline the same way `period_ticks` overrides the base rate |
| `lives.*` | §12.6 | Cap, loss per death, floor |
