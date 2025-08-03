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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.wallpaper.picker.option.ui.binder

import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.icon.ui.viewbinder.ContentDescriptionViewBinder
import com.android.wallpaper.picker.common.text.ui.viewbinder.TextViewBinder
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

object OptionItemBinder {
    /**
     * Binds the given [View] to the given [OptionItemViewModel].
     *
     * The child views of [view] must be named and arranged in the following manner, from top of the
     * z-axis to the bottom:
     * - [R.id.foreground] is the foreground drawable ([ImageView]).
     * - [R.id.background] is the view in the background ([View]).
     * - [R.id.selection_border] is a view rendering a border. It must have the same exact size as
     *   [R.id.background] ([View]) and must be placed below it on the z-axis (you read that right).
     *
     * The animation logic in this binder takes care of scaling up the border at the right time to
     * help it peek out around the background. In order to allow for this, you may need to disable
     * the clipping of child views across the view-tree using:
     * ```
     * android:clipChildren="false"
     * ```
     *
     * Optionally, there may be an [R.id.text] [TextView] to show the text from the view-model. If
     * one is not supplied, the text will be used as the content description of the icon.
     *
     * @param view The view; it must contain the child views described above.
     * @param viewModel The view-model.
     * @param lifecycleOwner The [LifecycleOwner].
     * @param animationSpec The specification for the animation.
     * @param foregroundTintSpec The specification of how to tint the foreground icons.
     * @return A [DisposableHandle] that must be invoked when the view is recycled.
     */
    fun bind(
        view: View,
        viewModel: OptionItemViewModel<*>,
        lifecycleOwner: LifecycleOwner,
        animationSpec: AnimationSpec = AnimationSpec(),
        foregroundTintSpec: TintSpec? = null,
    ): DisposableHandle {
        val borderView: View = view.requireViewById(R.id.selection_border)
        val backgroundView: View = view.requireViewById(R.id.background)
        val foregroundView: View = view.requireViewById(R.id.foreground)
        val textView: TextView? = view.findViewById(R.id.text)

        if (textView != null && viewModel.isTextUserVisible) {
            TextViewBinder.bind(
                view = textView,
                viewModel = viewModel.text,
            )
        } else {
            // Use the text as the content description of the foreground if we don't have a TextView
            // dedicated to for the text.
            ContentDescriptionViewBinder.bind(
                view = foregroundView,
                viewModel = viewModel.text,
            )
        }
        textView?.isVisible = viewModel.isTextUserVisible

        textView?.alpha =
            if (viewModel.isEnabled) {
                animationSpec.enabledAlpha
            } else {
                animationSpec.disabledTextAlpha
            }

        backgroundView.alpha =
            if (viewModel.isEnabled) {
                animationSpec.enabledAlpha
            } else {
                animationSpec.disabledBackgroundAlpha
            }

        foregroundView.alpha =
            if (viewModel.isEnabled) {
                animationSpec.enabledAlpha
            } else {
                animationSpec.disabledForegroundAlpha
            }

        view.onLongClickListener =
            if (viewModel.onLongClicked != null) {
                View.OnLongClickListener {
                    viewModel.onLongClicked.invoke()
                    true
                }
            } else {
                null
            }

        val job =
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        // We only want to animate if the view-model is updating in response to a
                        // selection or deselection of the same exact option. For that, we save the
                        // last
                        // value of isSelected.
                        var lastSelected: Boolean? = null

                        viewModel.key
                            .flatMapLatest {
                                // If the key changed, then it means that this binding is no longer
                                // rendering the UI for the same option as before, we nullify the
                                // last
                                // selected value to "forget" that we've ever seen a value for
                                // isSelected,
                                // effectively starting anew so the first update doesn't animate.
                                lastSelected = null
                                viewModel.isSelected
                            }
                            .collect { isSelected ->
                                if (foregroundTintSpec != null && foregroundView is ImageView) {
                                    if (isSelected) {
                                        foregroundView.setColorFilter(
                                            foregroundTintSpec.selectedColor
                                        )
                                    } else {
                                        foregroundView.setColorFilter(
                                            foregroundTintSpec.unselectedColor
                                        )
                                    }
                                }

                                animatedSelection(
                                    animationSpec = animationSpec,
                                    borderView = borderView,
                                    contentView = backgroundView,
                                    isSelected = isSelected,
                                    animate = lastSelected != null && lastSelected != isSelected,
                                )
                                view.isSelected = isSelected
                                lastSelected = isSelected
                            }
                    }

