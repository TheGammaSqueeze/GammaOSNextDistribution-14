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
package com.android.wallpaper.util

import android.annotation.AttrRes
import android.annotation.ColorInt
import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat

object SystemColors {

    /**
     * Returns the color by fetching the resId from the theme. Throws an exception when resource Id
     * is not available in the theme.
     */
    @JvmStatic
    @ColorInt
    fun getColor(context: Context, @AttrRes resId: Int): Int {
        val colorValue = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(resId, colorValue, /* resolveRefs= */ true)) {
            if (
                TypedValue.TYPE_FIRST_COLOR_INT <= colorValue.type &&
                    colorValue.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return colorValue.data
            }
            if (colorValue.type == TypedValue.TYPE_STRING) {
                return ContextCompat.getColor(context, colorValue.resourceId)
            }
        }
        throw IllegalArgumentException(
            "Theme is missing expected color ${context.resources.getResourceName(resId)} " +
                "($resId) references a missing resource."
        )
    }
}
