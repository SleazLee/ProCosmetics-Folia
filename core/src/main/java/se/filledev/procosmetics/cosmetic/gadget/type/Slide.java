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
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.ProCosmetics;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.util.structure.type.BlockStructure;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.FastMathUtil;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.BlockStructureImpl;

public class Slide implements GadgetBehavior, Listener {

    private static final ProCosmetics PLUGIN = ProCosmeticsPlugin.getPlugin();

    private BlockStructure structure;
    private Location center;
    private double angle;
    private Location plate;
    private NMSEntity seat;
    private int ticks;
    private final Vector vector = new Vector();

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new BlockStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        center = context.getPlayer().getLocation();

        angle = structure.spawn(center);

        for (Block block : structure.getPlacedEntries()) {
            if (Tag.WOODEN_PRESSURE_PLATES.isTagged(block.getType())) {
                plate = block.getLocation();
                break;
            }
        }
        Scheduler.runLater(center, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (seat != null) {
            NMSEntity currentSeat = seat;
            Scheduler.runOwned(currentSeat, () -> updateSeat(context, currentSeat));
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (Scheduler.isFolia() && center != null) {
            Scheduler.run(center, this::cleanupSlide);
            return;
        }
        cleanupSlide();
    }

    /**
     * Updates the slide seat from the seat entity's owning region.
     *
     * <p>The gadget update follows the owner, but the armor stand seat can carry another player
     * and move through a different Folia region. Passenger checks, seat movement, and removal are
     * therefore dispatched through the seat's backing entity.</p>
     *
     * @param context the active gadget context
     * @param currentSeat the seat being updated on its owning scheduler
     */
    private void updateSeat(CosmeticContext<GadgetType> context, NMSEntity currentSeat) {
        if (seat != currentSeat) {
            return;
        }
        Entity seatEntity = currentSeat.getBukkitEntity();

        if (seatEntity.getPassengers().isEmpty()) {
            seatEntity.remove();
            seat = null;
            return;
        }
        double movementAngle = FastMathUtil.toRadians(ticks) * 4;
        double forward = -Math.abs(Math.sin(movementAngle)) * 4.5d + 6.0;
        double height = -Math.abs(Math.cos(movementAngle)) * 4.2d + 3.0d;

        vector.setX(0.0d).setY(height).setZ(forward);
        currentSeat.setPositionRotation(center.clone().add(MathUtil.rotateAroundAxisY(vector, angle)));

        ticks++;

        if (ticks == 28) {
            Player player = context.getPlayer();
            Scheduler.run(player, () -> player.getWorld().playSound(player, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.5f));
        }

        if (ticks == 44) {
            Entity passenger = seatEntity.getPassenger();
            seatEntity.remove();
            seat = null;

            if (passenger != null) {
                Vector force = center.getDirection().multiply(0.4d);
                force.setY(force.getY() + 0.4d);
                Scheduler.run(passenger, () -> passenger.setVelocity(passenger.getVelocity().add(force)));
            }
        }
    }

    /**
     * Removes the slide structure from its anchored region and the seat from its entity region.
     *
     * <p>A player can unequip or leave while standing in another Folia region. Block cleanup
     * belongs to the original slide location, while the seat belongs to its own entity scheduler.</p>
     */
    private void cleanupSlide() {
        structure.remove();

        if (seat != null) {
            NMSEntity currentSeat = seat;
            Scheduler.runOwned(currentSeat, () -> {
                Entity seatEntity = currentSeat.getBukkitEntity();

                if (seatEntity != null) {
                    seatEntity.remove();
                }
            });
            seat = null;
        }
        plate = null;
        center = null;
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

    @EventHandler
    public void onPlayerPlateStep(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();

            if (block != null && block.getLocation().equals(plate)) {
                Player player = event.getPlayer();

                if (seat == null && !player.isSneaking()) {
                    ArmorStand seatEntity = CosmeticEntitySpawner.spawnLiving(
                            center.clone().add(0.0d, 4.0d, 0.0d), ArmorStand.class, entity -> {
                                entity.setBasePlate(false);
                                entity.setVisible(false);
                                entity.setGravity(false);

                                seat = PLUGIN.getNMSManager().entityToNMSEntity(entity);
                                seat.setNoClip(true);
                            });
                    if (seatEntity == null) {
                        event.setCancelled(true);
                        return;
                    }
                    seatEntity.addPassenger(player);
                    ticks = 22;
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPlateStep(EntityInteractEvent event) {
        if (event.getBlock().getLocation().equals(plate)) {
            event.setCancelled(true);
        }
    }
}
