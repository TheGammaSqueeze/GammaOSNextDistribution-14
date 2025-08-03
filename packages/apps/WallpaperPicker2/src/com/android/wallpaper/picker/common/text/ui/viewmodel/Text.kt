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
 *
 */

package com.android.wallpaper.picker.common.text.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes

sealed class Text {
    data class Resource(
        @StringRes val res: Int,
    ) : Text()

    data class Loaded(
        val text: String,
    ) : Text()

    fun asString(context: Context): String {
        return when (this) {
            is Resource -> context.getString(res)
            is Loaded -> text
        }
    }

    companion object {
        /**
         * Returns `true` if the given [Text] instances evaluate to the values; `false` otherwise.
         */
        fun evaluationEquals(
            context: Context,
            first: Text?,
            second: Text?,
        ): Boolean {
            return first?.asString(context) == second?.asString(context)
        }
    }
}
