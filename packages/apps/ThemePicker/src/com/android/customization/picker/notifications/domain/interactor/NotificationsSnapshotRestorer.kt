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
 */

package com.android.customization.picker.notifications.domain.interactor

import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Handles state restoration for notification settings. */
class NotificationsSnapshotRestorer(
    private val interactor: NotificationSettingsInteractor,
    @BackgroundDispatcher private val backgroundScope: CoroutineScope,
) : SnapshotRestorer {

    private var snapshotStore: SnapshotStore = SnapshotStore.NOOP

    private fun storeSnapshot(model: NotificationSnapshotModel) {
        snapshotStore.store(snapshot(model))
    }

    override suspend fun setUpSnapshotRestorer(
        store: SnapshotStore,
    ): RestorableSnapshot {
        snapshotStore = store
        backgroundScope.launch {
            interactor.isShowNotificationsOnLockScreenEnabled.collect {
                storeSnapshot(
                    NotificationSnapshotModel(isShowNotificationsOnLockScreenEnabled = it)
                )
            }
        }
        return snapshot(
            NotificationSnapshotModel(interactor.isShowNotificationsOnLockScreenEnabled.value)
        )
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        val isShowNotificationsOnLockScreenEnabled =
            snapshot.args[KEY_IS_SHOW_NOTIFICATIONS_ON_LOCK_SCREEN_ENABLED]?.toBoolean() ?: false
        interactor.setShowNotificationsOnLockscreenEnabled(isShowNotificationsOnLockScreenEnabled)
    }

    private fun snapshot(model: NotificationSnapshotModel): RestorableSnapshot {
        return RestorableSnapshot(
            mapOf(
                KEY_IS_SHOW_NOTIFICATIONS_ON_LOCK_SCREEN_ENABLED to
                    model.isShowNotificationsOnLockScreenEnabled.toString(),
            )
        )
    }

    companion object {
        private const val KEY_IS_SHOW_NOTIFICATIONS_ON_LOCK_SCREEN_ENABLED =
            "is_show_notifications_on_lock_screen_enabled"
    }
}

/** Snapshot of notification settings relevant to the theme picker. */
private data class NotificationSnapshotModel(
    /** Whether notifications are shown on the lock screen. */
    val isShowNotificationsOnLockScreenEnabled: Boolean = false,
)
