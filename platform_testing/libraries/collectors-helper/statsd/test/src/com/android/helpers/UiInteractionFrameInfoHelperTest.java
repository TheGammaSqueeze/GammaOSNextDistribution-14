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

import android.app.Application;
import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.INotificationHelper;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject2;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Android Unit tests for {@link UiInteractionFrameInfoHelper}.
 *
 * <p>To run: Disable SELinux: adb shell setenforce 0; if this fails with "permission denied", try
 * "adb shell su 0 setenforce 0" atest
 * CollectorsHelperTest:com.android.helpers.UiInteractionFrameInfoHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class UiInteractionFrameInfoHelperTest {

    // Keycode for pressing the home button.
    private static final String KEYCODE_HOME = "KEYCODE_HOME";

    private UiInteractionFrameInfoHelper mInteractionFrameHelper;

    @Before
    public void setUp() {
        mInteractionFrameHelper = new UiInteractionFrameInfoHelper();
        HelperTestUtility.wakeUpAndUnlock();
    }

    /** Test successful frame helper config. */
    @Test
    public void testConfig() throws Exception {
        assertTrue(mInteractionFrameHelper.startCollecting());
        assertTrue(mInteractionFrameHelper.stopCollecting());
    }

    /** Test no error is thrown if there is no CUJ. */
    @Test
    public void testEmptyMetric() throws Exception {
        assertTrue(mInteractionFrameHelper.startCollecting());
        assertTrue(mInteractionFrameHelper.getMetrics().isEmpty());
        assertTrue(mInteractionFrameHelper.stopCollecting());
    }

    /** Test that shade fling metric is collected.. */
    @Test
    public void testShadeFlingMetric() throws Exception {
        assertTrue(mInteractionFrameHelper.startCollecting());
        final HelperAccessor<INotificationHelper> notificationHelper =
                new HelperAccessor<>(INotificationHelper.class);
        notificationHelper.get().setAppName(Application.getProcessName());

        // The CUJ
        notificationHelper.get().open();
        UiObject2 notification = notificationHelper.get().postBigTextNotification(null /* pkg */);
        notificationHelper.get().cancelNotifications();
        notificationHelper.get().exit();

        // Checking metrics produced by the CUJ.
        final Map<String, StringBuilder> frameMetrics = mInteractionFrameHelper.getMetrics();
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_missed_frames",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_missed_frames"));
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_total_frames",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_total_frames"));
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_max_frame_time_ms",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_max_frame_time_ms"));
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_sf_missed_frames",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_sf_missed_frames"));
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_app_missed_frames",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_app_missed_frames"));
        assertTrue(
                "No metric cuj_NOTIFICATION_ADD_max_successive_misses",
                frameMetrics.containsKey("cuj_NOTIFICATION_ADD_max_successive_misses"));

        assertTrue(mInteractionFrameHelper.stopCollecting());
        HelperTestUtility.sendKeyCode(KEYCODE_HOME);
        SystemClock.sleep(HelperTestUtility.ACTION_DELAY);
    }

    @After
    public void tearDown() {
        mInteractionFrameHelper.stopCollecting();
    }
}
