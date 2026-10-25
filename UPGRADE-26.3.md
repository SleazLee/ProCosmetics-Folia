# Folia 26.3 update

This update merges upstream ProCosmetics 2.0.9 (`dc587f7`) into the fork's existing 2.0.6 merge plus custom fairy effects (`5181484`). It includes upstream's 26.3 NMS adapter and swing-animation packets, treasure-chest pagination and duplicate rewards, and Hikari idle-timeout fix.

## Fork behavior retained

- Folia entity/region scheduling, async teleports, entity tracking and gadget cleanup remain in place.
- Pets and mounts keep the Paper pathfinder, async recovery teleports and navigation cleanup. The new `v26_3` adapter implements the same `stopNavigation()` cleanup as the older adapters.
- NoteBlockAPI and music cosmetics remain disabled. Scoreboard collision handling remains disabled.
- Custom fairy effects, BMEssentials AFK integration and Grim exemptions remain in place.
- Existing category settings and the configuration updater's `keepAll` behavior remain in place.
- Shared core still compiles against the 26.1.2 Paper / Adventure 4 API. The Paper adapter targets the pinned `26.3.build.222-stable` bundle; 26.1 and 26.2 NMS adapters remain included.

## Build

Use Java 25. Put the official BuildTools JAR in `spigotInstall`, install the supported Spigot development artifacts, then build the plugin from the project directory:

```powershell
Push-Location spigotInstall
java -jar BuildTools.jar --rev 26.1.2
java -jar BuildTools.jar --rev 26.2
java -jar BuildTools.jar --rev 26.3
Pop-Location
.\gradlew.bat build --console=plain
```

The plugin is written to `build/libs/ProCosmetics-2.0.9.jar`.

## Before installing on a live server

Back up the existing plugin JAR and ProCosmetics data/configuration directory. Stop the server, replace the old ProCosmetics JAR with the new one, and start a Folia 26.3 staging server using a copy of your configuration.

The treasure-chest configuration advances to version 2. Upstream's duplicate reward conversion now takes effect; review `duplicate_handling` and the new `convert_to_coins` payout limits if treasure chests are enabled. Chest menu entries can now specify a `page`.

Check plugin startup and reload, disabled categories, custom fairy effects, balloons and morph self-view/swing animations. Exercise pets and mounts while moving across region boundaries and teleporting, then unequip them and disconnect. Test any enabled treasure chests, including duplicate rewards and menu pagination. Watch the console for region ownership errors or missing-method errors.

## Verification performed

- `gradlew.bat build --console=plain` passed with Java 25, including the 26.1, 26.2 and 26.3 adapters and formatting checks. The repository has no automated unit tests.
- A temporary copy of the version 1 treasure-chest configuration migrated to version 2 using the bundled YAML updater. Disabled chests and menus, a custom disabled chest and the existing duplicate-reward preference were retained, while the new payout and page settings were added.
- The finished JAR contains all three server adapters, the custom fairy effects, Folia scheduler, BMEssentials and Grim hooks, version `2.0.9` and `folia-supported: true`. NoteBlockAPI is absent.
- Compiled bytecode retains the shared Adventure 4 builder descriptor and includes the 26.3 swing packet and pet/mount navigation cleanup.

Player movement and cross-region behavior still require testing on a running Folia server. No live-server gameplay test was performed here.
