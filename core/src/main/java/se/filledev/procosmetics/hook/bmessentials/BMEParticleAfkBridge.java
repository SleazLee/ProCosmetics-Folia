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
    private static final String BALLOON_MODE_CLASS = "at.sleazlee.bmessentials.Cosmetics.BalloonAfkModeState";
    private static final String PARTICLE_SIGNAL_EVENT_CLASS = "at.sleazlee.bmessentials.Cosmetics.event.ParticleAfkStateSignalEvent";
    private static final String BALLOON_SIGNAL_EVENT_CLASS = "at.sleazlee.bmessentials.Cosmetics.event.BalloonAfkStateSignalEvent";

    private final ProCosmeticsPlugin plugin;
    private final Map<UUID, Boolean> afkStateByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> particleModeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> balloonModeByPlayer = new ConcurrentHashMap<>();

    private @Nullable Method afkManagerGetInstanceMethod;
    private @Nullable Method afkManagerIsAfkMethod;
    private @Nullable Method particleModeIsEnabledMethod;
    private @Nullable Method balloonModeIsEnabledMethod;
    private @Nullable Method particleSignalGetPlayerMethod;
    private @Nullable Method particleSignalIsAfkMethod;
    private @Nullable Method particleSignalIsParticleAfkModeEnabledMethod;
    private @Nullable Method balloonSignalGetPlayerMethod;
    private @Nullable Method balloonSignalIsAfkMethod;
    private @Nullable Method balloonSignalIsBalloonAfkModeEnabledMethod;

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
            Class<?> balloonModeClass = Class.forName(BALLOON_MODE_CLASS);
            balloonModeIsEnabledMethod = balloonModeClass.getMethod("isEnabled", Player.class);

            registerParticleSignalListener();
            registerBalloonSignalListener();

            Bukkit.getPluginManager().registerEvents(this, plugin);
            available = true;

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                cacheStates(onlinePlayer);
            }

            plugin.getLogger().info("Hooked into BMEssentials AFK particle/balloon bridge.");
        } catch (ReflectiveOperationException ex) {
            available = false;
            plugin.getLogger().log(Level.WARNING, "Failed to initialize BMEssentials AFK particle/balloon bridge: " + ex.getMessage());
            shutdown();
        }
    }

    public void shutdown() {
        available = false;
        afkStateByPlayer.clear();
        particleModeByPlayer.clear();
        balloonModeByPlayer.clear();
        afkManagerGetInstanceMethod = null;
        afkManagerIsAfkMethod = null;
        particleModeIsEnabledMethod = null;
        balloonModeIsEnabledMethod = null;
        particleSignalGetPlayerMethod = null;
        particleSignalIsAfkMethod = null;
        particleSignalIsParticleAfkModeEnabledMethod = null;
        balloonSignalGetPlayerMethod = null;
        balloonSignalIsAfkMethod = null;
        balloonSignalIsBalloonAfkModeEnabledMethod = null;
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

    public boolean shouldRenderBalloon(Player player) {
        if (!available) {
            return true;
        }
        Plugin bmePlugin = Bukkit.getPluginManager().getPlugin(BME_PLUGIN);
        if (bmePlugin == null || !bmePlugin.isEnabled()) {
            return true;
        }
        UUID playerId = player.getUniqueId();

        boolean onlyWhileAfk = balloonModeByPlayer.computeIfAbsent(playerId, uuid -> queryBalloonMode(player));
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
        balloonModeByPlayer.remove(playerId);
    }

    @SuppressWarnings("unchecked")
    private void registerParticleSignalListener() throws ReflectiveOperationException {
        Class<? extends Event> signalClass = (Class<? extends Event>) Class.forName(PARTICLE_SIGNAL_EVENT_CLASS);

        particleSignalGetPlayerMethod = signalClass.getMethod("getPlayer");
        particleSignalIsAfkMethod = signalClass.getMethod("isAfk");
        particleSignalIsParticleAfkModeEnabledMethod = signalClass.getMethod("isParticleAfkModeEnabled");

        Bukkit.getPluginManager().registerEvent(
                signalClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> updateFromParticleSignal(event),
                plugin,
                true
        );
    }

    @SuppressWarnings("unchecked")
    private void registerBalloonSignalListener() throws ReflectiveOperationException {
        Class<? extends Event> signalClass = (Class<? extends Event>) Class.forName(BALLOON_SIGNAL_EVENT_CLASS);

        balloonSignalGetPlayerMethod = signalClass.getMethod("getPlayer");
        balloonSignalIsAfkMethod = signalClass.getMethod("isAfk");
        balloonSignalIsBalloonAfkModeEnabledMethod = signalClass.getMethod("isBalloonAfkModeEnabled");

        Bukkit.getPluginManager().registerEvent(
                signalClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> updateFromBalloonSignal(event),
                plugin,
                true
        );
    }

    private void updateFromParticleSignal(Event event) {
        if (particleSignalGetPlayerMethod == null || particleSignalIsAfkMethod == null || particleSignalIsParticleAfkModeEnabledMethod == null) {
            return;
        }
        try {
            Object playerValue = particleSignalGetPlayerMethod.invoke(event);
            if (!(playerValue instanceof Player player)) {
                return;
            }
            Object afkValue = particleSignalIsAfkMethod.invoke(event);
            Object particleModeValue = particleSignalIsParticleAfkModeEnabledMethod.invoke(event);
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

    private void updateFromBalloonSignal(Event event) {
        if (balloonSignalGetPlayerMethod == null || balloonSignalIsAfkMethod == null || balloonSignalIsBalloonAfkModeEnabledMethod == null) {
            return;
        }
        try {
            Object playerValue = balloonSignalGetPlayerMethod.invoke(event);
            if (!(playerValue instanceof Player player)) {
                return;
            }
            Object afkValue = balloonSignalIsAfkMethod.invoke(event);
            Object balloonModeValue = balloonSignalIsBalloonAfkModeEnabledMethod.invoke(event);
            if (!(afkValue instanceof Boolean afk) || !(balloonModeValue instanceof Boolean balloonModeEnabled)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            afkStateByPlayer.put(playerId, afk);
            balloonModeByPlayer.put(playerId, balloonModeEnabled);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINEST, "Failed to read BMEssentials balloon AFK signal event.", ex);
        }
    }

    private void cacheStates(Player player) {
        UUID playerId = player.getUniqueId();
        particleModeByPlayer.put(playerId, queryParticleMode(player));
        balloonModeByPlayer.put(playerId, queryBalloonMode(player));
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

    private boolean queryBalloonMode(Player player) {
        if (balloonModeIsEnabledMethod == null) {
            return false;
        }
        try {
            Object result = balloonModeIsEnabledMethod.invoke(null, player);
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
