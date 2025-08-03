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
 *
 */

package com.android.customization.picker.grid.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Transition
import androidx.transition.doOnStart
import com.android.customization.model.CustomizationManager.Callback
import com.android.customization.module.ThemePickerInjector
import com.android.customization.picker.grid.domain.interactor.GridInteractor
import com.android.customization.picker.grid.ui.binder.GridScreenBinder
import com.android.customization.picker.grid.ui.viewmodel.GridScreenViewModel
import com.android.themepicker.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

private val TAG = GridFragment::class.java.simpleName

@OptIn(ExperimentalCoroutinesApi::class)
class GridFragment : AppbarFragment() {

    private lateinit var gridInteractor: GridInteractor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_grid,
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

        val isGridApplyButtonEnabled = BaseFlags.get().isGridApplyButtonEnabled(requireContext())

        val injector = InjectorProvider.getInjector() as ThemePickerInjector

        val wallpaperInfoFactory = injector.getCurrentWallpaperInfoFactory(requireContext())
        var screenPreviewBinding =
            bindScreenPreview(
                view,
                wallpaperInfoFactory,
                injector.getWallpaperInteractor(requireContext()),
                injector.getGridInteractor(requireContext())
            )

        val viewModelFactory = injector.getGridScreenViewModelFactory(requireContext())
        gridInteractor = injector.getGridInteractor(requireContext())
        GridScreenBinder.bind(
            view = view,
            viewModel =
                ViewModelProvider(
                    this,
                    viewModelFactory,
                )[GridScreenViewModel::class.java],
            lifecycleOwner = this,
            backgroundDispatcher = Dispatchers.IO,
            onOptionsChanged = {
                screenPreviewBinding.destroy()
                screenPreviewBinding =
                    bindScreenPreview(
                        view,
                        wallpaperInfoFactory,
                        injector.getWallpaperInteractor(requireContext()),
                        gridInteractor,
                    )
                if (isGridApplyButtonEnabled) {
                    val applyButton: Button = view.requireViewById(R.id.apply_button)
                    applyButton.isEnabled = !gridInteractor.isSelectedOptionApplied()
                }
            },
            isGridApplyButtonEnabled = isGridApplyButtonEnabled,
            onOptionApplied = {
                gridInteractor.applySelectedOption(
                    object : Callback {
                        override fun onSuccess() {
                            Toast.makeText(
                                    context,
                                    getString(
                                        R.string.toast_of_changing_grid,
                                        gridInteractor.getSelectOptionNonSuspend()?.title
                                    ),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                            val applyButton: Button = view.requireViewById(R.id.apply_button)
                            applyButton.isEnabled = false
                        }

                        override fun onError(throwable: Throwable?) {
                            val errorMsg =
                                getString(
                                    R.string.toast_of_failure_to_change_grid,
                                    gridInteractor.getSelectOptionNonSuspend()?.title
                                )
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            Log.e(TAG, errorMsg, throwable)
                        }
                    }
                )
            }
        )

        (returnTransition as? Transition)?.doOnStart {
            view.requireViewById<View>(R.id.preview).isVisible = false
        }

        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return getString(R.string.grid_title)
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

    private fun bindScreenPreview(
        view: View,
        wallpaperInfoFactory: CurrentWallpaperInfoFactory,
        wallpaperInteractor: WallpaperInteractor,
        gridInteractor: GridInteractor
    ): ScreenPreviewBinder.Binding {
        return ScreenPreviewBinder.bind(
            activity = requireActivity(),
            previewView = view.requireViewById(R.id.preview),
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
                    initialExtrasProvider = {
                        val bundle = Bundle()
                        bundle.putString("name", gridInteractor.getSelectOptionNonSuspend()?.name)
                        bundle
                    },
                    wallpaperInfoProvider = {
                        suspendCancellableCoroutine { continuation ->
                            wallpaperInfoFactory.createCurrentWallpaperInfos(
                                context,
                                /* forceRefresh= */ true,
                            ) { homeWallpaper, lockWallpaper, _ ->
                                continuation.resume(homeWallpaper ?: lockWallpaper, null)
                            }
                        }
                    },
                    wallpaperInteractor = wallpaperInteractor,
                    screen = CustomizationSections.Screen.HOME_SCREEN,
                ),
            lifecycleOwner = viewLifecycleOwner,
            offsetToStart = false,
            onWallpaperPreviewDirty = { activity?.recreate() },
        )
    }

    override fun onBackPressed(): Boolean {
        if (BaseFlags.get().isGridApplyButtonEnabled(requireContext())) {
            gridInteractor.clearSelectedOption()
        }
        return super.onBackPressed()
    }
}
