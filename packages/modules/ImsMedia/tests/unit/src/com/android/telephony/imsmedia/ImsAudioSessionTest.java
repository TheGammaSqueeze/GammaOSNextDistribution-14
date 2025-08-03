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
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.IImsAudioSession;
import android.telephony.imsmedia.ImsAudioSession;
import android.telephony.imsmedia.MediaQualityThreshold;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class ImsAudioSessionTest {

    static final int AUDIO_SESSION_ID = 1;

    @Mock
    IImsAudioSession mMockIImsAudioSession;

    @Mock
    IBinder mMockIBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mMockIBinder).when(mMockIImsAudioSession).asBinder();
    }

    @Test
    public void testConstructorAndApis() {
        ImsAudioSession imsAudioSession = new ImsAudioSession(mMockIImsAudioSession);

        imsAudioSession.getBinder();
        imsAudioSession.getSessionId();

        AudioConfig audioConfig = new AudioConfig.Builder().build();
        imsAudioSession.addConfig(audioConfig);
        imsAudioSession.deleteConfig(audioConfig);
        imsAudioSession.modifySession(audioConfig);
        imsAudioSession.confirmConfig(audioConfig);

        MediaQualityThreshold threshold = new MediaQualityThreshold.Builder().build();
        imsAudioSession.setMediaQualityThreshold(threshold);

        imsAudioSession.sendDtmf('1', 10);

        List<RtpHeaderExtension> extensions = new ArrayList<RtpHeaderExtension>();
        imsAudioSession.sendHeaderExtension(extensions);

        try {
            verify(mMockIImsAudioSession).asBinder();
            verify(mMockIImsAudioSession).getSessionId();
            verify(mMockIImsAudioSession).addConfig(audioConfig);
            verify(mMockIImsAudioSession).deleteConfig(audioConfig);
            verify(mMockIImsAudioSession).modifySession(audioConfig);
            verify(mMockIImsAudioSession).confirmConfig(audioConfig);
            verify(mMockIImsAudioSession).setMediaQualityThreshold(threshold);
            verify(mMockIImsAudioSession).sendDtmf('1', 10);
            verify(mMockIImsAudioSession).sendHeaderExtension(extensions);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
