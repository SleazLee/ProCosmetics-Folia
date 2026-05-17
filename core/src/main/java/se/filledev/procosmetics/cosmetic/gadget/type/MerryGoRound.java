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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.nms.EntityTracker;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.util.structure.type.BlockStructure;
import se.filledev.procosmetics.nms.EntityTrackerImpl;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.FastMathUtil;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.RGBFade;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.structure.type.BlockStructureImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MerryGoRound implements GadgetBehavior, Listener {

    private static final ItemStack SADDLE_ITEM = new ItemStack(Material.SADDLE);
    private static final List<Horse.Color> HORSE_COLORS = List.of(Horse.Color.values());
    private static final int HORSES = 7;
    private static final double RANGE = 4.4d;
    private static final double ANGLE_PER_HORSE = 360.0d / HORSES;
    private static final double LEASH_Y_OFFSET = 6.0d;
    private static final float MAX_SPEED = 4.0f;
    private static final float ACCELERATION = 0.02f;

    private BlockStructure structure;
    private float tick;
    private final EntityTracker tracker = new EntityTrackerImpl();
    private final Map<Horse, CoasterHorse> coasterHorses = new LinkedHashMap<>();
    private final RGBFade rgbFade = new RGBFade();
    private Location center;
    private float speed;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (structure == null) {
            structure = new BlockStructureImpl(context.getType().getStructure());
        }
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        Player player = context.getPlayer();
        center = player.getLocation().getBlock().getLocation().add(0.5d, 0.0d, 0.5d);
        World world = player.getWorld();
        speed = 0.0f;

        structure.spawn(center);

        for (int i = 0; i < HORSES; i++) {
            Location location = getHorseLocation(i);
            Location leashLoc = location.clone().add(0.0d, LEASH_Y_OFFSET, 0.0d);
            location.setY(getYOffset(i));

            ItemDisplay itemDisplay = CosmeticEntitySpawner.spawn(location, ItemDisplay.class, entity -> {
                entity.setGravity(false);
                entity.setTeleportDuration(2);
            });

            if (itemDisplay == null) {
                onUnequip(context);
                return InteractionResult.fail();
            }

            int finalI = i;
            Horse horse = CosmeticEntitySpawner.spawnLiving(location, Horse.class, entity -> {
                entity.setColor(HORSE_COLORS.get(finalI % HORSE_COLORS.size()));
                entity.setAI(false);
                entity.setInvulnerable(true);
                entity.getInventory().setSaddle(SADDLE_ITEM);
            });

            if (horse == null) {
                itemDisplay.remove();
                onUnequip(context);
                return InteractionResult.fail();
            }
            horse.addPassenger(itemDisplay);
            NMSEntity nmsEntityHorse = context.getPlugin().getNMSManager().entityToNMSEntity(horse);
            NMSEntity nmsEntityItemDisplay = context.getPlugin().getNMSManager().entityToNMSEntity(itemDisplay);

            NMSEntity nmsEntityLeash = context.getPlugin().getNMSManager().createEntity(world, EntityType.BAT, tracker);
            nmsEntityLeash.setPositionRotation(leashLoc);
            nmsEntityLeash.setLeashHolder(nmsEntityHorse.getBukkitEntity());
            if (nmsEntityLeash.getBukkitEntity() instanceof LivingEntity livingEntity) {
                livingEntity.setInvisible(true);
            }
            CoasterHorse coasterHorse = new CoasterHorse(nmsEntityHorse, nmsEntityItemDisplay, nmsEntityLeash);
            coasterHorses.put(horse, coasterHorse);
        }
        tracker.startTracking();

        moveNearbyPlayersOutOfCarousel(player);
        Scheduler.runLater(center, () -> onUnequip(context), context.getType().getDurationTicks());
        return InteractionResult.success();
    }

    /**
     * Teleports nearby players from their own schedulers before the carousel starts moving.
     *
     * <p>The nearby-entity query is anchored to the carousel region, but each player's teleport is
     * player state. Dispatching the teleport per player prevents Folia errors when nearby players
     * are not in the same region as the player who used the gadget.</p>
     *
     * @param sourcePlayer the player whose facing direction is used for the exit location
     */
    private void moveNearbyPlayersOutOfCarousel(Player sourcePlayer) {
        Location teleport = center.clone().add(4.5d, 1.5d, 0.0d);
        teleport.setDirection(sourcePlayer.getLocation().getDirection());

        for (Entity nearbyEntity : center.getWorld().getNearbyEntities(center, 6.0d, 6.0d, 6.0d)) {
            if (nearbyEntity instanceof Player closePlayer) {
                teleportPlayerFromOwnRegion(closePlayer, teleport.clone());
            }
        }
    }

    /**
     * Applies a carousel safety teleport from the target player's owning region.
     *
     * <p>Folia player teleports should be initiated from the player scheduler; Paper keeps the
     * original synchronous teleport behavior.</p>
     *
     * @param player the nearby player being moved away from the carousel
     * @param teleport the destination just outside the ride
     */
    private void teleportPlayerFromOwnRegion(Player player, Location teleport) {
        if (Scheduler.isFolia()) {
            Scheduler.run(player, () -> player.teleportAsync(teleport));
            return;
        }
        player.teleport(teleport);
    }

    private Location getHorseLocation(float angle) {
        Location location = MathUtil.getLocationAroundCircle(center, RANGE, angle);
        location.setYaw(location.getYaw() - 90.0f);
        return location;
    }

    private double getYOffset(float angle) {
        double y = FastMathUtil.sin(angle * 2.0f) + 2.0d;
        return center.getY() + y;
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (center == null) {
            return;
        }
        if (Scheduler.isFolia()) {
            Scheduler.run(center, this::updateCarousel);
            return;
        }
        updateCarousel();
    }

    /**
     * Updates carousel horses, display seats, leashes, and particles from the carousel's anchored region.
     *
     * <p>The ride remains centered where it was spawned, while the owner can move into another
     * Folia region. Cosmetic movement is therefore updated from the carousel region instead of the
     * player's current region.</p>
     */
    private void updateCarousel() {
        if (center == null) {
            return;
        }

        int i = 0;
        for (Map.Entry<?, CoasterHorse> entry : coasterHorses.entrySet()) {
            CoasterHorse coasterHorse = entry.getValue();
            NMSEntity nmsHorse = coasterHorse.horse();
            NMSEntity nmsLeash = coasterHorse.leash();

            float angle = FastMathUtil.toRadians(ANGLE_PER_HORSE * i + tick);
            Location location = getHorseLocation(angle);

            location.add(0.0d, LEASH_Y_OFFSET, 0.0d);
            nmsLeash.sendPositionRotationPacket(location);
            location.subtract(0.0d, LEASH_Y_OFFSET, 0.0d);

            location.setY(getYOffset(angle));
            nmsHorse.sendPositionRotationPacket(location);

            location.getWorld().spawnParticle(Particle.DUST, location.add(0.0d, 1.0d, 0.0d), 5, 0, 0, 0, 0.0d,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(rgbFade.getR(), rgbFade.getG(), rgbFade.getB()), 1)
            );
            i++;
        }

        if (speed < MAX_SPEED) {
            speed += ACCELERATION;
        }
        rgbFade.nextRGB();

        tick += speed;

        if (tick > 360) {
            tick = 0;
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        if (Scheduler.isFolia() && center != null) {
            Scheduler.run(center, this::cleanupCarousel);
            return;
        }
        cleanupCarousel();
    }

    /**
     * Removes carousel blocks, virtual leashes, and real ride entities from the carousel region.
     */
    private void cleanupCarousel() {
        structure.remove();

        tracker.destroy();

        for (CoasterHorse coasterHorse : coasterHorses.values()) {
            removeEntityOnOwningRegion(coasterHorse.display().getBukkitEntity());
            removeEntityOnOwningRegion(coasterHorse.horse().getBukkitEntity());
        }
        coasterHorses.clear();
        center = null;
        speed = 0.0f;
        tick = 0.0f;
    }

    /**
     * Removes a carousel entity from its owning region.
     *
     * @param entity the real carousel entity to remove
     */
    private void removeEntityOnOwningRegion(Entity entity) {
        Scheduler.runOwned(entity, center, () -> {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        });
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

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Horse horse) {
            CoasterHorse coasterHorse = coasterHorses.get(horse);

            if (coasterHorse != null) {
                Entity display = coasterHorse.display().getBukkitEntity();

                Scheduler.runOwned(display, center, () -> {
                    if (display.getPassengers().isEmpty()) {
                        display.addPassenger(event.getPlayer());
                    }
                });
            }
        }
    }

    public record CoasterHorse(NMSEntity horse, NMSEntity display, NMSEntity leash) {
    }
}
