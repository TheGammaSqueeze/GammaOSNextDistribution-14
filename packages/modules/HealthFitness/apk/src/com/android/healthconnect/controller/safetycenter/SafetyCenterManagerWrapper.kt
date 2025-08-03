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

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.util.Log
import javax.inject.Inject

/** A wrapper for the SafetyCenterManager system service. */
class SafetyCenterManagerWrapper @Inject constructor() {

    /** Sets the latest safety source data for Safety Center. */
    fun setSafetySourceData(
        context: Context,
        safetySourceId: String,
        safetySourceData: SafetySourceData?,
        safetyEvent: SafetyEvent
    ) {
        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)
        if (safetyCenterManager == null) {
            Log.e(TAG, "System service SAFETY_CENTER_SERVICE (SafetyCenterManager) is null")
            return
        }
        try {
            safetyCenterManager.setSafetySourceData(safetySourceId, safetySourceData, safetyEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SafetySourceData", e)
            return
        }
    }

    /** Returns true is SafetyCenter page is enabled, false otherwise. */
    fun isEnabled(context: Context?): Boolean {
        if (context == null) {
            Log.e(TAG, "Context is null at SafetyCenterManagerWrapper#isEnabled")
            return false
        }
        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)
        if (safetyCenterManager == null) {
            Log.w(TAG, "System service SAFETY_CENTER_SERVICE (SafetyCenterManager) is null")
            return false
        }
        return try {
            safetyCenterManager.isSafetyCenterEnabled
        } catch (e: RuntimeException) {
            Log.e(TAG, "Calling isSafetyCenterEnabled failed.", e)
            false
        }
    }

    companion object {
        /**
         * Tag for logging.
         *
         * The tag is restricted to 23 characters (the maximum allowed for Android logging).
         */
        private const val TAG = "SafetyCenterManagerWrap"
    }
}
