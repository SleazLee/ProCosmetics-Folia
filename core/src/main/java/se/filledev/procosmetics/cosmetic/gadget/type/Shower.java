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
package se.filledev.procosmetics.cosmetic.gadget.type;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.util.structure.type.BlockDisplayStructure;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.BlockDisplayStructureImpl;

import java.util.HashSet;
import java.util.Set;

public class Shower implements GadgetBehavior {

    private static final double DRIP_HEIGHT = 3.15d;

    private BlockDisplayStructure structure;
    private Location center;
    private Location shower;
    private Location eyeLocation;
    private final Set<Location> waterDripLocations = new HashSet<>(4);

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new BlockDisplayStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        center = context.getPlayer().getLocation();

        double angle = structure.spawn(center);
        shower = center.clone().add(MathUtil.rotateAroundAxisY(new Vector(0.0d, 0.0d, -0.9d), angle));
        eyeLocation = shower.clone();

        double offset = 0.23d;

        // Calculate drip locations
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < 2; k++) {
                waterDripLocations.add(shower.clone().add(2.0d * i * offset - offset, DRIP_HEIGHT, 2.0d * k * offset - offset));
            }
        }
        Scheduler.runLater(center, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (shower == null) {
            return;
        }
        if (Scheduler.isFolia()) {
            Scheduler.run(shower, this::updateShower);
            return;
        }
        updateShower();
    }

    /**
     * Emits shower particles and finds nearby players from the shower region.
     *
     * <p>The shower is stationary after placement, while the owner can leave its Folia region. The
     * rain sound, drip particles, and nearby-player query are therefore anchored to the shower
     * location instead of the owner update region.</p>
     */
    private void updateShower() {
        if (shower == null) {
            return;
        }
        for (Entity nearbyEntity : shower.getWorld().getNearbyEntities(shower, 0.75d, DRIP_HEIGHT, 0.75d)) {
            if (nearbyEntity instanceof Player onlinePlayer) {
                if (Scheduler.isFolia()) {
                    Scheduler.run(onlinePlayer, () -> splashPlayerIfUnderShower(onlinePlayer));
                } else {
                    splashPlayerIfUnderShower(onlinePlayer);
                }
            }
        }
        shower.getWorld().playSound(shower, Sound.WEATHER_RAIN, 0.05f, 1.8f);

        for (Location dripLocation : waterDripLocations) {
            center.getWorld().spawnParticle(Particle.DRIPPING_WATER, dripLocation, 1);
        }
    }

    /**
     * Checks the player's eye location from the player's owning region.
     *
     * <p>Folia requires player location reads to happen on the player scheduler. When the player is
     * under the shower, the splash particle is then sent back to the shower region.</p>
     *
     * @param onlinePlayer the nearby player being checked
     */
    private void splashPlayerIfUnderShower(Player onlinePlayer) {
        if (shower == null || center == null) {
            return;
        }
        Location playerEyeLocation = onlinePlayer.getLocation().add(0.0d, 1.8d, 0.0d);

        if (Math.abs(playerEyeLocation.getX() - shower.getX()) < 0.4d
                && Math.abs(playerEyeLocation.getZ() - shower.getZ()) < 0.4d
                && Math.abs(playerEyeLocation.getY() - shower.getY()) < DRIP_HEIGHT) {
            Location splashLocation = playerEyeLocation.clone();
            Scheduler.run(shower, () -> center.getWorld().spawnParticle(Particle.SPLASH, splashLocation, 5, 0.0d, 0.3d, 0.1d, 0.3d));
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (Scheduler.isFolia() && center != null) {
            Scheduler.run(center, this::cleanupShower);
            return;
        }
        cleanupShower();
    }

    /**
     * Removes shower displays from the original shower region.
     *
     * <p>Timed cleanup can fire after the owner moves away, so the display tracker is destroyed
     * from the placement region on Folia.</p>
     */
    private void cleanupShower() {
        structure.remove();
        center = null;
        shower = null;
        eyeLocation = null;
        waterDripLocations.clear();
    }

    @Override
    public boolean requiresGroundOnUse() {
        return true;
    }

    @Override
    public boolean isEnoughSpaceToUse(Location location) {
        return structure.isEnoughSpace(location);
    }

    @Override
    public boolean shouldUnequipOnTeleport() {
        return true;
    }
}
