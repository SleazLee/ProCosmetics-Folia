/*
 * This file is part of ProCosmetics - https://github.com/FilleDev/ProCosmetics
 * Copyright (C) 2026 FilleDev and contributors
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
package se.filledev.procosmetics.platform;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.platform.PlatformAdapter;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SpigotAdapter implements PlatformAdapter {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final Cache<Player, Audience> playerCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .weakKeys()
            .build();

    private final Cache<CommandSender, Audience> commandSenderCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .weakKeys()
            .build();

    @Override
    public Audience audience(Player player) {
        Audience cached = playerCache.getIfPresent(player);

        if (cached != null) {
            return cached;
        }
        Audience audience = new Audience() {
            @Override
            public void sendMessage(@Nonnull Component message) {
                String json = JSONComponentSerializer.json().serialize(message);
                player.spigot().sendMessage(ComponentSerializer.parse(json));
            }

            @Override
            public void sendActionBar(@Nonnull Component message) {
                String json = JSONComponentSerializer.json().serialize(message);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ComponentSerializer.parse(json));
            }

            @Override
            public void showTitle(@Nonnull Title title) {
                player.sendTitle(LEGACY_SERIALIZER.serialize(title.title()),
                        LEGACY_SERIALIZER.serialize(title.subtitle()),
                        (int) (title.times().fadeIn().toMillis() / 50),
                        (int) (title.times().stay().toMillis() / 50),
                        (int) (title.times().fadeOut().toMillis() / 50));
            }
        };
        playerCache.put(player, audience);
        return audience;
    }

    @Override
    public Audience audience(CommandSender commandSender) {
        Audience cached = commandSenderCache.getIfPresent(commandSender);

        if (cached != null) {
            return cached;
        }
        Audience audience = new Audience() {
            @Override
            public void sendMessage(@Nonnull Component message) {
                String json = JSONComponentSerializer.json().serialize(message);
                commandSender.spigot().sendMessage(ComponentSerializer.parse(json));
            }
        };
        commandSenderCache.put(commandSender, audience);
        return audience;
    }

    @Override
    public void setText(TextDisplay textDisplay, Component component) {
        textDisplay.setText(LEGACY_SERIALIZER.serialize(component));
    }

    @Override
    public void setCustomName(Entity entity, Component component) {
        entity.setCustomName(LEGACY_SERIALIZER.serialize(component));
    }

    @Override
    public @Nullable Component getDisplayName(ItemMeta itemMeta) {
        if (!itemMeta.hasDisplayName()) {
            return null;
        }
        return LEGACY_SERIALIZER.deserialize(itemMeta.getDisplayName());
    }

    @Override
    public void setDisplayName(ItemMeta itemMeta, Component component) {
        itemMeta.setDisplayName(LEGACY_SERIALIZER.serialize(component));
    }

    @Override
    public @Nullable List<Component> getLore(ItemMeta itemMeta) {
        if (!itemMeta.hasLore()) {
            return null;
        }
        List<Component> components = new ArrayList<>();

        for (String line : itemMeta.getLore()) {
            components.add(LEGACY_SERIALIZER.deserialize(line));
        }
        return components;
    }

    @Override
    public void setLore(ItemMeta itemMeta, List<Component> components) {
        List<String> legacyLore = new ArrayList<>();
        for (Component component : components) {
            legacyLore.add(LEGACY_SERIALIZER.serialize(component));
        }
        itemMeta.setLore(legacyLore);
    }

    @Override
    public Inventory createInventory(Player player, int rows, Component title) {
        return Bukkit.createInventory(player, rows, LEGACY_SERIALIZER.serialize(title));
    }
}
