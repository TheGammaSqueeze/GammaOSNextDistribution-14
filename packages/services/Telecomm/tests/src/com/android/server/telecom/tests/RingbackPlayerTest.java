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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.RingbackPlayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class RingbackPlayerTest extends TelecomTestCase {
    @Mock InCallTonePlayer.Factory mFactory;
    @Mock Call mCall;
    @Mock InCallTonePlayer mTonePlayer;

    private RingbackPlayer mRingbackPlayer;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(mFactory.createPlayer(any(Call.class), anyInt())).thenReturn(mTonePlayer);
        mRingbackPlayer = new RingbackPlayer(mFactory);
    }

    @SmallTest
    @Test
    public void testPlayerSync() {
        // make sure InCallTonePlayer try to start playing the tone after RingbackPlayer receives
        // stop tone request.
        CountDownLatch latch = new CountDownLatch(1);
        doReturn(CallState.DIALING).when(mCall).getState();
        doAnswer(x -> {
            new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // Ignore
                }
            }).start();
            return true;
        }).when(mTonePlayer).startTone();

        mRingbackPlayer.startRingbackForCall(mCall);
        mRingbackPlayer.stopRingbackForCall(mCall);
        assertFalse(mRingbackPlayer.isRingbackPlaying());
        latch.countDown();
    }
}
