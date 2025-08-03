/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.map;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapSettingsTest {

    Context mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent mIntent;

    ActivityScenario<BluetoothMapSettings> mActivityScenario;

    @Before
    public void setUp() throws Exception {
        enableActivity(true);
        TestUtils.setUpUiTest();
        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothMapSettings.class);
        mActivityScenario = ActivityScenario.launch(mIntent);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
        enableActivity(false);
    }

    @Test
    @FlakyTest
    public void initialize() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        long timeoutMs = 5_000;
        String activityLabel = mTargetContext.getString(R.string.bluetooth_map_settings_title);
        UiObject2 object = device.wait(Until.findObject(By.text(activityLabel)), timeoutMs);
        assertThat(object).isNotNull();

        object.click();

        onView(withId(R.id.bluetooth_map_settings_list_view)).check(matches(isDisplayed()));
    }

    private void enableActivity(boolean enable) {
        int enabledState = enable ? COMPONENT_ENABLED_STATE_ENABLED
                : COMPONENT_ENABLED_STATE_DEFAULT;

        mTargetContext.getPackageManager().setApplicationEnabledSetting(
                mTargetContext.getPackageName(), enabledState, DONT_KILL_APP);

        ComponentName activityName = new ComponentName(mTargetContext, BluetoothMapSettings.class);
        mTargetContext.getPackageManager().setComponentEnabledSetting(
                activityName, enabledState, DONT_KILL_APP);
    }
}
