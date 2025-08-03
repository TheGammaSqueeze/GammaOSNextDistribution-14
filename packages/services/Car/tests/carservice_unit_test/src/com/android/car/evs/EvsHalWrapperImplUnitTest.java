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

package com.android.car.evs;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

import com.android.car.internal.evs.EvsHalWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class EvsHalWrapperImplUnitTest {

    private static final String TAG = EvsHalWrapperImplUnitTest.class.getSimpleName();
    private static final String MOCK_EVS_CAMERA_NAME_BASE = "/dev/mockcamera";
    private static final String MOCK_EVS_HAL_LIBRARY_NAME = "carservicejni_test";
    private static final int NUMBER_OF_MOCK_CAMERAS = 3;
    private static final int MAX_WAIT_TIME_MS = 3000;
    private static final int NUMBER_EXPECTED_FRAMES = 5;
    private static final int INVALID_HANDLE = 0;

    @Mock private EvsHalWrapper.HalEventCallback mEventCallback;

    private EvsHalWrapperImpl mEvsHalWrapper;

    @Before
    public void setUp() {
        mEvsHalWrapper = EvsHalWrapperImpl.create(mEventCallback, MOCK_EVS_HAL_LIBRARY_NAME);
        assertThat(mEvsHalWrapper.init()).isTrue();

        long handle = mEvsHalWrapper.createServiceHandleForTest();
        assertThat(handle).isNotEqualTo(INVALID_HANDLE);
        assertThat(mEvsHalWrapper.setServiceHandle(handle)).isTrue();

        // EvsHalWrapperImpl always succeeds with the mock EVS HAL implementation.
        assertThat(mEvsHalWrapper.connectToHalServiceIfNecessary()).isTrue();
    }

    @After
    public void tearDown() throws Exception {
        if (mEvsHalWrapper != null) {
            mEvsHalWrapper.disconnectFromHalService();
            mEvsHalWrapper.release();
        }
    }

    @Test
    public void testIsConnected() {
        assertThat(mEvsHalWrapper.isConnected()).isTrue();
    }

    @Test
    public void testOpenAndCloseCamera() {
        for (int i = 0; i < NUMBER_OF_MOCK_CAMERAS; i++) {
            String cameraId = MOCK_EVS_CAMERA_NAME_BASE + i;
            assertThat(mEvsHalWrapper.openCamera(cameraId)).isTrue();
            mEvsHalWrapper.closeCamera();
        }
    }

    @Test
    public void testStartAndStopVideoStream() throws Exception {
        for (int i = 0; i < NUMBER_OF_MOCK_CAMERAS; i++) {
            CountDownLatch frameLatch = new CountDownLatch(NUMBER_EXPECTED_FRAMES);
            CountDownLatch eventLatch = new CountDownLatch(1);
            reset(mEventCallback);
            doAnswer(args -> {
                    eventLatch.countDown();
                    return true;
            }).when(mEventCallback).onHalEvent(anyInt());
            doAnswer(args -> {
                    mEvsHalWrapper.doneWithFrame(args.getArgument(0));
                    frameLatch.countDown();
                    return true;
            }).when(mEventCallback).onFrameEvent(anyInt(), anyObject());

            String cameraId = MOCK_EVS_CAMERA_NAME_BASE + i;
            assertThat(mEvsHalWrapper.openCamera(cameraId)).isTrue();
            assertThat(mEvsHalWrapper.requestToStartVideoStream()).isTrue();

            assertThat(frameLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();

            mEvsHalWrapper.requestToStopVideoStream();
            assertThat(eventLatch.await(1, TimeUnit.SECONDS)).isTrue();

            mEvsHalWrapper.closeCamera();
        }
    }

    @Test
    public void testDeathRecipient() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(args -> {
            latch.countDown();
            return true;
        }).when(mEventCallback).onHalDeath();

        mEvsHalWrapper.triggerBinderDied();

        assertThat(latch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();
    }
}
