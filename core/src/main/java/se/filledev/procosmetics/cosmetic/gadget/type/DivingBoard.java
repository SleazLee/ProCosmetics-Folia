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
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.BlockStructureImpl;

public class DivingBoard implements GadgetBehavior {

    private BlockStructure structure;
    private Location center;
    private Location location;
    private Location jump;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new BlockStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        center = context.getPlayer().getLocation();
        location = center.clone();

        double angle = structure.spawn(center);
        jump = center.clone().add(MathUtil.rotateAroundAxisY(new Vector(0.0d, 3.0d, 3.0d), angle));

        Scheduler.runLater(center, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (jump != null) {
            if (Scheduler.isFolia()) {
                Scheduler.run(jump, () -> updateDivingBoard(context));
                return;
            }
            updateDivingBoard(context);
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (Scheduler.isFolia() && center != null) {
            Scheduler.run(center, this::cleanupDivingBoard);
            return;
        }
        cleanupDivingBoard();
    }

    /**
     * Finds players near the diving-board jump point from the board region.
     *
     * <p>Folia cannot safely scan every world player from the owner's current region. The board
     * only needs nearby candidates, and each candidate's exact block/velocity check is then run on
     * that player's scheduler.</p>
     *
     * @param context the active gadget context
     */
    private void updateDivingBoard(CosmeticContext<GadgetType> context) {
        if (jump == null) {
            return;
        }
        for (Entity nearbyEntity : jump.getWorld().getNearbyEntities(jump, 1.5d, 2.0d, 1.5d)) {
            if (nearbyEntity instanceof Player worldPlayer) {
                if (Scheduler.isFolia()) {
                    Scheduler.run(worldPlayer, () -> launchPlayerIfOnBoard(context, worldPlayer));
                } else {
                    launchPlayerIfOnBoard(context, worldPlayer);
                }
            }
        }
    }

    /**
     * Applies diving-board velocity from the target player's owning region.
     *
     * <p>The player's current block and velocity are player-region state. The sound is routed back
     * to the board jump location after the launch decision is made.</p>
     *
     * @param context the active gadget context
     * @param worldPlayer the player being checked for launch
     */
    private void launchPlayerIfOnBoard(CosmeticContext<GadgetType> context, Player worldPlayer) {
        if (jump == null) {
            return;
        }
        Block block = worldPlayer.getLocation().getBlock();

        if (block.equals(jump.getBlock())) {
            worldPlayer.setVelocity(worldPlayer.getVelocity().add(new Vector(
                    0.0d,
                    Math.random() * 2.0d,
                    0.0d
            )));
            Location soundLocation = jump.clone();
            Scheduler.run(soundLocation, () -> soundLocation.getWorld().playSound(soundLocation, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.4f));

            User otherUser = context.getPlugin().getUserManager().getConnected(worldPlayer);

            if (otherUser != null) {
                otherUser.setFallDamageProtection(10);
            }
        }
    }

    /**
     * Removes diving-board blocks from the original board region.
     *
     * <p>Timed cleanup can run after the owner leaves the board, so Folia cleanup is anchored to the
     * structure center instead of the player's current region.</p>
     */
    private void cleanupDivingBoard() {
        structure.remove();
        jump = null;
        center = null;
        location = null;
    }

    @Override
    public boolean requiresGroundOnUse() {
        return false;
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
