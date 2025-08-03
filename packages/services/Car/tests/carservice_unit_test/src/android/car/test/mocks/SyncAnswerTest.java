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

package android.car.test.mocks;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncAnswerTest {

    private static final long TIMEOUT_MS = 1_000L;

    private SlowServiceInitializer mSlowServiceInitializer;

    private SlowService mSlowService;

    private BlockingExceptionLogger mExceptionLogger;

    @Before
    public void setUp() {
        mExceptionLogger = new BlockingExceptionLogger();
        mSlowServiceInitializer = spy(new SlowServiceInitializer());
        mSlowService = new SlowService(mSlowServiceInitializer, mExceptionLogger);
    }

    @Test
    public void testForException() throws Exception {
        Exception expected = new Exception("expected");
        SyncAnswer<Boolean> syncException = SyncAnswer.forException(expected);
        when(mSlowServiceInitializer.initialize()).thenAnswer(syncException);

        mSlowService.asyncSlowTask();
        syncException.await(TIMEOUT_MS);

        assertThat(mExceptionLogger.mExceptionReceiver.poll(3, TimeUnit.SECONDS)).isSameInstanceAs(
                expected);
    }

    @Test
    public void testForReturn() throws Exception {
        SyncAnswer<Boolean> syncInitialized = SyncAnswer.forReturn(true);
        when(mSlowServiceInitializer.initialize()).thenAnswer(syncInitialized);

        mSlowService.asyncSlowTask();
        syncInitialized.await(TIMEOUT_MS);

        assertThat(mSlowServiceInitializer.isInitialized()).isTrue();
    }

    private static final class BlockingExceptionLogger {

        final BlockingQueue<Exception> mExceptionReceiver = new ArrayBlockingQueue<>(1);

        void logException(Exception exception) {
            mExceptionReceiver.offer(exception);
        }
    }

    private static final class SlowServiceInitializer {

        private final AtomicBoolean mInitialized = new AtomicBoolean(false);

        boolean initialize() {
            mInitialized.set(true);
            return true;
        }

        boolean isInitialized() {
            return mInitialized.get();
        }
    }

    private static final class SlowService {

        private final SlowServiceInitializer mSlowServiceDelegate;

        private final BlockingExceptionLogger mExceptionLogger;

        SlowService(SlowServiceInitializer slowServiceDelegate,
                BlockingExceptionLogger exceptionLogger) {
            mSlowServiceDelegate = slowServiceDelegate;
            mExceptionLogger = exceptionLogger;
        }

        void asyncSlowTask() {
            new Thread(() -> {
                try {
                    mSlowServiceDelegate.initialize();
                } catch (Exception expected) {
                    mExceptionLogger.logException(expected);
                }
            }, getClass().getName() + "#asyncSlowTask").start();
        }
    }
}


