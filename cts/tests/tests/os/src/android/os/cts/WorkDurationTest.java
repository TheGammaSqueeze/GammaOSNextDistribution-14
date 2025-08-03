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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Flags;
import android.os.WorkDuration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WorkDurationTest {
    private static final String TAG = "WorkDurationTest";

    // Required for RequiresFlagsEnabled and RequiresFlagsDisabled annotations to take effect.
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ADPF_GPU_REPORT_ACTUAL_WORK_DURATION)
    public void testSetters() {
        WorkDuration workDuration = new WorkDuration();
        workDuration.setWorkPeriodStartTimestampNanos(1);
        assertEquals(1, workDuration.getWorkPeriodStartTimestampNanos());
        workDuration.setActualTotalDurationNanos(18);
        assertEquals(18, workDuration.getActualTotalDurationNanos());
        workDuration.setActualCpuDurationNanos(16);
        assertEquals(16, workDuration.getActualCpuDurationNanos());
        workDuration.setActualGpuDurationNanos(6);
        assertEquals(6, workDuration.getActualGpuDurationNanos());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ADPF_GPU_REPORT_ACTUAL_WORK_DURATION)
    public void testSetters_IllegalArgument() {
        WorkDuration workDuration = new WorkDuration();
        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setWorkPeriodStartTimestampNanos(-1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setWorkPeriodStartTimestampNanos(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setActualTotalDurationNanos(-1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setActualTotalDurationNanos(0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setActualCpuDurationNanos(-1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            workDuration.setActualGpuDurationNanos(-1);
        });
    }
}
