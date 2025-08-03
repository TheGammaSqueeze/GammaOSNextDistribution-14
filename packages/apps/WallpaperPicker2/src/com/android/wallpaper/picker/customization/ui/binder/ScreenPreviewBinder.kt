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

package com.android.wallpaper.picker.customization.ui.binder

import android.app.Activity
import android.app.WallpaperColors
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.SurfaceView
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.monet.ColorScheme
import com.android.wallpaper.R
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.BitmapCachingAsset
import com.android.wallpaper.asset.CurrentWallpaperAsset
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.picker.FixedWidthDisplayRatioFrameLayout
import com.android.wallpaper.picker.WorkspaceSurfaceHolderCallback
import com.android.wallpaper.picker.customization.animation.view.LoadingAnimation
import com.android.wallpaper.picker.customization.ui.section.ScreenPreviewClickView
import com.android.wallpaper.picker.customization.ui.view.WallpaperSurfaceView
import com.android.wallpaper.picker.customization.ui.viewmodel.AnimationStateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.ResourceUtils
import com.android.wallpaper.util.WallpaperConnection
import com.android.wallpaper.util.WallpaperSurfaceCallback
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch

/**
 * Binds between view and view-model for rendering the preview of the home screen or the lock
 * screen.
 */
object ScreenPreviewBinder {
    interface Binding {
        fun sendMessage(
            id: Int,
            args: Bundle = Bundle.EMPTY,
        )
        fun destroy()
        fun surface(): SurfaceView
    }

