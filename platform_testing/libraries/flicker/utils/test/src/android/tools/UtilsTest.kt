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

package android.tools

import android.tools.io.TraceType
import android.tools.traces.NullableDeviceStateDump
import android.tools.traces.getCurrentState
import android.tools.traces.getCurrentStateDumpNullable
import android.tools.utils.CleanFlickerEnvironmentRule
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [android.os.traces] utils tests. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UtilsTest {
    private fun getCurrState(
        vararg dumpTypes: TraceType = arrayOf(TraceType.SF_DUMP, TraceType.WM_DUMP)
    ): Pair<ByteArray, ByteArray> {
        return getCurrentState(*dumpTypes)
    }

    private fun getCurrStateDump(
        vararg dumpTypes: TraceType = arrayOf(TraceType.SF_DUMP, TraceType.WM_DUMP)
    ): NullableDeviceStateDump {
        return getCurrentStateDumpNullable(*dumpTypes, clearCacheAfterParsing = false)
    }

    @Test
    fun canFetchCurrentDeviceState() {
        val currState = this.getCurrState()
        Truth.assertThat(currState.first).isNotEmpty()
        Truth.assertThat(currState.second).isNotEmpty()
    }

    @Test
    fun canFetchCurrentDeviceStateOnlyWm() {
        val currStateDump = this.getCurrState(TraceType.WM_DUMP)
        Truth.assertThat(currStateDump.first).isNotEmpty()
        Truth.assertThat(currStateDump.second).isEmpty()
        val currState = this.getCurrStateDump(TraceType.WM_DUMP)
        Truth.assertThat(currState.wmState).isNotNull()
        Truth.assertThat(currState.layerState).isNull()
    }

    @Test
    fun canFetchCurrentDeviceStateOnlyLayers() {
        val currStateDump = this.getCurrState(TraceType.SF_DUMP)
        Truth.assertThat(currStateDump.first).isEmpty()
        Truth.assertThat(currStateDump.second).isNotEmpty()
        val currState = this.getCurrStateDump(TraceType.SF_DUMP)
        Truth.assertThat(currState.wmState).isNull()
        Truth.assertThat(currState.layerState).isNotNull()
    }

    @Test
    fun canParseCurrentDeviceState() {
        val currState = this.getCurrStateDump()
        val wmArray = currState.wmState?.asTrace()?.entries ?: emptyList()
        Truth.assertThat(wmArray).hasSize(1)
        Truth.assertThat(wmArray.first().windowStates).isNotEmpty()
        val layersArray = currState.layerState?.asTrace()?.entries ?: emptyList()
        Truth.assertThat(layersArray).hasSize(1)
        Truth.assertThat(layersArray.first().flattenedLayers).isNotEmpty()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
