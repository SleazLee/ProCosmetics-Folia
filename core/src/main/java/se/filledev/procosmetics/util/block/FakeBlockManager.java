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
package se.filledev.procosmetics.util.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class FakeBlockManager {

    private static final double VIEW_RANGE_SQUARED = 64.0d * 64.0d;
    public static final long PERMANENT = -1L;

    /**
     * Materials that are never allowed to be replaced.
     */
    private static final Set<Material> BLOCKED_MATERIALS = EnumSet.of(
            Material.BARRIER,
            Material.CACTUS,
            Material.SLIME_BLOCK,
            Material.HONEY_BLOCK,
            Material.TNT,
            Material.GLOWSTONE,
            Material.NOTE_BLOCK,
            Material.CRAFTING_TABLE,
            Material.OBSERVER,
            Material.DIRT_PATH,
            Material.FARMLAND,
            Material.MAGMA_BLOCK,
            Material.REDSTONE_BLOCK,
            Material.REDSTONE_LAMP,
            Material.TARGET
    );

    /**
     * Material tags that are never allowed to be replaced.
     */
    private static final List<Tag<Material>> BLOCKED_TAGS = List.of(
            Tag.SLABS,
            Tag.STAIRS,
            Tag.DOORS,
            Tag.TRAPDOORS,
            Tag.BUTTONS,
            Tag.PRESSURE_PLATES,
            Tag.BEDS,
            Tag.ALL_SIGNS,
            Tag.BANNERS,
            Tag.WALLS,
            Tag.FENCES,
            Tag.FENCE_GATES,
            Tag.SHULKER_BOXES,
            Tag.ICE,
            Tag.WOOL,
            Tag.WOOL_CARPETS,
            Tag.LANTERNS,
            Tag.CANDLE_CAKES,
            Tag.CAULDRONS,
            Tag.ANVIL,
            Tag.IMPERMEABLE // all glass blocks, including stained variants
    );

    protected final ProCosmeticsPlugin plugin;

    private final ConcurrentMap<BlockPosition, TrackedBlock> trackedBlocks = new ConcurrentHashMap<>();
    private final AtomicLong currentTick = new AtomicLong();
    private final AtomicLong revision = new AtomicLong();
    private final Object taskLock = new Object();

    private Scheduler.Task task;
    private volatile boolean shuttingDown;

    public FakeBlockManager(ProCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends a fake block to nearby players and tracks it.
     *
     * @param block         the block to change client-side
     * @param blockData     the fake block data to display
     * @param overwrite     whether an already tracked fake block may be replaced
     * @param durationTicks how long the fake block lasts in ticks, or {@link #PERMANENT}
     * @return true if the fake block was set
     */
    public boolean setFakeBlock(Block block, BlockData blockData, boolean overwrite, long durationTicks) {
        if (!overwrite && isFakeBlock(block)) {
            return false;
        }
        if (shuttingDown || !canSetFakeBlock(block)) {
            return false;
        }
        BlockPosition position = BlockPosition.from(block);
        TrackedBlock trackedBlock = new TrackedBlock(
                block.getWorld(),
                blockData.clone(),
                durationTicks < 0 ? PERMANENT : currentTick.get() + durationTicks,
                revision.incrementAndGet()
        );

        synchronized (taskLock) {
            if (shuttingDown) {
                return false;
            }
            MetadataUtil.setCustomBlock(block);

            if (overwrite) {
                trackedBlocks.put(position, trackedBlock);
            } else if (trackedBlocks.putIfAbsent(position, trackedBlock) != null) {
                return false;
            }
        }
        broadcastFakeBlock(position, trackedBlock);
        startTaskIfNeeded();
        return true;
    }

    public boolean setFakeBlock(Block block, Material material, boolean overwrite, long durationTicks) {
        if (!material.isBlock()) {
            return false;
        }
        return setFakeBlock(block, material.createBlockData(), overwrite, durationTicks);
    }

    public boolean setFakeBlock(Block block, ItemStack itemStack, boolean overwrite, long durationTicks) {
        return setFakeBlock(block, itemStack.getType(), overwrite, durationTicks);
    }

    public boolean setFakeBlock(Block block, ItemStack itemStack, boolean overwrite) {
        return setFakeBlock(block, itemStack, overwrite, PERMANENT);
    }

    /**
     * Restores the original (server-side) block data for all players and
     * stops tracking the block.
     */
    public void resetFakeBlock(Block block) {
        BlockPosition position = BlockPosition.from(block);
        TrackedBlock trackedBlock = trackedBlocks.remove(position);

        if (trackedBlock != null) {
            dispatchRestore(position, trackedBlock);
        }
        stopTaskIfIdle();
    }

    public void shutdown() {
        synchronized (taskLock) {
            shuttingDown = true;

            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        for (Map.Entry<BlockPosition, TrackedBlock> entry : trackedBlocks.entrySet()) {
            if (trackedBlocks.remove(entry.getKey(), entry.getValue())) {
                dispatchRestore(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Re-sends all tracked fake blocks in range to the given player.
     */
    public void refresh(Player player) {
        Scheduler.run(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location playerLocation = player.getLocation();

            for (Map.Entry<BlockPosition, TrackedBlock> entry : trackedBlocks.entrySet()) {
                if (trackedBlocks.get(entry.getKey()) == entry.getValue()
                        && isInRange(playerLocation, entry.getKey())) {
                    player.sendBlockChange(entry.getValue().location(entry.getKey()), entry.getValue().blockData());
                }
            }
        });
    }

    public boolean isFakeBlock(Block block) {
        return trackedBlocks.containsKey(BlockPosition.from(block)) || MetadataUtil.isCustomBlock(block);
    }

    public int getTrackedBlockCount() {
        return trackedBlocks.size();
    }

    /**
     * Checks whether a block is suitable for a client-side replacement.
     */
    public boolean canSetFakeBlock(Block block) {
        Material material = block.getType();

        if (!material.isBlock() || !material.isSolid() || !material.isOccluding()) {
            return false;
        }
        if (BLOCKED_MATERIALS.contains(material)) {
            return false;
        }
        for (Tag<Material> tag : BLOCKED_TAGS) {
            if (tag.isTagged(material)) {
                return false;
            }
        }
        BlockData blockData = block.getBlockData();

        if (blockData instanceof Openable // doors, trapdoors, fence gates
                || blockData instanceof Bed
                || blockData instanceof Powerable // buttons, levers, plates
                || blockData instanceof AnaloguePowerable
                || blockData instanceof Piston
                || blockData instanceof TechnicalPiston) {
            return false;
        }
        // Rejects every block entity: chests, furnaces, hoppers, dispensers,
        // droppers, beacons, brewing stands, jukeboxes, enchanting tables,
        // daylight detectors, etc.
        return !(block.getState() instanceof TileState);
    }

    private void tick() {
        long tick = currentTick.incrementAndGet();

        for (Map.Entry<BlockPosition, TrackedBlock> entry : trackedBlocks.entrySet()) {
            if (entry.getValue().isExpired(tick)
                    && trackedBlocks.remove(entry.getKey(), entry.getValue())) {
                dispatchRestore(entry.getKey(), entry.getValue());
            }
        }
        stopTaskIfIdle();
    }

    private void startTaskIfNeeded() {
        synchronized (taskLock) {
            if (!shuttingDown && task == null && !trackedBlocks.isEmpty()) {
                task = Scheduler.runTimer(this::tick, 1L, 1L);
            }
        }
    }

    private void stopTaskIfIdle() {
        synchronized (taskLock) {
            if (task != null && trackedBlocks.isEmpty()) {
                task.cancel();
                task = null;
            }
        }
    }

    private void dispatchRestore(BlockPosition position, TrackedBlock expiredBlock) {
        if (!plugin.isEnabled()) {
            return;
        }
        Location location = expiredBlock.location(position);
        Scheduler.run(location, () -> restoreOnRegion(position, location));
    }

    private void restoreOnRegion(BlockPosition position, Location location) {
        FakeBlockManager currentManager = plugin.getFakeBlockManager();

        if (trackedBlocks.containsKey(position)
                || currentManager != this && currentManager.trackedBlocks.containsKey(position)) {
            return;
        }
        Block block = location.getBlock();
        BlockData blockData = block.getBlockData();
        MetadataUtil.removeCustomBlock(block);
        broadcastRestoredBlock(position, block.getWorld(), blockData);
    }

    private void broadcastFakeBlock(BlockPosition position, TrackedBlock expected) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Scheduler.run(player, () -> {
                if (player.isOnline()
                        && trackedBlocks.get(position) == expected
                        && isInRange(player.getLocation(), position)) {
                    player.sendBlockChange(expected.location(position), expected.blockData());
                }
            });
        }
    }

    private void broadcastRestoredBlock(BlockPosition position, World world, BlockData blockData) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Scheduler.run(player, () -> {
                if (player.isOnline()
                        && !trackedBlocks.containsKey(position)
                        && isInRange(player.getLocation(), position)) {
                    player.sendBlockChange(position.location(world), blockData);
                }
            });
        }
    }

    private boolean isInRange(Location playerLocation, BlockPosition position) {
        World playerWorld = playerLocation.getWorld();

        if (playerWorld == null || !playerWorld.getUID().equals(position.worldId())) {
            return false;
        }
        double x = playerLocation.getX() - position.x();
        double y = playerLocation.getY() - position.y();
        double z = playerLocation.getZ() - position.z();
        return x * x + y * y + z * z < VIEW_RANGE_SQUARED;
    }

    private record BlockPosition(UUID worldId, int x, int y, int z) {

        static BlockPosition from(Block block) {
            return new BlockPosition(
                    block.getWorld().getUID(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
        }

        Location location(World world) {
            return new Location(world, x, y, z);
        }
    }

    /**
     * State for a single tracked fake block.
     *
     * @param world       the world used to dispatch region-owned work
     * @param blockData   the fake data currently shown to players
     * @param expiryTick  internal tick at which the block expires, or {@link #PERMANENT}
     * @param revision    unique revision used to reject stale expiry work
     */
    private record TrackedBlock(World world, BlockData blockData, long expiryTick, long revision) {

        Location location(BlockPosition position) {
            return position.location(world);
        }

        boolean isExpired(long currentTick) {
            return expiryTick != PERMANENT && currentTick >= expiryTick;
        }
    }
}
