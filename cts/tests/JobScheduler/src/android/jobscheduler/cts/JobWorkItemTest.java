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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.Intent;
import android.jobscheduler.MockJobService;
import android.os.PersistableBundle;

import java.util.List;

/**
 * Tests related to created and reading JobWorkItem objects.
 */
public class JobWorkItemTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = JobWorkItemTest.class.hashCode();
    private static final Intent TEST_INTENT = new Intent("some.random.action");

    public void testAllInfoGivenToJob() throws Exception {
        final JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        final JobWorkItem expectedJwi = new JobWorkItem.Builder()
                .setIntent(TEST_INTENT)
                .setExtras(pb)
                .setEstimatedNetworkBytes(30, 20)
                .setMinimumNetworkChunkBytes(5)
                .build();
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, expectedJwi.getDeliveryCount());

        try (NetworkingHelper networkingHelper =
                     new NetworkingHelper(getInstrumentation(), getContext())) {
            networkingHelper.setAllNetworksEnabled(true);
            kTestEnvironment.setExpectedExecutions(1);
            kTestEnvironment.setExpectedWork(new MockJobService.TestWorkItem[]{
                    new MockJobService.TestWorkItem(TEST_INTENT)});
            kTestEnvironment.readyToWork();
            mJobScheduler.enqueue(jobInfo, expectedJwi);
            runSatisfiedJob(JOB_ID);
            assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());
        }

        List<JobWorkItem> executedJwis = kTestEnvironment.getLastReceivedWork();
        assertEquals(1, executedJwis.size());
        final JobWorkItem actualJwi = executedJwis.get(0);
        assertEquals(1, actualJwi.getDeliveryCount());
        final Intent actualIntent = actualJwi.getIntent();
        assertNotNull(actualIntent);
        assertEquals(TEST_INTENT.getAction(), actualIntent.getAction());
        final PersistableBundle extras = actualJwi.getExtras();
        assertNotNull(extras);
        assertEquals(1, extras.keySet().size());
        assertEquals(42, extras.getInt("random_key"));
        assertEquals(30, actualJwi.getEstimatedNetworkDownloadBytes());
        assertEquals(20, actualJwi.getEstimatedNetworkUploadBytes());
        assertEquals(5, actualJwi.getMinimumNetworkChunkBytes());
    }

    public void testIntentOnlyItem_builder() {
        JobWorkItem jwi = new JobWorkItem.Builder().setIntent(TEST_INTENT).build();

        assertEquals(TEST_INTENT, jwi.getIntent());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkUploadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getMinimumNetworkChunkBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());
    }

    public void testIntentOnlyItem_ctor() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT);

        assertEquals(TEST_INTENT, jwi.getIntent());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkUploadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getMinimumNetworkChunkBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());
    }

    public void testItemWithEstimatedBytes_builder() {
        try {
            new JobWorkItem.Builder().setEstimatedNetworkBytes(-10, 20).build();
            fail("Successfully created JobWorkItem with negative download bytes value");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        try {
            new JobWorkItem.Builder().setEstimatedNetworkBytes(10, -20).build();
            fail("Successfully created JobWorkItem with negative upload bytes value");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        JobWorkItem jwi = new JobWorkItem.Builder().setEstimatedNetworkBytes(10, 20).build();
        assertNull(jwi.getIntent());
        assertEquals(10, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(20, jwi.getEstimatedNetworkUploadBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());
    }

    public void testItemWithEstimatedBytes_ctor() {
        try {
            new JobWorkItem(TEST_INTENT, -10, 20);
            fail("Successfully created JobWorkItem with negative download bytes value");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        try {
            new JobWorkItem(TEST_INTENT, 10, -20);
            fail("Successfully created JobWorkItem with negative upload bytes value");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 20);

        assertEquals(TEST_INTENT, jwi.getIntent());
        assertEquals(10, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(20, jwi.getEstimatedNetworkUploadBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());
    }

    public void testItemWithMinimumChunkBytes_builder() {
        JobWorkItem jwi = new JobWorkItem.Builder().setMinimumNetworkChunkBytes(3).build();

        assertNull(jwi.getIntent());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkUploadBytes());
        assertEquals(3, jwi.getMinimumNetworkChunkBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());

        try {
            new JobWorkItem.Builder().setMinimumNetworkChunkBytes(-3).build();
            fail("Successfully created JobWorkItem with negative minimum chunk value");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem.Builder().setMinimumNetworkChunkBytes(0).build();
            fail("Successfully created JobWorkItem with 0 minimum chunk value");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem.Builder()
                    .setEstimatedNetworkBytes(10, 20)
                    .setMinimumNetworkChunkBytes(50)
                    .build();
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem.Builder()
                    .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, 20)
                    .setMinimumNetworkChunkBytes(25)
                    .build();
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem.Builder()
                    .setEstimatedNetworkBytes(10, JobInfo.NETWORK_BYTES_UNKNOWN)
                    .setMinimumNetworkChunkBytes(15)
                    .build();
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testItemWithMinimumChunkBytes_ctor() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 20, 3);

        assertEquals(TEST_INTENT, jwi.getIntent());
        assertEquals(10, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(20, jwi.getEstimatedNetworkUploadBytes());
        assertEquals(3, jwi.getMinimumNetworkChunkBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        assertTrue(jwi.getExtras().isEmpty());

        try {
            new JobWorkItem(TEST_INTENT, 10, 20, -3);
            fail("Successfully created JobWorkItem with negative minimum chunk value");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem(TEST_INTENT, 10, 20, 0);
            fail("Successfully created JobWorkItem with 0 minimum chunk value");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem(TEST_INTENT, 10, 20, 50);
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem(TEST_INTENT, JobInfo.NETWORK_BYTES_UNKNOWN, 20, 25);
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
        try {
            new JobWorkItem(TEST_INTENT, 10, JobInfo.NETWORK_BYTES_UNKNOWN, 15);
            fail("Successfully created JobWorkItem with minimum chunk value too large");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testItemWithPersistableBundle() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobWorkItem jwi = new JobWorkItem.Builder().setExtras(pb).build();

        assertNull(jwi.getIntent());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getEstimatedNetworkUploadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, jwi.getMinimumNetworkChunkBytes());
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());
        final PersistableBundle extras = jwi.getExtras();
        assertNotNull(extras);
        assertEquals(1, extras.keySet().size());
        assertEquals(42, extras.getInt("random_key"));

        try {
            new JobWorkItem.Builder().setExtras(null).build();
            fail("Successfully created null extras");
        } catch (Exception expected) {
            // Success
        }
    }

    public void testDeliveryCountBumped() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertEquals(0, jwi.getDeliveryCount());

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(new MockJobService.TestWorkItem[]{
                new MockJobService.TestWorkItem(TEST_INTENT)});
        kTestEnvironment.readyToWork();
        mJobScheduler.enqueue(jobInfo, jwi);
        runSatisfiedJob(JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        List<JobWorkItem> executedJWIs = kTestEnvironment.getLastReceivedWork();
        assertEquals(1, executedJWIs.size());
        assertEquals(1, executedJWIs.get(0).getDeliveryCount());
    }

    public void testPersisted_withIntent() {
        JobWorkItem jwi = new JobWorkItem.Builder().setIntent(TEST_INTENT).build();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();
        try {
            mJobScheduler.enqueue(jobInfo, jwi);
            fail("Successfully enqueued persisted JWI with intent");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testPersisted_withPersistableBundle() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobWorkItem jwi = new JobWorkItem.Builder().setExtras(pb).build();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();

        assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.enqueue(jobInfo, jwi));
    }

    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_download() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, JobInfo.NETWORK_BYTES_UNKNOWN);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        try {
            mJobScheduler.enqueue(jobInfo, jwi);
            fail("Successfully scheduled JobWorkItem with network implication"
                    + " and job with no network constraint");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_upload() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        try {
            mJobScheduler.enqueue(jobInfo, jwi);
            fail("Successfully scheduled JobWorkItem with network implication"
                    + " and job with no network constraint");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_minimumChunk() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT,
                JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        try {
            mJobScheduler.enqueue(jobInfo, jwi);
            fail("Successfully scheduled JobWorkItem with network implication"
                    + " and job with no network constraint");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 10, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        try {
            mJobScheduler.enqueue(jobInfo, jwi);
            fail("Successfully scheduled JobWorkItem with network implication"
                    + " and job with no network constraint");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    public void testScheduleItemWithNetworkInfoAndNetworkConstraint() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT,
                JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.enqueue(jobInfo, jwi));
    }
}
