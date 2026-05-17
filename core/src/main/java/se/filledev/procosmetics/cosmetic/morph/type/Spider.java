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
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.item.ItemBuilderImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Spider implements MorphBehavior {

    private static final ItemStack ITEMSTACK = new ItemStack(Material.COBWEB);
    private static final Vector UP_FORCE = new Vector(0.0d, 0.35d, 0.0d);
    private static final PotionEffect POTION_EFFECT = new PotionEffect(PotionEffectType.SLOWNESS, 80, 2);

    private final Location location = new Location(null, 0.0d, 0.0d, 0.0d);
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int tick;
    private boolean activated;

    @Override
    public void onEquip(CosmeticContext<MorphType> context) {

    }

    @Override
    public void setupEntity(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<MorphType> context, PlayerInteractEvent event, NMSEntity nmsEntity) {
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            Player player = context.getPlayer();

            Scheduler.runLater(player, this::clearItems, 140L);
            player.playSound(player, Sound.ENTITY_SPIDER_HURT, 1.0f, 1.0f);

            activated = true;
            tick = 0;

            return InteractionResult.success();
        }
        return MorphBehavior.InteractionResult.noAction();
    }


    @Override
    public void onUpdate(CosmeticContext<MorphType> context, NMSEntity nmsEntity) {
        Player player = context.getPlayer();

        if (activated) {
            for (Item item : List.copyOf(items)) {
                Scheduler.runOwned(item, null, () -> updateWebItem(player, item));
            }

            if (tick++ < 50) {
                player.getLocation(location);

                items.add(location.getWorld().dropItem(
                        location.add(0.0d, 0.2d, 0.0d),
                        new ItemBuilderImpl(ITEMSTACK).setMaxSize(1).getItemStack(),
                        item -> {
                            Vector vector = location.getDirection().multiply(1.3d);
                            item.setVelocity(vector.add(UP_FORCE));
                            item.setPickupDelay(Integer.MAX_VALUE);
                            MetadataUtil.setCustomEntity(item);
                        }
                ));
                player.getWorld().playSound(player, Sound.BLOCK_COBWEB_HIT, 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void onUnequip(CosmeticContext<MorphType> context) {
        clearItems();
    }

    /**
     * Updates one thrown web item from that item's owning region.
     *
     * <p>The Spider morph can leave web items behind while the player keeps moving. Folia requires
     * item location, ground checks, and removal to happen on the item scheduler rather than the
     * player's current region.</p>
     *
     * @param owner the morph owner
     * @param item the web item being updated
     */
    private void updateWebItem(Player owner, Item item) {
        if (!items.contains(item) || !item.isValid()) {
            items.remove(item);
            return;
        }
        Location itemLocation = item.getLocation();

        if (item.isOnGround()) {
            itemLocation.getWorld().spawnParticle(Particle.ITEM,
                    itemLocation.clone().add(0.0d, 0.3d, 0.0d),
                    5,
                    0.3d,
                    0.1d,
                    0.3d,
                    0.1d,
                    ITEMSTACK
            );
            removeWebItem(item);
            return;
        }
        Player hitPlayer = MathUtil.getClosestPlayerFromLocation(itemLocation, 1.0d);

        if (hitPlayer != null && hitPlayer != owner) {
            applyWebHitFromPlayerRegion(hitPlayer, itemLocation.clone());
            removeWebItem(item);
        }
    }

    /**
     * Applies the web hit from the target player's owning region.
     *
     * <p>The item detects the hit from its region, but potion effects, knockback, and hit particles
     * target player state. Dispatching the hit keeps the morph ability safe at region boundaries.</p>
     *
     * @param hitPlayer the player hit by the web item
     * @param sourceLocation the item location used for knockback direction
     */
    private void applyWebHitFromPlayerRegion(Player hitPlayer, Location sourceLocation) {
        Scheduler.run(hitPlayer, () -> {
            hitPlayer.addPotionEffect(POTION_EFFECT);
            MathUtil.pushEntity(hitPlayer, sourceLocation, 0.05d, 0.0d);
            Location hitLocation = hitPlayer.getLocation().add(0.0d, 1.2d, 0.0d);
            hitLocation.getWorld().spawnParticle(Particle.ITEM,
                    hitLocation,
                    20,
                    0.3d,
                    0.3d,
                    0.3d,
                    0.1d,
                    ITEMSTACK
            );
        });
    }

    /**
     * Removes a tracked web item from its owning region.
     *
     * <p>Both natural expiry and unequip cleanup use this path so retained item entities are never
     * removed from the owner's current Folia region by accident.</p>
     *
     * @param item the web item to remove
     */
    private void removeWebItem(Item item) {
        items.remove(item);
        if (item.isValid()) {
            item.remove();
        }
    }

    /**
     * Clears all retained web items through their entity schedulers.
     *
     * <p>The delayed clear task is player-owned, but the items may have landed elsewhere. Each item
     * owns its own final removal.</p>
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
