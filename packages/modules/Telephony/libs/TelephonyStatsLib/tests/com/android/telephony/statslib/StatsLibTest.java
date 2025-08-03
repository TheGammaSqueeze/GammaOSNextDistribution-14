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

package com.android.telephony.statslib;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.StatsLog;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class StatsLibTest {

    private static final String RET_SUCCESS = "success";
    private static final String RET_FAILED = "failed";

    @Mock Context mMockContext;
    @Mock StatsLibPulledAtomCallback mMockPulledAtomCallback;
    @Mock StatsLibStorage mMockStorage;
    @Mock PulledCallback mMockPulledCallback;
    private StatsLib mStatsLib;
    private MockitoSession mMockitoSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext = spy(ApplicationProvider.getApplicationContext());
        mMockitoSession =
                mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(StatsLog.class)
                        .startMocking();
        when(mMockPulledAtomCallback.getStatsLibStorage()).thenReturn(mMockStorage);
        mStatsLib = new StatsLib(mMockPulledAtomCallback);
    }

    @After
    public void tearDown() {
        mStatsLib = null;
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
            mMockitoSession = null;
        }
    }

    @Test
    public void testWritePushedAtomHandler() {
        AtomsPushedTestInfo first = new AtomsPushedTestInfo(1);
        AtomsPushedTestInfo second = new AtomsPushedTestInfo(2);
        AtomsPushedTestInfo third = new AtomsPushedTestInfo(3);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Callable<String> callableTask =
                () -> {
                    mStatsLib.write(null);
                    mStatsLib.write(first);
                    mStatsLib.write(second);
                    mStatsLib.write(third);
                    return RET_SUCCESS;
                };
        int delay = 100;
        ScheduledFuture<String> future =
                executor.schedule(callableTask, delay, TimeUnit.MILLISECONDS);
        String result;
        try {
            result = future.get();
        } catch (ExecutionException | InterruptedException e) {
            result = RET_FAILED;
        }

        assertEquals(result, RET_SUCCESS);
        verify(mMockStorage, timeout(300).times(3)).appendPushedAtoms(any(AtomsPushed.class));
    }

    @Test
    public void testRegisterPulledAtomCallback() {
        AtomsPulledTestInfo first = new AtomsPulledTestInfo();
        mStatsLib.registerPulledAtomCallback(first.getStatsId(), mMockPulledCallback);
        verify(mMockPulledAtomCallback).registerAtom(first.getStatsId(), mMockPulledCallback);
    }

    @Test
    public void testNullStorage() {
        when(mMockPulledAtomCallback.getStatsLibStorage()).thenReturn(null);

        mStatsLib.onWritePushedAtom(new AtomsPushedTestInfo());
        mStatsLib.append(new AtomsPulledTestInfo());
        verify(mMockStorage, never()).appendPushedAtoms(any());
        verify(mMockStorage, never()).appendPulledAtoms(any());
    }

    @Test
    public void testAppendPulledAtom() {
        final int type1 = 1;
        final int type2 = 2;
        Random random = new Random();
        int count1 = random.nextInt(100);
        int count2 = random.nextInt(1000);
        int count3 = random.nextInt(10000);
        int count4 = random.nextInt(100000);

        mStatsLib.append(null);
        mStatsLib.append(new AtomsPulledTestInfo(type1, count1));
        mStatsLib.append(new AtomsPulledTestInfo(type1, count2));
        mStatsLib.append(new AtomsPulledTestInfo(type2, count3));
        mStatsLib.append(new AtomsPulledTestInfo(type2, count4));

        verify(mMockStorage, times(4)).appendPulledAtoms(any());
    }
}
