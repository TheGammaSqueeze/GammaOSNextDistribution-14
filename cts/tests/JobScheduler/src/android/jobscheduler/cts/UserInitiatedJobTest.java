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

package android.jobscheduler.cts;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.jobscheduler.cts.JobThrottlingTest.setScreenState;
import static android.jobscheduler.cts.JobThrottlingTest.setTestPackageStandbyBucket;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.jobscheduler.cts.jobtestapp.TestFgsService;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class UserInitiatedJobTest {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final int JOB_ID = UserInitiatedJobTest.class.hashCode();

    private Context mContext;
    private PowerManager mPowerManager;
    private UiDevice mUiDevice;
    private TestAppInterface mTestAppInterface;
    private NetworkingHelper mNetworkingHelper;

    private String mInitialActivityManagerConstants;
    private boolean mInitialLowPowerStandbyEnabled;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
        setTestPackageStandbyBucket(mUiDevice, JobThrottlingTest.Bucket.ACTIVE);
        mNetworkingHelper =
                new NetworkingHelper(InstrumentationRegistry.getInstrumentation(), mContext);
        mNetworkingHelper.setAllNetworksEnabled(true); // all user-initiated jobs require a network

        mInitialActivityManagerConstants = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS);
        // Set background_settle_time to 0 so that the transition from UID active to UID idle
        // happens quickly.
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS, "background_settle_time=0");
        SystemUtil.runShellCommand("am set-deterministic-uid-idle true");

        mPowerManager = mContext.getSystemService(PowerManager.class);
        mInitialLowPowerStandbyEnabled = mPowerManager.isLowPowerStandbyEnabled();
        try (PermissionContext p = TestApis.permissions().withPermission(
                Manifest.permission.MANAGE_LOW_POWER_STANDBY)) {
            mPowerManager.setLowPowerStandbyEnabled(false);
        }
    }

    @After
    public void tearDown() throws Exception {
        mTestAppInterface.cleanup();
        mNetworkingHelper.tearDown();
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS, mInitialActivityManagerConstants);
        SystemUtil.runShellCommand("am set-deterministic-uid-idle false");
        try (PermissionContext p = TestApis.permissions().withPermission(
                Manifest.permission.MANAGE_LOW_POWER_STANDBY)) {
            mPowerManager.setLowPowerStandbyEnabled(mInitialLowPowerStandbyEnabled);
        }
    }

    @Test
    @FlakyTest
    public void testJobUidState() throws Exception {
        // Go through the notification click/BAL route of scheduling the job so the proc state
        // data comes from being elevated by the running job and not because of the app being
        // in a higher state.
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true,
                            TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(2 * DEFAULT_WAIT_TIMEOUT_MS));
            mTestAppInterface.assertJobUidState(ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND,
                    ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
                    | ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK,
                    201 /* ProcessList.PERCEPTIBLE_APP_ADJ + 1 */);
        }
    }

    /** Test that UIJs for the TOP app start immediately and there is no limit on the number. */
    @Test
    @LargeTest
    public void testTopUiUnlimited() throws Exception {
        final int standardConcurrency = 64;
        final int numUijs = standardConcurrency + 1;
        setScreenState(mUiDevice, true);
        mTestAppInterface.startAndKeepTestActivity(true);
        for (int i = 0; i < numUijs; ++i) {
            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, i,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY
                    ));
        }
        for (int i = 0; i < numUijs; ++i) {
            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(i, DEFAULT_WAIT_TIMEOUT_MS));
        }
    }

    /**
     * Test that UI jobs can be scheduled when the app is in a state to start an Activity
     * from the background, but they won't run until the user opens the app.
     */
    @Test
    public void testRestrictedBalToTop() throws Exception {
        // Tests cannot disable ethernet network.
        assumeFalse("ethernet is connected", mNetworkingHelper.hasEthernetConnection());

        // Disable networks to control when the job is eligible to start.
        mNetworkingHelper.setAllNetworksEnabled(false);

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TEST_APP_PACKAGE)) {
            mTestAppInterface.setTestPackageRestricted(true);
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue(mTestAppInterface
                    .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));

            final ApplicationInfo testAppInfo =
                    mContext.getPackageManager().getApplicationInfo(TEST_APP_PACKAGE, 0);
            try (WatchUidRunner uidWatcher = new WatchUidRunner(
                    InstrumentationRegistry.getInstrumentation(), testAppInfo.uid)) {
                // Taking the app off the temp whitelist should make it go UID idle.
                SystemUtil.runShellCommand("cmd deviceidle tempwhitelist"
                        + " -u " + UserHandle.myUserId()
                        + " -r " + TEST_APP_PACKAGE);
                uidWatcher.waitFor(WatchUidRunner.CMD_IDLE);
                Thread.sleep(1000); // Wait a bit for JS to process.
            }

            mNetworkingHelper.setAllNetworksEnabled(true);
            assertFalse(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            setScreenState(mUiDevice, true);
            mTestAppInterface.startAndKeepTestActivity(true);
            assertTrue(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));
        }
    }

    /**
     * Test that UI jobs are stopped immediately when an app is background restricted, and can
     * resume in the background when the app is unrestricted.
     */
    @Test
    public void testRestrictedToggling() throws Exception {
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TEST_APP_PACKAGE)) {
            mTestAppInterface.setTestPackageRestricted(false);
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue(mTestAppInterface
                    .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
            assertTrue(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Take the app off the temp whitelist so it doesn't retain the exemptions.
            SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -u " + UserHandle.myUserId()
                    + " -r " + TEST_APP_PACKAGE);

            // Restrict app. Job should stop immediately and shouldn't restart.
            mTestAppInterface.setTestPackageRestricted(true);
            assertTrue(mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT_MS));
            assertFalse(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Unrestrict app. Job should be able to start.
            mTestAppInterface.setTestPackageRestricted(false);
            assertTrue(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));
        }
    }

    /** Test that UI jobs of restricted apps will be stopped after the app leaves the TOP state. */
    @Test
    public void testRestrictedTopToBg() throws Exception {
        setScreenState(mUiDevice, true);
        mTestAppInterface.setTestPackageRestricted(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.scheduleJob(
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                        TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                ),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

        assertTrue(mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

        final ApplicationInfo testAppInfo =
                mContext.getPackageManager().getApplicationInfo(TEST_APP_PACKAGE, 0);
        try (WatchUidRunner uidWatcher = new WatchUidRunner(
                InstrumentationRegistry.getInstrumentation(), testAppInfo.uid)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            uidWatcher.waitFor(WatchUidRunner.CMD_IDLE);
            Thread.sleep(1000); // Wait a bit for JS to process.
        }

        assertTrue(mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT_MS));
        JobParameters params = mTestAppInterface.getLastParams();
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION, params.getStopReason());
    }

    @Test
    @FlakyTest
    public void testRestrictedUidState() throws Exception {
        mTestAppInterface.setTestPackageRestricted(true);
        // Go through the notification click/BAL route of scheduling the job so the proc state
        // data comes from being elevated by the running job and not because of the app being
        // in a higher state.
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TEST_APP_PACKAGE)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true,
                            TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(2 * DEFAULT_WAIT_TIMEOUT_MS));
            mTestAppInterface.assertJobUidState(ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND,
                    ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK,
                    227 /* ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ + 2 */);
        }
    }

    /**
     * Test that UI jobs can be scheduled when the app is in a state to start an Activity
     * from the background.
     */
    @Test
    public void testSchedulingBal() throws Exception {
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            // Close the activity so the app isn't considered TOP.
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertTrue(mTestAppInterface
                    .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
        }
    }

    /** Test that UI jobs can't be scheduled directly from the background. */
    @Test
    public void testSchedulingBg() throws Exception {
        // Close the activity and turn the screen off so the app isn't considered TOP.
        mTestAppInterface.closeActivity();
        setScreenState(mUiDevice, false);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_FAILURE));
    }

    /** Test that UI jobs can't be scheduled directly from EJs. */
    @Test
    public void testSchedulingEj() throws Exception {
        // Close the activity and turn the screen off so the app isn't considered TOP.
        mTestAppInterface.closeActivity();
        setScreenState(mUiDevice, false);

        final int jobIdEj = JOB_ID;
        final int jobIdUij = JOB_ID + 1;

        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdEj));
        assertTrue(mTestAppInterface.awaitJobStart(jobIdEj, DEFAULT_WAIT_TIMEOUT_MS));

        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY,
                        TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdUij
                ));
        assertTrue(mTestAppInterface.awaitJobScheduleResult(
                jobIdUij, DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_FAILURE));
    }

    /** Test that UI jobs can be scheduled directly from an FGS that was started in TOP state. */
    @Test
    public void testSchedulingFgs_approved() throws Exception {
        setScreenState(mUiDevice, true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.startFgs();
        mTestAppInterface.closeActivity(true);
        // FGS started while the app was TOP. The app should be allowed to schedule a UI job
        // because the FGS is still running, even though it's no longer TOP.
        Thread.sleep(10000); // Wait a bit so that Activity-close BAL allowance disappears.
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
    }

    /** Test that UI jobs can't be scheduled directly from an FGS started from the background. */
    @Test
    public void testSchedulingFgs_disapproved() throws Exception {
        mTestAppInterface.closeActivity(true);
        final CallbackAsserter resultBroadcastAsserter =
                CallbackAsserter.forBroadcast(new IntentFilter(TestFgsService.ACTION_FGS_STARTED));
        mTestAppInterface.postFgsStartingAlarm();
        // FGS started in the background, but not a BAL-approved state. The app shouldn't
        // be allowed to schedule a UI job.
        resultBroadcastAsserter.assertCalled("Didn't get schedule FGS started broadcast",
                15 /* 15 seconds */);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_FAILURE));
    }

    /** Test that UI jobs can be scheduled directly from the TOP state. */
    @Test
    public void testSchedulingTop() throws Exception {
        setScreenState(mUiDevice, true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
        assertTrue(mTestAppInterface
                .awaitJobScheduleResult(DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));
    }

    /** Test that UI jobs can't be scheduled directly from other UIJs. */
    @Test
    public void testSchedulingUij() throws Exception {
        int firstJobId = JOB_ID;
        int secondJobId = firstJobId + 1;

        setScreenState(mUiDevice, true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.scheduleJob(
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                        TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                ),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY,
                        TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, firstJobId
                )
        );
        // Close the activity so the app is no longer considered TOP.
        mTestAppInterface.closeActivity(true);

        assertTrue(mTestAppInterface.awaitJobScheduleResult(firstJobId,
                DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_SUCCESS));

        Thread.sleep(10000); // Wait a bit so that BAL allowance disappears.

        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY,
                        TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, secondJobId
                )
        );
        assertTrue(mTestAppInterface.awaitJobScheduleResult(secondJobId,
                DEFAULT_WAIT_TIMEOUT_MS, JobScheduler.RESULT_FAILURE));
    }

    // TODO(141645789): merge with android.app.cts.android.app.cts.tools.WatchUidRunner
    static class WatchUidRunner implements AutoCloseable {
        static final String TAG = "WatchUidRunner";

        public static final int CMD_IDLE = 0;

        static final String[] COMMAND_TO_STRING = new String[] {"idle"};

        final Instrumentation mInstrumentation;
        final int mUid;
        final String mUidStr;
        final long mDefaultWaitTime;
        final Pattern mSpaceSplitter;
        final ParcelFileDescriptor mReadFd;
        final FileInputStream mReadStream;
        final BufferedReader mReadReader;
        final ParcelFileDescriptor mWriteFd;
        final FileOutputStream mWriteStream;
        final PrintWriter mWritePrinter;
        final Thread mReaderThread;

        // Shared state is protected by this.
        final ArrayList<String[]> mPendingLines = new ArrayList<>();

        boolean mStopping;

        WatchUidRunner(Instrumentation instrumentation, int uid) {
            mInstrumentation = instrumentation;
            mUid = uid;
            mUidStr = Integer.toString(uid);
            mDefaultWaitTime = 15_000;
            mSpaceSplitter = Pattern.compile("\\s+");
            ParcelFileDescriptor[] pfds = instrumentation.getUiAutomation().executeShellCommandRw(
                    "am watch-uids --oom " + uid);
            mReadFd = pfds[0];
            mReadStream = new ParcelFileDescriptor.AutoCloseInputStream(mReadFd);
            mReadReader = new BufferedReader(new InputStreamReader(mReadStream));
            mWriteFd = pfds[1];
            mWriteStream = new ParcelFileDescriptor.AutoCloseOutputStream(mWriteFd);
            mWritePrinter = new PrintWriter(new BufferedOutputStream(mWriteStream));
            // Executing a shell command is asynchronous but we can't proceed further with the test
            // until the 'watch-uids' cmd is executed.
            waitUntilUidObserverReady();
            mReaderThread = new ReaderThread();
            mReaderThread.start();
        }

        private void waitUntilUidObserverReady() {
            try {
                final String line = mReadReader.readLine();
                assertTrue("Unexpected output: " + line, line.startsWith("Watching uid states"));
            } catch (IOException e) {
                fail("Error occurred " + e);
            }
        }

        public void waitFor(int cmd) {
            Log.i(TAG, "waitFor(cmd=" + cmd + ", timeout=" + mDefaultWaitTime + ")");
            long waitUntil = SystemClock.uptimeMillis() + mDefaultWaitTime;
            while (true) {
                String[] line = waitForNextLine(waitUntil, cmd);
                if (COMMAND_TO_STRING[cmd].equals(line[1])) {
                    Log.d(TAG, "Waited for: " + Arrays.toString(line));
                    return;
                } else {
                    Log.d(TAG, "Skipping because not " + COMMAND_TO_STRING[cmd] + ": "
                            + Arrays.toString(line));
                }
            }
        }

        String[] waitForNextLine(long waitUntil, int cmd) {
            synchronized (mPendingLines) {
                while (true) {
                    while (mPendingLines.size() == 0) {
                        long now = SystemClock.uptimeMillis();
                        if (now >= waitUntil) {
                            String msg = "Timed out waiting for next line: uid=" + mUidStr
                                    + " cmd=" + COMMAND_TO_STRING[cmd];
                            Log.d(TAG, msg);
                            throw new IllegalStateException(msg);
                        }
                        try {
                            mPendingLines.wait(waitUntil - now);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    String[] res = mPendingLines.remove(0);
                    if (res[0].startsWith("#")) {
                        Log.d(TAG, "Note: " + res[0]);
                    } else {
                        Log.v(TAG, "LINE: " + Arrays.toString(res));
                        return res;
                    }
                }
            }
        }

        @Override
        public void close() throws Exception {
            synchronized (mPendingLines) {
                mStopping = true;
            }
            mWritePrinter.println("q");
            mWriteStream.close();
            mReadStream.close();
        }

        final class ReaderThread extends Thread {
            String mLastReadLine;

            @Override
            public void run() {
                String[] line;
                try {
                    while ((line = readNextLine()) != null) {
                        boolean comment = line.length == 1 && line[0].startsWith("#");
                        if (!comment) {
                            if (line.length < 2) {
                                Log.d(TAG, "Skipping short line: " + mLastReadLine);
                                continue;
                            }
                            if (!line[0].equals(mUidStr)) {
                                Log.d(TAG, "Skipping ignored uid: " + mLastReadLine);
                                continue;
                            }
                        }
                        synchronized (mPendingLines) {
                            if (mStopping) {
                                return;
                            }
                            mPendingLines.add(line);
                            mPendingLines.notifyAll();
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed reading", e);
                }
            }

            String[] readNextLine() throws IOException {
                mLastReadLine = mReadReader.readLine();
                if (mLastReadLine == null) {
                    return null;
                }
                if (mLastReadLine.startsWith("#")) {
                    return new String[] { mLastReadLine };
                }
                return mSpaceSplitter.split(mLastReadLine);
            }
        }
    }
}
