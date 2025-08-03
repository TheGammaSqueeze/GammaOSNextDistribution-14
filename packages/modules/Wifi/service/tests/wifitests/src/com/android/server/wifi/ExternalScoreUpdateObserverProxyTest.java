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

package com.android.server.wifi;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.ExternalScoreUpdateObserverProxy}.
 */
@SmallTest
public class ExternalScoreUpdateObserverProxyTest extends WifiBaseTest {
    private static final int TEST_SESSION_ID = 7;
    private static final int TEST_SCORE = 7;
    private static final boolean TEST_STATUS = true;

    TestLooper mLooper = new TestLooper();
    @Mock WifiManager.ScoreUpdateObserver mCallback;
    ExternalScoreUpdateObserverProxy mExternalScoreUpdateObserverProxy;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mExternalScoreUpdateObserverProxy = new ExternalScoreUpdateObserverProxy(
                new WifiThreadRunner(new Handler(mLooper.getLooper())));
    }

    @Test
    public void testCallback() throws Exception {
        mExternalScoreUpdateObserverProxy.registerCallback(mCallback);

        mExternalScoreUpdateObserverProxy.notifyScoreUpdate(TEST_SESSION_ID, TEST_SCORE);
        mLooper.dispatchAll();
        verify(mCallback).notifyScoreUpdate(TEST_SESSION_ID, TEST_SCORE);

        mExternalScoreUpdateObserverProxy.triggerUpdateOfWifiUsabilityStats(TEST_SESSION_ID);
        mLooper.dispatchAll();
        verify(mCallback).triggerUpdateOfWifiUsabilityStats(TEST_SESSION_ID);

        // Unregister the callback
        mExternalScoreUpdateObserverProxy.unregisterCallback(mCallback);

        mExternalScoreUpdateObserverProxy.notifyScoreUpdate(TEST_SESSION_ID, TEST_SCORE);
        mExternalScoreUpdateObserverProxy.triggerUpdateOfWifiUsabilityStats(TEST_SESSION_ID);
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testCallbackForApiInS() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mExternalScoreUpdateObserverProxy.registerCallback(mCallback);

        mExternalScoreUpdateObserverProxy.notifyStatusUpdate(TEST_SESSION_ID, TEST_STATUS);
        mLooper.dispatchAll();
        verify(mCallback).notifyStatusUpdate(TEST_SESSION_ID, TEST_STATUS);

        mExternalScoreUpdateObserverProxy.requestNudOperation(TEST_SESSION_ID);
        mLooper.dispatchAll();
        verify(mCallback).requestNudOperation(TEST_SESSION_ID);

        mExternalScoreUpdateObserverProxy.blocklistCurrentBssid(TEST_SESSION_ID);
        mLooper.dispatchAll();
        verify(mCallback).blocklistCurrentBssid(TEST_SESSION_ID);

        // Unregister the callback
        mExternalScoreUpdateObserverProxy.unregisterCallback(mCallback);

        mExternalScoreUpdateObserverProxy.notifyStatusUpdate(TEST_SESSION_ID, TEST_STATUS);
        mExternalScoreUpdateObserverProxy.requestNudOperation(TEST_SESSION_ID);
        mExternalScoreUpdateObserverProxy.blocklistCurrentBssid(TEST_SESSION_ID);
        mLooper.dispatchAll();

        verifyNoMoreInteractions(mCallback);
    }
}
