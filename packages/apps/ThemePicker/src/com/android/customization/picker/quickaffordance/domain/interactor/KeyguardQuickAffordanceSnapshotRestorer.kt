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

package com.android.customization.picker.quickaffordance.domain.interactor

import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot

/** Handles state restoration for the quick affordances system. */
class KeyguardQuickAffordanceSnapshotRestorer(
    private val interactor: KeyguardQuickAffordancePickerInteractor,
    private val client: CustomizationProviderClient,
) : SnapshotRestorer {

    private var snapshotStore: SnapshotStore = SnapshotStore.NOOP

    suspend fun storeSnapshot() {
        snapshotStore.store(snapshot())
    }

    override suspend fun setUpSnapshotRestorer(
        store: SnapshotStore,
    ): RestorableSnapshot {
        snapshotStore = store
        return snapshot()
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        // reset all current selections
        interactor.unselectAll()

        val allSelections = checkNotNull(snapshot.args[KEY_SELECTIONS])
        if (allSelections.isEmpty()) return

        val selections: List<Pair<String, String>> =
            allSelections.split(SELECTION_SEPARATOR).map { selection ->
                val (slotId, affordanceId) = selection.split(SLOT_AFFORDANCE_SEPARATOR)
                slotId to affordanceId
            }

        selections.forEach { (slotId, affordanceId) ->
            interactor.select(
                slotId,
                affordanceId,
            )
        }
    }

    private suspend fun snapshot(): RestorableSnapshot {
        return RestorableSnapshot(
            mapOf(
                KEY_SELECTIONS to
                    client.querySelections().joinToString(SELECTION_SEPARATOR) { selection ->
                        "${selection.slotId}${SLOT_AFFORDANCE_SEPARATOR}${selection.affordanceId}"
                    }
            )
        )
    }

    companion object {
        private const val KEY_SELECTIONS = "selections"
        private const val SLOT_AFFORDANCE_SEPARATOR = "->"
        private const val SELECTION_SEPARATOR = "|"
    }
}
