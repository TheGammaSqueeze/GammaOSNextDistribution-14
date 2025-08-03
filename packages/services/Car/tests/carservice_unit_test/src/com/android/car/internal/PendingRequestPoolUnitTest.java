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
package com.android.car.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link LongPendingRequestPool}.
 */
public final class PendingRequestPoolUnitTest {

    private static final class LongTestRequest implements LongRequestIdWithTimeout {
        private final long mRequestId;
        private final long mTimeoutUptimeMs;

        LongTestRequest(long requestId, long timeoutUptimeMs) {
            mRequestId = requestId;
            mTimeoutUptimeMs = timeoutUptimeMs;
        }

        @Override
        public long getRequestId() {
            return mRequestId;
        }

        @Override
        public long getTimeoutUptimeMs() {
            return mTimeoutUptimeMs;
        }
    }

    private static final class LongTestTimeoutCallback implements
            LongPendingRequestPool.TimeoutCallback {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final List<Long> mTimeoutRequestIds = new ArrayList<>();

        @Override
        public void onRequestsTimeout(List<Long> requestIds) {
            synchronized (mLock) {
                for (int i = 0; i < requestIds.size(); i++) {
                    mTimeoutRequestIds.add(requestIds.get(i));
                }
                mLock.notify();
            }
        }

        List<Long> waitForTimeoutRequestIds(int count, long waitTimeoutMs) throws Exception {
            synchronized (mLock) {
                long endTime = SystemClock.uptimeMillis() + waitTimeoutMs;
                while (mTimeoutRequestIds.size() < count) {
                    long now = SystemClock.uptimeMillis();
                    if (now >= endTime) {
                        break;
                    }
                    mLock.wait(endTime - now);
                }
                return new ArrayList<>(mTimeoutRequestIds);
            }
        }

        int countTimeoutRequestIds() {
            synchronized (mLock) {
                return mTimeoutRequestIds.size();
            }
        }
    }

    private final HandlerThread mHandlerThread = new HandlerThread(
            PendingRequestPoolUnitTest.class.getSimpleName());
    private LongTestTimeoutCallback mLongTestTimeoutCallback;
    private LongPendingRequestPool<LongTestRequest> mLongPendingRequestPool;

    @Before
    public void setUp() {
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mLongTestTimeoutCallback = new LongTestTimeoutCallback();
        mLongPendingRequestPool = new LongPendingRequestPool<>(looper, mLongTestTimeoutCallback);
    }

    @After
    public void tearDown() {
        mHandlerThread.quitSafely();
    }

    @Test
    public void testAddRemoveRequests() {
        long testRequestId1 = 123;
        long testRequestId2 = 234;
        long timeoutUptimeMs = SystemClock.uptimeMillis() + 1000;
        LongTestRequest request1 = new LongTestRequest(testRequestId1, timeoutUptimeMs);
        LongTestRequest request2 = new LongTestRequest(testRequestId2, timeoutUptimeMs);

        mLongPendingRequestPool.addPendingRequests(List.of(request1, request2));

        assertThat(mLongPendingRequestPool.getRequestIfFound(0)).isNull();
        assertThat(mLongPendingRequestPool.getRequestIfFound(testRequestId1)).isEqualTo(request1);
        assertThat(mLongPendingRequestPool.getRequestIfFound(testRequestId2)).isEqualTo(request2);

        mLongPendingRequestPool.removeRequest(testRequestId1);
        mLongPendingRequestPool.removeRequest(testRequestId2);

        assertThat(mLongTestTimeoutCallback.countTimeoutRequestIds()).isEqualTo(0);
        assertWithMessage("Expect request pool to be empty after the test").that(
                mLongPendingRequestPool.isEmpty()).isTrue();
    }

    @Test
    public void testRequestTimeout() throws Exception {
        long testRequestId1 = 123;
        long testRequestId2 = 234;
        long testRequestId3 = 345;
        LongTestRequest request1 = new LongTestRequest(testRequestId1,
                SystemClock.uptimeMillis() + 100);
        LongTestRequest request2 = new LongTestRequest(testRequestId2,
                SystemClock.uptimeMillis() + 200);
        LongTestRequest request3 = new LongTestRequest(testRequestId3,
                SystemClock.uptimeMillis() + 1000);

        mLongPendingRequestPool.addPendingRequests(List.of(request1, request2, request3));

        // No requests should timeout yet.
        assertThat(mLongTestTimeoutCallback.countTimeoutRequestIds()).isEqualTo(0);

        // Mark request 3 as finished.
        mLongPendingRequestPool.removeRequest(testRequestId3);

        List<Long> timeoutRequestIds = mLongTestTimeoutCallback.waitForTimeoutRequestIds(
                /* count= */ 2, /* waitTimeoutMs= */ 1000);

        assertThat(timeoutRequestIds).hasSize(2);
        assertThat(timeoutRequestIds).containsExactlyElementsIn(
                new Long[]{testRequestId1, testRequestId2});

        // Must remove the timeout request explicitly.
        mLongPendingRequestPool.removeRequest(testRequestId1);
        mLongPendingRequestPool.removeRequest(testRequestId2);
        assertWithMessage("Expect request pool to be empty after the test").that(
                mLongPendingRequestPool.isEmpty()).isTrue();
    }

    @Test
    public void testRequestsAlreadyTimeoutNoDuplicateCallbacksCalled() throws Exception {
        long testRequestId1 = 123;
        long testRequestId2 = 234;
        long testRequestId3 = 345;

        long now = SystemClock.uptimeMillis();
        // All the requests already timeout.
        LongTestRequest request1 = new LongTestRequest(testRequestId1, now);
        LongTestRequest request2 = new LongTestRequest(testRequestId2, now);
        LongTestRequest request3 = new LongTestRequest(testRequestId3, now);

        mLongPendingRequestPool.addPendingRequests(List.of(request1));

        // The callback is invoked in a message handler, so it is not guaranteed to be invoked
        // after the addPendingRequests returns.
        List<Long> timeoutRequestIds = mLongTestTimeoutCallback.waitForTimeoutRequestIds(
                /* count= */ 1, /* waitTimeoutMs= */ 1000);
        assertWithMessage("A request that already timeout must invoke the callback")
                .that(timeoutRequestIds).hasSize(1);
        assertThat(timeoutRequestIds).containsExactlyElementsIn(new Long[]{testRequestId1});

        mLongPendingRequestPool.addPendingRequests(List.of(request2, request3));

        timeoutRequestIds = mLongTestTimeoutCallback.waitForTimeoutRequestIds(
                /* count= */ 3, /* waitTimeoutMs= */ 1000);
        assertWithMessage("Requests that already timeout must invoke the callback")
                .that(timeoutRequestIds).hasSize(3);
        assertThat(timeoutRequestIds).containsExactlyElementsIn(
                new Long[]{testRequestId1, testRequestId2, testRequestId3});

        // Clean up the requests.
        mLongPendingRequestPool.removeRequest(testRequestId1);
        mLongPendingRequestPool.removeRequest(testRequestId2);
        mLongPendingRequestPool.removeRequest(testRequestId3);
        assertWithMessage("Expect request pool to be empty after the test").that(
                mLongPendingRequestPool.isEmpty()).isTrue();
    }
}
