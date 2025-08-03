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

package com.android.healthconnect.controller.shared.inactiveapp

import android.content.Context
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.settingslib.widget.AppPreference
import dagger.hilt.android.EntryPointAccessors

/** Custom preference for displaying an inactive app. */
class InactiveAppPreference constructor(context: Context) : AppPreference(context) {
    private var deleteButtonListener: OnClickListener? = null

    private var logger: HealthConnectLogger
    var logName: ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    init {
        widgetLayoutResource = R.layout.widget_delete_inactive_app
        isSelectable = false
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    override fun onAttached() {
        super.onAttached()
        logger.logImpression(logName)
        logger.logImpression(AppPermissionsElement.INACTIVE_APP_DELETE_BUTTON)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame: ViewGroup? = holder.findViewById(android.R.id.widget_frame) as ViewGroup?
        widgetFrame?.setOnClickListener(deleteButtonListener)
        widgetFrame?.tag = "Delete button inactive app"

        val widgetFrameParent: ViewGroup? = widgetFrame?.parent as ViewGroup?
        widgetFrameParent?.setPaddingRelative(
            widgetFrameParent.paddingStart,
            widgetFrameParent.paddingTop,
            /* end = */ 0,
            widgetFrameParent.paddingBottom)
    }

    /** Sets the listener for delete button click. */
    fun setOnDeleteButtonClickListener(listener: OnClickListener) {
        val loggingClickListener = OnClickListener {
            logger.logInteraction(AppPermissionsElement.INACTIVE_APP_DELETE_BUTTON)
            listener.onClick(it)
        }
        deleteButtonListener = loggingClickListener
        notifyChanged()
    }
}
