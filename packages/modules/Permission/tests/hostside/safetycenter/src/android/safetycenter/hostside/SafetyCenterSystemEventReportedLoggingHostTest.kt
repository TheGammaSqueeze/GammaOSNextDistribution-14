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

package android.safetycenter.hostside

import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.ReportUtils
import android.safetycenter.hostside.rules.HelperAppRule
import android.safetycenter.hostside.rules.RequireSafetyCenterRule
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetyCenterSystemEventReported.EventType
import com.android.os.AtomsProto.SafetyCenterSystemEventReported.Result
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class SafetyCenterSystemEventReportedLoggingHostTest : BaseHostJUnit4Test() {

    @get:Rule(order = 1) val safetyCenterRule = RequireSafetyCenterRule(this)
    @get:Rule(order = 2)
    val helperAppRule = HelperAppRule(this, HelperApp.APK_NAME, HelperApp.PACKAGE_NAME)

    @Before
    fun setUp() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        ConfigUtils.uploadConfigForPushedAtom(
            device,
            safetyCenterRule.getSafetyCenterPackageName(),
            Atom.SAFETY_CENTER_SYSTEM_EVENT_REPORTED_FIELD_NUMBER
        )
        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        ReportUtils.clearReports(device)
        ConfigUtils.removeConfig(device)
    }

    @Test
    fun refreshAllSources_allSourcesSuccessful_successAtomsForEachSourceAndOverallCompletion() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_allSuccessful"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertThat(systemEventAtoms.groupingBy { it.eventType }.eachCount())
            .containsExactly(
                EventType.COMPLETE_GET_NEW_DATA,
                1, // Overall refresh complete
                EventType.SINGLE_SOURCE_GET_NEW_DATA,
                3 // For the three sources in the multiple sources test config
            )
        assertThat(systemEventAtoms.groupingBy { it.result }.eachCount())
            .containsExactly(Result.SUCCESS, 4)
    }

    @Test
    fun refreshAllSources_oneSourceError_errorAtomForThatSourceAndOverall() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_oneError"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertThat(systemEventAtoms.groupingBy { Pair(it.eventType, it.result) }.eachCount())
            .containsExactly(
                Pair(EventType.COMPLETE_GET_NEW_DATA, Result.ERROR),
                1, // Overall refresh is an error
                Pair(EventType.SINGLE_SOURCE_GET_NEW_DATA, Result.ERROR),
                1, // For the source which had an error
                Pair(EventType.SINGLE_SOURCE_GET_NEW_DATA, Result.SUCCESS),
                2 // The remaining two sources
            )
    }

    @Test
    fun refreshAllSources_anyResult_atomsIncludeRefreshReason() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_oneSuccessOneErrorOneTimeout"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertWithMessage("the system event atoms").that(systemEventAtoms).hasSize(4)
        assertWithMessage("the number of atoms with the page-open refresh reason")
            .that(systemEventAtoms.count { it.refreshReason == REFRESH_REASON_PAGE_OPEN })
            .isEqualTo(4)
    }

    @Test
    fun refreshAllSources_differentRefreshReason_atomsIncludeCorrectRefreshReason() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonButtonClick_oneSuccessOneErrorOneTimeout"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertWithMessage("the system event atoms").that(systemEventAtoms).hasSize(4)
        assertWithMessage("the number of atoms with the button-click refresh reason")
            .that(systemEventAtoms.count { it.refreshReason == REFRESH_REASON_BUTTON_CLICK })
    }

    @Test
    fun refreshAllSources_firstTime_allSourcesSuccessful_dataChangedTrueForAll() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_allSuccessful"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertWithMessage("the number of atoms with dataChanged=true")
            .that(systemEventAtoms.count { it.dataChanged })
            .isEqualTo(4)
    }

    @Test
    fun refreshAllSources_secondTime_allSourcesUnchanged_dataChangedFalseForAll() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_twiceSameData_allSuccessful"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        // There are three sources in the multiple sources config, of which one is not allowed to
        // refresh on page open, except when there is no data. Plus, for each refresh there is an
        // overall refresh atom making seven atoms in total.
        assertWithMessage("the number of atoms").that(systemEventAtoms).hasSize(7)
        assertWithMessage("the number of atoms with dataChanged=false")
            .that(systemEventAtoms.count { !it.dataChanged })
            .isEqualTo(3)
    }

    @Test
    fun refreshAllSources_secondTime_someSourcesChanged_dataChangedCorrect() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_twiceDifferentData_onlySource1Unchanged"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertWithMessage("the number of atoms with dataChanged=false")
            .that(systemEventAtoms.count { !it.dataChanged })
            .isEqualTo(1) // Only source 1
    }

    @Test
    fun resolveAction_success_resolvingActionSuccessEvent() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "resolvingAction_success"
        )

        val resolvingActionEvent =
            ReportUtils.getEventMetricDataList(device)
                .mapNotNull { it.atom.safetyCenterSystemEventReported }
                .single { it.eventType == EventType.INLINE_ACTION }

        assertThat(resolvingActionEvent.result).isEqualTo(Result.SUCCESS)
        assertThat(resolvingActionEvent.encodedIssueTypeId).isNotEqualTo(0)
    }

    @Test
    fun resolveAction_error_resolvingActionErrorEvent() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "resolvingAction_error"
        )

        val resolvingActionEvent =
            ReportUtils.getEventMetricDataList(device)
                .mapNotNull { it.atom.safetyCenterSystemEventReported }
                .single { it.eventType == EventType.INLINE_ACTION }

        assertThat(resolvingActionEvent.result).isEqualTo(Result.ERROR)
        assertThat(resolvingActionEvent.encodedIssueTypeId).isNotEqualTo(0)
    }

    companion object {
        private const val REFRESH_REASON_PAGE_OPEN = 100L
        private const val REFRESH_REASON_BUTTON_CLICK = 200L
    }
}
