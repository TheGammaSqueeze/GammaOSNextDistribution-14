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

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.os.IBinder;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.IImsVideoSession;
import android.telephony.imsmedia.ImsVideoSession;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.VideoConfig;
import android.view.Surface;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class ImsVideoSessionTest {

    @Mock
    IImsVideoSession mMockIImsVideoSession;

    @Mock
    IBinder mMockIBinder;

    @Mock
    Surface mMockPreviewSurface;

    @Mock
    Surface mMockDisplaySurface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mMockIBinder).when(mMockIImsVideoSession).asBinder();
    }

    @Test
    public void testConstructorAndApis() {
        ImsVideoSession imsVideoSession = new ImsVideoSession(mMockIImsVideoSession);

        imsVideoSession.getBinder();
        imsVideoSession.getSessionId();
        imsVideoSession.setPreviewSurface(mMockPreviewSurface);
        imsVideoSession.setDisplaySurface(mMockDisplaySurface);
        imsVideoSession.requestVideoDataUsage();

        VideoConfig videoConfig = new VideoConfig.Builder().build();
        imsVideoSession.modifySession(videoConfig);

        MediaQualityThreshold threshold = new MediaQualityThreshold.Builder().build();
        imsVideoSession.setMediaQualityThreshold(threshold);

        List<RtpHeaderExtension> extensions = new ArrayList<RtpHeaderExtension>();
        imsVideoSession.sendHeaderExtension(extensions);

        try {
            verify(mMockIImsVideoSession).asBinder();
            verify(mMockIImsVideoSession).getSessionId();
            verify(mMockIImsVideoSession).setPreviewSurface(mMockPreviewSurface);
            verify(mMockIImsVideoSession).setDisplaySurface(mMockDisplaySurface);
            verify(mMockIImsVideoSession).requestVideoDataUsage();
            verify(mMockIImsVideoSession).modifySession(videoConfig);
            verify(mMockIImsVideoSession).setMediaQualityThreshold(threshold);
            verify(mMockIImsVideoSession).sendHeaderExtension(extensions);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
