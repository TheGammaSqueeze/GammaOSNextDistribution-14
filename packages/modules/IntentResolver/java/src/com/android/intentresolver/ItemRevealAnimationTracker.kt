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

package com.android.intentresolver

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import com.android.intentresolver.chooser.TargetInfo

private const val IMAGE_FADE_IN_MILLIS = 150L

internal class ItemRevealAnimationTracker {
    private val iconProgress = HashMap<TargetInfo, Record>()
    private val labelProgress = HashMap<TargetInfo, Record>()

    fun reset() {
        iconProgress.clear()
        labelProgress.clear()
    }

    fun animateIcon(view: View, info: TargetInfo) = animateView(view, info, iconProgress)
    fun animateLabel(view: View, info: TargetInfo) = animateView(view, info, labelProgress)

    private fun animateView(view: View, info: TargetInfo, map: MutableMap<TargetInfo, Record>) {
        val record = map.getOrPut(info) {
            Record()
        }
        if ((view.animation as? RevealAnimation)?.record === record) return

        view.clearAnimation()
        if (record.alpha >= 1f) {
            view.alpha = 1f
            return
        }

        view.startAnimation(RevealAnimation(record))
    }

    private class Record(var alpha: Float = 0f)

    private class RevealAnimation(val record: Record) : AlphaAnimation(record.alpha, 1f) {
        init {
            duration = (IMAGE_FADE_IN_MILLIS * (1f - record.alpha)).toLong()
            interpolator = LinearInterpolator()
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            super.applyTransformation(interpolatedTime, t)
            // One TargetInfo can be simultaneously bou into multiple UI grid items; make sure
            // that the alpha value only increases. This should not affect running animations, only
            // a starting point for a new animation when a different view is bound to this target.
            record.alpha = minOf(1f, maxOf(record.alpha, t.alpha))
        }
    }
}
