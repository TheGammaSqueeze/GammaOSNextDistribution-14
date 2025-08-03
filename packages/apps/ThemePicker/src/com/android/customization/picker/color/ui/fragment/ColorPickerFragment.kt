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
package com.android.customization.picker.color.ui.fragment

import android.app.WallpaperManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.transition.Transition
import androidx.transition.doOnStart
import com.android.customization.model.mode.DarkModeSectionController
import com.android.customization.module.ThemePickerInjector
import com.android.customization.picker.color.ui.binder.ColorPickerBinder
import com.android.themepicker.R
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ColorPickerFragment : AppbarFragment() {
    private var binding: ColorPickerBinder.Binding? = null

    companion object {
        @JvmStatic
        fun newInstance(): ColorPickerFragment {
            return ColorPickerFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_color_picker,
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

        val injector = InjectorProvider.getInjector() as ThemePickerInjector
        val lockScreenView: CardView = view.requireViewById(R.id.lock_preview)
        val homeScreenView: CardView = view.requireViewById(R.id.home_preview)
        val wallpaperInfoFactory = injector.getCurrentWallpaperInfoFactory(requireContext())
        val displayUtils: DisplayUtils = injector.getDisplayUtils(requireContext())
        val wallpaperColorsRepository = injector.getWallpaperColorsRepository()
        val wallpaperManager = WallpaperManager.getInstance(requireContext())

        binding =
            ColorPickerBinder.bind(
                view = view,
                viewModel =
                    ViewModelProvider(
                            requireActivity(),
                            injector.getColorPickerViewModelFactory(
                                context = requireContext(),
                                wallpaperColorsRepository = wallpaperColorsRepository,
                            ),
                        )
                        .get(),
                lifecycleOwner = this,
            )

        savedInstanceState?.let { binding?.restoreInstanceState(it) }

        val lockScreenPreviewBinder =
            ScreenPreviewBinder.bind(
                activity = requireActivity(),
                previewView = lockScreenView,
                viewModel =
                    ScreenPreviewViewModel(
                        previewUtils =
                            PreviewUtils(
                                context = requireContext(),
                                authority =
                                    requireContext()
                                        .getString(
                                            com.android.wallpaper.R.string
                                                .lock_screen_preview_provider_authority,
                                        ),
                            ),
                        wallpaperInfoProvider = { forceReload ->
                            suspendCancellableCoroutine { continuation ->
                                wallpaperInfoFactory.createCurrentWallpaperInfos(
                                    context,
                                    forceReload,
                                ) { homeWallpaper, lockWallpaper, _ ->
                                    lifecycleScope.launch {
                                        if (
                                            wallpaperColorsRepository.lockWallpaperColors.value
                                                is WallpaperColorsModel.Loading
                                        ) {
                                            loadInitialColors(
                                                wallpaperManager,
                                                wallpaperColorsRepository,
                                                CustomizationSections.Screen.LOCK_SCREEN
                                            )
                                        }
                                    }
                                    continuation.resume(lockWallpaper ?: homeWallpaper, null)
                                }
                            }
                        },
                        onWallpaperColorChanged = { colors ->
                            wallpaperColorsRepository.setLockWallpaperColors(colors)
                        },
                        wallpaperInteractor = injector.getWallpaperInteractor(requireContext()),
                        screen = CustomizationSections.Screen.LOCK_SCREEN,
                    ),
                lifecycleOwner = this,
                offsetToStart =
                    displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(requireActivity()),
                onWallpaperPreviewDirty = { activity?.recreate() },
            )
        val shouldMirrorHomePreview =
            wallpaperManager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM) != null &&
                wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0
        val mirrorSurface = if (shouldMirrorHomePreview) lockScreenPreviewBinder.surface() else null
        ScreenPreviewBinder.bind(
            activity = requireActivity(),
            previewView = homeScreenView,
            viewModel =
                ScreenPreviewViewModel(
                    previewUtils =
                        PreviewUtils(
                            context = requireContext(),
                            authorityMetadataKey =
                                requireContext()
                                    .getString(
                                        com.android.wallpaper.R.string.grid_control_metadata_name,
                                    ),
                        ),
                    wallpaperInfoProvider = { forceReload ->
                        suspendCancellableCoroutine { continuation ->
                            wallpaperInfoFactory.createCurrentWallpaperInfos(
                                context,
                                forceReload,
                            ) { homeWallpaper, lockWallpaper, _ ->
                                lifecycleScope.launch {
                                    if (
                                        wallpaperColorsRepository.homeWallpaperColors.value
                                            is WallpaperColorsModel.Loading
                                    ) {
                                        loadInitialColors(
                                            wallpaperManager,
                                            wallpaperColorsRepository,
                                            CustomizationSections.Screen.HOME_SCREEN
                                        )
                                    }
                                }
                                continuation.resume(homeWallpaper ?: lockWallpaper, null)
                            }
                        }
                    },
                    onWallpaperColorChanged = { colors ->
                        wallpaperColorsRepository.setHomeWallpaperColors(colors)
                    },
                    wallpaperInteractor = injector.getWallpaperInteractor(requireContext()),
                    screen = CustomizationSections.Screen.HOME_SCREEN,
                ),
            lifecycleOwner = this,
            offsetToStart =
                displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(requireActivity()),
            onWallpaperPreviewDirty = { activity?.recreate() },
            mirrorSurface = mirrorSurface,
        )
        val darkModeToggleContainerView: FrameLayout =
            view.requireViewById(R.id.dark_mode_toggle_container)
        val darkModeSectionView =
            DarkModeSectionController(
                    context,
                    lifecycle,
                    injector.getDarkModeSnapshotRestorer(requireContext()),
                    injector.getUserEventLogger(),
                )
                .createView(requireContext())
        darkModeSectionView.background = null
        darkModeToggleContainerView.addView(darkModeSectionView)

        (returnTransition as? Transition)?.doOnStart {
            lockScreenView.isVisible = false
            homeScreenView.isVisible = false
        }

        return view
    }

    private suspend fun loadInitialColors(
        wallpaperManager: WallpaperManager,
        colorViewModel: WallpaperColorsRepository,
        screen: CustomizationSections.Screen,
    ) {
        withContext(Dispatchers.IO) {
            val colors =
                wallpaperManager.getWallpaperColors(
                    if (screen == CustomizationSections.Screen.LOCK_SCREEN) {
                        WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                )
            withContext(Dispatchers.Main) {
                if (screen == CustomizationSections.Screen.LOCK_SCREEN) {
                    colorViewModel.setLockWallpaperColors(colors)
                } else {
                    colorViewModel.setHomeWallpaperColors(colors)
                }
            }
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        binding?.saveInstanceState(savedInstanceState)
    }

    override fun getDefaultTitle(): CharSequence {
        return requireContext().getString(R.string.color_picker_title)
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
