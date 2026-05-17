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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.morph.MorphBehavior;
import se.filledev.procosmetics.api.cosmetic.morph.MorphType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.item.ItemBuilderImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Squid implements MorphBehavior {

    private final static ItemStack ITEMSTACK = new ItemStack(Material.INK_SAC);
    private static final Vector UP_FORCE = new Vector(0.0d, 0.35d, 0.0d);
    private static final PotionEffect POTION_EFFECT = new PotionEffect(PotionEffectType.BLINDNESS, 60, 0);
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int ticks;
    private boolean shooting;

    @Override
    public void onEquip(CosmeticContext<MorphType> context) {
    }

    @Override
    public void setupEntity(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<MorphType> context, PlayerInteractEvent event, NMSEntity nmsEntity) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            ticks = 0;
            shooting = true;
            Player player = context.getPlayer();
            player.getWorld().playSound(player, Sound.ENTITY_SQUID_SQUIRT, 1.0f, 2.0f);
            Scheduler.runLater(player, this::clearItems, 140L);
            return InteractionResult.success();
        }
        return InteractionResult.noAction();
    }

    private final Location itemLocation = new Location(null, 0.0d, 0.0d, 0.0d);

    @Override
    public void onUpdate(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
        Player player = context.getPlayer();

        for (Item item : List.copyOf(items)) {
            Scheduler.runOwned(item, null, () -> updateInkItem(player, item));
        }

        if (shooting) {
            if (ticks++ > 50) {
                shooting = false;
            }
            player.getLocation(itemLocation);

            items.add(player.getWorld().dropItem(
                    itemLocation.add(0.0d, 0.2d, 0.0d),
                    new ItemBuilderImpl(ITEMSTACK).setMaxSize(1).getItemStack(),
                    item -> {
                        Vector vector = itemLocation.getDirection().multiply(1.3d);
                        item.setVelocity(vector.add(UP_FORCE));
                        item.setPickupDelay(Integer.MAX_VALUE);
                        MetadataUtil.setCustomEntity(item);
                    }
            ));

            if (ticks % 5 == 0) {
                player.getWorld().playSound(player, Sound.BLOCK_WATER_AMBIENT, 1.0f, 2.0f);
            }
            itemLocation.getWorld().spawnParticle(Particle.SPLASH,
                    itemLocation,
                    10,
                    0.3d,
                    0.3d,
                    0.3d,
                    0.1d
            );
        }
    }

    @Override
    public void onUnequip(CosmeticContext<MorphType> context) {
        clearItems();
    }

    /**
     * Updates one thrown ink item from that item's owning region.
     *
     * <p>The Squid morph leaves item projectiles in the world while the owner can keep moving. Folia
     * item ground checks, location reads, particles, and removal are therefore routed through the
     * item scheduler.</p>
     *
     * @param owner the morph owner
     * @param item the ink item being updated
     */
    private void updateInkItem(Player owner, Item item) {
        if (!items.contains(item) || !item.isValid()) {
            items.remove(item);
            return;
        }

        if (item.isOnGround()) {
            removeInkItem(item);
            return;
        }
        Location location = item.getLocation();
        location.getWorld().spawnParticle(Particle.SPLASH, location, 0);

        Player hitPlayer = MathUtil.getClosestPlayerFromLocation(location, 1.0d);

        if (hitPlayer != null && hitPlayer != owner) {
            applyInkHitFromPlayerRegion(hitPlayer, location.clone());
            removeInkItem(item);
        }
    }

    /**
     * Applies the ink hit from the target player's owning region.
     *
     * <p>The item detects a nearby player from its region, but blindness, knockback, and eye
     * particles belong to the target player.</p>
     *
     * @param hitPlayer the player hit by ink
     * @param sourceLocation the item location used for knockback direction
     */
    private void applyInkHitFromPlayerRegion(Player hitPlayer, Location sourceLocation) {
        Scheduler.run(hitPlayer, () -> {
            hitPlayer.addPotionEffect(POTION_EFFECT);
            hitPlayer.getWorld().spawnParticle(Particle.SPLASH,
                    hitPlayer.getEyeLocation(),
                    10,
                    0.3d,
                    0.4d,
                    0.3d,
                    0.1d
            );
            MathUtil.pushEntity(hitPlayer, sourceLocation, 0.05d, 0.0d);
        });
    }

    /**
     * Removes a tracked ink item from its owning region.
     *
     * <p>Item removal is kept on the item scheduler for both hit and ground cleanup.</p>
     *
     * @param item the ink item to remove
     */
    private void removeInkItem(Item item) {
        items.remove(item);
        if (item.isValid()) {
            item.remove();
        }
    }

    /**
     * Clears all retained ink items through their entity schedulers.
     *
     * <p>The delayed clear task is player-owned, but individual ink items may have crossed into
     * another Folia region before cleanup.</p>
     */
    private void clearItems() {
        List<Item> spawnedItems = List.copyOf(items);
        items.clear();

        for (Item item : spawnedItems) {
            Scheduler.runOwned(item, null, () -> {
                if (item.isValid()) {
                    item.remove();
                }
            });
        }
    }
}
