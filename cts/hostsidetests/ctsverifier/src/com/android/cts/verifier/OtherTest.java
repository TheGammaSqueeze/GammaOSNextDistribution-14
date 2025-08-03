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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class OtherTest extends CtsVerifierTest {

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = {"2.2.4/8.3/H-1-1", "2.3.4/8.3/T-1-1", "2.4.4/8.3/W-SR", "8.3/C-SR"})
    @ApiTest(apis = "android.os.PowerManager#isPowerSaveMode")
    public void BatterySaverTest() throws Exception {
        excludeFeatures("android.hardware.type.automotive", "android.hardware.type.watch");

        runTest(".battery.BatterySaverTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "8.3/C-1-6")
    @ApiTest(
            apis = {
                "android.os.PowerManager#isIgnoringBatteryOptimizations",
                "android.app.usage.UsageStatsManager#getAppStandbyBucket",
                "android.provider.Settings#ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                "android.provider.Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
            })
    public void IgnoreBatteryOptimizationsTest() throws Exception {
        excludeFeatures("android.hardware.type.automotive", "android.hardware.type.watch");

        runTest(".battery.IgnoreBatteryOptimizationsTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    // (no category)
    public void RecentTaskRemovalTest() throws Exception {
        excludeFeatures("android.hardware.type.automotive");

        runTest(".forcestop.RecentTaskRemovalTestActivity", "config_has_recents");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void WidgetTest() throws Exception {
        // This test should probably be migrated because as is it's unclear
        requireFeatures("android.software.app_widgets");
        excludeFeatures("android.hardware.type.automotive", "android.hardware.ram.low");

        runTest(".widget.WidgetTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ScreenPinningTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".screenpinning.ScreenPinningTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void TtsTest() throws Exception {
        excludeFeatures("android.hardware.type.watch");

        runTest(".speech.tts.TtsTestActivity");
    }
}
