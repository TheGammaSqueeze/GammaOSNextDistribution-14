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

package android.os.health.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.os.ConditionVariable;
import android.os.OutcomeReceiver;
import android.os.PowerMonitor;
import android.os.PowerMonitorReadings;
import android.os.health.SystemHealthManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.power.optimization.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PowerMonitorsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private List<PowerMonitor> mPowerMonitorInfo;
    private PowerMonitorReadings mReadings;
    private RuntimeException mException;

    @RequiresFlagsEnabled(Flags.FLAG_POWER_MONITOR_API)
    @Test
    public void testGetPowerMonitorsAsync() {
        SystemHealthManager shm = getContext().getSystemService(SystemHealthManager.class);
        ConditionVariable done = new ConditionVariable();
        shm.getSupportedPowerMonitors(null, pms -> {
            mPowerMonitorInfo = pms;
            done.open();
        });
        done.block();
        assertThat(mPowerMonitorInfo).isNotNull();
        if (mPowerMonitorInfo.isEmpty()) {
            // This device does not support PowerStats HAL
            return;
        }

        PowerMonitor consumerMonitor = null;
        PowerMonitor measurementMonitor = null;
        for (PowerMonitor pmi : mPowerMonitorInfo) {
            if (pmi.getType() == PowerMonitor.POWER_MONITOR_TYPE_MEASUREMENT) {
                measurementMonitor = pmi;
            } else {
                consumerMonitor = pmi;
            }
        }

        List<PowerMonitor> selectedMonitors = new ArrayList<>();
        if (consumerMonitor != null) {
            selectedMonitors.add(consumerMonitor);
        }
        if (measurementMonitor != null) {
            selectedMonitors.add(measurementMonitor);
        }

        done.close();
        shm.getPowerMonitorReadings(selectedMonitors, null, new OutcomeReceiver<>() {
            @Override
            public void onResult(PowerMonitorReadings readings) {
                mReadings = readings;
                done.open();
            }

            @Override
            public void onError(RuntimeException error) {
                mException = error;
                done.open();
            }
        });
        done.block();

        assertThat(mException).isNull();

        for (PowerMonitor monitor : selectedMonitors) {
            assertThat(mReadings.getConsumedEnergy(monitor)).isAtLeast(0);
            assertThat(mReadings.getTimestampMillis(monitor)).isGreaterThan(0);
        }
    }
}
