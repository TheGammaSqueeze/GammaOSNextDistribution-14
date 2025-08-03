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

package com.android.healthconnect.controller.utils.activity

import android.app.Activity
import android.content.Intent
import android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY
import android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI
import android.util.Log
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils.*

object EmbeddingUtils {

    private const val TAG = "EmbeddingUtils"
    private const val MENU_KEY_HEALTH_CONNECT = "top_level_privacy"

    fun maybeRedirectIntoTwoPaneSettings(activity: Activity): Boolean {
        return shouldUseTwoPaneSettings(activity) && tryRedirectTwoPaneSettings(activity)
    }

    private fun shouldUseTwoPaneSettings(activity: Activity): Boolean {
        if (!isEmbeddingActivityEnabled(activity)) {
            return false
        }
        return activity.isTaskRoot &&
            !isActivityEmbedded(activity) &&
            !isEmbeddedIntent(activity.intent)
    }

    private fun isEmbeddedIntent(intent: Intent): Boolean {
        return intent.hasExtra(EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI)
    }

    private fun tryRedirectTwoPaneSettings(activity: Activity): Boolean {
        try {
            val twoPaneIntent = getTwoPaneIntent(activity) ?: return false
            Log.i(TAG, "Health Connect restarting in Settings two-pane layout.")
            activity.startActivity(twoPaneIntent)
            activity.finishAndRemoveTask()
            return true
        } catch (ex: Exception) {
            Log.i(TAG, "Failed to restart Health Connect in Settings two-pane layout.")
            return false
        }
    }

    private fun getTwoPaneIntent(activity: Activity): Intent? {
        val twoPaneIntent = buildEmbeddingActivityBaseIntent(activity)
        return twoPaneIntent?.apply {
            putExtras(activity.intent)
            setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY, MENU_KEY_HEALTH_CONNECT)
            putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                activity.intent.toUri(Intent.URI_INTENT_SCHEME))
        }
    }
}
