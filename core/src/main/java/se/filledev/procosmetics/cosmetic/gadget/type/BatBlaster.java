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
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BatBlaster implements GadgetBehavior {

    private static final int BAT_AMOUNT = 12;
    private static final double VERTICAL_SPREAD = 0.1d;
    private static final double HORIZONTAL_SPREAD = 0.3d;

    private final List<Bat> bats = new CopyOnWriteArrayList<>();
    private Vector direction;
    private Location location;
    private final Vector velocity = new Vector();

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        location = context.getPlayer().getEyeLocation();
        direction = location.getDirection().multiply(0.5d);

        for (int i = 0; i < BAT_AMOUNT; i++) {
            Bat bat = CosmeticEntitySpawner.spawnLiving(location, Bat.class, null);

            if (bat != null) {
                bats.add(bat);
            }
        }

        if (bats.isEmpty()) {
            return InteractionResult.fail();
        }
        Scheduler.runLater(location, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (bats.isEmpty()) {
            return;
        }
        for (Bat bat : bats) {
            Scheduler.runOwned(bat, location, () -> updateBat(context, bat));
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        List<Bat> batsToRemove = new ArrayList<>(bats);
        bats.clear();

        for (Bat bat : batsToRemove) {
            Scheduler.runOwned(bat, location, () -> removeBatWithSmoke(bat));
        }
    }

    /**
     * Updates a moving bat from the bat's owning region.
     *
     * <p>The cosmetic timer follows the player on Folia, but bats can fly into a neighboring
     * region before the next tick. Running velocity, nearby-entity checks, and removal from
     * the bat scheduler prevents those moving entities from being touched from the player's
     * new region.</p>
     *
     * @param context the active gadget context
     * @param bat the bat to update
     */
    private void updateBat(CosmeticContext<GadgetType> context, Bat bat) {
        if (!bats.contains(bat) || !bat.isValid()) {
            return;
        }
        Vector batVelocity = velocity.clone();
        batVelocity.setX(direction.getX() + MathUtil.randomRange(-HORIZONTAL_SPREAD, HORIZONTAL_SPREAD));
        batVelocity.setY(direction.getY() + MathUtil.randomRange(-VERTICAL_SPREAD, VERTICAL_SPREAD));
        batVelocity.setZ(direction.getZ() + MathUtil.randomRange(-HORIZONTAL_SPREAD, HORIZONTAL_SPREAD));
        bat.setVelocity(batVelocity);

        Location batLocation = bat.getLocation();

        for (Entity nearby : bat.getNearbyEntities(1.5d, 1.5d, 1.5d)) {
            if (nearby instanceof Player hitPlayer && hitPlayer != context.getPlayer()) {
                Location pushSource = batLocation.clone();
                Scheduler.run(hitPlayer, () -> MathUtil.pushEntity(hitPlayer, pushSource, 0.3d, 0.2d));
                batLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, batLocation, 0);
                bat.getWorld().playSound(batLocation, Sound.ENTITY_BAT_HURT, 0.3f, 1.0f);
                bats.remove(bat);
                bat.remove();
                return;
            }
        }
    }

    /**
     * Removes a bat from its owning region while playing the despawn effect.
     *
     * <p>Cleanup can run after a bat has crossed a Folia region boundary. Reading its
     * location and removing it from the entity scheduler keeps delayed cleanup safe.</p>
     *
     * @param bat the bat to remove
     */
    private void removeBatWithSmoke(Bat bat) {
        if (!bat.isValid()) {
            return;
        }
        Location batLocation = bat.getLocation();
        batLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, batLocation, 0);
        bat.remove();
    }

    @Override
    public boolean requiresGroundOnUse() {
        return false;
    }

    @Override
    public boolean isEnoughSpaceToUse(Location location) {
        return true;
    }

    @Override
    public boolean shouldUnequipOnTeleport() {
        return true;
    }
}
