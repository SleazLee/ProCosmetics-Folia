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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.Scheduler;

public class SoccerBall implements GadgetBehavior {

    private static final double DECREASE_MOVEMENT_MULTIPLIER = 0.8d;
    private static final double COLLISION = 0.3d;

    private Location location;
    private Slime slimeBall;
    private Vector ballVector = new Vector();
    private int kickTicks;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        if (slimeBall != null) {
            removeBallWithEffects(slimeBall);
            slimeBall = null;
        }
        location = context.getPlayer().getLocation();
        location.setPitch(0.0f);
        location.add(location.getDirection().multiply(2.0d));

        Slime ball = CosmeticEntitySpawner.spawnLiving(location, Slime.class, entity -> {
            entity.setRemoveWhenFarAway(false);
            entity.setSize(2);

            context.getPlugin().getNMSManager().entityToNMSEntity(entity).removePathfinder();
        });

        if (ball == null) {
            return InteractionResult.fail();
        }
        ballVector.zero();
        slimeBall = ball;

        Scheduler.runLater(location, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (slimeBall == null) {
            return;
        }
        Slime ball = slimeBall;
        Scheduler.runOwned(ball, location, () -> updateBall(context, ball));
    }

    /**
     * Updates the soccer ball from the slime's owning region.
     *
     * <p>The ball is a mobile entity and can roll into a different Folia region than the player
     * who spawned it. Velocity, collision checks, and sound effects are therefore performed from
     * the slime's scheduler instead of the player-following cosmetic tick.</p>
     *
     * @param context the active gadget context
     * @param ball the slime ball being updated on its owning scheduler
     */
    private void updateBall(CosmeticContext<GadgetType> context, Slime ball) {
        if (slimeBall != ball || !ball.isValid()) {
            return;
        }

        if (kickTicks-- < 0) {
            ball.getLocation(location);
            Player kickPlayer = getNearbyKickPlayer(context.getPlayer(), ball);

            if (kickPlayer != null) {
                location.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.5f);
                location.getWorld().spawnParticle(Particle.ITEM_SLIME, location, 10, 0.5f, 0.5f, 0.5f, 0.0f);

                location = kickPlayer.getLocation(location);
                location.setPitch(0.0f);
                Vector vector = location.getDirection().multiply(1.5d);
                vector.setY(0.5d);

                ballVector = vector;
                ball.setVelocity(vector);
                kickTicks = 8;
                return;
            }
        }
        Vector newVelocity = ball.getVelocity();
        boolean collide = false;

        if (newVelocity.getX() == 0.0d) {
            newVelocity.setX(-ballVector.getX() * DECREASE_MOVEMENT_MULTIPLIER);

            if (Math.abs(ballVector.getX()) > COLLISION) {
                collide = true;
            }
        } else if (Math.abs(ballVector.getX() - newVelocity.getX()) < 0.1d) {
            newVelocity.setX(ballVector.getX() * 0.98d);
        }

        if (newVelocity.getZ() == 0.0d) {
            newVelocity.setZ(-ballVector.getZ() * DECREASE_MOVEMENT_MULTIPLIER);
            if (Math.abs(ballVector.getZ()) > COLLISION) {
                collide = true;
            }
        } else if (Math.abs(ballVector.getZ() - newVelocity.getZ()) < 0.1d) {
            newVelocity.setZ(ballVector.getZ() * 0.98d);
        }

        if (newVelocity.getX() != 0.0d && newVelocity.getY() != 0.0d && newVelocity.getZ() != 0.0d) {
            ball.setVelocity(newVelocity);
        }
        ballVector = newVelocity;

        if (collide) {
            ball.getWorld().playSound(ball.getLocation(location), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.5f);
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (slimeBall != null) {
            removeBallWithEffects(slimeBall);
            slimeBall = null;
        }
    }

    /**
     * Finds a nearby player from the ball's owning region.
     *
     * <p>Using the ball's local nearby-entity query avoids scanning every online player and
     * reading locations from regions that do not own those players.</p>
     *
     * @param owner the player who spawned the ball
     * @param ball the slime ball currently being updated
     * @return the first visible nearby player that can kick the ball
     */
    private Player getNearbyKickPlayer(Player owner, Slime ball) {
        for (Entity nearby : ball.getNearbyEntities(1.3d, 1.3d, 1.3d)) {
            if (nearby instanceof Player kickPlayer && owner.canSee(kickPlayer)) {
                return kickPlayer;
            }
        }
        return null;
    }

    /**
     * Removes the soccer ball from the slime's owning region with its despawn effects.
     *
     * <p>Delayed cleanup can run after the ball crosses a Folia boundary, so location reads,
     * particles, sound, and removal are dispatched to the slime scheduler.</p>
     *
     * @param ball the slime ball to remove
     */
    private void removeBallWithEffects(Slime ball) {
        Scheduler.runOwned(ball, location, () -> {
            if (!ball.isValid()) {
                return;
            }
            Location ballLocation = ball.getLocation();
            ballLocation.getWorld().playSound(ballLocation, Sound.ENTITY_CHICKEN_EGG, 0.5f, 0.0f);
            ballLocation.getWorld().spawnParticle(Particle.CLOUD, ballLocation.add(0.0d, 1.0d, 0.0d),
                    10, 0.15f, 0.15f, 0.15f, 0.05f);
            ball.remove();
        });
    }

    @Override
    public boolean requiresGroundOnUse() {
        return true;
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
