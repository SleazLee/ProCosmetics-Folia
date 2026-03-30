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
package se.filledev.procosmetics.hook.bmessentials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.ProCosmeticsPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Bridges BMEssentials AFK + particle toggle state into ProCosmetics without hard dependencies.
 */
public final class BMEParticleAfkBridge implements Listener {

    private static final String BME_PLUGIN = "BMEssentials";
    private static final String AFK_MANAGER_CLASS = "at.sleazlee.bmessentials.AFKSystem.AfkManager";
    private static final String PARTICLE_MODE_CLASS = "at.sleazlee.bmessentials.Cosmetics.ParticleAfkModeState";
    private static final String SIGNAL_EVENT_CLASS = "at.sleazlee.bmessentials.Cosmetics.event.ParticleAfkStateSignalEvent";

    private final ProCosmeticsPlugin plugin;
    private final Map<UUID, Boolean> afkStateByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> particleModeByPlayer = new ConcurrentHashMap<>();

    private @Nullable Method afkManagerGetInstanceMethod;
    private @Nullable Method afkManagerIsAfkMethod;
    private @Nullable Method particleModeIsEnabledMethod;
    private @Nullable Method signalGetPlayerMethod;
    private @Nullable Method signalIsAfkMethod;
    private @Nullable Method signalIsParticleAfkModeEnabledMethod;

    private boolean available;

    public BMEParticleAfkBridge(ProCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        shutdown();

        Plugin bmePlugin = Bukkit.getPluginManager().getPlugin(BME_PLUGIN);
        if (bmePlugin == null || !bmePlugin.isEnabled()) {
            return;
        }
        try {
            Class<?> afkManagerClass = Class.forName(AFK_MANAGER_CLASS);
            afkManagerGetInstanceMethod = afkManagerClass.getMethod("getInstance");
            afkManagerIsAfkMethod = afkManagerClass.getMethod("isAfk", Player.class);

            Class<?> particleModeClass = Class.forName(PARTICLE_MODE_CLASS);
            particleModeIsEnabledMethod = particleModeClass.getMethod("isEnabled", Player.class);

            registerSignalListener();

            Bukkit.getPluginManager().registerEvents(this, plugin);
            available = true;

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                cacheStates(onlinePlayer);
            }

            plugin.getLogger().info("Hooked into BMEssentials AFK particle bridge.");
        } catch (ReflectiveOperationException ex) {
            available = false;
            plugin.getLogger().log(Level.WARNING, "Failed to initialize BMEssentials AFK particle bridge: " + ex.getMessage());
            shutdown();
        }
    }

    public void shutdown() {
        available = false;
        afkStateByPlayer.clear();
        particleModeByPlayer.clear();
        afkManagerGetInstanceMethod = null;
        afkManagerIsAfkMethod = null;
        particleModeIsEnabledMethod = null;
        signalGetPlayerMethod = null;
        signalIsAfkMethod = null;
        signalIsParticleAfkModeEnabledMethod = null;
        HandlerList.unregisterAll(this);
    }

    public boolean shouldRenderParticle(Player player) {
        if (!available) {
            return true;
        }
        Plugin bmePlugin = Bukkit.getPluginManager().getPlugin(BME_PLUGIN);
        if (bmePlugin == null || !bmePlugin.isEnabled()) {
            return true;
        }
        UUID playerId = player.getUniqueId();

        boolean onlyWhileAfk = particleModeByPlayer.computeIfAbsent(playerId, uuid -> queryParticleMode(player));
        if (!onlyWhileAfk) {
            return true;
        }

        boolean isAfk = afkStateByPlayer.computeIfAbsent(playerId, uuid -> queryAfk(player));
        return isAfk;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        afkStateByPlayer.remove(playerId);
        particleModeByPlayer.remove(playerId);
    }

    @SuppressWarnings("unchecked")
    private void registerSignalListener() throws ReflectiveOperationException {
        Class<? extends Event> signalClass = (Class<? extends Event>) Class.forName(SIGNAL_EVENT_CLASS);

        signalGetPlayerMethod = signalClass.getMethod("getPlayer");
        signalIsAfkMethod = signalClass.getMethod("isAfk");
        signalIsParticleAfkModeEnabledMethod = signalClass.getMethod("isParticleAfkModeEnabled");

        Bukkit.getPluginManager().registerEvent(
                signalClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> updateFromSignal(event),
                plugin,
                true
        );
    }

    private void updateFromSignal(Event event) {
        if (signalGetPlayerMethod == null || signalIsAfkMethod == null || signalIsParticleAfkModeEnabledMethod == null) {
            return;
        }
        try {
            Object playerValue = signalGetPlayerMethod.invoke(event);
            if (!(playerValue instanceof Player player)) {
                return;
            }
            Object afkValue = signalIsAfkMethod.invoke(event);
            Object particleModeValue = signalIsParticleAfkModeEnabledMethod.invoke(event);
            if (!(afkValue instanceof Boolean afk) || !(particleModeValue instanceof Boolean particleModeEnabled)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            afkStateByPlayer.put(playerId, afk);
            particleModeByPlayer.put(playerId, particleModeEnabled);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINEST, "Failed to read BMEssentials AFK signal event.", ex);
        }
    }

    private void cacheStates(Player player) {
        UUID playerId = player.getUniqueId();
        particleModeByPlayer.put(playerId, queryParticleMode(player));
        afkStateByPlayer.put(playerId, queryAfk(player));
    }

    private boolean queryParticleMode(Player player) {
        if (particleModeIsEnabledMethod == null) {
            return false;
        }
        try {
            Object result = particleModeIsEnabledMethod.invoke(null, player);
            return result instanceof Boolean value && value;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean queryAfk(Player player) {
        if (afkManagerGetInstanceMethod == null || afkManagerIsAfkMethod == null) {
            return false;
        }
        try {
            Object afkManager = afkManagerGetInstanceMethod.invoke(null);
            if (afkManager == null) {
                return false;
            }
            Object result = afkManagerIsAfkMethod.invoke(afkManager, player);
            return result instanceof Boolean value && value;
        } catch (Exception ex) {
            return false;
        }
    }
}
