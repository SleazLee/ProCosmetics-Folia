/*
 * This file is part of ProCosmetics - https://github.com/FilleDev/ProCosmetics
 * Copyright (C) 2025-2026 FilleDev and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package se.filledev.procosmetics.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import se.filledev.procosmetics.util.MetadataUtil;

public class CreatureSpawnListener implements Listener {

    /**
     * Allows only ProCosmetics-tagged custom entity spawns to bypass generic spawn
     * cancellation from protection plugins such as WorldGuard.
     *
     * <p>Server owners should still use ProCosmetics' own WorldGuard flags to deny
     * cosmetics in regions. This listener exists only so unrelated flags like
     * {@code mob-spawning: deny} do not make cosmetic pets and mounts silently fail
     * after the plugin has already accepted the equip request.</p>
     *
     * @param event the creature spawn event fired by Bukkit/Paper
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.CUSTOM && MetadataUtil.isCustomEntity(event.getEntity())) {
            event.setCancelled(false);
        }
    }
}
