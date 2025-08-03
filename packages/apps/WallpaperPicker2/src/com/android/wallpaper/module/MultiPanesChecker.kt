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

/** Checks if the device is supporting multi panes' features */
interface MultiPanesChecker {
    /**
     * Checks if the device is supporting multi-panes
     *
     * @param context the Activity's context
     * @return `true` if supported, `false` otherwise.
     */
    fun isMultiPanesEnabled(context: Context): Boolean

    /**
     * Gets the intent to start multi-panes landscape Activity
     *
     * @param intent the Activity's intent
     * @return [Intent] to start multi panels
     */
    fun getMultiPanesIntent(intent: Intent): Intent?
}
