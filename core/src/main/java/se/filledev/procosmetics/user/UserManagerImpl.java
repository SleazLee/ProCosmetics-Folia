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
package se.filledev.procosmetics.user;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Striped;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.api.user.UserManager;
import se.filledev.procosmetics.util.AbstractRunnable;
import se.filledev.procosmetics.util.Scheduler;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class UserManagerImpl implements UserManager {

    private final ProCosmeticsPlugin plugin;

    private final Cache<@NotNull UUID, @NotNull User> cached = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Cache<@NotNull Integer, @NotNull User> cachedIds = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

    private final Map<UUID, Object> loginLocks = new ConcurrentHashMap<>();
    private final Cache<@NotNull UUID, @NotNull User> connecting = CacheBuilder.newBuilder().expireAfterWrite(40, TimeUnit.SECONDS).build();
    private final Map<UUID, User> connected = new ConcurrentHashMap<>();

    private final Striped<Lock> uuidLocks = Striped.lock(64);
    private final Striped<Lock> nameLocks = Striped.lock(64);
    private final Striped<Lock> idLocks = Striped.lock(64);

    public UserManagerImpl(ProCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new Listeners(), plugin);
        new MovementRunnable().runTaskTimer(plugin, 1L, 2L); // Keep this at least 2 ticks
    }

    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            User user = plugin.getDatabase().loadUser(player.getUniqueId());

            if (user == null) {
                user = plugin.getDatabase().insertUser(player.getUniqueId(), player.getName());
            }

            if (user != null) {
                cached.invalidate(player.getUniqueId());
                cachedIds.invalidate(user.getDatabaseId());
                connected.put(player.getUniqueId(), user);

                if (player.getGameMode() != GameMode.SPECTATOR) {
                    user.equipSavedCosmetics(true);
                }
            }
        }
    }

    private final class Listeners implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
            if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                UUID uuid = event.getUniqueId();
                String name = event.getName();

                connecting.invalidate(uuid);

                synchronized (loginLocks.computeIfAbsent(uuid, _ -> new Object())) {
                    User user = plugin.getDatabase().loadUser(uuid);

                    if (user == null) {
                        user = plugin.getDatabase().insertUser(uuid, name);
                    } else if (!user.getName().equals(name)) {
                        plugin.getDatabase().updateName(user, name);
                    }

                    if (user != null) {
                        connecting.put(uuid, user);
                    }
                    loginLocks.remove(uuid);
                }
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        private void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            connecting.cleanUp();
            User user = connecting.getIfPresent(uuid);

            if (user != null) {
                cached.invalidate(uuid);
                cachedIds.invalidate(user.getDatabaseId());
                connected.put(uuid, user);
                plugin.getDatabase().updateLastSeenAsync(user);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        private void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            User user = connected.remove(player.getUniqueId());

            if (user != null) {
                user.clearAllCosmetics(true, false);
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        private void onLateJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            User user = getConnected(player);

            if (user != null && player.getGameMode() != GameMode.SPECTATOR) {
                user.equipSavedCosmetics(true);
            }
        }
    }

    @Override
    public Collection<User> getAllConnected() {
        return connected.values();
    }

    @Override
    @Nullable
    public User getConnecting(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        connecting.cleanUp();
        return connecting.getIfPresent(uuid);
    }

    @Override
    @Nullable
    public User getConnected(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return connected.get(uuid);
    }

    @Override
    @Nullable
    public User getConnectedOrCached(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        User user = getConnected(uuid);

        if (user == null) {
            cached.cleanUp();
            cachedIds.cleanUp();
            user = cached.getIfPresent(uuid);
        }
        return user;
    }

    @Override
    @Nullable
    public User getConnectedOrCached(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        User user = getConnected(name);

        if (user == null) {
            cached.cleanUp();
            cachedIds.cleanUp();

            for (User cachedUser : cached.asMap().values()) {
                if (cachedUser.getName().equalsIgnoreCase(name)) {
                    user = cachedUser;
                    break;
                }
            }
        }
        return user;
    }

    @Override
    @Nullable
    public User getConnectedOrCached(int id) {
        User user = getConnected(id);

        if (user == null) {
            cached.cleanUp();
            cachedIds.cleanUp();
            user = cachedIds.getIfPresent(id);
        }
        return user;
    }

    @Override
    @Nullable
    public User get(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        User user = getConnectedOrCached(uuid);

        if (user == null) {
            Lock lock = uuidLocks.get(uuid);
            lock.lock();

            try {
                user = getConnectedOrCached(uuid);

                if (user == null) {
                    user = plugin.getDatabase().loadUser(uuid);
                }

                if (user != null) {
                    cached.put(uuid, user);
                    cachedIds.put(user.getDatabaseId(), user);
                }
            } finally {
                lock.unlock();
            }
        }
        return user;
    }

    @Override
    @Nullable
    public User get(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        User user = getConnectedOrCached(name);

        if (user == null) {
            Lock lock = nameLocks.get(name.toLowerCase(Locale.ROOT));
            lock.lock();

            try {
                user = getConnectedOrCached(name);

                if (user == null) {
                    user = plugin.getDatabase().loadUser(name);
                }

                if (user != null) {
                    cached.put(user.getUniqueId(), user);
                    cachedIds.put(user.getDatabaseId(), user);
                }
            } finally {
                lock.unlock();
            }
        }
        return user;
    }

    @Override
    @Nullable
    public User get(int id) {
        User user = getConnectedOrCached(id);

        if (user == null) {
            Lock lock = idLocks.get(id);
            lock.lock();

            try {
                user = getConnectedOrCached(id);

                if (user == null) {
                    user = plugin.getDatabase().loadUser(id);
                }

                if (user != null) {
                    cached.put(user.getUniqueId(), user);
                    cachedIds.put(id, user);
                }
            } finally {
                lock.unlock();
            }
        }
        return user;
    }

    @Override
    public CompletableFuture<@Nullable User> getAsync(@Nullable UUID uuid) {
        return CompletableFuture.supplyAsync(() -> get(uuid), plugin.getAsyncExecutor());
    }

    @Override
    public CompletableFuture<@Nullable User> getAsync(@Nullable String name) {
        return CompletableFuture.supplyAsync(() -> get(name), plugin.getAsyncExecutor());
    }

    @Override
    public CompletableFuture<@Nullable User> getAsync(int id) {
        return CompletableFuture.supplyAsync(() -> get(id), plugin.getAsyncExecutor());
    }

    private final class MovementRunnable extends AbstractRunnable {

        @Override
        public void run() {
            for (User user : connected.values()) {
                Player player = user.getPlayer();

                if (player != null) {
                    Scheduler.run(player, () -> updateMovement(user, player));
                }
            }
        }

        private void updateMovement(User user, Player player) {
            if (!player.isOnline() || connected.get(user.getUniqueId()) != user) {
                return;
            }
            Location location = player.getLocation();
            boolean moving = hasDifference(location, user.getLastLocation());
            user.setMoving(moving);

            if (moving) {
                user.setLastLocation(location);
            }
        }

        private boolean hasDifference(@Nullable Location from, @Nullable Location to) {
            if (from == null || to == null) {
                return true;
            }

            if (from.getWorld() != to.getWorld()) {
                return true;
            }
            return Double.doubleToLongBits(from.getX()) != Double.doubleToLongBits(to.getX())
                    || Double.doubleToLongBits(from.getY()) != Double.doubleToLongBits(to.getY())
                    || Double.doubleToLongBits(from.getZ()) != Double.doubleToLongBits(to.getZ());
        }
    }
}
