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

package android.bluetooth

import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import com.android.os.AtomsProto
import com.android.os.AtomsProto.BluetoothEnabledStateChanged
import com.android.os.StatsLog
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class MetricsTest : BaseHostJUnit4Test() {

    companion object {
        private const val TAG = "BluetoothMetricsTests"
        private const val TEST_APP_PKG_NAME = "android.bluetooth"
        private const val TEST_APP_CLASS_NAME = ".BluetoothMetricsHelperTest"
    }

    @Before
    fun setUp() {
        ConfigUtils.removeConfig(getDevice())
        ReportUtils.clearReports(getDevice())
    }

    @Test
    fun testBluetoothDisableEnable_shouldProduceEnabledStateChanged() {
        val data =
            uploadAtomConfigAndTriggerTest(
                "testBluetoothDisableEnable",
                intArrayOf(AtomsProto.Atom.BLUETOOTH_ENABLED_STATE_CHANGED_FIELD_NUMBER)
            )
        // First atom might be the setup one.
        val offset =
            data[0].atom.bluetoothEnabledStateChanged.let {
                if (it.state == BluetoothEnabledStateChanged.State.ENABLED) {
                    1
                } else {
                    0
                }
            }
        data[offset].atom.bluetoothEnabledStateChanged.apply {
            assertThat(state).isEqualTo(BluetoothEnabledStateChanged.State.DISABLED)
            assertThat(previousState).isEqualTo(BluetoothEnabledStateChanged.State.ENABLED)
            assertThat(timeSinceLastChangedMillis).isGreaterThan(Duration.ofMillis(1).toMillis())
        }
        data[offset + 1].atom.bluetoothEnabledStateChanged.apply {
            assertThat(state).isEqualTo(BluetoothEnabledStateChanged.State.ENABLED)
            assertThat(previousState).isEqualTo(BluetoothEnabledStateChanged.State.DISABLED)
            assertThat(timeSinceLastChangedMillis).isGreaterThan(Duration.ofMillis(1).toMillis())
        }
    }

    private fun uploadAtomConfigAndTriggerTest(
        testName: String,
        atoms: IntArray
    ): List<StatsLog.EventMetricData> {
        ConfigUtils.uploadConfigForPushedAtoms(device, TEST_APP_PKG_NAME, atoms)

        DeviceUtils.runDeviceTests(device, TEST_APP_PKG_NAME, TEST_APP_CLASS_NAME, testName)

        return ReportUtils.getEventMetricDataList(device)
    }
}
