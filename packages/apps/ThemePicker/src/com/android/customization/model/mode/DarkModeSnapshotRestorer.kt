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

package com.android.customization.model.mode

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DarkModeSnapshotRestorer : SnapshotRestorer {

    private val backgroundDispatcher: CoroutineDispatcher
    private val isActive: () -> Boolean
    private val setActive: suspend (Boolean) -> Unit

    private var store: SnapshotStore = SnapshotStore.NOOP

    constructor(
        context: Context,
        manager: UiModeManager,
        backgroundDispatcher: CoroutineDispatcher,
    ) : this(
        backgroundDispatcher = backgroundDispatcher,
        isActive = {
            context.applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_YES != 0
        },
        setActive = { isActive -> manager.setNightModeActivated(isActive) },
    )

    @VisibleForTesting
    constructor(
        backgroundDispatcher: CoroutineDispatcher,
        isActive: () -> Boolean,
        setActive: suspend (Boolean) -> Unit,
    ) {
        this.backgroundDispatcher = backgroundDispatcher
        this.isActive = isActive
        this.setActive = setActive
    }

    override suspend fun setUpSnapshotRestorer(store: SnapshotStore): RestorableSnapshot {
        this.store = store
        return snapshot(
            isActivated = isActive(),
        )
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        val isActivated = snapshot.args[KEY]?.toBoolean() == true
        withContext(backgroundDispatcher) { setActive(isActivated) }
    }

    fun store(
        isActivated: Boolean,
    ) {
        store.store(
            snapshot(
                isActivated = isActivated,
            ),
        )
    }

    private fun snapshot(
        isActivated: Boolean,
    ): RestorableSnapshot {
        return RestorableSnapshot(
            args =
                buildMap {
                    put(
                        KEY,
                        isActivated.toString(),
                    )
                }
        )
    }

    companion object {
        private const val KEY = "is_activated"
    }
}
