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

package com.android.permissioncontroller.safetycenter.ui.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.StatusUiData
import com.google.android.material.button.MaterialButton

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class StatusCardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    init {
        inflate(context, R.layout.view_status_card, this)
    }

    val statusImageView: ImageView by lazyView(R.id.status_image)
    val titleAndSummaryContainerView: LinearLayout by lazyView(R.id.status_title_and_summary)
    val titleView: TextView by lazyView(R.id.status_title)
    val summaryView: TextView by lazyView(R.id.status_summary)
    val reviewSettingsButton: MaterialButton by lazyView(R.id.review_settings_button)
    val rescanButton: MaterialButton by lazyView(R.id.rescan_button)

    fun showButtons(statusUiData: StatusUiData) {
        rescanButton.isEnabled = !statusUiData.isRefreshInProgress

        when (statusUiData.buttonToShow) {
            StatusUiData.ButtonToShow.RESCAN -> {
                rescanButton.visibility = VISIBLE
                reviewSettingsButton.visibility = GONE
            }
            StatusUiData.ButtonToShow.REVIEW_SETTINGS -> {
                rescanButton.visibility = GONE
                reviewSettingsButton.visibility = VISIBLE
            }
            null -> {
                rescanButton.visibility = GONE
                reviewSettingsButton.visibility = GONE
            }
        }
    }
}
