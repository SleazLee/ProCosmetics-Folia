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
package se.filledev.procosmetics.cosmetic.mount;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.config.Config;
import se.filledev.procosmetics.api.cosmetic.mount.Mount;
import se.filledev.procosmetics.api.cosmetic.mount.MountBehavior;
import se.filledev.procosmetics.api.cosmetic.mount.MountType;
import se.filledev.procosmetics.api.event.CosmeticEntitySpawnEvent;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.cosmetic.CosmeticImpl;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.Scheduler;

public class MountImpl extends CosmeticImpl<MountType, MountBehavior> implements Mount {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final boolean rideOnSpawn;
    private final boolean despawnOnDismount;

    protected NMSEntity nmsEntity;
    protected Entity entity;

    public MountImpl(ProCosmeticsPlugin plugin, User user, MountType type, MountBehavior behavior) {
        super(plugin, user, type, behavior);

        Config config = type.getCategory().getConfig();

        this.rideOnSpawn = config.getBoolean("ride_on_spawn");
        this.despawnOnDismount = config.getBoolean("despawn_on_dismount");
    }

    @Override
    protected void onEquip() {
        user.removeCosmetic(plugin.getCategoryRegistries().morphs(), false, true);
        if (!spawnAt(player.getLocation())) {
            abortEquip();
            return;
        }

        if (rideOnSpawn && entity.isValid()) {
            entity.addPassenger(player);
        }
        refreshGrimExemptionIfRiding(entity);
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    protected void onUpdate() {
        Entity currentEntity = entity;
        NMSEntity currentNmsEntity = nmsEntity;

        if (currentNmsEntity == null || currentEntity == null || !currentEntity.isValid()) {
            return;
        }
        boolean shouldFollow = currentEntity instanceof LivingEntity
                && player.getVehicle() != currentEntity
                && user.isMoving();

        if (Scheduler.isFolia()) {
            Scheduler.run(currentEntity, () -> handleEntityThreadUpdate(currentEntity, currentNmsEntity, shouldFollow));
            return;
        }
        handleEntityThreadUpdate(currentEntity, currentNmsEntity, shouldFollow);
    }

    private void handleEntityThreadUpdate(Entity currentEntity, NMSEntity currentNmsEntity, boolean shouldFollow) {
        if (entity != currentEntity || nmsEntity != currentNmsEntity || !currentEntity.isValid()) {
            return;
        }
        refreshGrimExemptionIfRiding(currentEntity);
        behavior.onUpdate(this, currentEntity, currentNmsEntity);
        refreshGrimExemptionIfRiding(currentEntity);

        if (shouldFollow) {
            currentNmsEntity.follow(player);
        }
    }

    @Override
    protected void onUnequip() {
        NMSEntity currentNmsEntity = nmsEntity;
        nmsEntity = null;
        Entity currentEntity = entity;
        entity = null;

        removeMountedEntity(currentEntity, currentNmsEntity);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();

        if (clickedEntity != entity) {
            return;
        }
        Player clicker = event.getPlayer();

        if (clicker.equals(player)) {
            if (!player.isInsideVehicle()) {
                entity.addPassenger(player);
                refreshGrimExemptionIfRiding(entity);
            }
        } else {
            User clickUser = plugin.getUserManager().getConnected(clicker);

            if (clickUser != null) {
                clickUser.sendMessage(clickUser.translate("cosmetic.mounts.equip.not_owner"));
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == DamageCause.FALL && event.getEntity() == player
                && player.getVehicle() == entity) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getDismounted() == entity && despawnOnDismount) {
            user.removeCosmetic(cosmeticType.getCategory(), false, true);
        }
    }

    // For custom movement so the entity does not move around with its AI while riding it
    @EventHandler(ignoreCancelled = true)
    public void onCustomMount(EntityMountEvent event) {
        if (event.getMount() == entity && event.getMount() instanceof LivingEntity livingEntity) {
            livingEntity.setAI(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCustomDismount(EntityDismountEvent event) {
        if (event.getDismounted() == entity && event.getDismounted() instanceof LivingEntity livingEntity) {
            livingEntity.setAI(true);
        }
    }

    @Override
    public void spawn() {
        spawn(player.getLocation());
    }

    @Override
    public void spawn(Location location) {
        if (!spawnAt(location) && isEquipped()) {
            unequip(false, false);
        }
    }

    /**
     * Spawns the mount through the shared cosmetic entity spawn path.
     *
     * <p>Mounts are both visible cosmetics and interactive vehicles. If their
     * entity spawn is cancelled, the equip flow must stop before adding passengers,
     * starting update tasks, sending success messages, or saving the mount as
     * equipped. The shared spawner also supports non-living mounts, such as
     * boats and display entities, while still tagging the entity early enough
     * for protection plugins to identify ProCosmetics-owned spawns.</p>
     *
     * @param location the location where the mount should appear
     * @return {@code true} when the mount entity and NMS wrapper were created successfully
     */
    private boolean spawnAt(Location location) {
        NMSEntity currentNmsEntity = nmsEntity;
        Entity currentEntity = entity;
        nmsEntity = null;
        entity = null;
        removeMountedEntity(currentEntity, currentNmsEntity);

        entity = CosmeticEntitySpawner.spawn(location, cosmeticType.getEntityType(), entity -> {
            entity.setCustomName(SERIALIZER.serialize(user.translate(
                    "cosmetic.mounts.name_tag",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("cosmetic", cosmeticType.getName(user))))
            );

            if (entity instanceof LivingEntity livingEntity) {
                AttributeInstance attribute = livingEntity.getAttribute(Attribute.MAX_HEALTH);

                if (attribute != null) {
                    double health = 2.0d;
                    attribute.setBaseValue(health);
                    livingEntity.setHealth(health);
                }
            }
            nmsEntity = plugin.getNMSManager().entityToNMSEntity(entity);
            behavior.setupEntity(this, entity, nmsEntity);
        });

        // Ensure that the entity has been spawned and was not blocked by other plugins
        if (entity == null || nmsEntity == null || !entity.isValid()) {
            nmsEntity = null;
            entity = null;
            return false;
        }
        nmsEntity.stopNavigation();
        behavior.postSetupEntity(this, entity, nmsEntity);

        CosmeticEntitySpawnEvent event = new CosmeticEntitySpawnEvent(plugin, user, player, entity);
        plugin.getServer().getPluginManager().callEvent(event);
        return true;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public NMSEntity getNMSEntity() {
        return nmsEntity;
    }

    private void removeMountedEntity(Entity mountedEntity, NMSEntity mountedNmsEntity) {
        if (mountedEntity == null) {
            return;
        }
        Scheduler.run(mountedEntity, () -> {
            if (mountedNmsEntity != null) {
                mountedNmsEntity.stopNavigation();
            }
            if (!mountedEntity.isValid()) {
                return;
            }
            mountedEntity.eject();
            mountedEntity.remove();
        });
    }

    /**
     * Refreshes the Grim exemption only while the player is riding this mount.
     *
     * <p>Mount movement is legitimate only while the player is actually a
     * passenger. Refreshing before and after the behavior update gives Grim
     * context before the cosmetic applies vehicle velocity and keeps the window
     * alive for delayed AntiKB-style processing. The short expiry naturally ends
     * after the player dismounts.</p>
     *
     * @param currentEntity the mounted entity being updated
     */
    private void refreshGrimExemptionIfRiding(Entity currentEntity) {
        if (player.getVehicle() == currentEntity) {
            plugin.getGrimExemptionManager().exemptMountRide(player);
        }
    }
}
