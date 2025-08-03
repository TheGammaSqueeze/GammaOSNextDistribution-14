/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.spe;

import static android.app.job.JobInfo.NETWORK_TYPE_NONE;
import static android.app.job.JobInfo.PRIORITY_MIN;
import static android.app.job.JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS;

import static com.google.common.truth.Truth.assertThat;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;

import org.junit.Test;

/** Unit tests for {@link JobUtil}. */
public final class JobUtilTest extends AdServicesUnitTestCase {
    @Test
    public void testJobInfoToString_noExpeditedAndPriorityAndUri() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();
        PersistableBundle extras = new PersistableBundle();
        extras.putString("ExtraString", "extra");

        builder.setRequiredNetworkType(NETWORK_TYPE_NONE)
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(true)
                .setPeriodic(1_000_000, 500_000)
                .setExtras(extras)
                .setPersisted(true);

        assertThat(JobUtil.jobInfoToString(builder.build()))
                .isEqualTo(
                        "JobInfo:{JobId=1, Network=0, RequiresCharging=true,"
                            + " RequiresBatteryNotLow=true, RequiresDeviceIdle=false,"
                            + " RequiresStorageNotLow=true, TriggerContentMaxDelayMs=-1,"
                            + " TriggerContentUpdateDelayMs=-1, PeriodicIntervalMs=1000000,"
                            + " FlexIntervalMs=500000, MinimumLatencyMs=0, OverrideDeadlineMs=0,"
                            + " Extras=PersistableBundle[{ExtraString=extra}], IsPersisted=true,"
                            + " IsExpedited=false, Priority=300}");
    }

    @Test
    public void testJobInfoToString_uri() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        builder.setRequiredNetworkType(NETWORK_TYPE_NONE)
                .addTriggerContentUri(
                        new JobInfo.TriggerContentUri(
                                Uri.parse("testUri"), FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(100)
                .setTriggerContentMaxDelay(200);

        assertThat(JobUtil.jobInfoToString(builder.build()))
                .isEqualTo(
                        "JobInfo:{JobId=1, Network=0, RequiresCharging=false,"
                                + " RequiresBatteryNotLow=false, RequiresDeviceIdle=false,"
                                + " RequiresStorageNotLow=false ,"
                                + " TriggerUri=[(uriString=testUri,uriFlag=1),],"
                                + " TriggerContentMaxDelayMs=200, TriggerContentUpdateDelayMs=100,"
                                + " PeriodicIntervalMs=0, FlexIntervalMs=0, MinimumLatencyMs=0,"
                                + " OverrideDeadlineMs=0, Extras=PersistableBundle[{}],"
                                + " IsPersisted=false, IsExpedited=false, Priority=300}");
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Expedited is only available in S+")
    public void testJobInfoToString_expedited() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        builder.setExpedited(true);

        assertThat(JobUtil.jobInfoToString(builder.build()))
                .isEqualTo(
                        "JobInfo:{JobId=1, Network=0, RequiresCharging=false,"
                            + " RequiresBatteryNotLow=false, RequiresDeviceIdle=false,"
                            + " RequiresStorageNotLow=false, TriggerContentMaxDelayMs=-1,"
                            + " TriggerContentUpdateDelayMs=-1, PeriodicIntervalMs=0,"
                            + " FlexIntervalMs=0, MinimumLatencyMs=0, OverrideDeadlineMs=0,"
                            + " Extras=PersistableBundle[{}], IsPersisted=false, IsExpedited=true,"
                            + " Priority=500}");
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Priority is only available in T+")
    public void testJobInfoToString_priority() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        builder.setPriority(PRIORITY_MIN);

        assertThat(JobUtil.jobInfoToString(builder.build()))
                .isEqualTo(
                        "JobInfo:{JobId=1, Network=0, RequiresCharging=false,"
                            + " RequiresBatteryNotLow=false, RequiresDeviceIdle=false,"
                            + " RequiresStorageNotLow=false, TriggerContentMaxDelayMs=-1,"
                            + " TriggerContentUpdateDelayMs=-1, PeriodicIntervalMs=0,"
                            + " FlexIntervalMs=0, MinimumLatencyMs=0, OverrideDeadlineMs=0,"
                            + " Extras=PersistableBundle[{}], IsPersisted=false, IsExpedited=false,"
                            + " Priority=100}");
    }

    private JobInfo.Builder getBaseJobInfoBuilder() {
        // The test doesn't actually schedule a job, so it isn't an issue to use a non-JobService
        // class.
        return new JobInfo.Builder(/* jobId= */ 1, new ComponentName(sContext, JobUtil.class));
    }
}
