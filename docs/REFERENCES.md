# Sigilstead — References

---

## Verified toolchain facts (checked 2026-07-31, build confirmed working)

Everything here was queried from the source of truth and then **proven by an actual successful
`./gradlew build`** — not taken from a tutorial or a search snippet. Re-verify the same way when
bumping versions.

| Fact | Value | How to re-check |
|---|---|---|
| Current Java release | **26.2**, released 2026-06-16 | `meta.fabricmc.net/v2/versions/game` |
| Java required by 26.2 | **25** | `piston-meta.mojang.com` manifest → `javaVersion` |
| Fabric Loader | **0.19.3** | `meta.fabricmc.net/v2/versions/loader` |
| Fabric API for 26.2 | **0.156.0+26.2** | `maven.fabricmc.net/.../fabric-api/maven-metadata.xml` |
| Loom plugin id | **`net.fabricmc.fabric-loom`** (fully qualified) | see trap 3 |
| Loom version | **1.17.17** | `maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml` |
| Gradle | **9.6.1** | `services.gradle.org/versions/current` |
| Mappings | **None exist and none are needed** | see trap 2 |

---

## The five traps this project actually hit

Each of these cost a failed build. They are recorded because none is discoverable from a normal
Fabric tutorial, and all of them will recur on a version bump.

### 1. Loom needs Gradle *itself* on JDK 25

A `java.toolchain` block is not enough — it governs compilation, not the Gradle JVM.

```
Failed to setup Minecraft: Minecraft 26.2 requires Java 25 but Gradle is using 21
```

Fix: `export JAVA_HOME=/opt/homebrew/opt/openjdk@25`, or `org.gradle.java.home` in
`~/.gradle/gradle.properties`.

### 2. **Minecraft 26.1 was the first unobfuscated release** — mappings no longer exist

This is the big one, and it explains every mapping-related dead end:

- Mojang **stopped publishing** `client_mappings`/`server_mappings` after 1.21.11. Confirmed against
  the version manifest: 1.21.9 and 1.21.11 have them; 26.1, 26.1.2, 26.2 and 26.3 snapshots have
  **none**.
- **Yarn stopped at 1.21.11** for the same reason. `meta.fabricmc.net/v2/versions/yarn/26.2` → `[]`.
- Fabric's **intermediary for 26.x is the placeholder `0.0.0`**.

