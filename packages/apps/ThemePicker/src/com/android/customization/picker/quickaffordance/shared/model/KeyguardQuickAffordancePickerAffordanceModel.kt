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
 *
 */

package com.android.customization.picker.quickaffordance.shared.model

import android.content.Intent
import androidx.annotation.DrawableRes

/** Models a quick affordance. */
data class KeyguardQuickAffordancePickerAffordanceModel(
    val id: String,
    val name: String,
    /**
     * The resource ID for a drawable of the icon. This is in the namespace of system UI so it
     * should be queries from that package.
     */
    @DrawableRes val iconResourceId: Int,
    /** Whether this quick affordance is enabled. */
    val isEnabled: Boolean,
    /** If not enabled, the user-visible message explaining why. */
    val enablementExplanation: String,
    /**
     * If not enabled, an optional label for a button that takes the user to a destination where
     * they can re-enable it.
     */
    val enablementActionText: String?,
    /**
     * If not enabled, an optional [Intent] for a button that takes the user to a destination where
     * they can re-enable it.
     */
    val enablementActionIntent: Intent?,
    /** Optional [Intent] to use to start an activity to configure this affordance. */
    val configureIntent: Intent?,
)
