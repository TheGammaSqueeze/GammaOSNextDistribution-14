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

package android.security.cts.CVE_2023_20916;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20916 extends StsExtraBusinessLogicTestCase {
    private static final long TIMEOUT_MS = 10_000L;

    // b/229256049
    // Vulnerable library : services.jar, framework.jar
    // Vulnerable module  : Not applicable
    // Is Play managed    : No
    @AsbSecurityTest(cveBugId = 229256049)
    @Test
    public void testPocCVE_2023_20916() {
        try {
            // Ensure that the test app does not have the ACCESS_SHORTCUTS permission
            Context context = getApplicationContext();
            assumeFalse("The test requires the app to not have the ACCESS_SHORTCUTS permission",
                    context.checkPermission(android.Manifest.permission.ACCESS_SHORTCUTS,
                    Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED);

            // Make a call to the vulnerable function getMainActivityLaunchIntent()
            Method method = LauncherApps.class.getMethod("getMainActivityLaunchIntent",
                    ComponentName.class, Bundle.class, UserHandle.class);
            PendingIntent pi =
                    (PendingIntent) method.invoke(context.getSystemService(LauncherApps.class),
                            new ComponentName(context, PocActivity.class), null,
                            UserHandle.getUserHandleForUid(Process.myUid()));

            // Register a broadcast receiver to receive broadcast from PocActivity indicating
            // presence of vulnerability
            final Semaphore broadcastReceived = new Semaphore(0);
            final String bcastAction = "CVE_2023_20916_action";
            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (intent.getAction().equals(bcastAction)) {
                            broadcastReceived.release();
                        }
                    } catch (Exception ignored) {
                        // ignore any exceptions
                    }
                }
            };
            IntentFilter filter = new IntentFilter(bcastAction);
            context.registerReceiver(broadcastReceiver, filter);

            // Attempt to launch the PocActivity using the pending intent received by calling
            // getMainActivityLaunchIntent()
            context.startIntentSender(pi.getIntentSender(), null, 0, 0, 0, null);

            // On vulnerable device, PocActivity is successfully launched using
            // LauncherAppsService#getActivityLaunchIntent and sends a broadcast, if it is received
            // successfully, the test fails.
            assertFalse("Device is vulnerable to b/229256049 !!",
                    broadcastReceived.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            try {
                if (e.getCause() instanceof SecurityException && e.getCause().getMessage()
                        .contains("Caller can't access shortcut information")) {
                    // this exception is thrown with fix so ignoring it
                    return;
                }
            } catch (Exception ignored) {
                // ignore any exceptions
            }
            assumeNoException(e);
        }
    }
}