                    launch {
                        viewModel.onClicked.collect { onClicked ->
                            view.setOnClickListener(
                                if (onClicked != null) {
                                    View.OnClickListener { onClicked.invoke() }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

        return DisposableHandle { job.cancel() }
    }

    /**
     * Uses a "bouncy" animation to animate the selecting or un-selecting of a view with a
     * background and a border.
     *
     * Note that it is expected that the [borderView] is below the [contentView] on the z axis so
     * the latter obscures the former at rest.
     *
     * @param borderView A view for the selection border that should be shown when the view is
     *
     * ```
     *     selected.
     * @param contentView
     * ```
     *
     * The view containing the opaque part of the view.
     *
     * @param isSelected Whether the view is selected or not.
     * @param animationSpec The specification for the animation.
     * @param animate Whether to animate; if `false`, will jump directly to the final state without
     *
     * ```
     *     animating.
     * ```
     */
    private fun animatedSelection(
        borderView: View,
        contentView: View,
        isSelected: Boolean,
        animationSpec: AnimationSpec,
        animate: Boolean = true,
    ) {
        if (isSelected) {
            if (!animate) {
                borderView.alpha = 1f
                borderView.scale(1f)
                contentView.scale(0.86f)
                return
            }

            // Border scale.
            borderView
                .animate()
                .scale(1.099f)
                .setDuration(animationSpec.durationMs / 2)
                .setInterpolator(PathInterpolator(0.29f, 0f, 0.67f, 1f))
                .withStartAction {
                    borderView.scaleX = 0.98f
                    borderView.scaleY = 0.98f
                    borderView.alpha = 1f
                }
                .withEndAction {
                    borderView
                        .animate()
                        .scale(1f)
                        .setDuration(animationSpec.durationMs / 2)
                        .setInterpolator(PathInterpolator(0.33f, 0f, 0.15f, 1f))
                        .start()
                }
                .start()

            // Background scale.
            contentView
                .animate()
                .scale(0.9321f)
                .setDuration(animationSpec.durationMs / 2)
                .setInterpolator(PathInterpolator(0.29f, 0f, 0.67f, 1f))
                .withEndAction {
                    contentView
                        .animate()
                        .scale(0.86f)
                        .setDuration(animationSpec.durationMs / 2)
                        .setInterpolator(PathInterpolator(0.33f, 0f, 0.15f, 1f))
                        .start()
                }
                .start()
        } else {
            if (!animate) {
                borderView.alpha = 0f
                contentView.scale(1f)
                return
            }

            // Border opacity.
            borderView
                .animate()
                .alpha(0f)
                .setDuration(animationSpec.durationMs / 2)
                .setInterpolator(LinearInterpolator())
                .start()

            // Border scale.
            borderView
                .animate()
                .scale(1f)
                .setDuration(animationSpec.durationMs)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()

            // Background scale.
            contentView
                .animate()
                .scale(1f)
                .setDuration(animationSpec.durationMs)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        }
    }

    data class AnimationSpec(
        /** Opacity of the option when it's enabled. */
        val enabledAlpha: Float = 1f,
        /** Opacity of the option background when it's disabled. */
        val disabledBackgroundAlpha: Float = 0.5f,
        /** Opacity of the option foreground when it's disabled. */
        val disabledForegroundAlpha: Float = 0.5f,
        /** Opacity of the option text when it's disabled. */
        val disabledTextAlpha: Float = 0.61f,
        /** Duration of the animation, in milliseconds. */
        val durationMs: Long = 333L,
    )

    data class TintSpec(
        @ColorInt val selectedColor: Int,
        @ColorInt val unselectedColor: Int,
    )

    private fun View.scale(scale: Float) {
        scaleX = scale
        scaleY = scale
    }

    private fun ViewPropertyAnimator.scale(scale: Float): ViewPropertyAnimator {
        return scaleX(scale).scaleY(scale)
    }
}
