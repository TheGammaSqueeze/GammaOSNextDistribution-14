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
package com.android.wallpaper.picker.preview.ui.binder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import com.android.wallpaper.picker.preview.ui.util.FullResImageViewUtil
import com.android.wallpaper.picker.preview.ui.view.SystemScaledWallpaperPreviewSurfaceView
import com.android.wallpaper.picker.preview.ui.viewmodel.StaticWallpaperPreviewViewModel
import com.android.wallpaper.util.RtlUtils
import com.android.wallpaper.util.WallpaperCropUtils
import com.android.wallpaper.util.WallpaperSurfaceCallback.LOW_RES_BITMAP_BLUR_RADIUS
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.launch

object StaticWallpaperPreviewBinder {

    private val ALPHA_OUT: Interpolator = PathInterpolator(0f, 0f, 0.8f, 1f)
    private const val CROSS_FADE_DURATION: Long = 200

    fun bind(
        lowResImageView: ImageView,
        fullResImageView: SubsamplingScaleImageView,
        viewModel: StaticWallpaperPreviewViewModel,
        displaySize: Point,
        viewLifecycleOwner: LifecycleOwner,
        shouldCalibrateWithSystemScale: Boolean = false,
    ) {
        lowResImageView.initLowResImageView()
        fullResImageView.initFullResImageView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.lowResBitmap.collect { lowResImageView.setImageBitmap(it) } }

                launch {
                    viewModel.subsamplingScaleImageViewModel.collect { imageModel ->
                        val cropHint = imageModel.fullPreviewCropModels?.get(displaySize)?.cropHint
                        fullResImageView.setFullResImage(
                            ImageSource.cachedBitmap(imageModel.rawWallpaperBitmap),
                            imageModel.rawWallpaperSize,
                            displaySize,
                            cropHint,
                            RtlUtils.isRtl(lowResImageView.context),
                            shouldCalibrateWithSystemScale,
                        )

                        // Fill in the default crop region if the displaySize for this preview is
                        // missing.
                        viewModel.fullPreviewCropModels.putIfAbsent(
                            displaySize,
                            FullPreviewCropModel(
                                cropHint =
                                    WallpaperCropUtils.calculateVisibleRect(
                                        imageModel.rawWallpaperSize,
                                        Point(
                                            fullResImageView.measuredWidth,
                                            fullResImageView.measuredHeight
                                        )
                                    ),
                                cropSizeModel = null,
                            )
                        )

                        crossFadeInFullResImageView(lowResImageView, fullResImageView)
                    }
                }
            }
        }
    }

    private fun ImageView.initLowResImageView() {
        setRenderEffect(
            RenderEffect.createBlurEffect(
                LOW_RES_BITMAP_BLUR_RADIUS,
                LOW_RES_BITMAP_BLUR_RADIUS,
                Shader.TileMode.CLAMP
            )
        )
    }

    private fun SubsamplingScaleImageView.initFullResImageView() {
        setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
        setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
    }

    /**
     * @param shouldCalibrateWithSystemScale This flag should be true for rendering small previews.
     *   Unlikely full wallpaper preview for static wallpapers, small wallpaper preview does not
     *   scale up the surface view larger than the display view to conform with the system's actual
     *   wallpaper scale (see [SystemScaledWallpaperPreviewSurfaceView]). Instead we need to apply
     *   this system scale to [SubsamplingScaleImageView].
     */
    private fun SubsamplingScaleImageView.setFullResImage(
        imageSource: ImageSource,
        rawWallpaperSize: Point,
        displaySize: Point,
        cropHint: Rect?,
        isRtl: Boolean,
        shouldCalibrateWithSystemScale: Boolean = false,
    ) {
        // Set the full res image
        setImage(imageSource)
        // Calculate the scale and the center point for the full res image
        FullResImageViewUtil.getScaleAndCenter(
                Point(measuredWidth, measuredHeight),
                rawWallpaperSize,
                displaySize,
                cropHint,
                isRtl,
            )
            .let { scaleAndCenter ->
                minScale = scaleAndCenter.minScale
                maxScale = scaleAndCenter.maxScale
                val scale =
                    if (shouldCalibrateWithSystemScale)
                        WallpaperCropUtils.getSystemWallpaperMaximumScale(
                            context.applicationContext
                        )
                    else 1F
                setScaleAndCenter(scaleAndCenter.defaultScale * scale, scaleAndCenter.center)
            }
    }

    private fun crossFadeInFullResImageView(lowResImageView: ImageView, fullResImageView: View) {
        fullResImageView.alpha = 0f
        fullResImageView
            .animate()
            .alpha(1f)
            .setInterpolator(ALPHA_OUT)
            .setDuration(CROSS_FADE_DURATION)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        lowResImageView.setImageBitmap(null)
                    }
                }
            )
    }
}
