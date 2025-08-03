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
 */

package com.android.permissioncontroller.safetycenter.ui

import android.transition.AutoTransition
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import java.time.Duration

/**
 * An animator which can animate a fade in/fade out of either one textView, or several textViews
 * that are in the same ViewGroup.
 */
class TextFadeAnimator
@JvmOverloads
constructor(targetIds: List<Int>, changeDuration: Duration = DEFAULT_TEXT_CHANGE_DURATION) {

    @JvmOverloads
    constructor(
        targetId: Int,
        changeDuration: Duration = DEFAULT_TEXT_CHANGE_DURATION
    ) : this(listOf(targetId), changeDuration)

    private val textChangeTransition: TransitionSet
    init {
        var transition =
            AutoTransition()
                .setInterpolator(linearInterpolator)
                .setDuration(changeDuration.toMillis())
        for (targetId in targetIds) {
            transition = transition.addTarget(targetId)
        }
        textChangeTransition = transition
    }

    @JvmOverloads
    fun animateChangeText(textView: TextView, text: String, onFinish: Runnable? = null) {
        animateChangeText(listOf(textView to text), onFinish)
    }

    /** Animate changes for a set of textViews under the same parent. */
    @JvmOverloads
    fun animateChangeText(textChanges: List<Pair<TextView, String>>, onFinish: Runnable? = null) {
        if (textChanges.isEmpty()) {
            return
        }

        Log.v(TAG, "Starting text animation")

        val firstView = textChanges[0].first
        val parentViewGroup: ViewGroup = firstView.parent as ViewGroup
        val fadeOutTransition =
            textChangeTransition
                .clone()
                .addListener(
                    object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: Transition?) {
                            fadeTextIn(textChanges, parentViewGroup, onFinish)
                        }
                    }
                )
        parentViewGroup.post {
            TransitionManager.beginDelayedTransition(parentViewGroup, fadeOutTransition)
            Log.v(TAG, "Starting text fade-out transition")
            for ((textView, _) in textChanges) {
                textView.visibility = View.INVISIBLE
            }
        }
    }

    private fun fadeTextIn(
        textChanges: List<Pair<TextView, String>>,
        parent: ViewGroup,
        onFinish: Runnable?
    ) {
        val fadeInTransition =
            textChangeTransition
                .clone()
                .addListener(
                    object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: Transition?) {
                            Log.v(TAG, String.format("Finishing text animation"))
                            onFinish?.run()
                        }
                    }
                )

        parent.post {
            TransitionManager.beginDelayedTransition(parent, fadeInTransition)
            Log.v(TAG, "Starting text fade-in transition")
            for ((textView, text) in textChanges) {
                textView.text = text
                textView.visibility = View.VISIBLE
            }
        }
    }

    fun cancelTextChangeAnimation(textView: TextView) {
        TransitionManager.endTransitions(textView.parent as ViewGroup)
    }

    companion object {
        private const val TAG = "TextFadeAnimator"
        // Duration is for fade-out & fade-in individually, not combined
        private val DEFAULT_TEXT_CHANGE_DURATION = Duration.ofMillis(167)
        private val linearInterpolator = LinearInterpolator()
    }
}
