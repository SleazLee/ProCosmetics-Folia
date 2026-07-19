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

The API and versioned NMS modules follow upstream's current server version. Shared core bytecode intentionally compiles against the newest supported 26.1.x Paper API so it retains the Adventure 4 ABI while still exposing Folia schedulers and Paper pathfinding:

- `api/build.gradle.kts`: `org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT`
- `core/build.gradle.kts`: `org.spigotmc:spigot:26.2-R0.1-SNAPSHOT`, excluding its transitive `spigot-api`
- `core/build.gradle.kts`: `io.papermc.paper:paper-api:${rootProject.extra["corePaperApiVersion"]}`
- `paper/build.gradle.kts`: `paperweight.paperDevBundle(rootProject.extra["paperApiVersion"])`

The `v26_1` and `v26_2` NMS modules compile against their matching full Spigot/CraftBukkit artifacts.

Keep `corePaperApiVersion` on 26.1.x while that server generation is supported. Update `paperApiVersion` for the current Paper adapter independently.

Paper 26.2 brings Adventure 5, whose `TextComponent.Builder#build()` descriptor differs from Adventure 4. Compile shared core against Adventure 4.26.1 and verify `GadgetImpl` invokes the `BuildableComponent` descriptor; Adventure 5 provides a bridge for that older call site.

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
- [ ] Shared core remains on the 26.1.x/Adventure 4 ABI while the Paper adapter targets the current server API.
- [ ] NoteBlockAPI remains disabled unless a Folia-safe replacement is introduced.

## 8) NoteBlockAPI (Music Cosmetics) Status

NoteBlockAPI-based music cosmetics are currently disabled due to Folia incompatibilities. The code remains commented for future rework, but should not be re-enabled without a Folia-safe audio system.

## 9) Build NMS Subprojects Locally (BuildTools)

The versioned NMS modules use CraftBukkit classes that are **not** available in the Paper API. Install each supported server artifact into your local Maven repository.

1. Download BuildTools from Spigot:
   - https://www.spigotmc.org/wiki/buildtools/
2. Run BuildTools for each required version:
   ```bash
   java -jar BuildTools.jar --rev 26.1.2
   java -jar BuildTools.jar --rev 26.2
   ```
3. Re-run the build:
   ```bash
   ./gradlew :v26_1:compileJava :v26_2:compileJava
   ```

BuildTools installs the NMS/CraftBukkit artifacts into `~/.m2/repository/org/spigotmc/`, which Gradle resolves when compiling the NMS subprojects.

## 10) Pet and Mount Following

Keep the Paper pathfinder implementation in `NMSEntityImpl` for real pet and mount entities:

- Read player and entity state only from their owning Folia schedulers.
- Use Paper's `Mob#getPathfinder()` for nearby movement.
- Stop both Paper and NMS navigation before removal or teleport.
- Use `teleportAsync` for long-distance Folia recovery.
- Implement `stopNavigation()` and safe goal-selector clearing in every versioned NMS module.

## References

Paper setup guide:
- https://docs.papermc.io/paper/dev/project-setup/
