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
package se.filledev.procosmetics.hook.grim;

import org.bukkit.entity.Player;
import se.filledev.procosmetics.ProCosmeticsPlugin;
import se.filledev.procosmetics.api.config.Config;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Tracks short GrimAC exemption windows created by ProCosmetics movement.
 *
 * <p>Grim performs much of its checking asynchronously, so this manager stores
 * only UUIDs and wall-clock expiry times. That keeps the Grim event handler
 * thread-safe and avoids accessing Bukkit or Folia player state from a Grim
 * worker thread.</p>
 */
public class GrimExemptionManager {

    private static final String GRIM_PLUGIN_NAME = "GrimAC";
    private static final long MILLIS_PER_TICK = 50L;
    private static final long DEFAULT_GADGET_FALLBACK_TICKS = 40L;
    private static final long DEFAULT_MOUNT_REFRESH_TICKS = 40L;
    private static final long DEFAULT_VELOCITY_TICKS = 120L;
    private static final long DEFAULT_GRACE_TICKS = 10L;

    private final ProCosmeticsPlugin plugin;
    private final ConcurrentMap<UUID, Long> exemptUntil = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final long gadgetFallbackTicks;
    private final long mountRefreshTicks;
    private final long velocityTicks;
    private final long graceTicks;

    private boolean hooked;

    public GrimExemptionManager(ProCosmeticsPlugin plugin) {
        this.plugin = plugin;

        Config config = plugin.getConfigManager().getMainConfig();
        this.enabled = getBoolean(config, "grim.enabled", true);
        this.gadgetFallbackTicks = getTicks(config, "grim.gadget_fallback_ticks", DEFAULT_GADGET_FALLBACK_TICKS);
        this.mountRefreshTicks = getTicks(config, "grim.mount_refresh_ticks", DEFAULT_MOUNT_REFRESH_TICKS);
        this.velocityTicks = getTicks(config, "grim.velocity_ticks", DEFAULT_VELOCITY_TICKS);
        this.graceTicks = getTicks(config, "grim.grace_ticks", DEFAULT_GRACE_TICKS);
    }

    /**
     * Hooks into GrimAC if it is installed and the integration is enabled.
     *
     * <p>The GrimAPI classes are only touched from this method after Bukkit has
     * confirmed that the {@code GrimAC} plugin is present. This keeps
     * ProCosmetics loadable on servers that do not use Grim.</p>
     */
    public void hook() {
        if (!enabled || hooked || plugin.getServer().getPluginManager().getPlugin(GRIM_PLUGIN_NAME) == null) {
            return;
        }

        try {
            plugin.getLogger().info("Hooking into GrimAC...");
            GrimEventRegistrar.register(plugin, this);
            hooked = true;
            plugin.getLogger().info("Hooked into GrimAC.");
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into GrimAC. Cosmetic movement exemptions will be disabled.", throwable);
        }
    }

    /**
     * Clears stored exemptions and unregisters the Grim event listener.
     *
     * <p>This prevents old exemption windows from surviving plugin reloads and
     * stops Grim from calling back into ProCosmetics after shutdown.</p>
     */
    public void shutdown() {
        exemptUntil.clear();

        if (!hooked) {
            return;
        }
        try {
            GrimEventRegistrar.unregister(plugin);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Failed to unregister GrimAC listeners during shutdown.", throwable);
        } finally {
            hooked = false;
        }
    }

    /**
     * Exempts a player after a successful gadget activation.
     *
     * <p>Some gadgets have a configured active duration while instant gadgets only
     * apply velocity or spawn an entity once. Taking the larger of the configured
     * duration and the fallback keeps active gadgets covered without exempting a
     * player merely for having a gadget equipped.</p>
     *
     * @param player the player who used the gadget
     * @param activeDurationTicks the gadget's configured active duration in ticks
     */
    public void exemptGadgetUse(Player player, long activeDurationTicks) {
        exempt(player.getUniqueId(), Math.max(activeDurationTicks, gadgetFallbackTicks) + graceTicks);
    }

    /**
     * Refreshes the exemption while the player is actively riding a ProCosmetics mount.
     *
     * <p>This method is intentionally called from the mount update loop instead
     * of when the mount is merely equipped. As soon as the player dismounts, no
     * new refresh is written and the short window expires naturally.</p>
     *
     * @param player the player riding the mount
     */
    public void exemptMountRide(Player player) {
        exempt(player.getUniqueId(), mountRefreshTicks + graceTicks);
    }

    /**
     * Exempts a player for a short ProCosmetics velocity movement.
     *
     * <p>Shared movement helpers use this for one-off pushes and pulls where the
     * call site does not know the full cosmetic duration. Grim's AntiKB-style
     * checks can evaluate knockback after delayed transaction acknowledgements,
     * so this uses a dedicated velocity window instead of the shorter generic
     * gadget fallback.</p>
     *
     * @param player the player being moved by ProCosmetics
     */
    public void exemptCosmeticMovement(Player player) {
        exempt(player.getUniqueId(), velocityTicks + graceTicks);
    }

    /**
     * Exempts a player for the same window as their ProCosmetics fall protection.
     *
     * <p>Fall protection is applied in the same code paths that launch or move
     * players. Mirroring that window into Grim prevents a cosmetic from allowing
     * fall damage safely while Grim still treats the matching movement as
     * suspicious.</p>
     *
     * @param uuid the player UUID
     * @param movementTicks the expected protected movement window in ticks
     */
    public void exemptCosmeticMovement(UUID uuid, long movementTicks) {
        exempt(uuid, Math.max(movementTicks, velocityTicks) + graceTicks);
    }

    /**
     * Checks whether Grim should currently ignore a player.
     *
     * <p>This method is called from Grim's asynchronous check event, so it only
     * reads the concurrent UUID map and removes expired entries with a
     * compare-and-remove operation.</p>
     *
     * @param uuid the player UUID Grim is checking
     * @return {@code true} if the player is still inside a ProCosmetics movement window
     */
    public boolean isExempt(UUID uuid) {
        if (!enabled || !hooked) {
            return false;
        }
        Long expiresAt = exemptUntil.get(uuid);

        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            exemptUntil.remove(uuid, expiresAt);
            return false;
        }
        return true;
    }

    /**
     * Stores the latest exemption expiry for a player.
     *
     * <p>Multiple cosmetics can move the same player close together, so the map
     * keeps the furthest expiry instead of allowing a shorter later movement to
     * reduce an existing protection window.</p>
     *
     * @param uuid the player UUID
     * @param ticks the number of ticks the exemption should last
     */
    private void exempt(UUID uuid, long ticks) {
        if (!enabled || ticks <= 0L) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + ticks * MILLIS_PER_TICK;
        exemptUntil.merge(uuid, expiresAt, Math::max);
    }

    private static boolean getBoolean(Config config, String path, boolean defaultValue) {
        if (!config.hasKey(path)) {
            return defaultValue;
        }
        return config.getBoolean(path);
    }

    private static long getTicks(Config config, String path, long defaultValue) {
        if (!config.hasKey(path)) {
            return defaultValue;
        }
        return Math.max(0L, config.getInt(path));
    }
}
