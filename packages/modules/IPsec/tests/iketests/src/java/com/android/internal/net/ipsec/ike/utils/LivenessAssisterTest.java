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

package com.android.internal.net.ipsec.ike.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.ipsec.test.ike.IkeSessionCallback;

import com.android.internal.net.ipsec.test.ike.utils.LivenessAssister;
import com.android.internal.net.ipsec.test.ike.utils.LivenessAssister.IIkeMetricsCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

public final class LivenessAssisterTest {

    LivenessAssister mLivenessAssister;
    Executor mSpyUserCbExecutor;

    IkeSessionCallback mMockIkeSessionCallback;

    IIkeMetricsCallback mMockIkeMetricsCallback;

    @Before
    public void setUp() throws Exception {
        mMockIkeSessionCallback = mock(IkeSessionCallback.class);
        mMockIkeMetricsCallback = mock(IIkeMetricsCallback.class);
        mSpyUserCbExecutor =
                spy(
                        (command) -> {
                            command.run();
                        });
        mLivenessAssister =
                new LivenessAssister(
                        mMockIkeSessionCallback, mSpyUserCbExecutor, mMockIkeMetricsCallback);
    }

    @Test
    public void testLivenessCheckRequestedWithDpdOnDemandIkeLocalInfo() throws Exception {
        assertFalse(mLivenessAssister.isLivenessCheckRequested());
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_ON_DEMAND);
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_BACKGROUND);
        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));
        assertTrue(mLivenessAssister.isLivenessCheckRequested());
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_STARTED));
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_ONGOING));
        verify(mMockIkeMetricsCallback, never())
                .onLivenessCheckCompleted(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testLivenessCheckRequestedWithJoiningBusyStateRunningInBackground()
            throws Exception {
        assertFalse(mLivenessAssister.isLivenessCheckRequested());
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_BACKGROUND);
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_BACKGROUND);
        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));
        assertTrue(mLivenessAssister.isLivenessCheckRequested());
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_STARTED));
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_ONGOING));
        verify(mMockIkeMetricsCallback, never())
                .onLivenessCheckCompleted(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testLivenessCheckRequestedAndSuccessCallback() throws Exception {
        // usercallback #1 - STARTED
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_ON_DEMAND);
        // usercallback #2 - ONGOING
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_BACKGROUND);
        assertTrue(mLivenessAssister.isLivenessCheckRequested());
        // usercallback #3 - SUCCESS
        mLivenessAssister.markPeerAsAlive();
        assertFalse(mLivenessAssister.isLivenessCheckRequested());
        verify(mSpyUserCbExecutor, times(3)).execute(any(Runnable.class));
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_SUCCESS));
        verify(mMockIkeMetricsCallback)
                .onLivenessCheckCompleted(
                        anyInt(),
                        eq(1) /* numberOfOnGoing */,
                        eq(true) /* resultSuccess */);
    }

    @Test
    public void testLivenessCheckRequestedAndFailureCallback() throws Exception {
        // usercallback #1 - STARTED
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_BACKGROUND);
        // usercallback #2 - ONGOING
        mLivenessAssister.livenessCheckRequested(LivenessAssister.REQ_TYPE_ON_DEMAND);
        assertTrue(mLivenessAssister.isLivenessCheckRequested());
        // usercallback #3 - FAILURE
        mLivenessAssister.markPeerAsDead();
        assertFalse(mLivenessAssister.isLivenessCheckRequested());
        verify(mSpyUserCbExecutor, times(3)).execute(any(Runnable.class));
        verify(mMockIkeSessionCallback)
                .onLivenessStatusChanged(eq(IkeSessionCallback.LIVENESS_STATUS_FAILURE));
        verify(mMockIkeMetricsCallback)
                .onLivenessCheckCompleted(
                        anyInt(),
                        eq(1) /* numberOfOnGoing */,
                        eq(false) /* resultSuccess */);
    }
}
