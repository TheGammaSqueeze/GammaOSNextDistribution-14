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
package com.android.permissioncontroller.permission.ui.v33

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
data class AdvancedConfirmDialogArgs(
    @DrawableRes val iconId: Int = 0,
    @StringRes val titleId: Int = 0,
    @StringRes val messageId: Int,
    @StringRes val negativeButtonTextId: Int,
    @StringRes val positiveButtonTextId: Int,
    val changeRequest: AppPermissionViewModel.ChangeRequest? = null,
    val setOneTime: Boolean? = null,
    val buttonClicked: Int? = null
)
