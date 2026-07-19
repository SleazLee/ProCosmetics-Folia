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
package se.filledev.procosmetics.cosmetic.mount.type;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.mount.MountBehavior;
import se.filledev.procosmetics.api.cosmetic.mount.MountType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.nms.EntityTrackerImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Rudolf implements MountBehavior {

    private static final ItemStack SADDLE_ITEM = new ItemStack(Material.SADDLE);
    private static final ItemStack HORN_ITEM = new ItemStack(Material.DEAD_BUSH);
    private static final ItemStack NOSE_ITEM = new ItemStack(Material.RED_CONCRETE);

    private static final int TELEPORT_DURATION_TICKS = 1;

    // Horn placement
    private static final float HORN_WIDTH_OFFSET = 0.3f;
    private static final float HORN_FORWARD_OFFSET = 1.0f;
    private static final float HORN_HEIGHT_OFFSET = 0.9f;
    private static final float HORN_SCALE = 0.7f;
    private static final float HORN_FRONT_TILT = (float) (Math.PI / 6);
    private static final float HORN_ROTATION = (float) (Math.PI / 4);

    // Nose placement
    private static final Vector3f NOSE_TRANSLATION = new Vector3f(0.0f, 0.35f, 1.55f);
    private static final float NOSE_TILT = (float) (Math.PI / 6);
    private static final float NOSE_SCALE = 0.2f;

    // Head-tracking & effects
    private static final float PITCH_FOLLOW_FACTOR = 0.5f;
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    private static final int PARTICLE_COUNT = 6;
    private static final double PARTICLE_SPREAD_HORIZONTAL = 1.0d;
    private static final double PARTICLE_SPREAD_VERTICAL = 1.2d;

    private final Location location = new Location(null, 0.0d, 0.0d, 0.0d);
    private final EntityTrackerImpl tracker = new EntityTrackerImpl();
    private final List<NMSEntity> displayEntities = new CopyOnWriteArrayList<>();
    private int ticks;

    @Override
    public void onEquip(CosmeticContext<MountType> context) {
    }

    @Override
    public void setupEntity(CosmeticContext<MountType> context, Entity entity, NMSEntity nmsEntity) {
        if (!(entity instanceof Horse horse)) {
            return;
        }
        horse.setColor(Horse.Color.BROWN);
        horse.setStyle(Horse.Style.NONE);
        horse.setJumpStrength(1.0d);
        horse.setAdult();
        horse.setTamed(true);
        horse.getInventory().setSaddle(SADDLE_ITEM);

        for (int i = 0; i < 2; i++) {
            int index = i;
            spawnDisplayEntity(context, horse, HORN_ITEM, itemDisplay ->
                    applyHornTransformation(itemDisplay, index));
        }
        spawnDisplayEntity(context, horse, NOSE_ITEM, this::applyNoseTransformation);

        tracker.startTracking();
    }

    @Override
    public void onUpdate(CosmeticContext<MountType> context, Entity entity, NMSEntity nmsEntity) {
        entity.getLocation(location);

        // Scale the pitch to better align the horns with the horse's head movement.
        // Ideally, this should be handled by updating the transforms directly.
        location.setPitch(location.getPitch() * PITCH_FOLLOW_FACTOR);

        for (NMSEntity displayEntity : displayEntities) {
            displayEntity.sendPositionRotationPacket(location);
        }
        if (ticks % PARTICLE_INTERVAL_TICKS == 0) {
            location.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    location,
                    PARTICLE_COUNT,
                    PARTICLE_SPREAD_HORIZONTAL,
                    PARTICLE_SPREAD_VERTICAL,
                    PARTICLE_SPREAD_HORIZONTAL,
                    0.0d
            );
        }

        if (++ticks > 360) {
            ticks = 0;
        }
    }

    @Override
    public void onUnequip(CosmeticContext<MountType> context) {
        tracker.destroy();
        displayEntities.clear();
    }

    private void spawnDisplayEntity(CosmeticContext<MountType> context,
                                    Horse horse,
                                    ItemStack itemStack,
                                    Consumer<ItemDisplay> transformer) {
        NMSEntity displayEntity = context.getPlugin().getNMSManager()
                .createEntity(horse.getWorld(), EntityType.ITEM_DISPLAY, tracker);

        if (displayEntity == null) {
            return;
        }
        displayEntity.setPositionRotation(horse.getLocation());

        if (displayEntity.getBukkitEntity() instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(itemStack);
            itemDisplay.setTeleportDuration(TELEPORT_DURATION_TICKS);
            transformer.accept(itemDisplay);
            horse.addPassenger(itemDisplay);
        }
        displayEntities.add(displayEntity);
    }

    private void applyNoseTransformation(ItemDisplay itemDisplay) {
        Transformation transformation = new Transformation(
                NOSE_TRANSLATION,
                new Quaternionf().rotateX(NOSE_TILT),
                new Vector3f(NOSE_SCALE, NOSE_SCALE, NOSE_SCALE),
                new Quaternionf()
        );
        itemDisplay.setTransformation(transformation);
    }

    private void applyHornTransformation(ItemDisplay itemDisplay, int index) {
        float sideOffset = -HORN_WIDTH_OFFSET + index * 2.0f * HORN_WIDTH_OFFSET;
        float flip = (float) (index * Math.PI);

        Transformation transformation = new Transformation(
                new Vector3f(sideOffset, HORN_HEIGHT_OFFSET, HORN_FORWARD_OFFSET),
                new Quaternionf().rotateXYZ(HORN_FRONT_TILT, flip, HORN_ROTATION),
                new Vector3f(HORN_SCALE, HORN_SCALE, HORN_SCALE),
                new Quaternionf()
        );
        itemDisplay.setTransformation(transformation);
    }
}
