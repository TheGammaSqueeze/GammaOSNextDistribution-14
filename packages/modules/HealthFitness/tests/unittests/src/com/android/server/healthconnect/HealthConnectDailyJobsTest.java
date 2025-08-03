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

package com.android.server.healthconnect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.content.Context;


import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HealthConnectDailyJobsTest {
    private static final String HEALTH_CONNECT_DAILY_JOB_NAMESPACE = "HEALTH_CONNECT_DAILY_JOB";
    private static final String ANDROID_SERVER_PACKAGE_NAME = "com.android.server";
    @Mock Context mContext;
    @Mock private JobScheduler mJobScheduler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mJobScheduler.forNamespace(HEALTH_CONNECT_DAILY_JOB_NAMESPACE))
                .thenReturn(mJobScheduler);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mContext.getPackageName()).thenReturn(ANDROID_SERVER_PACKAGE_NAME);
    }

    @Test
    public void testJobSchedule() {
        HealthConnectDailyJobs.schedule(mContext, 0);
        verify(mJobScheduler, times(1)).schedule(any());
        HealthConnectDailyJobs.schedule(mContext, 1);
        verify(mJobScheduler, times(2)).schedule(any());
        HealthConnectDailyJobs.cancelAllJobs(mContext);
        verify(mJobScheduler, times(1)).cancelAll();
    }
}
