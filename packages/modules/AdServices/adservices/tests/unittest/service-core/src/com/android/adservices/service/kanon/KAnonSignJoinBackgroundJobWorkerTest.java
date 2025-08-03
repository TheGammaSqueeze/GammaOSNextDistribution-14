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

package com.android.adservices.service.kanon;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(MockitoJUnitRunner.class)
public class KAnonSignJoinBackgroundJobWorkerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private KAnonSignJoinBackgroundJobWorker mKAnonSignJoinBackgroundJobWorker;
    private KAnonSignJoinManager mKAnonSignJoinManager;
    private KAnonMessageManager mKAnonMessageManager;
    private KAnonMessageDao mKAnonMessageDao;
    private Flags mFlags;
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);
    @Mock private Clock mockClock;
    @Mock private KAnonCaller mockKanonCaller;
    @Mock private AdServicesLogger mockAdServicesLogger;

    private final Instant FIXED_TIME = Instant.now();
    private static final long AD_SELECTION_ID_1 = 1;
    private static final String HASH_SET_1 = "somestring";
    private final DBKAnonMessage.Builder mDbkAnonMessageBuilder =
            DBKAnonMessage.builder()
                    .setKanonHashSet(HASH_SET_1)
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCorrespondingClientParametersExpiryInstant(FIXED_TIME)
                    .setExpiryInstant(FIXED_TIME)
                    .setCreatedAt(FIXED_TIME)
                    .setStatus(KAnonMessageConstants.MessageStatus.JOINED);

    @Before
    public void setup() {
        mKAnonMessageDao =
                Room.inMemoryDatabaseBuilder(mContext, KAnonDatabase.class)
                        .build()
                        .kAnonMessageDao();
        when(mockClock.instant()).thenReturn(FIXED_TIME);
        mFlags = FlagsFactory.getFlags();
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);
        mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        mockKanonCaller,
                        mKAnonMessageManager,
                        mFlags,
                        mockClock,
                        mockAdServicesLogger);
    }

    @Test
    public void makeSignJoinCalls_nonZeroBackgroundCallBatchSize_makesSignJoinCalls()
            throws InterruptedException {
        DBKAnonMessage dbkAnonMessage =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setKanonHashSet(HASH_SET_1)
                        .build();
        mKAnonMessageDao.insertKAnonMessage(dbkAnonMessage);
        Flags flags = new KAnonSignJoinBackgroundWorkerFlags(10);
        mKAnonSignJoinBackgroundJobWorker =
                new KAnonSignJoinBackgroundJobWorker(mContext, flags, mKAnonSignJoinManager);
        List<KAnonMessageEntity> messageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mKAnonSignJoinBackgroundJobWorker.runSignJoinBackgroundProcess().get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
        countDownLatch.await();

        verify(mockKanonCaller).signAndJoinMessages(messageEntityList);
    }

    @Test
    public void makeSignJoinCalls_zeroBackgroundCallBatchSize_doesNotMakeSignJoinCalls()
            throws InterruptedException {
        DBKAnonMessage dbkAnonMessage =
                mDbkAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setKanonHashSet(HASH_SET_1)
                        .build();
        mKAnonMessageDao.insertKAnonMessage(dbkAnonMessage);
        Flags flags = new KAnonSignJoinBackgroundWorkerFlags(0);
        mKAnonSignJoinBackgroundJobWorker =
                new KAnonSignJoinBackgroundJobWorker(mContext, flags, mKAnonSignJoinManager);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mKAnonSignJoinBackgroundJobWorker.runSignJoinBackgroundProcess().get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
        countDownLatch.await();

        verifyNoMoreInteractions(mockKanonCaller);
    }

    private static class KAnonSignJoinBackgroundWorkerFlags implements Flags {
        private final int mMessagesPerBackgroundRun;

        private KAnonSignJoinBackgroundWorkerFlags(int messagesPerBackgroundRun) {
            this.mMessagesPerBackgroundRun = messagesPerBackgroundRun;
        }

        @Override
        public int getFledgeKAnonMessagesPerBackgroundProcess() {
            return mMessagesPerBackgroundRun;
        }

        @Override
        public boolean getFledgeKAnonBackgroundProcessEnabled() {
            return true;
        }
    }
}