Consequences for `build.gradle`, per [Fabric's 26.1 announcement](https://fabricmc.net/2026/03/14/261.html):

| Old | Now |
|---|---|
| `mappings loom.officialMojangMappings()` | **remove entirely** |
| `modImplementation` | `implementation` |
| `remapJar` | `jar` |

`loom.officialMojangMappings()` fails with `Failed to find official mojang mappings for 26.2`.

### 3. The plugin id must be **fully qualified**

`id 'fabric-loom'` resolves to the legacy **remapping** plugin, which then demands a mappings
dependency that cannot exist:

```
Failed to setup Minecraft: Configuration 'mappings' has no dependencies
```

Use `id 'net.fabricmc.fabric-loom'`. (`net.fabricmc.fabric-loom-no-remap` was a prototype that ended
at 1.14.0-alpha.31 and folded back into mainline — don't use it.)

### 4. `ResourceLocation` was renamed to `Identifier`

`net.minecraft.resources.Identifier` in 26.x. `fromNamespaceAndPath`, `parse`, `tryParse` and
`withDefaultNamespace` all still exist. Expect more renames like this — unobfuscation was
accompanied by cleanup.

### 5. GameTest is annotation-based now

There is no `FabricGameTest` interface to implement. The entrypoint class is a **plain class** whose
test methods carry `@net.fabricmc.fabric.api.gametest.v1.GameTest` (`structure`, `maxTicks`,
`setupTicks`, `rotation`, `skyAccess`, `maxAttempts`, `requiredSuccesses`, `padding`, …). The
`fabric.mod.json` entrypoint key is still `fabric-gametest`.

### Bonus: don't trust mod-listing sites for versions

A search result confidently reported Fabric Loader 0.18.4 as current. The meta API says 0.19.3.
Query the APIs.

---

## How to check an API in 26.x

Unobfuscation makes this genuinely easy — real names are in the jar:

```bash
unzip -l ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar | grep -i <name>
javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar net.minecraft.resources.Identifier
./gradlew genSources    # full decompiled bodies
```

Do this instead of searching. Every tutorial predating 26.1 is wrong about names, and most are wrong
about shapes too.

---

## Verified: the recipe book is reusable (DESIGN.md §7.1)

Read out of `minecraft-common.jar` with `javap` on 2026-08-01. The Artisan's Table does **not** need
a custom recipe browser — extending the vanilla crafting menu inherits the whole thing.

```
AbstractCraftingMenu extends RecipeBookMenu extends AbstractContainerMenu

  RecipeBookType getRecipeBookType()
  PostPlaceAction handlePlacement(boolean, boolean, RecipeHolder<?>, ServerLevel, Inventory)
  void            fillCraftSlotsStackedContents(StackedItemContents)
  List<Slot>      getInputGridSlots()      int getGridWidth() / getGridHeight()
```

`RecipeBookType` is a **plain enum** — `CRAFTING`, `FURNACE`, `BLAST_FURNACE`, `SMOKER` — and is not
extensible. Return `CRAFTING`; a 3×3 grid is semantically exactly that, and it costs nothing.

The two Vault hooks DESIGN.md §7.1 asks for land here:

| Requirement | Hook | Trap |
|---|---|---|
| Recipes show craftable from Vault contents | override `fillCraftSlotsStackedContents`, `accountStack` the Vault as well as the grid | Craftability is computed **client-side**, so the client needs a synced Vault snapshot. §2.4 needs one too — build one sync path, not two |
| Clicking a recipe pulls missing items from the Vault | override `handlePlacement`; withdraw, then delegate to `ServerPlaceRecipe.placeRecipe(...)` | `placeRecipe` takes a player `Inventory` and pulls only from it. Route every withdrawal through the §2.5-tested transfer code — a second item-moving path is how the Vault gets a dupe bug |

Supporting classes, all in `minecraft-common.jar`: `net.minecraft.recipebook.ServerPlaceRecipe`
(+ `$CraftingMenuAccess`), `net.minecraft.world.entity.player.StackedItemContents`,
`net.minecraft.world.item.crafting.RecipeBookCategory` / `ExtendedRecipeBookCategory`.

---

## Still needed (not yet gathered)

| Topic | Needed for | Why it's risky |
|---|---|---|
| `Item.Properties` / `ResourceKey` construction | Phase 0, every item | Changed repeatedly across 1.21.x; check the real signature |
| `DataComponentType` registration + codecs | Per-stack state, core attunement (§4.1) | Typed, codec-backed, validated on load |
| Fabric `AttachmentRegistry` | Hearts, Codex archive, villager teaching | Respawn/dimension-change persistence semantics |
| `ScreenHandler` / slot sync | Vault, Artisan, Codex | The reason this is a mod; also where desync bugs live |
| `SavedData` API | Vault contents (§2), active-core registry (§4.3) | Persistence shape; both are world-level |
| Chunk-load event + world time | Core offline accrual (§4.2) | Settle from one timestamp; double-clocking is the bug this invites |
| Custom enchantment definition format | §3 enchantments | Data-driven since 1.21, still churning |
| 26.2 entity predicate format | Loot tables, advancements | Component-style map that **rejects unknown sub-predicates** |
| Loot table modification callback vs JSON override | Phase 0.2 | Overriding vanilla tables conflicts with other mods |
| Villager `Offers` persistence in 26.2 | §3.3, §7.2 | 26.2 fixed an empty-`Offers` bug; version-specific |

---

## Documentation

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html) — **read this first.** The
  unobfuscation announcement and the migration guidance behind trap 2
- [Fabric Documentation](https://docs.fabricmc.net/) — official developer guide
- [Custom Data Components](https://docs.fabricmc.net/develop/items/custom-data-components)
- [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) — the authoritative
  `build.gradle` shape; this is what settled traps 2 and 3
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [misode.github.io](https://misode.github.io/) — JSON generators/validators for recipes, loot
  tables, advancements and tags, version-aware through 26.2

---

## Prior art worth studying

- **Storage:** Applied Energistics / Refined Storage — the terminal metaphor the Vault imitates. Now
  that a real slot grid is possible, their UI affordances are directly relevant.
- **Farm replacement:** Mob Grinding Utils, Industrial Foregoing, Woot — especially how they balance
  "no mobs actually spawn".
- **Lives:** Hardcore Hearts, Origins-style life systems, the Lifesteal SMP heart economy.

---

## Source conversation

The design originated in a Claude conversation dated 30 Jul 2026, shared at
`claude.ai/share/d51527d6-59e7-4433-9300-d00d908e92d0`. It produced a design wiki file **not
retrievable from the share link** — [DESIGN.md](DESIGN.md) is a reconstruction from the conversation
body. Remaining gaps are in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).
