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
package se.filledev.procosmetics.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.nms.NMSEntity;

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

    public static Task run(Entity entity, Runnable runnable) {
        if (IS_FOLIA) {
            ScheduledTask task = entity.getScheduler().run(ProCosmeticsPlugin.getPlugin(),
                    scheduledTask -> runnable.run(), null);

            if (task == null) {
                return Task.dummy();
            }
            return new Task(task);
        }
        return new Task(Bukkit.getScheduler().runTask(ProCosmeticsPlugin.getPlugin(), runnable));
    }

    /**
     * Runs entity-backed cosmetic work on the entity's owning region when Folia is active.
     *
     * <p>Several gadgets spawn projectiles, seats, or display helper entities that can move away
     * from the player who started the gadget. If the normal cosmetic tick follows the player into a
     * different region, reading or mutating the retained entity from that player region can trip
     * Folia's ownership checks. This helper keeps that entity state on the entity scheduler, falls
     * back to a stable location when one is available, and skips the work on Folia when neither
     * region target is usable.</p>
     *
     * @param entity the Bukkit entity whose owning scheduler should run the work
     * @param fallbackLocation location to use when the entity scheduler cannot accept the task
     * @param runnable the region-owned work to run
     * @return the scheduled task handle
     */
    public static Task runOwned(@Nullable Entity entity, @Nullable Location fallbackLocation, Runnable runnable) {
        if (IS_FOLIA && entity != null) {
            ScheduledTask task = entity.getScheduler().run(ProCosmeticsPlugin.getPlugin(),
                    scheduledTask -> runnable.run(), null);

            if (task != null) {
                return new Task(task);
            }
            if (fallbackLocation == null) {
                return Task.dummy();
            }
        }
        if (fallbackLocation != null) {
            return run(fallbackLocation, runnable);
        }
        return run(runnable);
    }

    /**
     * Runs NMS-backed cosmetic work on the owning region for the backing Bukkit entity.
     *
     * <p>Packet-only cosmetics still expose a Bukkit wrapper for display/equipment APIs. Folia
     * validates those wrapper calls against the entity's current coordinates, so callers should use
     * this helper before touching Bukkit state or moving/removing the wrapper.</p>
     *
     * @param entity the NMS entity whose backing entity owns the work
     * @param runnable the region-owned work to run
     * @return the scheduled task handle
     */
    public static Task runOwned(@Nullable NMSEntity entity, Runnable runnable) {
        if (entity == null) {
            return run(runnable);
        }
        return runOwned(entity.getBukkitEntity(), entity.getPreviousLocation(), runnable);
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

    public static Task runLater(Entity entity, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return run(entity, runnable);
        }
        if (IS_FOLIA) {
            ScheduledTask task = entity.getScheduler().runDelayed(ProCosmeticsPlugin.getPlugin(),
                    scheduledTask -> runnable.run(), null, Math.max(1L, delayTicks));

            if (task == null) {
                return Task.dummy();
            }
            return new Task(task);
        }
        return new Task(Bukkit.getScheduler().runTaskLater(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks));
    }

    /**
     * Delays entity-backed cosmetic work on the entity's owning region when Folia is active.
     *
     * <p>Delayed cleanup is especially sensitive because projectiles and ride seats may have crossed
     * a region boundary by the time the task fires. Scheduling against the entity keeps cleanup and
     * state reads on the same region that owns that entity at execution time. If Folia can no longer
     * schedule the entity and no stable fallback location exists, the delayed cleanup is skipped
     * instead of running from the global region.</p>
     *
     * @param entity the Bukkit entity whose owning scheduler should run the work
     * @param fallbackLocation location to use when the entity scheduler cannot accept the task
     * @param runnable the region-owned work to run
     * @param delayTicks the delay in server ticks
     * @return the scheduled task handle
     */
    public static Task runLaterOwned(@Nullable Entity entity,
                                     @Nullable Location fallbackLocation,
                                     Runnable runnable,
                                     long delayTicks) {
        if (delayTicks <= 0) {
            return runOwned(entity, fallbackLocation, runnable);
        }
        if (IS_FOLIA && entity != null) {
            ScheduledTask task = entity.getScheduler().runDelayed(ProCosmeticsPlugin.getPlugin(),
                    scheduledTask -> runnable.run(), null, Math.max(1L, delayTicks));

            if (task != null) {
                return new Task(task);
            }
            if (fallbackLocation == null) {
                return Task.dummy();
            }
        }
        if (fallbackLocation != null) {
            return runLater(fallbackLocation, runnable, delayTicks);
        }
        return runLater(runnable, delayTicks);
    }

    /**
     * Delays NMS-backed cosmetic work on the owning region for the backing Bukkit entity.
     *
     * <p>This is used for virtual cosmetic entities whose Bukkit state may be read during cleanup.
     * The previous packet location gives Folia a stable fallback region when the entity scheduler is
     * unavailable, such as after the backing entity has already been removed.</p>
     *
     * @param entity the NMS entity whose backing entity owns the work
     * @param runnable the region-owned work to run
     * @param delayTicks the delay in server ticks
     * @return the scheduled task handle
     */
    public static Task runLaterOwned(@Nullable NMSEntity entity, Runnable runnable, long delayTicks) {
        if (entity == null) {
            return runLater(runnable, delayTicks);
        }
        return runLaterOwned(entity.getBukkitEntity(), entity.getPreviousLocation(), runnable, delayTicks);
    }

    public static Task runTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return new Task(Bukkit.getRegionScheduler().runAtFixedRate(ProCosmeticsPlugin.getPlugin(), location,
                    task -> runnable.run(), Math.max(1L, delayTicks), periodTicks));
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(ProCosmeticsPlugin.getPlugin(), runnable, delayTicks, periodTicks));
    }

    public static Task runTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            ScheduledTask task = entity.getScheduler().runAtFixedRate(ProCosmeticsPlugin.getPlugin(),
                    scheduledTask -> runnable.run(), null, Math.max(1L, delayTicks), Math.max(1L, periodTicks));

            if (task == null) {
                return Task.dummy();
            }
            return new Task(task);
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
