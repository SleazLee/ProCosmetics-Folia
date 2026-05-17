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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.nms.EntityTracker;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.util.structure.StructureData;
import se.filledev.procosmetics.api.util.structure.type.ParentBlockDisplayStructure;
import se.filledev.procosmetics.nms.EntityTrackerImpl;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.StructureImpl;

import java.util.Map;

public class ParentBlockDisplayStructureImpl extends StructureImpl<NMSEntity> implements ParentBlockDisplayStructure {

    private static final ProCosmeticsPlugin PLUGIN = ProCosmeticsPlugin.getPlugin();
    private final EntityTracker tracker = new EntityTrackerImpl();

    public ParentBlockDisplayStructureImpl(StructureData data) {
        super(data, block -> block.isPassable() && !block.isLiquid());
    }

    public double spawn(Location location, Entity parent, float heightOffset) {
        double angle = calculateAngle(location);
        Matrix4f transformationMatrix = new Matrix4f();

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

                transformationMatrix.identity()
                        //.scale(scale)
                        //.rotateY(rotation)
                        .translate((float) vector.getX() - 0.5f, (float) vector.getY() - heightOffset, (float) vector.getZ() - 0.5f);

                blockDisplay.setTransformationMatrix(transformationMatrix);
                addDisplayPassengerOnParentRegion(parent, nmsFallingBlock);
            }
            placedEntries.add(nmsFallingBlock);
        }
        tracker.startTracking();
        return angle;
    }

    /**
     * Positions child displays before their Bukkit wrapper state is mutated.
     *
     * <p>Folia validates display setters against the region owning the display's
     * current coordinates. These packet helpers are born at the world's default
     * coordinates, so they must be moved into the structure's region before
     * {@link BlockDisplay} state is configured or parented.</p>
     *
     * @param entity the display helper entity being configured
     * @param location the final structure position for the display helper
     */
    private void positionDisplayBeforeBukkitSetup(NMSEntity entity, Location location) {
        entity.setPositionRotation(location);
    }

    /**
     * Adds a display passenger from the parent entity's owning region on Folia.
     *
     * <p>The parent seat can move between regions for launch-style gadgets. Passenger
     * mutations are entity state, so anchoring the call to the parent keeps Folia from
     * rejecting the relationship when the setup happens near a region boundary.</p>
     *
     * @param parent the parent entity that should carry the display helper
     * @param child the child display helper to attach
     */
    private void addDisplayPassengerOnParentRegion(Entity parent, NMSEntity child) {
        if (!Scheduler.isFolia()) {
            parent.addPassenger(child.getBukkitEntity());
            return;
        }
        Scheduler.runOwned(parent, child.getPreviousLocation(), () -> parent.addPassenger(child.getBukkitEntity()));
    }

    @Override
    public double spawn(Location location) {
        return 0;
    }

    @Override
    public void remove() {
        tracker.destroy();
        placedEntries.clear();
    }
}
