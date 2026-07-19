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
package se.filledev.procosmetics.nms.entitytype;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CachedEntityType {

    private final Object entityTypeObject;

    CachedEntityType(EntityType entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("EntityType is null.");
        }

        try {
            Class<?> identifierClass = ReflectionUtil.getNMSClass("resources.Identifier");

            if (identifierClass == null) {
                throw new IllegalStateException("Identifier class is null.");
            }
            String entityName = entityType.getTranslationKey()
                    .toLowerCase()
                    .replace("entity.minecraft.", "");

            Method withDefaultNamespace = identifierClass.getMethod("withDefaultNamespace", String.class);
            Object identifier = withDefaultNamespace.invoke(null, entityName);

            if (identifier == null) {
                throw new IllegalStateException("Identifier is null.");
            }
            Class<?> builtInRegistriesClass = ReflectionUtil.getNMSClass("core.registries.BuiltInRegistries");

            if (builtInRegistriesClass == null) {
                throw new IllegalStateException("BuiltInRegistries class is null.");
            }
            Field entityTypeRegistryField = builtInRegistriesClass.getField("ENTITY_TYPE");
            Object registry = entityTypeRegistryField.get(null);

            if (registry == null) {
                throw new IllegalStateException("ENTITY_TYPE registry is null.");
            }
            Method getValue = registry.getClass().getMethod("getValue", identifierClass);
            Object result = getValue.invoke(registry, identifier);

            if (result == null) {
                throw new IllegalStateException("Registry lookup returned null for entity type: " + entityName);
            }
            entityTypeObject = result;
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to cache entity type " + entityType.name() + " via reflection.", e);
        }
    }

    @Nullable
    public Object getEntityTypeObject() {
        return entityTypeObject;
    }
}
