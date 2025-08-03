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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLaunchRecentAppTest extends PixelAppCompatTestBase {
    private static final int WAIT_FIFTEEN_SECONDS_IN_MS = 15000;
    private static final long WAIT_ONE_SECOND_IN_MS = 1000;
    private static final String CLEAR_ALL = "Clear all";
    private static final String NO_RECENT_ITEMS = "No recent items";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        getUiDevice().pressRecentApps();
        getUiDevice()
                .waitForWindowUpdate(
                        getUiDevice().getLauncherPackageName(), WAIT_FIFTEEN_SECONDS_IN_MS);
        getUiDevice().wait(Until.findObject(By.text(NO_RECENT_ITEMS)), WAIT_FIFTEEN_SECONDS_IN_MS);
        if (!getUiDevice().hasObject(By.text(NO_RECENT_ITEMS))) {
            int midY = getUiDevice().getDisplayHeight() / 2;
            int startX = getUiDevice().getDisplayWidth() * 1 / 10;
            int endX = getUiDevice().getDisplayWidth() * 9 / 10;
            for (int i = 0; i < 20; i++) {
                getUiDevice().swipe(startX, midY, endX, midY, (endX - startX) / 100);
                getUiDevice().waitForIdle();
                if (getUiDevice().hasObject(By.text(CLEAR_ALL))) {
                    break;
                }
            }
            if (getUiDevice().hasObject(By.text(CLEAR_ALL))) {
                getUiDevice().findObject(By.text(CLEAR_ALL)).click();
            }
            SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        }
        getDeviceUtils().backToHome(getUiDevice().getLauncherPackageName());
    }

    @Test
    public void testLaunchFromRecentApps() throws Exception {
        launchAndWaitAppOpen(WAIT_FIFTEEN_SECONDS_IN_MS);

        getUiDevice().pressRecentApps();
        getUiDevice().wait(Until.hasObject(By.text("Screenshot")), WAIT_FIFTEEN_SECONDS_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "press_recent_apps_1");
        Assert.assertTrue(
                "3P app should be in background", getUiDevice().hasObject(By.text("Screenshot")));

        getUiDevice().pressRecentApps();
        getUiDevice()
                .wait(Until.hasObject(By.pkg(getPackage()).depth(0)), WAIT_FIFTEEN_SECONDS_IN_MS);
        getDeviceUtils().takeScreenshot(getPackage(), "press_recent_apps_2");
        Assert.assertTrue(
                "3P app main page should be re-launched",
                getUiDevice().hasObject(By.pkg(getPackage()).depth(0)));
    }
}
