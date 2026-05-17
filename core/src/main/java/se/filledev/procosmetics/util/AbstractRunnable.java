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

import org.bukkit.plugin.Plugin;

public abstract class AbstractRunnable implements Runnable {

    private Scheduler.Task task;

    public Scheduler.Task runTask(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.run(this));
    }

    public Scheduler.Task runTaskAsynchronously(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.runAsync(this));
    }

    public Scheduler.Task runTaskLater(Plugin plugin, long delay) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.runLater(this, delay));
    }

    public Scheduler.Task runTaskLaterAsynchronously(Plugin plugin, long delay) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.runAsyncLater(this, delay));
    }

    public Scheduler.Task runTaskTimer(Plugin plugin, long delay, long period) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.runTimer(this, delay, period));
    }

    public Scheduler.Task runTaskTimerAsynchronously(Plugin plugin, long delay, long period) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Scheduler.runAsyncTimer(this, delay, period));
    }

    private Scheduler.Task setupTask(Scheduler.Task task) {
        this.task = task;
        return task;
    }

    public void cancel() {
        if (isRunning()) {
            task.cancel();
            task = null;
        }
    }

    public boolean isRunning() {
        return task != null;
    }

    private void checkNotYetScheduled() {
        if (task != null) {
            throw new IllegalStateException("Already scheduled");
        }
    }
}
