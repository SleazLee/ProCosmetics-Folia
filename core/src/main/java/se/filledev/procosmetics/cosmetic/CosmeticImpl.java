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
package se.filledev.procosmetics.cosmetic;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.ProCosmetics;
import se.filledev.procosmetics.api.config.Config;
import se.filledev.procosmetics.api.cosmetic.Cosmetic;
import se.filledev.procosmetics.api.cosmetic.CosmeticBehavior;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.CosmeticType;
import se.filledev.procosmetics.api.cosmetic.registry.CosmeticCategory;
import se.filledev.procosmetics.api.event.PlayerEquipCosmeticEvent;
import se.filledev.procosmetics.api.event.PlayerPreEquipCosmeticEvent;
import se.filledev.procosmetics.api.event.PlayerUnequipCosmeticEvent;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.config.ConfigManagerImpl;
import se.filledev.procosmetics.util.AbstractRunnable;
import se.filledev.procosmetics.util.Scheduler;

public abstract class CosmeticImpl<T extends CosmeticType<T, B>,
        B extends CosmeticBehavior<T>> extends AbstractRunnable implements Cosmetic<T, B>, Listener, CosmeticContext<T> {

    protected final ProCosmeticsPlugin plugin;
    protected final User user;
    protected final T cosmeticType;
    protected final B behavior;
    protected Player player;
    private boolean equipped;
    private boolean equipAborted;

    public CosmeticImpl(ProCosmeticsPlugin plugin, User user, T cosmeticType, B behavior) {
        this.plugin = plugin;
        this.user = user;
        this.cosmeticType = cosmeticType;
        this.behavior = behavior;
    }

    @Override
    public void equip(boolean silent, boolean saveToDatabase) {
        player = plugin.getServer().getPlayer(user.getUniqueId());

        if (player == null || !player.isOnline()) {
            return;
        }
        Server server = plugin.getServer();
        PluginManager pluginManager = server.getPluginManager();
        PlayerPreEquipCosmeticEvent event = new PlayerPreEquipCosmeticEvent(plugin, user, player, cosmeticType);
        pluginManager.callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        player.closeInventory();

        if (!cosmeticType.hasPermission(player)) {
            unequip(false, true);
            return;
        }
        ConfigManagerImpl configManager = plugin.getConfigManager();

        Config config = configManager.getMainConfig();
        boolean blacklistedWorlds = config.getBoolean("multi_world.blacklisted_worlds");
        boolean inWorld = config.getStringList("multi_world.worlds").contains(player.getWorld().getName());

        if (blacklistedWorlds && inWorld || !blacklistedWorlds && !inWorld) {
            if (!silent) {
                user.sendMessage(user.translate("cosmetic.equip.deny.world"));
            }
            return;
        }
        CosmeticCategory<T, B, ?> category = cosmeticType.getCategory();

        if (plugin.getWorldGuardManager() != null && !plugin.getWorldGuardManager().isCosmeticAllow(player, category)) {
            user.sendMessage(user.translate("cosmetic.equip.deny.region"));
            return;
        }
        if (!canEquip()) {
            return;
        }
        pluginManager.registerEvents(this, plugin);

        if (user.hasCosmetic(category) && user.getCosmetic(category).isEquipped()) {
            user.removeCosmetic(category, true, false);
        }
        user.setCosmetic(category, this);

        equipAborted = false;
        onEquip();

        if (equipAborted) {
            HandlerList.unregisterAll(this);

            if (user.getCosmetic(category) == this) {
                user.getCosmetics().remove(category);
            }
            return;
        }
        behavior.onEquip(this);

        if (behavior instanceof Listener behaviorListener) {
            pluginManager.registerEvents(behaviorListener, plugin);
        }

        if (!silent) {
            sendEquipMessageOnPlayerRegion();
        }
        equipped = true;
        pluginManager.callEvent(new PlayerEquipCosmeticEvent(plugin, user, player, cosmeticType));

        if (saveToDatabase) {
            plugin.getDatabase().saveEquippedCosmeticAsync(user, cosmeticType);
        }
    }

    @Override
    public void run() {
        if (player == null || !player.isOnline() || !equipped) {
            cancel();
            return;
        }
        if (Scheduler.isFolia()) {
            Scheduler.run(player, this::runUpdateOnPlayerRegion);
            return;
        }
        onUpdate();
    }

    /**
     * Re-checks cosmetic state after the Folia player scheduler accepts the update.
     *
     * <p>The repeating timer can fire just before the player disconnects or unequips the cosmetic.
     * This guard keeps the actual cosmetic update on the player's owning region and avoids running
     * behavior for a cosmetic that became inactive while the region task was queued.</p>
     */
    private void runUpdateOnPlayerRegion() {
        if (player == null || !player.isOnline() || !equipped) {
            return;
        }
        onUpdate();
    }

    /**
     * Sends the equip confirmation from the player's owning region.
     *
     * <p>Using the player scheduler avoids reading {@code player.getLocation()} from the global
     * timer path on Folia, which can happen while the player is crossing region boundaries.</p>
     */
    private void sendEquipMessageOnPlayerRegion() {
        Scheduler.run(player, () -> user.sendMessage(user.translate(
                "cosmetic." + cosmeticType.getCategory().getKey() + ".equip",
                Placeholder.unparsed("cosmetic", cosmeticType.getName(user))
        )));
    }

    @Override
    public void unequip(boolean silent, boolean saveToDatabase) {
        if (!equipped) {
            return;
        }
        cancel();
        HandlerList.unregisterAll(this);
        behavior.onUnequip(this);

        if (behavior instanceof Listener behaviorListener) {
            HandlerList.unregisterAll(behaviorListener);
        }
        onUnequip();

        if (!silent) {
            user.sendMessage(user.translate(
                    "cosmetic." + cosmeticType.getCategory().getKey() + ".unequip",
                    Placeholder.unparsed("cosmetic", cosmeticType.getName(user))
            ));
        }
        plugin.getServer().getPluginManager().callEvent(new PlayerUnequipCosmeticEvent(plugin, player, cosmeticType));
        equipped = false;

        if (saveToDatabase) {
            plugin.getDatabase().removeEquippedCosmeticAsync(user, cosmeticType.getCategory());
        }
    }

    protected boolean canEquip() {
        return true;
    }

    /**
     * Stops the current equip flow after {@link #onEquip()} discovers that the
     * cosmetic could not actually become active.
     *
     * <p>This is used by entity-backed cosmetics when a spawn is still blocked by
     * another plugin after all normal pre-equip checks pass. Without this hook, the
     * base equip flow would continue to send the success message, save the cosmetic
     * as equipped, and fire the public equip event even though no visible cosmetic
     * exists.</p>
     */
    protected void abortEquip() {
        equipAborted = true;
    }

    protected abstract void onEquip();

    protected abstract void onUpdate();

    protected abstract void onUnequip();

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public T getType() {
        return cosmeticType;
    }

    @Override
    public B getBehavior() {
        return behavior;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isEquipped() {
        return equipped;
    }

    @Override
    public ProCosmetics getPlugin() {
        return plugin;
    }
}
