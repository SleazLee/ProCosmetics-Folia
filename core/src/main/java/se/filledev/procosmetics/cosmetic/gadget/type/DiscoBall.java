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

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.RGBFade;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.material.Materials;

public class DiscoBall implements GadgetBehavior {

    private static final double HEIGHT_OFFSET = 4.0d;
    private static final int POINTS = 20;
    private static final double RANGE = 5.0d;
    private static final float ROTATION_PER_TICK = 5.0f;

    private NMSEntity nmsEntity;
    private int tick;
    private Location location;
    private final RGBFade rgbFade = new RGBFade();

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        Player player = context.getPlayer();
        location = player.getLocation().add(0.0d, HEIGHT_OFFSET, 0.0d);
        location.setPitch(0.0f);

        nmsEntity = context.getPlugin().getNMSManager().createEntity(player.getWorld(), EntityType.ITEM_DISPLAY);
        positionBallBeforeBukkitSetup(nmsEntity, location);

        if (nmsEntity.getBukkitEntity() instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(Materials.getRandomStainedGlassItem());
            itemDisplay.setTeleportDuration(1);
            Matrix4f matrix = new Matrix4f();
            matrix.scale(0.5f);
            itemDisplay.setTransformationMatrix(matrix);
        }
        nmsEntity.getTracker().startTracking();

        Scheduler.runLaterOwned(nmsEntity, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (nmsEntity == null) {
            return;
        }
        NMSEntity currentEntity = nmsEntity;
        Scheduler.runOwned(currentEntity, () -> updateDiscoBall(currentEntity));
    }

    /**
     * Updates the virtual disco ball from the ball's anchored region.
     *
     * <p>The owner may leave the placement region while the timed effect is still running. Display
     * state and particles are tied to the virtual item display, so the update is dispatched through
     * the display's owning region instead of following the player.</p>
     *
     * @param currentEntity the disco ball entity captured before the scheduler hop
     */
    private void updateDiscoBall(NMSEntity currentEntity) {
        if (nmsEntity != currentEntity || location == null) {
            return;
        }
        Location effectCenter = currentEntity.getPreviousLocation().clone();
        effectCenter.setYaw(ROTATION_PER_TICK * tick);
        currentEntity.sendPositionRotationPacket(effectCenter);

        if (tick % 4 == 0) {
            if (currentEntity.getBukkitEntity() instanceof ItemDisplay itemDisplay) {
                itemDisplay.setItemStack(Materials.getRandomStainedGlassItem());
            }
            currentEntity.sendEntityMetadataPacket();
        }
        Location randomLocation = effectCenter.clone().add(
                MathUtil.randomRange(-RANGE, RANGE),
                MathUtil.randomRange(-RANGE, RANGE),
                MathUtil.randomRange(-RANGE, RANGE)
        );
        effectCenter.getWorld().spawnParticle(Particle.FIREWORK, randomLocation, 0, 0.0f, 0.0f, 0.0f, 1.0f);

        effectCenter.getWorld().spawnParticle(Particle.NOTE, randomLocation, 0, MathUtil.randomRange(1.0d, 25.0d) / 24.0d, 0.0d, 0.0d, 0.0d);
        Vector vector = randomLocation.subtract(effectCenter).toVector().normalize().multiply(-0.4d);
        Location loc = effectCenter.clone();

        for (int i = 0; i < POINTS; i++) {
            rgbFade.nextRGB();

            loc.add(vector);
            effectCenter.getWorld().spawnParticle(Particle.DUST, loc, 0, rgbFade.getR(), rgbFade.getG(), rgbFade.getB(), 0.0d,
                    new Particle.DustOptions(Color.fromRGB(rgbFade.getR(), rgbFade.getG(), rgbFade.getB()), 1)
            );
        }
        tick++;

        if (tick > 360) {
            tick = 0;
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        NMSEntity currentEntity = nmsEntity;

        if (currentEntity == null) {
            return;
        }
        nmsEntity = null;
        location = null;
        Scheduler.runOwned(currentEntity, () -> currentEntity.getTracker().destroy());
    }

    /**
     * Positions the virtual item display before Bukkit display setters run.
     *
     * <p>Folia validates Bukkit wrapper state against the entity's current region. Moving the
     * virtual helper to the disco ball location first keeps item and transformation setup in the
     * correct region.</p>
     *
     * @param entity the virtual disco ball entity
     * @param location the final disco ball location
     */
    private void positionBallBeforeBukkitSetup(NMSEntity entity, Location location) {
        entity.setPositionRotation(location);
    }

    @Override
    public boolean requiresGroundOnUse() {
        return false;
    }

    @Override
    public boolean isEnoughSpaceToUse(Location location) {
        location.add(0.0d, HEIGHT_OFFSET, 0.0d);
        Material material = location.getBlock().getType();
        location.subtract(0.0d, HEIGHT_OFFSET, 0.0d);

        return material.isAir();
    }

    @Override
    public boolean shouldUnequipOnTeleport() {
        return true;
    }
}
