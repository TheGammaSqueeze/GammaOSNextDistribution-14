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

package com.android.systemui.tv.media

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.PowerExemptionManager
import android.util.Log
import android.view.View
import com.android.internal.logging.UiEventLogger
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.media.flags.Flags
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.media.dialog.MediaOutputDialogFactory
import com.android.systemui.media.nearby.NearbyMediaDevicesManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import javax.inject.Inject

/**
 * Factory to create [TvMediaOutputDialogActivity] objects.
 */
class TvMediaOutputDialogFactory @Inject constructor(
        private val context: Context,
        mediaSessionManager: MediaSessionManager,
        lbm: LocalBluetoothManager?,
        starter: ActivityStarter,
        broadcastSender: BroadcastSender,
        notifCollection: CommonNotifCollection,
        uiEventLogger: UiEventLogger,
        dialogTransitionAnimator: DialogTransitionAnimator,
        nearbyMediaDevicesManager: NearbyMediaDevicesManager,
        audioManager: AudioManager,
        powerExemptionManager: PowerExemptionManager,
        keyGuardManager: KeyguardManager,
        featureFlags: FeatureFlags,
        userTracker: UserTracker
) : MediaOutputDialogFactory(context, mediaSessionManager, lbm, starter, broadcastSender,
        notifCollection, uiEventLogger, dialogTransitionAnimator,
        nearbyMediaDevicesManager, audioManager, powerExemptionManager, keyGuardManager,
        featureFlags, userTracker) {
    companion object {
        private const val TAG = "TvMediaOutputDialogFactory"
    }

    /** Creates a [TvMediaOutputDialog]. */
    override fun create(packageName: String, aboveStatusBar: Boolean, view: View?) {
        if (!Flags.enableTvMediaOutputDialog()) {
            // Not showing any media output dialog since the mobile version is not navigable on TV.
            Log.w(TAG, "enable_tv_media_output_dialog flag is disabled")
            return
        }

        val intent = Intent(context, TvMediaOutputDialogActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Dismiss the [TvMediaOutputDialog] if it exists. */
    override fun dismiss() {
        // NOOP
    }
}
