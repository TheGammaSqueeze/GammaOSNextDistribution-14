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
package com.android.wallpaper.picker.customization.animation.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import com.android.systemui.monet.ColorScheme
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader
import com.android.wallpaper.picker.customization.animation.Interpolators
import com.android.wallpaper.picker.customization.animation.shader.CircularRevealShader
import com.android.wallpaper.picker.customization.animation.shader.CompositeLoadingShader
import com.android.wallpaper.picker.customization.animation.shader.SparkleShader
import kotlin.math.max

/** Renders loading and reveal animation. */
// TODO (b/281878827): remove this and use loading animation in SystemUIShaderLib when available
class LoadingAnimation(
    /** The view used to play the loading and reveal animation */
    private val revealOverlay: View,
    /** The type of reveal animation to play */
    private val revealType: RevealType = RevealType.CIRCULAR,
    /**
     * Amount of time before the loading animation times out and plays reveal animation, null
     * represents no time out
     */
    private val timeOutDuration: Long? = null
) {

    /** Type representing the reveal animation to be played in [LoadingAnimation] */
    enum class RevealType {
        /**
         * Reveal animation that reveals the views beneath with an expanding circle starting from
         * the center, ending with the loading view hidden
         */
        CIRCULAR,
        /**
         * Reveal animation that fades out the animation effects on the loading view, leaving the
         * original loading view visible
         */
        FADE
    }

    private val pixelDensity = revealOverlay.resources.displayMetrics.density

    private val loadingShader = CompositeLoadingShader()
    private val colorTurbulenceNoiseShader =
        TurbulenceNoiseShader().apply {
            setPixelDensity(pixelDensity)
            setGridCount(NOISE_SIZE)
            setOpacity(1f)
            setInverseNoiseLuminosity(inverse = true)
            setBackgroundColor(Color.BLACK)
        }
    private val sparkleShader =
        SparkleShader().apply {
            setPixelDensity(pixelDensity)
            setGridCount(NOISE_SIZE)
        }
    private val revealShader = CircularRevealShader()

    // Do not set blur radius to 0. It causes a crash.
    private var blurRadius: Float = MIN_BLUR_PX

    private var elapsedTime = 0L
    private var transitionProgress = 0f
    // Responsible for fade in and blur on start of the loading.
    private var fadeInAnimator: ValueAnimator? = null
    private var timeAnimator: TimeAnimator? = null
    private var revealAnimator: ValueAnimator? = null

    private var animationState = AnimationState.IDLE

    private var blurEffect =
        RenderEffect.createBlurEffect(
            blurRadius * pixelDensity,
            blurRadius * pixelDensity,
            Shader.TileMode.CLAMP
        )

    fun playLoadingAnimation(seed: Long? = null) {
        if (
            animationState == AnimationState.FADE_IN_PLAYING ||
                animationState == AnimationState.FADE_IN_PLAYED
        )
            return

        if (animationState == AnimationState.REVEAL_PLAYING) revealAnimator?.cancel()

        animationState = AnimationState.FADE_IN_PLAYING

        revealOverlay.visibility = View.VISIBLE

        elapsedTime = seed ?: (0L..10000L).random()

        fadeInAnimator?.cancel()
        timeAnimator?.cancel()
        revealAnimator?.cancel()

        fadeInAnimator =
            ValueAnimator.ofFloat(transitionProgress, 1f).apply {
                duration = FADE_IN_DURATION_MS
                interpolator = Interpolators.STANDARD_DECELERATE
                addUpdateListener {
                    transitionProgress = it.animatedValue as Float
                    loadingShader.setAlpha(transitionProgress)
                    // Match the timing with the fade animations.
                    blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            animationState = AnimationState.FADE_IN_PLAYED
                        }
                    }
                )
                start()
            }

        // Keep clouds moving until we finish loading
        timeAnimator =
            TimeAnimator().apply {
                setTimeListener { _, totalTime, deltaTime -> flushUniforms(totalTime, deltaTime) }
                start()
            }
    }

    fun playRevealAnimation() {
        when (revealType) {
            RevealType.CIRCULAR -> playCircularRevealAnimation()
            RevealType.FADE -> playFadeRevealAnimation()
        }
    }

    private fun playCircularRevealAnimation() {
        if (
            animationState == AnimationState.REVEAL_PLAYING ||
                animationState == AnimationState.REVEAL_PLAYED ||
                animationState == AnimationState.FADE_OUT_PLAYING ||
                animationState == AnimationState.FADE_OUT_PLAYED
        )
            return

        if (animationState == AnimationState.FADE_IN_PLAYING) fadeInAnimator?.cancel()

        animationState = AnimationState.REVEAL_PLAYING

        revealOverlay.visibility = View.VISIBLE

        revealShader.setCenter(revealOverlay.width * 0.5f, revealOverlay.height * 0.5f)

        revealAnimator?.cancel()
        revealAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = REVEAL_DURATION_MS
                interpolator = Interpolators.STANDARD

                addUpdateListener {
                    val progress = it.animatedValue as Float

                    // Draw a circle slightly larger than the screen. Need some offset due to large
                    // blur radius.
                    revealShader.setRadius(
                        progress * max(revealOverlay.width, revealOverlay.height) * 2f
                    )
                    // Map [0,1] to [MAX, MIN].
                    val blurAmount =
                        (1f - progress) * (MAX_REVEAL_BLUR_AMOUNT - MIN_REVEAL_BLUR_AMOUNT) +
                            MIN_REVEAL_BLUR_AMOUNT
                    revealShader.setBlur(blurAmount)
                }

                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            resetCircularRevealAnimation()
                        }
                    }
                )

                start()
            }
    }

    private fun resetCircularRevealAnimation() {
        animationState = AnimationState.REVEAL_PLAYED

        revealOverlay.setRenderEffect(null)
        revealOverlay.visibility = View.INVISIBLE

        // Stop turbulence and reset everything.
        timeAnimator?.cancel()
        blurRadius = MIN_BLUR_PX
        transitionProgress = 0f
    }

    private fun playFadeRevealAnimation() {
        if (
            animationState == AnimationState.REVEAL_PLAYING ||
                animationState == AnimationState.REVEAL_PLAYED ||
                animationState == AnimationState.FADE_OUT_PLAYING ||
                animationState == AnimationState.FADE_OUT_PLAYED
        )
            return

        if (animationState == AnimationState.FADE_IN_PLAYING) fadeInAnimator?.cancel()

        animationState = AnimationState.FADE_OUT_PLAYING

        revealOverlay.visibility = View.VISIBLE

        fadeInAnimator =
            ValueAnimator.ofFloat(transitionProgress, 0f).apply {
                duration = FADE_OUT_DURATION_MS
                interpolator = Interpolators.STANDARD_DECELERATE
                addUpdateListener {
                    transitionProgress = it.animatedValue as Float
                    loadingShader.setAlpha(transitionProgress)
                    // Match the timing with the fade animations.
                    blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            resetFadeRevealAnimation()
                        }
                    }
                )
                start()
            }
    }

    private fun resetFadeRevealAnimation() {
        animationState = AnimationState.FADE_OUT_PLAYED

        revealOverlay.setRenderEffect(null)

        // Stop turbulence and reset everything.
        timeAnimator?.cancel()
        blurRadius = MIN_BLUR_PX
        transitionProgress = 0f
    }

    fun updateColor(colorScheme: ColorScheme) {
        colorTurbulenceNoiseShader.apply {
            setColor(colorScheme.accent1.s600)
            setBackgroundColor(colorScheme.accent1.s900)
        }

        sparkleShader.setColor(colorScheme.accent1.s600)
        loadingShader.setScreenColor(colorScheme.accent1.s900)
    }

    private fun flushUniforms(totalTime: Long, deltaTime: Long) {
        elapsedTime += deltaTime
        val time = elapsedTime / 1000f
        val viewWidth = revealOverlay.width.toFloat()
        val viewHeight = revealOverlay.height.toFloat()

        colorTurbulenceNoiseShader.apply {
            setSize(viewWidth, viewHeight)
            setNoiseMove(time * NOISE_SPEED, 0f, time * NOISE_SPEED)
        }

        sparkleShader.apply {
            setSize(viewWidth, viewHeight)
            setNoiseMove(time * NOISE_SPEED, 0f, time * NOISE_SPEED)
            setTime(time)
        }

        loadingShader.apply {
            setSparkle(sparkleShader)
            setColorTurbulenceMask(colorTurbulenceNoiseShader)
        }

        val renderEffect = RenderEffect.createRuntimeShaderEffect(loadingShader, "in_background")

        // Update the blur effect only when loading animation is playing.
        if (
            animationState == AnimationState.FADE_IN_PLAYING ||
                animationState == AnimationState.FADE_OUT_PLAYING
        ) {
            blurEffect =
                RenderEffect.createBlurEffect(
                    blurRadius * pixelDensity,
                    blurRadius * pixelDensity,
                    Shader.TileMode.MIRROR
                )
        }

        // Animation time out
        if (
            timeOutDuration != null &&
                totalTime > timeOutDuration &&
                animationState == AnimationState.FADE_IN_PLAYED
        ) {
            playRevealAnimation()
        }

        if (animationState == AnimationState.REVEAL_PLAYING) {
            revealOverlay.setRenderEffect(
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(revealShader, "in_src"),
                    RenderEffect.createChainEffect(renderEffect, blurEffect)
                )
            )
        } else {
            revealOverlay.setRenderEffect(RenderEffect.createChainEffect(renderEffect, blurEffect))
        }
    }

    /** Cancels the animation. Unlike end() , cancel() causes the animation to stop in its tracks */
    fun cancel() {
        fadeInAnimator?.cancel()
        timeAnimator?.cancel()
        revealAnimator?.removeAllListeners()
        revealAnimator?.removeAllUpdateListeners()
        revealAnimator?.cancel()
    }

    /** Ends the animation, and causes the animation to skip to the end state */
    fun end() {
        fadeInAnimator?.end()
        timeAnimator?.end()
        revealAnimator?.removeAllListeners()
        revealAnimator?.removeAllUpdateListeners()
        revealAnimator?.end()
        when (revealType) {
            RevealType.CIRCULAR -> resetCircularRevealAnimation()
            RevealType.FADE -> resetFadeRevealAnimation()
        }
    }

    fun setupRevealAnimation(seed: Long? = null, revealTransitionProgress: Float? = null) {
        cancel()

        revealOverlay.visibility = View.VISIBLE

        elapsedTime = seed ?: (0L..10000L).random()
        transitionProgress = revealTransitionProgress ?: 1f

        // Fast forward to state at the end of fade in animation
        blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
        blurEffect =
            RenderEffect.createBlurEffect(
                blurRadius * pixelDensity,
                blurRadius * pixelDensity,
                Shader.TileMode.MIRROR
            )
        animationState = AnimationState.FADE_IN_PLAYED
        loadingShader.setAlpha(transitionProgress)

        // Keep clouds moving until we finish loading
        timeAnimator =
            TimeAnimator().apply {
                setTimeListener { _, totalTime, deltaTime -> flushUniforms(totalTime, deltaTime) }
                start()
            }
    }

    fun getElapsedTime(): Long {
        return elapsedTime
    }

    fun getTransitionProgress(): Float {
        return transitionProgress
    }

    companion object {
        private const val NOISE_SPEED = 0.2f
        private const val NOISE_SIZE = 1.7f
        private const val MAX_BLUR_PX = 80f
        private const val MIN_BLUR_PX = 1f
        private const val FADE_IN_DURATION_MS = 1100L
        private const val FADE_OUT_DURATION_MS = 1500L
        const val TIME_OUT_DURATION_MS = 10000L
        private const val REVEAL_DURATION_MS = 3600L
        private const val MIN_REVEAL_BLUR_AMOUNT = 1f
        private const val MAX_REVEAL_BLUR_AMOUNT = 2.5f
    }

    enum class AnimationState {
        IDLE,
        FADE_IN_PLAYING,
        FADE_IN_PLAYED,
        FADE_OUT_PLAYING,
        FADE_OUT_PLAYED,
        REVEAL_PLAYING,
        REVEAL_PLAYED
    }
}
