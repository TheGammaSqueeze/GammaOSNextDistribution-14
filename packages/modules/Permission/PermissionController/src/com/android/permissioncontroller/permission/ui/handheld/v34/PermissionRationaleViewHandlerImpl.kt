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

package com.android.permissioncontroller.permission.ui.handheld.v34

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.text.method.LinkMovementMethodCompat
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler.Result.Companion.CANCELLED

/**
 * Handheld implementation of [PermissionRationaleViewHandler]. Used for managing the presentation
 * and user interaction of the "permission rationale" user interface.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PermissionRationaleViewHandlerImpl(
    private val mActivity: Activity,
    private val resultListener: PermissionRationaleViewHandler.ResultListener,
    private val shouldShowSettingsSection: Boolean
) : PermissionRationaleViewHandler, OnClickListener {

    private var groupName: String? = null
    private var title: CharSequence? = null
    private var dataSharingSourceMessage: CharSequence? = null
    private var purposeTitle: CharSequence? = null
    private var purposeMessage: CharSequence? = null
    private var learnMoreMessage: CharSequence? = null
    private var settingsMessage: CharSequence? = null

    private var rootView: ViewGroup? = null
    private var titleView: TextView? = null
    private var dataSharingSourceMessageView: TextView? = null
    private var purposeTitleView: TextView? = null
    private var purposeMessageView: TextView? = null
    private var learnMoreMessageView: TextView? = null
    private var settingsMessageView: TextView? = null
    private var backButton: Button? = null

    override fun saveInstanceState(outState: Bundle) {
        outState.putString(ARG_GROUP_NAME, groupName)
        outState.putCharSequence(ARG_TITLE, title)
        outState.putCharSequence(ARG_DATA_SHARING_SOURCE_MESSAGE, dataSharingSourceMessage)
        outState.putCharSequence(ARG_PURPOSE_TITLE, purposeTitle)
        outState.putCharSequence(ARG_PURPOSE_MESSAGE, purposeMessage)
        outState.putCharSequence(ARG_LEARN_MORE_MESSAGE, learnMoreMessage)
        outState.putCharSequence(ARG_SETTINGS_MESSAGE, settingsMessage)
    }

    override fun loadInstanceState(savedInstanceState: Bundle) {
        groupName = savedInstanceState.getString(ARG_GROUP_NAME)
        title = savedInstanceState.getCharSequence(ARG_TITLE)
        dataSharingSourceMessage =
            savedInstanceState.getCharSequence(ARG_DATA_SHARING_SOURCE_MESSAGE)
        purposeTitle = savedInstanceState.getCharSequence(ARG_PURPOSE_TITLE)
        purposeMessage = savedInstanceState.getCharSequence(ARG_PURPOSE_MESSAGE)
        learnMoreMessage = savedInstanceState.getCharSequence(ARG_LEARN_MORE_MESSAGE)
        settingsMessage = savedInstanceState.getCharSequence(ARG_SETTINGS_MESSAGE)
    }

    override fun updateUi(
        groupName: String,
        title: CharSequence,
        dataSharingSourceMessage: CharSequence,
        purposeTitle: CharSequence,
        purposeMessage: CharSequence,
        learnMoreMessage: CharSequence,
        settingsMessage: CharSequence
    ) {
        this.groupName = groupName
        this.title = title
        this.dataSharingSourceMessage = dataSharingSourceMessage
        this.purposeTitle = purposeTitle
        this.purposeMessage = purposeMessage
        this.learnMoreMessage = learnMoreMessage
        this.settingsMessage = settingsMessage

        // If view already created, update all children
        if (rootView != null) {
            updateAll()
        }
    }

    private fun updateAll() {
        updateTitle()
        updateDataSharingSourceMessage()
        updatePurposeTitle()
        updatePurposeMessage()
        updateLearnMoreMessage()
        updateSettingsMessage()

        // Animate change in size
        // Grow or shrink the content container to size of new content
        val growShrinkToNewContentSize = ChangeBounds()
        growShrinkToNewContentSize.duration = ANIMATION_DURATION_MILLIS
        growShrinkToNewContentSize.interpolator =
            AnimationUtils.loadInterpolator(mActivity, android.R.interpolator.fast_out_slow_in)
        TransitionManager.beginDelayedTransition(rootView, growShrinkToNewContentSize)
    }

    override fun createView(): View {
        val rootView =
            LayoutInflater.from(mActivity).inflate(R.layout.permission_rationale, null) as ViewGroup

        // Uses the vertical gravity of the PermissionGrantSingleton style to position the window
        val gravity =
            rootView.requireViewById<LinearLayout>(R.id.permission_rationale_singleton).gravity
        val verticalGravity = Gravity.VERTICAL_GRAVITY_MASK and gravity
        mActivity.window.setGravity(Gravity.CENTER_HORIZONTAL or verticalGravity)

        // Cancel dialog
        rootView.findViewById<View>(R.id.permission_rationale_singleton)!!.setOnClickListener(this)
        // Swallow click event
        rootView.findViewById<View>(R.id.permission_rationale_dialog)!!.setOnClickListener(this)

        titleView = rootView.findViewById(R.id.permission_rationale_title)

        dataSharingSourceMessageView = rootView.findViewById(R.id.data_sharing_source_message)
        dataSharingSourceMessageView!!.movementMethod = LinkMovementMethodCompat.getInstance()

        purposeTitleView = rootView.findViewById(R.id.purpose_title)
        purposeMessageView = rootView.findViewById(R.id.purpose_message)

        learnMoreMessageView = rootView.findViewById(R.id.learn_more_message)
        learnMoreMessageView!!.movementMethod = LinkMovementMethodCompat.getInstance()

        settingsMessageView = rootView.findViewById(R.id.settings_message)
        settingsMessageView!!.movementMethod = LinkMovementMethodCompat.getInstance()

        if (!shouldShowSettingsSection) {
            val settingsSectionView: ViewGroup? = rootView.findViewById(R.id.settings_section)
            settingsSectionView?.visibility = View.GONE
        }
        backButton =
            rootView.findViewById<Button>(R.id.back_button)!!.apply {
                setOnClickListener(this@PermissionRationaleViewHandlerImpl)

                // Load the text color from the activity theme rather than the Material Design theme
                val textColor =
                    getColorStateListForAttr(mActivity, android.R.attr.textColorPrimary)!!
                setTextColor(textColor)
            }

        this.rootView = rootView

        // If ui model present, update all children
        if (groupName != null) {
            updateAll()
        }

        return rootView
    }

    override fun onClick(view: View) {
        val id = view.id

        if (id == R.id.permission_rationale_singleton) {
            onCancelled()
            return
        }

        if (id == R.id.back_button) {
            onCancelled()
        }
    }

    override fun onBackPressed() {
        onCancelled()
    }

    override fun onCancelled() {
        resultListener.onPermissionRationaleResult(groupName, CANCELLED)
    }

    private fun updateTitle() {
        if (title == null) {
            titleView!!.visibility = View.GONE
        } else {
            titleView!!.text = title
            titleView!!.visibility = View.VISIBLE
        }
    }

    private fun updateDataSharingSourceMessage() {
        if (dataSharingSourceMessage == null) {
            dataSharingSourceMessageView!!.visibility = View.GONE
        } else {
            dataSharingSourceMessageView!!.text = dataSharingSourceMessage
            dataSharingSourceMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updatePurposeTitle() {
        if (purposeTitle == null) {
            purposeTitleView!!.visibility = View.GONE
        } else {
            purposeTitleView!!.text = purposeTitle
            purposeTitleView!!.visibility = View.VISIBLE
        }
    }

    private fun updatePurposeMessage() {
        if (purposeMessage == null) {
            purposeMessageView!!.visibility = View.GONE
        } else {
            purposeMessageView!!.text = purposeMessage
            purposeMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updateLearnMoreMessage() {
        if (learnMoreMessage == null) {
            learnMoreMessageView!!.visibility = View.GONE
        } else {
            learnMoreMessageView!!.text = learnMoreMessage
            learnMoreMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updateSettingsMessage() {
        if (settingsMessage == null) {
            settingsMessageView!!.visibility = View.GONE
        } else {
            settingsMessageView!!.text = settingsMessage
            settingsMessageView!!.visibility = View.VISIBLE
        }
    }

    companion object {
        private val TAG = PermissionRationaleViewHandlerImpl::class.java.simpleName

        const val ARG_GROUP_NAME = "ARG_GROUP_NAME"
        const val ARG_TITLE = "ARG_TITLE"
        const val ARG_DATA_SHARING_SOURCE_MESSAGE = "ARG_DATA_SHARING_SOURCE_MESSAGE"
        const val ARG_PURPOSE_TITLE = "ARG_PURPOSE_TITLE"
        const val ARG_PURPOSE_MESSAGE = "ARG_PURPOSE_MESSAGE"
        const val ARG_LEARN_MORE_MESSAGE = "ARG_LEARN_MORE_MESSAGE"
        const val ARG_SETTINGS_MESSAGE = "ARG_SETTINGS_MESSAGE"

        // Animation parameters.
        private const val ANIMATION_DURATION_MILLIS: Long = 200

        fun getColorStateListForAttr(context: Context, attr: Int): ColorStateList? {
            val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
            val colorStateList = typedArray.getColorStateList(0)
            typedArray.recycle()
            return colorStateList
        }
    }
}
