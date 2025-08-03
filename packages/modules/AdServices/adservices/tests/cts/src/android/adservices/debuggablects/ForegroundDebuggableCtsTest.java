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

package android.adservices.debuggablects;

import static org.junit.Assert.assertTrue;

import com.android.modules.utils.build.SdkLevel;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

abstract class ForegroundDebuggableCtsTest extends AdServicesDebuggableTestCase {
    // If the context is initialized in the setup method the importance of our foreground
    // service will be IMPORTANCE_FOREGROUND_SERVICE (125) instead of
    // IMPORTANCE_FOREGROUND (100) on some platforms only.
    // See http://ag/c/platform/packages/modules/AdServices/+/19607471/comments/e6767fdc_971415d0
    // Given that AdServicesTestCase sets it that way, Saul Goodman!

    private static boolean sSimpleActivityStarted;

    /**
     * Starts a foreground activity to make the test process a foreground one to pass PPAPI and SDK
     * Sandbox checks
     */
    protected static void makeTestProcessForeground() throws TimeoutException {
        // PPAPI foreground checks are not done on S-, so no need for the SimpleActivity
        if (SdkLevel.isAtLeastT()) {
            SimpleActivity.startAndWait(sContext, Duration.ofSeconds(2));
            sSimpleActivityStarted = true;
        }
    }

    /** Terminates the SimpleActivity */
    protected static void shutdownForegroundActivity() {
        if (SdkLevel.isAtLeastT()) {
            SimpleActivity.stop(sContext);
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

    protected void assertForegroundActivityStarted() {
        assertTrue("Foreground activity didn't start successfully", sSimpleActivityStarted);
    }
}
