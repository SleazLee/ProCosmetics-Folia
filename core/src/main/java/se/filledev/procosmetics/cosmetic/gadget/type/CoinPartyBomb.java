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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import se.filledev.procosmetics.api.ProCosmetics;
import se.filledev.procosmetics.api.cosmetic.CosmeticContext;
import se.filledev.procosmetics.api.cosmetic.CosmeticType;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetBehavior;
import se.filledev.procosmetics.api.cosmetic.gadget.GadgetType;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.util.FastMathUtil;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.MetadataUtil;
import se.filledev.procosmetics.util.Scheduler;
import se.filledev.procosmetics.util.item.ItemBuilderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CoinPartyBomb implements GadgetBehavior, Listener {

    private static final FireworkEffect FIREWORK_EFFECT = FireworkEffect.builder()
            .flicker(false)
            .withColor(Color.YELLOW)
            .with(Type.BURST).
            trail(false).build();

    private static final float Y_MOVEMENT_PER_TICK = FastMathUtil.PI / 90.0f;
    private static final float ROTATION_PER_TICK = FastMathUtil.PI / 45.0f;
    private static final float HEAD_ROTATION_PER_TICK = (float) Math.toDegrees(ROTATION_PER_TICK);
    private static final float Y_OFFSET = 10.0f;

    private static final ItemStack GOLD_ITEM = new ItemStack(Material.GOLD_BLOCK);
    private static final long EXTRA_TIME = 400L;
    private static final int DROP_INTERVAL = 4;

    private final List<Item> items = new CopyOnWriteArrayList<>();
    private NMSEntity nmsEntity;
    private final Location location = new Location(null, 0.0d, 0.0d, 0.0d);
    private final Location center = new Location(null, 0.0d, 0.0d, 0.0d);
    private int tick;

    private ProCosmetics plugin;
    private int pickupReward;

    @Override
    public void onEquip(CosmeticContext<GadgetType> context) {
        if (plugin == null) {
            plugin = context.getPlugin();
        }
        CosmeticType<?, ?> type = context.getType();
        pickupReward = type.getCategory().getConfig().getInt("cosmetics." + type.getKey() + ".pickup_reward");
    }

    @Override
    public InteractionResult onInteract(CosmeticContext<GadgetType> context, Action action, @Nullable Block clickedBlock, @Nullable Vector clickedPosition) {
        Player player = context.getPlayer();

        if (context.getType().hasPurchasableAmmo()) {
            long totalValue = pickupReward * (context.getType().getDurationTicks() / DROP_INTERVAL);

            if (totalValue > context.getType().getAmmoCost()) {
                context.getUser().sendMessage(Component.text("This gadget is misconfigured and cannot be used. " +
                        "The coin rewards exceed the ammo cost. Please contact a server administrator.", NamedTextColor.RED));
                return InteractionResult.fail();
            }
        }
        player.getLocation(location);
        player.getLocation(center);
        location.setPitch(45.0f); // tilt slightly down

        nmsEntity = context.getPlugin().getNMSManager().createEntity(player.getWorld(), EntityType.BLOCK_DISPLAY);
        positionBlockBeforeBukkitSetup(nmsEntity, center);

        if (nmsEntity.getBukkitEntity() instanceof BlockDisplay blockDisplay) {
            blockDisplay.setBlock(GOLD_ITEM.getType().createBlockData());
            Matrix4f transformationMatrix = new Matrix4f();
            transformationMatrix.identity()
                    //.scale(scale)
                    //.rotateY(radians)
                    .translate(-0.5f, 0.0f, -0.5f);
            blockDisplay.setTransformationMatrix(transformationMatrix);
            blockDisplay.setTeleportDuration(2);
        }
        nmsEntity.getTracker().startTracking();

        Scheduler.runLater(center, () -> {
            tick = 0;
            despawnBlock();
        }, context.getType().getDurationTicks());

        Scheduler.runLater(center, () -> {
            // Make sure it's not running (the player could have started another one)
            if (tick == 0) {
                onUnequip(context);
            }
        }, context.getType().getDurationTicks() + EXTRA_TIME);
        return InteractionResult.success();
    }

    @Override
    public void onUpdate(CosmeticContext<GadgetType> context) {
        if (nmsEntity == null) {
            return;
        }
        if (Scheduler.isFolia()) {
            Scheduler.run(center, () -> updateBomb(context));
            return;
        }
        updateBomb(context);
    }

    /**
     * Updates the bomb display and coin drops from the bomb's anchored region.
     *
     * <p>The bomb itself is static, while the base gadget tick follows the player. Running the
     * display movement and item spawning at the original center prevents player region changes
     * from touching the bomb's virtual display state.</p>
     *
     * @param context the active gadget context
     */
    private void updateBomb(CosmeticContext<GadgetType> context) {
        if (nmsEntity == null) {
            return;
        }
        float y = Math.abs(FastMathUtil.sin(Y_MOVEMENT_PER_TICK * tick));
        location.setY(center.getY() + y * Y_OFFSET);
        location.setYaw(HEAD_ROTATION_PER_TICK * tick);

        nmsEntity.sendPositionRotationPacket(location);

        if (tick % DROP_INTERVAL == 0) {
            Location location = nmsEntity.getPreviousLocation();

            items.add(location.getWorld().dropItem(location,
                    new ItemBuilderImpl(context.getType().getItemStack().getType()).setMaxSize(1).getItemStack(),
                    entity -> {
                        float angle = ROTATION_PER_TICK * tick;
                        float x = FastMathUtil.cos(angle);
                        float z = FastMathUtil.sin(angle);

                        entity.setVelocity(new Vector(x, MathUtil.randomRange(-3.0d, 0.1d), z));
                        entity.setPickupDelay(20);

                        MetadataUtil.setCustomEntity(entity);
                    }));

            location.getWorld().spawn(location, Firework.class, entity -> {
                FireworkMeta meta = entity.getFireworkMeta();
                meta.addEffect(FIREWORK_EFFECT);
                entity.setFireworkMeta(meta);
                MetadataUtil.setCustomEntity(entity);
            }).detonate();

            location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        }

        if (tick++ > 360) {
            tick = 0;
        }
    }

    @Override
    public void onUnequip(CosmeticContext<GadgetType> context) {
        tick = 0;
        despawnBlock();
        despawnItems();
    }

    public void despawnBlock() {
        if (nmsEntity != null) {
            nmsEntity.getTracker().destroy();
            nmsEntity = null;
        }
    }

    private void despawnItems() {
        List<Item> itemsToRemove = new ArrayList<>(items);
        items.clear();

        for (Item item : itemsToRemove) {
            Scheduler.runOwned(item, center, () -> removeItemWithSmoke(item));
        }
    }

    /**
     * Positions the virtual bomb display before Bukkit display setters run.
     *
     * <p>Fresh NMS display helpers start at the world's default coordinates. Folia validates
     * {@link BlockDisplay#setBlock(org.bukkit.block.data.BlockData)} and transformation setters
     * against the display's current region, so the helper must be moved to the bomb center before
     * the Bukkit wrapper is configured.</p>
     *
     * @param entity the bomb display entity
     * @param location the bomb center location
     */
    private void positionBlockBeforeBukkitSetup(NMSEntity entity, Location location) {
        entity.setPositionRotation(location);
    }

    /**
     * Removes a dropped coin item from the item entity's owning region.
     *
     * <p>Coins can be launched away from the bomb center. Delayed cleanup therefore dispatches
     * each item to its own scheduler before reading its location or removing it.</p>
     *
     * @param item the coin item to remove
     */
    private void removeItemWithSmoke(Item item) {
        if (!item.isValid()) {
            return;
        }
        Location itemLocation = item.getLocation();
        itemLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, itemLocation, 0);
        item.remove();
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

    @EventHandler
    public void onCoinPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player pickupPlayer && items.remove(event.getItem())) {
            event.setCancelled(true);
            event.getItem().remove();

            Location pickupLocation = event.getItem().getLocation().add(0.0d, 0.4d, 0.0d);
            pickupPlayer.getWorld().playSound(pickupLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
            location.getWorld().spawnParticle(Particle.FIREWORK, pickupLocation.add(0.0d, 0.4d, 0.0d), 0);

            User user = plugin.getUserManager().getConnected(pickupPlayer);

            if (user != null) {
                plugin.getEconomyManager().getEconomyProvider().addCoinsAsync(user, pickupReward);
            }
        }
    }
}
