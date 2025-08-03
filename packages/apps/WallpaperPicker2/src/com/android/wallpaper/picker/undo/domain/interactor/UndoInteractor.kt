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

package com.android.wallpaper.picker.undo.domain.interactor

import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Encapsulates the "undo" business logic.
 *
 * ## Usage
 * 1. Instantiate, injecting the supported [SnapshotRestorer] into it, one for each feature that
 *
 * ```
 *    should support undo functionality.
 * ```
 * 2. Call [startSession] which will bootstrap all passed-in [SnapshotRestorer] instances and
 *
 * ```
 *    hydrate our model with the latest snapshots from each one.
 * ```
 * 3. Observe [isUndoable] to know whether the UI for triggering an "undo" action should be made
 *
 * ```
 *    visible to the user.
 * ```
 * 4. Call [revertAll] when the user wishes to revert everything.
 */
class UndoInteractor(
    private val scope: CoroutineScope,
    private val repository: UndoRepository,
    private val restorerByOwnerId: Map<Int, SnapshotRestorer>,
) {

    /** Whether the current state is undoable. */
    val isUndoable: Flow<Boolean> = repository.isAnythingDirty

    /** Bootstraps the undo system, querying each undo-supporting area for the initial snapshot. */
    fun startSession() {
        repository.clearAllDirty()
        restorerByOwnerId.forEach { (ownerId, restorer) ->
            scope.launch {
                val initialSnapshot =
                    restorer.setUpSnapshotRestorer(
                        object : SnapshotStore {
                            override fun retrieve(): RestorableSnapshot {
                                return repository.getSnapshot(ownerId)
                                    ?: error(
                                        "No snapshot for this owner ID! Did you call this before" +
                                            " storing a snapshot?"
                                    )
                            }

                            override fun store(snapshot: RestorableSnapshot) {
                                val initialSnapshot = repository.getSnapshot(ownerId)
                                repository.putDirty(
                                    ownerId = ownerId,
                                    isDirty = initialSnapshot != snapshot
                                )
                            }
                        }
                    )

                repository.putSnapshot(
                    ownerId = ownerId,
                    snapshot = initialSnapshot,
                )
            }
        }
    }

    /** Triggers a revert for all areas. */
    fun revertAll() {
        repository.getAllDirty().forEach { ownerId ->
            val restorer = restorerByOwnerId[ownerId]
            val snapshot = repository.getSnapshot(ownerId)
            if (restorer != null && snapshot != null) {
                scope.launch { restorer.restoreToSnapshot(snapshot) }
            }
        }

        repository.clearAllDirty()
    }
}
