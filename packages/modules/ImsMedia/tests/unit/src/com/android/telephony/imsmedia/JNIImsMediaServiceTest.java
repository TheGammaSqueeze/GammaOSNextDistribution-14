/**
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

package com.android.telephony.imsmedia;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Parcel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class JNIImsMediaServiceTest {
    private JNIImsMediaService mJniService;
    @Mock private AudioListener mAudioListener1;
    @Mock private AudioListener mAudioListener2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mJniService = JNIImsMediaService.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        mJniService.clearListener();
        assertEquals(mJniService.getListenerSize(), 0);
    }

    @Test
    public void testMultipleAudioListener() {
        final int sessionId1 = 0;
        final int sessionId2 = 1;

        mJniService.setListener(sessionId1, mAudioListener1);
        mJniService.setListener(sessionId2, mAudioListener2);
        assertEquals(mJniService.getListenerSize(), 2);

        Parcel parcel1 = Parcel.obtain();
        parcel1.writeInt(AudioSession.EVENT_OPEN_SESSION_SUCCESS);
        parcel1.writeInt(sessionId1);
        byte[] data1 = parcel1.marshall();

        mJniService.sendData2Java(sessionId1, data1);
        verify(mAudioListener1, times(1)).onMessage(any());

        Parcel parcel2 = Parcel.obtain();
        parcel2.writeInt(AudioSession.EVENT_OPEN_SESSION_SUCCESS);
        parcel1.writeInt(sessionId2);
        byte[] data2 = parcel2.marshall();
        parcel2.recycle();

        mJniService.sendData2Java(sessionId2, data2);
        verify(mAudioListener2, times(1)).onMessage(any());
    }
}
