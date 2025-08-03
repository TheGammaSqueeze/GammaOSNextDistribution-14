/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.LinearLayout

/**
 * Animation Helper for animating elements on the appGrid
 */
class BackgroundAnimationHelper(
        private val appsGridBackground: LinearLayout,
        private val banner: Banner
) {
    private var bannerAnimatorSet = AnimatorSet()

    fun showBanner() {
        banner.visibility = View.VISIBLE
        val bannerTranslateAnimator = ValueAnimator.ofInt(-banner.measuredHeight, 0)
        val bannerAlphaAnimator = ObjectAnimator.ofFloat(banner, "alpha", 1f)
        val appsGridTranslateAnimator = ValueAnimator.ofInt(0, banner.measuredHeight)

        animateBanner(bannerTranslateAnimator, bannerAlphaAnimator, appsGridTranslateAnimator)
    }
    fun hideBanner() {
        val bannerTranslateAnimator = ValueAnimator.ofInt(0, -banner.measuredHeight)
        val bannerAlphaAnimator = ObjectAnimator.ofFloat(banner, "alpha", 0f)
        val appsGridTranslateAnimator = ValueAnimator.ofInt(banner.measuredHeight, 0)

        bannerTranslateAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                banner.visibility = View.GONE
            }
        })

        animateBanner(bannerTranslateAnimator, bannerAlphaAnimator, appsGridTranslateAnimator)
    }

    private fun animateBanner(
            bannerTranslateAnimator: ValueAnimator,
            bannerAlphaAnimator: ObjectAnimator,
            appsGridTranslateAnimator: ValueAnimator
    ) {
        bannerTranslateAnimator.addUpdateListener { banner.y = (it.animatedValue as Int).toFloat() }
        appsGridTranslateAnimator
            .addUpdateListener { appsGridBackground.y = (it.animatedValue as Int).toFloat() }

        bannerAnimatorSet.cancel()
        bannerAnimatorSet = AnimatorSet().apply {
            duration = BANNER_ANIMATION_DURATION_MS
            startDelay = BANNER_ANIMATION_START_DELAY_MS
            playTogether(
                bannerTranslateAnimator,
                bannerAlphaAnimator,
                appsGridTranslateAnimator
            )
        }
        bannerAnimatorSet.start()
    }

    private companion object {
        const val BANNER_ANIMATION_DURATION_MS = 300L
        const val BANNER_ANIMATION_START_DELAY_MS = 400L
    }
}
