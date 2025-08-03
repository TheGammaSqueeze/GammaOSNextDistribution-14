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

package com.android.pixel.tests;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLaunchRotateTest extends PixelAppCompatTestBase {
    private static final String ROTATE_LANDSCAPE =
            "content insert --uri content://settings/system"
                    + " --bind name:s:user_rotation --bind value:i:1";
    private static final String ROTATE_PORTRAIT =
            "content insert --uri content://settings/system"
                    + " --bind name:s:user_rotation --bind value:i:0";
    private static final int LAUNCH_TIME_MS = 15000; // 15 seconds
    private static final long WAIT_ONE_SECOND_IN_MS = 1000;

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        getUiDevice().unfreezeRotation();
    }

    @Test
    public void testRotateDevice() throws Exception {
        launchAndWaitAppOpen(LAUNCH_TIME_MS);

        // Turn off the automatic rotation
        getUiDevice().freezeRotation();
        getUiDevice().executeShellCommand(ROTATE_PORTRAIT);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "set_portrait_mode");
        Assert.assertTrue(
                "Screen should be in portrait mode", getUiDevice().isNaturalOrientation());

        getUiDevice().executeShellCommand(ROTATE_LANDSCAPE);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "rotate_landscape");
        Assert.assertFalse(
                "Screen should be in landscape mode", getUiDevice().isNaturalOrientation());

        getUiDevice().executeShellCommand(ROTATE_PORTRAIT);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "rotate_portrait");
        Assert.assertTrue(
                "Screen should be in portrait mode", getUiDevice().isNaturalOrientation());
    }
}
