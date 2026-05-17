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
package se.filledev.procosmetics.nms;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.nms.EntityTracker;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.util.Scheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class EntityTrackerImpl implements EntityTracker, Runnable {

    private static final ProCosmeticsPlugin PLUGIN = ProCosmeticsPlugin.getPlugin();
    private static final long DEFAULT_UPDATE_INTERVAL = 20L;
    private static final long DEFAULT_START_DELAY = 1L;

    private double trackingRange;
    private long updateInterval = DEFAULT_UPDATE_INTERVAL;
    private long startDelay = DEFAULT_START_DELAY;

    private final Set<NMSEntity> entities = ConcurrentHashMap.newKeySet();
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> antiViewers = ConcurrentHashMap.newKeySet();

    private volatile Player owner;
    private volatile UUID ownerUUID;

    private volatile Predicate<Player> visibilityPredicate;
    private volatile BiPredicate<Player, Player> ownerVisibilityPredicate;

    private volatile Scheduler.Task trackingTask;
    private volatile boolean tracking;

    public EntityTrackerImpl() {
        this(null);
    }

    public EntityTrackerImpl(@Nullable Player owner) {
        trackingRange = PLUGIN.getConfigManager().getMainConfig().getDouble("settings.entity_tracking_range");
        setOwner(owner);
    }

    @Override
    public void addEntity(NMSEntity nmsEntity) {
        entities.add(nmsEntity);
    }

    @Override
    public void removeEntity(NMSEntity nmsEntity) {
        if (entities.remove(nmsEntity)) {
            // Despawn entity for all current viewers
            nmsEntity.despawn(viewers);
        }
    }

    @Override
    public Collection<NMSEntity> getEntities() {
        return entities;
    }

    @Override
    public void clearEntities() {
        Set<NMSEntity> entitiesToRemove = Set.copyOf(entities);

        for (NMSEntity entity : entitiesToRemove) {
            removeEntity(entity);
        }
    }

    @Override
    public void startTracking() {
        if (!isTracking()) {
            tracking = true;
            scheduleNextRun(startDelay);
        }
    }

    @Override
    public void stopTracking() {
        if (isTracking()) {
            tracking = false;

            Scheduler.Task task = trackingTask;
            if (task != null) {
                task.cancel();
                trackingTask = null;
            }

            // Despawn all entities for all viewers
            removeViewers(Set.copyOf(viewers));
        }
    }

    @Override
    public boolean isTracking() {
        return tracking;
    }

    @Override
    public void destroy() {
        stopTracking();
        clearEntities();
        clearAntiViewers();
    }

    @Override
    public Collection<Player> getViewers() {
        return viewers;
    }

    @Override
    public void addViewer(Player player) {
        if (viewers.add(player)) {
            Collection<Player> players = Collections.singleton(player);

            for (NMSEntity entity : entities) {
                entity.spawn(players);
            }
        }
    }

    @Override
    public void addViewers(Collection<Player> players) {
        if (players.isEmpty()) {
            return;
        }
        viewers.addAll(players);

        for (NMSEntity entity : Set.copyOf(entities)) {
            entity.spawn(players);
        }
    }

    @Override
    public void removeViewer(Player player) {
        removeViewers(Collections.singleton(player));
    }

    @Override
    public void removeViewers(Collection<Player> players) {
        if (players.isEmpty()) {
            return;
        }
        // Despawn all entities for these viewers in batch
        Collection<Player> playersToRemove = Set.copyOf(players);

        if (playersToRemove.isEmpty()) {
            return;
        }
        for (NMSEntity entity : Set.copyOf(entities)) {
            entity.despawn(playersToRemove);
        }
        viewers.removeAll(playersToRemove);
    }

    @Override
    public boolean isViewer(Player player) {
        return viewers.contains(player);
    }

    @Override
    public void addAntiViewer(Player player) {
        antiViewers.add(player.getUniqueId());
        removeViewer(player);
    }

    @Override
    public void removeAntiViewer(Player player) {
        antiViewers.remove(player.getUniqueId());
    }

    @Override
    public boolean isAntiViewer(Player player) {
        return antiViewers.contains(player.getUniqueId());
    }

    @Override
    public Collection<UUID> getAntiViewers() {
        return antiViewers;
    }

    @Override
    public void clearAntiViewers() {
        antiViewers.clear();
    }

    @Override
    public void setOwner(@Nullable Player owner) {
        this.owner = owner;
        this.ownerUUID = owner != null ? owner.getUniqueId() : null;

        // Update viewers based on the new owner
        if (isTracking()) {
            updateViewers();
        }
    }

    @Override
    @Nullable
    public Player getOwner() {
        return owner;
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public void setTrackingRange(double range) {
        this.trackingRange = range;
    }

    @Override
    public double getTrackingRange() {
        return trackingRange;
    }

    @Override
    public void setUpdateInterval(long interval) {
        this.updateInterval = interval;

        // Restart tracking with the new interval if currently running
        if (isTracking()) {
            stopTracking();
            startTracking();
        }
    }

    @Override
    public long getUpdateInterval() {
        return updateInterval;
    }

    @Override
    public void setStartDelay(long delay) {
        this.startDelay = delay;
    }

    @Override
    public long getStartDelay() {
        return startDelay;
    }

    @Override
    public void setVisibilityPredicate(@Nullable Predicate<Player> predicate) {
        this.visibilityPredicate = predicate;
    }

    @Override
    public void setOwnerVisibilityPredicate(@Nullable BiPredicate<Player, Player> predicate) {
        this.ownerVisibilityPredicate = predicate;
    }

    @Override
    public void respawnAt(Location location) {
        // Despawn all entities for all viewers
        removeViewers(viewers);

        // Update entity positions
        for (NMSEntity entity : entities) {
            entity.setPositionRotation(location);
        }
        // Force update to respawn entities
        updateViewers();
    }

    @Override
    @Nullable
    public Location getTrackingLocation() {
        for (NMSEntity entity : entities) {
            return entity.getPreviousLocation();
        }
        return null;
    }


    @Override
    public void destroyAfter(int ticks) {
        Location trackingLocation = getTrackingLocation();
        if (trackingLocation != null) {
            Scheduler.runLater(trackingLocation, this::destroy, ticks);
            return;
        }
        Scheduler.runLater(this::destroy, ticks);
    }

    @Override
    public void updateViewers() {
        // Force immediate update
        tick(false);
    }

    @Override
    public void run() {
        tick(true);
    }

    private void tick(boolean shouldReschedule) {
        if (!tracking) {
            return;
        }

        if (entities.isEmpty()) {
            if (shouldReschedule) {
                scheduleNextRun(updateInterval);
            }
            return;
        }
        Location trackingLocation = getTrackingLocation();

        if (trackingLocation == null) {
            if (shouldReschedule) {
                scheduleNextRun(updateInterval);
            }
            return;
        }
        if (Scheduler.isFolia()) {
            tickFolia(trackingLocation.clone(), shouldReschedule);
            return;
        }
        tickSynchronous(trackingLocation, shouldReschedule);
    }

    /**
     * Evaluates packet-entity visibility on each player's owning region.
     *
     * <p>The tracker itself is anchored to the cosmetic entity location, but Folia requires player
     * location reads to happen on the player's own scheduler. This method splits the work: player
     * visibility is calculated on the player region, then the packet spawn/despawn decision is
     * applied back on the cosmetic entity's tracking region.</p>
     *
     * @param trackingLocation the region location of the tracked virtual entities
     * @param shouldReschedule whether this tick should queue the next tracker update
     */
    private void tickFolia(Location trackingLocation, boolean shouldReschedule) {
        // Remove offline viewers
        viewers.removeIf(player -> !player.isOnline());

        double rangeSquared = trackingRange * trackingRange;

        for (Player player : PLUGIN.getServer().getOnlinePlayers()) {
            Scheduler.run(player, () -> evaluatePlayerVisibilityOnPlayerRegion(player, trackingLocation, rangeSquared));
        }

        if (shouldReschedule) {
            scheduleNextRun(updateInterval);
        }
    }

    /**
     * Reads one player's visibility inputs from that player's owning region.
     *
     * <p>After the read-only checks are complete, the viewer set is changed on the tracked
     * cosmetic's region so virtual entity packet generation still happens beside the virtual
     * entity state.</p>
     *
     * @param player the player being evaluated
     * @param trackingLocation the virtual entity location used for range checks
     * @param rangeSquared the squared visibility range
     */
    private void evaluatePlayerVisibilityOnPlayerRegion(Player player, Location trackingLocation, double rangeSquared) {
        if (!tracking) {
            return;
        }
        if (!player.isOnline() || !player.isValid()) {
            Scheduler.run(trackingLocation, () -> viewers.remove(player));
            return;
        }
        Location playerLocation = player.getLocation();
        boolean shouldView = shouldPlayerSeeEntities(player, playerLocation, trackingLocation, rangeSquared);

        Scheduler.run(trackingLocation, () -> applyViewerDecision(player, shouldView));
    }

    /**
     * Applies a player visibility decision from the tracked cosmetic's region.
     *
     * <p>Spawning virtual entities may read Bukkit wrapper state for equipment and display data, so
     * the final add/remove step stays anchored to the cosmetic location instead of the player region
     * that produced the decision.</p>
     *
     * @param player the player whose viewer state should change
     * @param shouldView whether the player should currently see the tracked entities
     */
    private void applyViewerDecision(Player player, boolean shouldView) {
        if (!tracking) {
            return;
        }
        boolean currentlyViewing = isViewer(player);

        if (shouldView && !currentlyViewing) {
            addViewer(player);
        } else if (!shouldView && currentlyViewing) {
            removeViewer(player);
        }
    }

    /**
     * Performs the legacy single-thread visibility scan used by Bukkit and Paper.
     *
     * <p>Non-Folia servers still have one owning server thread, so the original nearby-player scan
     * remains cheaper and keeps packet updates grouped in one tick.</p>
     *
     * @param trackingLocation the virtual entity location used for range checks
     * @param shouldReschedule whether this tick should queue the next tracker update
     */
    private void tickSynchronous(Location trackingLocation, boolean shouldReschedule) {
        Set<Player> playersToAdd = new HashSet<>();
        Set<Player> playersToRemove = new HashSet<>();

        viewers.removeIf(player -> !player.isOnline());

        double rangeSquared = trackingRange * trackingRange;

        for (Player player : PLUGIN.getServer().getOnlinePlayers()) {
            if (!player.isValid()) {
                continue;
            }
            Location playerLocation = player.getLocation();
            boolean shouldView = shouldPlayerSeeEntities(player, playerLocation, trackingLocation, rangeSquared);
            boolean currentlyViewing = isViewer(player);

            if (shouldView && !currentlyViewing) {
                playersToAdd.add(player);
            } else if (!shouldView && currentlyViewing) {
                playersToRemove.add(player);
            }
        }

        if (!playersToAdd.isEmpty()) {
            addViewers(playersToAdd);
        }
        if (!playersToRemove.isEmpty()) {
            removeViewers(playersToRemove);
        }

        if (shouldReschedule) {
            scheduleNextRun(updateInterval);
        }
    }

    private void scheduleNextRun(long delayTicks) {
        if (!tracking) {
            return;
        }

        Runnable taskRunnable = this::run;
        Location trackingLocation = getTrackingLocation();

        trackingTask = trackingLocation != null
                ? Scheduler.runLater(trackingLocation, taskRunnable, delayTicks)
                : Scheduler.runLater(taskRunnable, delayTicks);
    }

    /**
     * Determines if a player should see the entities based on all visibility rules.
     */
    private boolean shouldPlayerSeeEntities(Player player, Location playerLocation, Location trackingLocation, double rangeSquared) {
        // Check anti-viewers
        if (isAntiViewer(player)) {
            return false;
        }

        // Check world and range
        if (!playerLocation.getWorld().equals(trackingLocation.getWorld())
                || playerLocation.distanceSquared(trackingLocation) > rangeSquared) {
            return false;
        }

        // Check owner visibility
        if (owner != null && !canPlayerSeeOwner(player, owner)) {
            return false;
        }

        // Check custom visibility predicate
        if (visibilityPredicate != null && !visibilityPredicate.test(player)) {
            return false;
        }

        // Check owner visibility predicate
        if (ownerVisibilityPredicate != null && owner != null && !ownerVisibilityPredicate.test(player, owner)) {
            return false;
        }
        return true;
    }

    /**
     * Checks if a player can see the owner using Bukkit's canSee method.
     */
    private boolean canPlayerSeeOwner(Player player, Player owner) {
        if (player.equals(owner)) {
            return true; // Owner can always see their own entities
        }
        if (!owner.isOnline()) {
            return false; // Can't see entities of an offline owner
        }
        return player.canSee(owner);
    }

    public static Builder builder() {
        return new BuilderImpl();
    }

    private static class BuilderImpl implements EntityTracker.Builder {

        private double trackingRange = 32.0d;
        private long updateInterval = 20L;
        private long startDelay = 5L;
        private Player owner;
        private Predicate<Player> visibilityPredicate;
        private BiPredicate<Player, Player> ownerVisibilityPredicate;

        @Override
        public Builder trackingRange(double range) {
            this.trackingRange = range;
            return this;
        }

        @Override
        public Builder updateInterval(long interval) {
            this.updateInterval = interval;
            return this;
        }

        @Override
        public Builder startDelay(long delay) {
            this.startDelay = delay;
            return this;
        }

        @Override
        public Builder owner(@Nullable Player owner) {
            this.owner = owner;
            return this;
        }

        @Override
        public Builder visibilityPredicate(@Nullable Predicate<Player> predicate) {
            this.visibilityPredicate = predicate;
            return this;
        }

        @Override
        public Builder ownerVisibilityPredicate(@Nullable BiPredicate<Player, Player> predicate) {
            this.ownerVisibilityPredicate = predicate;
            return this;
        }

        @Override
        public EntityTrackerImpl build() {
            EntityTrackerImpl tracker = new EntityTrackerImpl();
            tracker.setTrackingRange(trackingRange);
            tracker.setUpdateInterval(updateInterval);
            tracker.setStartDelay(startDelay);
            tracker.setOwner(owner);
            tracker.setVisibilityPredicate(visibilityPredicate);
            tracker.setOwnerVisibilityPredicate(ownerVisibilityPredicate);

            return tracker;
        }
    }
}
