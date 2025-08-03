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

package com.android.healthconnect.controller.safetycenter

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.health.connect.HealthConnectManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceStatus
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.FeatureUtils
import javax.inject.Inject

class HealthConnectSafetySource
@Inject
constructor(
    private val featureUtils: FeatureUtils,
    private val safetyCenterManagerWrapper: SafetyCenterManagerWrapper
) {

    fun setSafetySourceData(context: Context, safetyEvent: SafetyEvent) {
        if (!safetyCenterManagerWrapper.isEnabled(context)) {
            return
        }
        if (!featureUtils.isEntryPointsEnabled()) {
            safetyCenterManagerWrapper.setSafetySourceData(
                context, HEALTH_CONNECT_SOURCE_ID, null, safetyEvent)
            return
        }

        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(
                    SafetySourceStatus.Builder(
                            context.getString(R.string.app_label),
                            context.getString(R.string.health_connect_summary),
                            SEVERITY_LEVEL_UNSPECIFIED)
                        .setPendingIntent(
                            PendingIntent.getActivity(
                                context,
                                /* requestCode= */ 0,
                                Intent(HealthConnectManager.ACTION_HEALTH_HOME_SETTINGS),
                                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE))
                        .build(),
                )
                .build()

        safetyCenterManagerWrapper.setSafetySourceData(
            context, HEALTH_CONNECT_SOURCE_ID, safetySourceData, safetyEvent)
    }

    /** Companion object for [HealthConnectPrivacySource]. */
    companion object {
        /** Source id for safety center source for health connect. */
        const val HEALTH_CONNECT_SOURCE_ID = "AndroidHealthConnect"
    }
}
