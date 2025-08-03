/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Android Unit tests for {@link UiActionLatencyHelper}.
 *
 * <p>To run: Disable SELinux: adb shell setenforce 0; if this fails with "permission denied", try
 * "adb shell su 0 setenforce 0" atest
 * CollectorsHelperTest:com.android.helpers.UiActionLatencyHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class UiActionLatencyHelperTest {
    private static final String LOG_TAG = UiActionLatencyHelperTest.class.getSimpleName();

    // Keycode for pressing the home button.
    private static final String KEYCODE_HOME = "KEYCODE_HOME";

    private UiActionLatencyHelper mActionLatencyHelper;

    @Before
    public void setUp() {
        mActionLatencyHelper = new UiActionLatencyHelper();
        HelperTestUtility.wakeUpAndUnlock();
    }

    /** Test successful latency helper config. */
    @Test
    public void testConfig() throws Exception {
        assertTrue(mActionLatencyHelper.startCollecting());
        assertTrue(mActionLatencyHelper.stopCollecting());
    }

    /** Test no error is thrown if there is no CUJ. */
    @Test
    public void testEmptyMetric() throws Exception {
        assertTrue(mActionLatencyHelper.startCollecting());
        assertTrue(mActionLatencyHelper.getMetrics().isEmpty());
        assertTrue(mActionLatencyHelper.stopCollecting());
    }

    /** Test that shade quick switch metric is collected. Enable after b/173623876 is fixed */
    @Test
    public void testQuickSwitchMetric() throws Exception {
        final LauncherInstrumentation sLauncher = new LauncherInstrumentation();

        // Lock the orientation.
        HelperTestUtility.setOrientationNatural();

        startApp(sLauncher, "Chrome", "com.android.chrome");
        startApp(sLauncher, "Calculator", "com.google.android.calculator");

        assertTrue(mActionLatencyHelper.startCollecting());
        Log.d(LOG_TAG, "testQuickSwitchMetric: started collecting");

        sLauncher.getLaunchedAppState().quickSwitchToPreviousApp();

        // Checking metrics produced by the CUJ.
        final Map<String, StringBuilder> latencyMetrics = mActionLatencyHelper.getMetrics();
        Log.d(
                LOG_TAG,
                "testQuickSwitchMetric: got metrics: " + String.join(",", latencyMetrics.keySet()));
        assertTrue(
                "No metric latency_ACTION_TOGGLE_RECENTS",
                latencyMetrics.containsKey("latency_ACTION_TOGGLE_RECENTS"));

        assertTrue(mActionLatencyHelper.stopCollecting());
        HelperTestUtility.sendKeyCode(KEYCODE_HOME);
        SystemClock.sleep(HelperTestUtility.ACTION_DELAY);

        // Unlock the orientation
        HelperTestUtility.unfreezeRotation();

    }

    private void startApp(LauncherInstrumentation sLauncher, String appName, String appPackage) {
        final AllApps allApps = sLauncher.goHome().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon(appName).launch(appPackage);
        } finally {
            allApps.unfreeze();
        }
    }
}
