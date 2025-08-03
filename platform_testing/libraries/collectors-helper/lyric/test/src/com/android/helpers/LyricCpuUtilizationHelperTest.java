/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.helpers;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Android unit test for {@link LyricCpuUtilizationHelper}
 *
 * <p>To run: atest CollectorsHelperTest:com.android.helpers.tests.LyricCpuUtilizationHelper
 */
@RunWith(AndroidJUnit4.class)
public class LyricCpuUtilizationHelperTest {

    private static final String METRIC_KEY = "cpu_util_%s_%s";

    private static final Double METRIC_VALUE_THRESHOLD = 0.00001;

    @Test
    public void testProcessOutput() {

        String testString =
                "[>] p2 retiming:empty_group after 2994 invocations\n"
                    + "  System CPU: 222.601us (Max: 1.819ms Min: 0)\n"
                    + "  User CPU: 698.4612us (Max: 2.023ms Min: 0)\n"
                    + "  Wall Time: 50.36290575ms (Max: 61.2205ms Min: 50.035889ms)\n"
                    + "  Processing frequency 19.7906(Hz), Duty cycle: 99.671%\n"
                    + "  Average Graph Runner Scheduling Delay is 132.352us (Max: 10.517701ms Min:"
                    + " 20.956us)  with 0 of it due to waiting for a particular"
                    + " thread/reserialization\n"
                    + "  Spent 98.1711% of each ProcessInput (Mean duration: 50.36290575ms Max:"
                    + " 61.2205ms Min: 50.035889ms) waiting for something (or on overhead).\n"
                    + "  On average per process call, there were 0 (Max: 0 Min: 0) "
                    + " reserializations into another queue and 0.999664 (Max: 1 Min: 0)  wakes"
                    + " and 0.830996 involuntary and 12.9198 voluntary context switches"
                    + " (+GraphRunner overhead).";

        Map<String, Double> metrics = LyricCpuUtilizationHelper.processOutput(testString);

        String node = "retiming-empty_group";
        assertThat(metrics.get(String.format(METRIC_KEY, node, "number_of_invocations")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(2994);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "user_time")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(0.6984612);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "user_time")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(0.6984612);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "user_time_max")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(2.023);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "user_time_min")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(0);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "system_time")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(0.222601);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "system_time_max")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(1.819);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "system_time_min")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(0);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "wall_time")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(50.36290575);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "wall_time_max")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(61.2205);
        assertThat(metrics.get(String.format(METRIC_KEY, node, "wall_time_min")))
                .isWithin(METRIC_VALUE_THRESHOLD)
                .of(50.035889);
    }
}
