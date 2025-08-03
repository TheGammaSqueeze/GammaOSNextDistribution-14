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

package com.android.permissioncontroller.tests.mocking.safetycenter.ui

import android.os.Build
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.safetycenter.ui.SafetyStatusAnimationSequencer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetyStatusAnimationSequencerTest {

    private val sequencer: SafetyStatusAnimationSequencer = SafetyStatusAnimationSequencer()

    @Test
    fun getCurrentlyVisibleSeverityLevel_returnsUnknown() {
        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_refreshingWithCriticalSeverity_returnsCritical() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onStartScanningAnimationStart()

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_severityChangedToCriticalWhileRefreshing_returnsUnknown() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_refreshingStartedUnknownStoppedCritical_returnsUnknown() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_scanAnimationEndedAfterStoppedCritical_returnsCritical() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        sequencer.onFinishScanAnimationEnd(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_continueScanEndedRefreshingCritical_returnsCritical() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        sequencer.onContinueScanningAnimationEnd(
            REFRESHING,
            OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
        )

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_continueScanEndedNotRefreshingCritical_returnsUnknown() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        sequencer.onContinueScanningAnimationEnd(
            NOT_REFRESHING,
            OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
        )

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_iconChangeAnimationQueuedCritical_returnsUnknown() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_iconChangeAnimationEndedUnknown_returnsUnknown() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        sequencer.onIconChangeAnimationEnd(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getCurrentlyVisibleSeverityLevel_iconChangeAnimationEndedCritical_returnsCritical() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        sequencer.onIconChangeAnimationEnd(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(sequencer.getCurrentlyVisibleSeverityLevel())
            .isEqualTo(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun onContinueScanningAnimationEnd_whileRefreshing_returnsContinueScanning() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()

        assertThat(
                sequencer.onContinueScanningAnimationEnd(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CONTINUE_SCANNING_ANIMATION)
    }

    @Test
    fun onContinueScanningAnimationEnd_afterRefreshingStoppedUnknown_returnsNull() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)

        assertThat(
                sequencer.onContinueScanningAnimationEnd(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_UNKNOWN
                )
            )
            .isNull()
    }

    @Test
    fun onContinueScanningAnimationEnd_afterRefreshingStoppedCritical_returnsResetScanning() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(
                sequencer.onContinueScanningAnimationEnd(
                    REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.RESET_SCANNING_ANIMATION)
    }

    @Test
    fun onUpdateReceived_whileRefreshing_returnsStartScanning() {
        assertThat(sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN))
            .isEqualTo(SafetyStatusAnimationSequencer.Action.START_SCANNING_ANIMATION)
    }

    @Test
    fun onUpdateReceived_notRefreshingCriticalSeverityLevel_returnsStartIconChangeAnimation() {
        assertThat(
                sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.START_ICON_CHANGE_ANIMATION)
    }

    @Test
    fun onUpdateReceived_notRefreshingUnknownSeverityLevel_returnsChangeIcon() {
        assertThat(sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN))
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    @Test
    fun onUpdateReceived_refreshingWhileIconChangeAnimationRunning_returnsNull() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()

        assertThat(sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING))
            .isNull()
    }

    @Test
    fun onUpdateReceived_UnknownSeverityWhileCriticalIconChangeAnimationRunning_returnsNull() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()

        assertThat(sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN))
            .isNull()
    }

    @Test
    fun onUpdateReceived_whileRefreshingWithSameSeverity_returnsNull() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        assertThat(sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)).isNull()
    }

    @Test
    fun onUpdateReceived_refreshingStoppedWtihSameSeverity_returnsFinishScanningAnimation() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        assertThat(sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN))
            .isEqualTo(SafetyStatusAnimationSequencer.Action.FINISH_SCANNING_ANIMATION)
    }

    @Test
    fun onUpdateReceived_whileRefreshingWithDifferentSeverity_returnsNull() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        assertThat(sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING))
            .isNull()
    }

    @Test
    fun onUpdateReceived_refreshingStoppedWithDifferentSeverity_returnsFinishScanningAnimation() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()

        assertThat(
                sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.FINISH_SCANNING_ANIMATION)
    }

    @Test
    fun onStartScanningAnimationEnd_returnsContinueScanningAnimation() {
        assertThat(sequencer.onStartScanningAnimationEnd())
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CONTINUE_SCANNING_ANIMATION)
    }

    @Test
    fun onFinishScanAnimationEnd_noIconChangeAnimationQueued_returnsChangeIcon() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)

        assertThat(
                sequencer.onFinishScanAnimationEnd(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    @Test
    fun onFinishScanAnimationEnd_iconChangeAnimationQueuedWithSameSeverity_returnsChangeIcon() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(
                sequencer.onFinishScanAnimationEnd(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    @Test
    fun onFinishScanAnimationEnd_iconChangeAnimationQueuedAfterRefreshing_returnsStartIconChange() {
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onStartScanningAnimationStart()
        sequencer.onStartScanningAnimationEnd()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(
                sequencer.onFinishScanAnimationEnd(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.START_ICON_CHANGE_ANIMATION)
    }

    @Test
    fun onIconChangeAnimationEnd_noAnimationQueued_returnsChangeIcon() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()

        assertThat(
                sequencer.onIconChangeAnimationEnd(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    @Test
    fun onIconChangeAnimationEnd_updateReceivedWithDifferentSeverity_returnsStartIconChange() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)

        assertThat(
                sequencer.onIconChangeAnimationEnd(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.START_ICON_CHANGE_ANIMATION)
    }

    @Test
    fun onIconChangeAnimationEnd_updateReceivedWithSameSeverity_returnsChangeIcon() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_UNKNOWN)
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(
                sequencer.onIconChangeAnimationEnd(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    @Test
    fun onIconChangeAnimationEnd_scanAnimationQueued_returnsStartScanningAnimation() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()
        sequencer.onUpdateReceived(REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)

        assertThat(
                sequencer.onIconChangeAnimationEnd(
                    REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.START_SCANNING_ANIMATION)
    }

    @Test
    fun onCouldNotStartIconChangeAnimation_noAnimationQueued_returnsChangeIcon() {
        sequencer.onUpdateReceived(NOT_REFRESHING, OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        sequencer.onIconChangeAnimationStart()

        assertThat(
                sequencer.onCouldNotStartIconChangeAnimation(
                    NOT_REFRESHING,
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
            .isEqualTo(SafetyStatusAnimationSequencer.Action.CHANGE_ICON_WITHOUT_ANIMATION)
    }

    private companion object {
        const val REFRESHING = true
        const val NOT_REFRESHING = false
    }
}
