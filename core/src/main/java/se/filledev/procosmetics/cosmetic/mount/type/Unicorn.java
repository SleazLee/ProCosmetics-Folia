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

import org.bukkit.Color;
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
import se.filledev.procosmetics.api.cosmetic.mount.MountType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.cosmetic.mount.BlockTrailBehavior;
import se.filledev.procosmetics.util.MathUtil;

import java.util.List;

public class Unicorn extends BlockTrailBehavior {

    private static final ItemStack SADDLE_ITEM = new ItemStack(Material.SADDLE);
    private static final ItemStack HORN_ITEM = new ItemStack(Material.END_ROD);
    private static final List<Color> RAINBOW_COLORS = List.of(
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.LIME,
            Color.GREEN,
            Color.AQUA,
            Color.BLUE,
            Color.PURPLE
    );
    private static final List<ItemStack> TRAIL_BLOCKS = List.of(
            new ItemStack(Material.PINK_CONCRETE),
            new ItemStack(Material.MAGENTA_CONCRETE)
    );

    private static final int TELEPORT_DURATION_TICKS = 1;

    // Horn placement
    private static final Vector3f HORN_TRANSLATION = new Vector3f(0.0f, 0.85f, 1.2f);
    private static final float HORN_PITCH = (float) (Math.PI / 6);
    private static final float HORN_YAW = (float) (-Math.PI / 2);
    private static final float HORN_SCALE = 0.6f;

    // Rainbow trail
    private static final double RAINBOW_BEHIND_OFFSET = -0.7d;
    private static final double RAINBOW_HEIGHT_OFFSET = 1.4d;
    private static final double RAINBOW_COLOR_SPACING = 0.08d;
    private static final int RAINBOW_PARTICLE_COUNT = 2;
    private static final float RAINBOW_PARTICLE_SIZE = 1.0f;

    // Head-tracking & effects
    private static final float PITCH_FOLLOW_FACTOR = 0.5f;
    private static final int FIREWORK_INTERVAL_TICKS = 10;
    private static final int FIREWORK_PARTICLE_COUNT = 5;
    private static final double FIREWORK_HEIGHT_OFFSET = 1.0d;
    private static final double FIREWORK_SPREAD_HORIZONTAL = 1.0d;
    private static final double FIREWORK_SPREAD_VERTICAL = 1.5d;

    private int ticks;
    private NMSEntity horn;

    @Override
    public void setupEntity(CosmeticContext<MountType> context, Entity entity, NMSEntity nmsEntity) {
        super.setupEntity(context, entity, nmsEntity);

        if (entity instanceof Horse horse) {
            horse.setColor(Horse.Color.WHITE);
            horse.setStyle(Horse.Style.WHITE);
            horse.setJumpStrength(1.0d);
            horse.setAdult();
            horse.setTamed(true);
            horse.getInventory().setSaddle(SADDLE_ITEM);
        }
        horn = context.getPlugin().getNMSManager().createEntity(entity.getWorld(), EntityType.ITEM_DISPLAY);

        if (horn == null) {
            return;
        }
        horn.setPositionRotation(entity.getLocation());

        if (horn.getBukkitEntity() instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(HORN_ITEM);
            itemDisplay.setTeleportDuration(TELEPORT_DURATION_TICKS);
            applyHornTransformation(itemDisplay);
            entity.addPassenger(itemDisplay);
        }
        horn.getTracker().startTracking();
    }

    @Override
    public void onUpdate(CosmeticContext<MountType> context, Entity entity, NMSEntity nmsEntity) {
        super.onUpdate(context, entity, nmsEntity);

        entity.getLocation(location);

        // Scale the pitch to better align the horns with the horse's head movement.
        // Ideally, this should be handled by updating the transforms directly.
        location.setPitch(location.getPitch() * PITCH_FOLLOW_FACTOR);

        if (horn != null) {
            horn.sendPositionRotationPacket(location);
        }

        if (context.getUser().isMoving() && context.getPlayer().getVehicle() == entity) {
            Location rainbowLocation = MathUtil.getDirectionalLocation(location, 0.0d, RAINBOW_BEHIND_OFFSET);
            rainbowLocation.add(0.0d, RAINBOW_HEIGHT_OFFSET, 0.0d);

            for (Color color : RAINBOW_COLORS) {
                rainbowLocation.getWorld().spawnParticle(Particle.DUST,
                        rainbowLocation,
                        RAINBOW_PARTICLE_COUNT,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.0d,
                        new Particle.DustOptions(color, RAINBOW_PARTICLE_SIZE)
                );
                rainbowLocation.setY(rainbowLocation.getY() - RAINBOW_COLOR_SPACING);
            }
        }

        if (ticks % FIREWORK_INTERVAL_TICKS == 0) {
            Location fireworkLocation = entity.getLocation().add(0.0d, FIREWORK_HEIGHT_OFFSET, 0.0d);
            fireworkLocation.getWorld().spawnParticle(Particle.FIREWORK,
                    fireworkLocation,
                    FIREWORK_PARTICLE_COUNT,
                    FIREWORK_SPREAD_HORIZONTAL,
                    FIREWORK_SPREAD_VERTICAL,
                    FIREWORK_SPREAD_HORIZONTAL,
                    0.0d
            );
        }

        if (++ticks > 360) {
            ticks = 0;
        }
    }

    private void applyHornTransformation(ItemDisplay itemDisplay) {
        Transformation transformation = new Transformation(
                HORN_TRANSLATION,
                new Quaternionf().rotateX(HORN_PITCH).rotateY(HORN_YAW),
                new Vector3f(HORN_SCALE, HORN_SCALE, HORN_SCALE),
                new Quaternionf()
        );
        itemDisplay.setTransformation(transformation);
    }

    @Override
    public void onUnequip(CosmeticContext<MountType> context) {
        super.onUnequip(context);

        if (horn != null) {
            horn.getTracker().destroy();
            horn = null;
        }
    }

    @Override
    public int getBlockTrailRadius() {
        return 1;
    }

    @Override
    public List<ItemStack> getTrailBlocks() {
        return TRAIL_BLOCKS;
    }
}
