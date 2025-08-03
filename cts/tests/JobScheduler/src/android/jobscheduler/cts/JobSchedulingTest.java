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

package android.jobscheduler.cts;

import static android.Manifest.permission.RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.OP_RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.opToPermission;
import static android.jobscheduler.cts.JobThrottlingTest.setTestPackageStandbyBucket;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.jobscheduler.MockJobService.TestEnvironment;
import android.jobscheduler.MockJobService.TestEnvironment.Event;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests related to scheduling jobs.
 */
@TargetApi(30)
public class JobSchedulingTest extends BaseJobSchedulerTest {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final int MIN_SCHEDULE_QUOTA = 250;
    private static final int JOB_ID = JobSchedulingTest.class.hashCode();
    // The maximum number of jobs that can run concurrently.
    private static final int MAX_JOB_CONTEXTS_COUNT = 64;

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler reset-schedule-quota");
        BatteryUtils.runDumpsysBatteryReset();
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);

        // The super method should be called at the end.
        super.tearDown();
    }

    /** Tests that an ANR happens if the job is blocked in onStartJob. */
    public void testAnr_onStartJob() throws Exception {
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                     TEST_APP_PACKAGE)) {

            setTestPackageStandbyBucket(
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
                    JobThrottlingTest.Bucket.ACTIVE);
            SystemUtil.runShellCommand(getInstrumentation(),
                    "am compat enable ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES " + TEST_APP_PACKAGE);

            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                            TestJobSchedulerReceiver.EXTRA_SLOW_START, true
                    ),
                    Collections.emptyMap());

            mTestAppInterface.forceRunJob();
            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    /** Tests that an ANR happens if the job is blocked in onStopJob. */
    public void testAnr_onStopJob() throws Exception {
        try (TestAppInterface mTestAppInterface = new TestAppInterface(mContext, JOB_ID);
             AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                     TEST_APP_PACKAGE)) {

            setTestPackageStandbyBucket(
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
                    JobThrottlingTest.Bucket.ACTIVE);
            SystemUtil.runShellCommand(getInstrumentation(),
                    "am compat enable ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES " + TEST_APP_PACKAGE);

            mTestAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                            TestJobSchedulerReceiver.EXTRA_SLOW_STOP, true
                    ),
                    Collections.emptyMap());

            mTestAppInterface.forceRunJob();
            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));

            mTestAppInterface.cancelJob();

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    public void testCancel_runningJob() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setExpectedStopped();
        kTestEnvironment.setRequestReschedule();
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        mJobScheduler.cancelAll();
        assertTrue("Job didn't start", kTestEnvironment.awaitStopped());
        Thread.sleep(5000); // Give some time for JS to finish its internal processing.
        assertEquals(0, mJobScheduler.getAllPendingJobs().size());
    }

    public void testCanRunUserInitiatedJobs() throws Exception {
        final boolean isAppOpPermission = isRunUserInitiatedJobsPermissionAppOp();

        // Default is allowed.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);
        assertTrue(mJobScheduler.canRunUserInitiatedJobs());

        // Toggle the appop won't make a change of JobScheduler#canRunUserInitiatedJobs if it's not
        // an appop permission.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ERRORED);
        assertTrue(isAppOpPermission ^ mJobScheduler.canRunUserInitiatedJobs());

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ALLOWED);
        assertTrue(mJobScheduler.canRunUserInitiatedJobs());

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_IGNORED);
        assertTrue(isAppOpPermission ^ mJobScheduler.canRunUserInitiatedJobs());
    }

    /**
     * Test that apps can call schedule at least the minimum amount of times without being
     * blocked.
     */
    public void testMinSuccessfulSchedulingQuota() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < MIN_SCHEDULE_QUOTA; ++i) {
            assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that scheduling fails once an app hits the schedule quota limit.
     */
    public void testFailingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 300000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < 500; ++i) {
            final int expected =
                    i < 300 ? JobScheduler.RESULT_SUCCESS : JobScheduler.RESULT_FAILURE;
            assertEquals("Got unexpected result for schedule #" + (i + 1),
                    expected, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that scheduling succeeds even after an app hits the schedule quota limit.
     */
    public void testContinuingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setBoolean("enable_api_quotas", true)
                        .setInt("aq_schedule_count", 300)
                        .setLong("aq_schedule_window_ms", 300000)
                        .setBoolean("aq_schedule_throw_exception", false)
                        .setBoolean("aq_schedule_return_failure", false)
                        .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < 500; ++i) {
            assertEquals("Got unexpected result for schedule #" + (i + 1),
                    JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that non-persisted jobs aren't limited by quota.
     */
    public void testNonPersistedJobsNotLimited() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 60000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(false)
                .build();

        for (int i = 0; i < 500; ++i) {
            assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    public void testHigherPriorityJobRunsFirst() throws Exception {
        setStorageStateLow(true);
        final int higherPriorityJobId = JOB_ID;
        final int numMinPriorityJobs = 2 * MAX_JOB_CONTEXTS_COUNT;
        kTestEnvironment.setExpectedExecutions(1 + numMinPriorityJobs);
        for (int i = 0; i < numMinPriorityJobs; ++i) {
            JobInfo job = new JobInfo.Builder(higherPriorityJobId + 1 + i, kJobServiceComponent)
                    .setPriority(JobInfo.PRIORITY_MIN)
                    .setRequiresStorageNotLow(true)
                    .build();
            mJobScheduler.schedule(job);
        }
        // Schedule the higher priority job last since the default sorting is by enqueue time.
        JobInfo jobMax = new JobInfo.Builder(higherPriorityJobId, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_DEFAULT)
                .setRequiresStorageNotLow(true)
                .build();
        mJobScheduler.schedule(jobMax);

        setStorageStateLow(false);
        kTestEnvironment.awaitExecution();

        Event jobHigherExecution = new Event(TestEnvironment.Event.EVENT_START_JOB,
                higherPriorityJobId);
        List<Event> executedEvents = kTestEnvironment.getExecutedEvents();
        boolean higherExecutedFirst = false;
        // Due to racing, we can't just check the very first item in the array. We can however
        // make sure it was in the first set of jobs to run.
        for (int i = 0; i < executedEvents.size() && i < MAX_JOB_CONTEXTS_COUNT; ++i) {
            if (executedEvents.get(i).equals(jobHigherExecution)) {
                higherExecutedFirst = true;
                break;
            }
        }
        assertTrue(
                "Higher priority job (" + higherPriorityJobId + ") didn't run in first batch: "
                        + executedEvents, higherExecutedFirst);
    }

    public void testNamespaceSetting() {
        JobScheduler js = getContext().getSystemService(JobScheduler.class);
        assertNull(js.getNamespace());

        js = js.forNamespace("A");
        assertEquals("A", js.getNamespace());
        js = js.forNamespace("B");
        assertEquals("B", js.getNamespace());
        js = js.forNamespace("AB");
        assertEquals("AB", js.getNamespace());
        js = js.forNamespace("A");
        assertEquals("A", js.getNamespace());

        js = getContext().getSystemService(JobScheduler.class);
        assertNull(js.getNamespace());

        try {
            js.forNamespace(null);
            fail("Successfully retrieved instance with null namespace");
        } catch (NullPointerException expected) {
            // Expected
        }
        try {
            js.forNamespace("");
            fail("Successfully retrieved instance with empty namespace");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
        try {
            js.forNamespace("        ");
            fail("Successfully retrieved instance with whitespace-only namespace");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
    }

    public void testNamespace_schedule() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .build();
        JobInfo jobB = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_001)
                .setPriority(JobInfo.PRIORITY_LOW)
                .setPersisted(true)
                .build();

        assertNotEquals(jobA, jobB);
        assertEquals(JobScheduler.RESULT_SUCCESS, jsA.schedule(jobA));
        assertEquals(JobScheduler.RESULT_SUCCESS, jsB.schedule(jobB));

        assertNull(mJobScheduler.getPendingJob(JOB_ID));
        assertEquals(jobA, jsA.getPendingJob(JOB_ID));
        assertEquals(jobB, jsB.getPendingJob(JOB_ID));

        // App global
        Map<String, List<JobInfo>> allJobs = mJobScheduler.getPendingJobsInAllNamespaces();
        Map<String, List<JobInfo>> allJobsA = jsA.getPendingJobsInAllNamespaces();
        Map<String, List<JobInfo>> allJobsB = jsB.getPendingJobsInAllNamespaces();
        assertEquals(allJobs, allJobsA);
        assertEquals(allJobsA, allJobsB);
        assertEquals(2, allJobsA.size());
        assertEquals(1, allJobsA.get("A").size());
        assertEquals(1, allJobsA.get("B").size());
        assertTrue(allJobsA.get("A").contains(jobA));
        assertTrue(allJobsA.get("B").contains(jobB));

        // In namespace
        List<JobInfo> namespaceJobs = mJobScheduler.getAllPendingJobs();
        List<JobInfo> namespaceJobsA = jsA.getAllPendingJobs();
        List<JobInfo> namespaceJobsB = jsB.getAllPendingJobs();
        assertNotEquals(namespaceJobsA, namespaceJobsB);
        assertEquals(0, namespaceJobs.size());
        assertEquals(1, namespaceJobsA.size());
        assertEquals(1, namespaceJobsB.size());
        assertTrue(namespaceJobsA.contains(jobA));
        assertTrue(namespaceJobsB.contains(jobB));
    }

    public void testNamespace_cancel() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .build();
        JobInfo jobB = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_001)
                .setPriority(JobInfo.PRIORITY_LOW)
                .setPersisted(true)
                .build();

        assertNotEquals(jobA, jobB);
        assertEquals(JobScheduler.RESULT_SUCCESS, jsA.schedule(jobA));
        assertEquals(JobScheduler.RESULT_SUCCESS, jsB.schedule(jobB));

        jsA.cancel(JOB_ID);
        assertNull(jsA.getPendingJob(JOB_ID));
        assertEquals(jobB, jsB.getPendingJob(JOB_ID));

        jsB.cancel(JOB_ID);
        assertNull(jsA.getPendingJob(JOB_ID));
        assertNull(jsB.getPendingJob(JOB_ID));
    }

    public void testNamespace_cancelInAllNamespaces() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .build();
        JobInfo jobB = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_001)
                .setPriority(JobInfo.PRIORITY_LOW)
                .setPersisted(true)
                .build();

        assertNotEquals(jobA, jobB);
        assertEquals(JobScheduler.RESULT_SUCCESS, jsA.schedule(jobA));
        assertEquals(JobScheduler.RESULT_SUCCESS, jsB.schedule(jobB));

        mJobScheduler.cancelInAllNamespaces();
        assertNull(jsA.getPendingJob(JOB_ID));
        assertNull(jsB.getPendingJob(JOB_ID));
    }

    public void testNamespace_cancelAllInNamespace() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .build();
        JobInfo jobB = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_001)
                .setPriority(JobInfo.PRIORITY_LOW)
                .setPersisted(true)
                .build();

        assertNotEquals(jobA, jobB);
        assertEquals(JobScheduler.RESULT_SUCCESS, jsA.schedule(jobA));
        assertEquals(JobScheduler.RESULT_SUCCESS, jsB.schedule(jobB));

        mJobScheduler.cancelAll();
        assertEquals(jobA, jsA.getPendingJob(JOB_ID));
        assertEquals(jobB, jsB.getPendingJob(JOB_ID));

        jsA.cancelAll();
        assertNull(jsA.getPendingJob(JOB_ID));
        assertEquals(jobB, jsB.getPendingJob(JOB_ID));

        jsB.cancelAll();
        assertNull(jsA.getPendingJob(JOB_ID));
        assertNull(jsB.getPendingJob(JOB_ID));
    }

    public void testPendingJobReason_noJob() {
        assertEquals(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_alreadyRunning() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        assertEquals(JobScheduler.PENDING_JOB_REASON_EXECUTING,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_batteryNotLow() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }

        setBatteryState(false, 5);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_charging() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }

        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_connectivity() throws Exception {
        final NetworkingHelper networkingHelper =
                new NetworkingHelper(getInstrumentation(), getContext());
        if (networkingHelper.hasEthernetConnection()) {
            // Can't test while there's an active ethernet connection.
            return;
        }

        try {
            networkingHelper.setAllNetworksEnabled(false);
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();

            mJobScheduler.schedule(jobInfo);

            assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY,
                    mJobScheduler.getPendingJobReason(JOB_ID));
        } finally {
            networkingHelper.tearDown();
        }
    }

    public void testPendingJobReason_contentTrigger() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        TriggerContentTest.MEDIA_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_minimumLatency() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testPendingJobReason_storageNotLow() throws Exception {
        setStorageStateLow(true);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    /** Verify that any caching isn't JobScheduler doesn't result in returning invalid reasons. */
    public void testPendingJobReason_reasonCanChange() throws Exception {
        assertEquals(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID,
                mJobScheduler.getPendingJobReason(JOB_ID));

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY,
                mJobScheduler.getPendingJobReason(JOB_ID));

        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        assertEquals(JobScheduler.PENDING_JOB_REASON_EXECUTING,
                mJobScheduler.getPendingJobReason(JOB_ID));

        mJobScheduler.cancel(JOB_ID);
        assertEquals(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID,
                mJobScheduler.getPendingJobReason(JOB_ID));

        setStorageStateLow(true);
        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertEquals(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW,
                mJobScheduler.getPendingJobReason(JOB_ID));
    }

    public void testRunUserInitiatedJobsPermissionRequirement() throws Exception {
        startAndKeepTestActivity();
        final boolean isAppOpPermission = isRunUserInitiatedJobsPermissionAppOp();
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        // Default is allowed.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);
        assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(ji));

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ERRORED);
        if (isAppOpPermission) {
            try {
                mJobScheduler.schedule(ji);
                fail("Successfully scheduled user-initiated job without permission");
            } catch (Exception expected) {
                // Success
            }
        } else {
            assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(ji));
        }

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ALLOWED);
        assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(ji));

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_IGNORED);
        // TODO(263159631): uncomment to enable testing this scenario
        // assertEquals(JobScheduler.RESULT_FAILURE, mJobScheduler.schedule(ji));
    }

    /**
     * @return {@code true} if the RUN_USER_INITIATED_JOBS is an appop permission.
     */
    private boolean isRunUserInitiatedJobsPermissionAppOp() {
        return TextUtils.equals(RUN_USER_INITIATED_JOBS,
                opToPermission(OP_RUN_USER_INITIATED_JOBS));
    }
}
