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

package com.android.wallpaper.testing

import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot

val FAKE_RESTORERS =
    mapOf(
        1 to FakeSnapshotRestorer(1),
        2 to FakeSnapshotRestorer(2),
        3 to FakeSnapshotRestorer(3),
    )

fun snapshot(ownerId: Int, version: Int): RestorableSnapshot {
    return RestorableSnapshot(
        mapOf(
            KEY_OWNER_ID to "$ownerId",
            KEY_VERSION to "$version",
        )
    )
}

class FakeSnapshotRestorer(
    private val ownerId: Int,
) : SnapshotRestorer {
    private lateinit var store: SnapshotStore
    var restored: RestorableSnapshot? = null
        private set

    fun update(version: Int) {
        store.store(snapshot(ownerId, version))
    }

    override suspend fun setUpSnapshotRestorer(
        store: SnapshotStore,
    ): RestorableSnapshot {
        this.store = store
        return snapshot(
            ownerId = ownerId,
            version = 0,
        )
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        restored = snapshot
    }
}

private const val KEY_OWNER_ID = "ownerId"
private const val KEY_VERSION = "version"
