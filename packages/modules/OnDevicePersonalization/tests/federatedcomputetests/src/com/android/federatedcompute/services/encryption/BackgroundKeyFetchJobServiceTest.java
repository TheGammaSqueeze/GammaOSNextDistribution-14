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

package com.android.federatedcompute.services.encryption;

import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyDao;
import com.android.federatedcompute.services.http.HttpClient;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.ExecutionException;

// TODO: add tests with Ph flags
@RunWith(JUnit4.class)
public class BackgroundKeyFetchJobServiceTest {

    private BackgroundKeyFetchJobService mSpyService;

    private MockitoSession mStaticMockSession;

    private Context mContext;

    private JobScheduler mJobScheduler;

    private HttpClient mHttpClient;

    public FederatedComputeEncryptionKeyDao mEncryptionDao;

    public FederatedComputeEncryptionKeyManager mSpyKeyManager;

    private TestInjector mInjector;

    @Before
    public void setUp() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.enableScheduleBackgroundKeyFetchJob();
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mInjector = new TestInjector();
        mEncryptionDao = FederatedComputeEncryptionKeyDao.getInstanceForTest(mContext);
        mHttpClient = new HttpClient();
        mSpyService = spy(new BackgroundKeyFetchJobService(new TestInjector()));
        doReturn(mSpyService).when(mSpyService).getApplicationContext();
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        mJobScheduler = mContext.getSystemService(JobScheduler.class);
        mJobScheduler.cancel(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID);
        mSpyKeyManager =
                spy(
                        new FederatedComputeEncryptionKeyManager(
                                MonotonicClock.getInstance(),
                                mEncryptionDao,
                                FlagsFactory.getFlags(),
                                mHttpClient,
                                MoreExecutors.newDirectExecutorService()));
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }

        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testOnStartJob() {
        FederatedComputeEncryptionKeyManager keyManager =
                mInjector.getEncryptionKeyManager(mContext);
        List<FederatedComputeEncryptionKey> emptyKeyList = List.of();
        doReturn(FluentFuture.from(Futures.immediateFuture(emptyKeyList)))
                .when(keyManager)
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true);

        mSpyService.run(mock(JobParameters.class));

        verify(mSpyService, times(1)).onStartJob(any());
        verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
    }

    @Test
    public void testOnStartJob_onFailure() {
        FederatedComputeEncryptionKeyManager keyManager =
                mInjector.getEncryptionKeyManager(mContext);
        doReturn(
                        FluentFuture.from(
                                Futures.immediateFailedFuture(
                                        new ExecutionException(
                                                " Failed to fetch keys",
                                                new IllegalStateException("http 404")))))
                .when(keyManager)
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true);

        mSpyService.run(mock(JobParameters.class));

        verify(mSpyService, times(1)).onStartJob(any());
        verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
    }

    @Test
    public void testScheduleJob() {
        final JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertThat(
                        BackgroundKeyFetchJobService.scheduleJobIfNeeded(
                                mContext, FlagsFactory.getFlags()))
                .isEqualTo(true);

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(
                        FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID);

        assertThat(scheduledJob.getId())
                .isEqualTo(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID);
    }

    @Test
    public void testScheduleJob_notNeeded() {
        assertThat(
                        BackgroundKeyFetchJobService.scheduleJobIfNeeded(
                                mContext, FlagsFactory.getFlags()))
                .isEqualTo(true);

        assertThat(
                        BackgroundKeyFetchJobService.scheduleJobIfNeeded(
                                mContext, FlagsFactory.getFlags()))
                .isEqualTo(false);
    }

    @Test
    public void testScheduleJob_notEnabled() {
        PhFlagsTestUtil.disableScheduleBackgroundKeyFetchJob();

        assertThat(BackgroundKeyFetchJobService.scheduleJobIfNeeded(
                mContext, FlagsFactory.getFlags()
        )).isEqualTo(false);
    }

    @Test
    public void testOnStopJob() {
        assertFalse(mSpyService.onStopJob(mock(JobParameters.class)));
    }

    @Test
    public void testOnStartJob_enableKillSwitch() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        FederatedComputeEncryptionKeyManager keyManager =
                mInjector.getEncryptionKeyManager(mContext);
        List<FederatedComputeEncryptionKey> emptyKeyList = List.of();
        doReturn(FluentFuture.from(Futures.immediateFuture(emptyKeyList)))
                .when(keyManager)
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true);
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        mSpyService.scheduleJobIfNeeded(mContext, FlagsFactory.getFlags());
        assertTrue(mJobScheduler.getPendingJob(
                FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID)
                != null);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));
        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(keyManager, never()).fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION,
                /* isScheduledJob= */ true);
        assertTrue(mJobScheduler.getPendingJob(
                FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID)
                == null);
    }

    @Test
    public void testDefaultInjector() {
        BackgroundKeyFetchJobService.Injector injector =
                new BackgroundKeyFetchJobService.Injector();
        assertThat(injector.getExecutor())
                .isEqualTo(FederatedComputeExecutors.getBackgroundExecutor());
        assertThat(injector.getEncryptionKeyManager(mContext))
                .isEqualTo(FederatedComputeEncryptionKeyManager.getInstance(mContext));
        assertThat(injector.getLightWeightExecutor())
                .isEqualTo(FederatedComputeExecutors.getLightweightExecutor());
    }

    class TestInjector extends BackgroundKeyFetchJobService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ListeningExecutorService getLightWeightExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        FederatedComputeEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return mSpyKeyManager;
        }
    }
}
