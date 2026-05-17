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
package se.filledev.procosmetics.cosmetic.gadget.type;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.util.structure.type.ParentBlockDisplayStructure;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.ParentBlockDisplayStructureImpl;

public class Rocket implements GadgetBehavior {

    private static final double Y_COLLISION_CHECK = 10.0d;

    private static final double MAX_SPEED = 0.5d;
    private static final double ACCELERATION = 0.01d;
    private static final float HEIGHT_OFFSET = 3.5f;

    private ParentBlockDisplayStructure structure;
    private NMSEntity seat;
    private boolean launching;
    private int tick;
    private double speed;
    private Location seatLocation;
    private Display seatEntity;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new ParentBlockDisplayStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        Player player = context.getPlayer();
        Location center = player.getLocation();

        seatLocation = center.clone().add(0.0d, HEIGHT_OFFSET, 0.0d);

        seatEntity = seatLocation.getWorld().spawn(seatLocation, BlockDisplay.class, entity -> {
            entity.setTeleportDuration(2);
            MetadataUtil.setCustomEntity(entity);
        });
        seatEntity.addPassenger(player);
        structure.spawn(center, seatEntity, HEIGHT_OFFSET);
        seat = context.getPlugin().getNMSManager().entityToNMSEntity(seatEntity);

        context.getUser().setFallDamageProtection((int) (context.getType().getDuration() + 6));

        speed = 0.0d;
        tick = 0;
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (seat == null) {
            return;
        }
        NMSEntity currentSeat = seat;
        Display currentSeatEntity = seatEntity;
        Scheduler.runOwned(currentSeatEntity, seatLocation, () -> updateRocket(context, currentSeat));
    }

    /**
     * Updates launch movement from the rocket seat's owning region.
     *
     * <p>The player can move with the rocket into another Folia region while the base gadget tick
     * still originates from the player scheduler. Seat movement, structure tracker positions, block
     * collision checks, and launch particles are all tied to the moving rocket seat.</p>
     *
     * @param context the active gadget context
     * @param currentSeat the active rocket seat captured before the scheduler hop
     */
    private void updateRocket(CosmeticContext<GadgetType> context, NMSEntity currentSeat) {
        if (seat != currentSeat || seatLocation == null) {
            return;
        }
        World world = seatLocation.getWorld();

        if (launching) {
            if (speed < MAX_SPEED) {
                speed += ACCELERATION;
            }
            seatLocation.add(0.0d, speed, 0.0d);
            currentSeat.setPositionRotation(seatLocation);

            // Set this to make sure the tracker updates its location and also prevent a spawn-in flicker
            for (NMSEntity entity : structure.getPlacedEntries()) {
                entity.setPreviousLocation(entity.getPreviousLocation().add(0.0d, speed, 0.0d));
            }
            Location flameLocation = seatLocation.clone().subtract(0.0d, HEIGHT_OFFSET, 0.0d);
            world.spawnParticle(Particle.FLAME, flameLocation, 10, 0.2f, 0.2f, 0.2f, 0.0d);
            world.spawnParticle(Particle.CLOUD, flameLocation, 10, 0.2f, 0.2f, 0.2f, 0.0d);
            world.spawnParticle(Particle.EXPLOSION, flameLocation, 1, 0.2f, 0.2f, 0.2f, 0.0d);
            world.playSound(seatLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.0f);

            if (tick > 220 && tick < 240) {
                world.playSound(seatLocation, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 0.0f);
                world.playSound(seatLocation, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.0f);
            } else if (tick == 240) {
                explode(context, seatLocation.clone());
            }
            Location collisionLocation = seatLocation.clone().add(0.0d, Y_COLLISION_CHECK, 0.0d);
            Material topMaterial = collisionLocation.getBlock().getType();

            if (!topMaterial.isAir()) {
                explode(context, collisionLocation);
                return;
            }
        } else {
            world.playSound(seatLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.1f, 0.0f);

            if (tick % 20 == 0) {
                Location smokeLocation = seatLocation.clone().subtract(0.0d, HEIGHT_OFFSET, 0.0d);
                world.spawnParticle(Particle.CLOUD, smokeLocation, 10, 0.2f, 0.2f, 0.2f, 0.0d);

                if (tick == 100) {
                    launching = true;
                    world.playSound(seatLocation, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.0f);
                    world.playSound(seatLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.0f);
                }
            }
        }
        tick++;
    }

    private void explode(CosmeticContext<GadgetType> context, Location location) {
        onUnequip(context);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.0f);
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 0);
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        NMSEntity currentSeat = seat;
        Display currentSeatEntity = seatEntity;
        Location cleanupLocation = seatLocation != null ? seatLocation.clone() : null;

        seat = null;
        seatEntity = null;
        seatLocation = null;
        launching = false;
        speed = 0.0d;
        tick = 0;

        if (Scheduler.isFolia() && (currentSeatEntity != null || cleanupLocation != null)) {
            Scheduler.runOwned(currentSeatEntity, cleanupLocation, () -> cleanupRocket(currentSeat, currentSeatEntity));
            return;
        }
        cleanupRocket(currentSeat, currentSeatEntity);
    }

    /**
     * Removes rocket structure and seat from the seat's final region.
     *
     * <p>Rocket cleanup can be caused by a timer, collision, or player unequip after the owner has
     * crossed Folia regions. Keeping structure tracker teardown and display removal beside the seat
     * prevents stale rocket entities from being removed from the wrong region.</p>
     *
     * @param currentSeat the rocket seat wrapper active when cleanup was requested
     * @param currentSeatEntity the Bukkit display seat to remove
     */
    private void cleanupRocket(NMSEntity currentSeat, Display currentSeatEntity) {
        structure.remove();

        if (currentSeat != null && currentSeatEntity != null) {
            currentSeatEntity.remove();
        }
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
