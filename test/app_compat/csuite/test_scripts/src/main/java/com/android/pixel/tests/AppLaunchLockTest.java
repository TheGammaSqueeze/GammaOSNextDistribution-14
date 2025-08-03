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
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLaunchLockTest extends PixelAppCompatTestBase {
    private static final int LAUNCH_TIME_MS = 15000; // 15 seconds
    private static final long WAIT_ONE_SECOND_IN_MS = 1000;
    private static final String DISMISS_KEYGUARD = "wm dismiss-keyguard";

    @Test
    public void testLockDevice() throws Exception {
        launchAndWaitAppOpen(LAUNCH_TIME_MS);

        if (getUiDevice().isScreenOn()) {
            getUiDevice().sleep();
            SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        }
        getDeviceUtils().takeScreenshot(getPackage(), "sleep_device");
        Assert.assertFalse("The screen should be off", getUiDevice().isScreenOn());

        getUiDevice().wakeUp();
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "wake_up_device");
        Assert.assertTrue("The screen should be off", getUiDevice().isScreenOn());
        Assert.assertTrue("The keyguard should show up", getKeyguardManager().isKeyguardLocked());

        getUiDevice().executeShellCommand(DISMISS_KEYGUARD);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "dismiss_keyguard");
        getUiDevice().wait(Until.hasObject(By.pkg(getPackage()).depth(0)), LAUNCH_TIME_MS);
        Assert.assertFalse(
                "The keyguard should be dismissed", getKeyguardManager().isKeyguardLocked());
        Assert.assertTrue(
                "3P app main page should show up after unlocking the screen",
                getUiDevice().hasObject(By.pkg(getPackage()).depth(0)));
    }
}
