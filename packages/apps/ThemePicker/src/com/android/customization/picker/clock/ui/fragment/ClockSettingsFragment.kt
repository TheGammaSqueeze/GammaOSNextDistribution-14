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
package com.android.customization.picker.clock.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.transition.Transition
import androidx.transition.doOnStart
import com.android.customization.module.ThemePickerInjector
import com.android.customization.picker.clock.ui.binder.ClockSettingsBinder
import com.android.systemui.shared.clocks.shared.model.ClockPreviewConstants
import com.android.themepicker.R
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class ClockSettingsFragment : AppbarFragment() {
    companion object {
        const val DESTINATION_ID = "clock_settings"

        @JvmStatic
        fun newInstance(): ClockSettingsFragment {
            return ClockSettingsFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_clock_settings,
                container,
                false,
            )
        setUpToolbar(view)

        // For nav bar edge-to-edge effect.
        view.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets
        }

        val context = requireContext()
        val activity = requireActivity()
        val injector = InjectorProvider.getInjector() as ThemePickerInjector

        val lockScreenView: CardView = view.requireViewById(R.id.lock_preview)
        val wallpaperColorsRepository = injector.getWallpaperColorsRepository()
        val displayUtils = injector.getDisplayUtils(context)
        ScreenPreviewBinder.bind(
            activity = activity,
            previewView = lockScreenView,
            viewModel =
                ScreenPreviewViewModel(
                    previewUtils =
                        PreviewUtils(
                            context = context,
                            authority =
                                resources.getString(
                                    com.android.wallpaper.R.string
                                        .lock_screen_preview_provider_authority,
                                ),
                        ),
                    wallpaperInfoProvider = { forceReload ->
                        suspendCancellableCoroutine { continuation ->
                            injector
                                .getCurrentWallpaperInfoFactory(context)
                                .createCurrentWallpaperInfos(
                                    context,
                                    forceReload,
                                ) { homeWallpaper, lockWallpaper, _ ->
                                    continuation.resume(
                                        lockWallpaper ?: homeWallpaper,
                                        null,
                                    )
                                }
                        }
                    },
                    onWallpaperColorChanged = { colors ->
                        wallpaperColorsRepository.setLockWallpaperColors(colors)
                    },
                    initialExtrasProvider = {
                        Bundle().apply {
                            // Hide the clock from the system UI rendered preview so we can
                            // place the carousel on top of it.
                            putBoolean(
                                ClockPreviewConstants.KEY_HIDE_CLOCK,
                                true,
                            )
                        }
                    },
                    wallpaperInteractor = injector.getWallpaperInteractor(requireContext()),
                    screen = CustomizationSections.Screen.LOCK_SCREEN,
                ),
            lifecycleOwner = this,
            offsetToStart = displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(activity),
            onWallpaperPreviewDirty = { activity.recreate() },
        )

        ClockSettingsBinder.bind(
            view,
            ViewModelProvider(
                    this,
                    injector.getClockSettingsViewModelFactory(
                        context,
                        injector.getWallpaperColorsRepository(),
                        injector.getClockViewFactory(activity),
                    ),
                )
                .get(),
            injector.getClockViewFactory(activity),
            viewLifecycleOwner,
        )

        (returnTransition as? Transition)?.doOnStart { lockScreenView.isVisible = false }

        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return requireContext().getString(R.string.clock_color_and_size_title)
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }

    override fun getToolbarTextColor(): Int {
        return ContextCompat.getColor(
            requireContext(),
            com.android.wallpaper.R.color.system_on_surface
        )
    }
}
