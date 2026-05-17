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

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExplosiveSheep implements GadgetBehavior {

    private final static List<DyeColor> DYE_COLORS = List.of(DyeColor.values());
    private static final int SHEEP_AMOUNT = 10;

    private Sheep sheep;
    private final Set<Sheep> babies = ConcurrentHashMap.newKeySet();
    private Location location;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        if (sheep != null || !babies.isEmpty()) {
            return InteractionResult.fail();
        }
        Player player = context.getPlayer();
        location = player.getLocation();

        sheep = CosmeticEntitySpawner.spawnLiving(location, Sheep.class, entity -> {
            Vector vector = player.getEyeLocation().getDirection();
            vector.setY(vector.getY() + 0.5d);
            entity.setVelocity(vector);
        });

        if (sheep == null) {
            return InteractionResult.fail();
        }

        Scheduler.runLaterOwned(sheep, location, this::explodeSheep, Long.max(0L, context.getType().getDurationTicks() - 80L));

        Scheduler.runLater(location, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (sheep == null) {
            return;
        }
        Scheduler.runOwned(sheep, location, this::updateSheepWarning);
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        despawnSheep();
        removeBabies();
    }

    private void despawnSheep() {
        Sheep sheepToRemove = sheep;
        sheep = null;

        if (sheepToRemove != null) {
            Scheduler.runOwned(sheepToRemove, location, () -> {
                if (sheepToRemove.isValid()) {
                    sheepToRemove.remove();
                }
            });
        }
    }

    private void removeBabies() {
        Set<Sheep> babiesToRemove = new HashSet<>(babies);
        babies.clear();

        for (Sheep baby : babiesToRemove) {
            Scheduler.runOwned(baby, location, () -> removeBabyWithCloud(baby));
        }
    }

    /**
     * Explodes the thrown sheep from the sheep's owning region.
     *
     * <p>The thrown sheep can cross a Folia region boundary before the delayed explosion fires.
     * Running the explosion on the sheep scheduler keeps the location read, removal, and baby
     * spawns on the region that owns the sheep at that moment.</p>
     */
    private void explodeSheep() {
        Sheep explodingSheep = sheep;

        if (explodingSheep == null || !explodingSheep.isValid()) {
            return;
        }
        Location explosionLocation = explodingSheep.getLocation();
        explodingSheep.getWorld().spawnParticle(Particle.EXPLOSION, explosionLocation, 0);
        explodingSheep.getWorld().playSound(explosionLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.0f);
        explodingSheep.remove();
        sheep = null;

        for (int i = 0; i < SHEEP_AMOUNT; i++) {
            explosionLocation.setYaw(explosionLocation.getYaw() + 45.0f);

            Sheep baby = CosmeticEntitySpawner.spawnLiving(explosionLocation, Sheep.class, entity -> {
                entity.setBaby();
                entity.setColor(DYE_COLORS.get(MathUtil.THREAD_LOCAL_RANDOM.nextInt(DYE_COLORS.size())));
                entity.setVelocity(new Vector(
                        MathUtil.randomRange(-0.5d, 0.5d),
                        MathUtil.randomRange(0.8d, 1.5d),
                        MathUtil.randomRange(-0.5d, 0.5d)
                ));
            });

            if (baby != null) {
                babies.add(baby);
            }
        }
    }

    /**
     * Flashes the warning sheep from its owning region.
     *
     * <p>The normal gadget update follows the player, but the sheep is a mobile entity. The
     * color, sound, and smoke effect must therefore run on the sheep scheduler once it leaves
     * the player's Folia region.</p>
     */
    private void updateSheepWarning() {
        Sheep warningSheep = sheep;

        if (warningSheep == null || !warningSheep.isValid()) {
            return;
        }
        warningSheep.setColor(warningSheep.getColor() == DyeColor.WHITE ? DyeColor.RED : DyeColor.WHITE);
        Location sheepLocation = warningSheep.getLocation();
        warningSheep.getWorld().playSound(sheepLocation, Sound.UI_BUTTON_CLICK, 0.5f, 2.0f);
        sheepLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, sheepLocation.add(0.0d, 0.5d, 0.0d), 0);
    }

    /**
     * Removes a baby sheep from its owning region during delayed cleanup.
     *
     * <p>Baby sheep inherit explosion velocity and can drift away from the original gadget
     * location, so cleanup cannot assume the original location still owns their state.</p>
     *
     * @param baby the baby sheep to remove
     */
    private void removeBabyWithCloud(Sheep baby) {
        if (!baby.isValid()) {
            return;
        }
        Location babyLocation = baby.getLocation().add(0.0d, 0.3d, 0.0d);
        babyLocation.getWorld().spawnParticle(Particle.CLOUD, babyLocation, 0);
        baby.remove();
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
