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
package com.android.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Android Unit tests for {@link BatteryUsageStatsHelperTest}.
 *
 * <p>To run: Disable SELinux: adb shell setenforce 0; if this fails with "permission denied", try
 * "adb shell su 0 setenforce 0" atest
 * CollectorsHelperTest:com.android.helpers.BatteryUsageStatsHelperTest
 */
@Ignore("b/222159838, the metrics aren't consistently reported.")
@RunWith(AndroidJUnit4.class)
public class BatteryUsageStatsHelperTest {

    private BatteryUsageStatsHelper mBatteryUsageStatsHelper;

    @Before
    public void setUp() {
        mBatteryUsageStatsHelper = new BatteryUsageStatsHelper();
    }

    /** Test config registration and unregistration works. */
    @Test
    public void testBatteryUsageStatsConfig() throws Exception {
        assertTrue(mBatteryUsageStatsHelper.startCollecting());
        assertTrue(mBatteryUsageStatsHelper.stopCollecting());
    }

    /** Test battery usage stats metrics are collected. */
    @Test
    public void testBatteryUsageStatsMetrics() throws Exception {
        assertTrue(mBatteryUsageStatsHelper.startCollecting());
        Map<String, Long> batteryUsageStats = mBatteryUsageStatsHelper.getMetrics();

        boolean hasTotalConsumed = false;
        boolean hasTotalConsumedByComponent = false;
        boolean hasTotalDurationByComponent = false;
        boolean hasPerUidConsumed = false;
        boolean hasPerUidDuration = false;
        boolean hasTimeInFg = false;
        boolean hasTimeInBg = false;
        for (Map.Entry<String, Long> entry : batteryUsageStats.entrySet()) {
            hasTotalConsumed = hasTotalConsumed || entry.getKey().equals("power-consumed-total-dC");
            hasTotalConsumedByComponent =
                    hasTotalConsumedByComponent
                            || Pattern.compile("power-consumed-total-on.*")
                                    .matcher(entry.getKey())
                                    .matches();
            hasTotalDurationByComponent =
                    hasTotalDurationByComponent
                            || Pattern.compile("duration-total-on.*")
                                    .matcher(entry.getKey())
                                    .matches();
            hasPerUidConsumed =
                    hasPerUidConsumed
                            || Pattern.compile("power-consumed-by.*")
                                    .matcher(entry.getKey())
                                    .matches();
            hasPerUidDuration =
                    hasPerUidDuration
                            || Pattern.compile("duration-by.*").matcher(entry.getKey()).matches();
            hasTimeInFg =
                    hasTimeInFg
                            || Pattern.compile("time-in-fg.*").matcher(entry.getKey()).matches();
            hasTimeInBg =
                    hasTimeInBg
                            || Pattern.compile("time-in-bg.*").matcher(entry.getKey()).matches();
        }

        assertTrue(hasTotalConsumed);
        assertTrue(hasTotalConsumedByComponent);
        assertTrue(hasTotalDurationByComponent);
        assertTrue(hasPerUidConsumed);
        assertTrue(hasPerUidDuration);
        assertTrue(hasTimeInFg);
        assertTrue(hasTimeInBg);

        assertTrue(batteryUsageStats.containsKey("session-start-ms"));
        assertTrue(batteryUsageStats.containsKey("session-end-ms"));
        assertTrue(batteryUsageStats.containsKey("session-discharge-pct"));

        assertTrue(mBatteryUsageStatsHelper.stopCollecting());
    }

    /** Test total consumption is the sum of per-package consumption. */
    @Test
    public void testTotalConsumptionIsSumOfPerPackageConsumption() {
        assertTrue(mBatteryUsageStatsHelper.startCollecting());
        Map<String, Long> batteryUsageStats = mBatteryUsageStatsHelper.getMetrics();

        long summedConsumption = 0;
        long reportedConsumption = 0;

        String component = "cpu";
        for (Map.Entry<String, Long> entry : batteryUsageStats.entrySet()) {
            Pattern total = Pattern.compile(String.format("power-consumed-total.*%s.*", component));
            Pattern partial = Pattern.compile(String.format("power.*by.*-on-%s.*", component));
            if (total.matcher(entry.getKey()).matches()) {
                reportedConsumption = entry.getValue();
            } else if (partial.matcher(entry.getKey()).matches()) {
                summedConsumption += entry.getValue();
            }
        }

        assertEquals(
                "Reported consumption and summed consumption don't match.",
                reportedConsumption,
                summedConsumption);
    }

    /** Test total duration is the sum of per-package durations. */
    @Test
    public void testTotalDurationIsSumOfPerPackageDurations() {
        assertTrue(mBatteryUsageStatsHelper.startCollecting());
        Map<String, Long> batteryUsageStats = mBatteryUsageStatsHelper.getMetrics();

        long summedDuration = 0;
        long reportedDuration = 0;

        String component = "cpu";
        for (Map.Entry<String, Long> entry : batteryUsageStats.entrySet()) {
            Pattern total = Pattern.compile(String.format("duration-total.*%s.*", component));
            Pattern partial = Pattern.compile(String.format("duration.*by.*-on-%s.*", component));
            if (total.matcher(entry.getKey()).matches()) {
                reportedDuration = entry.getValue();
            } else if (partial.matcher(entry.getKey()).matches()) {
                summedDuration += entry.getValue();
            }
        }

        assertEquals(
                "Reported duration and summed duration don't match",
                reportedDuration,
                summedDuration);
    }

    @After
    public void tearDown() {
        mBatteryUsageStatsHelper.stopCollecting();
    }
}
