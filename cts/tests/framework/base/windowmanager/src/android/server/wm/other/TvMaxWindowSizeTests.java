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

package android.server.wm.other;


import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_LEANBACK_ONLY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app30.Components.SDK_30_TEST_ACTIVITY;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import static java.util.Objects.requireNonNull;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CommandSession;
import android.server.wm.WaitForValidActivityState;
import android.view.Display;

import org.junit.Before;
import org.junit.Test;

/**
 * The goal of this test is to make sure that on Android TV applications with target SDK version
 * lower than S do not get a larger than 1080p (1920x1080) Window.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceOther:TvMaxWindowSizeTests
 */
public class TvMaxWindowSizeTests extends ActivityManagerTestBase {

    private int mDisplayLongestWidth;
    private int mDisplayShortestWidth;

    @Before
    public void setUp() {
        // We only need to run this on TV.
        final PackageManager pm = mInstrumentation.getContext().getPackageManager();
        final boolean isTv = pm.hasSystemFeature(FEATURE_LEANBACK) ||
                pm.hasSystemFeature(FEATURE_LEANBACK_ONLY);
        assumeTrue(isTv);

        // Get the real size of the display.
        final DisplayManager dm = mInstrumentation.getContext()
                .getSystemService(DisplayManager.class);
        requireNonNull(dm);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        assumeNotNull(display);
        final Point displaySize = new Point();
        display.getRealSize(displaySize);
        mDisplayLongestWidth = Math.max(displaySize.x, displaySize.y);
        mDisplayShortestWidth = Math.min(displaySize.x, displaySize.y);
    }

    @Test
    public void test_preSApplication_1080p_windowSizeCap() {
        // Only run this if the resolution is over 1080p (at least on one side).
        assumeFalse(mDisplayLongestWidth <= 1920 && mDisplayShortestWidth <= 1080);

        final CommandSession.SizeInfo sizeInfo = launchAndGetReportedSizes(SDK_30_TEST_ACTIVITY);

        final int longestWidth = Math.max(sizeInfo.windowAppWidth, sizeInfo.windowAppHeight);
        final int shortestWidth = Math.min(sizeInfo.windowAppWidth, sizeInfo.windowAppHeight);

        assertThat(longestWidth, lessThanOrEqualTo(1920));
        assertThat(shortestWidth, lessThanOrEqualTo(1080));
    }

    @Test
    public void test_windowSize_notLargerThan_displaySize() {
        final CommandSession.SizeInfo sizeInfo = launchAndGetReportedSizes(TEST_ACTIVITY);

        final int longestWidth = Math.max(sizeInfo.windowAppWidth, sizeInfo.windowAppHeight);
        final int shortestWidth = Math.min(sizeInfo.windowAppWidth, sizeInfo.windowAppHeight);

        assertThat(longestWidth, lessThanOrEqualTo(mDisplayLongestWidth));
        assertThat(shortestWidth, lessThanOrEqualTo(mDisplayShortestWidth));
    }

    private CommandSession.SizeInfo launchAndGetReportedSizes(ComponentName componentName) {
        startActivityOnDisplay(Display.DEFAULT_DISPLAY, componentName);
        mWmState.computeState(new WaitForValidActivityState(componentName));
        final CommandSession.SizeInfo sizeInfo = getLastReportedSizesForActivity(componentName);
        assertNotNull(sizeInfo);
        return sizeInfo;
    }
}
