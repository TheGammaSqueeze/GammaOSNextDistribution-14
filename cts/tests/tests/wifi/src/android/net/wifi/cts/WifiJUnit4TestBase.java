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

package android.net.wifi.cts;

import android.content.Context;
import android.location.LocationManager;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Base test for Wifi JUnit4 tests that enables/disables location
 */
public abstract class WifiJUnit4TestBase {

    private static LocationManager sLocationManager;
    private static boolean sWasLocationEnabledForTest = false;

    @BeforeClass
    public static void enableLocationIfNotEnabled() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        sLocationManager = context.getSystemService(LocationManager.class);
        if (!sLocationManager.isLocationEnabled()) {
            // Turn on location if it isn't on already
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sLocationManager.setLocationEnabledForUser(
                            true, UserHandle.getUserHandleForUid(Process.myUid())));

            sWasLocationEnabledForTest = true;
        }
    }

    @AfterClass
    public static void disableLocationIfOriginallyDisabled() throws Exception {
        if (sWasLocationEnabledForTest) {
            ShellIdentityUtils.invokeWithShellPermissions(() ->
                    sLocationManager.setLocationEnabledForUser(
                            false, UserHandle.getUserHandleForUid(Process.myUid())));
        }
    }
}
