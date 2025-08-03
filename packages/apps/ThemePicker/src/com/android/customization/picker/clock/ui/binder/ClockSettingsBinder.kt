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
package com.android.customization.picker.clock.ui.binder

import android.content.Context
import android.content.res.Configuration
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RadioGroup.OnCheckedChangeListener
import android.widget.SeekBar
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.ui.adapter.ClockSettingsTabAdapter
import com.android.customization.picker.clock.ui.view.ClockCarouselView
import com.android.customization.picker.clock.ui.view.ClockHostView
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.ui.binder.ColorOptionIconBinder
import com.android.customization.picker.common.ui.view.ItemSpacing
import com.android.themepicker.R
import com.android.wallpaper.picker.option.ui.binder.OptionItemBinder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** Bind between the clock settings screen and its view model. */
object ClockSettingsBinder {
    private const val SLIDER_ENABLED_ALPHA = 1f
    private const val SLIDER_DISABLED_ALPHA = .3f
    private const val COLOR_PICKER_ITEM_PREFIX_ID = 1234

    fun bind(
        view: View,
        viewModel: ClockSettingsViewModel,
        clockViewFactory: ClockViewFactory,
        lifecycleOwner: LifecycleOwner,
    ) {
        val clockHostView: ClockHostView = view.requireViewById(R.id.clock_host_view)
        val tabView: RecyclerView = view.requireViewById(R.id.tabs)
        val tabAdapter = ClockSettingsTabAdapter()
        tabView.adapter = tabAdapter
        tabView.layoutManager = LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        tabView.addItemDecoration(ItemSpacing(ItemSpacing.TAB_ITEM_SPACING_DP))
        val colorOptionContainerListView: LinearLayout = view.requireViewById(R.id.color_options)
        val slider: SeekBar = view.requireViewById(R.id.slider)
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewModel.onSliderProgressChanged(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let {
                        lifecycleOwner.lifecycleScope.launch { viewModel.onSliderProgressStop(it) }
                    }
                }
            }
        )

        val onCheckedChangeListener = OnCheckedChangeListener { _, id ->
            when (id) {
                R.id.radio_dynamic -> viewModel.setClockSize(ClockSize.DYNAMIC)
                R.id.radio_small -> viewModel.setClockSize(ClockSize.SMALL)
            }
        }
        val clockSizeRadioGroup =
            view.requireViewById<RadioGroup>(R.id.clock_size_radio_button_group)
        clockSizeRadioGroup.setOnCheckedChangeListener(onCheckedChangeListener)
        view.requireViewById<RadioButton>(R.id.radio_dynamic).text =
            getRadioText(
                view.context.applicationContext,
                view.resources.getString(R.string.clock_size_dynamic),
                view.resources.getString(R.string.clock_size_dynamic_description)
            )
        view.requireViewById<RadioButton>(R.id.radio_small).text =
            getRadioText(
                view.context.applicationContext,
                view.resources.getString(R.string.clock_size_small),
                view.resources.getString(R.string.clock_size_small_description)
            )

        val colorOptionContainer = view.requireViewById<View>(R.id.color_picker_container)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.seedColor.collect { seedColor ->
                        viewModel.selectedClockId.value?.let { selectedClockId ->
                            clockViewFactory.updateColor(selectedClockId, seedColor)
                        }
                    }
                }

                launch { viewModel.tabs.collect { tabAdapter.setItems(it) } }

                launch {
                    viewModel.selectedTab.collect { tab ->
                        when (tab) {
                            ClockSettingsViewModel.Tab.COLOR -> {
                                colorOptionContainer.isVisible = true
                                clockSizeRadioGroup.isInvisible = true
                            }
                            ClockSettingsViewModel.Tab.SIZE -> {
                                colorOptionContainer.isInvisible = true
                                clockSizeRadioGroup.isVisible = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.colorOptions.collect { colorOptions ->
                        colorOptionContainerListView.removeAllViews()
                        colorOptions.forEachIndexed { index, colorOption ->
                            colorOption.payload?.let { payload ->
                                val item =
                                    LayoutInflater.from(view.context)
                                        .inflate(
                                            R.layout.clock_color_option,
                                            colorOptionContainerListView,
                                            false,
                                        ) as LinearLayout
                                val darkMode =
                                    (view.resources.configuration.uiMode and
                                        Configuration.UI_MODE_NIGHT_MASK ==
                                        Configuration.UI_MODE_NIGHT_YES)
                                ColorOptionIconBinder.bind(
                                    item.requireViewById(R.id.foreground),
                                    payload,
                                    darkMode
                                )
                                OptionItemBinder.bind(
                                    view = item,
                                    viewModel = colorOptions[index],
                                    lifecycleOwner = lifecycleOwner,
                                    foregroundTintSpec = null,
                                )

                                val id = COLOR_PICKER_ITEM_PREFIX_ID + index
                                item.id = id
                                colorOptionContainerListView.addView(item)
                            }
                        }
                    }
                }

                launch {
                    viewModel.selectedColorOptionPosition.collect { selectedPosition ->
                        if (selectedPosition != -1) {
                            val colorOptionContainerListView: LinearLayout =
                                view.requireViewById(R.id.color_options)

                            val selectedView =
                                colorOptionContainerListView.findViewById<View>(
                                    COLOR_PICKER_ITEM_PREFIX_ID + selectedPosition
                                )
                            selectedView?.parent?.requestChildFocus(selectedView, selectedView)
                        }
                    }
                }

                launch {
                    combine(
                            viewModel.selectedClockId.mapNotNull { it },
                            viewModel.selectedClockSize,
                            ::Pair,
                        )
                        .collect { (clockId, size) ->
                            clockHostView.removeAllViews()
                            val clockView =
                                when (size) {
                                    ClockSize.DYNAMIC -> clockViewFactory.getLargeView(clockId)
                                    ClockSize.SMALL -> clockViewFactory.getSmallView(clockId)
                                }
                            // The clock view might still be attached to an existing parent. Detach
                            // before adding to another parent.
                            (clockView.parent as? ViewGroup)?.removeView(clockView)
                            clockHostView.addView(clockView)
                            when (size) {
                                ClockSize.DYNAMIC -> {
                                    // When clock size data flow emits clock size signal, we want
                                    // to update the view without triggering on checked change,
                                    // which is supposed to be triggered by user interaction only.
                                    clockSizeRadioGroup.setOnCheckedChangeListener(null)
                                    clockSizeRadioGroup.check(R.id.radio_dynamic)
                                    clockSizeRadioGroup.setOnCheckedChangeListener(
                                        onCheckedChangeListener
                                    )
                                    clockHostView.doOnPreDraw {
                                        it.pivotX = it.width / 2F
                                        it.pivotY = it.height / 2F
                                    }
                                }
                                ClockSize.SMALL -> {
                                    // When clock size data flow emits clock size signal, we want
                                    // to update the view without triggering on checked change,
                                    // which is supposed to be triggered by user interaction only.
                                    clockSizeRadioGroup.setOnCheckedChangeListener(null)
                                    clockSizeRadioGroup.check(R.id.radio_small)
                                    clockSizeRadioGroup.setOnCheckedChangeListener(
                                        onCheckedChangeListener
                                    )
                                    clockHostView.doOnPreDraw {
                                        it.pivotX = ClockCarouselView.getCenteredHostViewPivotX(it)
                                        it.pivotY = 0F
                                    }
                                }
                            }
                        }
                }

                launch {
                    viewModel.sliderProgress.collect { progress ->
                        slider.setProgress(progress, true)
                    }
                }

                launch {
                    viewModel.isSliderEnabled.collect { isEnabled ->
                        slider.isEnabled = isEnabled
                        slider.alpha =
                            if (isEnabled) SLIDER_ENABLED_ALPHA else SLIDER_DISABLED_ALPHA
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(
            LifecycleEventObserver { source, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        clockViewFactory.registerTimeTicker(source)
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        clockViewFactory.unregisterTimeTicker(source)
                    }
                    else -> {}
                }
            }
        )
    }

    private fun getRadioText(
        context: Context,
        title: String,
        description: String
    ): SpannableString {
        val text = SpannableString(title + "\n" + description)
        text.setSpan(
            TextAppearanceSpan(context, R.style.SectionTitleTextStyle),
            0,
            title.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.setSpan(
            TextAppearanceSpan(context, R.style.SectionSubtitleTextStyle),
            title.length + 1,
            title.length + 1 + description.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }
}
