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
 *
 */

package com.android.wallpaper.picker.customization.ui.section

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.systemui.shared.clocks.shared.model.ClockPreviewConstants
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.model.WallpaperPreviewNavigator
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.picker.FixedWidthDisplayRatioFrameLayout
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Controls the screen preview section. */
@OptIn(ExperimentalCoroutinesApi::class)
open class ScreenPreviewSectionController(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val screen: CustomizationSections.Screen,
    private val wallpaperInfoFactory: CurrentWallpaperInfoFactory,
    private val colorViewModel: WallpaperColorsRepository,
    private val displayUtils: DisplayUtils,
    private val wallpaperPreviewNavigator: WallpaperPreviewNavigator,
    private val wallpaperInteractor: WallpaperInteractor,
    private val wallpaperManager: WallpaperManager,
    private val isTwoPaneAndSmallWidth: Boolean,
    private val customizationPickerViewModel: CustomizationPickerViewModel,
) : CustomizationSectionController<ScreenPreviewView> {

    protected val isOnLockScreen: Boolean = screen == CustomizationSections.Screen.LOCK_SCREEN

    protected var previewViewBinding: ScreenPreviewBinder.Binding? = null

    /** Override to hide the lock screen clock preview. */
    open val hideLockScreenClockPreview = false

    override fun shouldRetainInstanceWhenSwitchingTabs(): Boolean {
        return true
    }

    override fun isAvailable(context: Context): Boolean {
        // Assumption is that, if this section controller is included, we are using the revamped UI
        // so it should always be shown.
        return true
    }

    override fun createView(context: Context): ScreenPreviewView {
        return createView(context, CustomizationSectionController.ViewCreationParams())
    }

    @SuppressLint("InflateParams")
    override fun createView(
        context: Context,
        params: CustomizationSectionController.ViewCreationParams,
    ): ScreenPreviewView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.screen_preview_section,
                    /* parent= */ null,
                ) as ScreenPreviewView

        if (isTwoPaneAndSmallWidth) {
            val previewHost =
                view.requireViewById<FixedWidthDisplayRatioFrameLayout>(R.id.preview_host)
            val layoutParams =
                FrameLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(
                        R.dimen.screen_preview_width_for_2_pane_small_width
                    ),
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            layoutParams.gravity = Gravity.CENTER
            previewHost.layoutParams = layoutParams
        }

        view
            .requireViewById<ScreenPreviewClickView>(R.id.screen_preview_click_view)
            .setOnPreviewClickedListener {
                lifecycleOwner.lifecycleScope.launch {
                    getWallpaperInfo(context)?.let { wallpaperInfo ->
                        wallpaperPreviewNavigator.showViewOnlyPreview(
                            wallpaperInfo,
                            /* isAssetIdPresent= */ false,
                        )
                    }
                }
            }

        val previewView: CardView = view.requireViewById(R.id.preview)

        bindScreenPreview(previewView, context, !params.isWallpaperVisibilityControlledByTab)
        return view
    }

    protected open fun bindScreenPreview(
        previewView: CardView,
        context: Context,
        isWallpaperAlwaysVisible: Boolean,
    ) {
        previewViewBinding?.destroy()
        previewViewBinding =
            ScreenPreviewBinder.bind(
                activity = activity,
                previewView = previewView,
                viewModel = createScreenPreviewViewModel(context),
                lifecycleOwner = lifecycleOwner,
                offsetToStart = displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(activity),
                onWallpaperPreviewDirty = { activity.recreate() },
                animationStateViewModel = customizationPickerViewModel,
                isWallpaperAlwaysVisible = isWallpaperAlwaysVisible,
            )
    }

    protected open fun createScreenPreviewViewModel(context: Context): ScreenPreviewViewModel {
        return ScreenPreviewViewModel(
            previewUtils =
                if (isOnLockScreen) {
                    PreviewUtils(
                        context = context,
                        authority =
                            context.getString(
                                R.string.lock_screen_preview_provider_authority,
                            ),
                    )
                } else {
                    PreviewUtils(
                        context = context,
                        authorityMetadataKey =
                            context.getString(
                                R.string.grid_control_metadata_name,
                            ),
                    )
                },
            wallpaperInfoProvider = { forceReload ->
                suspendCancellableCoroutine { continuation ->
                    wallpaperInfoFactory.createCurrentWallpaperInfos(
                        context,
                        forceReload,
                    ) { homeWallpaper, lockWallpaper, _ ->
                        val wallpaper =
                            if (isOnLockScreen) {
                                lockWallpaper ?: homeWallpaper
                            } else {
                                homeWallpaper ?: lockWallpaper
                            }
                        loadInitialColors(
                            context = context,
                            screen = screen,
                        )
                        continuation.resume(wallpaper, null)
                    }
                }
            },
            onWallpaperColorChanged = { colors ->
                if (isOnLockScreen) {
                    colorViewModel.setLockWallpaperColors(colors)
                } else {
                    colorViewModel.setHomeWallpaperColors(colors)
                }
            },
            initialExtrasProvider = { getInitialExtras(isOnLockScreen) },
            wallpaperInteractor = wallpaperInteractor,
            screen = screen,
        )
    }

    protected fun loadInitialColors(
        context: Context,
        screen: CustomizationSections.Screen,
    ) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val colors =
                wallpaperManager.getWallpaperColors(
                    if (screen == CustomizationSections.Screen.LOCK_SCREEN) {
                        WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                )
            withContext(Dispatchers.Main) {
                if (colors != null) {
                    if (screen == CustomizationSections.Screen.LOCK_SCREEN) {
                        colorViewModel.setLockWallpaperColors(colors)
                    } else {
                        colorViewModel.setHomeWallpaperColors(colors)
                    }
                }
            }
        }
    }

    private suspend fun getWallpaperInfo(context: Context): WallpaperInfo? {
        return suspendCancellableCoroutine { continuation ->
            wallpaperInfoFactory.createCurrentWallpaperInfos(
                context,
                true,
            ) { homeWallpaper, lockWallpaper, _ ->
                continuation.resume(
                    if (isOnLockScreen) {
                        lockWallpaper ?: homeWallpaper
                    } else {
                        homeWallpaper
                    },
                    null
                )
            }
        }
    }

    protected fun getInitialExtras(isOnLockScreen: Boolean): Bundle? {
        return if (isOnLockScreen) {
            Bundle().apply {
                // Hide the clock from the system UI rendered preview so we can
                // place the carousel on top of it.
                putBoolean(
                    ClockPreviewConstants.KEY_HIDE_CLOCK,
                    hideLockScreenClockPreview,
                )
            }
        } else {
            null
        }
    }
}
