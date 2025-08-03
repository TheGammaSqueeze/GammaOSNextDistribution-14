/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.ui.viewmodel

import android.os.Bundle
import android.os.UserHandle
import com.android.intentresolver.v2.ResolverActivity.PROFILE_PERSONAL
import com.android.intentresolver.v2.ResolverActivity.PROFILE_WORK
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ResolverRequest
import com.android.intentresolver.v2.validation.Validation
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.types.value
import com.android.intentresolver.v2.validation.validateFrom

const val EXTRA_CALLING_USER = "com.android.internal.app.ResolverActivity.EXTRA_CALLING_USER"
const val EXTRA_SELECTED_PROFILE =
    "com.android.internal.app.ResolverActivity.EXTRA_SELECTED_PROFILE"
const val EXTRA_IS_AUDIO_CAPTURE_DEVICE = "is_audio_capture_device"

fun readResolverRequest(launch: ActivityModel): ValidationResult<ResolverRequest> {
    @Suppress("DEPRECATION")
    return validateFrom((launch.intent.extras ?: Bundle())::get) {
        val callingUser = optional(value<UserHandle>(EXTRA_CALLING_USER))
        val selectedProfile = checkSelectedProfile()
        val audioDevice = optional(value<Boolean>(EXTRA_IS_AUDIO_CAPTURE_DEVICE)) ?: false
        ResolverRequest(launch.intent, selectedProfile, callingUser, audioDevice)
    }
}

private fun Validation.checkSelectedProfile(): Profile.Type? {
    return when (val selected = optional(value<Int>(EXTRA_SELECTED_PROFILE))) {
        null -> null
        PROFILE_PERSONAL -> Profile.Type.PERSONAL
        PROFILE_WORK -> Profile.Type.WORK
        else ->
            error(
                EXTRA_SELECTED_PROFILE +
                    " has invalid value ($selected)." +
                    " Must be either ResolverActivity.PROFILE_PERSONAL ($PROFILE_PERSONAL)" +
                    " or ResolverActivity.PROFILE_WORK ($PROFILE_WORK)."
            )
    }
}
