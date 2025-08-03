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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.clock.ui.view.ClockCarouselView
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.wallpaper.picker.customization.ui.section.ScreenPreviewClickView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object ClockCarouselViewBinder {

    @JvmStatic
    fun bind(
        context: Context,
        carouselView: ClockCarouselView,
        screenPreviewClickView: ScreenPreviewClickView,
        viewModel: ClockCarouselViewModel,
        clockViewFactory: ClockViewFactory,
        lifecycleOwner: LifecycleOwner,
        isTwoPaneAndSmallWidth: Boolean,
    ) {
        carouselView.setClockViewFactory(clockViewFactory)
        carouselView.isVisible = true
        clockViewFactory.updateRegionDarkness()
        val carouselAccessibilityDelegate =
            CarouselAccessibilityDelegate(
                context,
                scrollForwardCallback = {
                    // Callback code for scrolling forward
                    carouselView.transitionToNext()
                },
                scrollBackwardCallback = {
                    // Callback code for scrolling backward
                    carouselView.transitionToPrevious()
                }
            )
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.selectedClockSize, viewModel.allClocks, ::Pair).collect {
                        (size, allClocks) ->
                        carouselView.setUpClockCarouselView(
                            clockSize = size,
                            clocks = allClocks,
                            onClockSelected = { clock ->
                                viewModel.setSelectedClock(clock.clockId)
                            },
                            isTwoPaneAndSmallWidth = isTwoPaneAndSmallWidth,
                        )
                        screenPreviewClickView.accessibilityDelegate = carouselAccessibilityDelegate
                        screenPreviewClickView.setOnSideClickedListener { isStart ->
                            if (isStart) carouselView.scrollToPrevious()
                            else carouselView.scrollToNext()
                        }
                    }
                }

                launch {
                    viewModel.allClocks.collect {
                        it.forEach { clock -> clockViewFactory.updateTimeFormat(clock.clockId) }
                    }
                }

                launch {
                    viewModel.selectedIndex.collect { selectedIndex ->
                        carouselAccessibilityDelegate.contentDescriptionOfSelectedClock =
                            carouselView.getContentDescription(selectedIndex)
                        carouselView.setSelectedClockIndex(selectedIndex)
                    }
                }

                launch {
                    viewModel.seedColor.collect { clockViewFactory.updateColorForAllClocks(it) }
                }

                launch {
                    val night =
                        (context.resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                    viewModel.getClockCardColorResId(night).collect {
                        carouselView.setCarouselCardColor(ContextCompat.getColor(context, it))
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
}
