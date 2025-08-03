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

package android.security.cts;

import static org.junit.Assert.*;

import android.app.ActivityManager;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Process;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AttributionSourceTest extends StsExtraBusinessLogicTestCase {
    private static final String TAG = AttributionSourceTest.class.getSimpleName();

    private static final int WAIT_TIME = 2000; // Time to wait for process to launch (ms).

    // Time to wait for the service process to end (ms).
    private static final long SERVICE_KILL_TIME = 5000;

    public static final String ATTRIBUTION_SOURCE_KEY = "attributionSource";

    @AsbSecurityTest(cveBugId = 200288596)
    @Test
    public void testPidCheck() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AttributionSource attributionSource =
                new AttributionSource(
                        (AttributionSource)
                                Context.class.getMethod("getAttributionSource").invoke(context),
                        null);

        Field attSourceStateField =
                attributionSource.getClass().getDeclaredField("mAttributionSourceState");
        attSourceStateField.setAccessible(true);

        Object attSourceState = attSourceStateField.get(attributionSource);
        attSourceState.getClass().getField("pid").setInt(attSourceState, 0);
        final AttributionSource attributionSourceFinal = attributionSource;
        assertThrows(SecurityException.class, () -> attributionSourceFinal.enforceCallingPid());
    }

    @Test
    @AsbSecurityTest(cveBugId = 267231571)
    public void testOutsideTransaction() throws Exception {
        Context context = getInstrumentation().getContext();
        AttributionSourceService.AttributionSourceServiceConnection connection =
                new AttributionSourceService.AttributionSourceServiceConnection(context);
        connection.start();

        try {
            assertTrue("Service successfully unparceled AttributionSource off transaction thread",
                    !connection.postAttributionSource(context.getAttributionSource(), WAIT_TIME,
                            true));
        } finally {
            connection.stop();
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 267231571)
    public void testInsideTransaction() throws Exception {
        Context context = getInstrumentation().getContext();
        AttributionSourceService.AttributionSourceServiceConnection connection =
                new AttributionSourceService.AttributionSourceServiceConnection(context);
        connection.start();

        try {
            assertTrue("Service successfully unparceled AttributionSource off transaction thread",
                    connection.postAttributionSource(context.getAttributionSource(), WAIT_TIME,
                            false));
        } finally {
            connection.stop();
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        /**
         * Ensure the AttributionSourceService process is killed so that it is not present
         * when RunningAppProcessInfoTest is run (b/324156671)
         */

        ActivityManager amActivityManager =
                (ActivityManager)
                        getInstrumentation()
                                .getContext()
                                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appList =
                amActivityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo processInfo : appList) {
            if (processInfo.processName.equals(
                    "android.security.cts:attributionSourceServiceProcess")) {
                Log.i(TAG, "Killing AttributionSourceService process");
                Process.killProcess(processInfo.pid);
            }
        }

        long begin = System.currentTimeMillis();
        long now = begin;
        boolean found = false;
        while (now - begin < SERVICE_KILL_TIME) {
            appList =
                amActivityManager.getRunningAppProcesses();

            found = false;
            for (ActivityManager.RunningAppProcessInfo processInfo : appList) {
                if (processInfo.processName.equals(
                        "android.security.cts:attributionSourceServiceProcess")) {
                    found = true;
                }
            }

            if (!found) {
                break;
            }

            Thread.sleep(1000);
            now = System.currentTimeMillis();
        }

        assertFalse("Failed to kill the AttributionSourceService process before timeout", found);
    }
}

