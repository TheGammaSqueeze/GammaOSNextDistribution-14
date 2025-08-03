/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wallpaper.widget.floatingsheetcontent

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import com.android.wallpaper.R
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.effects.EffectsController.EffectEnumInterface

/** A view for displaying wallpaper info. */
class WallpaperEffectsView2(context: Context?, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private var effectSwitchListener: EffectSwitchListener? = null
    private var downloadClickListener: EffectDownloadClickListener? = null
    private var effectSwitch: Switch? = null

    private var effectTextRes: EffectTextRes =
        EffectTextRes(
            effectTitle = "",
            effectFailedTitle = "",
            effectSubTitle = "",
            retryInstruction = "",
            noEffectInstruction = "",
        )

    @VisibleForTesting(otherwise = PRIVATE) var effectTitle: TextView? = null
    @VisibleForTesting(otherwise = PRIVATE) var description: TextView? = null
    @VisibleForTesting(otherwise = PRIVATE) var title: TextView? = null
    @VisibleForTesting(otherwise = PRIVATE) var myPhotosButton: Button? = null
    @VisibleForTesting(otherwise = PRIVATE) var continueButton: Button? = null
    @VisibleForTesting(otherwise = PRIVATE) var tryAgainLaterButton: Button? = null
    @VisibleForTesting(otherwise = PRIVATE) var switchLayout: View? = null
    @VisibleForTesting(otherwise = PRIVATE) var container: View? = null
    @VisibleForTesting(otherwise = PRIVATE) var downloadProgression: ProgressBar? = null
    @VisibleForTesting(otherwise = PRIVATE) var downloadButton: Button? = null
    @VisibleForTesting(otherwise = PRIVATE) var downloadButtonLayout: View? = null

    data class EffectTextRes(
        val effectTitle: String,
        val effectFailedTitle: String,
        val effectSubTitle: String,
        val retryInstruction: String,
        val noEffectInstruction: String,
    )

    enum class Status {
        IDLE,
        FAILED,
        SUCCESS,
        PROCESSING,
        SHOW_DOWNLOAD_BUTTON,
        DOWNLOADING,
    }

    /**
     * Adds an Effects switch listener.
     *
     * @param listener The effects switch listener.
     */
    fun addEffectSwitchListener(listener: EffectSwitchListener?) {
        effectSwitchListener = listener
    }

    /**
     * Sets an Effects download button listener.
     *
     * @param listener The effects download button listener.
     */
    fun setEffectDownloadClickListener(listener: EffectDownloadClickListener?) {
        downloadClickListener = listener
    }

    /**
     * Updates the effect switch status.
     *
     * @param checked The status of the switch.
     */
    private fun updateEffectSwitchStatus(effect: EffectEnumInterface, checked: Boolean) {
        effectSwitch?.isChecked = checked
        effectSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            switchChanged(effect, isChecked)
        }
    }

    /**
     * Sets click listener to myPhotos button.
     *
     * @param clickListener listener for myPhotos.
     */
    fun setMyPhotosClickListener(clickListener: OnClickListener?) {
        myPhotosButton?.setOnClickListener(clickListener)
    }

    /**
     * Sets click listener to 'try again' and 'continue' button.
     *
     * @param clickListener listener for myPhotos.
     */
    fun setCollapseFloatingSheetListener(clickListener: OnClickListener?) {
        tryAgainLaterButton?.setOnClickListener(clickListener)
        continueButton?.setOnClickListener(clickListener)
    }

    fun setEffectResources(effectTextRes: EffectTextRes) {
        this.effectTextRes = effectTextRes
        updateDefaultTextIfEmpty(effectTextRes)
    }

    private fun updateDefaultTextIfEmpty(effectTextRes: EffectTextRes) {
        tryAgainLaterButton?.updateTextIfEmpty(effectTextRes.noEffectInstruction)
        title?.updateTextIfEmpty(effectTextRes.effectTitle)
        description?.updateTextIfEmpty(effectTextRes.effectSubTitle)
    }

    private fun TextView.updateTextIfEmpty(newText: String) {
        this.text = this.text.ifEmpty { newText }
    }

    /**
     * Updates the wallpaper effect view by status.
     *
     * @param status Last status code of wallpaper effect.
     * @param resultCode the result code to handle different layouts.
     * @param errorMessage the description of the sheet.
     */
    fun updateEffectStatus(
        effect: EffectEnumInterface,
        status: Status,
        resultCode: Int?,
        errorMessage: String?
    ) {
        when (status) {
            Status.IDLE -> {
                showBasicLayout()
                updateEffectSwitchStatus(effect, /* checked = */ false)
                effectSwitch?.isEnabled = true
                switchLayout?.visibility = VISIBLE
                container?.visibility = VISIBLE
            }
            Status.SUCCESS -> {
                showBasicLayout()
                updateEffectSwitchStatus(effect, /* checked = */ true)
                Handler().postDelayed({ effectSwitch?.isEnabled = true }, 500)
                switchLayout?.visibility = VISIBLE
                container?.visibility = VISIBLE
            }
            Status.PROCESSING -> {
                showBasicLayout()
                updateEffectSwitchStatus(effect, /* checked = */ true)
                effectSwitch?.isEnabled = false
                switchLayout?.visibility = VISIBLE
                container?.visibility = VISIBLE
            }
            Status.FAILED -> showFailedLayout(errorMessage)
            Status.SHOW_DOWNLOAD_BUTTON -> {
                description?.text = effectTextRes.effectSubTitle
                switchLayout?.visibility = INVISIBLE
                container?.visibility = INVISIBLE
                downloadButtonLayout?.visibility = VISIBLE
                downloadProgression?.visibility = INVISIBLE
            }
            Status.DOWNLOADING -> {
                switchLayout?.visibility = INVISIBLE
                container?.visibility = INVISIBLE
                downloadButtonLayout?.visibility = INVISIBLE
                downloadProgression?.visibility = VISIBLE
            }
        }
        controlButtonByCode(
            myPhotosButton,
            resultCode,
            EffectsController.RESULT_ERROR_TRY_ANOTHER_PHOTO
        )
        controlButtonByCode(
            tryAgainLaterButton,
            resultCode,
            EffectsController.RESULT_ERROR_TRY_AGAIN_LATER
        )
        controlButtonByCode(continueButton, resultCode, EffectsController.RESULT_ERROR_CONTINUE)
    }

    private fun showBasicLayout() {
        title?.text = effectTextRes.effectTitle
        description?.text = effectTextRes.effectSubTitle
        switchLayout?.visibility = VISIBLE
        container?.visibility = INVISIBLE
        downloadProgression?.visibility = INVISIBLE
        downloadButtonLayout?.visibility = INVISIBLE
        downloadProgression?.visibility = INVISIBLE
    }

    private fun showFailedLayout(errorMessage: String?) {
        switchLayout?.visibility = INVISIBLE
        title?.text = effectTextRes.effectFailedTitle
        description?.text = errorMessage
        container?.visibility = VISIBLE
        downloadProgression?.visibility = INVISIBLE
        downloadButtonLayout?.visibility = INVISIBLE
        downloadProgression?.visibility = INVISIBLE
    }

    private fun controlButtonByCode(view: View?, resultCode: Int?, mask: Int) {
        view?.visibility = if (resultCode == null || resultCode and mask == 0) GONE else VISIBLE
    }

    /**
     * Updates the wallpaper effect switch title.
     *
     * @param title The title of the switch.
     */
    fun updateEffectTitle(title: String?) {
        effectTitle!!.text = title
    }

    private fun switchChanged(effect: EffectEnumInterface, isChecked: Boolean) {
        effectSwitchListener?.onEffectSwitchChanged(effect, isChecked)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        effectSwitch = findViewById(R.id.wallpaper_effect_switch)
        effectTitle = findViewById(R.id.wallpaper_effect_toggle_title)
        title = findViewById(R.id.wallpaper_effects_title)
        description = findViewById(R.id.wallpaper_effects_subtitle)
        myPhotosButton = findViewById(R.id.open_my_photo_button)
        tryAgainLaterButton = findViewById(R.id.try_again_button)
        continueButton = findViewById(R.id.continue_button)
        container = findViewById(R.id.buttons_container)
        switchLayout = findViewById(R.id.wallpaper_effect_linear_layout)
        switchLayout?.setOnClickListener {
            if (effectSwitch?.isEnabled == true) effectSwitch?.toggle()
        }
        downloadButton = findViewById(R.id.download_model_button)
        downloadButton?.setOnClickListener { downloadClickListener?.onEffectDownloadClick() }

        downloadButtonLayout = findViewById(R.id.button_layout)
        downloadProgression = findViewById(R.id.action_progress)
    }

    /** Implements a listener to know when an effect switch has been selected/unselected. */
    interface EffectSwitchListener {
        /**
         * Called when one of the effect switches has change the checked value.
         *
         * @param effect The effect connected to the switch.
         * @param isChecked if the switch is checked.
         */
        fun onEffectSwitchChanged(effect: EffectEnumInterface, isChecked: Boolean)
    }

    /** Implements a listener to know when an effect download button has been click. */
    interface EffectDownloadClickListener {
        fun onEffectDownloadClick()
    }
}
