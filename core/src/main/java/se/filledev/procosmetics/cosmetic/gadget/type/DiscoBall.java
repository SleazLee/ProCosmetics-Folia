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

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
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

        nmsEntity = context.getPlugin().getNMSManager().createEntity(player.getWorld(), EntityType.ARMOR_STAND);
        positionBallBeforeBukkitSetup(nmsEntity, location);

        if (nmsEntity.getBukkitEntity() instanceof ArmorStand armorStand) {
            armorStand.setInvisible(true);
            armorStand.setArms(false);
        }
        nmsEntity.getTracker().startTracking();

        Scheduler.runLater(location, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (nmsEntity == null) {
            return;
        }
        NMSEntity currentEntity = nmsEntity;
        Scheduler.run(location, () -> updateDiscoBall(currentEntity));
    }

    /**
     * Updates the virtual disco ball from the ball's anchored region.
     *
     * <p>The owner may leave the placement region while the timed effect is still running. Head
     * pose, equipment, and particles are tied to the virtual armor stand location, so the update is
     * dispatched to that region instead of following the player.</p>
     *
     * @param currentEntity the disco ball entity captured before the scheduler hop
     */
    private void updateDiscoBall(NMSEntity currentEntity) {
        if (nmsEntity != currentEntity || location == null) {
            return;
        }
        currentEntity.setHeadPose(0.0f, ROTATION_PER_TICK * tick, 0.0f);
        currentEntity.sendEntityMetadataPacket();

        if (tick % 4 == 0) {
            currentEntity.setHelmet(Materials.getRandomStainedGlassItem());
            currentEntity.sendEntityEquipmentPacket();
        }
        Location effectCenter = currentEntity.getPreviousLocation().clone().add(0.0d, 1.6d, 0.0d);

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
        if (nmsEntity != null) {
            NMSEntity currentEntity = nmsEntity;
            nmsEntity = null;
            Location cleanupLocation = location;
            location = null;
            Scheduler.run(cleanupLocation, () -> currentEntity.getTracker().destroy());
        }
    }

    /**
     * Positions the virtual armor stand before Bukkit armor-stand setters run.
     *
     * <p>Folia validates Bukkit wrapper state against the entity's current region. Moving the
     * virtual helper to the disco ball location first keeps invisibility and arm-state setup in the
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
