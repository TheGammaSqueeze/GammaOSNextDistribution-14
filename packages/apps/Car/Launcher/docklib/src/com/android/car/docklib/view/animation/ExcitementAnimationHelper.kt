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

package com.android.car.docklib.view.animation

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.util.Log
import android.util.Property
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.core.animation.Animator
import androidx.core.animation.ArgbEvaluator
import androidx.core.animation.ObjectAnimator
import androidx.core.animation.PathInterpolator
import androidx.core.animation.PropertyValuesHolder
import androidx.core.graphics.alpha
import androidx.core.graphics.toColorLong
import com.google.android.material.imageview.ShapeableImageView

/**
 * Helper class to build an [Animator] that can animate a view to be excited or reset. This is
 * generally used to excite the dock item when something is hovered over it.
 */
class ExcitementAnimationHelper {
    companion object {
        private const val TAG = "ExcitementAnimator"
        private val DEBUG = Build.isDebuggable()
        private const val PVH_STROKE_WIDTH = "strokeWidth"
        private const val PVH_STROKE_COLOR = "strokeColor"
        private const val PVH_PADDING = "padding"
        private const val PVH_COLOR_FILTER = "colorFilter"

        /**
         * Creates an [Animator] using the final values to be animated to. The initial values to
         * start the animation from are taken from the View.
         *
         * Some assumptions:
         * 1. strokeWidth, strokeColor and contentPadding will only be animated if the given [view]
         *   is of type [ShapeableImageView]
         * 2. colorFilter will only be animated if the given [view] is of type [ImageView]
         * 3. The colorFilter set on the [view] should be [PorterDuffColorFilter] to be able to get
         *   the initial value from.
         *
         *   @param view [View] that should be animated and sourced the initial animation values
         *   from.
         *   @param animationDuration length of the animation
         *   @param toStrokeWidth final strokeWidth value
         *   @param toStrokeColor final strokeColor value
         *   @param toContentPadding final content padding value, applied to all sides
         *   @param toColorFilterAlpha final alpha value of the colorFilter
         *   @param successCallback called when the animation has completed successfully
         *   @param failureCallback called when the animation is unsuccessful/cancelled
         */
        fun getExcitementAnimator(
            view: View,
            animationDuration: Long,
            toStrokeWidth: Float,
            @ColorInt toStrokeColor: Int,
            toContentPadding: Int,
            toColorFilterAlpha: Float,
            successCallback: Runnable,
            failureCallback: Runnable
        ): Animator {
            // todo(b/312718542): hidden api(PorterDuffColorFilter.getAlpha) usage
            return getExcitementAnimator(
                view,
                animationDuration,
                fromStrokeWidth = getStrokeWidth(view, defaultWidth = 0f),
                toStrokeWidth = toStrokeWidth,
                fromStrokeColor = getStrokeColor(view, defaultColor = Color.rgb(0f, 0f, 0f)),
                toStrokeColor = toStrokeColor,
                fromPadding = getAverageContentPadding(view, defaultContentPadding = 0),
                toContentPadding = toContentPadding,
                fromColorFilterAlpha = getColorFilterAlpha(view, defaultColorFilterAlpha = 0f),
                toColorFilterAlpha = toColorFilterAlpha,
                successCallback,
                failureCallback
            )
        }

        private fun getExcitementAnimator(
            view: View,
            animationDuration: Long,
            fromStrokeWidth: Float,
            toStrokeWidth: Float,
            @ColorInt fromStrokeColor: Int,
            @ColorInt toStrokeColor: Int,
            fromPadding: Int,
            toContentPadding: Int,
            fromColorFilterAlpha: Float,
            toColorFilterAlpha: Float,
            successCallback: Runnable,
            failureCallback: Runnable
        ): Animator {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "getExcitementAnimator{" +
                            "view: $view, " +
                            "animationDuration: $animationDuration, " +
                            "fromStrokeWidth: $fromStrokeWidth," +
                            "toStrokeWidth: $toStrokeWidth," +
                            "fromStrokeColor: $fromStrokeColor," +
                            "toStrokeColor: $toStrokeColor," +
                            "fromPadding: $fromPadding," +
                            "toContentPadding: $toContentPadding," +
                            "fromColorFilterAlpha: $fromColorFilterAlpha," +
                            "toColorFilterAlpha: $toColorFilterAlpha," +
                            "}"
                )
            }
            var pvhStrokeWidth: PropertyValuesHolder? = null
            val pvhPadding: PropertyValuesHolder?
            var pvhColorFilter: PropertyValuesHolder? = null
            var pvhStrokeColor: PropertyValuesHolder? = null

