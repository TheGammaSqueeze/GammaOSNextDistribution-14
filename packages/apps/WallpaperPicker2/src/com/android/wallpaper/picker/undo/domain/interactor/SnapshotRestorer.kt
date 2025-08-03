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

import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot

/** Defines interface for classes that can handle state restoration. */
interface SnapshotRestorer {

    /**
     * Sets up the restorer.
     *
     * @param store An object the can be used when a new snapshot should be stored; use this in
     *   response to state changes that you wish could be restored when the user asks to reset the
     *   changes.
     * @return A snapshot of the initial state as it was at the moment that this method was invoked.
     */
    suspend fun setUpSnapshotRestorer(store: SnapshotStore): RestorableSnapshot

    /** Restores the state to what is described in the given snapshot. */
    suspend fun restoreToSnapshot(snapshot: RestorableSnapshot)
}
