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

import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterIssue
import android.text.TextUtils
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import java.time.Duration

class IssueCardAnimator(val callback: AnimationCallback) {

    fun transitionToIssueResolvedThenMarkComplete(
        context: Context,
        holder: PreferenceViewHolder,
        action: SafetyCenterIssue.Action
    ) {
        var successMessage = action.successMessage
        if (TextUtils.isEmpty(successMessage)) {
            successMessage = context.getString(R.string.safety_center_resolved_issue_fallback)
        }
        (holder.findViewById(R.id.resolved_issue_text) as TextView).text = successMessage
        val resolvedImageView = holder.findViewById(R.id.resolved_issue_image) as ImageView
        resolvedImageView.contentDescription = successMessage

        // Ensure AVD is reset before transition starts
        (resolvedImageView.drawable as AnimatedVectorDrawable).reset()

        val defaultIssueContentGroup = holder.findViewById(R.id.default_issue_content)!!
        val resolvedIssueContentGroup = holder.findViewById(R.id.resolved_issue_content)!!

        val transitionSet =
            TransitionSet()
                .setOrdering(TransitionSet.ORDERING_SEQUENTIAL)
                .setInterpolator(linearInterpolator)
                .addTransition(hideIssueContentTransition)
                .addTransition(
                    showResolvedImageTransition
                        .clone()
                        .addListener(
                            object : TransitionListenerAdapter() {
                                override fun onTransitionEnd(transition: Transition) {
                                    super.onTransitionEnd(transition)
                                    startIssueResolvedAnimation(
                                        resolvedIssueContentGroup,
                                        resolvedImageView
                                    )
                                }
                            }
                        )
                )
                .addTransition(showResolvedTextTransition)

        // Defer transition so that it's called after the root ViewGroup has been laid out.
        holder.itemView.post {
            TransitionManager.beginDelayedTransition(
                defaultIssueContentGroup.parent as ViewGroup?,
                transitionSet
            )

            // Setting INVISIBLE rather than GONE to ensure consistent card height between
            // view groups.
            defaultIssueContentGroup.visibility = View.INVISIBLE

            // These views are outside of the group since their visibility must be set
            // independently of the rest of the group, and some frustrating constraints of
            // constraint layout's behavior. See b/242705351 for context.
            makeInvisibleIfVisible(holder.findViewById(R.id.issue_card_attribution_title))
            makeInvisibleIfVisible(holder.findViewById(R.id.issue_card_dismiss_btn))
            makeInvisibleIfVisible(holder.findViewById(R.id.issue_card_subtitle))
            makeInvisibleIfVisible(holder.findViewById(R.id.issue_card_protected_by_android))

            resolvedIssueContentGroup.visibility = View.VISIBLE
        }

        // Cancel animations if they are scrolled out of view (detached from recycler view)
        holder.itemView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    holder.itemView.removeOnAttachStateChangeListener(this)
                    cancelIssueResolvedUiTransitionsAndMarkCompleted(
                        defaultIssueContentGroup,
                        resolvedIssueContentGroup,
                        resolvedImageView
                    )
                }
            }
        )
    }

    private fun makeInvisibleIfVisible(view: View?) {
        if (view != null && view.visibility == View.VISIBLE) {
            view.visibility = View.INVISIBLE
        }
    }

    private fun startIssueResolvedAnimation(
        resolvedIssueContentGroup: View,
        resolvedImageView: ImageView
    ) {
        val animatedDrawable = resolvedImageView.drawable as AnimatedVectorDrawable
        animatedDrawable.reset()
        animatedDrawable.clearAnimationCallbacks()
        animatedDrawable.registerAnimationCallback(
            object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable) {
                    super.onAnimationEnd(drawable)
                    transitionResolvedIssueUiToHiddenAndMarkComplete(resolvedIssueContentGroup)
                }
            }
        )
        animatedDrawable.start()
    }

    private fun transitionResolvedIssueUiToHiddenAndMarkComplete(resolvedIssueContentGroup: View) {
        val hideTransition =
            hideResolvedUiTransition
                .clone()
                .setInterpolator(linearInterpolator)
                .addListener(
                    object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: Transition) {
                            super.onTransitionEnd(transition)
                            callback.markIssueResolvedUiCompleted()
                        }
                    }
                )
        TransitionManager.beginDelayedTransition(
            resolvedIssueContentGroup.parent as ViewGroup,
            hideTransition
        )
        resolvedIssueContentGroup.visibility = View.GONE
    }

    private fun cancelIssueResolvedUiTransitionsAndMarkCompleted(
        defaultIssueContentGroup: View,
        resolvedIssueContentGroup: View,
        resolvedImageView: ImageView
    ) {
        // Cancel any in flight initial fade (in and out) transitions
        TransitionManager.endTransitions(defaultIssueContentGroup.parent as ViewGroup)

        // Cancel any in flight resolved image animations
        val animatedDrawable = resolvedImageView.drawable as AnimatedVectorDrawable
        animatedDrawable.clearAnimationCallbacks()
        animatedDrawable.stop()

        // Cancel any in flight fade out transitions
        TransitionManager.endTransitions(resolvedIssueContentGroup.parent as ViewGroup)
        callback.markIssueResolvedUiCompleted()
    }

    interface AnimationCallback {
        fun markIssueResolvedUiCompleted()
    }

    companion object {
        /**
         * Device config property for time in milliseconds to increase
         * HIDE_RESOLVED_UI_TRANSITION_DELAY for use in testing.
         */
        private const val PROPERTY_HIDE_RESOLVED_UI_TRANSITION_DELAY_MILLIS =
            "safety_center_hide_resolved_ui_transition_delay_millis"

        private val HIDE_ISSUE_CONTENT_TRANSITION_DURATION = Duration.ofMillis(333)
        private val SHOW_RESOLVED_TEXT_TRANSITION_DELAY = Duration.ofMillis(133)
        private val SHOW_RESOLVED_TEXT_TRANSITION_DURATION = Duration.ofMillis(250)
        private val HIDE_RESOLVED_UI_TRANSITION_DURATION = Duration.ofMillis(167)

        // Using getter due to reliance on DeviceConfig property modification in tests
        private val hideResolvedUiTransitionDelay
            get() =
                Duration.ofMillis(
                    DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PROPERTY_HIDE_RESOLVED_UI_TRANSITION_DELAY_MILLIS,
                        400
                    )
                )

        private val linearInterpolator = LinearInterpolator()

        private val hideIssueContentTransition =
            Fade(Fade.OUT).setDuration(HIDE_ISSUE_CONTENT_TRANSITION_DURATION.toMillis())

        private val showResolvedImageTransition =
            Fade(Fade.IN)
                // Fade is used for visibility transformation. Image to be shown immediately
                .setDuration(0)
                .addTarget(R.id.resolved_issue_image)

        private val showResolvedTextTransition =
            Fade(Fade.IN)
                .setStartDelay(SHOW_RESOLVED_TEXT_TRANSITION_DELAY.toMillis())
                .setDuration(SHOW_RESOLVED_TEXT_TRANSITION_DURATION.toMillis())
                .addTarget(R.id.resolved_issue_text)

        private val hideResolvedUiTransition
            get() =
                Fade(Fade.OUT)
                    .setStartDelay(hideResolvedUiTransitionDelay.toMillis())
                    .setDuration(HIDE_RESOLVED_UI_TRANSITION_DURATION.toMillis())
    }
}
