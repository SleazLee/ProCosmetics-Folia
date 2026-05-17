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
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;

public class WitherMissile implements GadgetBehavior, Listener {

    private WitherSkull witherSkull;
    private Location location;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        despawn();

        Player player = context.getPlayer();
        location = player.getEyeLocation();

        witherSkull = location.getWorld().spawn(location, WitherSkull.class, entity -> {
            entity.setCharged(true);
            entity.setIsIncendiary(false);
            entity.setYield(0.0f);
            entity.setShooter(player);
            entity.setVelocity(location.getDirection());
            MetadataUtil.setCustomEntity(entity);
        });
        witherSkull.addPassenger(player);

        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (witherSkull != null) {
            WitherSkull skull = witherSkull;
            Scheduler.runOwned(skull, location, () -> updateSkull(context, skull));
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        despawn();
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

    private void despawn() {
        WitherSkull skull = witherSkull;
        witherSkull = null;

        if (skull != null) {
            Scheduler.runOwned(skull, location, () -> {
                if (skull.isValid()) {
                    skull.remove();
                }
            });
        }
    }

    @EventHandler
    public void onSkullExplode(ExplosionPrimeEvent event) {
        if (event.getEntity() == witherSkull) {
            event.setCancelled(true);
            explode(witherSkull);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSkullHit(ProjectileHitEvent event) {
        if (event.getEntity() == witherSkull) {
            event.setCancelled(true);
        }
    }

    /**
     * Updates the wither missile from the projectile's owning region.
     *
     * <p>The missile carries the player and can quickly enter a different Folia region than the
     * original gadget tick. Location reads, particles, sounds, and lifetime checks stay on the
     * projectile scheduler so the player-following cosmetic tick does not touch projectile state.</p>
     *
     * @param context the active gadget context
     * @param skull the missile projectile being updated
     */
    private void updateSkull(CosmeticContext<GadgetType> context, WitherSkull skull) {
        if (witherSkull != skull || !skull.isValid()) {
            return;
        }
        location = skull.getLocation(location);

        location.getWorld().spawnParticle(Particle.CLOUD, location, 0);
        location.getWorld().playSound(location, Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.2f, 2.0f);

        if (skull.getTicksLived() > context.getType().getDurationTicks()) {
            explode(skull);
        }
    }

    /**
     * Explodes and removes the missile from the projectile's owning region.
     *
     * <p>Explosion events are already fired from the projectile region, and scheduled lifetime
     * expiry is routed there by {@link #updateSkull(CosmeticContext, WitherSkull)}. Keeping the
     * effect and removal together avoids a cross-region read of the projectile location.</p>
     *
     * @param skull the missile projectile to explode
     */
    private void explode(WitherSkull skull) {
        if (skull == null || !skull.isValid()) {
            return;
        }
        Location explosionLocation = skull.getLocation();
        explosionLocation.getWorld().playSound(explosionLocation, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.0f);
        explosionLocation.getWorld().spawnParticle(Particle.EXPLOSION, explosionLocation, 1, 0.1d, 0.1d, 0.1d, 0.1d);
        witherSkull = null;
        skull.remove();
    }
}
