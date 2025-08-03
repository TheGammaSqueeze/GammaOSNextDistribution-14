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

package com.android.ondevicepersonalization.services.data.user;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class UserDataCollectionJobServiceTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
    private UserDataCollector mUserDataCollector;
    private UserDataCollectionJobService mService;
    private UserPrivacyStatus mPrivacyStatus = UserPrivacyStatus.getInstance();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        mPrivacyStatus.setPersonalizationStatusEnabled(true);
        mUserDataCollector = UserDataCollector.getInstanceForTest(mContext);
        mService = spy(new UserDataCollectionJobService());
    }

    @Test
    public void onStartJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            doNothing().when(mService).jobFinished(any(), anyBoolean());
            doReturn(mContext.getPackageManager()).when(mService).getPackageManager();
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            boolean result = mService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
            mService.schedule(mContext);
            assertTrue(mJobScheduler.getPendingJob(
                    OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                            != null);
            doNothing().when(mService).jobFinished(any(), anyBoolean());
            boolean result = mService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mService, times(1)).jobFinished(any(), eq(false));
            assertTrue(mJobScheduler.getPendingJob(
                    OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                            == null);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestPersonalizationBlocked() {
        mPrivacyStatus.setPersonalizationStatusEnabled(false);
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            doNothing().when(mService).jobFinished(any(), anyBoolean());
            boolean result = mService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStopJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(
                Strictness.LENIENT).startMocking();
        try {
            assertTrue(mService.onStopJob(mock(JobParameters.class)));
        } finally {
            session.finishMocking();
        }
    }

    @After
    public void cleanUp() {
        mUserDataCollector.clearUserData(RawUserData.getInstance());
        mUserDataCollector.clearMetadata();
    }
}
