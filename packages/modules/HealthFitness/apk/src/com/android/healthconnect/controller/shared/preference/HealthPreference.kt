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
package com.android.healthconnect.controller.shared.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import com.android.healthconnect.controller.permissions.connectedapps.ComparablePreference
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** A [Preference] that allows logging. */
open class HealthPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs), ComparablePreference {

    private var logger: HealthConnectLogger
    var logName: ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    override fun onAttached() {
        super.onAttached()
        logger.logImpression(logName)
    }

    // TODO (b/270944053) - This does not currently work for preferences defined in XML
    //  because they don't have the log name when this method is called
    //    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
    //        super.onAttachedToHierarchy(preferenceManager)
    //        logger.logImpression(logName)
    //    }

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
        return preference == this
    }

    override fun hasSameContents(preference: Preference): Boolean {
        return preference is HealthPreference &&
            this.title == preference.title &&
            this.summary == preference.summary &&
            this.icon == preference.icon &&
            this.isEnabled == preference.isEnabled
    }
}
