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
package se.filledev.procosmetics.util.structure.type;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.nms.EntityTracker;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.util.structure.StructureData;
import se.filledev.procosmetics.api.util.structure.type.BlockDisplayStructure;
import se.filledev.procosmetics.nms.EntityTrackerImpl;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.structure.StructureImpl;

import java.util.Map;

public class BlockDisplayStructureImpl extends StructureImpl<NMSEntity> implements BlockDisplayStructure {

    private static final ProCosmeticsPlugin PLUGIN = ProCosmeticsPlugin.getPlugin();
    private final EntityTracker tracker = new EntityTrackerImpl();

    public BlockDisplayStructureImpl(StructureData data) {
        super(data, block -> block.isPassable() && !block.isLiquid());
    }

    @Override
    public double spawn(Location location) {
        double angle = calculateAngle(location);
        Matrix4f transformationMatrix = new Matrix4f();
        transformationMatrix.identity()
                //.scale(scale)
                //.rotateY(radians)
                .translate(-0.5f, 0.0f, -0.5f);

        for (Map.Entry<Vector, BlockData> entry : data.getPlacement().entrySet()) {
            Vector vector = MathUtil.rotateAroundAxisY(entry.getKey().clone(), angle);
            BlockData blockData = entry.getValue().clone();

            rotate(blockData, location.getYaw());

            NMSEntity nmsFallingBlock = PLUGIN.getNMSManager().createEntity(location.getWorld(), EntityType.BLOCK_DISPLAY, tracker);
            Location displayLocation = location.clone().add(vector);
            positionDisplayBeforeBukkitSetup(nmsFallingBlock, displayLocation);

            if (nmsFallingBlock.getBukkitEntity() instanceof BlockDisplay blockDisplay) {
                blockDisplay.setBlock(blockData);
                blockDisplay.setTeleportDuration(2);
                blockDisplay.setTransformationMatrix(transformationMatrix);
            }
            placedEntries.add(nmsFallingBlock);
        }
        tracker.startTracking();
        return angle;
    }

    /**
     * Positions virtual display entities before touching their Bukkit wrapper state.
     *
     * <p>Folia assigns entity state to the region containing the entity's current
     * coordinates. Newly-created packet helpers start at the world's default
     * coordinates, so display setters such as {@link BlockDisplay#setBlock(BlockData)}
     * must run after the helper has been moved to the structure block's real region.</p>
     *
     * @param entity the display helper entity being configured
     * @param location the final structure position for the display helper
     */
    private void positionDisplayBeforeBukkitSetup(NMSEntity entity, Location location) {
        entity.setPositionRotation(location);
    }

    @Override
    public void remove() {
        tracker.destroy();
        placedEntries.clear();
    }
}
