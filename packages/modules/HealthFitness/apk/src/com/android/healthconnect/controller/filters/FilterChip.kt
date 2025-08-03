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
package com.android.healthconnect.controller.filters

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatRadioButton
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.healthconnect.controller.utils.logging.PermissionTypesElement
import dagger.hilt.android.EntryPointAccessors

/**
 * The FilterChip is a stylised RadioButton which helps the user filter Health Connect data by the
 * contributing app.
 *
 * Each chip belongs to a RadioGroup and behaves like a RadioButton. There are two states for each
 * FilterChip:
 * 1. Selected (checked)
 * 2. Unselected (unchecked)
 *
 * An separate icon can be set for each of the two states. If the `selected` icon is not specified,
 * a default check icon is used. If the `unselected` icon is not specified, no icon will show when
 * the chip is unchecked and the left padding is adjusted accordingly. By default, the width changes
 * of a FilterChip are animated.
 */
class FilterChip
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.chipStyle,
) : AppCompatRadioButton(context, attrs, defStyleAttr) {

    private var logger: HealthConnectLogger

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    private var selectedIcon: Drawable? = null
    var unSelectedIcon: Drawable? = null

    fun setSelectedIcon(res: Drawable?) {
        selectedIcon = res
        buttonDrawable = makeSelector()
    }

    fun setUnselectedIcon(res: Drawable?) {
        unSelectedIcon = res
        buttonDrawable = makeSelector()
    }

    private val spacingXSmallPx = (context.resources.getDimension(R.dimen.spacing_xsmall)).toInt()
    private val spacingSmallPx = (context.resources.getDimension(R.dimen.spacing_small)).toInt()
    private val spacingNormalPx = (context.resources.getDimension(R.dimen.spacing_normal)).toInt()

    init {

        val params =
            RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)

        val px = (context.resources.getDimension(R.dimen.spacing_small)).toInt()
        params.setMargins(0, 0, px, 0)
        this.layoutParams = params

        if (unSelectedIcon == null) {
            // Padding needs to be changed programmatically when no button icon is used
            setChipPadding(this.isChecked)
        }

        buttonDrawable = makeSelector()
    }

    private fun setChipPadding(isChecked: Boolean) {
        // Padding needs to be changed programmatically when no button icon is used
        if (isChecked) {
            this.setPadding(spacingSmallPx, spacingXSmallPx, spacingNormalPx, spacingXSmallPx)
        } else {
            this.setPadding(spacingNormalPx, spacingXSmallPx, spacingNormalPx, spacingXSmallPx)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        logger.logImpression(PermissionTypesElement.APP_FILTER_BUTTON)

        this.setOnCheckedChangeListener { buttonView, isChecked ->
            if (unSelectedIcon == null) {
                setChipPadding(isChecked)
                animateLayoutChanges(buttonView)
            }
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        val loggingClickListener = OnClickListener {
            logger.logInteraction(PermissionTypesElement.APP_FILTER_BUTTON)
            l?.onClick(it)
        }
        super.setOnClickListener(loggingClickListener)
    }

    private fun animateLayoutChanges(view: View) {
        val oldWidth = view.width
        view.measure(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT)
        val targetWidth = view.measuredWidth

        val animator = ValueAnimator.ofInt(oldWidth, targetWidth)
        animator.duration = 200
        animator.addUpdateListener {
            view.layoutParams.width = it.animatedValue as Int
            view.requestLayout()
        }
        animator.start()
    }

    private fun makeSelector(): StateListDrawable {
        val res = StateListDrawable()
        val checkedLayers =
            AppCompatResources.getDrawable(context, R.drawable.filter_chip_button_icon_layer)
                as LayerDrawable
        val checkedDrawable =
            selectedIcon ?: AttributeResolver.getDrawable(context, R.attr.checkIcon)
        checkedLayers.setDrawableByLayerId(R.id.icon_layer, checkedDrawable)

        res.addState(intArrayOf(android.R.attr.state_checked), checkedLayers)

        if (unSelectedIcon == null) {
            res.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        } else {
            val uncheckedLayers =
                AppCompatResources.getDrawable(context, R.drawable.filter_chip_button_icon_layer)
                    as LayerDrawable
            uncheckedLayers.setDrawableByLayerId(R.id.icon_layer, unSelectedIcon)

            res.addState(intArrayOf(), uncheckedLayers)
        }

        return res
    }
}
