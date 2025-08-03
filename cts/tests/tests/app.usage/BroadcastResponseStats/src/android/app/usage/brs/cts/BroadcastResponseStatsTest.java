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

package android.app.usage.brs.cts;

import static android.Manifest.permission.ACCESS_BROADCAST_RESPONSE_STATS;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.PACKAGE_USAGE_STATS;
import static android.Manifest.permission.USE_EXACT_ALARM;
import static android.app.role.RoleManager.ROLE_ASSISTANT;
import static android.content.Intent.EXTRA_REMOTE_CALLBACK;
import static android.provider.DeviceConfig.NAMESPACE_APP_STANDBY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.usage.BroadcastResponseStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.cts.ITestReceiver;
import android.app.usage.cts.UsageStatsTestRunner;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(UsageStatsTestRunner.class)
public class BroadcastResponseStatsTest {
    private static final String TAG = "BroadcastResponseStatsTest";

    private static final String TEST_APP_PKG = "android.app.usage.cts.test1";
    private static final String TEST_APP_CLASS = "android.app.usage.cts.test1.SomeActivity";
    private static final String TEST_APP_CLASS_SERVICE =
            "android.app.usage.cts.test1.TestService";
    private static final String TEST_APP_CLASS_BROADCAST_RECEIVER =
            "android.app.usage.cts.test1.TestBroadcastReceiver";

    private static final String TEST_APP3_PKG = "android.app.usage.cts.test3";
    private static final String TEST_APP4_PKG = "android.app.usage.cts.test4";
    private static final String TEST_ASSIST_APP_PKG = "android.app.usage.cts.test.assist";
    private static final String TEST_EXACT_ALARM_APP_PKG = "android.app.usage.cts.test.exactalarm";

    private static final long TEST_RESPONSE_STATS_ID_1 = 11;
    private static final long TEST_RESPONSE_STATS_ID_2 = 22;

    private static final String TEST_NOTIFICATION_CHANNEL_ID = "test-channel-id";
    private static final String TEST_NOTIFICATION_CHANNEL_NAME = "test-channel-name";
    private static final String TEST_NOTIFICATION_CHANNEL_DESC = "test-channel-description";

    private static final int TEST_NOTIFICATION_ID_1 = 10;
    private static final int TEST_NOTIFICATION_ID_2 = 20;
    private static final String TEST_NOTIFICATION_TITLE_FMT = "Test title; id=%s";
    private static final String TEST_NOTIFICATION_TEXT_1 = "Test content 1";
    private static final String TEST_NOTIFICATION_TEXT_2 = "Test content 2";

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    // For tests that are verifying a certain event doesn't occur, wait for some time
    // to ensure the event doesn't really occur. Otherwise, we cannot be sure if the event didn't
    // occur or the verification was done too early before the event occurred.
    private static final int WAIT_TIME_FOR_NEGATIVE_TESTS_MS = 500;

    private static final long TIMEOUT_BINDER_SERVICE_SEC = 2;

    private static final long BROADCAST_SESSIONS_DURATION_MS = 2_000;
    private static final long BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS = 2_000;
    private static final long BROADCAST_RESPONSE_WINDOW_DURATION_MS = 8_000;

    // TODO: Define these constants in UsageStatsManager as @TestApis to avoid hardcoding here.
    private static final String KEY_BROADCAST_RESPONSE_WINDOW_DURATION_MS =
            "broadcast_response_window_timeout_ms";
    private static final String KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE =
            "broadcast_response_fg_threshold_state";
    private static final String KEY_BROADCAST_SESSIONS_DURATION_MS =
            "broadcast_sessions_duration_ms";
    private static final String KEY_BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS =
            "broadcast_sessions_with_response_duration_ms";
    private static final String KEY_NOTE_RESPONSE_EVENT_FOR_ALL_BROADCAST_SESSIONS =
            "note_response_event_for_all_broadcast_sessions";
    private static final String KEY_BROADCAST_RESPONSE_EXEMPTED_ROLES =
            "brodacast_response_exempted_roles";
    private static final String KEY_BROADCAST_RESPONSE_EXEMPTED_PERMISSIONS =
            "brodacast_response_exempted_permissions";

