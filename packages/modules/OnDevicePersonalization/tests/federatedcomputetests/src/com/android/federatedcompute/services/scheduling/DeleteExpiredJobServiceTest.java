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

package com.android.federatedcompute.services.scheduling;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenContract;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.data.TaskHistory;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.UUID;

public class DeleteExpiredJobServiceTest {
    private static final String TAG = DeleteExpiredJobServiceTest.class.getSimpleName();
    private static final String POPULATION_NAME = "population_name";
    private static final int JOB_ID = 123;
    private static final String TASK_ID = "task_id";
    private DeleteExpiredJobService mSpyService;

    private ODPAuthorizationTokenDao mSpyAuthTokenDao;
    private FederatedTrainingTaskDao mTrainingTaskDao;

    private Context mContext;
    private JobScheduler mJobScheduler;
    @Mock private Clock mClock;
    @Mock private Flags mMockFlag;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        mContext = ApplicationProvider.getApplicationContext();
        when(mClock.currentTimeMillis()).thenReturn(400L);
        when(mMockFlag.getTaskHistoryTtl()).thenReturn(200L);
        LogUtil.i(TAG, "mSpyAuthTokenDao " + mSpyAuthTokenDao);
        mSpyAuthTokenDao = spy(ODPAuthorizationTokenDao.getInstanceForTest(mContext));
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);
        mSpyService = spy(new DeleteExpiredJobService(new TestInjector()));

        mJobScheduler = mContext.getSystemService(JobScheduler.class);
        mJobScheduler.cancel(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void deleteExpiredAuthToken_success() throws Exception {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired1"));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired2"));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken("unexpired"));

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(5000);
        verify(mSpyService).jobFinished(any(), eq(false));
        SQLiteDatabase db =
                FederatedComputeDbHelper.getInstanceForTest(mContext).getReadableDatabase();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(1);
    }

    @Test
    public void deleteExpiredAuthToken_failure() throws Exception {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired1"));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired2"));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken("unexpired"));
        doThrow(new SQLiteException("exception"))
                .when(mSpyAuthTokenDao)
                .deleteExpiredAuthorizationTokens();

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(2000);
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao).deleteExpiredAuthorizationTokens();
        SQLiteDatabase db =
                FederatedComputeDbHelper.getInstanceForTest(mContext).getReadableDatabase();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(3);
    }

    @Test
    public void deletedExpiredTaskHistory_success() throws Exception {
        // record1 is expired because contribution time (100) < current time (400) - ttl (200).
        TaskHistory record1 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(100)
                        .build();
        TaskHistory record2 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(300)
                        .build();
        mTrainingTaskDao.updateOrInsertTaskHistory(record1);
        mTrainingTaskDao.updateOrInsertTaskHistory(record2);
        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record1, record2);

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(2000);
        verify(mSpyService).jobFinished(any(), eq(false));
        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record2);
    }

    @Test
    public void enableKillSwitch() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        DeleteExpiredJobService.scheduleJobIfNeeded(mContext, FlagsFactory.getFlags());
        assertNotNull(mJobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID));
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao, never()).deleteExpiredAuthorizationTokens();
        assertNull(mJobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID));
    }

    @Test
    public void testDefaultInjector() {
        DeleteExpiredJobService.Injector injector = new DeleteExpiredJobService.Injector();

        assertThat(injector.getExecutor())
                .isEqualTo(FederatedComputeExecutors.getBackgroundExecutor());
        assertThat(injector.getODPAuthorizationTokenDao(mContext))
                .isEqualTo(ODPAuthorizationTokenDao.getInstance(mContext));
    }

    private ODPAuthorizationToken createExpiredAuthToken(String ownerId) {
        long now = MonotonicClock.getInstance().currentTimeMillis();
        ODPAuthorizationToken token =
                new ODPAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now - 10)
                        .build();
        return token;
    }

    private ODPAuthorizationToken createUnexpiredAuthToken(String ownerId) {
        long now = MonotonicClock.getInstance().currentTimeMillis();
        long ttl = 24 * 60 * 60 * 1000L;
        ODPAuthorizationToken token =
                new ODPAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now + ttl)
                        .build();
        return token;
    }

    class TestInjector extends DeleteExpiredJobService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return mSpyAuthTokenDao;
        }

        @Override
        FederatedTrainingTaskDao getTrainingTaskDao(Context context) {
            return mTrainingTaskDao;
        }

        @Override
        Clock getClock() {
            return mClock;
        }

        @Override
        Flags getFlags() {
            return mMockFlag;
        }
    }
}
