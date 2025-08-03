/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.builtin.job;

import android.annotation.SystemApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobSnapshot;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for JobScheduler related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class JobSchedulerHelper {

    private JobSchedulerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /** Gets the running jobs which are executed when a device goes idle. */
    public static List<JobInfo> getRunningJobsAtIdle(Context context) {
        List<JobInfo> startedJobs = context.getSystemService(JobScheduler.class).getStartedJobs();
        if (startedJobs == null) {
            return new ArrayList<>();
        }
        List<JobInfo> deviceIdleJobs = new ArrayList<>();
        for (int idx = 0; idx < startedJobs.size(); idx++) {
            JobInfo jobInfo = startedJobs.get(idx);
            if (jobInfo.isRequireDeviceIdle()) {
                deviceIdleJobs.add(jobInfo);
            }
        }
        return deviceIdleJobs;
    }

    /** Gets the jobs which are scheduled for execution at idle but not finished. */
    public static List<JobInfo> getPendingJobs(Context context) {
        List<JobSnapshot> allScheduledJobs =
                context.getSystemService(JobScheduler.class).getAllJobSnapshots();
        if (allScheduledJobs == null) {
            return new ArrayList<>();
        }
        List<JobInfo> idleJobs = new ArrayList<>();
        for (int idx = 0; idx < allScheduledJobs.size(); idx++) {
            JobSnapshot scheduledJob = allScheduledJobs.get(idx);
            JobInfo jobInfo = scheduledJob.getJobInfo();
            if (scheduledJob.isRunnable() && jobInfo.isRequireDeviceIdle()) {
                idleJobs.add(jobInfo);
            }
        }
        return idleJobs;
    }
}
