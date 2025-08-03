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

package android.tare.cts;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.app.tare.EconomyManager;
import android.content.Context;
import android.provider.DeviceConfig;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceConfigStateHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests related to TARE.
 */
@RunWith(AndroidJUnit4.class)
public class EconomyManagerTest {
    private static final String ENABLE_TARE_SETTINGS_KEY = "enable_tare";

    private Context mContext;
    private DeviceConfigStateHelper mDeviceConfigStateHelper;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDeviceConfigStateHelper = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_TARE);
    }

    @After
    public void teardown() {
        mDeviceConfigStateHelper.restoreOriginalValues();
    }

    @CddTest(requirements={"3.5.1/C-2-1"})
    @Test
    public void testDefault() throws Exception {
        // Reset overrides to see what the actual default is.
        mDeviceConfigStateHelper.set(EconomyManager.KEY_ENABLE_TARE_MODE, null);
        Settings.Global.putString(mContext.getContentResolver(), ENABLE_TARE_SETTINGS_KEY, null);

        final EconomyManager economyManager = mContext.getSystemService(EconomyManager.class);
        // Wait a bit in case the device needs some time to process the changes.
        waitUntil("Tare still enabled: " + economyManager.getEnabledMode(), 5 /* seconds */,
                () -> {
                    // TARE shouldn't be on by default. Shadow mode is fine since it doesn't
                    // affect actual
                    // device policy.
                    final int defaultEnabledMode = economyManager.getEnabledMode();
                    return defaultEnabledMode == EconomyManager.ENABLED_MODE_OFF
                            || defaultEnabledMode == EconomyManager.ENABLED_MODE_SHADOW;
                });
    }
}
