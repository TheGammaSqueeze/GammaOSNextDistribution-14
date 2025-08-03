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
package com.android.healthconnect.controller.permissions.connectedapps

import android.content.Context
import android.text.TextUtils
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.settingslib.widget.AppPreference
import dagger.hilt.android.EntryPointAccessors

class HealthAppPreference(context: Context, private val appMetadata: AppMetadata) :
    AppPreference(context), ComparablePreference {

    private var logger: HealthConnectLogger
    var logName : ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    init {
        title = appMetadata.appName
        icon = appMetadata.icon

        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    override fun onAttached() {
        super.onAttached()
        logger.logImpression(logName)
    }

    override fun setOnPreferenceClickListener(
        onPreferenceClickListener: OnPreferenceClickListener?
    ) {
        val loggingClickListener = OnPreferenceClickListener {
            logger.logInteraction(logName)
            onPreferenceClickListener?.onPreferenceClick(it) ?: false
        }
        super.setOnPreferenceClickListener(loggingClickListener)
    }

    override fun isSameItem(preference: Preference): Boolean {
        return preference is HealthAppPreference &&
            TextUtils.equals(appMetadata.appName, preference.appMetadata.appName)
    }

    override fun hasSameContents(preference: Preference): Boolean {
        return preference is HealthAppPreference && appMetadata == preference.appMetadata
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
    }
}

/** Allows comparison with a [Preference] to determine if it has been changed. */
internal interface ComparablePreference {
    /** Returns true if given Preference represents an item of the same kind. */
    fun isSameItem(preference: Preference): Boolean

    /** Returns true if given Preference contains the same data. */
    fun hasSameContents(preference: Preference): Boolean
}
