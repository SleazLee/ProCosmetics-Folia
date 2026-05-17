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

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

public class HotAirBalloon implements GadgetBehavior {

    private static final double MAX_SPEED = 0.075d;
    private static final double ACCELERATION = 0.001d;
    private static final float HEIGHT_OFFSET = 1.3f;

    private ParentBlockDisplayStructure structure;
    private Location seatLocation;
    private NMSEntity seat;
    private Display seatEntity;
    private double speed;

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

        seatEntity.getWorld().playSound(seatLocation, Sound.ENTITY_WITHER_SHOOT, 0.3f, 0.5f);

        context.getUser().setFallDamageProtection((int) context.getType().getDuration());

        Scheduler.runLater(center, () -> onUnequip(context), context.getType().getDurationTicks());
        context.getUser().setFallDamageProtection((int) (context.getType().getDuration() + 6));
        speed = 0.0d;
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (seat == null) {
            return;
        }
        NMSEntity currentSeat = seat;
        Display currentSeatEntity = seatEntity;
        Scheduler.runOwned(currentSeatEntity, seatLocation, () -> updateBalloon(currentSeat));
    }

    /**
     * Moves the balloon seat and virtual structure from the seat's owning region.
     *
     * <p>The balloon can keep rising after the owner crosses into another Folia region. Seat
     * movement, structure tracker positions, and smoke particles are tied to the moving seat, so the
     * update is routed through the seat entity instead of the player update region.</p>
     *
     * @param currentSeat the active seat captured before the scheduler hop
     */
    private void updateBalloon(NMSEntity currentSeat) {
        if (seat != currentSeat || seatLocation == null) {
            return;
        }
        if (speed < MAX_SPEED) {
            speed += ACCELERATION;
        }
        seatLocation.add(0.0d, speed, 0.0d);
        currentSeat.setPositionRotation(seatLocation);

        // Set this to make sure the tracker updates its location and also prevent a spawn-in flicker
        for (NMSEntity entity : structure.getPlacedEntries()) {
            entity.setPreviousLocation(entity.getPreviousLocation().add(0.0d, speed, 0.0d));
        }
        Location smokeLocation = seatLocation.clone().add(0.0d, 3.0d, 0.0d);
        smokeLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                smokeLocation,
                0,
                0.0d,
                speed,
                0.0d,
                2.0d
        );
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        NMSEntity currentSeat = seat;
        Display currentSeatEntity = seatEntity;
        Location cleanupLocation = seatLocation != null ? seatLocation.clone() : null;

        seat = null;
        seatEntity = null;
        seatLocation = null;
        speed = 0.0d;

        if (Scheduler.isFolia() && currentSeatEntity != null) {
            Scheduler.runOwned(currentSeatEntity, cleanupLocation, () -> cleanupBalloon(currentSeat, currentSeatEntity, cleanupLocation));
            return;
        }
        cleanupBalloon(currentSeat, currentSeatEntity, cleanupLocation);
    }

    /**
     * Removes the balloon structure and seat from the seat's final region.
     *
     * <p>Timed unequip can fire after the owner has left the balloon region. Running cleanup beside
     * the seat keeps the explosion effect, virtual structure tracker, and Bukkit display removal
     * region-correct on Folia.</p>
     *
     * @param currentSeat the seat wrapper active when cleanup was requested
     * @param currentSeatEntity the Bukkit display seat to remove
     * @param cleanupLocation the last known seat location for effects
     */
    private void cleanupBalloon(NMSEntity currentSeat, Display currentSeatEntity, Location cleanupLocation) {
        structure.remove();

        if (cleanupLocation != null) {
            cleanupLocation.getWorld().playSound(cleanupLocation, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.3f, 1.0f);
            cleanupLocation.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, cleanupLocation.add(0.0d, 5.0d, 0.0d), 1);
        }

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