    /**
     * Binds the view to the given [viewModel].
     *
     * Note that if [dimWallpaper] is `true`, the wallpaper will be dimmed (to help highlight
     * something that is changing on top of the wallpaper, for example, the lock screen shortcuts or
     * the clock).
     */
    // TODO (b/274443705): incorporate color picker to allow preview loading on color change
    // TODO (b/274443705): make loading animation more continuous on reveal
    // TODO (b/274443705): adjust for better timing on animation reveal
    @JvmStatic
    fun bind(
        activity: Activity,
        previewView: CardView,
        viewModel: ScreenPreviewViewModel,
        lifecycleOwner: LifecycleOwner,
        offsetToStart: Boolean,
        dimWallpaper: Boolean = false,
        onWallpaperPreviewDirty: () -> Unit,
        animationStateViewModel: AnimationStateViewModel? = null,
        isWallpaperAlwaysVisible: Boolean = true,
        mirrorSurface: SurfaceView? = null,
    ): Binding {
        val workspaceSurface: SurfaceView = previewView.requireViewById(R.id.workspace_surface)
        val wallpaperSurface: WallpaperSurfaceView =
            previewView.requireViewById(R.id.wallpaper_surface)
        val thumbnailRequested = AtomicBoolean(false)
        // Tracks whether the live preview should be shown, since a) visibility updates may arrive
        // before the engine is ready, and b) we need this state for onResume
        // TODO(b/287618705) Remove this
        val showLivePreview = AtomicBoolean(isWallpaperAlwaysVisible)
        val fixedWidthDisplayFrameLayout = previewView.parent as? FixedWidthDisplayRatioFrameLayout
        val screenPreviewClickView = fixedWidthDisplayFrameLayout?.parent as? ScreenPreviewClickView
        if (screenPreviewClickView != null) {
            // If screenPreviewClickView exists, we will have it handle accessibility and
            // disable a11y for the descendants.
            // Set the content description on the parent view
            screenPreviewClickView.contentDescription =
                activity.resources.getString(viewModel.previewContentDescription)
            fixedWidthDisplayFrameLayout.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            // This ensures that we do not announce the time multiple times
            previewView.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        var wallpaperIsReadyForReveal = false
        val surfaceViewsReady = {
            wallpaperSurface.setBackgroundColor(Color.TRANSPARENT)
            workspaceSurface.visibility = View.VISIBLE
        }
        wallpaperSurface.setZOrderOnTop(false)

        val flags = BaseFlags.get()
        val isPageTransitionsFeatureEnabled = flags.isPageTransitionsFeatureEnabled(activity)
        val isMultiCropEnabled = flags.isMultiCropEnabled() && flags.isMultiCropPreviewUiEnabled()

        val showLoadingAnimation =
            flags.isPreviewLoadingAnimationEnabled(activity.applicationContext)
        var loadingAnimation: LoadingAnimation? = null
        val loadingView: ImageView = previewView.requireViewById(R.id.loading_view)

        if (dimWallpaper) {
            previewView.requireViewById<View>(R.id.wallpaper_dimming_scrim).isVisible = true
            workspaceSurface.setZOrderOnTop(true)
        }

        previewView.radius =
            previewView.resources.getDimension(R.dimen.wallpaper_picker_entry_card_corner_radius)

        previewView.isClickable = true

        var previewSurfaceCallback: WorkspaceSurfaceHolderCallback? = null
        var wallpaperSurfaceCallback: WallpaperSurfaceCallback? = null
        var wallpaperConnection: WallpaperConnection? = null
        var wallpaperInfo: WallpaperInfo? = null
        var animationState: AnimationStateViewModel.AnimationState? = null
        var loadingImageDrawable: Drawable? = null
        var animationTimeToRestore: Long? = null
        var animationTransitionProgress: Float? = null
        var animationColorToRestore: Int? = null
        var currentWallpaperThumbnail: Bitmap? = null

        var disposableHandle: DisposableHandle? = null

        val cleanupWallpaperConnectionRunnable = Runnable {
            disposableHandle?.dispose()
            wallpaperConnection?.destroy()
            wallpaperConnection = null
        }

        fun cleanupWallpaperConnection() {
            // If existing, remove any scheduled cleanups...
            previewView.removeCallbacks(cleanupWallpaperConnectionRunnable)
            // ...and cleanup immediately
            cleanupWallpaperConnectionRunnable.run()
        }

        val job =
            lifecycleOwner.lifecycleScope.launch {
                launch {
                    val lifecycleObserver =
                        object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) {
                                super.onStart(owner)
                                if (showLoadingAnimation) {
                                    if (loadingAnimation == null) {
                                        animationState =
                                            animationStateViewModel?.getAnimationState(
                                                viewModel.screen
                                            )
                                        loadingImageDrawable = animationState?.drawable
                                        // TODO (b/290054874): investigate why app restarts twice
                                        // The lines below are a workaround for the issue of
                                        // wallpaper picker lifecycle restarting twice after a
                                        // config change; because of this, on second start, saved
                                        // instance state would always return null. Instead we would
                                        // like the saved instance state on the first restart to
                                        // pass through to the second.
                                        animationTimeToRestore = animationState?.time
                                        animationTransitionProgress =
                                            animationState?.transitionProgress
                                        animationColorToRestore = animationState?.color
                                        // a null drawable means the loading animation should not
                                        // be played
                                        loadingImageDrawable?.let {
                                            loadingView.setImageDrawable(it)
                                            loadingAnimation =
                                                LoadingAnimation(
                                                    loadingView,
                                                    LoadingAnimation.RevealType.CIRCULAR,
                                                    LoadingAnimation.TIME_OUT_DURATION_MS
                                                )
                                        }
                                    }
                                }
                            }

                            override fun onDestroy(owner: LifecycleOwner) {
                                super.onDestroy(owner)
                                if (isPageTransitionsFeatureEnabled) {
                                    cleanupWallpaperConnection()
                                }
                            }

                            override fun onStop(owner: LifecycleOwner) {
                                super.onStop(owner)
                                animationTimeToRestore =
                                    loadingAnimation?.getElapsedTime() ?: animationTimeToRestore
                                animationTransitionProgress =
                                    loadingAnimation?.getTransitionProgress()
                                        ?: animationTransitionProgress
                                loadingAnimation?.end()
                                loadingAnimation = null
                                // To ensure reveal animation is only played after a theme config
                                // change from wallpaper/color switch, only save the current loading
                                // image if this is a configuration change restart and reset to
                                // null otherwise
                                animationStateViewModel?.saveAnimationState(
                                    viewModel.screen,
                                    // Check if activity is changing configurations, and check that
                                    // the set of changing configurations does not include screen
                                    // size changes (such as rotation and folding/unfolding device)
                                    // Note: activity.changingConfigurations is not 100% accurate
                                    if (
                                        activity.isChangingConfigurations &&
                                            (activity.changingConfigurations.and(
                                                ActivityInfo.CONFIG_SCREEN_SIZE
                                            ) == 0)
                                    ) {
                                        AnimationStateViewModel.AnimationState(
                                            loadingImageDrawable,
                                            animationTimeToRestore,
                                            animationTransitionProgress,
                                            animationColorToRestore,
                                        )
                                    } else null
                                )
                                wallpaperIsReadyForReveal = false
                                if (isPageTransitionsFeatureEnabled) {
                                    // delay cleanup to prevent flicker between onStop and page
                                    // transition animation start
                                    previewView.postDelayed(cleanupWallpaperConnectionRunnable, 100)
                                } else {
                                    cleanupWallpaperConnectionRunnable.run()
                                }
                            }

                            override fun onPause(owner: LifecycleOwner) {
                                super.onPause(owner)
                                wallpaperConnection?.setVisibility(false)
                            }
                        }

                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        previewSurfaceCallback =
                            WorkspaceSurfaceHolderCallback(
                                workspaceSurface,
                                viewModel.previewUtils,
                                viewModel.getInitialExtras(),
                            )
                        workspaceSurface.holder.addCallback(previewSurfaceCallback)
                        if (!dimWallpaper) {
                            workspaceSurface.setZOrderMediaOverlay(true)
                        }

                        wallpaperSurfaceCallback =
                            WallpaperSurfaceCallback(
                                previewView.context,
                                previewView,
                                wallpaperSurface,
                                CompletableFuture.completedFuture(
                                    WallpaperInfo.ColorInfo(
                                        /* wallpaperColors= */ null,
                                        ResourceUtils.getColorAttr(
                                            previewView.context,
                                            android.R.attr.colorSecondary,
                                        )
                                    )
                                ),
                            ) {
                                maybeLoadThumbnail(
                                    activity = activity,
                                    wallpaperInfo = wallpaperInfo,
                                    surfaceCallback = wallpaperSurfaceCallback,
                                    offsetToStart =
                                        if (isMultiCropEnabled) false else offsetToStart,
                                    onSurfaceViewsReady = surfaceViewsReady,
                                    thumbnailRequested = thumbnailRequested
                                )
                                if (showLoadingAnimation) {
                                    val colorAccent =
                                        animationColorToRestore
                                            ?: ResourceUtils.getColorAttr(
                                                activity,
                                                android.R.attr.colorAccent
                                            )
                                    val night =
                                        (previewView.resources.configuration.uiMode and
                                            Configuration.UI_MODE_NIGHT_MASK ==
                                            Configuration.UI_MODE_NIGHT_YES)
                                    loadingAnimation?.updateColor(
                                        ColorScheme(seed = colorAccent, darkTheme = night)
                                    )
                                    loadingAnimation?.setupRevealAnimation(
                                        animationTimeToRestore,
                                        animationTransitionProgress
                                    )
                                    val isStaticWallpaper =
                                        wallpaperInfo != null && wallpaperInfo !is LiveWallpaperInfo
                                    wallpaperIsReadyForReveal =
                                        isStaticWallpaper || wallpaperIsReadyForReveal
                                    if (wallpaperIsReadyForReveal) {
                                        loadingAnimation?.playRevealAnimation()
                                    }
                                }
                            }
                        wallpaperSurface.holder.addCallback(wallpaperSurfaceCallback)
                        if (!dimWallpaper) {
                            wallpaperSurface.setZOrderMediaOverlay(true)
                        }

                        if (!isWallpaperAlwaysVisible) {
                            wallpaperSurface.visibilityCallback = { visible: Boolean ->
                                showLivePreview.set(visible)
                                wallpaperConnection?.setVisibility(showLivePreview.get())
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                    }

                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    workspaceSurface.holder.removeCallback(previewSurfaceCallback)
                    previewSurfaceCallback?.cleanUp()
                    wallpaperSurface.holder.removeCallback(wallpaperSurfaceCallback)
                    wallpaperSurfaceCallback?.homeImageWallpaper?.post {
                        wallpaperSurfaceCallback?.cleanUp()
                    }
                }

                launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        var initialWallpaperUpdate = true
                        viewModel.shouldReloadWallpaper().collect { shouldReload ->
                            viewModel.getWallpaperInfo(forceReload = shouldReload)
                            // Do not update screen preview on initial update,since the initial
                            // update results from starting or resuming the activity.
                            if (initialWallpaperUpdate) {
                                initialWallpaperUpdate = false
                            } else if (shouldReload) {
                                onWallpaperPreviewDirty()
                            }
                        }
                    }
                }

                launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.wallpaperThumbnail().collect { thumbnail ->
                            currentWallpaperThumbnail = thumbnail
                        }
                    }
                }

                launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.workspaceUpdateEvents()?.collect {
                            workspaceSurface.holder.removeCallback(previewSurfaceCallback)
                            previewSurfaceCallback?.cleanUp()
                            removeAndReadd(workspaceSurface)
                            previewSurfaceCallback =
                                WorkspaceSurfaceHolderCallback(
                                    workspaceSurface,
                                    viewModel.previewUtils,
                                    viewModel.getInitialExtras(),
                                )
                            workspaceSurface.holder.addCallback(previewSurfaceCallback)
                        }
                    }
                }

                if (showLoadingAnimation) {
                    launch {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.isLoading.collect { isLoading ->
                                if (isLoading) {
                                    loadingAnimation?.cancel()

                                    // Loading is started, create a new loading animation
                                    // with the current wallpaper as background.
                                    // First, try to get the wallpaper image from
                                    // wallpaperSurfaceCallback, this is the best solution for
                                    // static and live wallpapers but not for creative wallpapers
                                    val wallpaperPreviewImage =
                                        wallpaperSurfaceCallback?.homeImageWallpaper
                                    // If wallpaper drawable was not loaded, and the preview
                                    // drawable is the placeholder color drawable, use the wallpaper
                                    // thumbnail instead: the best solution for creative wallpapers
                                    val animationBackground: Drawable? =
                                        if (wallpaperPreviewImage?.drawable is ColorDrawable) {
                                            currentWallpaperThumbnail?.let { thumbnail ->
                                                BitmapDrawable(activity.resources, thumbnail)
                                            }
                                                ?: wallpaperPreviewImage.drawable
                                        } else wallpaperPreviewImage?.drawable
                                    animationBackground?.let {
                                        loadingView.setImageDrawable(animationBackground)
                                        loadingAnimation =
                                            LoadingAnimation(
                                                loadingView,
                                                LoadingAnimation.RevealType.CIRCULAR,
                                                LoadingAnimation.TIME_OUT_DURATION_MS
                                            )
                                    }
                                    loadingImageDrawable = animationBackground
                                    val colorAccent =
                                        ResourceUtils.getColorAttr(
                                            activity,
                                            android.R.attr.colorAccent
                                        )
                                    val night =
                                        (previewView.resources.configuration.uiMode and
                                            Configuration.UI_MODE_NIGHT_MASK ==
                                            Configuration.UI_MODE_NIGHT_YES)
                                    animationColorToRestore = colorAccent
                                    loadingAnimation?.updateColor(
                                        ColorScheme(seed = colorAccent, darkTheme = night)
                                    )
                                    loadingAnimation?.playLoadingAnimation()
                                }
                            }
                        }
                    }
                }

                launch {
                    lifecycleOwner.repeatOnLifecycle(
                        if (isPageTransitionsFeatureEnabled) {
                            Lifecycle.State.STARTED
                        } else {
                            Lifecycle.State.RESUMED
                        }
                    ) {
                        lifecycleOwner.lifecycleScope.launch {
                            wallpaperInfo = viewModel.getWallpaperInfo(forceReload = false)
                            maybeLoadThumbnail(
                                activity = activity,
                                wallpaperInfo = wallpaperInfo,
                                surfaceCallback = wallpaperSurfaceCallback,
                                offsetToStart = if (isMultiCropEnabled) false else offsetToStart,
                                onSurfaceViewsReady = surfaceViewsReady,
                                thumbnailRequested = thumbnailRequested
                            )
                            if (showLoadingAnimation && wallpaperInfo !is LiveWallpaperInfo) {
                                loadingAnimation?.playRevealAnimation()
                            }
                            (wallpaperInfo as? LiveWallpaperInfo)?.let { liveWallpaperInfo ->
                                if (isPageTransitionsFeatureEnabled) {
                                    cleanupWallpaperConnection()
                                }
                                val connection =
                                    wallpaperConnection
                                        ?: createWallpaperConnection(
                                                liveWallpaperInfo,
                                                previewView,
                                                viewModel,
                                                wallpaperSurface,
                                                mirrorSurface,
                                                viewModel.screen
                                            ) {
                                                surfaceViewsReady()
                                                if (showLoadingAnimation) {
                                                    wallpaperIsReadyForReveal = true
                                                    loadingAnimation?.playRevealAnimation()
                                                }
                                            }
                                            .also { wallpaperConnection = it }
                                if (!previewView.isAttachedToWindow) {
                                    // Sometimes the service gets connected before the view
                                    // is valid.
                                    // TODO(b/284233455): investigate why and remove this workaround
                                    val listener =
                                        object : OnAttachStateChangeListener {
                                            override fun onViewAttachedToWindow(v: View) {
                                                connection.connect()
                                                connection.setVisibility(showLivePreview.get())
                                                previewView.removeOnAttachStateChangeListener(this)
                                            }

                                            override fun onViewDetachedFromWindow(v: View) {
                                                // Do nothing
                                            }
                                        }

                                    previewView.addOnAttachStateChangeListener(listener)
                                    disposableHandle = DisposableHandle {
                                        previewView.removeOnAttachStateChangeListener(listener)
                                    }
                                } else {
                                    connection.connect()
                                    connection.setVisibility(showLivePreview.get())
                                }
                            }
                        }
                    }
                }
            }

        return object : Binding {
            override fun sendMessage(id: Int, args: Bundle) {
                previewSurfaceCallback?.send(id, args)
            }

            override fun destroy() {
                job.cancel()
                // We want to remove the SurfaceView from its parent and add it back. This causes
                // the hierarchy to treat the SurfaceView as "dirty" which will cause it to render
                // itself anew the next time the bind function is invoked.
                removeAndReadd(workspaceSurface)
            }

            override fun surface(): SurfaceView {
                return wallpaperSurface
            }
        }
    }

    private fun createWallpaperConnection(
        liveWallpaperInfo: LiveWallpaperInfo,
        previewView: CardView,
        viewModel: ScreenPreviewViewModel,
        wallpaperSurface: SurfaceView,
        mirrorSurface: SurfaceView?,
        screen: CustomizationSections.Screen,
        onEngineShown: () -> Unit
    ) =
        WallpaperConnection(
            Intent(WallpaperService.SERVICE_INTERFACE).apply {
                setClassName(
                    liveWallpaperInfo.wallpaperComponent.packageName,
                    liveWallpaperInfo.wallpaperComponent.serviceName
                )
            },
            previewView.context,
            object : WallpaperConnection.WallpaperConnectionListener {
                override fun onWallpaperColorsChanged(colors: WallpaperColors?, displayId: Int) {
                    viewModel.onWallpaperColorsChanged(colors)
                }

                override fun onEngineShown() {
                    onEngineShown()
                }
            },
            wallpaperSurface,
            mirrorSurface,
            screen.toFlag(),
            WallpaperConnection.WhichPreview.PREVIEW_CURRENT
        )

    private fun removeAndReadd(view: View) {
        (view.parent as? ViewGroup)?.let { parent ->
            val indexInParent = parent.indexOfChild(view)
            if (indexInParent >= 0) {
                parent.removeView(view)
                parent.addView(view, indexInParent)
            }
        }
    }

    private fun maybeLoadThumbnail(
        activity: Activity,
        wallpaperInfo: WallpaperInfo?,
        surfaceCallback: WallpaperSurfaceCallback?,
        offsetToStart: Boolean,
        onSurfaceViewsReady: () -> Unit,
        thumbnailRequested: AtomicBoolean
    ) {
        if (wallpaperInfo == null || surfaceCallback == null) {
            return
        }

        val imageView = surfaceCallback.homeImageWallpaper
        val thumbAsset: Asset = wallpaperInfo.getThumbAsset(activity)
        if (imageView != null && imageView.drawable == null) {
            if (!thumbnailRequested.compareAndSet(false, true)) {
                return
            }
            // Respect offsetToStart only for CurrentWallpaperAssetVN otherwise true.
            BitmapCachingAsset(activity, thumbAsset)
                .loadPreviewImage(
                    activity,
                    imageView,
                    ResourceUtils.getColorAttr(activity, android.R.attr.colorSecondary),
                    /* offsetToStart= */ thumbAsset !is CurrentWallpaperAsset || offsetToStart,
                    wallpaperInfo.wallpaperCropHints
                )
            if (wallpaperInfo !is LiveWallpaperInfo) {
                imageView.addOnLayoutChangeListener(
                    object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: View?,
                            left: Int,
                            top: Int,
                            right: Int,
                            bottom: Int,
                            oldLeft: Int,
                            oldTop: Int,
                            oldRight: Int,
                            oldBottom: Int
                        ) {
                            v?.removeOnLayoutChangeListener(this)
                            onSurfaceViewsReady()
                        }
                    }
                )
            }
        }
    }
}
