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
package se.filledev.procosmetics.api.cosmetic.music;

import se.filledev.procosmetics.api.cosmetic.CosmeticType;

/**
 * Represents a type of music cosmetic.
 */
public interface MusicType extends CosmeticType<MusicType, MusicBehavior> {

    /*
     * NoteBlockAPI-based song playback is disabled for Folia compatibility.
     *
     * Original API surface:
     *
     *   Song getSong();
     *   Builder song(Song song);
     */

    /**
     * Builder interface for constructing music type instances.
     */
    interface Builder extends CosmeticType.Builder<MusicType, MusicBehavior, Builder> {

        /**
         * Builds and returns the configured music type instance.
         *
         * @return the built music type
         */
        @Override
        MusicType build();
    }
}
