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

package android.sdksandbox.test.scenario.testapp;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
public class OpenApp {
    private static final UiDevice sUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    /** Returns the bundle containing the instrumentation arguments. */
    private static final Bundle sArgsBundle = InstrumentationRegistry.getArguments();

    private static final String CLIENT_APP_PACKAGE_NAME_KEY = "client-app-package-name";
    private static final String CLIENT_APP_ACTIVITY_NAME_KEY = "client-app-activity-name";

    private static final int WAIT_TIME_BEFORE_CLOSE_APP_MS = 3000;
    protected static String sPackageName;
    private static String sActivityName;

    @AfterClass
    public static void tearDown() throws IOException {
        sUiDevice.executeShellCommand("am force-stop " + sPackageName);
    }

    @BeforeClass
    public static void setup() throws Exception {
        assertThat(sArgsBundle).isNotNull();
        sPackageName = sArgsBundle.getString(CLIENT_APP_PACKAGE_NAME_KEY);
        sActivityName = sArgsBundle.getString(CLIENT_APP_ACTIVITY_NAME_KEY);
        assertThat(sPackageName).isNotNull();
        assertThat(sActivityName).isNotNull();
    }

    @Test
    public void testOpenApp() throws Exception {
        sUiDevice.executeShellCommand("am start " + sPackageName + "/" + sActivityName);
        SystemClock.sleep(WAIT_TIME_BEFORE_CLOSE_APP_MS);
    }
}
