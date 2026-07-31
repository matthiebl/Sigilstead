---
description: Retarget the mod at a new Minecraft version
---

Retarget Heartstead at: $ARGUMENTS

**Query the meta APIs directly — do not trust search results or mod-listing sites.** One of them
reported the wrong Fabric Loader version during initial setup.

```
https://meta.fabricmc.net/v2/versions/game
https://meta.fabricmc.net/v2/versions/loader
https://meta.fabricmc.net/v2/versions/yarn/<version>        # expect [] — mojmap is the plan
https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
https://piston-meta.mojang.com/mc/game/version_manifest_v2.json   # javaVersion for the target
```

Then:

1. Update `gradle.properties` and the verified-facts table in `docs/REFERENCES.md`, plus the version
   table in `docs/DESIGN.md` §9 and the toolchain table in `CLAUDE.md`.
2. **Check the required Java version.** Loom needs Gradle itself running on it, not just a toolchain
   — if it changed, the README setup instructions need updating too.
3. Run `./gradlew build` and report what actually broke.
4. Prioritise fixes by risk, leading with: villager `Offers` manipulation (DESIGN.md §7.5), anything
   persisting state through a codec, and the client/server split.

Report before migrating code.
