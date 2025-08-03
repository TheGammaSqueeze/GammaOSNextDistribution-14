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

package android.platform.tests;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoHomeHelper;
import android.platform.helpers.IAutoStatusBarHelper;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StatusBarTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private static final String TIME_ZONE = "GMT";
    private HelperAccessor<IAutoHomeHelper> mHomeHelper;
    private HelperAccessor<IAutoStatusBarHelper> mStatusBarHelper;

    public StatusBarTest() {
        mHomeHelper = new HelperAccessor<>(IAutoHomeHelper.class);
        mStatusBarHelper = new HelperAccessor<>(IAutoStatusBarHelper.class);
    }

    @Test
    public void testToverifyDefaultStatusbar() {
        assertTrue("Bluetooth Button is not displayed", mHomeHelper.get().hasBluetoothButton());
        assertTrue("Network Button is not displayed", mHomeHelper.get().hasNetworkButton());
        assertTrue("Brightness Button is not displayed", mHomeHelper.get().hasDisplayBrightness());
    }

    @Test
    public void testToVerifyChangeToDayMode() {
        assertTrue("Unable to change to day mode", mStatusBarHelper.get().changeToDayMode());
        // Constant value is 1 for Night mode no
        assertEquals(1, mStatusBarHelper.get().getCurrentDisplayMode());
    }

    @Test
    public void testToVerifyChangeToNightMode() {
        assertTrue("Unable to change to night mode", mStatusBarHelper.get().changeToNightMode());
        // Constant value is 2 for Night mode yes
        assertEquals(2, mStatusBarHelper.get().getCurrentDisplayMode());
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testClockTime() {
        assertEquals(
                "Clock time in Status bar is not displayed",
                mStatusBarHelper.get().getClockTime(),
                mStatusBarHelper.get().getCurrentTimeWithTimeZone(TIME_ZONE));
    }
}
