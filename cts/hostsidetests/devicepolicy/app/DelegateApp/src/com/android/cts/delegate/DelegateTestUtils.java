/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.delegate;

import static junit.framework.Assert.fail;

import android.app.admin.DelegatedAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NetworkEvent;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.test.MoreAsserts;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.dpmwrapper.DeviceOwnerHelper;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utils class for delegation tests.
 */
public class DelegateTestUtils {

    private static final String TAG = DelegateTestUtils.class.getSimpleName();

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    /**
     * A receiver for listening for network and security logs.
     *
     * To use this the sBatchCountDown must be assigned before generating logs.
     * The receiver will ignore events until sBatchCountDown is assigned.
     */
    public static class DelegatedLogsReceiver extends DelegatedAdminReceiver {

        private static final long TIMEOUT_MIN = 3;

        static CountDownLatch sBatchCountDown;
        static ArrayList<NetworkEvent> sNetworkEvents = new ArrayList<>();
        static ArrayList<SecurityEvent> sSecurityEvents = new ArrayList<>();

        @Override
        public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
                int networkLogsCount) {
            if (DeviceOwnerHelper.forwardBroadcastToTestApp(context, intent)) return;

            Log.v(TAG, "onNetworkLogsAvailable() received for user " + Process.myUid() + ": token="
                    + batchToken + ", count=" + networkLogsCount);
            if (sBatchCountDown == null) {
                // If the latch is not set then nothing will be using the receiver to examine
                // the logs. Leave the logs unread.
                return;
            }

            DevicePolicyManager dpm = BaseTestCase.getDpm(context);
            final List<NetworkEvent> events = dpm.retrieveNetworkLogs(null, batchToken);
            if (events == null || events.size() == 0) {
                fail("Failed to retrieve batch of network logs with batch token " + batchToken);
            } else {
                sNetworkEvents.addAll(events);
                sBatchCountDown.countDown();
            }
        }

        @Override
        public void onSecurityLogsAvailable(Context context, Intent intent) {
            if (false) {
                // TODO(b/191149410): at some point check below should be needed to fix a test on
                // automotive - if not, than this whole method might not be used anymore...
                if (DeviceOwnerHelper.forwardBroadcastToTestApp(context, intent)) return;
            }

            Log.v(TAG, "onSecurityLogsAvailable() received for user " + Process.myUid());

            if (sBatchCountDown == null) {
                // If the latch is not set then nothing will be using the receiver to examine
                // the logs. Leave the logs unread.
                return;
            }

            DevicePolicyManager dpm = BaseTestCase.getDpm(context);
            final List<SecurityEvent> events = dpm.retrieveSecurityLogs(null);
            if (events == null || events.size() == 0) {
                Log.v(TAG, "No events");
                fail("Failed to retrieve security logs");
            } else {
                Log.v(TAG, "Received " + events.size() + " events");
                sSecurityEvents.addAll(events);
                sBatchCountDown.countDown();
            }
        }

        public static void waitForBroadcast() throws InterruptedException {
            if (!sBatchCountDown.await(TIMEOUT_MIN, TimeUnit.MINUTES)) {
                fail("Did not get DelegateAdminReceiver callback");
            }
        }

        public static void forceAndWaitForSecurityLogsBroadcast() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(TIMEOUT_MIN);
            while (System.nanoTime() < deadline) {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .executeShellCommand("dpm force-security-logs");
                if (sBatchCountDown.await(10, TimeUnit.SECONDS)) {
                    return;
                }
            }
            fail("Did not get DelegateAdminReceiver callback");
        }

        public static List<NetworkEvent> getNetworkEvents() {
            return sNetworkEvents;
        }

        public static List<SecurityEvent> getSecurityEvents() {
            return sSecurityEvents;
        }
    }

    public static void assertExpectException(Class<? extends Throwable> expectedExceptionType,
            String expectedExceptionMessageRegex, ExceptionRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            Assert.assertTrue("Expected " + expectedExceptionType.getName() + " but caught:"
                            + "\n" + Log.getStackTraceString(e) + "\nTest exception:\n",
                expectedExceptionType.isAssignableFrom(e.getClass()));
            if (expectedExceptionMessageRegex != null) {
                MoreAsserts.assertContainsRegex(expectedExceptionMessageRegex, e.getMessage());
            }
            return; // Pass
        }
        Assert.fail("Expected " + expectedExceptionType.getName() + " was not thrown");
    }
}
