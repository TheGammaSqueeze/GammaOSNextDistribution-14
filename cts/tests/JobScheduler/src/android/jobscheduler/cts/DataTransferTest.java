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

import android.app.job.JobInfo;
import android.os.UserHandle;

import com.android.compatibility.common.util.SystemUtil;

/** Tests related to data transfer jobs. */
public class DataTransferTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = DataTransferTest.class.hashCode();

    private NetworkingHelper mNetworkingHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);

        mNetworkingHelper.tearDown();

        // The super method should be called at the end.
        super.tearDown();
    }

    public void testUpdateEstimatedNetworkBytes() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, getEstimatedDownloadBytes());
        assertEquals(JobInfo.NETWORK_BYTES_UNKNOWN, getEstimatedUploadBytes());

        kTestEnvironment.setEstimatedNetworkBytes(5, 10);

        assertEquals(5, getEstimatedDownloadBytes());
        assertEquals(10, getEstimatedUploadBytes());
    }

    public void testUpdateTransferredNetworkBytes() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        assertEquals(0, getTransferredDownloadBytes());
        assertEquals(0, getTransferredUploadBytes());

        kTestEnvironment.setTransferredNetworkBytes(5, 10);

        assertEquals(5, getTransferredDownloadBytes());
        assertEquals(10, getTransferredUploadBytes());
    }

    private long getEstimatedDownloadBytes() throws Exception {
        return getBytes("get-estimated-download-bytes");
    }

    private long getEstimatedUploadBytes() throws Exception {
        return getBytes("get-estimated-upload-bytes");
    }

    private long getTransferredDownloadBytes() throws Exception {
        return getBytes("get-transferred-download-bytes");
    }

    private long getTransferredUploadBytes() throws Exception {
        return getBytes("get-transferred-upload-bytes");
    }

    private long getBytes(String option) throws Exception {
        String output = SystemUtil.runShellCommand(getInstrumentation(),
                        "cmd jobscheduler"
                                + " " + option
                                + " -u " + UserHandle.myUserId()
                                + " " + kJobServiceComponent.getPackageName()
                                + " " + JOB_ID)
                .trim();
        return Long.parseLong(output);
    }
}
