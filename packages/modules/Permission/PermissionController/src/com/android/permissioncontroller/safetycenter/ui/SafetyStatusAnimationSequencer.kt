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

package com.android.permissioncontroller.safetycenter.ui

import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN

/**
 * Controls the animation flow and hold all the data necessary to determine the appearance of Status
 * icon of [SafetyStatusPreference]. For each lifecycle event (such as [onUpdateReceived],
 * [onStartScanningAnimationStart], [onStartScanningAnimationEnd], etc.) it changes its internal
 * state and may provide a presentation instruction in the form of [Action].
 */
class SafetyStatusAnimationSequencer {

    private var isIconChangeAnimationRunning: Boolean = false
    private var isScanAnimationRunning: Boolean = false
    private var shouldStartScanAnimation: Boolean = false
    private var queuedIconChangeAnimationSeverityLevel: Int? = null
    /**
     * Stores the last known Severity Level that user could observe as a static status image, as
     * scan animation, or as the beginning state of a changing status animation.
     */
    private var currentlyVisibleSeverityLevel: Int = OVERALL_SEVERITY_LEVEL_UNKNOWN

    fun getCurrentlyVisibleSeverityLevel(): Int {
        return currentlyVisibleSeverityLevel
    }

    fun onUpdateReceived(isRefreshInProgress: Boolean, severityLevel: Int): Action? {
        if (isRefreshInProgress) {
            if (isIconChangeAnimationRunning) {
                shouldStartScanAnimation = true
                return null
            } else if (!isScanAnimationRunning) {
                currentlyVisibleSeverityLevel = severityLevel
                return Action.START_SCANNING_ANIMATION
            }
            // isRefreshInProgress && isScanAnimationRunning && !isIconChangeAnimationRunning
            // Next action needs to wait for onStartScanningAnimationEnd or
            // onContinueScanningAnimationEnd not to break currently running animation.
            return null
        } else {
            val isDifferentSeverityQueued =
                queuedIconChangeAnimationSeverityLevel != null &&
                    queuedIconChangeAnimationSeverityLevel != severityLevel
            val shouldChangeIcon =
                currentlyVisibleSeverityLevel != severityLevel || isDifferentSeverityQueued

            if (isIconChangeAnimationRunning || shouldChangeIcon && isScanAnimationRunning) {
                queuedIconChangeAnimationSeverityLevel = severityLevel
            }
            if (isScanAnimationRunning) {
                return Action.FINISH_SCANNING_ANIMATION
            } else if (shouldChangeIcon && !isIconChangeAnimationRunning) {
                return Action.START_ICON_CHANGE_ANIMATION
            } else if (!isIconChangeAnimationRunning) {
                // Possible if status was finalized by Safety Center at the beginning,
                // when no scanning animation is launched and refresh is not in progress.
                // In this case we need to show the final icon straigt away without any animations.
                return Action.CHANGE_ICON_WITHOUT_ANIMATION
            }
            // !isRefreshInProgress && !isScanAnimationRunning && isIconChangeAnimationRunning
            // Next action needs to wait for onIconChangeAnimationEnd not to break currently
            // running animation.
            return null
        }
    }

    fun onStartScanningAnimationStart() {
        isScanAnimationRunning = true
    }

    fun onStartScanningAnimationEnd(): Action {
        return Action.CONTINUE_SCANNING_ANIMATION
    }

    fun onContinueScanningAnimationEnd(isRefreshInProgress: Boolean, severityLevel: Int): Action? {
        if (isRefreshInProgress) {
            if (currentlyVisibleSeverityLevel != severityLevel) {
                // onUpdateReceived does not handle this case since we should not break
                // the animation while it is running. Once current scan cycle is finished, this
                // call will return the request to restart animation with updated severity level.
                currentlyVisibleSeverityLevel = severityLevel
                return Action.RESET_SCANNING_ANIMATION
            } else {
                return Action.CONTINUE_SCANNING_ANIMATION
            }
        } else {
            // Possible if scanning animation has been ended right after status is updated with
            // final data, but before we got the onUpdateReceived call (that is posted to the
            // message queue and will happen soon), so no need to do anything right now.
            return null
        }
    }

    fun onFinishScanAnimationEnd(isRefreshing: Boolean, severityLevel: Int): Action {
        isScanAnimationRunning = false
        currentlyVisibleSeverityLevel = severityLevel
        return handleQueuedAction(isRefreshing, severityLevel)
    }

    fun onCouldNotStartIconChangeAnimation(isRefreshing: Boolean, severityLevel: Int): Action {
        return handleQueuedAction(isRefreshing, severityLevel)
    }

    fun onIconChangeAnimationStart() {
        isIconChangeAnimationRunning = true
    }

    fun onIconChangeAnimationEnd(isRefreshing: Boolean, severityLevel: Int): Action {
        isIconChangeAnimationRunning = false
        currentlyVisibleSeverityLevel = severityLevel
        return handleQueuedAction(isRefreshing, severityLevel)
    }

    private fun handleQueuedAction(isRefreshing: Boolean, severityLevel: Int): Action {
        if (shouldStartScanAnimation) {
            shouldStartScanAnimation = false
            if (isRefreshing) {
                return Action.START_SCANNING_ANIMATION
            } else {
                return handleQueuedAction(isRefreshing, severityLevel)
            }
        } else if (queuedIconChangeAnimationSeverityLevel != null) {
            val queuedSeverityLevel = queuedIconChangeAnimationSeverityLevel
            queuedIconChangeAnimationSeverityLevel = null
            if (currentlyVisibleSeverityLevel != queuedSeverityLevel) {
                return Action.START_ICON_CHANGE_ANIMATION
            } else {
                return handleQueuedAction(isRefreshing, severityLevel)
            }
        }
        currentlyVisibleSeverityLevel = severityLevel
        return Action.CHANGE_ICON_WITHOUT_ANIMATION
    }

    /** Set of instructions of what should Status icon currently show. */
    enum class Action {
        START_SCANNING_ANIMATION,
        /**
         * Requests to continue the scanning animation with the same Severity Level as stored in
         * [currentlyVisibleSeverityLevel].
         */
        CONTINUE_SCANNING_ANIMATION,
        /**
         * Requests to start scanning animation from the beginning when
         * [currentlyVisibleSeverityLevel] has been changed.
         */
        RESET_SCANNING_ANIMATION,
        FINISH_SCANNING_ANIMATION,
        START_ICON_CHANGE_ANIMATION,
        CHANGE_ICON_WITHOUT_ANIMATION
    }
}
