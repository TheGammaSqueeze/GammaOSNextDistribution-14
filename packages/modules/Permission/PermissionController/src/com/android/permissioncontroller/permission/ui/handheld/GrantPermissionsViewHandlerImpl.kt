/*
 * Copyright (C) 2015 The Android Open Source Project
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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui.handheld

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.SparseIntArray
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.RawRes
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALL_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_SELECTED_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.COARSE_RADIO_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_BOTH_LOCATIONS
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_COARSE_LOCATION_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_FINE_LOCATION_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DONT_ALLOW_MORE_SELECTED_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.FINE_RADIO_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.LOCATION_ACCURACY_LAYOUT
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_LOCATION_DIALOG
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_MORE
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_USER_SELECTED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.ResultListener

class GrantPermissionsViewHandlerImpl(
    private val mActivity: Activity,
    private val resultListener: ResultListener
) : GrantPermissionsViewHandler, OnClickListener {

    private val LOCATION_ACCURACY_DIALOGS =
        listOf(
            DIALOG_WITH_BOTH_LOCATIONS,
            DIALOG_WITH_FINE_LOCATION_ONLY,
            DIALOG_WITH_COARSE_LOCATION_ONLY
        )
    private val LOCATION_ACCURACY_IMAGE_DIAMETER =
        mActivity.resources.getDimension(R.dimen.location_accuracy_image_size)

    // Configuration of the current dialog
    private var groupName: String? = null
    private var groupCount: Int = 0
    private var groupIndex: Int = 0
    private var groupIcon: Icon? = null
    private var groupMessage: CharSequence? = null
    private var detailMessage: CharSequence? = null
    private var permissionRationaleMessage: CharSequence? = null
    private val buttonVisibilities = BooleanArray(NEXT_BUTTON) { false }
    private val locationVisibilities = BooleanArray(NEXT_LOCATION_DIALOG) { false }
    private var selectedPrecision: Int = 0
    private var isLocationPermissionDialogActionClicked: Boolean = false
    private var coarseRadioButton: RadioButton? = null
    private var fineRadioButton: RadioButton? = null
    private var coarseOffDrawable: LottieDrawable? = null
    private var coarseOnDrawable: LottieDrawable? = null
    private var fineOffDrawable: LottieDrawable? = null
    private var fineOnDrawable: LottieDrawable? = null

    // Views
    private var iconView: ImageView? = null
    private var messageView: TextView? = null
    private var detailMessageView: TextView? = null
    private var permissionRationaleView: View? = null
    private var permissionRationaleMessageView: TextView? = null
    private var buttons: Array<Button?> = arrayOfNulls(NEXT_BUTTON)
    private var locationViews: Array<View?> = arrayOfNulls(NEXT_LOCATION_DIALOG)
    private var rootView: ViewGroup? = null

    override fun saveInstanceState(arguments: Bundle) {
        arguments.putString(ARG_GROUP_NAME, groupName)
        arguments.putInt(ARG_GROUP_COUNT, groupCount)
        arguments.putInt(ARG_GROUP_INDEX, groupIndex)
        arguments.putParcelable(ARG_GROUP_ICON, groupIcon)
        arguments.putCharSequence(ARG_GROUP_MESSAGE, groupMessage)
        arguments.putCharSequence(ARG_GROUP_DETAIL_MESSAGE, detailMessage)
        arguments.putCharSequence(
            ARG_GROUP_PERMISSION_RATIONALE_MESSAGE,
            permissionRationaleMessage
        )
        arguments.putBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES, buttonVisibilities)
        arguments.putBooleanArray(ARG_DIALOG_LOCATION_VISIBILITIES, locationVisibilities)
        arguments.putInt(ARG_DIALOG_SELECTED_PRECISION, selectedPrecision)
    }

    override fun loadInstanceState(savedInstanceState: Bundle) {
        groupName = savedInstanceState.getString(ARG_GROUP_NAME)
        groupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE)
        groupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON)
        groupCount = savedInstanceState.getInt(ARG_GROUP_COUNT)
        groupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX)
        detailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE)
        permissionRationaleMessage =
            savedInstanceState.getCharSequence(ARG_GROUP_PERMISSION_RATIONALE_MESSAGE)
        setButtonVisibilities(savedInstanceState.getBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES))
        setLocationVisibilities(
            savedInstanceState.getBooleanArray(ARG_DIALOG_LOCATION_VISIBILITIES)
        )
        selectedPrecision = savedInstanceState.getInt(ARG_DIALOG_SELECTED_PRECISION)

        updateAll()
    }

    override fun updateUi(
        groupName: String?,
        groupCount: Int,
        groupIndex: Int,
        icon: Icon?,
        message: CharSequence?,
        detailMessage: CharSequence?,
        permissionRationaleMessage: CharSequence?,
        buttonVisibilities: BooleanArray?,
        locationVisibilities: BooleanArray?
    ) {
        this.groupName = groupName
        this.groupCount = groupCount
        this.groupIndex = groupIndex
        groupIcon = icon
        groupMessage = message
        this.detailMessage = detailMessage
        this.permissionRationaleMessage = permissionRationaleMessage
        setButtonVisibilities(buttonVisibilities)
        setLocationVisibilities(locationVisibilities)

        // If this is a second (or later) permission and the views exist, then animate.
        if (iconView != null) {
            updateAll()
        }
    }

    private fun updateAll() {
        updateDescription()
        updateDetailDescription()
        updatePermissionRationale()
        updateButtons()
        updateLocationVisibilities()

        // Animate change in size
        // Grow or shrink the content container to size of new content
        val growShrinkToNewContentSize = ChangeBounds()
        growShrinkToNewContentSize.duration = ANIMATION_DURATION_MILLIS
        growShrinkToNewContentSize.interpolator =
            AnimationUtils.loadInterpolator(mActivity, android.R.interpolator.fast_out_slow_in)
        TransitionManager.beginDelayedTransition(rootView, growShrinkToNewContentSize)
    }

    override fun createView(): View {
        val useMaterial3PermissionGrantDialog =
            mActivity.resources.getBoolean(R.bool.config_useMaterial3PermissionGrantDialog)
        val rootView =
            if (useMaterial3PermissionGrantDialog || SdkLevel.isAtLeastT()) {
                LayoutInflater.from(mActivity).inflate(R.layout.grant_permissions_material3, null)
                    as ViewGroup
            } else {
                LayoutInflater.from(mActivity).inflate(R.layout.grant_permissions, null)
                    as ViewGroup
            }
        this.rootView = rootView

        // Uses the vertical gravity of the PermissionGrantSingleton style to position the window
        val gravity = rootView.requireViewById<LinearLayout>(R.id.grant_singleton).gravity
        val verticalGravity = Gravity.VERTICAL_GRAVITY_MASK and gravity
        mActivity.window.setGravity(Gravity.CENTER_HORIZONTAL or verticalGravity)

        // Cancel dialog
        rootView.findViewById<View>(R.id.grant_singleton)!!.setOnClickListener(this)
        // Swallow click event
        rootView.findViewById<View>(R.id.grant_dialog)!!.setOnClickListener(this)

        messageView = rootView.findViewById(R.id.permission_message)
        detailMessageView = rootView.findViewById(R.id.detail_message)
        detailMessageView!!.movementMethod = LinkMovementMethod.getInstance()
        iconView = rootView.findViewById(R.id.permission_icon)

        permissionRationaleView = rootView.findViewById(R.id.permission_rationale_container)
        permissionRationaleMessageView = rootView.findViewById(R.id.permission_rationale_message)
        permissionRationaleView!!.setOnClickListener(this)

        val buttons = arrayOfNulls<Button>(NEXT_BUTTON)
        val numButtons = BUTTON_RES_ID_TO_NUM.size()
        for (i in 0 until numButtons) {
            val button = rootView.findViewById<Button>(BUTTON_RES_ID_TO_NUM.keyAt(i))
            button!!.setOnClickListener(this)
            buttons[BUTTON_RES_ID_TO_NUM.valueAt(i)] = button
        }
        this.buttons = buttons

        val locationViews = arrayOfNulls<View>(NEXT_LOCATION_DIALOG)
        for (i in 0 until LOCATION_RES_ID_TO_NUM.size()) {
            val locationView = rootView.findViewById<View>(LOCATION_RES_ID_TO_NUM.keyAt(i))
            locationViews[LOCATION_RES_ID_TO_NUM.valueAt(i)] = locationView
        }

        initializeAnimatedImages()

        // Set location accuracy radio buttons' click listeners
        coarseRadioButton = locationViews[COARSE_RADIO_BUTTON] as RadioButton
        fineRadioButton = locationViews[FINE_RADIO_BUTTON] as RadioButton
        coarseRadioButton!!.setOnClickListener(this)
        fineRadioButton!!.setOnClickListener(this)
        this.locationViews = locationViews

        if (groupName != null) {
            updateAll()
        }

        return rootView
    }

    private fun getLottieDrawable(@RawRes rawResId: Int): LottieDrawable {
        val composition = LottieCompositionFactory.fromRawResSync(mActivity, rawResId).value!!
        val scale = LOCATION_ACCURACY_IMAGE_DIAMETER / composition.bounds.width()
        val drawable =
            object : LottieDrawable() {
                override fun getIntrinsicHeight(): Int {
                    return (super.getIntrinsicHeight() * scale).toInt()
                }
                override fun getIntrinsicWidth(): Int {
                    return (super.getIntrinsicWidth() * scale).toInt()
                }
            }
        drawable.composition = composition
        return drawable
    }

    private fun initializeAnimatedImages() {
        coarseOffDrawable = getLottieDrawable(R.raw.coarse_loc_off)
        coarseOnDrawable = getLottieDrawable(R.raw.coarse_loc_on)
        fineOffDrawable = getLottieDrawable(R.raw.fine_loc_off)
        fineOnDrawable = getLottieDrawable(R.raw.fine_loc_on)
    }

    override fun updateWindowAttributes(outLayoutParams: LayoutParams) {
        // No-op
    }

    private fun setButtonVisibilities(visibilities: BooleanArray?) {
        for (i in buttonVisibilities.indices) {
            buttonVisibilities[i] =
                if (visibilities != null && i < visibilities.size) {
                    visibilities[i]
                } else {
                    false
                }
        }
    }

    private fun setLocationVisibilities(visibilities: BooleanArray?) {
        for (i in locationVisibilities.indices) {
            locationVisibilities[i] =
                if (visibilities != null && i < visibilities.size) {
                    visibilities[i]
                } else {
                    false
                }
        }
    }

    private fun updateDescription() {
        if (groupIcon != null) {
            iconView!!.setImageDrawable(groupIcon!!.loadDrawable(mActivity))
        }
        messageView!!.text = groupMessage
    }

    private fun updateDetailDescription() {
        if (detailMessage == null) {
            detailMessageView!!.visibility = View.GONE
        } else {
            detailMessageView!!.text = detailMessage
            detailMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updatePermissionRationale() {
        val message = permissionRationaleMessage
        if (message == null || message.isEmpty()) {
            permissionRationaleView!!.visibility = View.GONE
        } else {
            permissionRationaleMessageView!!.text = message
            permissionRationaleView!!.visibility = View.VISIBLE
        }
    }

    private fun updateButtons() {
        for (i in 0 until BUTTON_RES_ID_TO_NUM.size()) {
            val pos = BUTTON_RES_ID_TO_NUM.valueAt(i)
            buttons[pos]?.visibility =
                if (buttonVisibilities[pos]) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            if (pos == ALLOW_FOREGROUND_BUTTON && buttonVisibilities[pos]) {
                if (
                    locationVisibilities[LOCATION_ACCURACY_LAYOUT] &&
                        locationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]
                ) {
                    buttons[pos]?.text =
                        mActivity.resources.getString(
                            R.string.grant_dialog_button_change_to_precise_location
                        )
                } else {
                    buttons[pos]?.text =
                        mActivity.resources.getString(R.string.grant_dialog_button_allow_foreground)
                }
            }
            if ((pos == DENY_BUTTON || pos == DENY_AND_DONT_ASK_AGAIN_BUTTON)) {
                if (
                    locationVisibilities[LOCATION_ACCURACY_LAYOUT] &&
                        locationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]
                ) {
                    buttons[pos]?.text =
                        mActivity.resources.getString(
                            R.string.grant_dialog_button_keey_approximate_location
                        )
                } else {
                    buttons[pos]?.text =
                        mActivity.resources.getString(R.string.grant_dialog_button_deny)
                }
            }
            buttons[pos]?.requestLayout()
        }
    }

    private fun updateLocationVisibilities() {
        if (locationVisibilities[LOCATION_ACCURACY_LAYOUT]) {
            if (isLocationPermissionDialogActionClicked) {
                return
            }
            locationViews[LOCATION_ACCURACY_LAYOUT]?.visibility = View.VISIBLE
            for (i in LOCATION_ACCURACY_DIALOGS) {
                locationViews[i]?.visibility =
                    if (locationVisibilities[i]) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
            if (locationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
                coarseRadioButton?.visibility = View.VISIBLE
                fineRadioButton?.visibility = View.VISIBLE
                if (selectedPrecision == 0) {
                    fineRadioButton?.isChecked = locationVisibilities[FINE_RADIO_BUTTON]
                    coarseRadioButton?.isChecked = locationVisibilities[COARSE_RADIO_BUTTON]
                } else {
                    fineRadioButton?.isChecked = selectedPrecision == FINE_RADIO_BUTTON
                    coarseRadioButton?.isChecked = selectedPrecision == COARSE_RADIO_BUTTON
                }
                if (coarseRadioButton?.isChecked == true) {
                    runLocationAccuracyAnimation(false)
                } else if (fineRadioButton?.isChecked == true) {
                    runLocationAccuracyAnimation(true)
                }
            } else if (locationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
                (locationViews[DIALOG_WITH_COARSE_LOCATION_ONLY] as ImageView).setImageDrawable(
                    coarseOnDrawable
                )
                coarseOnDrawable?.start()
            } else if (locationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]) {
                (locationViews[DIALOG_WITH_FINE_LOCATION_ONLY] as ImageView).setImageDrawable(
                    fineOnDrawable
                )
                fineOnDrawable?.start()
            }
        } else {
            locationViews[LOCATION_ACCURACY_LAYOUT]?.visibility = View.GONE
            for (i in LOCATION_ACCURACY_DIALOGS) {
                locationVisibilities[i] = false
                locationViews[i]?.visibility = View.GONE
            }
        }
    }

    private fun runLocationAccuracyAnimation(isFineSelected: Boolean) {
        if (isFineSelected) {
            coarseOnDrawable?.stop()
            fineOffDrawable?.stop()
            coarseRadioButton?.setCompoundDrawablesWithIntrinsicBounds(
                null,
                coarseOffDrawable,
                null,
                null
            )
            fineRadioButton?.setCompoundDrawablesWithIntrinsicBounds(
                null,
                fineOnDrawable,
                null,
                null
            )
            coarseOffDrawable?.start()
            fineOnDrawable?.start()
            fineRadioButton?.setTypeface(null, Typeface.BOLD)
            coarseRadioButton?.setTypeface(null, Typeface.NORMAL)
        } else {
            coarseOffDrawable?.stop()
            fineOnDrawable?.stop()
            coarseRadioButton?.setCompoundDrawablesWithIntrinsicBounds(
                null,
                coarseOnDrawable,
                null,
                null
            )
            fineRadioButton?.setCompoundDrawablesWithIntrinsicBounds(
                null,
                fineOffDrawable,
                null,
                null
            )
            fineOffDrawable?.start()
            coarseOnDrawable?.start()
            coarseRadioButton?.setTypeface(null, Typeface.BOLD)
            fineRadioButton?.setTypeface(null, Typeface.NORMAL)
        }
    }

    override fun onClick(view: View) {
        val id = view.id

        if (id == R.id.permission_rationale_container) {
            resultListener.onPermissionRationaleClicked(groupName)
            return
        }

        if (id == R.id.permission_location_accuracy_radio_fine) {
            if (selectedPrecision != FINE_RADIO_BUTTON) {
                (locationViews[FINE_RADIO_BUTTON] as RadioButton).isChecked = true
                selectedPrecision = FINE_RADIO_BUTTON
                runLocationAccuracyAnimation(true)
            }
            return
        }

        if (id == R.id.permission_location_accuracy_radio_coarse) {
            if (selectedPrecision != COARSE_RADIO_BUTTON) {
                (locationViews[COARSE_RADIO_BUTTON] as RadioButton).isChecked = true
                selectedPrecision = COARSE_RADIO_BUTTON
                runLocationAccuracyAnimation(false)
            }
            return
        }

        if (locationVisibilities[LOCATION_ACCURACY_LAYOUT]) {
            isLocationPermissionDialogActionClicked = true
        }

        if (id == R.id.grant_singleton) {
            resultListener.onPermissionGrantResult(groupName, CANCELED)
            return
        }

        var affectedForegroundPermissions: List<String>? = null
        if (locationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
            when ((locationViews[DIALOG_WITH_BOTH_LOCATIONS] as RadioGroup).checkedRadioButtonId) {
                R.id.permission_location_accuracy_radio_coarse ->
                    affectedForegroundPermissions = listOf(ACCESS_COARSE_LOCATION)
                R.id.permission_location_accuracy_radio_fine ->
                    affectedForegroundPermissions =
                        listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
            }
        } else if (locationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]) {
            affectedForegroundPermissions = listOf(ACCESS_FINE_LOCATION)
        } else if (locationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
            affectedForegroundPermissions = listOf(ACCESS_COARSE_LOCATION)
        }

        when (BUTTON_RES_ID_TO_NUM.get(id, -1)) {
            ALLOW_ALL_BUTTON,
            ALLOW_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    GRANTED_ALWAYS
                )
            }
            ALLOW_FOREGROUND_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    GRANTED_FOREGROUND_ONLY
                )
            }
            ALLOW_ALWAYS_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    GRANTED_ALWAYS
                )
            }
            ALLOW_ONE_TIME_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    GRANTED_ONE_TIME
                )
            }
            ALLOW_SELECTED_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    GRANTED_USER_SELECTED
                )
            }
            DONT_ALLOW_MORE_SELECTED_BUTTON -> {
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    DENIED_MORE
                )
            }
            DENY_BUTTON,
            NO_UPGRADE_BUTTON,
            NO_UPGRADE_OT_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    DENIED
                )
            }
            DENY_AND_DONT_ASK_AGAIN_BUTTON,
            NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON,
            NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON -> {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                    null
                )
                resultListener.onPermissionGrantResult(
                    groupName,
                    affectedForegroundPermissions,
                    DENIED_DO_NOT_ASK_AGAIN
                )
            }
        }
    }

    override fun onBackPressed() {
        onCancelled()
    }

    override fun onCancelled() {
        resultListener.onPermissionGrantResult(groupName, CANCELED)
    }

    override fun setResultListener(listener: ResultListener): GrantPermissionsViewHandler {
        throw UnsupportedOperationException()
    }

    companion object {
        private val TAG = GrantPermissionsViewHandlerImpl::class.java.simpleName

        const val ARG_GROUP_NAME = "ARG_GROUP_NAME"
        const val ARG_GROUP_COUNT = "ARG_GROUP_COUNT"
        const val ARG_GROUP_INDEX = "ARG_GROUP_INDEX"
        const val ARG_GROUP_ICON = "ARG_GROUP_ICON"
        const val ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE"
        private const val ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE"
        private const val ARG_GROUP_PERMISSION_RATIONALE_MESSAGE =
            "ARG_GROUP_PERMISSION_RATIONALE_MESSAGE"
        private const val ARG_DIALOG_BUTTON_VISIBILITIES = "ARG_DIALOG_BUTTON_VISIBILITIES"
        private const val ARG_DIALOG_LOCATION_VISIBILITIES = "ARG_DIALOG_LOCATION_VISIBILITIES"
        private const val ARG_DIALOG_SELECTED_PRECISION = "ARG_DIALOG_SELECTED_PRECISION"

        // Animation parameters.
        private const val SWITCH_TIME_MILLIS: Long = 75
        private const val ANIMATION_DURATION_MILLIS: Long = 200

        private val BUTTON_RES_ID_TO_NUM = SparseIntArray()
        private val LOCATION_RES_ID_TO_NUM = SparseIntArray()

        init {
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_button, ALLOW_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_allow_foreground_only_button,
                ALLOW_FOREGROUND_BUTTON
            )
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_button, DENY_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_deny_and_dont_ask_again_button,
                DENY_AND_DONT_ASK_AGAIN_BUTTON
            )
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_one_time_button, ALLOW_ONE_TIME_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_button, NO_UPGRADE_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_no_upgrade_and_dont_ask_again_button,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON
            )
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_no_upgrade_one_time_button,
                NO_UPGRADE_OT_BUTTON
            )
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_no_upgrade_one_time_and_dont_ask_again_button,
                NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON
            )
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_all_button, ALLOW_ALL_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_selected_button, ALLOW_SELECTED_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(
                R.id.permission_dont_allow_more_selected_button,
                DONT_ALLOW_MORE_SELECTED_BUTTON
            )

            LOCATION_RES_ID_TO_NUM.put(R.id.permission_location_accuracy, LOCATION_ACCURACY_LAYOUT)
            LOCATION_RES_ID_TO_NUM.put(
                R.id.permission_location_accuracy_radio_fine,
                FINE_RADIO_BUTTON
            )
            LOCATION_RES_ID_TO_NUM.put(
                R.id.permission_location_accuracy_radio_coarse,
                COARSE_RADIO_BUTTON
            )
            LOCATION_RES_ID_TO_NUM.put(
                R.id.permission_location_accuracy_radio_group,
                DIALOG_WITH_BOTH_LOCATIONS
            )
            LOCATION_RES_ID_TO_NUM.put(
                R.id.permission_location_accuracy_fine_only,
                DIALOG_WITH_FINE_LOCATION_ONLY
            )
            LOCATION_RES_ID_TO_NUM.put(
                R.id.permission_location_accuracy_coarse_only,
                DIALOG_WITH_COARSE_LOCATION_ONLY
            )
        }
    }
}
