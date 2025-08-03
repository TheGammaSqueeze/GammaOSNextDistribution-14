/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.android.wallpaper.picker.undo.data.repository

import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Encapsulates application state for the undo system. */
class UndoRepository {
    private val snapshotByOwnerId = mutableMapOf<Int, RestorableSnapshot>()
    private val dirtyOwnerIds = MutableStateFlow(emptySet<Int>())

    /** Whether any area is "dirty" right now (meaning, it could be undone). */
    val isAnythingDirty: Flow<Boolean> =
        dirtyOwnerIds.map { it.isNotEmpty() }.distinctUntilChanged()

    /** Associates the given snapshot with the area with the given owner ID. */
    fun putSnapshot(ownerId: Int, snapshot: RestorableSnapshot) {
        snapshotByOwnerId[ownerId] = snapshot
    }

    /** Returns the latest snapshot for the area with the given owner ID. */
    fun getSnapshot(ownerId: Int): RestorableSnapshot? {
        return snapshotByOwnerId[ownerId]
    }

    /**
     * Marks the area with the owner of the given ID as dirty or not dirty.
     *
     * A "dirty" area is one that contains changes that can be undone. An area that is not "dirty"
     * does not currently have pending changes that can be undone.
     */
    fun putDirty(ownerId: Int, isDirty: Boolean) {
        if (isDirty) {
            dirtyOwnerIds.value = dirtyOwnerIds.value + setOf(ownerId)
        } else {
            dirtyOwnerIds.value = dirtyOwnerIds.value - setOf(ownerId)
        }
    }

    /**
     * Returns the set of IDs for owners of all areas that are currently marked as dirty (meaning
     * all areas that can currently be undone).
     */
    fun getAllDirty(): Collection<Int> {
        return dirtyOwnerIds.value.toSet()
    }

    /** Marks all areas as not dirty (meaning they can't be undone). */
    fun clearAllDirty() {
        dirtyOwnerIds.value = emptySet()
    }
}