            if (view is ShapeableImageView) {
                pvhStrokeWidth = PropertyValuesHolder.ofFloat(
                    PVH_STROKE_WIDTH,
                    fromStrokeWidth,
                    toStrokeWidth
                )

                pvhStrokeColor =
                    PropertyValuesHolder.ofObject(
                        object : Property<View, Int>(Int::class.java, PVH_STROKE_COLOR) {
                            override fun get(view: View?): Int {
                                return getStrokeColor(view, defaultColor = fromStrokeColor)
                            }

                            override fun set(view: View?, value: Int?) {
                                if (view is ShapeableImageView && value != null) {
                                    view.strokeColor = ColorStateList.valueOf(value)
                                }
                            }
                        },
                        ArgbEvaluator.getInstance(),
                        fromStrokeColor,
                        toStrokeColor
                    )
            }

            pvhPadding = PropertyValuesHolder.ofInt(
                object : Property<View, Int>(Int::class.java, PVH_PADDING) {
                    override fun get(view: View?): Int {
                        return if (view != null) {
                            getAverageContentPadding(view, defaultContentPadding = 0)
                        } else {
                            0
                        }
                    }

                    override fun set(view: View?, value: Int?) {
                        if (view != null && value != null) setContentPadding(view, value)
                    }
                },
                fromPadding,
                toContentPadding
            )

            if (view is ImageView) {
                pvhColorFilter =
                    PropertyValuesHolder.ofFloat(
                        object : Property<View, Float>(
                            Float::class.java,
                            PVH_COLOR_FILTER
                        ) {
                            override fun get(view: View?): Float {
                                return getColorFilterAlpha(view, defaultColorFilterAlpha = 0f)
                            }

                            override fun set(view: View?, value: Float?) {
                                if (view is ImageView && value != null) {
                                    view.colorFilter = PorterDuffColorFilter(
                                        Color.argb(value, 0f, 0f, 0f),
                                        PorterDuff.Mode.DARKEN
                                    )
                                }
                            }
                        },
                        fromColorFilterAlpha,
                        toColorFilterAlpha
                    )
            }

            val animator = ObjectAnimator.ofPropertyValuesHolder(
                view,
                pvhStrokeWidth,
                pvhPadding,
                pvhColorFilter,
                pvhStrokeColor
            )
            animator.setDuration(animationDuration)
            animator.interpolator = PathInterpolator(0f, 0f, 0f, 1f)
            animator.addListener(getAnimatorListener(successCallback, failureCallback))
            return animator
        }

        @VisibleForTesting
        fun getAnimatorListener(
            successCallback: Runnable,
            failureCallback: Runnable
        ): Animator.AnimatorListener {
            return object : Animator.AnimatorListener {
                private var isCancelled = false
                override fun onAnimationStart(animator: Animator) {
                    isCancelled = false
                }

                override fun onAnimationEnd(animator: Animator) {
                    if (!isCancelled) successCallback.run()
                }

                override fun onAnimationCancel(animator: Animator) {
                    isCancelled = true
                    failureCallback.run()
                }

                override fun onAnimationRepeat(animator: Animator) {
                    // no-op
                }
            }
        }

        private fun setContentPadding(view: View, contentPadding: Int) {
            (view as? ShapeableImageView)?.setContentPadding(
                contentPadding,
                contentPadding,
                contentPadding,
                contentPadding
            )
        }

        private fun getAverageContentPadding(view: View, defaultContentPadding: Int): Int {
            (view as? ShapeableImageView)?.let {
                return (it.contentPaddingStart + it.contentPaddingEnd +
                        it.contentPaddingTop + it.contentPaddingBottom) / 4
            }
            return defaultContentPadding
        }

        private fun getColorFilterAlpha(view: View?, defaultColorFilterAlpha: Float): Float {
            return ((view as? ImageView)
                ?.colorFilter as? PorterDuffColorFilter)
                ?.color?.toColorLong()?.alpha ?: defaultColorFilterAlpha
        }

        private fun getStrokeWidth(view: View?, defaultWidth: Float): Float {
            return (view as? ShapeableImageView)?.strokeWidth ?: defaultWidth
        }

        private fun getStrokeColor(view: View?, @ColorInt defaultColor: Int): Int {
            return (view as? ShapeableImageView)
                ?.strokeColor?.getColorForState(null, defaultColor) ?: defaultColor
        }
    }
}
