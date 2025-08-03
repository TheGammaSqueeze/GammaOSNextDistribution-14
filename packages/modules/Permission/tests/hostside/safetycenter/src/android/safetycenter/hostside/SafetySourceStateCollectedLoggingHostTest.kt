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

package android.safetycenter.hostside

import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.ReportUtils
import android.safetycenter.hostside.rules.HelperAppRule
import android.safetycenter.hostside.rules.RequireSafetyCenterRule
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetySourceStateCollected
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Host-side tests for Safety Center statsd logging. */
@RunWith(DeviceJUnit4ClassRunner::class)
class SafetySourceStateCollectedLoggingHostTest : BaseHostJUnit4Test() {

    @get:Rule(order = 1) val safetyCenterRule = RequireSafetyCenterRule(this)
    @get:Rule(order = 2)
    val helperAppRule = HelperAppRule(this, HelperApp.APK_NAME, HelperApp.PACKAGE_NAME)

    @Before
    fun setUp() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        val config = ConfigUtils.createConfigBuilder(safetyCenterRule.getSafetyCenterPackageName())
        ConfigUtils.addGaugeMetric(config, Atom.SAFETY_STATE_FIELD_NUMBER)
        ConfigUtils.addEventMetric(config, Atom.SAFETY_SOURCE_STATE_COLLECTED_FIELD_NUMBER)
        ConfigUtils.uploadConfig(device, config)
        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        ReportUtils.clearReports(device)
        ConfigUtils.removeConfig(device)
    }

    @Test
    fun triggerStatsPull_atomsPushedForAllSources() {
        helperAppRule.runTest(TEST_CLASS_NAME, "triggerStatsPull")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        // This assertion purposefully uses containsAtLeast and not containsExact because on test
        // devices with multiple primary users there will be multiple atoms per source.
        assertThat(sourceStateAtoms.map { it.encodedSafetySourceId })
            .containsAtLeast(
                SOURCE_1_ENCODED_SOURCE_ID,
                SOURCE_2_ENCODED_SOURCE_ID,
                SOURCE_3_ENCODED_SOURCE_ID
            )
    }

    @Test
    fun triggerStatsPull_atomsHaveCollectionTypeAutomatic() {
        helperAppRule.runTest(TEST_CLASS_NAME, "triggerStatsPull")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.collectionType }.distinct())
            .containsExactly(SafetySourceStateCollected.CollectionType.AUTOMATIC)
    }

    @Test
    fun setSafetySourceData_atomPushedForThatSource() {
        helperAppRule.runTest(TEST_CLASS_NAME, "setSafetySourceData_source1")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.encodedSafetySourceId })
            .containsExactly(SOURCE_1_ENCODED_SOURCE_ID)
    }

    @Test
    fun setSafetySourceData_atomHasCollectionTypeSourceUpdated() {
        helperAppRule.runTest(TEST_CLASS_NAME, "setSafetySourceData_source1")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.collectionType })
            .containsExactly(SafetySourceStateCollected.CollectionType.SOURCE_UPDATED)
    }

    @Test
    fun reportSafetySourceError_atomPushedForThatSource() {
        helperAppRule.runTest(TEST_CLASS_NAME, "reportSafetySourceError_source1")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.encodedSafetySourceId })
            .containsExactly(SOURCE_1_ENCODED_SOURCE_ID)
        assertThat(sourceStateAtoms.map { it.collectionType })
            .containsExactly(SafetySourceStateCollected.CollectionType.SOURCE_UPDATED)
    }

    private fun getSafetySourceStateCollectedAtoms() =
        ReportUtils.getEventMetricDataList(device)
            .mapNotNull { it.atom.safetySourceStateCollected }
            .filterNot {
                // Installing/uninstalling the helper app can cause Play Protect to run a scan and
                // push new data to Safety Center which interferes with the test results so we
                // specifically filter the resultant atoms out using the real encoded source ID.
                // Similar failures are also observed on Android Test Hub due to the background
                // location source (b/278782808)
                it.encodedSafetySourceId == PLAY_PROTECT_ENCODED_SOURCE_ID ||
                    it.encodedSafetySourceId == BACKGROUND_LOCATION_ENCODED_SOURCE_ID
            }

    private companion object {
        const val TEST_CLASS_NAME = ".SafetySourceStateCollectedLoggingHelperTests"
        const val PLAY_PROTECT_ENCODED_SOURCE_ID = 7711894340233229936L
        const val BACKGROUND_LOCATION_ENCODED_SOURCE_ID = 7355693215512427559L
        const val SOURCE_1_ENCODED_SOURCE_ID = 6446219357586936066L
        const val SOURCE_2_ENCODED_SOURCE_ID = -5887429047684886602L
        const val SOURCE_3_ENCODED_SOURCE_ID = -619470868366498469L
    }
}
