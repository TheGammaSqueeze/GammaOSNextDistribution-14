/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.customization.picker.grid.domain.interactor

import android.util.Log
import com.android.customization.picker.grid.shared.model.GridOptionItemModel
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot

class GridSnapshotRestorer(
    private val interactor: GridInteractor,
) : SnapshotRestorer {

    private var store: SnapshotStore = SnapshotStore.NOOP
    private var originalOption: GridOptionItemModel? = null

    override suspend fun setUpSnapshotRestorer(store: SnapshotStore): RestorableSnapshot {
        this.store = store
        val option = interactor.getSelectedOption()
        originalOption = option
        return snapshot(option)
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        val optionNameFromSnapshot = snapshot.args[KEY_GRID_OPTION_NAME]
        originalOption?.let { optionToRestore ->
            if (optionToRestore.name != optionNameFromSnapshot) {
                Log.wtf(
                    TAG,
                    """Original snapshot name was ${optionToRestore.name} but we're being told to
                        | restore to $optionNameFromSnapshot. The current implementation doesn't
                        | support undo, only a reset back to the original grid option."""
                        .trimMargin(),
                )
            }

            interactor.setSelectedOption(optionToRestore)
        }
    }

    fun store(option: GridOptionItemModel) {
        store.store(snapshot(option))
    }

    private fun snapshot(option: GridOptionItemModel?): RestorableSnapshot {
        return RestorableSnapshot(
            args =
                buildMap {
                    option?.name?.let { optionName -> put(KEY_GRID_OPTION_NAME, optionName) }
                }
        )
    }

    companion object {
        private const val TAG = "GridSnapshotRestorer"
        private const val KEY_GRID_OPTION_NAME = "grid_option"
    }
}
