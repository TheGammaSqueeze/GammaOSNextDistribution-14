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
 */

package com.android.permissioncontroller.privacysources

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.hibernation.cancelUnusedAppsNotification
import com.android.permissioncontroller.hibernation.rescanAndPushDataToSafetyCenter
import java.util.Random

/** Privacy source for auto-revoked permissions. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AutoRevokePrivacySource : PrivacySource {
    override val shouldProcessProfileRequest: Boolean = false

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        cancelUnusedAppsNotification(context)
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: SafetyCenterReceiver.RefreshEvent
    ) {
        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = Random().nextLong()
        }

        val safetyRefreshEvent = getSafetyCenterEvent(refreshEvent, intent)
        rescanAndPushDataToSafetyCenter(context, sessionId, safetyRefreshEvent)
    }
}
