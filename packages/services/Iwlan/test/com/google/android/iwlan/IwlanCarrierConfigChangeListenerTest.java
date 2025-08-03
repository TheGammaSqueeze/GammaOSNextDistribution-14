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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.*;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.Mockito.lenient;

import android.content.Context;
import android.os.test.TestLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class IwlanCarrierConfigChangeListenerTest {
    private static final String TAG = "IwlanCarrierConfigChangeListenerTest";
    private IwlanCarrierConfigChangeListener mIwlanCarrierConfigChangeListener;
    private TestLooper mTestLooper = new TestLooper();

    private static final int TEST_SUB_ID = 5;
    private static final int TEST_SLOT_ID = 6;
    private static final int TEST_CARRIER_ID = 7;

    MockitoSession mStaticMockSession;
    @Mock private Context mMockContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(IwlanDataService.class)
                        .mockStatic(IwlanEventListener.class)
                        .startMocking();

        lenient().when(IwlanDataService.getContext()).thenReturn(mMockContext);

        mIwlanCarrierConfigChangeListener =
                new IwlanCarrierConfigChangeListener(mTestLooper.getLooper());
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testCarrierConfigChanged() {
        mIwlanCarrierConfigChangeListener.onCarrierConfigChanged(
                TEST_SLOT_ID, TEST_SUB_ID, TEST_CARRIER_ID, TEST_CARRIER_ID);
        mTestLooper.dispatchAll();

        verify(
                () ->
                        IwlanEventListener.onCarrierConfigChanged(
                                mMockContext, TEST_SLOT_ID, TEST_SUB_ID, TEST_CARRIER_ID));
    }
}
