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

package com.android.customization.picker.color.domain.interactor

import android.util.Log
import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot

/** Handles state restoration for the color picker system. */
class ColorPickerSnapshotRestorer(
    private val interactor: ColorPickerInteractor,
) : SnapshotRestorer {

    private var snapshotStore: SnapshotStore = SnapshotStore.NOOP
    private var originalOption: ColorOptionModel? = null

    fun storeSnapshot(colorOptionModel: ColorOptionModel) {
        snapshotStore.store(snapshot(colorOptionModel))
    }

    override suspend fun setUpSnapshotRestorer(
        store: SnapshotStore,
    ): RestorableSnapshot {
        snapshotStore = store
        originalOption = interactor.getCurrentColorOption()
        return snapshot(originalOption)
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        val optionPackagesFromSnapshot: String? = snapshot.args[KEY_COLOR_OPTION_PACKAGES]
        originalOption?.let { optionToRestore ->
            if (
                optionToRestore.colorOption.serializedPackages != optionPackagesFromSnapshot ||
                    optionToRestore.colorOption.style.toString() !=
                        snapshot.args[KEY_COLOR_OPTION_STYLE]
            ) {
                Log.wtf(
                    TAG,
                    """ Original packages does not match snapshot packages to restore to. The 
                        | current implementation doesn't support undo, only a reset back to the 
                        | original color option."""
                        .trimMargin(),
                )
            }

            interactor.select(optionToRestore)
        }
    }

    private fun snapshot(colorOptionModel: ColorOptionModel? = null): RestorableSnapshot {
        val snapshotMap = mutableMapOf<String, String>()
        colorOptionModel?.let {
            snapshotMap[KEY_COLOR_OPTION_PACKAGES] = colorOptionModel.colorOption.serializedPackages
            snapshotMap[KEY_COLOR_OPTION_STYLE] = colorOptionModel.colorOption.style.toString()
        }
        return RestorableSnapshot(snapshotMap)
    }

    companion object {
        private const val TAG = "ColorPickerSnapshotRestorer"
        private const val KEY_COLOR_OPTION_PACKAGES = "color_packages"
        private const val KEY_COLOR_OPTION_STYLE = "color_style"
    }
}
