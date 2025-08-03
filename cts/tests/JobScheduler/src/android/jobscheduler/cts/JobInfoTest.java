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

import static android.jobscheduler.cts.TestAppInterface.ENFORCE_MINIMUM_TIME_WINDOWS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import android.app.compat.CompatChanges;
import android.app.job.Flags;
import android.app.job.JobInfo;
import android.content.ClipData;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Tests related to creating and reading JobInfo objects.
 */
public class JobInfoTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = JobInfoTest.class.hashCode();
    private static final String TAG = JobInfoTest.class.getSimpleName();

    private static final long REJECT_NEGATIVE_DELAYS_AND_DEADLINES = 323349338L;
    private static final long THROW_ON_UNSUPPORTED_BIAS_USAGE = 300477393L;

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);

        // The super method should be called at the end.
        super.tearDown();
    }

    public void testBackoffCriteria() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setBackoffCriteria(12345, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        assertEquals(12345, ji.getInitialBackoffMillis());
        assertEquals(JobInfo.BACKOFF_POLICY_LINEAR, ji.getBackoffPolicy());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setBackoffCriteria(54321, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        assertEquals(54321, ji.getInitialBackoffMillis());
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, ji.getBackoffPolicy());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testBatteryNotLow() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(true)
                .build();
        assertTrue(ji.isRequireBatteryNotLow());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(false)
                .build();
        assertFalse(ji.isRequireBatteryNotLow());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testBias() throws Exception {
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);

        Method setBiasMethod = JobInfo.Builder.class.getDeclaredMethod("setBias", int.class);
        setBiasMethod.setAccessible(true);
        setBiasMethod.invoke(builder, 40);

        JobInfo ji = builder.build();
        // Confirm JobScheduler rejects the JobInfo object.
        // TODO(309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(THROW_ON_UNSUPPORTED_BIAS_USAGE)) {
            assertScheduleFailsWithException(
                    "Successfully scheduled a job with a modified bias",
                    ji, SecurityException.class);
        } else {
            mJobScheduler.schedule(ji);

            assertEquals("Bias wasn't changed to default",
                    0, getBias(mJobScheduler.getPendingJob(JOB_ID)));
        }
    }

    private int getBias(JobInfo job) throws Exception {
        Method getBiasMethod = JobInfo.class.getDeclaredMethod("getBias");
        getBiasMethod.setAccessible(true);
        return (Integer) getBiasMethod.invoke(job);
    }

    public void testCharging() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .build();
        assertTrue(ji.isRequireCharging());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(false)
                .build();
        assertFalse(ji.isRequireCharging());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testClipData() {
        final ClipData clipData = ClipData.newPlainText("test", "testText");
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setClipData(clipData, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .build();
        assertEquals(clipData, ji.getClipData());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, ji.getClipGrantFlags());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setClipData(null, 0)
                .build();
        assertNull(ji.getClipData());
        assertEquals(0, ji.getClipGrantFlags());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    // TODO(315035390): migrate to JUnit4
    @RequiresFlagsEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS) // Doesn't work for JUnit3
    public void testDebugTags() {
        if (!isAconfigFlagEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS)) {
            return;
        }
        // Confirm defaults
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        assertEquals(0, ji.getDebugTags().size());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag("a")
                .addDebugTag("b")
                .addDebugTag("c")
                .build();
        assertEquals(Set.of("a", "b", "c"), ji.getDebugTags());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag("a")
                .addDebugTag("b")
                .addDebugTag("c")
                .removeDebugTag("b")
                .build();
        assertEquals(Set.of("a", "c"), ji.getDebugTags());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Tag is at the character limit
        final String maxLengthDebugTag =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
                        + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag(maxLengthDebugTag)
                .build();
        assertEquals(Set.of(maxLengthDebugTag), ji.getDebugTags());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).addDebugTag(null).build();
            fail("Successfully built a JobInfo with a null debug tag");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).addDebugTag("").build();
            fail("Successfully built a JobInfo with an empty debug tag");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).addDebugTag("        ").build();
            fail("Successfully built a JobInfo with a whitespace-only debug tag");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                    .setTraceTag(maxLengthDebugTag + "x").build();
            fail("Successfully built a JobInfo with a long debug tag");
        } catch (Exception e) {
            // Success
        }
        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);
        for (int i = 0; i < 33; ++i) {
            jiBuilder.addDebugTag(Integer.toString(i));
        }
        assertBuildFails("Successfully built a JobInfo with too many debug tags", jiBuilder);
    }

    public void testDeviceIdle() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresDeviceIdle(true)
                .build();
        assertTrue(ji.isRequireDeviceIdle());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresDeviceIdle(false)
                .build();
        assertFalse(ji.isRequireDeviceIdle());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testEstimatedNetworkBytes() {
        assertBuildFails(
                "Successfully built a JobInfo specifying estimated network bytes without"
                        + " requesting network",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setEstimatedNetworkBytes(500, 1000));

        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying a negative download bytes value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setEstimatedNetworkBytes(-500, JobInfo.NETWORK_BYTES_UNKNOWN));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying a negative upload bytes value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, -500));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(500, 1000)
                .build();
        assertEquals(500, ji.getEstimatedNetworkDownloadBytes());
        assertEquals(1000, ji.getEstimatedNetworkUploadBytes());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, ji.getEstimatedNetworkDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, ji.getEstimatedNetworkUploadBytes());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testExtras() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .setExtras(pb)
                .build();
        final PersistableBundle extras = ji.getExtras();
        assertNotNull(extras);
        assertEquals(1, extras.keySet().size());
        assertEquals(42, extras.getInt("random_key"));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testExpeditedJob() {
        // Test all allowed constraints.
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresStorageNotLow(true)
                .build();
        assertTrue(ji.isExpedited());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm default priority for EJs.
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();
        assertEquals(JobInfo.PRIORITY_MAX, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Test disallowed constraints.
        final String failureMessage =
                "Successfully built an expedited JobInfo object with disallowed constraints";
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setMinimumLatency(100));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPeriodic(15 * 60_000));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPriority(JobInfo.PRIORITY_LOW));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPriority(JobInfo.PRIORITY_DEFAULT));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setImportantWhileForeground(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPrefetch(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresDeviceIdle(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresBatteryNotLow(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresCharging(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setUserInitiated(true));
        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                Uri.parse("content://" + MediaStore.AUTHORITY + "/"),
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .addTriggerContentUri(tcu));
    }

    @SuppressWarnings("deprecation")
    public void testImportantWhileForeground() {
        // Assert the default value is false
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertFalse(ji.isImportantWhileForeground());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setImportantWhileForeground(true)
                .build();
        assertTrue(ji.isImportantWhileForeground());
        assertEquals(JobInfo.PRIORITY_HIGH, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setImportantWhileForeground(false)
                .build();
        assertFalse(ji.isImportantWhileForeground());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testMinimumChunkSizeBytes() {
        assertBuildFails(
                "Successfully built a JobInfo specifying minimum chunk bytes without"
                        + " requesting network",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumNetworkChunkBytes(500));
        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying minimum chunk bytes a negative value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setMinimumNetworkChunkBytes(-500));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(5, 5));
        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, 5));
        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(5, JobInfo.NETWORK_BYTES_UNKNOWN));

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumNetworkChunkBytes(500)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();
        assertEquals(500, ji.getMinimumNetworkChunkBytes());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testMinimumLatency() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(1337)
                .build();
        assertEquals(1337, ji.getMinLatencyMillis());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testMinimumLatency_negative() {
        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(-1);

        // TODO(309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(REJECT_NEGATIVE_DELAYS_AND_DEADLINES)) {
            assertBuildFails("Successfully scheduled a job with a negative latency", jiBuilder);
        } else {
            // Confirm JobScheduler accepts the JobInfo object.
            JobInfo ji = jiBuilder.build();
            assertEquals(0, ji.getMinLatencyMillis());
            mJobScheduler.schedule(ji);
        }
    }

    public void testOverrideDeadline() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setOverrideDeadline(HOUR_IN_MILLIS)
                .build();
        // ...why are the set/get methods named differently?? >.>
        assertEquals(HOUR_IN_MILLIS, ji.getMaxExecutionDelayMillis());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testOverrideDeadline_minimumTimeWindows() throws Exception {
        JobInfo.Builder jiBuilderShortFunctional = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .setMinimumLatency(MINUTE_IN_MILLIS)
                .setOverrideDeadline(16 * MINUTE_IN_MILLIS - 1);
        JobInfo.Builder jiBuilderShortNonfunctional =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(MINUTE_IN_MILLIS)
                        .setOverrideDeadline(16 * MINUTE_IN_MILLIS - 1);
        JobInfo.Builder jiBuilderLongFunctional = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .setMinimumLatency(MINUTE_IN_MILLIS)
                .setOverrideDeadline(16 * MINUTE_IN_MILLIS);
        JobInfo.Builder jiBuilderLongNonfunctional =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(MINUTE_IN_MILLIS)
                        .setOverrideDeadline(16 * MINUTE_IN_MILLIS);

        // TODO(309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(ENFORCE_MINIMUM_TIME_WINDOWS) && isAconfigFlagEnabled(
                "android.app.job.enforce_minimum_time_windows")) {
            // Confirm JobScheduler rejects the bad JobInfo objects.
            assertBuildFails(
                    "Successfully scheduled a job with a short deadline and functional constraints",
                    jiBuilderShortFunctional);
        } else {
            // Confirm JobScheduler accepts the JobInfo objects.
            mJobScheduler.schedule(jiBuilderShortFunctional.build());
        }
        // Confirm JobScheduler accepts the good JobInfo objects.
        mJobScheduler.schedule(jiBuilderShortNonfunctional.build());
        mJobScheduler.schedule(jiBuilderLongFunctional.build());
        mJobScheduler.schedule(jiBuilderLongNonfunctional.build());
    }

    public void testOverrideDeadline_negative() {
        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setOverrideDeadline(-1);

        // TODO(309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(REJECT_NEGATIVE_DELAYS_AND_DEADLINES)) {
            assertBuildFails("Successfully scheduled a job with a negative deadline", jiBuilder);
        } else {
            // Confirm JobScheduler accepts the JobInfo object.
            JobInfo ji = jiBuilder.build();
            assertTrue(ji.getMaxExecutionDelayMillis() >= 0);
            mJobScheduler.schedule(ji);
        }
    }

    public void testPeriodic() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPeriodic(60 * 60 * 1000L)
                .build();
        assertTrue(ji.isPeriodic());
        assertEquals(60 * 60 * 1000L, ji.getIntervalMillis());
        assertEquals(60 * 60 * 1000L, ji.getFlexMillis());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPeriodic(120 * 60 * 1000L, 20 * 60 * 1000L)
                .build();
        assertTrue(ji.isPeriodic());
        assertEquals(120 * 60 * 1000L, ji.getIntervalMillis());
        assertEquals(20 * 60 * 1000L, ji.getFlexMillis());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testPersisted() {
        // Assert the default value is false
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertFalse(ji.isPersisted());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();
        assertTrue(ji.isPersisted());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(false)
                .build();
        assertFalse(ji.isPersisted());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testPrefetch() {
        // Assert the default value is false
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertFalse(ji.isPrefetch());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPrefetch(true)
                .build();
        assertTrue(ji.isPrefetch());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPrefetch(false)
                .build();
        assertFalse(ji.isPrefetch());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000L)
                .setPrefetch(true)
                .build();
        assertTrue(ji.isPrefetch());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // CTS naturally targets latest SDK version. Compat change should be enabled by default.
        assertBuildFails("Modern prefetch jobs can't have a deadline",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000L)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS)
                        .setPrefetch(true));
    }

    public void testPriority() {
        // Assert the default value is DEFAULT
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertEquals(JobInfo.PRIORITY_DEFAULT, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_LOW)
                .build();
        assertEquals(JobInfo.PRIORITY_LOW, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_MIN)
                .build();
        assertEquals(JobInfo.PRIORITY_MIN, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Attempt an invalid number
        try {
            // It's over 9000!!!
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(9001).build();
            fail("Successfully built a job with an invalid priority level");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(-1).build();
            fail("Successfully built a job with an invalid priority level");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(123).build();
            fail("Successfully built a job with an invalid priority level");
        } catch (Exception e) {
            // Success
        }

        // Test other invalid configurations.
        final String failureMessage =
                "Successfully built a JobInfo object with disallowed priority configurations";
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_MAX));
        //noinspection deprecation
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .setImportantWhileForeground(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPrefetch(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPeriodic(JobInfo.getMinPeriodMillis()));
    }

    public void testRequiredNetwork() {
        final NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetwork(nr)
                .build();
        assertEquals(nr, ji.getRequiredNetwork());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetwork(null)
                .build();
        assertNull(ji.getRequiredNetwork());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @SuppressWarnings("deprecation")
    public void testRequiredNetworkType() {
        // Assert the default value is NONE
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_NONE, ji.getNetworkType());
        assertNull(ji.getRequiredNetwork());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_ANY, ji.getNetworkType());
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, ji.getNetworkType());
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_NOT_ROAMING, ji.getNetworkType());
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_CELLULAR, ji.getNetworkType());
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertTrue(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(ji.getRequiredNetwork()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        assertEquals(JobInfo.NETWORK_TYPE_NONE, ji.getNetworkType());
        assertNull(ji.getRequiredNetwork());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testStorageNotLow() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();
        assertTrue(ji.isRequireStorageNotLow());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(false)
                .build();
        assertFalse(ji.isRequireStorageNotLow());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    // TODO(315035390): migrate to JUnit4
    @RequiresFlagsEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS) // Doesn't work for JUnit3
    public void testTraceTag() {
        if (!isAconfigFlagEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS)) {
            return;
        }
        // Confirm defaults
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        assertNull(ji.getTraceTag());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTraceTag("tracing").build();
        assertEquals("tracing", ji.getTraceTag());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Tag is at the character limit
        final String maxLengthTraceTag =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
                        + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTraceTag(maxLengthTraceTag)
                .build();
        assertEquals(maxLengthTraceTag, ji.getTraceTag());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTraceTag(null)
                .build();
        assertNull(null, ji.getTraceTag());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTraceTag("").build();
            fail("Successfully built a JobInfo with an empty trace tag");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTraceTag("        ").build();
            fail("Successfully built a JobInfo with a whitespace-only trace tag");
        } catch (Exception e) {
            // Success
        }
        try {
            new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                    .setTraceTag(maxLengthTraceTag + "x").build();
            fail("Successfully built a JobInfo with a long trace tag");
        } catch (Exception e) {
            // Success
        }
    }

    public void testTransientExtras() {
        final Bundle b = new Bundle();
        b.putBoolean("random_bool", true);
        assertBuildFails("Successfully built a persisted JobInfo object with transient extras",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPersisted(true)
                        .setTransientExtras(b));

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTransientExtras(b)
                .build();
        assertEquals(b.size(), ji.getTransientExtras().size());
        for (String key : b.keySet()) {
            assertEquals(b.get(key), ji.getTransientExtras().get(key));
        }
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testTriggerContentMaxDelay() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTriggerContentMaxDelay(1337)
                .build();
        assertEquals(1337, ji.getTriggerContentMaxDelay());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testTriggerContentUpdateDelay() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTriggerContentUpdateDelay(1337)
                .build();
        assertEquals(1337, ji.getTriggerContentUpdateDelay());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testTriggerContentUri() {
        final Uri u = Uri.parse("content://" + MediaStore.AUTHORITY + "/");
        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                u, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        assertEquals(u, tcu.getUri());
        assertEquals(JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS, tcu.getFlags());
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(tcu)
                .build();
        assertEquals(1, ji.getTriggerContentUris().length);
        assertEquals(tcu, ji.getTriggerContentUris()[0]);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        final Uri u2 = Uri.parse("content://" + ContactsContract.AUTHORITY + "/");
        final JobInfo.TriggerContentUri tcu2 = new JobInfo.TriggerContentUri(u2, 0);
        assertEquals(u2, tcu2.getUri());
        assertEquals(0, tcu2.getFlags());
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(tcu)
                .addTriggerContentUri(tcu2)
                .build();
        assertEquals(2, ji.getTriggerContentUris().length);
        assertEquals(tcu, ji.getTriggerContentUris()[0]);
        assertEquals(tcu2, ji.getTriggerContentUris()[1]);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    public void testUserInitiatedJob() {
        // Test all allowed constraints.
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPriority(JobInfo.PRIORITY_MAX)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build();
        assertTrue(ji.isUserInitiated());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm default priority for UIJs.
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEquals(JobInfo.PRIORITY_MAX, ji.getPriority());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm linear backoff allowed
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        assertTrue(ji.isUserInitiated());
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Test disallowed constraints.
        final String failureMessage =
                "Successfully built a user-initiated JobInfo object with disallowed constraints";

        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(100));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(15 * 60_000));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_LOW));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_HIGH));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_DEFAULT));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setImportantWhileForeground(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPrefetch(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setRequiresDeviceIdle(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY));
        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                Uri.parse("content://" + MediaStore.AUTHORITY + "/"),
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .addTriggerContentUri(tcu));
    }

    private void assertBuildFails(String message, JobInfo.Builder builder) {
        try {
            builder.build();
            fail(message);
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    private void assertScheduleFailsWithException(
            String message, JobInfo jobInfo, Class<? extends Exception> expectedExceptionClass) {
        try {
            mJobScheduler.schedule(jobInfo);
            fail(message);
        } catch (Exception e) {
            if (expectedExceptionClass.isInstance(e)) {
                // Expected
            } else {
                fail("Scheduling failed with wrong exception class."
                        + " Got " + e.getClass().getSimpleName()
                        + ", wanted " + expectedExceptionClass.getSimpleName());
            }
        }
    }

    private boolean isAconfigFlagEnabled(String fullFlagName) {
        final String ogValue = SystemUtil.runShellCommand(
                "cmd jobscheduler get-aconfig-flag-state " + fullFlagName).trim();
        final boolean enabled = Boolean.parseBoolean(ogValue);
        Log.d(TAG, fullFlagName + "=" + ogValue  + " ... enabled=" + enabled);
        return enabled;
    }
}
