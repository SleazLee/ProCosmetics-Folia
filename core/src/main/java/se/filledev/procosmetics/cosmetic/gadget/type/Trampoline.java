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

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.api.util.structure.type.BlockStructure;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.BlockStructureImpl;

import java.util.Set;

public class Trampoline implements GadgetBehavior {

    private static final Set<Material> BOUNCE_MATERIALS = Set.of(
            Material.BLACK_WOOL,
            Material.WHITE_WOOL
    );

    private BlockStructure structure;
    private Location location;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new BlockStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        location = context.getPlayer().getLocation();

        structure.spawn(location);

        Location teleport = location.clone().add(0.d, 3.5d, 0.0d);

        for (Entity nearbyEntity : location.getWorld().getNearbyEntities(location, 4.0d, 4.0d, 4.0d)) {
            if (nearbyEntity instanceof Player closePlayer) {
                teleportPlayerFromOwnRegion(closePlayer, teleport.clone());
            }
        }
        Scheduler.runLater(location, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    /**
     * Teleports a nearby player from that player's owning region.
     *
     * <p>The trampoline placement region can find nearby players, but the actual teleport mutates
     * player state. Folia needs that final operation to run on each target player's scheduler.</p>
     *
     * @param player the player being moved onto the trampoline
     * @param teleport the trampoline landing location
     */
    private void teleportPlayerFromOwnRegion(Player player, Location teleport) {
        if (Scheduler.isFolia()) {
            Scheduler.run(player, () -> player.teleportAsync(teleport));
            return;
        }
        player.teleport(teleport);
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (location != null) {
            if (Scheduler.isFolia()) {
                Scheduler.run(location, () -> updateTrampoline(context));
                return;
            }
            updateTrampoline(context);
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (Scheduler.isFolia() && location != null) {
            Scheduler.run(location, this::cleanupTrampoline);
            return;
        }
        cleanupTrampoline();
    }

    /**
     * Finds possible trampoline riders from the trampoline region.
     *
     * <p>Using a nearby-entity query avoids scanning every world player from the wrong Folia
     * region. Each candidate's precise block and velocity update are then handled on that player's
     * scheduler.</p>
     *
     * @param context the active gadget context
     */
    private void updateTrampoline(CosmeticContext<GadgetType> context) {
        if (location == null) {
            return;
        }
        for (Entity nearbyEntity : location.getWorld().getNearbyEntities(location, 5.0d, 5.0d, 5.0d)) {
            if (nearbyEntity instanceof Player worldPlayer) {
                if (Scheduler.isFolia()) {
                    Scheduler.run(worldPlayer, () -> bouncePlayerIfOnTrampoline(context, worldPlayer));
                } else {
                    bouncePlayerIfOnTrampoline(context, worldPlayer);
                }
            }
        }
    }

    /**
     * Applies trampoline bounce from the target player's owning region.
     *
     * <p>The block below the player and the player's velocity both belong to the player's current
     * region. Effects are sent back to the trampoline region after the bounce decision is made.</p>
     *
     * @param context the active gadget context
     * @param worldPlayer the player being checked for a bounce
     */
    private void bouncePlayerIfOnTrampoline(CosmeticContext<GadgetType> context, Player worldPlayer) {
        if (location == null) {
            return;
        }
        Block block = worldPlayer.getLocation().subtract(0.0d, 1.0d, 0.0d).getBlock();

        if (structure.getPlacedEntries().contains(block) && BOUNCE_MATERIALS.contains(block.getType())) {
            worldPlayer.setVelocity(worldPlayer.getVelocity().add(new Vector(0.0d, Math.random() >= 0.5d ? 2.5d : 3.0d, 0.0d)));

            Location effectLocation = location.clone();
            Scheduler.run(effectLocation, () -> {
                effectLocation.getWorld().playEffect(effectLocation, Effect.STEP_SOUND, Material.BLACK_CONCRETE);
                effectLocation.getWorld().playSound(effectLocation, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.4f);
            });

            User otherUser = context.getPlugin().getUserManager().getConnected(worldPlayer);

            if (otherUser != null) {
                otherUser.setFallDamageProtection(9);
            }
        }
    }

    /**
     * Removes trampoline blocks from the original trampoline region.
     *
     * <p>Timed cleanup can fire after the owner has walked away, so structure removal is routed back
     * to the placement location on Folia.</p>
     */
    private void cleanupTrampoline() {
        structure.remove();
        location = null;
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
