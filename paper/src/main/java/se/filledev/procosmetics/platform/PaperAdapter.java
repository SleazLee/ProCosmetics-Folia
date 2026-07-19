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

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.platform.PlatformAdapter;

import java.util.ArrayList;
import java.util.List;

public class PaperAdapter implements PlatformAdapter {

    @Override
    public Audience audience(Player player) {
        return player;
    }

    @Override
    public Audience audience(CommandSender commandSender) {
        return commandSender;
    }

    @Override
    public void setText(TextDisplay textDisplay, Component component) {
        textDisplay.text(component);
    }

    @Override
    public void setCustomName(Entity entity, Component component) {
        entity.customName(component);
    }

    @Override
    public @Nullable Component getDisplayName(ItemMeta itemMeta) {
        return itemMeta.customName();
    }

    @Override
    public void setDisplayName(ItemMeta itemMeta, Component component) {
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
        itemMeta.customName(component);
    }

    @Override
    public @Nullable List<Component> getLore(ItemMeta itemMeta) {
        return itemMeta.lore();
    }

    @Override
    public void setLore(ItemMeta itemMeta, List<Component> components) {
        List<Component> copy = new ArrayList<>(components);
        copy.replaceAll(component ->
                component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        itemMeta.lore(copy);
    }

    @Override
    public Inventory createInventory(Player player, int rows, Component title) {
        return Bukkit.createInventory(player, rows, title);
    }
}
