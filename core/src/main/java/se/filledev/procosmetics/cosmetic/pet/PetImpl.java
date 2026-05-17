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
package se.filledev.procosmetics.cosmetic.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.cosmetic.pet.Pet;
import se.filledev.procosmetics.api.cosmetic.pet.PetBehavior;
import se.filledev.procosmetics.api.cosmetic.pet.PetType;
import se.filledev.procosmetics.api.event.CosmeticEntitySpawnEvent;
import se.filledev.procosmetics.api.nms.NMSEntity;
import se.filledev.procosmetics.api.user.User;
import se.filledev.procosmetics.cosmetic.CosmeticImpl;
import se.filledev.procosmetics.nms.NMSEntityImpl;
import se.filledev.procosmetics.util.CosmeticEntitySpawner;
import se.filledev.procosmetics.util.MathUtil;
import se.filledev.procosmetics.util.Scheduler;

public class PetImpl extends CosmeticImpl<PetType, PetBehavior> implements Pet {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    protected NMSEntity nmsEntity;
    protected Entity entity;
    protected Location location;

    public PetImpl(ProCosmeticsPlugin plugin, User user, PetType type, PetBehavior behavior) {
        super(plugin, user, type, behavior);
    }

    @Override
    protected void onEquip() {
        if (!spawnAt(player.getLocation())) {
            abortEquip();
            return;
        }
        runTaskTimer(plugin, 0L, 10L);
    }

    @Override
    protected void onUpdate() {
        Entity currentEntity = entity;
        NMSEntity currentNMSEntity = nmsEntity;

        if (currentNMSEntity == null || currentEntity == null || !currentEntity.isValid()) {
            return;
        }
        boolean moving = user.isMoving();

        if (Scheduler.isFolia()) {
            Scheduler.run(currentEntity, () -> handleEntityThreadUpdate(currentEntity, currentNMSEntity, moving));
            return;
        }
        handleEntityThreadUpdate(currentEntity, currentNMSEntity, moving);
    }

    @Override
    protected void onUnequip() {
        despawn();
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
     * Spawns the pet through the shared cosmetic living-entity spawn path.
     *
     * <p>Pets are user-facing equip state, so a blocked spawn must be reported back
     * to the base equip flow as a failure instead of allowing a "success" message
     * and database save for a pet that does not exist. The shared spawner also tags
     * the entity before protection plugins finish handling the spawn event.</p>
     *
     * @param location the location where the pet should appear
     * @return {@code true} when the pet entity and NMS wrapper were created successfully
     */
    private boolean spawnAt(Location location) {
        this.location = location.clone();

        despawn();

        entity = CosmeticEntitySpawner.spawnLiving(location, cosmeticType.getEntityType(), entity -> {
            Component nameTag = user.translate(
                    "cosmetic.pets.name_tag",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("cosmetic", cosmeticType.getName(user))
            );
            entity.setCustomName(SERIALIZER.serialize(nameTag));
            entity.setCustomNameVisible(!nameTag.equals(Component.empty()));
            entity.setSilent(true);

            if (entity instanceof LivingEntity livingEntity) {
                AttributeInstance attribute = livingEntity.getAttribute(Attribute.SCALE);

                if (attribute != null) {
                    attribute.setBaseValue(cosmeticType.getScale());
                }
            }

            if (cosmeticType.isBaby()) {
                if (entity instanceof Ageable ageable) {
                    ageable.setBaby();
                    ageable.setAgeLock(true);
                    ageable.setBreed(false);
                }
            }
            nmsEntity = plugin.getNMSManager().entityToNMSEntity(entity);
            behavior.onSetupEntity(this, entity);
        });

        // Ensure that the entity has been spawned and was not blocked by other plugins
        if (entity == null || nmsEntity == null || !entity.isValid()) {
            nmsEntity = null;
            entity = null;
            return false;
        }
        Sound spawnSound = cosmeticType.getSpawnSound();
        if (spawnSound != null) {
            entity.getWorld().playSound(location, spawnSound, 0.5f, 1.0f);
        }
        nmsEntity.removePathfinder();
        nmsEntity.stopNavigation();

        CosmeticEntitySpawnEvent event = new CosmeticEntitySpawnEvent(plugin, user, player, entity);
        plugin.getServer().getPluginManager().callEvent(event);
        return true;
    }

    private void despawn() {
        NMSEntity currentNMSEntity = nmsEntity;
        nmsEntity = null;
        Entity currentEntity = entity;
        entity = null;

        if (currentEntity != null) {
            Scheduler.run(currentEntity, () -> {
                if (currentNMSEntity != null) {
                    currentNMSEntity.stopNavigation();
                }
                if (currentEntity.isValid()) {
                    currentEntity.remove();
                }
            });
        }
    }

    private void handleEntityThreadUpdate(Entity currentEntity, NMSEntity currentNMSEntity, boolean moving) {
        if (entity != currentEntity || nmsEntity != currentNMSEntity || !currentEntity.isValid()) {
            return;
        }
        if (moving) {
            currentNMSEntity.follow(player);
        }

        if (cosmeticType.getTossItem() != null) {
            dropDespawningItem(currentEntity, cosmeticType.getTossItem());
        }
        behavior.onUpdate(this, currentEntity);
    }

    protected void dropDespawningItem(Entity sourceEntity, ItemStack itemStack) {
        NMSEntityImpl nmsEntity = plugin.getNMSManager().createEntity(sourceEntity.getWorld(), EntityType.ITEM);
        nmsEntity.setEntityItemStack(itemStack);
        nmsEntity.setVelocity(
                MathUtil.randomRange(-0.5d, 0.5d),
                MathUtil.randomRange(-0.1d, 0.3d),
                MathUtil.randomRange(-0.5d, 0.5d)
        );
        nmsEntity.setPositionRotation(sourceEntity.getLocation(location).add(0.0d, 0.2d, 0.0d));
        nmsEntity.getTracker().startTracking();
        nmsEntity.getTracker().destroyAfter(70);
    }
}
