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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import se.filledev.procosmetics.ProCosmeticsPlugin;

import java.util.concurrent.TimeUnit;

public final class Scheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            folia = true;
            Bukkit.getLogger().warning("Scheduler detected a Folia server.");
        } catch (ClassNotFoundException e) {
            folia = false;
            Bukkit.getLogger().warning("Scheduler detected a Bukkit/Paper server.");
        }
        IS_FOLIA = folia;
    }

    private Scheduler() {
    }

    public static Task run(Runnable runnable) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().execute(ProCosmeticsPlugin.getPlugin(), runnable);
            return Task.dummy();
        }
        return new Task(Bukkit.getScheduler().runTask(ProCosmeticsPlugin.getPlugin(), runnable));
    }

    public static Task runLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return run(runnable);
        }
        if (IS_FOLIA) {
            return new Task(Bukkit.getGlobalRegionScheduler()
                    .runDelayed(ProCosmeticsPlugin.getPlugin(), task -> runnable.run(), delayTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskLater(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks));
    }

    public static Task runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return new Task(Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(ProCosmeticsPlugin.getPlugin(), task -> runnable.run(),
                            Math.max(1L, delayTicks), periodTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks, periodTicks));
    }

    public static Task runAsync(Runnable runnable) {
        if (IS_FOLIA) {
            return new Task(Bukkit.getAsyncScheduler().runNow(ProCosmeticsPlugin.getPlugin(), task -> runnable.run()));
        }
        return new Task(Bukkit.getScheduler().runTaskAsynchronously(ProCosmeticsPlugin.getPlugin(), runnable));
    }

    public static Task runAsyncLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return runAsync(runnable);
        }
        if (IS_FOLIA) {
            return new Task(Bukkit.getAsyncScheduler().runDelayed(ProCosmeticsPlugin.getPlugin(), task -> runnable.run(),
                    delayTicks * 50L, TimeUnit.MILLISECONDS));
        }
        return new Task(Bukkit.getScheduler().runTaskLaterAsynchronously(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks));
    }

    public static Task runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return new Task(Bukkit.getAsyncScheduler().runAtFixedRate(ProCosmeticsPlugin.getPlugin(), task -> runnable.run(),
                    Math.max(1L, delayTicks) * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS));
        }
        return new Task(Bukkit.getScheduler().runTaskTimerAsynchronously(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks, periodTicks));
    }

    public static Task run(Location location, Runnable runnable) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().execute(ProCosmeticsPlugin.getPlugin(), location, runnable);
            return Task.dummy();
        }
        return new Task(Bukkit.getScheduler().runTask(ProCosmeticsPlugin.getPlugin(), runnable));
    }

    public static Task runLater(Location location, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return run(location, runnable);
        }
        if (IS_FOLIA) {
            return new Task(Bukkit.getRegionScheduler().runDelayed(ProCosmeticsPlugin.getPlugin(), location,
                    task -> runnable.run(), delayTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskLater(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks));
    }

    public static Task runTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return new Task(Bukkit.getRegionScheduler().runAtFixedRate(ProCosmeticsPlugin.getPlugin(), location,
                    task -> runnable.run(), Math.max(1L, delayTicks), periodTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks, periodTicks));
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void cancelTasks() {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(ProCosmeticsPlugin.getPlugin());
            Bukkit.getAsyncScheduler().cancelTasks(ProCosmeticsPlugin.getPlugin());
            return;
        }
        Bukkit.getScheduler().cancelTasks(ProCosmeticsPlugin.getPlugin());
    }

    public static class Task {

        private final ScheduledTask foliaTask;
        private final BukkitTask bukkitTask;

        private Task(ScheduledTask foliaTask, BukkitTask bukkitTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = bukkitTask;
        }

        Task(ScheduledTask foliaTask) {
            this(foliaTask, null);
        }

        Task(BukkitTask bukkitTask) {
            this(null, bukkitTask);
        }

        static Task dummy() {
            return new Task(null, null);
        }

        public void cancel() {
            if (foliaTask != null) {
                foliaTask.cancel();
                return;
            }
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
        }
    }
}
