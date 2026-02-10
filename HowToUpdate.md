# How to Update for Folia + Paper Compatibility

This document captures the steps taken to keep ProCosmetics compatible with both Folia and traditional Paper/Bukkit servers, and how to maintain that compatibility as the plugin evolves.

## 1) Use the Scheduler Utility Everywhere

All task scheduling must go through `core/src/main/java/se/filledev/procosmetics/util/Scheduler.java` or helper classes that delegate to it (e.g., `AbstractRunnable`).

### Global sync scheduling
- `Scheduler.run(...)`
- `Scheduler.runLater(...)`
- `Scheduler.runTimer(...)`

### Async scheduling
- `Scheduler.runAsync(...)`
- `Scheduler.runAsyncLater(...)`
- `Scheduler.runAsyncTimer(...)`

### Region/world-sensitive scheduling
Use these when you touch entities, blocks, players, or world state:
- `Scheduler.run(location, ...)`
- `Scheduler.runLater(location, ...)`
- `Scheduler.runTimer(location, ...)`

**Important:** Folia requires at least **1 tick** delay for scheduled tasks. The `Scheduler` already enforces this for delayed and repeating tasks.

### AbstractRunnable usage
`core/src/main/java/se/filledev/procosmetics/util/AbstractRunnable.java` delegates all scheduling through `Scheduler`. New repeating/timed logic should extend or follow this pattern.

## 2) Search for Raw Bukkit Scheduler Usage

Before merging changes, scan for direct usage of Bukkit scheduling APIs and replace them with `Scheduler`:

- `Bukkit.getScheduler()`
- `BukkitRunnable`
- `runTask*` methods on Bukkit scheduler

If a new feature needs scheduling, use `Scheduler` instead of Bukkit directly.

## 3) Folia Flag in plugin.yml

Ensure `src/main/resources/plugin.yml` includes:

```
folia-supported: true
```

This flag tells Folia servers the plugin is safe to load.

## 4) Paper API Dependencies

The project should compile against the Paper API, not Spigot:

- `core/build.gradle.kts`: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- `api/build.gradle.kts`: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- `v1_21_11/build.gradle.kts`: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- `v1_21_10/build.gradle.kts`: `io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT`

If Paper updates its API version, update these coordinates consistently.

## 5) Shutdown Task Cancellation

Use `Scheduler.cancelTasks()` on plugin shutdown to cancel tasks for Folia (global/region/async) and legacy servers.

## 6) Region-Sensitive Actions

Any code that interacts with **entities, blocks, or players** must be executed via region-aware scheduling:

- Use a `Location` derived from the entity/player/block for `Scheduler.run(...)` or `Scheduler.runLater(...)`.

## 7) Quick Regression Checklist

When adding new features:

- [ ] No direct Bukkit scheduler usage (all scheduling uses `Scheduler`).
- [ ] Any world/entity/block interaction uses region-aware scheduling with `Location`.
- [ ] `plugin.yml` still contains `folia-supported: true`.
- [ ] Paper API dependency versions remain consistent across modules.
- [ ] NoteBlockAPI remains disabled unless a Folia-safe replacement is introduced.

## 8) NoteBlockAPI (Music Cosmetics) Status

NoteBlockAPI-based music cosmetics are currently disabled due to Folia incompatibilities. The code remains commented for future rework, but should not be re-enabled without a Folia-safe audio system.

## 9) Option B: Build NMS Subprojects Locally (BuildTools)

The `v1_21_10` and `v1_21_11` modules use NMS and CraftBukkit classes that are **not** available in the Paper API. If you need these modules to compile locally, you must install the corresponding server artifacts into your local Maven repository.

1. Download BuildTools from Spigot:
   - https://www.spigotmc.org/wiki/buildtools/
2. Run BuildTools for each required version:
   ```bash
   java -jar BuildTools.jar --rev 1.21.10
   java -jar BuildTools.jar --rev 1.21.11
   ```
3. Re-run the build:
   ```bash
   ./gradlew :v1_21_10:compileJava :v1_21_11:compileJava
   ```

BuildTools installs the NMS/CraftBukkit artifacts into `~/.m2/repository/org/spigotmc/`, which Gradle resolves when compiling the NMS subprojects.

## References

Paper setup guide:
- https://docs.papermc.io/paper/dev/project-setup/
