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

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.android.intentresolver.inject.ChooserServiceFlags
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ActivityModel.Companion.ACTIVITY_MODEL_KEY
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val TAG = "ChooserViewModel"

@HiltViewModel
class ChooserViewModel
@Inject
constructor(
    args: SavedStateHandle,
    flags: ChooserServiceFlags,
) : ViewModel() {

    /** Parcelable-only references provided from the creating Activity */
    val activityModel: ActivityModel =
        requireNotNull(args[ACTIVITY_MODEL_KEY]) {
            "ActivityModel missing in SavedStateHandle! ($ACTIVITY_MODEL_KEY)"
        }

    /** The result of reading and validating the inputs provided in savedState. */
    private val status: ValidationResult<ChooserRequest> = readChooserRequest(activityModel, flags)

    val chooserRequest: ChooserRequest by lazy {
        when (status) {
            is Valid -> status.value
            is Invalid -> error(status.errors)
        }
    }

    fun init(): Boolean {
        Log.i(TAG, "viewModel init")
        if (status is Invalid) {
            status.errors.forEach { finding -> finding.log(TAG) }
            return false
        }
        Log.i(TAG, "request = $chooserRequest")
        return true
    }
}
