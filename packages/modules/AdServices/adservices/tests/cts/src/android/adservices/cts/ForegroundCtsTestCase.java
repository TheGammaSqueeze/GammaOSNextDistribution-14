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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Build;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

abstract class ForegroundCtsTestCase extends CtsAdServicesDeviceTestCase {

    private static final String TAG = ForegroundCtsTestCase.class.getSimpleName();

    // NOTICE: if the context used by tests is initialized in the setup method the importance of our
    // foreground service will be IMPORTANCE_FOREGROUND_SERVICE (125) instead of
    // IMPORTANCE_FOREGROUND (100) on some platforms only.
    // This class is indirectly extending AdServicesCtsTestCase - which sets sContext outside any
    // JUnit @Before / @BeforeClass method - so the process has the proper importance.

    private static boolean sSimpleActivityStarted;

    /**
     * Starts a foreground activity to make the test process a foreground one to pass PPAPI and SDK
     * Sandbox checks
     */
    protected static void makeTestProcessForeground() throws TimeoutException {
        // PPAPI foreground checks are not done on S-, so no need for the SimpleActivity
        if (SdkLevel.isAtLeastT()) {
            Log.d(TAG, "Starting activity on T+ (and waiting for 2s)");
            SimpleActivity.startAndWait(sContext, Duration.ofSeconds(2));
            Log.d(TAG, "Activity started");
            sSimpleActivityStarted = true;
        } else {
            Log.d(TAG, "Not starting activity on device running " + Build.VERSION.SDK_INT);
        }
    }

    /** Terminates the SimpleActivity */
    protected static void shutdownForegroundActivity() {
        if (SdkLevel.isAtLeastT()) {
            Log.d(TAG, "Stopping activity on T+");
            SimpleActivity.stop(sContext);
        } else {
            Log.d(TAG, "Not stopping activity on device running " + Build.VERSION.SDK_INT);
        }
    }

    @BeforeClass
    public static void prepareSuite() throws TimeoutException {
        makeTestProcessForeground();
    }

    @AfterClass
    public static void tearDownSuite() {
        shutdownForegroundActivity();
    }

    protected static void assertForegroundActivityStarted() {
        assertWithMessage("Foreground activity started successfully")
                .that(sSimpleActivityStarted)
                .isTrue();
    }
}
