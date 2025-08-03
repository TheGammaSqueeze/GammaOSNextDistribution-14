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
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** Preference for displaying a banner with optional action and dismiss buttons. */
class BannerPreference constructor(context: Context, private val logName: ElementName) :
    Preference(context) {

    private var logger: HealthConnectLogger

    private lateinit var bannerIcon: ImageView
    private lateinit var bannerTitle: TextView
    private lateinit var bannerMessage: TextView
    private lateinit var bannerPrimaryButton: Button
    private lateinit var bannerSecondaryButton: Button

    private var dismissButton: ImageView? = null

    private var buttonPrimaryText: String? = null
    private var buttonPrimaryAction: OnClickListener? = null
    private var buttonPrimaryVisibility = View.VISIBLE
    private var buttonPrimaryLogName: ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    private var buttonSecondaryText: String? = null
    private var buttonSecondaryAction: OnClickListener? = null
    private var buttonSecondaryVisibility = View.VISIBLE
    private var buttonSecondaryLogName: ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    private var isDismissable = false
    private var dismissAction: OnClickListener? = null
    private var dismissActionLogName: ElementName = ErrorPageElement.UNKNOWN_ELEMENT

    init {
        layoutResource = R.layout.widget_banner_preference
        isSelectable = false

        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    fun setPrimaryButton(buttonText: String, logName: ElementName) {
        this.buttonPrimaryText = buttonText
        this.buttonPrimaryLogName = logName
    }

    fun setPrimaryButtonOnClickListener(onClickListener: OnClickListener?) {
        logger.logInteraction(buttonPrimaryLogName)
        this.buttonPrimaryAction = onClickListener
    }

    fun setPrimaryButtonVisibility(visibility: Int) {
        this.buttonPrimaryVisibility = visibility
    }

    fun setSecondaryButton(buttonText: String, logName: ElementName) {
        this.buttonSecondaryText = buttonText
        this.buttonSecondaryLogName = logName
    }

    fun setSecondaryButtonOnClickListener(onClickListener: OnClickListener?) {
        logger.logInteraction(buttonSecondaryLogName)
        this.buttonSecondaryAction = onClickListener
    }

    fun setSecondaryButtonVisibility(visibility: Int) {
        this.buttonSecondaryVisibility = visibility
    }

    fun setIsDismissable(isDismissable: Boolean) {
        this.isDismissable = isDismissable
    }

    fun setDismissAction(logName: ElementName, onClickListener: OnClickListener?) {
        dismissActionLogName = logName
        logger.logInteraction(dismissActionLogName)
        this.dismissAction = onClickListener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        bannerIcon = holder.findViewById(R.id.banner_icon) as ImageView
        bannerTitle = holder.findViewById(R.id.banner_title) as TextView
        bannerMessage = holder.findViewById(R.id.banner_message) as TextView
        bannerPrimaryButton = holder.findViewById(R.id.banner_primary_button) as Button
        bannerSecondaryButton = holder.findViewById(R.id.banner_secondary_button) as Button

        bannerTitle.text = title
        bannerMessage.text = summary
        bannerIcon.background = icon

        // set button text and visibility
        buttonPrimaryText?.let {
            bannerPrimaryButton.text = it
            bannerPrimaryButton.visibility = View.VISIBLE
            bannerPrimaryButton.setOnClickListener(buttonPrimaryAction)
        }

        buttonSecondaryText?.let {
            bannerSecondaryButton.text = it
            bannerSecondaryButton.visibility = View.VISIBLE
            bannerSecondaryButton.setOnClickListener(buttonSecondaryAction)
        }

        bannerPrimaryButton.visibility = buttonPrimaryVisibility
        bannerSecondaryButton.visibility = buttonSecondaryVisibility

        if (buttonPrimaryVisibility == View.VISIBLE) {
            logger.logImpression(buttonPrimaryLogName)
        }

        if (buttonSecondaryVisibility == View.VISIBLE) {
            logger.logImpression(buttonSecondaryLogName)
        }

        if (isDismissable) {
            dismissButton = holder.findViewById(R.id.dismiss_button) as ImageView
            dismissButton?.visibility = View.VISIBLE
            logger.logImpression(dismissActionLogName)
            dismissButton?.setOnClickListener(dismissAction)
        }
    }

    override fun onAttached() {
        super.onAttached()
        logger.logImpression(logName)
    }
}
