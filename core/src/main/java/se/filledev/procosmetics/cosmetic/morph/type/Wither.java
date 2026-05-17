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
package se.filledev.procosmetics.cosmetic.morph.type;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.morph.MorphType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.cosmetic.morph.FlyableMorph;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;

public class Wither extends FlyableMorph implements Listener {

    private static final ProCosmeticsPlugin PLUGIN = ProCosmeticsPlugin.getPlugin();

    private WitherSkull skull;

    @Override
    public void onEquip(CosmeticContext<MorphType> context) {
        super.onEquip(context);
    }

    @Override
    public void setupEntity(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<MorphType> context, PlayerInteractEvent event, NMSEntity nmsEntity) {
        if (event.getAction() == Action.LEFT_CLICK_AIR && skull == null) {
            Player player = context.getPlayer();

            skull = player.launchProjectile(WitherSkull.class);
            MetadataUtil.setCustomEntity(skull);
            player.getWorld().playSound(player, Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.0f);

            WitherSkull launchedSkull = skull;
            Scheduler.runLaterOwned(launchedSkull, launchedSkull.getLocation(), () -> despawnSkull(launchedSkull), 60L);
            return InteractionResult.success();
        }
        return InteractionResult.noAction();
    }

    @Override
    public void onUpdate(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
        super.onUpdate(context, nmsEntity);
    }

    @Override
    public void onUnequip(CosmeticContext<MorphType> context) {
        super.onUnequip(context);
        despawnSkull();
    }

    @EventHandler
    public void onSkullExplode(EntityExplodeEvent event) {
        if (event.getEntity() == skull) {
            event.setCancelled(true);

            Location location = skull.getLocation();
            location.getWorld().playSound(location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 1.0f);
            location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1, 0.1d, 0.1d, 0.1d, 0.1d);

            for (Player hitPlayer : MathUtil.getClosestPlayersFromLocation(location, 2.5d)) {
                pushPlayerFromOwnRegion(hitPlayer);
            }
            despawnSkull();
        }
    }

    /**
     * Applies Wither morph explosion knockback from the target player's owning region.
     *
     * <p>The skull explosion event belongs to the projectile region, but velocity and fall-protection
     * state belong to each affected player. Dispatching per player keeps the ability safe when the
     * blast overlaps a Folia region boundary.</p>
     *
     * @param hitPlayer the player affected by the skull explosion
     */
    private void pushPlayerFromOwnRegion(Player hitPlayer) {
        Vector velocity = new Vector(MathUtil.randomRange(-0.5d, 0.5d),
                MathUtil.randomRange(0.8d, 1.5d),
                MathUtil.randomRange(-0.5d, 0.5d)
        );

        Scheduler.run(hitPlayer, () -> {
            User otherUser = PLUGIN.getUserManager().getConnected(hitPlayer);

            if (otherUser != null) {
                otherUser.setFallDamageProtection(6);
            }
            hitPlayer.setVelocity(velocity);
        });
    }

    /**
     * Removes the active skull from the skull entity's owning region.
     *
     * <p>Unequip and delayed cleanup can happen after the projectile has moved away from the player
     * who fired it. The public cleanup method captures the skull and lets its entity scheduler own
     * the actual remove call.</p>
     */
    private void despawnSkull() {
        if (skull != null) {
            WitherSkull currentSkull = skull;
            skull = null;
            Scheduler.runOwned(currentSkull, null, () -> {
                if (currentSkull.isValid()) {
                    currentSkull.remove();
                }
            });
        }
    }

    /**
     * Removes the skull when a delayed cleanup task fires on that skull's region.
     *
     * <p>The identity check prevents an old delayed task from removing a newer skull fired after the
     * ability was used again.</p>
     *
     * @param currentSkull the skull captured when the delayed task was scheduled
     */
    private void despawnSkull(WitherSkull currentSkull) {
        if (skull == currentSkull) {
            skull = null;
            if (currentSkull.isValid()) {
                currentSkull.remove();
            }
        }
    }
}
