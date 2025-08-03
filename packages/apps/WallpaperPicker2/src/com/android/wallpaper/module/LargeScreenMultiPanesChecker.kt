/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.module

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SET_WALLPAPER
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.provider.Settings.*
import com.android.wallpaper.util.DisplayUtils

/** Utility class to check the support of multi panes integration (trampoline) */
class LargeScreenMultiPanesChecker : MultiPanesChecker {
    companion object {
        private const val TAG = "LargeScreenMultiPanesChecker"
        private const val VALUE_HIGHLIGHT_MENU = "top_level_wallpaper"
    }

    override fun isMultiPanesEnabled(context: Context): Boolean {
        val pm = context.packageManager
        val intent =
            getMultiPanesIntent(Intent(ACTION_SET_WALLPAPER).setPackage(context.packageName))

        val resolveInfo = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY)?.activityInfo
        return resolveInfo != null && DisplayUtils(context).isLargeScreenDevice()
    }

    override fun getMultiPanesIntent(intent: Intent): Intent {
        return Intent(ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY).apply {
            intent.extras?.let { putExtras(it) }
            putExtra(EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY, VALUE_HIGHLIGHT_MENU)
            putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                intent.toUri(Intent.URI_INTENT_SCHEME)
            )
        }
    }
}
