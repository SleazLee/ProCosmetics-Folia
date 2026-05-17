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
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.util.LocationUtil;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;

public class EtherealPearl implements GadgetBehavior, Listener {

    private static final FireworkEffect FIREWORK_EFFECT = FireworkEffect.builder()
            .trail(true)
            .flicker(false)
            .withColor(Color.PURPLE)
            .withFade(Color.AQUA)
            .with(Type.BALL_LARGE)
            .build();

    private Player player;
    private EnderPearl enderPearl;
    private Location location;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        player = context.getPlayer();
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        Player player = context.getPlayer();
        location = player.getEyeLocation();

        enderPearl = location.getWorld().spawn(location, EnderPearl.class, entity -> {
            entity.setVelocity(location.getDirection().multiply(2.0d));
            MetadataUtil.setCustomEntity(entity);
        });
        enderPearl.addPassenger(player);
        context.getUser().setFallDamageProtection(15);

        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (enderPearl != null) {
            EnderPearl pearl = enderPearl;
            Scheduler.runOwned(pearl, location, () -> updatePearlTrail(pearl));
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
        return false;
    }

    @EventHandler
    public void onPearlHit(ProjectileHitEvent event) {
        if (event.getEntity() == enderPearl) {
            enderPearl.getLocation(location);
            lunchFirework(location);

            LocationUtil.center(location);
            location.setY(location.getBlockY() + 1.5d);

            Location playerLocation = player.getLocation();
            location.setPitch(playerLocation.getPitch());
            location.setYaw(playerLocation.getYaw());

            if (Scheduler.isFolia()) {
                player.teleportAsync(location);
            } else {
                player.teleport(location);
            }

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() == enderPearl) {
            lunchFirework(event.getEntity().getLocation(location));
            despawn();
        }
    }

    /**
     * Removes the active pearl from the projectile's owning region.
     *
     * <p>Unequip and dismount events are not guaranteed to run on the same Folia region as the
     * moving pearl, so removal is dispatched through the pearl scheduler when one exists.</p>
     */
    private void despawn() {
        if (enderPearl != null) {
            EnderPearl pearl = enderPearl;
            enderPearl = null;
            Scheduler.runOwned(pearl, location, () -> {
                if (pearl.isValid()) {
                    pearl.remove();
                }
            });
        }
    }

    /**
     * Spawns pearl trail particles from the pearl entity's owning region.
     *
     * <p>The owner can be carried across Folia regions by the pearl. Reading the projectile
     * location from the normal player-following gadget update can become invalid once that happens,
     * so the trail is emitted by the projectile scheduler itself.</p>
     *
     * @param pearl the currently active pearl captured before the scheduler hop
     */
    private void updatePearlTrail(EnderPearl pearl) {
        if (enderPearl != pearl || !pearl.isValid()) {
            return;
        }
        Location particleLocation = pearl.getLocation();
        particleLocation.getWorld().spawnParticle(Particle.CLOUD, particleLocation, 1, 0, 0, 0, 0.0d);
    }

    private void lunchFirework(Location location) {
        location.getWorld().spawn(location, Firework.class, entity -> {
            FireworkMeta meta = entity.getFireworkMeta();
            meta.addEffect(FIREWORK_EFFECT);
            entity.setFireworkMeta(meta);

            MetadataUtil.setCustomEntity(entity);
        }).detonate();
    }
}