    private static Context sContext;
    private static String sTargetPackage;
    private UsageStatsManager mUsageStatsManager;
    private RoleManager mRoleManager;
    private UiDevice mUiDevice;
    private UiAutomation mUiAutomation;

    private static int sInitialAppOpMode;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sTargetPackage = sContext.getPackageName();
        sInitialAppOpMode = AppOpsUtils.getOpMode(sTargetPackage,
                AppOpsManager.OPSTR_GET_USAGE_STATS);
        AppOpsUtils.setOpMode(sTargetPackage, AppOpsManager.OPSTR_GET_USAGE_STATS,
                AppOpsManager.MODE_IGNORED);
        SystemUtil.runShellCommand("am wait-for-broadcast-barrier");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        AppOpsUtils.setOpMode(sTargetPackage, AppOpsManager.OPSTR_GET_USAGE_STATS,
                sInitialAppOpMode);
    }

    @Before
    public void setUp() throws Exception {
        mUsageStatsManager = sContext.getSystemService(UsageStatsManager.class);
        mRoleManager = sContext.getSystemService(RoleManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiAutomation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        mUiAutomation.grantRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);

        // Make sure all procstats are reset by killing test apps
        SystemUtil.runShellCommand("am force-stop " + TEST_APP_PKG);
        SystemUtil.runShellCommand("am force-stop " + TEST_APP3_PKG);
        SystemUtil.runShellCommand("am force-stop " + TEST_APP4_PKG);
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.pressHome();

        // Clear broadcast response stats
        mUsageStatsManager.clearBroadcastEvents();
        mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */, 0 /* id */);
        mUiAutomation.revokeRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
    }

    @Test
    public void testBroadcastOptions_noPermission() throws Exception {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
        final Intent intent = new Intent().setComponent(new ComponentName(
                TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
        sendBroadcastAndWaitForReceipt(intent, options.toBundle());

        mUiAutomation.revokeRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        try {
            assertThrows(SecurityException.class, () -> {
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            });
        } finally {
            mUiAutomation.grantRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        }
    }

    @Test
    public void testQueryBroadcastResponseStats_noPermission() throws Exception {
        mUsageStatsManager.queryBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);

        mUiAutomation.revokeRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        try {
            assertThrows(SecurityException.class, () -> {
                mUsageStatsManager.queryBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
            });
        } finally {
            mUiAutomation.grantRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        }
    }

    @Test
    public void testClearBroadcastResponseStats_noPermission() throws Exception {
        mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);

        mUiAutomation.revokeRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        try {
            assertThrows(SecurityException.class, () -> {
                mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
            });
        } finally {
            mUiAutomation.grantRuntimePermission(sTargetPackage, ACCESS_BROADCAST_RESPONSE_STATS);
        }
    }

    @FlakyTest(bugId = 288339565)
    @Test
    public void testBroadcastResponseStats_broadcastDispatchedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            // Trigger a notification from test app and verify none of the counts get
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_notificationPostedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast and verify none of the counts get incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_notificationUpdatedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Post a notification (before sending any broadcast) and verify none of the counts
            // get incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Update a previously posted notification (change content text) and verify
            // notification-updated count gets incremented.
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_notificationCancelledCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Post a notification (before sending any broadcast) and verify none of the counts
            // get incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Cancel a previously posted notification (change content text) and verify
            // notification-cancelled count gets incremented.
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    1 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_multipleEvents() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast and verify none of the counts get incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send another broadcast and trigger another notification.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_2,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_2,
                            TEST_NOTIFICATION_TEXT_2));
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send another broadcast with a different ID and update a previously posted
            // notification.
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Update/cancel a previously posted notifications and verify there is
            // no change in counts.
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_2);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_clearCounts() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send the broadcast again after clearing counts and verify counts get incremented
            // as expected.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    1 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @MediumTest
    @Test
    public void testBroadcastResponseStats_changeResponseWindowDuration() throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagsWithDefaultDelays(deviceConfigStateHelper);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Trigger a notification from test app and verify notification-posted count gets
                // incremented.
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);
                mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                SystemClock.sleep(BROADCAST_RESPONSE_WINDOW_DURATION_MS);

                // Trigger a notification from test app but verify counts do not get
                // incremented as the notification is posted after the window durations is expired.
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelAll();
            } finally {
                connection.unbind();
            }
        }
    }

    @Test
    public void testBroadcastResponseStats_appNotInForeground() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_TOP));

                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                // Trigger a notification from test app and verify notification-posted count gets
                // incremented.
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Bring the test app to the foreground, send the broadcast again and verify that
                // counts do not change.
                launchTestActivityAndWaitToBeResumed(TEST_APP_PKG, TEST_APP_CLASS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_2));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Change the threshold to something lower than TOP, send the broadcast again
                // and verify that counts get incremented.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_PERSISTENT));
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                mUiDevice.pressHome();
                // Change the threshold to a process state higher than RECEIVER, send the
                // broadcast again and verify that counts do not change.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_HOME));
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelAll();
            } finally {
                connection.unbind();
            }
        }
    }

    @Test
    public void testBroadcastResponseStats_multiplePackages() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStats = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        final TestServiceConnection connection4 = bindToTestServiceAndGetConnection(TEST_APP4_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();
            ITestReceiver testReceiver4 = connection4.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent4 = new Intent().setComponent(new ComponentName(
                    TEST_APP4_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            expectedStats.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);

            // Send a broadcast to test-pkg3 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent3, options.toBundle());
            expectedStats.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);

            // Trigger a notification from test-pkg1 and verify notification-posted count gets
            // incremented.
            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStats.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);

            // Trigger a notification from test-pkg3 and verify notification-posted count gets
            // incremented.
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStats.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            expectedStats.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStats.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Trigger a notification from test-pkg3 and verify stats remain the same
            testReceiver4.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver4.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Send a broadcast to test-pkg4 with a request to record response and verify
            // broadcast-send count gets incremented.
            sendBroadcastAndWaitForReceipt(intent4, options.toBundle());
            testReceiver4.cancelNotification(TEST_NOTIFICATION_ID_1);
            expectedStats.put(TEST_APP4_PKG, new BroadcastResponseStats(TEST_APP4_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP4_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStats.get(TEST_APP4_PKG).incrementNotificationsCancelledCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            mUsageStatsManager.clearBroadcastResponseStats(null, TEST_RESPONSE_STATS_ID_1);
            expectedStats.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
            connection4.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_multiplePackages_multipleIds() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        final TestServiceConnection connection4 = bindToTestServiceAndGetConnection(TEST_APP4_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();
            ITestReceiver testReceiver4 = connection4.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent4 = new Intent().setComponent(new ComponentName(
                    TEST_APP4_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));

            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            // Trigger a notification from test-pkg1 and verify notification-posted count gets
            // incremented.
            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastEvents();
            // Trigger a notification from test-pkg4 and verify notification-posted count gets
            // incremented.
            testReceiver4.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver4.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            sendBroadcastAndWaitForReceipt(intent4, options2.toBundle());
            expectedStatsForId2.put(TEST_APP4_PKG, new BroadcastResponseStats(TEST_APP4_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP4_PKG).incrementBroadcastsDispatchedCount(1);

            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver4.cancelNotification(TEST_NOTIFICATION_ID_1);
            expectedStatsForId2.get(TEST_APP4_PKG).incrementNotificationsCancelledCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null, TEST_RESPONSE_STATS_ID_1);
            expectedStatsForId1.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
            connection4.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_clearCounts_multiplePackages() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */,
                    TEST_RESPONSE_STATS_ID_1);
            expectedStatsForId1.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */,
                    TEST_RESPONSE_STATS_ID_2);
            expectedStatsForId2.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_clearCounts_multipleIds() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, 0 /* id */);
            expectedStatsForId1.remove(TEST_APP_PKG);
            expectedStatsForId2.remove(TEST_APP_PKG);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP3_PKG, 0 /* id */);
            expectedStatsForId1.remove(TEST_APP3_PKG);
            expectedStatsForId2.remove(TEST_APP3_PKG);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_clearAllCounts() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */, 0 /* id */);
            expectedStatsForId1.clear();
            expectedStatsForId2.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    @Test
    public void testBroadcastResponseStats_mediaNotification() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @MediumTest
    @Test
    public void testBroadcastResponseStats_broadcastSession() throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagsWithDefaultDelays(deviceConfigStateHelper);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Send the broadcast again multiple times
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait until the broadcast response duration is elapsed and send the
                // broadcast again.
                SystemClock.sleep(BROADCAST_RESPONSE_WINDOW_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Verify that total broadcasts are considered as only 2 even though they
                // are dispatched multiple times.
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
            } finally {
                connection.unbind();
            }
        }
    }

    @MediumTest
    @Test
    public void testBroadcastResponseStats_broadcastSession_withLateNotification()
            throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagsWithDefaultDelays(deviceConfigStateHelper);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Send the broadcast again multiple times
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait until the broadcast response duration is elapsed and post a
                // notification.
                SystemClock.sleep(BROADCAST_RESPONSE_WINDOW_DURATION_MS);
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                // Verify that total broadcasts are considered as only 2 even though they
                // are dispatched multiple times and the posted notification doesn't get counted.
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
            } finally {
                connection.unbind();
            }
        }
    }

    @MediumTest
    @Test
    public void testBroadcastResponseStats_broadcastSessionWithResponse() throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagsWithDefaultDelays(deviceConfigStateHelper);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Send the broadcast again multiple times
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Repeat the previous step - wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Trigger a notification from test app and verify notification-posted count gets
                // incremented.
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                // Verify that total broadcasts are considered as only 2 even though they
                // are dispatched multiple times.
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        3 /* broadcastCount */,
                        3 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
            } finally {
                connection.unbind();
            }
        }
    }

    @MediumTest
    @Test
    public void testBroadcastResponseStats_broadcastSessionWithResponse_recordOnlyOne()
            throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagsWithDefaultDelays(deviceConfigStateHelper);
            updateFlagWithDelay(deviceConfigStateHelper,
                    KEY_NOTE_RESPONSE_EVENT_FOR_ALL_BROADCAST_SESSIONS,
                    String.valueOf(false));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Send the broadcast again multiple times
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Now wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Repeat the previous step - wait for a while and send the broadcast again.
                SystemClock.sleep(BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                // Trigger a notification from test app and verify notification-posted count gets
                // incremented.
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                // Verify that total broadcasts are considered as only 2 even though they
                // are dispatched multiple times.
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Wait until the broadcast response window duration is elapsed and verify that
                // previously sent broadcasts are recorded correctly.
                SystemClock.sleep(BROADCAST_RESPONSE_WINDOW_DURATION_MS);

                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

                // Verify that total broadcasts are considered as only 2 even though they
                // are dispatched multiple times.
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        3 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
            } finally {
                connection.unbind();
            }
        }
    }

    @FlakyTest(bugId = 288339565)
    @Test
    public void testBroadcastResponseStats_exemptedRole() throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagWithDelay(deviceConfigStateHelper,
                    KEY_BROADCAST_RESPONSE_EXEMPTED_ROLES,
                    ROLE_ASSISTANT);

            assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection(
                    TEST_ASSIST_APP_PKG);
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_ASSIST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                addAssistRoleHolder(TEST_ASSIST_APP_PKG, Process.myUserHandle().getIdentifier());
                // Wait until the component tracking the broadcast response stats is updated with
                // the new role holder.
                waitForPackageToBeExempted(TEST_ASSIST_APP_PKG);

                // Since the assistant role is exempted and the test app holds assist role,
                // broadcast response stats for it would not be recorded.
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_2));

                assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Remove the assistant role from exempted roles and verify broadcast
                // response stats for the test app are recorded.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_EXEMPTED_ROLES,
                        String.join("|", RoleManager.ROLE_BROWSER, RoleManager.ROLE_EMERGENCY));

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

                assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);

                // Add the assistant role to the list of exempted roles and verify broadcast
                // response stats for the test app are not recorded.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_EXEMPTED_ROLES,
                        String.join("|", RoleManager.ROLE_BROWSER, RoleManager.ROLE_EMERGENCY,
                                ROLE_ASSISTANT));

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_ASSIST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);
            } finally {
                removeAssistRoleHolder(TEST_ASSIST_APP_PKG, Process.myUserHandle().getIdentifier());
                connection.unbind();
            }
        }
    }

    @Test
    public void testBroadcastResponseStats_exemptedPermission() throws Exception {
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {

            assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection(
                    TEST_EXACT_ALARM_APP_PKG);
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_EXACT_ALARM_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Add USE_EXACT_ALARM to the list of exempted permissions and verify
                // broadcast response stats for the test app are not recorded.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_EXEMPTED_PERMISSIONS,
                        USE_EXACT_ALARM);

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_2));

                assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Remove USE_EXACT_ALARM from the list of exempted permissions and verify
                // broadcast response stats for the test app are recorded.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_EXEMPTED_PERMISSIONS,
                        String.join("|", PACKAGE_USAGE_STATS, INTERNET));

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

                assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);

                // Add USE_EXACT_ALARM to the list of exempted permissions again and verify
                // broadcast response stats for the test app are not recorded.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_EXEMPTED_PERMISSIONS,
                        String.join("|", PACKAGE_USAGE_STATS, INTERNET, USE_EXACT_ALARM));

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildMediaNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_EXACT_ALARM_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        1 /* notificationCancelledCount */);
            } finally {
                connection.unbind();
            }
        }
    }

    private void updateFlagsWithDefaultDelays(DeviceConfigStateHelper deviceConfigStateHelper) {
        updateFlagWithDelay(deviceConfigStateHelper,
                KEY_BROADCAST_SESSIONS_DURATION_MS,
                String.valueOf(BROADCAST_SESSIONS_DURATION_MS));
        updateFlagWithDelay(deviceConfigStateHelper,
                KEY_BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS,
                String.valueOf(BROADCAST_SESSIONS_WITH_RESPONSE_DURATION_MS));
        updateFlagWithDelay(deviceConfigStateHelper,
                KEY_BROADCAST_RESPONSE_WINDOW_DURATION_MS,
                String.valueOf(BROADCAST_RESPONSE_WINDOW_DURATION_MS));
    }

    private void updateFlagWithDelay(DeviceConfigStateHelper deviceConfigStateHelper,
            String key, String value) {
        deviceConfigStateHelper.set(key, value);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final String actualValue = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                    () -> mUsageStatsManager.getAppStandbyConstant(key),
                    result -> value.equals(result));
            assertEquals("Error changing the value of " + key, value, actualValue);
        });
    }

    private Notification buildNotification(String channelId, int notificationId,
            String notificationText) {
        return new Notification.Builder(sContext, channelId)
                .setSmallIcon(android.R.drawable.ic_info)
                .setContentTitle(String.format(TEST_NOTIFICATION_TITLE_FMT, notificationId))
                .setContentText(notificationText)
                .build();
    }

    private Notification buildMediaNotification(String channelId, int notificationId,
            String notificationText) {
        final PendingIntent pendingIntent = PendingIntent.getActivity(sContext,
                0 /* requestCode */, new Intent(sContext, this.getClass()),
                PendingIntent.FLAG_IMMUTABLE);
        final MediaSession session = new MediaSession(sContext, "test_media");
        return new Notification.Builder(sContext, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_day)
                .setContentTitle(String.format(TEST_NOTIFICATION_TITLE_FMT, notificationId))
                .setContentText(notificationText)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(sContext, android.R.drawable.ic_media_previous),
                        "previous", pendingIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(sContext, android.R.drawable.ic_media_play),
                        "play", pendingIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(sContext, android.R.drawable.ic_media_next),
                        "next", pendingIntent).build())
                .setStyle(new Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(session.getSessionToken()))
                .build();
    }

    private void sendBroadcastAndWaitForReceipt(Intent intent, Bundle options)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, new RemoteCallback(result -> latch.countDown()));
        sContext.sendBroadcast(intent, null /* receiverPermission */, options);
        SystemUtil.runShellCommand("am wait-for-broadcast-barrier");
        if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for the test app to receive the broadcast");
        }
    }

    private void assertResponseStats(String packageName, long id, int... expectedCounts) {
        final BroadcastResponseStats expectedStats = new BroadcastResponseStats(packageName, id);
        expectedStats.incrementBroadcastsDispatchedCount(expectedCounts[0]);
        expectedStats.incrementNotificationsPostedCount(expectedCounts[1]);
        expectedStats.incrementNotificationsUpdatedCount(expectedCounts[2]);
        expectedStats.incrementNotificationsCancelledCount(expectedCounts[3]);
        assertResponseStats(packageName, id, expectedStats);
    }

    private void assertResponseStats(String packageName, long id,
            BroadcastResponseStats expectedStats) {
        List<BroadcastResponseStats> actualStats = mUsageStatsManager
                .queryBroadcastResponseStats(packageName, id);
        if (compareStats(expectedStats, actualStats)) {
            SystemClock.sleep(WAIT_TIME_FOR_NEGATIVE_TESTS_MS);
        }

        actualStats = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                () -> mUsageStatsManager.queryBroadcastResponseStats(packageName, id),
                result -> compareStats(expectedStats, result));
        actualStats.sort(Comparator.comparing(BroadcastResponseStats::getPackageName));
        final String errorMsg = String.format("\nEXPECTED(%d)=%s\nACTUAL(%d)=%s\n",
                1, expectedStats,
                actualStats.size(), Arrays.toString(actualStats.toArray()));
        assertTrue(errorMsg, compareStats(expectedStats, actualStats));
    }

    private void assertResponseStats(long id,
            ArrayMap<String, BroadcastResponseStats> expectedStats) {
        // TODO: Call into the above assertResponseStats() method instead of duplicating
        // the logic.
        List<BroadcastResponseStats> actualStats = mUsageStatsManager
                .queryBroadcastResponseStats(null /* packageName */, id);
        if (compareStats(expectedStats, actualStats)) {
            SystemClock.sleep(WAIT_TIME_FOR_NEGATIVE_TESTS_MS);
        }

        actualStats = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                () -> mUsageStatsManager.queryBroadcastResponseStats(null /* packageName */, id),
                result -> compareStats(expectedStats, result));
        actualStats.sort(Comparator.comparing(BroadcastResponseStats::getPackageName));
        final String errorMsg = String.format("\nEXPECTED(%d)=%s\nACTUAL(%d)=%s\n",
                expectedStats.size(), expectedStats,
                actualStats.size(), Arrays.toString(actualStats.toArray()));
        assertTrue(errorMsg, compareStats(expectedStats, actualStats));
    }

    private boolean compareStats(ArrayMap<String, BroadcastResponseStats> expectedStats,
            List<BroadcastResponseStats> actualStats) {
        if (expectedStats.size() != actualStats.size()) {
            return false;
        }
        for (int i = 0; i < actualStats.size(); ++i) {
            final BroadcastResponseStats actualPackageStats = actualStats.get(i);
            final String packageName = actualPackageStats.getPackageName();
            if (!actualPackageStats.equals(expectedStats.get(packageName))) {
                return false;
            }
        }
        return true;
    }

    private boolean compareStats(BroadcastResponseStats expectedStats,
            List<BroadcastResponseStats> actualStats) {
        if (actualStats.size() > 1) {
            return false;
        }
        final BroadcastResponseStats stats = (actualStats == null || actualStats.isEmpty())
                ? new BroadcastResponseStats(expectedStats.getPackageName(), expectedStats.getId())
                : actualStats.get(0);
        return expectedStats.equals(stats);
    }

    private TestServiceConnection bindToTestServiceAndGetConnection(String packageName) {
        final TestServiceConnection
                connection = new TestServiceConnection(sContext);
        final Intent intent = new Intent().setComponent(
                new ComponentName(packageName, TEST_APP_CLASS_SERVICE));
        sContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return connection;
    }

    private TestServiceConnection bindToTestServiceAndGetConnection() throws Exception {
        return bindToTestServiceAndGetConnection(TEST_APP_PKG);
    }

    private void launchTestActivityAndWaitToBeResumed(String pkgName, String className)
            throws Exception {
        // Make sure the screen is awake and unlocked. Otherwise, the app activity won't be resumed.
        wakeUpAndDismissKeyguard();

        final Intent intent = createTestActivityIntent(pkgName, className);
        final CountDownLatch latch = new CountDownLatch(1);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, new RemoteCallback(result -> latch.countDown()));
        sContext.startActivity(intent);
        if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for the test app activity to be resumed");
        }
    }

    protected void addAssistRoleHolder(String pkgName, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (isAssistantRoleHolder(pkgName, userId)) {
                Log.d(TAG, pkgName + " already holds the assist role on u" + userId);
                return;
            }
            final CountDownLatch latch = new CountDownLatch(1);
            final OnRoleHoldersChangedListener listener = (roleName, user) -> {
                Log.d(TAG, "Received role changed callback for role=" + roleName
                        + " in u" + user.getIdentifier());
                if (latch.getCount() > 0
                        && ROLE_ASSISTANT.equals(roleName) && user.getIdentifier() == userId
                        && isAssistantRoleHolder(pkgName, userId)) {
                    latch.countDown();
                }
            };
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(sContext.getMainExecutor(),
                    listener, UserHandle.of(userId));
            addRoleHolderAsUser(ROLE_ASSISTANT, pkgName, userId);
            if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for role holder to be added");
            }
            mRoleManager.removeOnRoleHoldersChangedListenerAsUser(listener, UserHandle.of(userId));
        });
    }

    private void addRoleHolderAsUser(String roleName, String pkgName, int userId)
            throws Exception {
        final CompletableFuture<Boolean> callback = new CompletableFuture<>();
        Log.d(TAG, "Adding assist role holder; pkg=" + pkgName + ", userId=" + userId);
        mRoleManager.addRoleHolderAsUser(roleName, pkgName, 0,
                UserHandle.of(userId), sContext.getMainExecutor(), callback::complete);
        assertTrue(callback.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    protected void removeAssistRoleHolder(String pkgName, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (!isAssistantRoleHolder(pkgName, userId)) {
                Log.d(TAG, pkgName + " doesn't hold the assist role on u" + userId);
                return;
            }
            final CountDownLatch latch = new CountDownLatch(1);
            final OnRoleHoldersChangedListener listener = (roleName, user) -> {
                Log.d(TAG, "Received role changed callback for role=" + roleName
                        + " in u" + user.getIdentifier());
                if (latch.getCount() > 0
                        && ROLE_ASSISTANT.equals(roleName) && user.getIdentifier() == userId
                        && !isAssistantRoleHolder(pkgName, userId)) {
                    latch.countDown();
                }
            };
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(sContext.getMainExecutor(),
                    listener, UserHandle.of(userId));
            removeRoleHolderAsUser(ROLE_ASSISTANT, pkgName, userId);
            if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for role holder to be removed");
            }
            mRoleManager.removeOnRoleHoldersChangedListenerAsUser(listener, UserHandle.of(userId));
        });
    }

    private void removeRoleHolderAsUser(String roleName, String pkgName, int userId)
            throws Exception {
        final CompletableFuture<Boolean> callback = new CompletableFuture<>();
        Log.d(TAG, "Removing assist role holder; pkg=" + pkgName + ", userId=" + userId);
        mRoleManager.removeRoleHolderAsUser(roleName, pkgName, 0,
                UserHandle.of(userId), sContext.getMainExecutor(), callback::complete);
        assertTrue(callback.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    protected boolean isAssistantRoleHolder(String pkgName, int userId) {
        final List<String> roleHolders = mRoleManager.getRoleHoldersAsUser(
                ROLE_ASSISTANT, UserHandle.of(userId));
        return roleHolders.contains(pkgName);
    }

    private void waitForPackageToBeExempted(String pkgName) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                TestUtils.waitUntil(
                        "Timed out waiting for " + pkgName + " to be exempted",
                        DEFAULT_TIMEOUT_MS,
                        () -> mUsageStatsManager.isPackageExemptedFromBroadcastResponseStats(
                                TEST_ASSIST_APP_PKG)));
    }

    private void wakeUpAndDismissKeyguard() throws Exception {
        mUiDevice.wakeUp();
        SystemUtil.runShellCommand("wm dismiss-keyguard");
    }

    private Intent createTestActivityIntent(String pkgName, String className) {
        final Intent intent = new Intent();
        intent.setClassName(pkgName, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static class TestServiceConnection implements ServiceConnection {
        private BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private Context mContext;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
        }

        public IBinder getService() throws Exception {
            final IBinder service = mBlockingQueue.poll(TIMEOUT_BINDER_SERVICE_SEC,
                    TimeUnit.SECONDS);
            return service;
        }

        public ITestReceiver getITestReceiver() throws Exception {
            return ITestReceiver.Stub.asInterface(getService());
        }

        public void unbind() {
            mContext.unbindService(this);
        }
    }
}
