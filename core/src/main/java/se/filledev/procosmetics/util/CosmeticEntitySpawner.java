/*
 * This file is part of ProCosmetics - https://github.com/FilleDev/ProCosmetics
 * Copyright (C) 2025 FilleDev and contributors
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
package se.filledev.procosmetics.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class CosmeticEntitySpawner {

    private CosmeticEntitySpawner() {
    }

    /**
     * Spawns a ProCosmetics-owned entity using {@link SpawnReason#CUSTOM}.
     *
     * <p>Mounts can be backed by living entities, boats, or displays. The shared
     * mount spawn path needs to preserve the early ProCosmetics metadata tagging
     * used for protection-plugin compatibility without rejecting non-living
     * mount entity types such as {@code BLOCK_DISPLAY} and boats.</p>
     *
     * @param location the location where the cosmetic entity should spawn
     * @param entityType the configured entity type for the cosmetic
     * @param initializer cosmetic-specific setup to apply before the entity enters the world
     * @return the spawned entity, or {@code null} if the entity type cannot be spawned or the spawn is blocked
     */
    @Nullable
    public static Entity spawn(Location location,
                               EntityType entityType,
                               Consumer<? super Entity> initializer) {
        Class<? extends Entity> entityClass = entityType.getEntityClass();

        if (entityClass == null) {
            return null;
        }
        return spawn(location, entityClass, initializer);
    }

    /**
     * Spawns a ProCosmetics-owned living entity using {@link SpawnReason#CUSTOM}.
     *
     * <p>WorldGuard's generic {@code mob-spawning} flag can cancel normal Bukkit
     * plugin mob spawns before the cosmetic is visible, which used to make pets
     * and mounts report a successful equip while no entity appeared. This helper
     * tags the entity before the spawn event finishes so {@code CreatureSpawnListener}
     * can safely uncancel only ProCosmetics custom entities without affecting
     * natural mobs or other plugins' entities.</p>
     *
     * @param location the location where the cosmetic entity should spawn
     * @param entityType the configured entity type for the cosmetic
     * @param initializer cosmetic-specific setup to apply before the entity enters the world
     * @return the spawned entity, or {@code null} if the entity type is not living or the spawn is blocked
     */
    @Nullable
    public static LivingEntity spawnLiving(Location location,
                                          EntityType entityType,
                                          Consumer<? super LivingEntity> initializer) {
        Entity entity = spawn(location, entityType, initializerForLivingEntity(initializer));

        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    /**
     * Spawns a ProCosmetics-owned living entity class with the shared WorldGuard
     * bypass behavior used by Bukkit-backed cosmetic mobs.
     *
     * <p>The initializer runs after the ProCosmetics metadata is applied. That
     * ordering is important because protection plugins observe the metadata during
     * the same spawn event that may otherwise cancel the entity.</p>
     *
     * @param location the location where the cosmetic entity should spawn
     * @param entityClass the Bukkit living entity class to spawn
     * @param initializer cosmetic-specific setup to apply before the entity enters the world
     * @return the spawned entity, or {@code null} if the spawn is blocked
     */
    @Nullable
    public static <T extends LivingEntity> T spawnLiving(Location location,
                                                        Class<T> entityClass,
                                                        Consumer<? super T> initializer) {
        return spawn(location, entityClass, initializer);
    }

    /**
     * Spawns a ProCosmetics-owned entity class with the shared WorldGuard bypass behavior.
     *
     * <p>The initializer runs after the ProCosmetics metadata is applied. That
     * ordering is important because protection plugins observe the metadata during
     * the same spawn event that may otherwise cancel the entity.</p>
     *
     * @param location the location where the cosmetic entity should spawn
     * @param entityClass the Bukkit entity class to spawn
     * @param initializer cosmetic-specific setup to apply before the entity enters the world
     * @return the spawned entity, or {@code null} if the spawn is blocked
     */
    @Nullable
    public static <T extends Entity> T spawn(Location location,
                                            Class<T> entityClass,
                                            Consumer<? super T> initializer) {
        World world = location.getWorld();

        if (world == null) {
            return null;
        }
        T entity = world.spawn(location, entityClass, SpawnReason.CUSTOM, spawnedEntity -> {
            MetadataUtil.setCustomEntity(spawnedEntity);

            if (initializer != null) {
                initializer.accept(spawnedEntity);
            }
        });

        return entity.isValid() ? entity : null;
    }

    /**
     * Adapts a living-entity initializer for the generic entity spawn path.
     *
     * <p>This keeps existing callers that only support living entities on the
     * shared metadata-tagged spawn path without running their initializer for
     * displays, boats, or other non-living entity classes.</p>
     *
     * @param initializer the living-entity initializer to adapt
     * @return a generic entity initializer, or {@code null} when no initializer was provided
     */
    private static Consumer<? super Entity> initializerForLivingEntity(Consumer<? super LivingEntity> initializer) {
        if (initializer == null) {
            return null;
        }
        return entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                initializer.accept(livingEntity);
            }
        };
    }
}
