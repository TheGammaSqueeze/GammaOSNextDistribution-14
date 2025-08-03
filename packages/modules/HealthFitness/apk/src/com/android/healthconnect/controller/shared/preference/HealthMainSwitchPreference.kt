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
import android.widget.CompoundButton.OnCheckedChangeListener
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.settingslib.widget.MainSwitchPreference
import dagger.hilt.android.EntryPointAccessors

/** A [MainSwitchPreference] that allows logging. */
class HealthMainSwitchPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : MainSwitchPreference(context, attrs) {

    private var logger: HealthConnectLogger
    var logNameActive: ElementName = ErrorPageElement.UNKNOWN_ELEMENT
    var logNameInactive: ElementName = ErrorPageElement.UNKNOWN_ELEMENT
    private var loggingSwitchListener: OnCheckedChangeListener

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
        loggingSwitchListener = OnCheckedChangeListener { buttonView, newValue ->
            if (buttonView.isPressed) {
                if (newValue) {
                    logger.logInteraction(
                        logNameInactive,
                        com.android.healthconnect.controller.utils.logging.UIAction
                            .ACTION_TOGGLE_ON)
                } else {
                    logger.logInteraction(
                        logNameActive,
                        com.android.healthconnect.controller.utils.logging.UIAction
                            .ACTION_TOGGLE_OFF)
                }
            }
        }
        this.addOnSwitchChangeListener(loggingSwitchListener)
    }

    override fun onAttached() {
        super.onAttached()
        if (isChecked) {
            logger.logImpression(logNameActive)
        } else {
            logger.logImpression(logNameInactive)
        }
    }
}
