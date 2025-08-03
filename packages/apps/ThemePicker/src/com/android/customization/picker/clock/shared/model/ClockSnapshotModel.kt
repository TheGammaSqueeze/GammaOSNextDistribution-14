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

package com.android.customization.picker.clock.shared.model

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.android.customization.picker.clock.shared.ClockSize

/** Models application state for a clock option in a picker experience. */
data class ClockSnapshotModel(
    val clockId: String? = null,
    val clockSize: ClockSize? = null,
    val selectedColorId: String? = null,
    @IntRange(from = 0, to = 100) val colorToneProgress: Int? = null,
    @ColorInt val seedColor: Int? = null,
)
