# Heartstead — Conventions

Decisions that keep code consistent across sessions. If you break one of these, change it here first.

> **Rewritten 2026-07-31** when the project moved from a data pack to a Fabric mod. The data-pack-era
> `custom_data` marker conventions and base-item workarounds are gone — see §2.

---

## 1. Identity

| Thing | Value |
|---|---|
| Mod name | **Heartstead** |
| Mod id / namespace | `heartstead` |
| Java package | `com.heartstead` |
| Target | Minecraft **26.2**, Java **25**, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2 |
| Mappings | **None** — 26.x ships unobfuscated. `implementation`, not `modImplementation`; `jar`, not `remapJar` |

Every `Identifier` goes through `Heartstead.id(String)`. Never construct one inline; a typo'd
namespace fails at runtime, not compile time.

> **Note:** the class is `net.minecraft.resources.Identifier` in 26.x. It was called
> `ResourceLocation` through 1.21.x — see §9.

---

## 2. Items and blocks

Items and blocks are **real registered objects with their own ids**. This is the whole reason the
project is a mod.

Consequences, all of which reverse a data-pack-era workaround:

- **Recipes gate correctly.** An ingredient of `heartstead:heart_shard` matches *only* a Heart Shard.
  The old "recipes match by item id so a currency item can be faked" problem is gone, along with the
  "pick an obscure base item nobody has" mitigation. There is no base-item table any more; do not
  reintroduce one.
- **Blocks are blocks.** Vault Anchor, Soul Cage, Quarry Node and the rest are real blocks with their
  own block states, textures, and block entities. No marker entities shadowing a vanilla barrel, and
  no desync when a piston or explosion moves the block out from under a marker.
- **Stack sizes, durability, rarity, tooltips** are item properties, not component overrides.

### 2.1 Naming

| Kind | Convention | Example |
|---|---|---|
| Registry id | snake_case | `heartstead:vital_heart` |
| Registry holder field | SCREAMING_SNAKE | `HsItems.VITAL_HEART` |
| Registry class | `Hs` + plural | `HsItems`, `HsBlocks`, `HsBlockEntities` |
| Lang key | vanilla scheme | `item.heartstead.vital_heart` |

One registry class per registry, all under `com.heartstead.registry`. Registration is called
explicitly and in order from `Heartstead.onInitialize()` — never rely on static class-load timing.

### 2.2 Per-stack state

Use **custom data components** (`DataComponentType`), registered in `HsComponents`. A record per
component, not a loose `CompoundTag`. Components are typed, codec-backed and validated on load; raw
NBT is none of those things.

Per-*player* state (hearts, Vault contents, Codex archive) uses **attachments**
(`AttachmentRegistry`), not scoreboards and not a global map — attachments serialise with the player
and survive dimension change and respawn.

---

## 3. Source sets

```
src/main/java      common — runs on client AND dedicated server
src/client/java    client only — screens, renderers, keybinds, tooltips, colour providers
src/test/java      plain JUnit — pure logic, no world
```

A client-only class referenced from common code **crashes the dedicated server on load**, and it
surfaces late. Keep the split honest: if it draws, it's in `src/client`.

Package layout under `com.heartstead`:

```
registry/     HsItems, HsBlocks, HsBlockEntities, HsComponents, HsScreenHandlers, HsAttachments
item/         item classes
block/        block classes
blockentity/  block entities
vault/ core/ codex/ lives/ economy/ enchantment/    one package per design-wiki system
config/       the tuning dials (§6)
util/         shared helpers, owned by no system
gametest/     in-world automated tests
```

---

## 4. Persistence

| State | Mechanism |
|---|---|
| Per-stack | Data components (§2.2) |
| Per-player | Attachments on the player |
| Per-block | Block entity NBT |
| World-level (core registry, waystones) | `SavedData` |

Everything persisted goes through a **Codec**. No hand-rolled `read`/`write` NBT pairs — codecs give
you validation and a versioning story, and this project has a lot of state that must survive a crash.

Every persisted structure carries a schema version field from day one. Migrating is cheap; guessing
what an unversioned blob meant is not.

---

## 5. Config

Config is a real config file (JSON, codec-backed) loaded on server start, **not** scoreboards.
Every tuning dial in design wiki §11 must be a config field, not a literal.

```
core_rate_multiplier   default 1.0    // DESIGN.md §11 — expect to lower after playtesting
heart_floor            default 5      // DESIGN.md §5
```

Server-authoritative. Clients receive what they need to render, never to decide.

---

## 6. Data-driven content stays data-driven

**Being a mod does not mean writing everything in Java.** Recipes, loot tables, advancements, tags
and enchantments are still JSON, shipped in `src/main/resources/data/heartstead/`. Vanilla loot table
overrides go in `data/minecraft/`.

This is why the Phase 0 economy work carries over from the data pack era essentially unchanged.

Prefer a **data generator** (Fabric's `DataGeneratorEntrypoint`) over hand-written JSON for anything
structurally repetitive — loot tables, recipes, models, tags. The generator is the artifact; the JSON
is build output. Generated JSON is never hand-edited.

---

## 7. Style

- 4-space indent, 120 column soft limit, UTF-8, LF.
- `final` on fields that don't change; package-private over public unless another package needs it.
- Every class gets a javadoc saying what it's for and which design-wiki section it implements.
- No `System.out` — use `Heartstead.LOG`.

---

## 8. Verification

**This is the part that changed most.** The mod can verify its own work:

| Layer | Command | Use for |
|---|---|---|
| Compile | `./gradlew build` | Catches the majority of mistakes the data pack couldn't catch at all |
| Unit | `./gradlew test` | Pure logic — accrual maths, capacity, cost curves |
| In-world | `./gradlew runGametest` | Item conservation, persistence, offline accrual, trade survival |
| By hand | `./gradlew runClient` | Feel, UI, balance — the things tests can't judge |

**Write the GameTest before the system it covers** for anything that moves items. The Vault is
expected to have item-loss bugs (design wiki §2.5); the difference now is that they can be caught by
a test instead of by a player.

Claude Code **can** run all three of the first rows. "It compiles and the tests pass" is now a real
claim — but it is still not the same as "it feels right", which only playing can tell you.

---

## 9. API churn — and how to actually check

26.1 was the first **unobfuscated** Minecraft release. The upside is large: the jar carries real
names, so you can read the true API directly instead of trusting a tutorial.

```bash
# list classes
unzip -l ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar | grep -i <name>

# read exact signatures
javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar net.minecraft.resources.Identifier

# full decompiled bodies when signatures aren't enough
./gradlew genSources
```

**Do this before writing against an unfamiliar class.** 26.x renamed a lot, and the compile error
never points at the real problem. Confirmed renames so far:

| Through 1.21.x | In 26.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| `FabricGameTest` interface, implemented | plain class + `@GameTest`-annotated methods, still under the `fabric-gametest` entrypoint |

Item construction also changed repeatedly across 1.21.x — items need their `ResourceKey` set on the
properties at construction. Treat any tutorial older than 26.1 as wrong about names *and* shapes.
