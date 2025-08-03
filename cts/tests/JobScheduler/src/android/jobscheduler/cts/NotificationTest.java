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

package android.jobscheduler.cts;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.ApplicationInfo;
import android.jobscheduler.cts.UserInitiatedJobTest.WatchUidRunner;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.SystemUtil;

import java.util.Collections;
import java.util.Map;

/**
 * Tests related to attaching notifications to jobs via
 * {@link JobService#setNotification(JobParameters, int, Notification, int)}
 */
public class NotificationTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = NotificationTest.class.hashCode();
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final String NOTIFICATION_CHANNEL_ID =
            NotificationTest.class.getSimpleName() + "_channel";

    private NotificationManager mNotificationManager;
    private NetworkingHelper mNetworkingHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mNotificationManager = getContext().getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NotificationTest.class.getSimpleName(), NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        mNetworkingHelper =
                new NetworkingHelper(InstrumentationRegistry.getInstrumentation(), mContext);
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        mNotificationManager.cancelAll();
        mNetworkingHelper.tearDown();

        // The super method should be called at the end.
        super.tearDown();
    }

    public void testNotificationJobEndDetach() throws Exception {
        mNotificationManager.cancelAll();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);
        assertTrue(kTestEnvironment.awaitStopped());

        Thread.sleep(1000); // Wait a bit for NotificationManager to catch up
        // Notification should remain
        StatusBarNotification[] activeNotifications = mNotificationManager.getActiveNotifications();
        assertEquals(1, activeNotifications.length);
        assertEquals(notificationId, activeNotifications[0].getId());
    }

    public void testNotificationJobEndRemove() throws Exception {
        mNotificationManager.cancelAll();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);
        assertTrue(kTestEnvironment.awaitStopped());

        waitUntil("Notification wasn't removed", 15 /* seconds */,
                () -> {
                    // Notification should be gone
                    return mNotificationManager.getActiveNotifications().length == 0;
                });
    }

    public void testNotificationRemovedOnForceStop() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mTestAppInterface.startAndKeepTestActivity(true);
            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY
                    ));

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertNotNull(jobNotification);

            mTestAppInterface.forceStopApp();

            notificationHelper.assertNotificationsRemoved();
        }
    }

    public void testNotificationRemovedOnPackageRestriction() throws Exception {
        String initialActivityManagerConstants = null;
        try (TestAppInterface testAppInterface = new TestAppInterface(mContext, JOB_ID);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            initialActivityManagerConstants =
                    Settings.Global.getString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS);
            SystemUtil.runShellCommand("am set-deterministic-uid-idle true");
            // Set background_settle_time to 0 so that the transition from UID active to UID idle
            // happens quickly.
            Settings.Global.putString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS, "background_settle_time=0");

            testAppInterface.setTestPackageRestricted(true);
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH
                    ));

            assertTrue("Job did not start after scheduling",
                    testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertNotNull(jobNotification);

            final ApplicationInfo testAppInfo =
                    mContext.getPackageManager().getApplicationInfo(TEST_APP_PACKAGE, 0);
            try (WatchUidRunner uidWatcher = new WatchUidRunner(
                    InstrumentationRegistry.getInstrumentation(), testAppInfo.uid)) {
                // Close the activity so the app isn't considered TOP.
                testAppInterface.closeActivity(true);
                uidWatcher.waitFor(UserInitiatedJobTest.WatchUidRunner.CMD_IDLE);
                Thread.sleep(1000); // Wait a bit for JS to process.
            }

            assertTrue(testAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT_MS));
            notificationHelper.assertNotificationsRemoved();
        } finally {
            Settings.Global.putString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS, initialActivityManagerConstants);
            SystemUtil.runShellCommand("am set-deterministic-uid-idle false");
        }
    }

    public void testNotificationRemovedOnTaskManagerStop() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mTestAppInterface.startAndKeepTestActivity(true);
            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY
                    ));

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertNotNull(jobNotification);

            // Use the same stop reasons as a Task Manager stop.
            mTestAppInterface.stopJob(JobParameters.STOP_REASON_USER,
                    JobParameters.INTERNAL_STOP_REASON_USER_UI_STOP);

            notificationHelper.assertNotificationsRemoved();
        }
    }

    /**
     * Test that an ANR happens if the app is required to show a notification
     * but doesn't provide one.
     */
    public void testNotification_userInitiated_anrWhenNotProvided() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(mContext, JOB_ID);
             AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                     TEST_APP_PACKAGE);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(mContext, TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, false
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    /**
     * Test that no ANR happens if the app is required to show a notification and it provides one.
     */
    @LargeTest
    public void testNotification_userInitiated_noAnrWhenProvided() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(mContext, JOB_ID);
             AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                     TEST_APP_PACKAGE);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(mContext, TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Confirm no ANR
            monitor.assertNoAnr(25_000);
        }
    }

    /**
     * Test that no ANR happens if the app is not required to show a notification
     * and it doesn't provide one.
     */
    @LargeTest
    public void testNotification_regular_noAnrWhenNotProvided() throws Exception {
        try (TestAppInterface testAppInterface = new TestAppInterface(mContext, JOB_ID);
             AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                     TEST_APP_PACKAGE);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(mContext, TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, false),
                    Collections.emptyMap());

            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Confirm no ANR
            monitor.assertNoAnr(25_000);
        }
    }

    public void testUserInitiatedJob_hasUijNotificationFlag() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mTestAppInterface.startAndKeepTestActivity(true);
            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertNotNull(jobNotification);
            assertTrue("A user-initiated job notification should have the UIJ flag",
                    jobNotification.getNotification().isUserInitiatedJob());
        }
    }

    public void testNonUserInitiatedJob_doesNotHaveUijNotificationFlag() throws Exception {
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mTestAppInterface.startAndKeepTestActivity(true);
            mTestAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Collections.emptyMap());

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertNotNull(jobNotification);
            assertFalse("A non user-initiated job notification should not have the UIJ flag",
                    jobNotification.getNotification().isUserInitiatedJob());
        }
    }

    /**
     * Test that a notification associated with a user-initiated job cannot be cancelled and that
     * its notification channel cannot be deleted.
     */
    public void testUserInitiatedJobNotificationBehavior() throws Exception {
        mNotificationManager.cancelAll();
        mNetworkingHelper.setAllNetworksEnabled(true);
        startAndKeepTestActivity();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(NETWORK_TYPE_ANY)
                .build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        mNotificationManager.cancel(notificationId);
        waitUntil("A user-initiated job notification should not be cancellable by apps.",
                5 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        try {
            mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
            fail("A notification channel associated with a user-initiated job "
                    + "should not be cancellable by apps.");
        } catch (SecurityException expected) {
            assertNotNull(mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID));
        }
    }

    /**
     * Test that a notification associated with a non user-initiated job can be cancelled and that
     * its notification channel can be deleted.
     */
    public void testNonUserInitiatedJobNotificationBehavior() throws Exception {
        mNotificationManager.cancelAll();
        mNetworkingHelper.setAllNetworksEnabled(true);
        startAndKeepTestActivity();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        mNotificationManager.cancel(notificationId);
        waitUntil("A non user-initiated job notification should be cancellable by apps.",
                15 /* seconds */,
                () -> {
                    // Notification should be gone
                    return mNotificationManager.getActiveNotifications().length == 0;
                });

        try {
            mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
            assertNull(mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID));
        } catch (SecurityException e) {
            fail("A notification channel associated with a non user-initiated job "
                    + "should be cancellable by apps.");
        }
    }
}
